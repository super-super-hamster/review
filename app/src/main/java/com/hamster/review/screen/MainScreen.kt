package com.hamster.review.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.hamster.review.AddQuestion
import com.hamster.review.R
import com.hamster.review.Route
import com.hamster.review.compose.ClickItem
import com.hamster.review.compose.EditTextDialog
import com.hamster.review.compose.InquiryDialog
import com.hamster.review.compose.ItemGroup
import com.hamster.review.compose.OptionDialog
import com.hamster.review.compose.PageColumn
import com.hamster.review.compose.ProgressBar
import com.hamster.review.compose.ProgressDialog
import com.hamster.review.compose.SharedTiltState
import com.hamster.review.compose.SliderDialog
import com.hamster.review.compose.rememberSharedTiltState
import com.hamster.review.data.db.SubjectWithTodayCount
import com.hamster.review.viewModel.MainViewModel
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    mainViewModel: MainViewModel,
    subjects: List<SubjectWithTodayCount>,
    onSetDailyLimit: (Long, Int) -> Unit,
    onAddSubject: (String) -> Unit,
    onDeleteSubject: (Long) -> Unit,
    onUpdateOfficialBank: () -> Unit,
    homeExpanded: Boolean,
    onHomeExpandedChange: (Boolean) -> Unit,
    homeTopSubjectId: Long?,
    onHomeTopSubjectIdChange: (Long?) -> Unit,
    onNavigate: (Route) -> Unit,
    setTopbarTitle: (String) -> Unit
) {
    val sharedTiltState = rememberSharedTiltState()

    var longPressSubject by remember { mutableStateOf<SubjectWithTodayCount?>(null) }
    var chooseTypeSubject by remember { mutableStateOf<SubjectWithTodayCount?>(null) }
    var limitSubject by remember { mutableStateOf<SubjectWithTodayCount?>(null) }
    var deleteSubject by remember { mutableStateOf<SubjectWithTodayCount?>(null) }
    var showLimitDialog by remember { mutableStateOf(false) }
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showBankUpdateDialog by remember { mutableStateOf(false) }

    setTopbarTitle("首页")

    val orderedSubjects = remember(subjects, homeTopSubjectId) {
        if (subjects.isEmpty()) {
            emptyList()
        } else {
            val start = subjects.indexOfFirst { it.subject.id == homeTopSubjectId }
                .takeIf { it >= 0 } ?: 0
            subjects.drop(start) + subjects.take(start)
        }
    }

    LaunchedEffect(subjects, homeTopSubjectId) {
        if (subjects.isEmpty()) {
            if (homeTopSubjectId != null) onHomeTopSubjectIdChange(null)
        } else if (subjects.none { it.subject.id == homeTopSubjectId }) {
            onHomeTopSubjectIdChange(subjects.first().subject.id)
        }
    }

    longPressSubject?.let { subject ->
        OptionDialog(
            title = subject.subject.name,
            options = listOf("创建题目", "设置每日题目数量", "已掌握题目测试", "管理题目", "删除科目"),
            initialSelections = setOf(0),
            singleSelect = true,
            optionSelectedColors = listOf(null, null, null, null, colorResource(R.color.red)),
            onDismissRequest = { longPressSubject = null },
            onCancel = { longPressSubject = null },
            onConfirm = { selected ->
                if (0 in selected) {
                    chooseTypeSubject = subject
                }
                if (1 in selected) {
                    limitSubject = subject
                    showLimitDialog = true
                }
                if (2 in selected) {
                    onNavigate(com.hamster.review.Review(subject.subject.id, "mastered_test"))
                }
                if (3 in selected) {
                    onNavigate(com.hamster.review.ManageQuestions(subject.subject.id))
                }
                if (4 in selected) {
                    deleteSubject = subject
                }
                longPressSubject = null
            }
        )
    }

    chooseTypeSubject?.let { subject ->
        OptionDialog(
            title = "选择题目类型",
            options = listOf("单选题", "多选题", "判断题", "填空题"),
            singleSelect = true,
            onDismissRequest = { chooseTypeSubject = null },
            onCancel = { chooseTypeSubject = null },
            onConfirm = { selected ->
                val typeName = when {
                    0 in selected -> "SINGLE_CHOICE"
                    1 in selected -> "MULTIPLE_CHOICE"
                    2 in selected -> "TRUE_FALSE"
                    3 in selected -> "FILL_BLANK"
                    else -> null
                }
                if (typeName != null) {
                    onNavigate(AddQuestion(subject.subject.id, typeName))
                }
                chooseTypeSubject = null
            }
        )
    }

    deleteSubject?.let { subject ->
        InquiryDialog(
            title = "删除科目",
            content = "确定要删除科目「${subject.subject.name}」吗？",
            confirmText = "删除",
            confirmColor = colorResource(R.color.red),
            onCancel = { deleteSubject = null },
            onDismissRequest = { deleteSubject = null },
            onConfirm = {
                onDeleteSubject(subject.subject.id)
                deleteSubject = null
                true
            }
        )
    }

    if (showLimitDialog) {
        val subject = limitSubject
        if (subject != null) {
            val maxLimit = min(200, subject.totalCount.coerceAtLeast(10))
            val currentLimit = subject.subject.dailyLimit.coerceIn(1, maxLimit)
            SliderDialog(
                title = "设置每日题目数量",
                content = "${subject.subject.name}：每日题目数量",
                value = currentLimit.toFloat(),
                onValueChange = { newValue ->
                    // 允许 1，其余保持 10 的步进
                    val stepped = if (newValue < 5.5f) {
                        1
                    } else {
                        ((newValue / 10f).roundToInt() * 10).coerceAtLeast(10)
                    }
                    limitSubject = subject.copy(
                        subject = subject.subject.copy(dailyLimit = stepped.coerceIn(1, maxLimit))
                    )
                },
                valueRange = 1f..maxLimit.toFloat(),
                onDismissRequest = { showLimitDialog = false },
                onCancel = { showLimitDialog = false },
                onConfirm = {
                    val finalLimit = (limitSubject?.subject?.dailyLimit ?: currentLimit)
                        .coerceIn(1, maxLimit)
                    onSetDailyLimit(subject.subject.id, finalLimit)
                    showLimitDialog = false
                    true
                }
            )
        }
    }

    if (showAddSubjectDialog) {
        EditTextDialog(
            title = "创建科目",
            initialValue = "",
            hint = "请输入科目名称",
            singleLine = true,
            maxLength = 20,
            validate = { input ->
                val name = input.trim()
                when {
                    name.isEmpty() -> "科目名称不能为空"
                    name.length > 20 -> "科目名称不能超过 20 个字符"
                    subjects.any { it.subject.name == name } -> "已存在同名科目：$name"
                    else -> null
                }
            },
            onCancel = { showAddSubjectDialog = false },
            onDismissRequest = { showAddSubjectDialog = false },
            onConfirm = { input ->
                onAddSubject(input.trim())
                true
            }
        )
    }

    PageColumn(modifier = Modifier.verticalScroll(rememberScrollState()), sharedTiltState = sharedTiltState) {
        ItemGroup(titleState = sharedTiltState) {
            ClickItem(
                title = "创建科目",
                icon = R.drawable.add_line
            ) {
                showAddSubjectDialog = true
            }

            ClickItem(
                title = "更新题库",
                icon = R.drawable.update
            ) {
                showBankUpdateDialog = true
            }
        }

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.item_group_gap)))

        if (subjects.isNotEmpty()) {
            SubjectStack(
                ordered = orderedSubjects,
                expanded = homeExpanded,
                onExpandChange = onHomeExpandedChange,
                sharedTiltState = sharedTiltState,
                onNavigate = onNavigate,
                setTopbarTitle = setTopbarTitle,
                onLongPress = { longPressSubject = it },
                onSwipeTop = { newTopId -> onHomeTopSubjectIdChange(newTopId) }
            )

            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.item_group_gap)))
        }

        if (showBankUpdateDialog) {
            InquiryDialog(
                title = "更新题库",
                content = "将下载最新题库并覆盖当前题库。\n用户创建的题目不会被覆盖",
                confirmText = "更新",
                onCancel = { showBankUpdateDialog = false },
                onDismissRequest = { showBankUpdateDialog = false },
                onConfirm = {
                    showBankUpdateDialog = false
                    onUpdateOfficialBank()
                    true
                }
            )
        }

        mainViewModel.bankUpdateState?.let { update ->
            ProgressDialog(
                title = "更新题库",
                text = update.text,
                progress = update.progress,
                onCancel = {
                    if (update.cancelable) mainViewModel.cancelBankUpdate()
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubjectCard(
    subjectName: String,
    todayCount: Int,
    dailyLimit: Int,
    availableCount: Int,
    subjectId: Long,
    sharedTiltState: SharedTiltState,
    onNavigate: (Route) -> Unit,
    setTopbarTitle: (String) -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true
) {
    // 当天目标 = min(每日数量, 未掌握题数)；为 0 时不显示进度与计数
    val dailyTarget = min(dailyLimit, availableCount)
    val remaining = todayCount.coerceIn(0, dailyTarget)

    ItemGroup(
        modifier = if (interactive) {
            modifier.combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    setTopbarTitle(subjectName)
                    onNavigate(com.hamster.review.Review(subjectId))
                },
                onLongClick = onLongPress
            )
        } else {
            modifier
        },
        contentModifier = Modifier
            .height(128.dp)
            .padding(24.dp),
        titleState = sharedTiltState
    ) {
        Text(
            text = subjectName,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        if (dailyTarget > 0) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                modifier = Modifier.padding(start = 4.dp),
                text = "${dailyTarget - remaining} / $dailyTarget",
                fontSize = 12.sp,
                fontWeight = FontWeight.Light
            )

            ProgressBar(
                modifier = Modifier
                    .height(8.dp)
                    .fillMaxWidth(),
                progress = (dailyTarget - remaining).toFloat() / dailyTarget
            )
        }
    }
}

