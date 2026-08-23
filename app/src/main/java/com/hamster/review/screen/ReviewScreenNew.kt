package com.hamster.review.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hamster.review.compose.ItemGroup
import com.hamster.review.compose.PageColumn
import com.hamster.review.compose.SingleMonthHeatmap
import com.hamster.review.compose.rememberSharedTiltState
import com.hamster.review.data.db.QuestionDetail
import com.hamster.review.data.db.QuestionOptionEntity
import com.hamster.review.data.model.QuestionType
import com.hrm.latex.renderer.Latex
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import org.intellij.markdown.ast.getTextInNode

@Composable
fun ReviewScreenNew(
    subjectId: Long,
    onNavigate: (com.hamster.review.Route) -> Unit,
    setTopbarTitle: (String) -> Unit
) {
    val viewModel: ReviewViewModel = viewModel()
    val state by viewModel.uiState.collectAsState()
    val sharedTiltState = rememberSharedTiltState()
    var cardSize by remember { mutableStateOf(IntSize.Zero) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val cardAlpha = remember { Animatable(1f) }
    val coroutineScope = rememberCoroutineScope()

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

    setTopbarTitle("复习")

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
                Text(
                    modifier = Modifier.padding(24.dp),
                    text = "已完成当日学习",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                if (state.completedToday) {
                    SingleMonthHeatmap(
                        records = state.dailyRecords,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }

            else -> {
                val question = state.currentQuestion ?: return@PageColumn
                QuestionCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { cardSize = it }
                        .graphicsLayer {
                            translationX = offsetX.value
                            translationY = offsetY.value
                            alpha = cardAlpha.value
                        },
                    question = question,
                    state = state,
                    onSelectOption = viewModel::selectOption,
                    onSubmitChoice = viewModel::submitChoice,
                    onRevealAnswer = viewModel::revealAnswer,
                    onSelfAssess = viewModel::selfAssess,
                    onNext = {
                        animateExitAndNext(state.lastResultCorrect ?: true)
                    },
                    onToggleMastered = viewModel::toggleMastered
                )
            }
        }
    }
}

@Composable
private fun QuestionCard(
    modifier: Modifier = Modifier,
    question: QuestionDetail,
    state: ReviewUiState,
    onSelectOption: (Long) -> Unit,
    onSubmitChoice: () -> Unit,
    onRevealAnswer: () -> Unit,
    onSelfAssess: (Boolean) -> Unit,
    onNext: () -> Unit,
    onToggleMastered: () -> Unit
) {
    ItemGroup(
        modifier = modifier.fillMaxWidth(),
        contentModifier = Modifier.padding(16.dp),
        titleState = rememberSharedTiltState()
    ) {
        if (state.answered) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = if (state.mastered) "取消已掌握" else "已掌握",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(onClick = onToggleMastered)
                        .padding(4.dp)
                )
            }
        }

        MarkdownContent(question.question.content)

        when (question.question.type) {
            QuestionType.SINGLE_CHOICE,
            QuestionType.MULTIPLE_CHOICE,
            QuestionType.TRUE_FALSE -> {
                val fixedOrder = question.question.type == QuestionType.TRUE_FALSE
                val options = remember(question.question.id, fixedOrder) {
                    if (fixedOrder) {
                        question.options.sortedBy { it.sortOrder }
                    } else {
                        question.options.shuffled()
                    }
                }
                options.forEach { option ->
                    OptionItem(
                        option = option,
                        selected = option.id in state.selectedOptionIds,
                        enabled = !state.answered,
                        onClick = { onSelectOption(option.id) }
                    )
                }

                if (!state.answered) {
                    Button(
                        onClick = onSubmitChoice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        enabled = state.selectedOptionIds.isNotEmpty()
                    ) {
                        Text("确认答案")
                    }
                }
            }

            QuestionType.FILL_BLANK -> {
                if (!state.showAnswer) {
                    Button(
                        onClick = onRevealAnswer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text("显示答案")
                    }
                } else if (!state.answered) {
                    question.question.answer?.let {
                        Text(
                            text = "答案：$it",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { onSelfAssess(true) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("我答对了")
                        }
                        Button(
                            onClick = { onSelfAssess(false) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("我答错了")
                        }
                    }
                }
            }
        }

        if (state.answered) {
            state.lastResultCorrect?.let { correct ->
                Text(
                    text = if (correct) "回答正确" else "回答错误",
                    color = if (correct) Color(0xFF2E7D32) else Color(0xFFC62828),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }

            if (question.question.explanation.isNotBlank()) {
                Text(
                    text = "备注：${question.question.explanation}",
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            if (question.tags.isNotEmpty()) {
                Text(
                    text = "标签：" + question.tags.joinToString(" / ") { it.fullPath },
                    fontSize = 13.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text("下一题")
            }
        }
    }
}

@Composable
private fun OptionItem(
    option: QuestionOptionEntity,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val background = if (selected) Color(0xFFB3E5FC) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = option.content,
            fontSize = 16.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}

@Composable
private fun MarkdownContent(content: String) {
    Markdown(
        modifier = Modifier.padding(vertical = 12.dp),
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
            )
        )
    )
}
