package com.hamster.review.screen

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamster.review.R
import com.hamster.review.compose.InquiryDialog
import com.hamster.review.compose.ItemGroup
import com.hamster.review.compose.PageColumn
import com.hamster.review.compose.outlinedTextFieldColors
import com.hamster.review.compose.rememberSharedTiltState
import com.hamster.review.compose.squircleShape
import com.hamster.review.data.model.QuestionType
import com.hamster.review.viewModel.AddQuestionViewModel

@Composable
fun AddQuestionScreen(
    setTopbarTitle: (String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: AddQuestionViewModel = viewModel()
    val type = viewModel.type
    val isEdit = viewModel.isEdit
    val seed by viewModel.seed.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val isChoice = type == QuestionType.SINGLE_CHOICE || type == QuestionType.MULTIPLE_CHOICE
    val isJudge = type == QuestionType.TRUE_FALSE
    val isFill = type == QuestionType.FILL_BLANK
    val sharedTiltState = rememberSharedTiltState()
    val context = LocalContext.current

    val typeLabel = when (type) {
        QuestionType.SINGLE_CHOICE -> "单选题"
        QuestionType.MULTIPLE_CHOICE -> "多选题"
        QuestionType.TRUE_FALSE -> "判断题"
        QuestionType.FILL_BLANK -> "填空题"
    }
    setTopbarTitle(if (isEdit) "编辑题目" else "新增题目")

    var content by remember(seed?.id) { mutableStateOf(seed?.content ?: "") }
    var answer by remember(seed?.id) { mutableStateOf(seed?.answer ?: "") }

    val defaultOptions = when (type) {
        QuestionType.SINGLE_CHOICE -> listOf("选项 1", "选项 2")
        QuestionType.MULTIPLE_CHOICE -> listOf("选项 1", "选项 2", "选项 3")
        QuestionType.TRUE_FALSE -> listOf("对", "错")
        else -> emptyList()
    }
    var options by remember(seed?.id) { mutableStateOf(seed?.optionTexts ?: defaultOptions) }
    var singleCorrect by remember(seed?.id) {
        mutableStateOf(seed?.optionCorrect?.indexOfFirst { it }?.takeIf { it >= 0 })
    } // 单选/判断
    var multiCorrect by remember(seed?.id) {
        mutableStateOf(
            seed?.optionCorrect
                ?.mapIndexedNotNull { index, correct -> if (correct) index else null }
                ?.toSet()
                ?: emptySet()
        )
    } // 多选
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 结束选项文字编辑时移除焦点并收起键盘
    LaunchedEffect(editingIndex) {
        if (editingIndex == null) {
            focusManager.clearFocus()
            keyboardController?.hide()
        }
    }

    fun removeOption(index: Int) {
        options = options.filterIndexed { i, _ -> i != index }
        singleCorrect = when {
            singleCorrect == null -> null
            singleCorrect == index -> null
            singleCorrect!! > index -> singleCorrect!! - 1
            else -> singleCorrect
        }
        multiCorrect = multiCorrect
            .mapNotNull { if (it == index) null else if (it > index) it - 1 else it }
            .toSet()
        editingIndex = null
    }

    fun onRowClick(index: Int) {
        editingIndex = null
        val minSize = if (type == QuestionType.MULTIPLE_CHOICE) 3 else 2
        val rowBlank = options[index].isBlank()
        // 编辑模式下不允许增删选项（保持与已存选项一一对应）
        if (!isEdit && !isJudge && rowBlank && options.size > minSize) {
            removeOption(index)
            return
        }
        when (type) {
            QuestionType.MULTIPLE_CHOICE ->
                multiCorrect = if (index in multiCorrect) multiCorrect - index else multiCorrect + index
            QuestionType.SINGLE_CHOICE, QuestionType.TRUE_FALSE ->
                singleCorrect = if (singleCorrect == index) null else index
            else -> Unit
        }
    }

    fun updateOptionText(index: Int, text: String) {
        options = options.toMutableList().also { it[index] = text }
    }

    fun addOption() {
        if (options.size < 5) options = options + "选项 ${options.size + 1}"
    }

    fun canSave(): Boolean = when (type) {
        QuestionType.FILL_BLANK -> content.isNotBlank() && answer.isNotBlank()
        QuestionType.TRUE_FALSE, QuestionType.SINGLE_CHOICE ->
            content.isNotBlank() && singleCorrect != null && options.all { it.isNotBlank() }
        QuestionType.MULTIPLE_CHOICE ->
            content.isNotBlank() && multiCorrect.size >= 2 && options.all { it.isNotBlank() }
    }

    fun buildOptionPairs(): List<Pair<String, Boolean>> = if (isFill) {
        emptyList()
    } else {
        options.mapIndexed { index, text ->
            val correct = if (type == QuestionType.MULTIPLE_CHOICE) {
                index in multiCorrect
            } else {
                singleCorrect == index
            }
            text to correct
        }
    }

    /** 编辑模式下判断是否有改动。 */
    fun isDirty(): Boolean {
        val origin = seed ?: return false
        if (content.trim() != origin.content.trim()) return true
        if (isFill) return answer.trim() != origin.answer.trim()
        val pairs = buildOptionPairs()
        if (pairs.size != origin.optionTexts.size) return true
        return pairs.indices.any { i ->
            pairs[i].first.trim() != origin.optionTexts[i].trim() ||
                pairs[i].second != origin.optionCorrect[i]
        }
    }

    fun save() {
        viewModel.saveQuestion(content, answer, buildOptionPairs()) { ok ->
            Toast.makeText(
                context,
                if (ok) "题目已保存" else "保存失败，请重试",
                Toast.LENGTH_SHORT
            ).show()
            if (ok) onBack()
        }
    }

    // 返回：编辑模式且有改动时先询问是否保存
    fun attemptExit() {
        if (isEdit && isDirty()) {
            showExitDialog = true
        } else {
            onBack()
        }
    }

    BackHandler { attemptExit() }

    if (showExitDialog) {
        InquiryDialog(
            title = "保存修改",
            content = "是否保存对题目的修改？",
            confirmText = "保存",
            cancelText = "放弃",
            onDismissRequest = { showExitDialog = false }, // 点击外部：留在页面
            onCancel = {
                showExitDialog = false
                onBack() // 放弃修改并退出
            },
            onConfirm = {
                save() // 保存成功后自动退出
                true
            }
        )
    }

    if (loading) {
        PageColumn(sharedTiltState = sharedTiltState) {
            Text(
                modifier = Modifier.padding(24.dp),
                text = "加载中...",
                fontSize = 18.sp
            )
        }
        return
    }

    PageColumn(sharedTiltState = sharedTiltState) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // 点击空白处：结束选项编辑、移除焦点并收起键盘
                    editingIndex = null
                    focusManager.clearFocus()
                    keyboardController?.hide()
                }
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                ItemGroup(
                    titleState = sharedTiltState,
                    contentModifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = typeLabel,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("题目") },
                        placeholder = { Text("请输入题目内容", color = Color.Gray) },
                        shape = squircleShape,
                        colors = outlinedTextFieldColors()
                    )

                    if (isFill) {
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = answer,
                            onValueChange = { answer = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("答案") },
                            placeholder = { Text("请输入答案", color = Color.Gray) },
                            shape = squircleShape,
                            colors = outlinedTextFieldColors()
                        )
                    }

                    if (isChoice || isJudge) {
                        Spacer(modifier = Modifier.height(16.dp))

                        val minOptionSize = if (type == QuestionType.MULTIPLE_CHOICE) 3 else 2

                        options.forEachIndexed { index, text ->
                            val correct = if (type == QuestionType.MULTIPLE_CHOICE) {
                                index in multiCorrect
                            } else {
                                singleCorrect == index
                            }
                            val editing = editingIndex == index && !isJudge
                            OptionEditRow(
                                text = text,
                                correct = correct,
                                editable = !isJudge,
                                editing = editing,
                                deletable = !isEdit && options.size > minOptionSize,
                                onClick = { onRowClick(index) },
                                onDoubleTap = { editingIndex = index },
                                onTextChange = { updateOptionText(index, it) },
                                onEndEdit = { editingIndex = null },
                                onDelete = { removeOption(index) }
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }

                        if (isChoice && !isEdit && options.size < 5) {
                            AddOptionRow(onClick = { addOption() })
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            ItemGroup(
                titleState = sharedTiltState,
                contentModifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        modifier = Modifier.weight(1f).height(48.dp),
                        border = BorderStroke(1.dp, Color.LightGray),
                        shape = squircleShape,
                        colors = ButtonDefaults.textButtonColors(Color.Transparent),
                        onClick = { attemptExit() }
                    ) {
                        Text("取消", color = colorResource(R.color.text))
                    }
                    Button(
                        modifier = Modifier.weight(1f).height(48.dp),
                        enabled = canSave(),
                        border = BorderStroke(1.dp, Color.LightGray),
                        shape = squircleShape,
                        colors = ButtonDefaults.textButtonColors(colorResource(R.color.btn_confirm)),
                        onClick = { save() }
                    ) {
                        Text("保存", color = colorResource(R.color.text))
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}

@Composable
private fun OptionEditRow(
    text: String,
    correct: Boolean,
    editable: Boolean,
    editing: Boolean,
    deletable: Boolean,
    onClick: () -> Unit,
    onDoubleTap: () -> Unit,
    onTextChange: (String) -> Unit,
    onEndEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val background = if (correct) {
        colorResource(R.color.mikuGreen).copy(alpha = 0.8f)
    } else {
        Color.Transparent
    }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    var wasFocused by remember { mutableStateOf(false) }

    LaunchedEffect(editing) {
        if (editing) {
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }

    val gestureModifier = Modifier.pointerInput(editable, editing) {
        if (editing) return@pointerInput
        detectTapGestures(
            onTap = { onClick() },
            onDoubleTap = { if (editable) onDoubleTap() }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(squircleShape)
            .background(background)
            .then(gestureModifier)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (editing) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            if (wasFocused && !it.isFocused) onEndEdit()
                            wasFocused = it.isFocused
                        }
                        .onPreviewKeyEvent { event ->
                            // 内容为空时按退格(删除)键删除该选项，与日记编辑器行为一致
                            if (event.type == KeyEventType.KeyDown &&
                                event.key == Key.Backspace &&
                                text.isEmpty() &&
                                deletable
                            ) {
                                onDelete()
                                true
                            } else {
                                false
                            }
                        }
                        .padding(vertical = 10.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp)
                )
            }
        } else {
            Text(
                text = text,
                fontSize = 16.sp,
                color = colorResource(R.color.text),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp)
                    .padding(vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun AddOptionRow(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(squircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.add_line),
            contentDescription = "新增选项",
            tint = colorResource(R.color.icon),
            modifier = Modifier.size(24.dp)
        )
    }
}
