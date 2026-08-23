package com.hamster.review.compose

import androidx.annotation.Keep
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.annotations.SerializedName
import com.hamster.review.R
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.ceil

@Composable
fun <T> LineChart(
    data: List<T>,
    yValueMapper: (T) -> Float,
    xLabelMapper: (Int, T) -> String?,
    modifier: Modifier = Modifier,
    lineColor: Color = colorResource(R.color.mikuGreen),
    dotColor: Color = colorResource(R.color.mikuGreen),
    textColor: Color = colorResource(R.color.text),
    maxYValue: Float? = null,
    isScrollable: Boolean = false,
    pointSpacing: Dp = 64.dp,
    horizontalLines: List<HorizontalLine> = emptyList(),
    onPointClick: ((index: Int, item: T) -> Unit)? = null
) {
    if (data.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    val textStyle = TextStyle(color = textColor, fontSize = 10.sp)
    val labelStyle = TextStyle(color = textColor.copy(alpha = 0.6f), fontSize = 9.sp)

    val computedMaxY = remember(data, maxYValue, horizontalLines) {
        val dataMax = data.maxOfOrNull { yValueMapper(it) } ?: 0f
        val linesMax = horizontalLines.maxOfOrNull { it.value } ?: 0f
        val max = maxYValue ?: maxOf(dataMax, linesMax)
        if (max <= 0f) 1f else max
    }

    val scrollState = rememberScrollState()

    val pointCoordinates = remember { mutableMapOf<Int, Offset>() }

    val canvasModifier = if (isScrollable) {
        val extraPadding = 40.dp
        val calculatedWidth = if (data.size > 1) {
            (pointSpacing * (data.size - 1)) + extraPadding
        } else {
            pointSpacing + extraPadding
        }
        modifier.horizontalScroll(scrollState).width(calculatedWidth)
    } else {
        modifier.fillMaxWidth()
    }

    val finalModifier = canvasModifier
        .height(220.dp)
        .pointerInput(data, computedMaxY) { // 将依赖项传入，数据变化时重新绑定
            detectTapGestures { tapOffset ->
                // 定义点击的有效半径24.dp
                val clickThreshold = 24.dp.toPx()
                var clickedIndex = -1
                var minDistance = Float.MAX_VALUE

                for ((index, pointOffset) in pointCoordinates) {
                    val distance = (pointOffset - tapOffset).getDistance()
                    if (distance < clickThreshold && distance < minDistance) {
                        minDistance = distance
                        clickedIndex = index
                    }
                }

                if (clickedIndex != -1 && onPointClick != null) {
                    onPointClick(clickedIndex, data[clickedIndex])
                }
            }
        }

    Canvas(modifier = finalModifier) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val paddingBottom = 40.dp.toPx()
        val paddingTop = 20.dp.toPx()
        val paddingX = if (isScrollable) 20.dp.toPx() else 0f
        val graphHeight = canvasHeight - paddingBottom - paddingTop
        val graphWidth = canvasWidth - (paddingX * 2)

        horizontalLines.forEach { line ->
            val ratio = line.value / computedMaxY
            val y = paddingTop + graphHeight - (ratio * graphHeight)

            drawLine(
                color = line.color ?: textColor.copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(canvasWidth, y),
                strokeWidth = 1.dp.toPx(),
            )

            val textLayoutResult = textMeasurer.measure(line.label, labelStyle)
            val labelX = if (isScrollable) scrollState.value.toFloat() + 4.dp.toPx() else 4.dp.toPx()

            drawText(
                textMeasurer = textMeasurer,
                text = line.label,
                style = labelStyle,
                topLeft = Offset(labelX, y - textLayoutResult.size.height)
            )
        }

        val stepX = if (data.size > 1) graphWidth / (data.size - 1) else graphWidth / 2f
        val path = Path()
        val points = mutableListOf<Offset>()

        pointCoordinates.clear()

        data.forEachIndexed { index, item ->
            val x = paddingX + if (data.size > 1) index * stepX else stepX
            val yVal = yValueMapper(item)
            val ratio = yVal / computedMaxY
            val y = paddingTop + graphHeight - (ratio * graphHeight)

            val offset = Offset(x, y)
            points.add(offset)

            pointCoordinates[index] = offset

            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        points.forEachIndexed { index, point ->
            drawCircle(color = dotColor, radius = 4.dp.toPx(), center = point)
            val label = xLabelMapper(index, data[index])
            if (label != null) {
                val textLayoutResult = textMeasurer.measure(label, textStyle)

                var textStartX = point.x - textLayoutResult.size.width / 2f
                if (textStartX < 0f) textStartX = 0f
                if (textStartX + textLayoutResult.size.width > canvasWidth) {
                    textStartX = canvasWidth - textLayoutResult.size.width.toFloat()
                }

                drawText(
                    textMeasurer = textMeasurer,
                    text = label,
                    style = textStyle,
                    topLeft = Offset(
                        x = textStartX,
                        y = canvasHeight - paddingBottom + 10.dp.toPx()
                    )
                )
            }
        }

        drawLine(
            color = textColor.copy(alpha = 0.3f),
            start = Offset(0f, canvasHeight - paddingBottom),
            end = Offset(canvasWidth, canvasHeight - paddingBottom),
            strokeWidth = 1.dp.toPx()
        )
    }
}

@Composable
fun Heatmap(
    modifier: Modifier = Modifier,
    dataMap: Map<Long, Int>,
    levelColors: List<Color>,
    endMonth: YearMonth = YearMonth.now(),
    verticalGap: Dp = 24.dp,
    horizontalGap: Dp = 24.dp,
    emptyColor: Color = colorResource(R.color.heatmap_empty),
) {
    val normalizedDataMap = remember(dataMap) {
        val zoneId = ZoneId.systemDefault()
        val map = mutableMapOf<LocalDate, Int>()
        dataMap.forEach { (timestamp, level) ->
            val date = Instant.ofEpochMilli(timestamp)
                .atZone(zoneId)
                .toLocalDate()
            map[date] = level
        }
        map
    }

    val textMeasurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.labelMedium.copy(color = Color.Gray)

    // 控制弹窗的状态
    var selectedMonth by remember { mutableStateOf<YearMonth?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val cols = 2
        val rows = 6

        val cellGap = 4.dp
        val titleHeight = 24.dp

        val monthBlockW = (maxWidth - horizontalGap * (cols - 1)) / cols
        val cellSize = (monthBlockW - cellGap * 6) / 7

        val monthBlockH = titleHeight + (cellSize * 6) + (cellGap * 5)
        val totalCanvasHeight = (monthBlockH * rows) + (verticalGap * (rows - 1))
        val scrollState = rememberScrollState()

        val startMonth = endMonth.minusMonths(11)

        LaunchedEffect(scrollState.maxValue) {
            scrollState.scrollTo(scrollState.maxValue)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalCanvasHeight)
                    // 添加点击事件监听
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val monthBlockWPx = monthBlockW.toPx()
                            val monthBlockHPx = monthBlockH.toPx()
                            val monthGapXPx = horizontalGap.toPx()
                            val monthGapYPx = verticalGap.toPx()

                            // 计算点击所在的行和列
                            val c = (offset.x / (monthBlockWPx + monthGapXPx)).toInt()
                            val r = (offset.y / (monthBlockHPx + monthGapYPx)).toInt()

                            // 确保点击的不是间隔空白处
                            val xInCell = offset.x % (monthBlockWPx + monthGapXPx)
                            val yInCell = offset.y % (monthBlockHPx + monthGapYPx)

                            if (c < cols && r < rows && xInCell <= monthBlockWPx && yInCell <= monthBlockHPx) {
                                val index = r * cols + c
                                if (index < 12) {
                                    selectedMonth = startMonth.plusMonths(index.toLong())
                                }
                            }
                        }
                    }
            ) {
                val monthBlockWPx = monthBlockW.toPx()
                val monthBlockHPx = monthBlockH.toPx()
                val monthGapXPx = horizontalGap.toPx()
                val monthGapYPx = verticalGap.toPx()
                val cellSizePx = cellSize.toPx()
                val cellGapPx = cellGap.toPx()
                val titleHeightPx = titleHeight.toPx()

                for (i in 0 until 12) {
                    val currentMonth = startMonth.plusMonths(i.toLong())
                    val r = i / cols
                    val c = i % cols

                    val offsetX = c * (monthBlockWPx + monthGapXPx)
                    val offsetY = r * (monthBlockHPx + monthGapYPx)

                    translate(left = offsetX, top = offsetY) {
                        // 绘制月份标题
                        val monthText = "${currentMonth.monthValue}月"
                        drawText(
                            textMeasurer = textMeasurer,
                            text = monthText,
                            style = textStyle,
                            topLeft = Offset(0f, 0f)
                        )

                        // 绘制日历网格
                        val daysInMonth = currentMonth.lengthOfMonth()
                        val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value
                        val startColIndex = firstDayOfWeek - 1

                        for (day in 1..daysInMonth) {
                            val date = currentMonth.atDay(day)
                            val level = normalizedDataMap[date] ?: -1

                            val color = if (level in levelColors.indices) {
                                levelColors[level]
                            } else {
                                emptyColor
                            }

                            val dayGridIndex = startColIndex + (day - 1)
                            val dayRow = dayGridIndex / 7
                            val dayCol = dayGridIndex % 7

                            val cellX = dayCol * (cellSizePx + cellGapPx)
                            val cellY = titleHeightPx + dayRow * (cellSizePx + cellGapPx)

                            drawRoundRect(
                                color = color,
                                topLeft = Offset(cellX, cellY),
                                size = Size(cellSizePx, cellSizePx),
                                cornerRadius = CornerRadius(4.dp.toPx())
                            )
                        }
                    }
                }
            }
        }
    }

    // 显示选中的月份弹窗
    selectedMonth?.let { month ->
        MonthDetailDialog(
            month = month,
            dataMap = normalizedDataMap,
            levelColors = levelColors,
            emptyColor = emptyColor,
            onDismiss = { selectedMonth = null } // 关闭弹窗
        )
    }
}

@Composable
fun MonthDetailDialog(
    month: YearMonth,
    dataMap: Map<LocalDate, Int>,
    levelColors: List<Color>,
    emptyColor: Color,
    onDismiss: () -> Unit
) {
    StandardDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth(0.85f)) {
            Text(
                text = "${month.year}年 ${month.monthValue}月",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 星期表头
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

            // 日期网格
            val daysInMonth = month.lengthOfMonth()
            val firstDayOfWeek = month.atDay(1).dayOfWeek.value
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
                                val date = month.atDay(day)
                                val level = dataMap[date] ?: -1
                                val bgColor =
                                    if (level in levelColors.indices) levelColors[level] else emptyColor

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
                                        color = if (level != -1) Color.White else MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Keep
data class HorizontalLine(
    @SerializedName("value") val value: Float,
    @SerializedName("label") val label: String,
    @SerializedName("color") val color: Color? = Color.Gray
)