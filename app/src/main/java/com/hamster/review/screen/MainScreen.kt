package com.hamster.review.screen

import androidx.compose.foundation.clickable
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
        if (subjects.isEmpty()) {
            Text(
                modifier = Modifier.padding(24.dp),
                text = "暂无科目，请先导入题库",
                fontSize = 18.sp
            )
        } else {
            subjects.forEach { subject ->
                SubjectCard(
                    subjectName = subject.subject.name,
                    todayCount = subject.todayCount,
                    subjectId = subject.subject.id,
                    sharedTiltState = sharedTiltState,
                    onNavigate = onNavigate,
                    setTopbarTitle = setTopbarTitle
                )
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
        Text(text = subjectName, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            text = "今日待复习：$todayCount 题",
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}
