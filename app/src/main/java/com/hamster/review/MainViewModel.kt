package com.hamster.review

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hamster.review.data.db.AppDatabase
import com.hamster.review.data.db.SubjectWithTodayCount
import com.hamster.review.data.repository.ReviewRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ReviewRepository(AppDatabase.getInstance(application))

    val subjects: StateFlow<List<SubjectWithTodayCount>> = repository
        .observeSubjectsWithTodayCount()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            repository.seedIfEmpty()
            repository.cleanupOldReviewLogs()
        }
    }

    private var _topbarTitle by mutableStateOf("首页")
    var topbarTitle: String
        get() = _topbarTitle
        set(title) {
            _topbarTitle = title
        }
}
