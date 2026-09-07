package com.hamster.review.viewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.hamster.review.data.db.AppDatabase
import com.hamster.review.data.model.QuestionType
import com.hamster.review.data.repository.ReviewRepository
import kotlinx.coroutines.launch

class AddQuestionViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val subjectId: Long = checkNotNull(savedStateHandle["subjectId"])
    val type: QuestionType = QuestionType.valueOf(checkNotNull(savedStateHandle["typeName"]))

    private val repository = ReviewRepository(AppDatabase.getInstance(application))

    fun saveQuestion(
        content: String,
        answer: String?,
        options: List<Pair<String, Boolean>>,
        onDone: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val id = repository.addQuestion(
                subjectId = subjectId,
                type = type,
                content = content,
                answer = if (type == QuestionType.FILL_BLANK) answer else null,
                options = options
            )
            onDone(id)
        }
    }
}