/** 堆叠槽位参数：近大远小 + 间距递增（非等差），底层不透明。 */
internal data class StackSlot(val scale: Float, val x: Dp, val y: Dp)

private val STACK_SLOTS = listOf(
    StackSlot(scale = 1.00f, x = 0.dp, y = 0.dp),
    StackSlot(scale = 0.94f, x = 10.dp, y = 12.dp),
    StackSlot(scale = 0.88f, x = 18.dp, y = 22.dp)
)

private val StackSpring = spring<Float>(dampingRatio = 0.85f, stiffness = 350f)

internal data class StackCardPlacement(
    val slot: StackSlot,
    val translationX: Float,
    val zIndex: Float,
    val visible: Boolean
)

private fun stackSlot(index: Int) = STACK_SLOTS[min(index, STACK_SLOTS.lastIndex)]

/** 所有卡片共享一次切换进度，终点与重排后的静态槽位完全一致。 */
internal fun stackCardPlacement(
    index: Int,
    size: Int,
    dragX: Float,
    screenWidthPx: Float,
    settleProgress: Float
): StackCardPlacement {
    val progress = settleProgress.coerceIn(0f, 1f)
    val previous = dragX > 0f && size > 1
    val next = dragX < 0f && size > 1
    if (previous && index == size - 1) {
        return StackCardPlacement(
            slot = STACK_SLOTS[0],
            translationX = (dragX - screenWidthPx) * (1f - progress),
            zIndex = 200f,
            visible = true
        )
    }

    val start = stackSlot(index)
    val end = when {
        previous -> stackSlot(index + 1)
        next && index > 0 -> stackSlot(index - 1)
        else -> start
    }
    return StackCardPlacement(
        slot = StackSlot(
            scale = start.scale + (end.scale - start.scale) * progress,
            x = start.x + (end.x - start.x) * progress,
            y = start.y + (end.y - start.y) * progress
        ),
        translationX = when {
            index != 0 -> 0f
            previous -> dragX * (1f - progress)
            next -> dragX + (-screenWidthPx - dragX) * progress
            else -> 0f
        },
        zIndex = (100 - index).toFloat(),
        visible = index < 3 || (next && index == 3)
    )
}

