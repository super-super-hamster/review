package com.hamster.review.viewModel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.hamster.review.data.db.AppDatabase
import com.hamster.review.data.db.QuestionDetail
import com.hamster.review.data.repository.ReviewRepository
import kotlinx.coroutines.launch

class ManageQuestionsViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    val subjectId: Long = checkNotNull(savedStateHandle["subjectId"])

    private val repository = ReviewRepository(AppDatabase.getInstance(application))

    var questions by mutableStateOf<List<QuestionDetail>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    fun refresh() {
        viewModelScope.launch {
            questions = repository.getSubjectQuestionDetails(subjectId)
            loading = false
        }
    }
}
