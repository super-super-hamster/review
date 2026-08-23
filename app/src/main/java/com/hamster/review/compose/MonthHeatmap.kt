package com.hamster.review.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hamster.review.data.db.DailyRecordEntity
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun SingleMonthHeatmap(
    records: List<DailyRecordEntity>,
    modifier: Modifier = Modifier
) {
    val currentMonth = YearMonth.now()
    val dataMap = remember(records) {
        records.associate { LocalDate.parse(it.date) to it.reviewCount }
    }
    val firstDay = currentMonth.atDay(1)
    val startOffset = firstDay.dayOfWeek.value - 1
    val daysInMonth = currentMonth.lengthOfMonth()

    val cells = buildList<LocalDate?> {
        repeat(startOffset) { add(null) }
        for (day in 1..daysInMonth) {
            add(currentMonth.atDay(day))
        }
    }

    Column(modifier = modifier) {
        Row(modifier = Modifier.padding(bottom = 4.dp)) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center
                )
            }
        }
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { index ->
                    val date = week.getOrNull(index)
                    val count = date?.let { dataMap[it] } ?: 0
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .height(20.dp)
                            .background(
                                color = when {
                                    date == null -> Color.Transparent
                                    count <= 0 -> Color(0xFFEBEDF0)
                                    count == 1 -> Color(0xFFC6E48B)
                                    count <= 3 -> Color(0xFF7BC96F)
                                    count <= 5 -> Color(0xFF239A3B)
                                    else -> Color(0xFF196127)
                                }
                            )
                    )
                }
            }
        }
    }
}
