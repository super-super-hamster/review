package com.hamster.review.viewModel

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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

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
    val canAnotherGroup: Boolean = false,
    val dailyRecords: List<DailyRecordEntity> = emptyList(),
    val remainingCount: Int = 0,
    val wrongCount: Int = 0,
    /** 已掌握测试中答错、被自动置为未掌握的题目数 */
    val unmasteredCount: Int = 0,
    /** 已掌握测试的题目总数（0 表示该科目没有已掌握题目） */
    val testTotal: Int = 0
)

class ReviewViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val subjectId: Long = checkNotNull(savedStateHandle["subjectId"])
    private val mode: String = savedStateHandle.get<String>("mode") ?: "daily"

    /** 是否为"已掌握题目测试"模式：不读写每日数据，答错仅置为未掌握。 */
    val isMasteredTest: Boolean = mode == "mastered_test"

    private val repository = ReviewRepository(AppDatabase.getInstance(application))

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private val _masteredTestProgress = MutableStateFlow(0f)
    private var masteredTestTotal = 0
    private var masteredTestAnswered = 0

    /**
     * 顶栏环形进度：
     * - 每日模式：与首页进度条同口径（已完成 / min(dailyLimit, 未掌握题数)）；
     * - 测试模式：本次测试进度（已答题数 / 已掌握题总数）。
     */
    val todayProgress: StateFlow<Float> = if (isMasteredTest) {
        _masteredTestProgress.asStateFlow()
    } else {
        repository
            .observeSubjectsWithTodayCount()
            .map { list -> list.firstOrNull { it.subject.id == subjectId } }
            .map { subject ->
                if (subject == null) {
                    0f
                } else {
                    val target = min(subject.subject.dailyLimit, subject.availableCount)
                    if (target <= 0) {
                        0f
                    } else {
                        val remaining = subject.todayCount.coerceIn(0, target)
                        ((target - remaining).toFloat() / target).coerceIn(0f, 1f)
                    }
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = 0f
            )
    }

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
        // 测试模式答错不回队重答，仅置为未掌握
        if (!isMasteredTest && !correct && !state.mastered) {
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
                    completedToday = !isMasteredTest,
                    remainingCount = 0,
                    wrongCount = 0
                )
            }
            if (isMasteredTest) {
                if (masteredTestTotal > 0) _masteredTestProgress.value = 1f
            } else {
                viewModelScope.launch {
                    repository.markSubjectCompleted(subjectId)
                    val records = repository.getSubjectCurrentMonthDailyRecordsOnce(subjectId)
                    _uiState.update { it.copy(dailyRecords = records) }
                    refreshCanAnotherGroup()
                }
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
                            completedToday = !isMasteredTest
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


    /**
     * 完成当天学习后「再来一组」：补一组当天还没刷过的题并回到答题状态。
     */
    fun startAnotherGroup() {
        // 立即隐藏按钮，避免加载期间被重复点击
        _uiState.update { it.copy(canAnotherGroup = false) }
        viewModelScope.launch {
            val pushed = repository.startAnotherGroup(subjectId)
            if (pushed > 0) {
                loadQueue()
            } else {
                // 没有可推的新候选，维持完成态并刷新按钮显隐
                refreshCanAnotherGroup()
            }
        }
    }

    private fun refreshCanAnotherGroup() {
        viewModelScope.launch {
            val can = repository.hasMorePoolCandidates(subjectId)
            _uiState.update { it.copy(canAnotherGroup = can) }
        }
    }

    private fun loadQueue() {
        if (isMasteredTest) {
            loadMasteredTestQueue()
            return
        }
        viewModelScope.launch {
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
                refreshCanAnotherGroup()
                questionStartTime = System.currentTimeMillis()
                return@launch
            }

            // 每天第一次进入时生成当天题目池；已有推送行则沿用（含错题待重答状态）
            repository.prepareTodayPool(subjectId)
            val statuses = repository.getSubjectDailyQuestions(subjectId)

            val pending = statuses.filter { !it.completed }
            val allQuestions = repository.getQuestionDetailsByIds(statuses.map { it.questionId })
            val questionsById = allQuestions.associateBy { it.question.id }

            // 保持插入顺序（到期题在前、新题在后）
            normalQueue.addAll(
                pending
                    .filter { !it.wrongPending }
                    .mapNotNull { questionsById[it.questionId] }
            )
            normalQueue.addAll(
                pending
                    .filter { it.wrongPending }
                    .mapNotNull { questionsById[it.questionId] }
            )

            val shouldMarkCompleted = normalQueue.isEmpty()
            if (shouldMarkCompleted) {
                repository.markSubjectCompleted(subjectId)
                refreshCanAnotherGroup()
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

    /** 已掌握题目测试：取全部已掌握题目并随机打乱，不涉及任何每日数据。 */
    private fun loadMasteredTestQueue() {
        viewModelScope.launch {
            normalQueue.clear()
            wrongQueue.clear()
            completedInSession.clear()

            val mastered = repository.getMasteredQuestionsOnce(subjectId).shuffled()
            masteredTestTotal = mastered.size
            masteredTestAnswered = 0
            _masteredTestProgress.value = 0f

            normalQueue.addAll(mastered)
            val first = nextQuestion()

            _uiState.value = ReviewUiState(
                isLoading = false,
                currentQuestion = first,
                finished = first == null,
                mastered = first?.question?.mastered ?: false,
                completedToday = false,
                remainingCount = normalQueue.size + wrongQueue.size,
                wrongCount = 0,
                unmasteredCount = 0,
                testTotal = mastered.size
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

        if (isMasteredTest) {
            viewModelScope.launch {
                // 不写日志/不更新调度/不计入每日统计；答错仅置为未掌握
                if (!isCorrect) {
                    repository.toggleMastered(question.question.id, false)
                }
                masteredTestAnswered++
                _masteredTestProgress.value = if (masteredTestTotal <= 0) {
                    0f
                } else {
                    (masteredTestAnswered.toFloat() / masteredTestTotal).coerceIn(0f, 1f)
                }
                _uiState.update {
                    it.copy(
                        answered = true,
                        lastResultCorrect = isCorrect,
                        mastered = if (isCorrect) question.question.mastered else false,
                        unmasteredCount = it.unmasteredCount + if (isCorrect) 0 else 1
                    )
                }
            }
            return
        }

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
