package com.hamster.review.compose

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.unit.IntOffset

// 进场动画
fun slideInWithScaleEnter(): EnterTransition {
    val duration = 200
    val delay = 75
    val offsetSpec = tween<IntOffset>(durationMillis = duration, delayMillis = delay)
    val floatSpec = tween<Float>(durationMillis = duration, delayMillis = delay)

    return slideInHorizontally(
        initialOffsetX = { fullWidth -> fullWidth },
        animationSpec = offsetSpec
    ) + scaleIn(
        initialScale = 0.9f,
        animationSpec = floatSpec
    )
}

// 出场动画
fun scaleOutExit(): ExitTransition {
    val duration = 200
    val floatSpec = tween<Float>(durationMillis = duration, delayMillis = 0)

    return scaleOut(
        targetScale = 0.85f,
        animationSpec = floatSpec
    )
}

// 返回-进场动画：旧页面从背景放大回来
fun scaleInPopEnter(): EnterTransition {
    val duration = 200
    val floatSpec = tween<Float>(durationMillis = duration)

    return scaleIn(
        initialScale = 0.85f,
        animationSpec = floatSpec
    )
}

// 返回-出场动画：当前页面向右滑出 + 缩小
fun slideOutWithScalePopExit(): ExitTransition {
    val duration = 200
    val offsetSpec = tween<IntOffset>(durationMillis = duration)
    val floatSpec = tween<Float>(durationMillis = duration)

    return slideOutHorizontally(
        targetOffsetX = { fullWidth -> fullWidth },
        animationSpec = offsetSpec
    ) + scaleOut(
        targetScale = 0.9f,
        animationSpec = floatSpec
    )
}