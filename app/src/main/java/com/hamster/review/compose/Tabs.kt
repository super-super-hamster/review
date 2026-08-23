package com.hamster.review.compose

import androidx.annotation.Keep
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import com.google.gson.annotations.SerializedName
import com.hamster.review.R
import kotlinx.coroutines.launch
import kotlin.math.abs

@Keep
data class TabItem(
    @SerializedName("title") val title: String,
    @SerializedName("screen") val screen: @Composable () -> Unit
)

@Composable
fun Tabs(
    tabHorizontalPadding: Dp = 0.dp,
    tabVerticalPadding: Dp = 0.dp,
    tabs: List<TabItem>,
    selectedIndex: Int,
    setSelectedIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = selectedIndex,
        pageCount = { tabs.size }
    )
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        if (selectedIndex != pagerState.currentPage) {
            setSelectedIndex(pagerState.currentPage)
        }
    }

    LaunchedEffect(selectedIndex) {
        if (pagerState.currentPage != selectedIndex) {
            pagerState.animateScrollToPage(selectedIndex)
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.padding(horizontal = tabHorizontalPadding, vertical = tabVerticalPadding)) {
            SegmentedControl(
                items = tabs.map { it.title },
                pagerState = pagerState,
                selectedIndex = selectedIndex,
                onItemSelected = { index ->
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.Top
        ) { pageIndex ->
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                tabs[pageIndex].screen()
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
fun SegmentedControl(
    items: List<String>,
    pagerState: PagerState,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    val tabWidths = remember { mutableStateMapOf<Int, Dp>() }
    val tabOffsets = remember { mutableStateMapOf<Int, Dp>() }

    val indicatorOffset by remember {
        derivedStateOf {
            val currentPage = pagerState.currentPage
            val fraction = pagerState.currentPageOffsetFraction

            // 判断滑动方向
            val targetPage = if (fraction > 0) currentPage + 1 else currentPage - 1
            val safeTargetPage = targetPage.coerceIn(0, items.size - 1)

            val currentOffset = tabOffsets[currentPage] ?: 0.dp
            val targetOffset = tabOffsets[safeTargetPage] ?: 0.dp

            // 根据偏移比例进行线性插值
            lerp(currentOffset, targetOffset, abs(fraction))
        }
    }

    val indicatorWidth by remember {
        derivedStateOf {
            val currentPage = pagerState.currentPage
            val fraction = pagerState.currentPageOffsetFraction

            val targetPage = if (fraction > 0) currentPage + 1 else currentPage - 1
            val safeTargetPage = targetPage.coerceIn(0, items.size - 1)

            val currentWidth = tabWidths[currentPage] ?: 0.dp
            val targetWidth = tabWidths[safeTargetPage] ?: 0.dp

            lerp(currentWidth, targetWidth, abs(fraction))
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(color = colorResource(R.color.tabs_bg), shape = squircleShape)
            .padding(6.dp)
            .height(IntrinsicSize.Min)
    ) {
        if (indicatorWidth > 0.dp) {
            Box(
                modifier = Modifier
                    .offset(x = indicatorOffset)
                    .width(indicatorWidth)
                    .fillMaxHeight()
                    .shadow(
                        elevation = 2.dp,
                        shape = squircleShape,
                        clip = false,
                        spotColor = colorResource(id = R.color.item_group_card_shadow),  // 主阴影
                        ambientColor = colorResource(id = R.color.item_group_card_shadow)// 柔和阴影
                    )
                    .clip(squircleShape)
                    .background(colorResource(R.color.tabs_selected_bg))
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            items.forEachIndexed { index, title ->
                val isSelected = index == selectedIndex

                val textColor by animateColorAsState(
                    targetValue = if (isSelected) colorResource(R.color.tabs_selected_text) else colorResource(R.color.tabs_text),
                    animationSpec = tween(150),
                    label = "TabText"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .onGloballyPositioned { coordinates ->
                            tabWidths[index] = with(density) { coordinates.size.width.toDp() }
                            tabOffsets[index] = with(density) { coordinates.positionInParent().x.toDp() }
                        }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { onItemSelected(index) }
                        )
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = textColor,
                        fontSize = 15.sp,
                        fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}