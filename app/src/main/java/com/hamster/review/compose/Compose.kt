package com.hamster.review.compose

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import com.airbnb.lottie.LottieProperty
import com.airbnb.lottie.SimpleColorFilter
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.airbnb.lottie.compose.rememberLottieDynamicProperties
import com.airbnb.lottie.compose.rememberLottieDynamicProperty
import com.hamster.review.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ItemGroup(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    titleState: SharedTiltState,
    color: Color = colorResource(id = R.color.item_group_card),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier) {
        Card(
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, top = 1.dp, bottom = 1.dp)
                .applySharedTilt(titleState)
                .shadow(
                    elevation = 2.dp,
                    shape = squircleShape,
                    clip = false, // 防止内容被裁掉
                    spotColor = colorResource(id = R.color.item_group_card_shadow),  // 主阴影
                    ambientColor = colorResource(id = R.color.item_group_card_shadow)// 柔和阴影
                ),
            colors = CardDefaults.cardColors(containerColor = color),
            shape = squircleShape
        ) {
            Column(modifier = contentModifier.fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
fun PageColumn(
    modifier: Modifier = Modifier,
    sharedTiltState: SharedTiltState,
    content: @Composable ColumnScope.() -> Unit //@Composable ColumnScope.() 让content知道自己的父组件是column
) {
    Box(modifier = Modifier.fillMaxSize().background(colorResource(id = R.color.background)).tiltGestureContainer(sharedTiltState)) {
        Column(
            modifier = modifier
                .fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.top_padding)))

            content()

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.bottom_padding)))
        }
    }
}

@Composable
fun VerticalScrollPageColumn(
    modifier: Modifier = Modifier,
    sharedTiltState: SharedTiltState,
    scrollTrigger: Int,
    scrollTarget: String?,
    content: @Composable ColumnScope.() -> Unit,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val targets = remember { mutableMapOf<String, ScrollTarget>() }

    fun scrollTo(id: String?) {
        if (!id.isNullOrEmpty()) {
            coroutineScope.launch {
                delay(400)
                targets[id]?.scrollTo(context)
            }
        }
    }

    LaunchedEffect(scrollTrigger) {
        scrollTo(scrollTarget)
    }

    CompositionLocalProvider(LocalScrollTargets provides targets) {
        PageColumn(
            modifier = modifier.verticalScroll(rememberScrollState()),
            sharedTiltState = sharedTiltState
        ) {
            content()
        }
    }
}
// TODO:fuuuuuuuck
val LocalScrollTargets = compositionLocalOf<MutableMap<String, ScrollTarget>> {
    error("只能在 VerticalScrollPageColumn 内部使用此 Modifier")
}

fun Modifier.scrollTargetId(id: String): Modifier = composed {
    val targets = LocalScrollTargets.current
    val targetModifier = remember(id) { targets.getOrPut(id) { ScrollTarget() }.modifier }
    this.then(targetModifier)
}

@Composable
fun RingProgress(
    progress: Float, // 范围是 0f ~ 1f
    modifier: Modifier = Modifier,
    ringColor: Color = colorResource(R.color.mikuGreen),
    trackColor: Color = colorResource(R.color.light_gray),
    strokeWidth: Dp = 24.dp,
    size: Dp = 256.dp,
    text: String = "",
    textFontSize: TextUnit = 40.sp,
    onClick: () -> Unit
) {
    val animatedProgress = remember { Animatable(progress) }

    LaunchedEffect(progress) {
        val delta = abs(progress - animatedProgress.value)

        val maxDuration = 500f
        val minDuration = 200f

        val calculatedDuration = (maxDuration - delta * (maxDuration - minDuration)).toInt()
            .coerceIn(minDuration.toInt(), maxDuration.toInt())

        animatedProgress.animateTo(
            targetValue = progress,
            animationSpec = tween(
                durationMillis = calculatedDuration,
                easing = FastOutSlowInEasing
            )
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onClick() }
            ),
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .padding(strokeWidth / 2)
        ) {
            val strokeWidthPx = strokeWidth.toPx()

            drawCircle(
                color = trackColor,
                style = Stroke(width = strokeWidthPx)
            )

            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress.value,
                useCenter = false,
                style = Stroke(
                    width = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            )
        }

        Text(text = text, fontSize = textFontSize, fontWeight = FontWeight.Normal)
    }
}

