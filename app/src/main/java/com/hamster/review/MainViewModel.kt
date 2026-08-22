package com.hamster.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class MainViewModel : ViewModel() {
    private var _topbarTitle by mutableStateOf("首页")
    var topbarTitle: String
        get() = _topbarTitle
        set(title) {
            _topbarTitle = title
        }
}