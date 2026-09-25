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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** 更新题库进度状态：text=当前操作，progress=0f..1f，cancelable=是否可取消。 */
    data class BankUpdateState(
        val text: String,
        val progress: Float,
        val cancelable: Boolean
    )

    private var _bankUpdateBusy by mutableStateOf(false)

    private var _bankUpdateState by mutableStateOf<BankUpdateState?>(null)
    val bankUpdateState: BankUpdateState?
        get() = _bankUpdateState

    private var bankUpdateJob: Job? = null

    fun updateOfficialBank() {
        if (_bankUpdateBusy) return
        _bankUpdateBusy = true
        _bankUpdateState = BankUpdateState("正在检查题库版本", 0f, cancelable = true)
        bankUpdateJob = viewModelScope.launch {
            try {
                val message = repository.updateOfficialBankFromGitHub(getApplication()) { text, progress, cancelable ->
                    // 进度回调来自 IO 线程，切回主线程更新状态
                    withContext(Dispatchers.Main) {
                        _bankUpdateState = BankUpdateState(text, progress, cancelable)
                    }
                }
                Toast.makeText(getApplication(), message, Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                Toast.makeText(getApplication(), "已取消题库更新", Toast.LENGTH_SHORT).show()
                throw e
            } finally {
                _bankUpdateState = null
                _bankUpdateBusy = false
                bankUpdateJob = null
            }
        }
    }

    /** 用户取消本次题库更新（下载/校验阶段生效；写入阶段 cancelable=false）。 */
    fun cancelBankUpdate() {
        bankUpdateJob?.cancel()
    }



    private var _topbarTitle by mutableStateOf("首页")
    var topbarTitle: String
        get() = _topbarTitle
        set(title) {
            _topbarTitle = title
        }

    /** 顶栏环形进度（null = 不显示），仅 ReviewScreen 在栈上时由页面写入。 */
    private var _reviewProgress by mutableStateOf<Float?>(null)
    val reviewProgress: Float?
        get() = _reviewProgress

    fun setReviewProgress(value: Float?) {
        _reviewProgress = value
    }

    /** 首页科目卡片是否展开为列表（会话内保持）。 */
    private var _homeExpanded by mutableStateOf(false)
    val homeExpanded: Boolean
        get() = _homeExpanded

    fun setHomeExpanded(expanded: Boolean) {
        _homeExpanded = expanded
    }

    /** 首页堆叠模式下当前顶层科目 id（会话内保持）。 */
    private var _homeTopSubjectId by mutableStateOf<Long?>(null)
    val homeTopSubjectId: Long?
        get() = _homeTopSubjectId

    fun setHomeTopSubjectId(id: Long?) {
        _homeTopSubjectId = id
    }
}