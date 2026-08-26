package com.hamster.review.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamster.review.Route
import com.hamster.review.compose.ItemGroup
import com.hamster.review.compose.PageColumn
import com.hamster.review.compose.ProgressBar
import com.hamster.review.compose.SharedTiltState
import com.hamster.review.compose.rememberSharedTiltState
import com.hamster.review.data.db.SubjectWithTodayCount

@Composable
fun MainScreen(
    subjects: List<SubjectWithTodayCount>,
    onNavigate: (Route) -> Unit,
    setTopbarTitle: (String) -> Unit
) {
    val sharedTiltState = rememberSharedTiltState()

    setTopbarTitle("首页")

    PageColumn(sharedTiltState = sharedTiltState) {
        if (!subjects.isEmpty()) {
            subjects.forEachIndexed { index, subject ->
                SubjectCard(
                    subjectName = subject.subject.name,
                    todayCount = subject.todayCount,
                    subjectId = subject.subject.id,
                    sharedTiltState = sharedTiltState,
                    onNavigate = onNavigate,
                    setTopbarTitle = setTopbarTitle
                )

                if (index < subjects.size - 1) {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun SubjectCard(
    subjectName: String,
    todayCount: Int,
    subjectId: Long,
    sharedTiltState: SharedTiltState,
    onNavigate: (Route) -> Unit,
    setTopbarTitle: (String) -> Unit
) {
    ItemGroup(
        modifier = Modifier.clickable(
            enabled = true,
            onClick = {
                setTopbarTitle(subjectName)
                onNavigate(com.hamster.review.Review(subjectId))
            }
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
            text = "${10 - todayCount} / 10",  // TODO: 改成实际总数
            fontSize = 12.sp,
            fontWeight = FontWeight.Light
        )

        ProgressBar(
            modifier = Modifier
                .height(8.dp)
                .fillMaxWidth(),
            progress = (10 - todayCount).toFloat() / 10
        ) {
            // TODO: 设置刷题数量，用Slider
        }
    }
}