@Composable
fun ProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    progressColor: Color = colorResource(R.color.mikuGreen),
    trackColor: Color = colorResource(R.color.light_gray),
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 500),
    )

    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = animatedProgress)
                .background(progressColor)
        )
    }
}

@Composable
fun TextInputField(
    value: String,
    onValueChange: (String) -> Unit,
    textStyle: TextStyle = TextStyle.Default,
    singleLine: Boolean = false,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit =
        @Composable { innerTextField -> innerTextField() },
    modifier: Modifier = Modifier,
) {
    CompositionLocalProvider(LocalTextSelectionColors provides cursorBrushColor) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            modifier = modifier,
            singleLine = singleLine,
            decorationBox = decorationBox,
            cursorBrush = SolidColor(colorResource(R.color.mikuGreen))
        )
    }
}

@Composable
fun ButtonPro(
    @DrawableRes icon: Int,
    onTap: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
) {
    val viewConfiguration = LocalViewConfiguration.current

    Box(
        modifier = Modifier.height(48.dp).width(72.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(
                    elevation = 2.dp,
                    shape = squircleShape,
                    spotColor = Color.Black,
                    ambientColor = Color.Black,
                )
                .background(
                    color = colorResource(R.color.mikuGreen),
                    shape = squircleShape
                )

                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()

                        val upEvent = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation()
                        }
                        if (upEvent != null) {
                            onTap()
                        } else {
                            onLongPressStart()
                            waitForUpOrCancellation()
                            onLongPressEnd()
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = "universal",
                modifier = Modifier.size(20.dp),
                tint = colorResource(R.color.icon)
            )
        }
    }
}

@Composable
fun AnimationButton(
    modifier: Modifier = Modifier,
    changed: Boolean,
    @RawRes animation: Int = 0,
    onClick: (Boolean) -> Unit
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(animation))

    val progress by animateFloatAsState(
        targetValue = if (!changed) 0.5f else 0f,
        animationSpec = tween(durationMillis = 800),
        label = "LottieProgress"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(changed) }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            contentScale = ContentScale.Crop,
            dynamicProperties = rememberLottieDynamicProperties( // 所有图层染色
                rememberLottieDynamicProperty(
                    property = LottieProperty.COLOR_FILTER,
                    value = SimpleColorFilter(colorResource(R.color.sheet_button).toArgb()),
                    keyPath = arrayOf("**")
                )
            ),
            modifier = Modifier
                .height(40.dp)
                .width(80.dp)
        )
    }
}

@Composable
fun BlurEffect(blurRadius: Int = 24, blurDimAmount: Float = 0f, fallbackDimAmount: Float = 0.6f) {
    val context = LocalContext.current
    val view = LocalView.current

    val activityManager = remember(context) {
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    }

    LaunchedEffect(Unit) {
        val dialogWindow = (view.parent as? DialogWindowProvider)?.window
        dialogWindow?.let { window ->
            val isBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !activityManager.isLowRamDevice
            if (isBlur) {
                window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply {
                    blurBehindRadius = blurRadius
                    dimAmount = blurDimAmount
                }
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                window.attributes = window.attributes.apply {
                    dimAmount = fallbackDimAmount
                }
            }
        }
    }
}

@Composable
fun OptionAnimItem(
    modifier: Modifier = Modifier,
    title: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: () -> Unit
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.check_anim))

    // 控制动画进度 (这里设定选中时进度为 1f，未选中时为 0f。如果您使用的动画满进度为 0.5f，请自行修改 targetValue)
    val progress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 300), // 动画时长500ms
        label = "LottieProgress"
    )

    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onCheckedChange
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            fontSize = 20.sp,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )

        LottieAnimation(
            composition = composition,
            progress = { progress },
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(28.dp)
                .alpha(if (enabled) 1f else 0.5f)
        )
    }
}