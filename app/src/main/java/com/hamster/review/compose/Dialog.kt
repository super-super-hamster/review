package com.hamster.review.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hamster.review.R
import kotlinx.coroutines.delay

@Composable
fun EditTextDialog(
    title: String,
    initialValue: String,
    hint: String = "",
    singleLine: Boolean = false,
    maxLength: Int = -1,
    type: String = "String",
    validate: (String) -> String? = { null },
    onCancel: () -> Unit = {},
    onDismissRequest: () -> Unit,
    onConfirm: (String) -> Boolean
) {
    var tempText by remember(initialValue) {
        mutableStateOf(
            TextFieldValue(
                text = initialValue,
                selection = TextRange(initialValue.length)
            )
        )
    }
    var errorText by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current
    val dialogMaxHeight = with(density) { (windowInfo.containerSize.height * 0.6f).toDp() }

    // 在组件加载时触发请求焦点
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    val submitAction = {
        val error = validate(tempText.text)
        if (error != null) {
            errorText = error
        } else if (onConfirm(tempText.text)) {
            onDismissRequest()
        }
    }

    val cancelAction = {
        tempText = TextFieldValue(
            text = initialValue,
            selection = TextRange(initialValue.length)
        )
        onCancel()
        onDismissRequest()
    }

    StandardDialog(
        onDismissRequest = cancelAction,
        modifier = Modifier.heightIn(max = dialogMaxHeight) // 限制最大高度
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(0.85f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(20.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                errorText?.let { message ->
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        color = Color.Red,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            OutlinedTextField(
                value = tempText,
                onValueChange = { input ->
                    val inputText = input.text
                    if (maxLength == -1 || inputText.length <= maxLength) {
                        when (type) {
                            "Int" -> {
                                if (inputText.all { it.isDigit() }) {
                                    tempText = input
                                }
                            }
                            "Float" -> {
                                if (inputText.matches(Regex("^\\d*\\.?\\d*$"))) {
                                    tempText = input
                                }
                            }
                            else -> tempText = input
                        }
                    }
                },
                placeholder = { Text(text = hint, color = Color.Gray) },
                singleLine = singleLine,
                supportingText = if (maxLength != -1) {
                    {
                        Text(
                            text = "${tempText.text.length} / $maxLength",
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End,
                            color =Color.Gray
                        )
                    }
                } else null,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false) // 弹性高度
                    .focusRequester(focusRequester), // 焦点
                keyboardOptions = KeyboardOptions(
                    imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
                    keyboardType = if (type == "String") KeyboardType.Text else KeyboardType.Number
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (singleLine) submitAction() }
                ),
                shape = squircleShape,
                colors = outlinedTextFieldColors()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    border = BorderStroke(1.dp, Color.LightGray),
                    shape = squircleShape,
                    colors = ButtonDefaults.textButtonColors(Color.Transparent),
                    onClick = cancelAction
                ) {
                    Text("取消", color = Color.Gray)
                }
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    border = BorderStroke(1.dp, Color.LightGray),
                    shape = squircleShape,
                    colors = ButtonDefaults.textButtonColors(colorResource(R.color.btn_confirm)),
                    onClick = submitAction
                ) {
                    Text("确定", color = colorResource(R.color.text))
                }
            }
        }
    }
}

@Composable
fun InquiryDialog(
    title: String,
    content: String,
    cancelText: String = "取消",
    confirmText: String = "确认",
    confirmColor: Color = colorResource(R.color.btn_confirm),
    onCancel: () -> Unit = {},
    onDismissRequest: () -> Unit,
    onConfirm: () -> Boolean
) {
    ConfirmDialog(
        title = title,
        cancelText = cancelText,
        confirmText = confirmText,
        confirmColor = confirmColor,
        onCancel = { onCancel() },
        onDismissRequest = { onDismissRequest() },
        onConfirm = { onConfirm() }
    ) {
        if (content.isNotEmpty()) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun SliderDialog(
    title: String,
    content: String = "",
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onCancel: () -> Unit = {},
    onDismissRequest: () -> Unit,
    onConfirm: () -> Boolean,
    cancelText: String = "取消",
    confirmText: String = "确认",
) {
    val initialValue = remember { value }
    var isConfirmed by remember { mutableStateOf(false) }

    val currentLocale = LocalConfiguration.current.locales.get(0)

    ConfirmDialog(
        title = title,
        cancelText = cancelText,
        confirmText = confirmText,
        onCancel = onCancel,
        onDismissRequest = {
            if (!isConfirmed) {
                onValueChange(initialValue)
            }
            onDismissRequest()
        },
        onConfirm = {
            val success = onConfirm()
            if (success) {
                isConfirmed = true
            }
            success
        }
    ) {
        if (content.isNotEmpty()) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = String.format(currentLocale, "%+.1f", valueRange.start), fontSize = 12.sp, color = colorResource(R.color.text))

            Spacer(modifier = Modifier.width(8.dp))

            Slider(
                value = value,
                onValueChange = { onValueChange(it) },
                valueRange = valueRange,
                colors = SliderDefaults.colors(
                    thumbColor = colorResource(R.color.mikuGreen),
                    activeTrackColor = colorResource(R.color.mikuGreen),
                    inactiveTrackColor = colorResource(R.color.mikuGreen).copy(alpha = 0.25f)
                ),
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Text(text = String.format(currentLocale, "%+.1f", valueRange.endInclusive), fontSize = 12.sp, color = colorResource(R.color.text))
        }
    }
}
@Composable
fun OptionDialog(
    title: String,
    options: List<String>,
    initialSelections: Set<Int> = emptySet(),
    singleSelect: Boolean = false,
    onCancel: () -> Unit = {},
    onDismissRequest: () -> Unit,
    onConfirm: (Set<Int>) -> Unit,
    cancelText: String = "取消",
    confirmText: String = "确认",
) {
    var selectedItems by remember { mutableStateOf(initialSelections) }

    val confirmAction = {
        onConfirm(selectedItems)
        onDismissRequest()
    }

    val cancelAction = {
        onCancel()
        onDismissRequest()
    }

    StandardDialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                itemsIndexed(options) { index, optionTitle ->
                    val isChecked = selectedItems.contains(index)
                    OptionAnimItem(
                        title = optionTitle,
                        checked = isChecked,
                        onCheckedChange = {
                            selectedItems = if (singleSelect) {
                                setOf(index)
                            } else {
                                val newSet = selectedItems.toMutableSet()
                                if (isChecked) newSet.remove(index) else newSet.add(index)
                                newSet
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    border = BorderStroke(1.dp, Color.LightGray),
                    shape = squircleShape,
                    colors = ButtonDefaults.textButtonColors(Color.Transparent),
                    onClick = cancelAction
                ) {
                    Text(cancelText, color = Color.Gray)
                }
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    border = BorderStroke(1.dp, Color.LightGray),
                    shape = squircleShape,
                    colors = ButtonDefaults.textButtonColors(colorResource(R.color.btn_confirm)),
                    onClick = confirmAction
                ) {
                    Text(confirmText, color = colorResource(R.color.text))
                }
            }
        }
    }
}

