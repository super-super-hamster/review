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
import com.hamster.review.Review
import com.hamster.review.Route
import com.hamster.review.compose.ItemGroup
import com.hamster.review.compose.PageColumn
import com.hamster.review.compose.SharedTiltState
import com.hamster.review.compose.rememberSharedTiltState

@Composable
fun MainScreen(
    onNavigate: (Route) -> Unit,
    setTopbarTitle: (String) -> Unit
) {
    val sharedTiltState = rememberSharedTiltState()

    PageColumn(sharedTiltState = sharedTiltState) {
        SubjectCard("test", sharedTiltState, onNavigate, setTopbarTitle)
    }
}

@Composable
fun SubjectCard(
    subjectName: String,
    sharedTiltState: SharedTiltState,
    onNavigate: (Route) -> Unit,
    setTopbarTitle: (String) -> Unit
) {
    ItemGroup(
        modifier = Modifier.clickable(
            enabled = true,
            onClick = {
                setTopbarTitle(subjectName)
                onNavigate(Review)
            }
        ),
        contentModifier = Modifier
            .height(128.dp)
            .padding(24.dp),
        titleState = sharedTiltState
    ) {
        Text(text = subjectName, fontSize = 24.sp, fontWeight = FontWeight.Bold)

//        Heatmap()
    }
}