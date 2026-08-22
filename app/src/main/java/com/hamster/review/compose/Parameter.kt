package com.hamster.review.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.scale
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.hamster.review.R
import kotlinx.coroutines.launch
import sv.lib.squircleshape.CornerSmoothing
import sv.lib.squircleshape.SquircleShape

val squircleShape = SquircleShape(
    radius = 16.dp,
    smoothing = CornerSmoothing.Medium
)

@Composable
fun outlinedTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = colorResource(id = R.color.mikuGreen),
        unfocusedBorderColor = Color.LightGray,
        errorBorderColor = Color.Red,
        disabledBorderColor = Color.Gray.copy(alpha = 0.5f),
        focusedLabelColor = colorResource(id = R.color.mikuGreen),
        cursorColor = colorResource(id = R.color.mikuGreen)
    )
}

val cursorBrushColor = TextSelectionColors(
    handleColor = Color(0xFF39C5BB),
    backgroundColor = Color(0xFF39C5BB).copy(alpha = 0.25f)
)

@Stable
class SharedTiltState {
    var currentGlobalTouch by mutableStateOf<Offset?>(null)
}

@Composable
fun rememberSharedTiltState() = remember { SharedTiltState() }

fun Modifier.tiltGestureContainer(state: SharedTiltState): Modifier = this.pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull()

            if (change != null && change.pressed) {
                state.currentGlobalTouch = change.position // 获取屏幕绝对坐标
            } else {
                state.currentGlobalTouch = null
            }
        }
    }
}

@Composable
fun Modifier.applySharedTilt(
    state: SharedTiltState,
    maxTilt: Float = 1.5f
): Modifier {
    val rotX = remember { Animatable(0f) }
    val rotY = remember { Animatable(0f) }
    var myBounds by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(state.currentGlobalTouch) {
        val touch = state.currentGlobalTouch
        // 关键判断：全局触摸点是否在这张卡片的全局 Bounds 内
        if (touch != null && myBounds.contains(touch)) {
            val centerX = myBounds.center.x
            val centerY = myBounds.center.y
            val normX = (touch.x - centerX) / (myBounds.width / 2f)
            val normY = (touch.y - centerY) / (myBounds.height / 2f)

            launch { rotX.snapTo(normY * -maxTilt) }
            launch { rotY.snapTo(normX * maxTilt) }
        } else {
            if (rotX.value != 0f || rotY.value != 0f) {
                launch { rotX.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
                launch { rotY.animateTo(0f, spring(stiffness = Spring.StiffnessMedium)) }
            }
        }
    }

    return this
        .onGloballyPositioned { coordinates ->
            // 获取卡片在整个屏幕上的绝对位置。即使外层有 Scroll 滚动，这个位置也会实时更新！
            myBounds = coordinates.boundsInRoot()
        }
        .graphicsLayer {
            rotationX = rotX.value
            rotationY = rotY.value
            cameraDistance = 12f * density
        }
}

fun Modifier.glow(
    color: Color,
    blurRadius: Dp = 15.dp,
    spread: Dp = 0.dp,
    shape: Shape
) = this.drawBehind {
    val shadowColor = color.toArgb()
    val transparentColor = color.copy(alpha = 0f).toArgb()

    val spreadPx = spread.toPx()
    val blurPx = blurRadius.toPx()

    this.drawIntoCanvas { canvas ->
        val paint = Paint()
        val frameworkPaint = paint.asFrameworkPaint()

        frameworkPaint.color = transparentColor // 形状本身透明
        frameworkPaint.setShadowLayer(
            blurPx,
            0f,
            0f,
            shadowColor
        )

        canvas.save()
        if (spreadPx > 0f) {
            val scaleX = (size.width + 2 * spreadPx) / size.width
            val scaleY = (size.height + 2 * spreadPx) / size.height

            canvas.scale(scaleX, scaleY, center.x, center.y)
        }

        val outline = shape.createOutline(size, layoutDirection, this@drawBehind)
        canvas.drawOutline(outline, paint)

        canvas.restore()
    }
}

@Composable
fun rememberStringPreference(key: String, default: String = ""): MutableState<String> {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val state = remember { mutableStateOf(prefs.getString(key, default) ?: default) }

    return remember(state) {
        object : MutableState<String> {
            override var value: String
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    prefs.edit { putString(key, newValue) }
                }
            override fun component1() = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberIntPreference(key: String, default: Int = 0): MutableState<Int> {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val state = remember { mutableIntStateOf(prefs.getInt(key, default)) }

    return remember(state) {
        object : MutableState<Int> {
            override var value: Int
                get() = state.intValue
                set(newValue) {
                    state.intValue = newValue
                    prefs.edit { putInt(key, newValue) }
                }
            override fun component1() = value
            override fun component2(): (Int) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberFloatPreference(key: String, default: Float = 0f): MutableState<Float> {
    val context = LocalContext.current
    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }

    val state = remember { mutableFloatStateOf(prefs.getFloat(key, default)) }

    return remember(state) {
        object : MutableState<Float> {
            override var value: Float
                get() = state.floatValue
                set(newValue) {
                    state.floatValue = newValue
                    prefs.edit { putFloat(key, newValue) }
                }
            override fun component1() = value
            override fun component2(): (Float) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberBooleanPreference(key: String, default: Boolean = false): MutableState<Boolean> {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    val state = remember { mutableStateOf(prefs.getBoolean(key, default)) }

    return remember(state) {
        object : MutableState<Boolean> {
            override var value: Boolean
                get() = state.value
                set(newValue) {
                    state.value = newValue
                    prefs.edit { putBoolean(key, newValue) }
                }

            override fun component1() = value
            override fun component2(): (Boolean) -> Unit = { value = it }
        }
    }
}