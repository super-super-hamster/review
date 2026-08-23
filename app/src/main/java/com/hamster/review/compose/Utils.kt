package com.hamster.review.compose

import android.content.Context
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.hamster.review.R
import kotlinx.coroutines.delay

class ScrollTarget {
    val requester = BringIntoViewRequester()
    val colorAnimatable = Animatable(Color.Transparent)

    val modifier = Modifier
        .bringIntoViewRequester(requester)
        .drawWithContent {
            drawContent()
            drawRect(color = colorAnimatable.value)
        }

    suspend fun scrollTo(context: Context) {
        try {
            requester.bringIntoView()

            delay(100)

            val highlightColor = Color(ColorUtils.setAlphaComponent(ContextCompat.getColor(context, R.color.mikuGreen), 64))
            repeat(2) {
                colorAnimatable.animateTo(highlightColor, animationSpec = tween(300))
                colorAnimatable.animateTo(Color.Transparent, animationSpec = tween(300))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}