@Composable
private fun SubjectStack(
    ordered: List<SubjectWithTodayCount>,
    expanded: Boolean,
    onExpandChange: (Boolean) -> Unit,
    sharedTiltState: SharedTiltState,
    onNavigate: (Route) -> Unit,
    setTopbarTitle: (String) -> Unit,
    onLongPress: (SubjectWithTodayCount) -> Unit,
    onSwipeTop: (Long) -> Unit
) {
    if (ordered.isEmpty()) return
    val size = ordered.size
    val canSwitch = size > 1

    val density = LocalDensity.current
    val screenWidthPx = with(density) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val thresholdPx = with(density) { 60.dp.toPx() }
    val cardHeight = 128.dp
    val gap = dimensionResource(R.dimen.item_group_gap)

    // 展开/收起进度
    val expansion = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) {
        expansion.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = StackSpring
        )
    }

    val collapsedLayers = min(size, 3)
    val collapsedHeight = cardHeight + STACK_SLOTS[collapsedLayers - 1].y
    val expandedHeight = (cardHeight + gap) * size
    val containerHeight by animateDpAsState(
        targetValue = if (expanded) expandedHeight else collapsedHeight,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 350f),
        label = "stackHeight"
    )

    var dragX by remember { mutableFloatStateOf(0f) }
    var settleProgress by remember { mutableFloatStateOf(0f) }
    var isSettling by remember { mutableStateOf(false) }
    var swipeOrder by remember { mutableStateOf<List<SubjectWithTodayCount>?>(null) }
    var pendingTopId by remember { mutableStateOf<Long?>(null) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    fun resetSwipe() {
        dragX = 0f
        settleProgress = 0f
        swipeOrder = null
        pendingTopId = null
        isSettling = false
    }

    // 数据成员变化或展开时取消手势；单纯循环重排不打断交接。
    LaunchedEffect(ordered.map { it.subject.id }.toSet(), expanded) {
        settleJob?.cancel()
        resetSwipe()
    }
    LaunchedEffect(ordered.first().subject.id, pendingTopId) {
        if (pendingTopId == ordered.first().subject.id) resetSwipe()
    }

    val dragState = rememberDraggableState { delta ->
        if (!isSettling) {
            dragX = (dragX + delta).coerceIn(-screenWidthPx, screenWidthPx)
        }
    }

    fun finishDrag() {
        if (isSettling) return
        val source = swipeOrder ?: ordered
        if (source.size < 2) {
            resetSwipe()
            return
        }
        val target = when {
            dragX < -thresholdPx -> source[1].subject.id
            dragX > thresholdPx -> source.last().subject.id
            else -> null
        }
        isSettling = true
        settleJob = scope.launch {
            if (target == null) {
                animate(
                    initialValue = dragX,
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 1f, stiffness = 350f)
                ) { value, _ -> dragX = value }
                resetSwipe()
            } else {
                animate(0f, 1f, animationSpec = tween(200)) { value, _ ->
                    settleProgress = value
                }
                // 保留动画终态，直到父级确实应用新顺序后再一起清理。
                pendingTopId = target
                onSwipeTop(target)
            }
        }
    }

    val displayed = swipeOrder ?: ordered
    val moving = !expanded && canSwitch && dragX != 0f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(containerHeight)
    ) {
        // 离场卡片的底层占位始终不透明，交接时由真实卡片在同一位置接替。
        // 右滑时占住上一张原来的槽位，避免只有两三张卡片时底层突然露空。
        if (moving && (dragX < 0f || size <= 3)) {
            val backingSubject = if (dragX < 0f) displayed.first() else displayed.last()
            StackCard(
                subject = backingSubject,
                expansion = 0f,
                listY = 0.dp,
                stackSlot = stackSlot(size - 1),
                sharedTiltState = sharedTiltState,
                onNavigate = onNavigate,
                setTopbarTitle = setTopbarTitle,
                onLongPress = {},
                interactive = false,
                cardModifier = Modifier.clearAndSetSemantics {},
                zIndex = 0f
            )
        }

        displayed.forEachIndexed { index, subject ->
            val placement = stackCardPlacement(
                index, size, if (moving) dragX else 0f, screenWidthPx, settleProgress
            )
            if (placement.visible || expansion.value != 0f) {
                key(subject.subject.id) {
                    val isTop = index == 0 && !expanded
                    val isIncoming = moving &&
                        ((dragX > 0f && index == size - 1) || (dragX < 0f && index == 1))
                    StackCard(
                        subject = subject,
                        expansion = expansion.value,
                        listY = (cardHeight + gap) * index,
                        stackSlot = placement.slot,
                        sharedTiltState = sharedTiltState,
                        onNavigate = onNavigate,
                        setTopbarTitle = setTopbarTitle,
                        onLongPress = { onLongPress(subject) },
                        interactive = !isSettling && (expanded || index == 0),
                        cardModifier = if (isTop && canSwitch) {
                            Modifier.draggable(
                                state = dragState,
                                orientation = Orientation.Horizontal,
                                enabled = !isSettling && expansion.value == 0f,
                                onDragStarted = {
                                    swipeOrder = ordered
                                },
                                onDragStopped = { finishDrag() }
                            )
                        } else {
                            Modifier
                        },
                        extraTranslationX = placement.translationX,
                        zIndex = placement.zIndex,
                        overlay = {
                            if (canSwitch && (index == 0 || isIncoming)) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(24.dp)
                                        .clickable(
                                            enabled = !isSettling && dragX == 0f,
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            onExpandChange(!expanded)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (expanded) R.drawable.arrow_up_line else R.drawable.arrow_down_line
                                        ),
                                        contentDescription = if (expanded) "收起科目" else "展开所有科目",
                                        tint = colorResource(R.color.icon),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun StackCard(
    subject: SubjectWithTodayCount,
    expansion: Float,
    listY: Dp,
    stackSlot: StackSlot,
    sharedTiltState: SharedTiltState,
    onNavigate: (Route) -> Unit,
    setTopbarTitle: (String) -> Unit,
    onLongPress: () -> Unit,
    interactive: Boolean,
    cardModifier: Modifier = Modifier,
    extraTranslationX: Float = 0f,
    zIndex: Float,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val e = expansion
    val scale = stackSlot.scale + (1f - stackSlot.scale) * e
    val x = stackSlot.x * (1f - e)
    val y = stackSlot.y + (listY - stackSlot.y) * e

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .offset(x = x, y = y)
            .graphicsLayer {
                // 左上锚点缩放，保证右下方向的堆叠偏移可见
                transformOrigin = TransformOrigin(0f, 0f)
                scaleX = scale
                scaleY = scale
                translationX = extraTranslationX
            }
            .zIndex(zIndex)
            .then(cardModifier)
    ) {
        SubjectCard(
            subjectName = subject.subject.name,
            todayCount = subject.todayCount,
            dailyLimit = subject.subject.dailyLimit,
            availableCount = subject.availableCount,
            subjectId = subject.subject.id,
            sharedTiltState = sharedTiltState,
            onNavigate = onNavigate,
            setTopbarTitle = setTopbarTitle,
            onLongPress = onLongPress,
            interactive = interactive
        )

        overlay()
    }
}
