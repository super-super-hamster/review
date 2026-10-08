package com.hamster.review.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.hamster.review.data.db.AppDatabase
import com.hamster.review.data.model.QuestionType
import com.hamster.review.data.repository.ReviewRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class QuestionEditorSeed(
    val id: Long,
    val content: String,
    val answer: String,
    val explanation: String,
    val optionIds: List<Long>,
    val optionTexts: List<String>,
    val optionCorrect: List<Boolean>
)

class AddQuestionViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val subjectId: Long = checkNotNull(savedStateHandle["subjectId"])
    val type: QuestionType = QuestionType.valueOf(checkNotNull(savedStateHandle["typeName"]))

    private val questionId: Long = savedStateHandle.get<Long>("questionId") ?: 0L

    val isEdit: Boolean = questionId > 0L

    private val repository = ReviewRepository(AppDatabase.getInstance(application))

    private val _seed = MutableStateFlow<QuestionEditorSeed?>(null)
    val seed: StateFlow<QuestionEditorSeed?> = _seed.asStateFlow()

    private val _loading = MutableStateFlow(isEdit)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        if (isEdit) {
            viewModelScope.launch {
                val detail = repository.getQuestionDetailOnce(questionId)
                if (detail != null) {
                    val orderedOptions = detail.options.sortedBy { it.sortOrder }
                    _seed.value = QuestionEditorSeed(
                        id = detail.question.id,
                        content = detail.question.content,
                        answer = detail.question.answer.orEmpty(),
                        explanation = detail.question.explanation,
                        optionIds = orderedOptions.map { it.id },
                        optionTexts = orderedOptions.map { it.content },
                        optionCorrect = orderedOptions.map { it.isCorrect }
                    )
                }
                _loading.value = false
            }
        }
    }

    fun saveQuestion(
        content: String,
        answer: String,
        options: List<Pair<String, Boolean>>,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            if (isEdit) {
                val current = _seed.value
                if (current == null) {
                    onDone(false)
                    return@launch
                }
                val optionTriples = options.mapIndexedNotNull { index, (text, correct) ->
                    current.optionIds.getOrNull(index)?.let { Triple(it, text, correct) }
                }
                repository.updateQuestion(
                    questionId = current.id,
                    content = content,
                    answer = if (type == QuestionType.FILL_BLANK) answer else current.answer,
                    explanation = current.explanation,
                    options = optionTriples
                )
                onDone(true)
            } else {
                val id = repository.addQuestion(
                    subjectId = subjectId,
                    type = type,
                    content = content,
                    answer = if (type == QuestionType.FILL_BLANK) answer else null,
                    options = options
                )
                onDone(id > 0)
            }
        }
    }
}
