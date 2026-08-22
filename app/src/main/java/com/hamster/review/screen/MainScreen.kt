package com.hamster.review.screen

import androidx.compose.runtime.Composable
import com.hamster.review.compose.PageColumn
import com.hamster.review.compose.rememberSharedTiltState

@Composable
fun MainScreen() {
    val sharedTiltState = rememberSharedTiltState()

    PageColumn(sharedTiltState = sharedTiltState) {

    }
}