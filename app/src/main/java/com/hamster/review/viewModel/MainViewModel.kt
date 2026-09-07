package com.hamster.review.viewModel

import android.app.Application
import android.widget.Toast
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

    fun setSubjectDailyLimit(subjectId: Long, dailyLimit: Int) {
        viewModelScope.launch {
            repository.setSubjectDailyLimit(subjectId, dailyLimit)
        }
    }

    fun addSubject(name: String) {
        viewModelScope.launch {
            repository.addSubject(name)
        }
    }

    fun deleteSubject(subjectId: Long) {
        viewModelScope.launch {
            repository.deleteSubject(subjectId)
        }
    }

    private var _bankUpdateBusy by mutableStateOf(false)

    /** 是否正在更新官方题库。 */
    val bankUpdateBusy: Boolean
        get() = _bankUpdateBusy

    /** 手动更新官方题库(GitHub Release)，结果用 Toast 提示。 */
    fun updateOfficialBank() {
        if (_bankUpdateBusy) return
        viewModelScope.launch {
            _bankUpdateBusy = true
            try {
                val message = repository.updateOfficialBankFromGitHub(getApplication())
                Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
            } finally {
                _bankUpdateBusy = false
            }
        }
    }


    private var _topbarTitle by mutableStateOf("首页")
    var topbarTitle: String
        get() = _topbarTitle
        set(title) {
            _topbarTitle = title
        }
}