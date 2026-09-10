package com.hamster.review.screen

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamster.review.Main
import com.hamster.review.compose.InquiryDialog
import com.hamster.review.compose.ItemGroup
import com.hamster.review.compose.PageColumn
import com.hamster.review.compose.SharedTiltState
import com.hamster.review.compose.rememberSharedTiltState
import com.hamster.review.data.db.QuestionDetail
import com.hamster.review.data.db.QuestionOptionEntity
import com.hamster.review.data.model.QuestionType
import com.hrm.latex.renderer.Latex
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import kotlinx.coroutines.delay
import org.intellij.markdown.ast.getTextInNode
import com.hamster.review.R
import com.hamster.review.compose.squircleShape
import com.hamster.review.data.db.DailyRecordEntity
import com.hamster.review.viewModel.ReviewUiState
import com.hamster.review.viewModel.ReviewViewModel
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.ceil

@Composable
fun ReviewScreen(
    onNavigate: (com.hamster.review.Route) -> Unit,
    setTopbarTitle: (String) -> Unit,
    setReviewProgress: (Float?) -> Unit
) {
    val viewModel: ReviewViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val todayProgress by viewModel.todayProgress.collectAsState()
    val sharedTiltState = rememberSharedTiltState()
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val cardAlpha = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

    // 只要停留在 Review 页就显示顶栏环形进度；离开时清除
    LaunchedEffect(todayProgress) {
        setReviewProgress(todayProgress)
    }
    LaunchedEffect(Unit) {
        if (viewModel.isMasteredTest) setTopbarTitle("已掌握测试")
    }
    DisposableEffect(Unit) {
        onDispose { setReviewProgress(null) }
    }

    fun animateExitAndNext(correct: Boolean) {
        coroutineScope.launch {
            val width = cardSize.width.toFloat().coerceAtLeast(1f)
            val height = cardSize.height.toFloat().coerceAtLeast(1f)
            kotlinx.coroutines.coroutineScope {
                if (correct) {
                    launch { offsetX.animateTo(-width, tween(250)) }
                    launch { cardAlpha.animateTo(0f, tween(250)) }
                } else {
                    launch { offsetY.animateTo(height, tween(250)) }
                    launch { cardAlpha.animateTo(0f, tween(250)) }
                }
            }
            viewModel.next()
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
            cardAlpha.snapTo(1f)
        }
    }

    BackHandler {
        setTopbarTitle("首页")
        onNavigate(Main)
    }

    PageColumn(sharedTiltState = sharedTiltState) {
        when {
            state.isLoading -> {
                Text(
                    modifier = Modifier.padding(24.dp),
                    text = "加载中...",
                    fontSize = 18.sp
                )
            }

            state.finished || state.currentQuestion == null -> {
                ItemGroup(titleState = sharedTiltState) {
                    Text(
                        modifier = Modifier.padding(24.dp),
                        text = when {
                            viewModel.isMasteredTest && state.testTotal == 0 -> "没有已掌握的题目"
                            viewModel.isMasteredTest -> "测试完成"
                            else -> "已完成当日学习"
                        },
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )

                    if (viewModel.isMasteredTest && state.testTotal > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "本次答错 ${state.unmasteredCount} 题，已置为未掌握",
                            modifier = Modifier.padding(horizontal = 24.dp),
                            fontSize = 14.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    if (state.completedToday) {
                        SingleMonthHeatmap(
                            records = state.dailyRecords,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )

                        if (state.canAnotherGroup) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .fillMaxWidth()
                                    .height(48.dp),
                                border = BorderStroke(1.dp, Color.LightGray),
                                shape = squircleShape,
                                colors = ButtonDefaults.textButtonColors(colorResource(R.color.btn_confirm)),
                                onClick = { viewModel.startAnotherGroup() }
                            ) {
                                Text("再来一组", color = Color.Black)
                            }
                        }
                    }
                }
            }

            else -> {
                val question = state.currentQuestion ?: return@PageColumn
                var isEditing by remember { mutableStateOf(false) }

                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .onSizeChanged { cardSize = it }
                            .graphicsLayer {
                                translationX = offsetX.value
                                translationY = offsetY.value
                                alpha = cardAlpha.value
                            }
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            ReviewQuestionSection(
                                titleState = sharedTiltState,
                                question = question,
                                state = state,
                                onSelectOption = viewModel::selectOption,
                                onToggleMastered = viewModel::toggleMastered,
                                onSaveEdited = viewModel::saveEditedQuestion,
                                onDeleteQuestion = viewModel::deleteQuestion,
                                isEditing = isEditing,
                                onEditingChange = { isEditing = it },
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.item_group_gap)))

                    ActionButtons(
                        question = question,
                        state = state,
                        editing = isEditing,
                        onSubmitChoice = viewModel::submitChoice,
                        onRevealAnswer = viewModel::revealAnswer,
                        onSelfAssess = viewModel::selfAssess,
                        onNext = {
                            if (isEditing) {
                                isEditing = false
                            } else {
                                animateExitAndNext(state.lastResultCorrect ?: true)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(36.dp))
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ReviewQuestionSection(
    titleState: SharedTiltState,
    question: QuestionDetail,
    state: ReviewUiState,
    onSelectOption: (Long) -> Unit,
    onToggleMastered: () -> Unit,
    onSaveEdited: (Long, String, String?, String, List<Triple<Long, String, Boolean>>?) -> Unit,
    onDeleteQuestion: (Long) -> Unit,
    isEditing: Boolean,
    onEditingChange: (Boolean) -> Unit
) {
    val questionTypeName = when (question.question.type) {
        QuestionType.SINGLE_CHOICE -> "单选题"
        QuestionType.TRUE_FALSE -> "判断题"
        QuestionType.FILL_BLANK -> "填空题"
        QuestionType.MULTIPLE_CHOICE -> "多选题"
    }

    var showSaveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var editContent by remember(question.question.id) { mutableStateOf(question.question.content) }
    var editAnswer by remember(question.question.id) { mutableStateOf(question.question.answer ?: "") }
    var editExplanation by remember(question.question.id) { mutableStateOf(question.question.explanation) }
    var editOptions by remember(question.question.id) {
        mutableStateOf(question.options.map { Triple(it.id, it.content, it.isCorrect) })
    }
    var editingField by remember { mutableStateOf<String?>(null) }
    var editingOptionId by remember { mutableStateOf<Long?>(null) }

    BackHandler(enabled = isEditing) {
        onEditingChange(false)
    }

    // 顶部单独卡片：题型 / 答题结果 + 功能按钮
    ItemGroup(
        titleState = titleState,
        contentModifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (state.answered) {
                    if (state.lastResultCorrect == true) "回答正确" else "回答错误"
                } else {
                    questionTypeName
                },
                color = if (state.answered) {
                    if (state.lastResultCorrect == true)
                        colorResource(R.color.btn_confirm)
                    else
                        colorResource(R.color.red)
                } else Color.Black,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.answered) {

                    ExpandableCapsule(
                        text = if (state.mastered) "已掌握" else "已取消",
                        iconRes = if (state.mastered) R.drawable.check_circle_line else R.drawable.minus_circle_line,
                        onClick = onToggleMastered
                    )

                    IconButton(
                        onClick = {
                            if (isEditing) {
                                showSaveDialog = true
                            } else {
                                onEditingChange(true)
                            }
                        }
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
                            painter = painterResource(R.drawable.edit_line),
                            tint = if (isEditing) colorResource(R.color.mikuGreen) else Color.Black,
                            contentDescription = "编辑题目"
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(dimensionResource(R.dimen.item_group_gap)))

    // 题目卡片：占满剩余空间。题面固定最上方(过长可滚动)，备注/选项/答案固定在最下方
    ItemGroup(
        titleState = titleState,
        modifier = Modifier.weight(1f),
        contentModifier = Modifier
            .fillMaxHeight()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) {
                    editingField = null
                    editingOptionId = null
                }
        ) {

            // 题面
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (isEditing && editingField == "content") {
                    BasicTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (isEditing) Modifier.clickable { editingField = "content" } else Modifier)
                    ) {
                        MarkdownContent(
                            modifier = Modifier.padding(8.dp),
                            content = if (isEditing) editContent else question.question.content,
                            fontSize = 20.sp
                        )
                    }
                }

                if (question.question.type == QuestionType.FILL_BLANK && (state.showAnswer || isEditing)) {
                    Spacer(modifier = Modifier.height(12.dp))
                    if (isEditing && editingField == "answer") {
                        BasicTextField(
                            value = editAnswer,
                            onValueChange = { editAnswer = it },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        )
                    } else {
                        Text(
                            text = if (isEditing) "答案：\n$editAnswer" else "答案：\n${question.question.answer.orEmpty()}",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .padding(vertical = 4.dp)
                                .then(if (isEditing) Modifier.clickable { editingField = "answer" } else Modifier)
                        )
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(8.dp))

                if (state.answered || isEditing) {
                    if (isEditing && editingField == "explanation") {
                        BasicTextField(
                            value = editExplanation,
                            onValueChange = { editExplanation = it },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Text(
                            text = if (isEditing) "备注：$editExplanation" else "备注：${question.question.explanation}",
                            fontSize = 14.sp,
                            modifier = Modifier
                                .then(if (isEditing) Modifier.clickable { editingField = "explanation" } else Modifier)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                when (question.question.type) {
                    QuestionType.SINGLE_CHOICE,
                    QuestionType.MULTIPLE_CHOICE,
                    QuestionType.TRUE_FALSE -> {
                        val fixedOrder = question.question.type == QuestionType.TRUE_FALSE
                        val displayOptions = remember(question.question.id, fixedOrder) {
                            if (fixedOrder) {
                                question.options.sortedBy { it.sortOrder }
                            } else {
                                question.options.shuffled()
                            }
                        }
                        val options = if (isEditing) {
                            displayOptions.map { option ->
                                val edit = editOptions.first { it.first == option.id }
                                QuestionOptionEntity(
                                    questionId = question.question.id,
                                    id = edit.first,
                                    content = edit.second,
                                    isCorrect = edit.third,
                                    sortOrder = option.sortOrder
                                )
                            }
                        } else {
                            displayOptions
                        }
                        options.forEachIndexed { index, option ->
                            if (isEditing) {
                                OptionItem(
                                    option = option,
                                    background = if (option.isCorrect) {
                                        colorResource(R.color.mikuGreen).copy(alpha = 0.8f)
                                    } else {
                                        Color.Transparent
                                    },
                                    enabled = true,
                                    onClick = {
                                        editingOptionId = null
                                        val newList = editOptions.toMutableList()
                                        if (question.question.type == QuestionType.MULTIPLE_CHOICE) {
                                            newList[index] = newList[index].copy(third = !newList[index].third)
                                        } else {
                                            newList.indices.forEach { i ->
                                                newList[i] = newList[i].copy(third = i == index)
                                            }
                                        }
                                        editOptions = newList
                                    },
                                    onDoubleTap = {
                                        if (question.question.type != QuestionType.TRUE_FALSE) {
                                            editingOptionId = option.id
                                        }
                                    },
                                    editingText = if (editingOptionId == option.id) editOptions[index].second else null,
                                    onTextChange = if (editingOptionId == option.id) { newText ->
                                        editOptions = editOptions.toMutableList().also { list ->
                                            list[index] = list[index].copy(second = newText)
                                        }
                                    } else null
                                )
                            } else {
                                OptionItem(
                                    option = option,
                                    background = optionBackground(question, state, option),
                                    enabled = !state.answered,
                                    onClick = { onSelectOption(option.id) }
                                )
                            }

                            Spacer(modifier = Modifier.height(2.dp))
                        }
                    }

                    QuestionType.FILL_BLANK -> Unit
                }

                if (isEditing) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { showDeleteDialog = true }
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.delete_line),
                            contentDescription = null,
                            tint = colorResource(R.color.red),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        InquiryDialog(
            title = "保存修改",
            content = "是否保存对题目的修改？",
            confirmText = "保存",
            onDismissRequest = {
                showSaveDialog = false
                onEditingChange(false)
            },
            onCancel = {
                showSaveDialog = false
                onEditingChange(false)
            },
            onConfirm = {
                onSaveEdited(
                    question.question.id,
                    editContent,
                    if (question.question.type == QuestionType.FILL_BLANK) editAnswer else question.question.answer,
                    editExplanation,
                    editOptions
                )
                showSaveDialog = false
                onEditingChange(false)
                true
            }
        )
    }

    if (showDeleteDialog) {
        InquiryDialog(
            title = "删除题目",
            content = "确定删除该题目吗？",
            confirmText = "删除",
            confirmColor = colorResource(R.color.red),
            onDismissRequest = { showDeleteDialog = false },
            onCancel = { showDeleteDialog = false },
            onConfirm = {
                onDeleteQuestion(question.question.id)
                showDeleteDialog = false
                onEditingChange(false)
                true
            }
        )
    }
}

@Composable
private fun ActionButtons(
    question: QuestionDetail,
    state: ReviewUiState,
    editing: Boolean,
    onSubmitChoice: () -> Unit,
    onRevealAnswer: () -> Unit,
    onSelfAssess: (Boolean) -> Unit,
    onNext: () -> Unit
) {
    val buttonHeight = 48.dp

    ItemGroup(
        modifier = Modifier.fillMaxWidth(),
        contentModifier = Modifier.padding(16.dp),
        titleState = rememberSharedTiltState()
    ) {
        when {
            state.answered -> {
                Button(
                    modifier = Modifier
                        .height(buttonHeight)
                        .fillMaxWidth(),
                    border = BorderStroke(1.dp, Color.LightGray),
                    shape = squircleShape,
                    colors = ButtonDefaults.textButtonColors(colorResource(R.color.btn_confirm)),
                    enabled = !editing,
                    onClick = onNext
                ) {
                    Text("下一个", color = Color.Black)
                }
            }

            question.question.type == QuestionType.FILL_BLANK && !state.showAnswer -> {
                Button(
                    modifier = Modifier
                        .height(buttonHeight)
                        .fillMaxWidth(),
                    border = BorderStroke(1.dp, Color.LightGray),
                    shape = squircleShape,
                    colors = ButtonDefaults.textButtonColors(colorResource(R.color.btn_confirm)),
                    onClick = onRevealAnswer
                ) {
                    Text("查看答案", color = Color.Black)
                }
            }

            question.question.type == QuestionType.FILL_BLANK && state.showAnswer -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        modifier = Modifier
                            .height(buttonHeight)
                            .weight(1f),
                        border = BorderStroke(1.dp, Color.LightGray),
                        shape = squircleShape,
                        colors = ButtonDefaults.textButtonColors(colorResource(R.color.red)),
                        onClick = { onSelfAssess(false) }
                    ) {
                        Text("记错了", color = Color.Black)
                    }

                    Button(
                        modifier = Modifier
                            .height(buttonHeight)
                            .weight(1f),
                        border = BorderStroke(1.dp, Color.LightGray),
                        shape = squircleShape,
                        colors = ButtonDefaults.textButtonColors(colorResource(R.color.btn_confirm)),
                        onClick = { onSelfAssess(true) }
                    ) {
                        Text("确认", color = Color.Black)
                    }
                }
            }

            else -> {
                Button(
                    modifier = Modifier
                        .height(buttonHeight)
                        .fillMaxWidth(),
                    border = BorderStroke(1.dp, Color.LightGray),
                    shape = squircleShape,
                    colors = ButtonDefaults.textButtonColors(colorResource(R.color.btn_confirm)),
                    onClick = onSubmitChoice
                ) {
                    Text("确认", color = Color.Black)
                }
            }
        }
    }
}

@Composable
private fun OptionItem(
    option: QuestionOptionEntity,
    background: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    onDoubleTap: (() -> Unit)? = null,
    editingText: String? = null,
    onTextChange: ((String) -> Unit)? = null
) {
    val interactionModifier = if (onDoubleTap == null) {
        Modifier.clickable(
            enabled = enabled,
            onClick = onClick,
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        )
    } else {
        Modifier.pointerInput(option.id, enabled) {
            detectTapGestures(
                onTap = { if (enabled) onClick() },
                onDoubleTap = { if (enabled) onDoubleTap.invoke() }
            )
        }
    }

    Row(
        modifier = Modifier
//            .height(48.dp)
            .fillMaxWidth()
            .clip(shape = squircleShape)
            .background(background)
            .then(interactionModifier)
            .padding(horizontal = 24.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (editingText != null && onTextChange != null) {
            BasicTextField(
                value = editingText,
                onValueChange = onTextChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
            )
        } else {
            MarkdownContent(content = option.content, fontSize = 16.sp)
        }
    }
}
@Composable
private fun optionBackground(
    question: QuestionDetail,
    state: ReviewUiState,
    option: QuestionOptionEntity
): Color {
    val selected = option.id in state.selectedOptionIds
    val selectedColor = colorResource(R.color.mikuGreen).copy(alpha = 0.8f)

    if (!state.answered) {
        return if (selected) selectedColor else Color.Transparent
    }

    return when (question.question.type) {
        QuestionType.SINGLE_CHOICE,
        QuestionType.TRUE_FALSE -> {
            if (state.lastResultCorrect == true) {
                if (selected) selectedColor else Color.Transparent
            } else {
                when {
                    option.isCorrect -> selectedColor
                    selected -> colorResource(R.color.red)
                    else -> Color.Transparent
                }
            }
        }

        QuestionType.MULTIPLE_CHOICE -> {
            when {
                option.isCorrect && selected -> selectedColor
                !option.isCorrect && selected -> colorResource(R.color.red)
                option.isCorrect && !selected -> colorResource(R.color.yellow)
                else -> Color.Transparent
            }
        }

        QuestionType.FILL_BLANK -> Color.Transparent
    }
}


@Composable
private fun MarkdownContent(modifier: Modifier = Modifier, content: String, fontSize: TextUnit = 12.sp) {
    Markdown(
        modifier = modifier,
        content = content,
        components = markdownComponents(
            codeFence = { model ->
                val blockText = model.node.getTextInNode(model.content).toString()

                if (blockText.startsWith("```math") || blockText.startsWith("```latex")) {
                    val formula = blockText
                        .replace(Regex("^```(math|latex)"), "")
                        .removeSuffix("```")
                        .trim()

                    Latex(latex = formula)
                } else {
                    Text(
                        text = blockText,
                        modifier = Modifier
                            .padding(8.dp)
                            .background(Color.LightGray)
                    )
                }
            }
        ),
        typography = markdownTypography(
            h1 = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            h2 = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            h3 = MaterialTheme.typography.headlineLarge.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold
            ),
            text = MaterialTheme.typography.bodyLarge.copy(
                fontSize = fontSize
            ),
            paragraph = MaterialTheme.typography.bodyLarge.copy(
                fontSize = fontSize
            )
        )
    )
}

@Composable
fun ExpandableCapsule(
    text: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .clickable {
                expanded = !expanded
                onClick()
            }
            .animateContentSize()
    ) {
        LaunchedEffect(expanded) {
            if (expanded) {
                delay(3000)
                expanded = false
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
            exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut()
        ) {
            Text(
                text = text,
                color = Color.Black,
                modifier = Modifier.padding(start = 20.dp, end = 4.dp),
                maxLines = 1
            )
        }

        Box(
            modifier = Modifier
                .size(36.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                modifier = Modifier.size(24.dp),
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = Color.Black
            )
        }
    }
}

@Composable
fun SingleMonthHeatmap(
    records: List<DailyRecordEntity>,
    modifier: Modifier = Modifier
) {
    val currentMonth = YearMonth.now()
    val dataMap = remember(records) {
        records.associate { LocalDate.parse(it.date) to it.reviewCount }
    }

    Column(modifier = modifier) {
        val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weekdays.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val daysInMonth = currentMonth.lengthOfMonth()
        val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value
        val totalCells = daysInMonth + firstDayOfWeek - 1
        val rows = ceil(totalCells / 7.0).toInt()

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (r in 0 until rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (c in 0..6) {
                        val cellIndex = r * 7 + c
                        val day = cellIndex - firstDayOfWeek + 2

                        if (day in 1..daysInMonth) {
                            val date = currentMonth.atDay(day)
                            val count = dataMap[date] ?: 0

                            // 保留了你原本根据 count 设定的热力图颜色
                            val bgColor = when {
                                count <= 0 -> Color(0xFFEBEDF0)
                                count == 1 -> Color(0xFFC6E48B)
                                count <= 3 -> Color(0xFF7BC96F)
                                count <= 5 -> Color(0xFF239A3B)
                                else -> Color(0xFF196127)
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(bgColor, squircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = day.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (count > 0) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            // 空白占位符
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
