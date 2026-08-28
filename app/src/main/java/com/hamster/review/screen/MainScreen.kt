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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamster.review.Route
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
    onNavigate: (Route) -> Unit,
    setTopbarTitle: (String) -> Unit
) {
    val sharedTiltState = rememberSharedTiltState()

    var longPressSubject by remember { mutableStateOf<SubjectWithTodayCount?>(null) }
    var limitSubject by remember { mutableStateOf<SubjectWithTodayCount?>(null) }
    var showLimitDialog by remember { mutableStateOf(false) }

    setTopbarTitle("首页")

    longPressSubject?.let { subject ->
        OptionDialog(
            title = subject.subject.name,
            options = listOf("新增题目", "设置每日题目数量"),
            singleSelect = true,
            onDismissRequest = { longPressSubject = null },
            onCancel = { longPressSubject = null },
            onConfirm = { selected ->
                if (1 in selected) {
                    limitSubject = subject
                    showLimitDialog = true
                }
                longPressSubject = null
            }
        )
    }

    if (showLimitDialog) {
        val subject = limitSubject
        if (subject != null) {
            val maxLimit = min(200, subject.totalCount.coerceAtLeast(10))
            SliderDialog(
                title = "设置每日题目数量",
                content = "${subject.subject.name}：每日题目数量",
                value = subject.subject.dailyLimit.toFloat(),
                onValueChange = { newValue ->
                    val stepped = (newValue / 10f).roundToInt() * 10
                    limitSubject = subject.copy(
                        subject = subject.subject.copy(dailyLimit = stepped.coerceIn(10, maxLimit))
                    )
                },
                valueRange = 10f..maxLimit.toFloat(),
                onDismissRequest = { showLimitDialog = false },
                onCancel = { showLimitDialog = false },
                onConfirm = {
                    val finalLimit = limitSubject?.subject?.dailyLimit ?: subject.subject.dailyLimit
                    onSetDailyLimit(subject.subject.id, finalLimit)
                    showLimitDialog = false
                    true
                }
            )
        }
    }

    PageColumn(sharedTiltState = sharedTiltState) {
        if (!subjects.isEmpty()) {
            subjects.forEachIndexed { index, subject ->
                SubjectCard(
                    subjectName = subject.subject.name,
                    todayCount = subject.todayCount,
                    dailyLimit = subject.subject.dailyLimit,
                    subjectId = subject.subject.id,
                    sharedTiltState = sharedTiltState,
                    onNavigate = onNavigate,
                    setTopbarTitle = setTopbarTitle,
                    onLongPress = { longPressSubject = subject }
                )

                if (index < subjects.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SubjectCard(
    subjectName: String,
    todayCount: Int,
    dailyLimit: Int,
    subjectId: Long,
    sharedTiltState: SharedTiltState,
    onNavigate: (Route) -> Unit,
    setTopbarTitle: (String) -> Unit,
    onLongPress: () -> Unit
) {
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

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            modifier = Modifier.padding(start = 4.dp),
            text = "${(dailyLimit - todayCount).coerceAtLeast(0)} / $dailyLimit",
            fontSize = 12.sp,
            fontWeight = FontWeight.Light
        )

        ProgressBar(
            modifier = Modifier
                .height(8.dp)
                .fillMaxWidth(),
            progress = if (dailyLimit == 0) 0f else (dailyLimit - todayCount).toFloat() / dailyLimit
        )
    }
}