@Composable
fun DatePicker(
    title: String? = null,
    initialSelectedDateMillis: Long = System.currentTimeMillis(),
    onDateSelected: (Long?) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialSelectedDateMillis)

    StandardDialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(bottom = 4.dp)
                .scale(0.85f)
                .fillMaxWidth(0.85f)
        ) {
            Box(
                modifier = Modifier.requiredWidth(360.dp),
                contentAlignment = Alignment.Center
            ) {
                DatePicker(
                    state = datePickerState,
                    title = {
                        Text(
                            text = title ?: "选择日期",
                            modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp),
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = DatePickerDefaults.colors(
                        containerColor = colorResource(R.color.bg_dialog),
                        selectedDayContainerColor = colorResource(R.color.mikuGreen),
                        todayDateBorderColor = colorResource(R.color.mikuGreen),
                        selectedYearContainerColor = colorResource(R.color.mikuGreen)
                    )
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Button(
                    modifier = Modifier
                        .height(40.dp)
                        .weight(1f),
                    border = BorderStroke(1.dp, Color.LightGray),
                    colors = ButtonDefaults.textButtonColors(Color.Transparent),
                    onClick = onDismiss,
                    shape = squircleShape
                ) {
                    Text(text = "取消", color = colorResource(R.color.text))
                }

                Button(
                    modifier = Modifier
                        .height(40.dp)
                        .weight(1f),
                    border = BorderStroke(1.dp, Color.LightGray),
                    shape = squircleShape,
                    colors = ButtonDefaults.textButtonColors(colorResource(R.color.btn_confirm)),
                    onClick = {
                        onDateSelected(datePickerState.selectedDateMillis)
                        onDismiss()
                    }
                ) {
                    Text(text = "确定", color = colorResource(R.color.text))
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    onCancel: () -> Unit = {},
    onDismissRequest: () -> Unit,
    onConfirm: () -> Boolean,
    cancelText: String = "取消",
    confirmText: String = "确认",
    confirmColor: Color = colorResource(R.color.btn_confirm),
    content: @Composable () -> Unit
) {
    val confirmAction = {
        if (onConfirm()) {
            onDismissRequest()
        }
    }

    val cancelAction = {
        onCancel()
        onDismissRequest()
    }

    StandardDialog(onDismissRequest = onDismissRequest) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            content()

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    border = BorderStroke(1.dp, Color.LightGray),
                    shape = squircleShape,
                    colors = ButtonDefaults.textButtonColors(Color.Transparent),
                    onClick = cancelAction
                ) {
                    Text(text = cancelText, color = Color.Gray)
                }

                Button(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    border = BorderStroke(1.dp, Color.LightGray),
                    shape = squircleShape,
                    colors = ButtonDefaults.textButtonColors(confirmColor),
                    onClick = confirmAction
                ) {
                    Text(text = confirmText, color = colorResource(R.color.text))
                }
            }
        }
    }
}

@Composable
fun StandardDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val sharedTiltState = rememberSharedTiltState()

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .tiltGestureContainer(sharedTiltState)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest
                ),
            contentAlignment = Alignment.Center
        ) {
            BlurEffect()

            Card(
                modifier = modifier
                    .applySharedTilt(sharedTiltState)
                    .border(
                        width = 2.dp,
                        color = Color.LightGray,
                        shape = squircleShape
                    )
                    .clickable(enabled = false) {},
                shape = squircleShape,
                colors = CardDefaults.cardColors(containerColor = colorResource(R.color.bg_dialog)),
                elevation = CardDefaults.cardElevation(16.dp)
            ) {
                content()
            }
        }
    }
}
