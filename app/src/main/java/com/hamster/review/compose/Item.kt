package com.hamster.review.compose

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.hamster.review.R

@Composable
fun ClickItem(
    modifier: Modifier = Modifier,
    title: String,
    summary: String = "",
    @DrawableRes icon: Int = 0,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != 0) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier
                    .width(24.dp),
                tint = colorResource(R.color.icon)
            )

            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        ) {
            Text(text = title, fontSize = 16.sp, color = colorResource(id = R.color.text))
            if (summary.isNotEmpty()) {
                Text(text = summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Icon(
            painter = painterResource(id = R.drawable.arrow_right_bold),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colorResource(R.color.icon)
        )
    }

    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun SwitchItem(
    modifier: Modifier = Modifier,
    title: String,
    summary: String = "",
    checked: Boolean,
    enabled: Boolean = true,
    @DrawableRes icon: Int = 0,
    onCheckedChange: (Boolean) -> Unit
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.toggle_button_anim))

    // 控制动画进度
    val progress by animateFloatAsState(
        targetValue = if (checked) 0.5f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "LottieProgress"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 0.dp, top = 16.dp, bottom = 16.dp)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null, // 去掉点击的视觉效果
                onClick = { onCheckedChange(!checked) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != 0) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier
                    .width(24.dp),
                tint = colorResource(R.color.icon)
            )

            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                fontSize = 16.sp,
                color = if (enabled) colorResource(id = R.color.text) else colorResource(id = R.color.text).copy(alpha = 0.38f)
            )
            if (summary.isNotEmpty()) {
                Text(text = summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        LottieAnimation(
            composition = composition,
            progress = { progress },
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .height(40.dp)
                .width(80.dp)
                .alpha(if (enabled) 1f else 0.5f)
        )
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
fun EditTextItem(
    modifier: Modifier = Modifier,
    title: String,
    summary: String = "",
    dialogTitle: String,
    initialValue: String,
    hint: String = "",
    singleLine: Boolean = false,
    maxLength: Int = -1,
    @DrawableRes icon: Int = 0,
    onCancel: () -> Unit = {},
    onConfirm: (String) -> Boolean // 返回true关闭窗口，返回false不关闭
) {
    var showDialog by remember { mutableStateOf(false) }

    ClickItem(title = title, summary = summary, modifier = modifier, icon = icon) {
        showDialog = true
    }

    if (showDialog) {
        EditTextDialog(
            title = dialogTitle,
            initialValue = initialValue,
            hint = hint,
            singleLine = singleLine,
            maxLength = maxLength,
            onCancel = onCancel,
            onDismissRequest = { showDialog = false },
            onConfirm = onConfirm
        )
    }
}

@Composable
fun SliderItem(
    modifier: Modifier = Modifier,
    title: String,
    summary: String = "",
    @DrawableRes icon: Int = 0,
    dialogTitle: String,
    dialogContent: String = "",
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onCancel: () -> Unit = {},
    onConfirm: () -> Boolean,
    cancelText: String = "取消",
    confirmText: String = "确认"
) {
    var showDialog by remember { mutableStateOf(false) }

    ClickItem(title = title, summary = summary, modifier = modifier, icon = icon) {
        showDialog = true
    }

    if (showDialog) {
        SliderDialog(
            title = dialogTitle,
            content = dialogContent,
            value = value,
            onValueChange = { onValueChange(it) },
            valueRange = valueRange,
            onCancel = { onCancel() },
            onConfirm = { onConfirm() },
            onDismissRequest = { showDialog = false },
            cancelText = cancelText,
            confirmText = confirmText
        )
    }
}

@Composable
fun InquiryItem(
    modifier: Modifier = Modifier,
    title: String,
    summary: String,
    dialogTitle: String,
    dialogContent: String,
    cancelText: String = "取消",
    confirmText: String = "确认",
    @DrawableRes icon: Int = 0,
    onCancel: () -> Unit = {},
    onConfirm: () -> Boolean
) {
    var showDialog by remember { mutableStateOf(false) }

    ClickItem(title = title, summary = summary, modifier = modifier, icon = icon) {
        showDialog = true
    }

    if (showDialog) {
        InquiryDialog(
            title = dialogTitle,
            content = dialogContent,
            cancelText = cancelText,
            confirmText = confirmText,
            onCancel = onCancel,
            onConfirm = onConfirm,
            onDismissRequest = { showDialog = false }
        )
    }
}

@Composable
fun ExplanationItem(
    modifier: Modifier = Modifier,
    title: String,
    content: String,
    buttonContent: String? = null,
    onButtonClick: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = squircleShape,
        colors = CardDefaults.cardColors(containerColor = colorResource(id = R.color.bg_dialog)),
        elevation = CardDefaults.cardElevation(8.dp)
    ) {
        Column (
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_tips),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp).padding(end = 8.dp)
                )

                Text(
                    text = title,
                    fontSize = 24.sp,
                    lineHeight = 32.sp,
                    color = colorResource(id = R.color.text)
                )
            }


            Text(
                text = content,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = colorResource(id = R.color.explanation_text),
                modifier = Modifier.padding(4.dp)
            )

            buttonContent?.let {
                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 4.dp),
                    border = BorderStroke(1.dp, Color.LightGray),
                    shape = squircleShape,
                    colors = ButtonDefaults.textButtonColors(colorResource(R.color.btn_confirm).copy(alpha = 0.8f)),
                    onClick = onButtonClick,
                ) {
                    Text(text = it, color = colorResource(R.color.text))
                }
            }
        }
    }
}