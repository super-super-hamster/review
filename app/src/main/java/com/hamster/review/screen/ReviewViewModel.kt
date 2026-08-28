package com.hamster.review.screen

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.hamster.review.data.db.AppDatabase
import com.hamster.review.data.db.DailyRecordEntity
import com.hamster.review.data.db.QuestionDetail
import com.hamster.review.data.model.QuestionType
import com.hamster.review.data.repository.ReviewRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ReviewUiState(
    val isLoading: Boolean = true,
    val currentQuestion: QuestionDetail? = null,
    val answered: Boolean = false,
    val selectedOptionIds: Set<Long> = emptySet(),
    val showAnswer: Boolean = false,
    val lastResultCorrect: Boolean? = null,
    val mastered: Boolean = false,
    val finished: Boolean = false,
    val completedToday: Boolean = false,
    val dailyRecords: List<DailyRecordEntity> = emptyList(),
    val remainingCount: Int = 0,
    val wrongCount: Int = 0
)

class ReviewViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val subjectId: Long = checkNotNull(savedStateHandle["subjectId"])
    private val repository = ReviewRepository(AppDatabase.getInstance(application))

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var questionStartTime = System.currentTimeMillis()

    private val normalQueue = ArrayDeque<QuestionDetail>()
    private val wrongQueue = ArrayDeque<WrongReviewItem>()

    private val completedInSession = mutableSetOf<Long>()

    private data class WrongReviewItem(
        val question: QuestionDetail,
        var remainingGap: Int
    )

    companion object {
        private const val WRONG_REVIEW_GAP = 3
    }


    init {
        loadQueue()
    }

    fun selectOption(optionId: Long) {
        _uiState.update { state ->
            if (state.answered) return@update state
            val question = state.currentQuestion ?: return@update state
            val newSelection = when (question.question.type) {
                QuestionType.SINGLE_CHOICE, QuestionType.TRUE_FALSE -> setOf(optionId)
                QuestionType.MULTIPLE_CHOICE -> {
                    if (optionId in state.selectedOptionIds) {
                        state.selectedOptionIds - optionId
                    } else {
                        state.selectedOptionIds + optionId
                    }
                }
                QuestionType.FILL_BLANK -> state.selectedOptionIds
            }
            state.copy(selectedOptionIds = newSelection)
        }
    }

    fun submitChoice() {
        val state = _uiState.value
        val question = state.currentQuestion ?: return
        if (state.answered || question.options.isEmpty()) return

        val correctIds = question.options.filter { it.isCorrect }.map { it.id }.toSet()
        val isCorrect = state.selectedOptionIds == correctIds
        recordAnswer(isCorrect)
    }

    fun revealAnswer() {
        _uiState.update { state ->
            if (state.currentQuestion?.question?.type == QuestionType.FILL_BLANK) {
                state.copy(showAnswer = true)
            } else {
                state
            }
        }
    }

    fun selfAssess(correct: Boolean) {
        val state = _uiState.value
        if (state.answered) return
        if (state.currentQuestion?.question?.type != QuestionType.FILL_BLANK) return
        recordAnswer(correct)
    }

    fun next() {
        val state = _uiState.value
        val current = state.currentQuestion ?: return
        val correct = state.lastResultCorrect ?: return
        if (!correct && !state.mastered) {
            wrongQueue.addLast(WrongReviewItem(current, WRONG_REVIEW_GAP + 1))
        }

        advanceWrongQueue()

        val next = nextQuestion()
        if (next == null) {
            _uiState.update {
                it.copy(
                    currentQuestion = null,
                    answered = false,
                    selectedOptionIds = emptySet(),
                    showAnswer = false,
                    lastResultCorrect = null,
                    finished = true,
                    completedToday = true,
                    remainingCount = 0,
                    wrongCount = 0
                )
            }
            viewModelScope.launch {
                repository.markSubjectCompleted(subjectId)
                val records = repository.getSubjectCurrentMonthDailyRecordsOnce(subjectId)
                _uiState.update { it.copy(dailyRecords = records) }
            }
        } else {
            _uiState.update {
                it.copy(
                    currentQuestion = next,
                    answered = false,
                    selectedOptionIds = emptySet(),
                    showAnswer = false,
                    lastResultCorrect = null,
                    mastered = next.question.mastered,
                    remainingCount = normalQueue.size + wrongQueue.size,
                    wrongCount = wrongQueue.size
                )
            }
            questionStartTime = System.currentTimeMillis()
        }
    }

    fun toggleMastered() {
        val state = _uiState.value
        val question = state.currentQuestion ?: return
        if (!state.answered) return
        val newValue = !state.mastered
        _uiState.update { it.copy(mastered = newValue) }
        viewModelScope.launch {
            repository.toggleMastered(question.question.id, newValue)
        }
    }

    fun saveEditedQuestion(
        questionId: Long,
        content: String,
        answer: String?,
        explanation: String,
        options: List<Triple<Long, String, Boolean>>?
    ) {
        viewModelScope.launch {
            repository.updateQuestion(
                questionId = questionId,
                content = content,
                answer = answer,
                explanation = explanation,
                options = options?.map { Triple(it.first, it.second, it.third) }
            )
        }
    }

    fun deleteQuestion(questionId: Long) {
        viewModelScope.launch {
            repository.deleteQuestion(questionId)
            _uiState.update { state ->
                val newQueue = normalQueue.filterNot { it.question.id == questionId }
                normalQueue.clear()
                normalQueue.addAll(newQueue)
                wrongQueue.removeAll { it.question.question.id == questionId }
                if (state.currentQuestion?.question?.id == questionId) {
                    val next = nextQuestion()
                    if (next == null) {
                        state.copy(
                            currentQuestion = null,
                            finished = true,
                            completedToday = true
                        )
                    } else {
                        state.copy(
                            currentQuestion = next,
                            answered = false,
                            selectedOptionIds = emptySet(),
                            showAnswer = false,
                            lastResultCorrect = null,
                            mastered = next.question.mastered,
                            remainingCount = normalQueue.size + wrongQueue.size,
                            wrongCount = wrongQueue.size
                        )
                    }
                } else {
                    state.copy(remainingCount = normalQueue.size + wrongQueue.size, wrongCount = wrongQueue.size)
                }
            }
        }
    }


    private fun loadQueue() {
        viewModelScope.launch {
            val due = repository.getDueQuestionDetailsOnce(subjectId)
            val alreadyCompleted = repository.isSubjectCompletedToday(subjectId)
            val dailyRecords = repository.getSubjectCurrentMonthDailyRecordsOnce(subjectId)

            normalQueue.clear()
            wrongQueue.clear()
            completedInSession.clear()

            if (alreadyCompleted) {
                _uiState.value = ReviewUiState(
                    isLoading = false,
                    finished = true,
                    completedToday = true,
                    dailyRecords = dailyRecords
                )
                questionStartTime = System.currentTimeMillis()
                return@launch
            }

            val record = repository.getOrCreateSubjectDailyRecord(subjectId, due.size)
            repository.ensureDailySubjectQuestions(subjectId, due)
            val statuses = repository.getSubjectDailyQuestions(subjectId)

            val pending = statuses.filter { !it.completed }
            val normalIds = pending.filter { !it.wrongPending }.map { it.questionId }.toSet()
            val wrongIds = pending.filter { it.wrongPending }.map { it.questionId }.toSet()

            val allQuestions = repository.getQuestionDetailsByIds(statuses.map { it.questionId })
            val normalQueueList = allQuestions.filter { it.question.id in normalIds }
            val wrongQueueList = allQuestions.filter { it.question.id in wrongIds }
            val queue = normalQueueList + wrongQueueList

            normalQueue.addAll(queue)

            val shouldMarkCompleted = queue.isEmpty()
            if (shouldMarkCompleted) {
                repository.markSubjectCompleted(subjectId)
            }

            val completed = shouldMarkCompleted
            val first = if (completed) null else nextQuestion()

            _uiState.value = ReviewUiState(
                isLoading = false,
                currentQuestion = first,
                finished = completed || first == null,
                mastered = first?.question?.mastered ?: false,
                completedToday = completed || first == null,
                dailyRecords = dailyRecords,
                remainingCount = normalQueue.size + wrongQueue.size,
                wrongCount = wrongQueue.size
            )
            questionStartTime = System.currentTimeMillis()
        }
    }

    private fun nextQuestion(): QuestionDetail? {
        return if (normalQueue.isNotEmpty()) {
            normalQueue.removeFirst()
        } else {
            wrongQueue.removeFirstOrNull()?.question
        }
    }

    private fun advanceWrongQueue() {
        val ready = mutableListOf<QuestionDetail>()
        val iterator = wrongQueue.iterator()
        while (iterator.hasNext()) {
            val item = iterator.next()
            item.remainingGap--
            if (item.remainingGap <= 0) {
                ready.add(item.question)
                iterator.remove()
            }
        }
        normalQueue.addAll(ready)
    }


    private fun recordAnswer(isCorrect: Boolean) {
        val state = _uiState.value
        val question = state.currentQuestion ?: return
        val responseTime = (System.currentTimeMillis() - questionStartTime).coerceAtLeast(0L)

        viewModelScope.launch {
            withContext(NonCancellable) {
                repository.submitAnswer(
                    questionId = question.question.id,
                    isCorrect = isCorrect,
                    responseTimeMs = responseTime
                )
                if (isCorrect) {
                    repository.markDailyQuestionCompleted(subjectId, question.question.id)
                    if (completedInSession.add(question.question.id)) {
                        repository.incrementSubjectCompletedCount(subjectId)
                    }
                } else {
                    repository.markDailyQuestionWrong(subjectId, question.question.id)
                }
            }
            _uiState.update {
                it.copy(
                    answered = true,
                    lastResultCorrect = isCorrect,
                    mastered = question.question.mastered
                )
            }
        }
    }
}
