package com.hamster.review.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.hamster.review.compose.SharedTiltState
import com.hamster.review.compose.SliderDialog
import com.hamster.review.compose.rememberSharedTiltState
import com.hamster.review.data.db.SubjectWithTodayCount
import kotlin.math.min
import kotlin.math.roundToInt

@Composable
fun MainScreen(
    subjects: List<SubjectWithTodayCount>,
    onSetDailyLimit: (Long, Int) -> Unit,
    onAddSubject: (String) -> Unit,
    onDeleteSubject: (Long) -> Unit,
    onUpdateOfficialBank: () -> Unit,
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

    longPressSubject?.let { subject ->
        OptionDialog(
            title = subject.subject.name,
            options = listOf("新增题目", "设置每日题目数量", "删除科目", "已掌握题目测试"),
            initialSelections = setOf(0),
            singleSelect = true,
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
                    deleteSubject = subject
                }
                if (3 in selected) {
                    onNavigate(com.hamster.review.Review(subject.subject.id, "mastered_test"))
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
            title = "新增科目",
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

    PageColumn(sharedTiltState = sharedTiltState) {
        if (!subjects.isEmpty()) {
            subjects.forEachIndexed { index, subject ->
                SubjectCard(
                    subjectName = subject.subject.name,
                    todayCount = subject.todayCount,
                    dailyLimit = subject.subject.dailyLimit,
                    availableCount = subject.availableCount,
                    subjectId = subject.subject.id,
                    sharedTiltState = sharedTiltState,
                    onNavigate = onNavigate,
                    setTopbarTitle = setTopbarTitle,
                    onLongPress = { longPressSubject = subject }
                )

                Spacer(modifier = Modifier.height(dimensionResource(R.dimen.item_group_gap)))
            }
        }

        ItemGroup(titleState = sharedTiltState) {
            ClickItem(
                title = "新增科目",
                icon = R.drawable.add_line
            ) {
                showAddSubjectDialog = true
            }

        }

        Spacer(modifier = Modifier.height(dimensionResource(R.dimen.item_group_gap)))

        ItemGroup(titleState = sharedTiltState) {
            ClickItem(
                title = "更新题库",
                icon = R.drawable.update
            ) {
                showBankUpdateDialog = true
            }
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
    onLongPress: () -> Unit
) {
    // 当天目标 = min(每日数量, 未掌握题数)；为 0 时不显示进度与计数
    val dailyTarget = min(dailyLimit, availableCount)
    val remaining = todayCount.coerceIn(0, dailyTarget)

    ItemGroup(
        modifier = Modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {
                setTopbarTitle(subjectName)
                onNavigate(com.hamster.review.Review(subjectId))
            },
            onLongClick = onLongPress
        ),
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
