package com.hamster.review

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hamster.review.compose.RingProgress
import com.hamster.review.compose.scaleInPopEnter
import com.hamster.review.compose.scaleOutExit
import com.hamster.review.compose.slideInWithScaleEnter
import com.hamster.review.compose.slideOutWithScalePopExit
import com.hamster.review.screen.AddQuestionScreen
import com.hamster.review.screen.MainScreen
import com.hamster.review.screen.ManageQuestionsScreen
import com.hamster.review.screen.ReviewScreen
import com.hamster.review.viewModel.MainViewModel
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob


class MainActivity : FragmentActivity() {
    // 跟随应用生命周期的协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val mainViewModel: MainViewModel by viewModels()

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val navController = rememberNavController()
            val subjects by mainViewModel.subjects.collectAsState()

            fun NavHostController.simpleNavigate(route: Any) {
                this.navigate(route) {
                    popUpTo(this@simpleNavigate.graph.findStartDestination().id) {
                        saveState = true // 保留滚动状态
                    }
                    launchSingleTop = true // 同一个页面不会创建新的实例

                }
            }

            val hazeState = remember { HazeState() }
            val backdrop = rememberLayerBackdrop {
                drawContent()
            }

            var showTopBar by remember { mutableStateOf(true) }

            MaterialTheme {
                CompositionLocalProvider( LocalOverscrollFactory provides null) { // 禁用边缘回弹和光晕效果
                    Surface(modifier = Modifier.fillMaxSize(), color = colorResource(R.color.background)) { // 覆盖原有的主题色背景
                        Scaffold { _ ->
                            Box(modifier = Modifier.fillMaxSize()) {
                                NavHost(
                                    navController = navController,
                                    startDestination = Main,
                                    modifier = Modifier
                                        .layerBackdrop(backdrop) // 应用玻璃效果
                                        .hazeSource(state = hazeState)
                                        .fillMaxSize()
                                ) {
                                    composable<Main>(
                                        enterTransition = { slideInWithScaleEnter() },
                                        exitTransition = { scaleOutExit() },
                                        popEnterTransition = { scaleInPopEnter() },
                                        popExitTransition = { slideOutWithScalePopExit() }
                                    ) {
                                        MainScreen(
                                            mainViewModel = mainViewModel,
                                              subjects = subjects,
                                              onSetDailyLimit = mainViewModel::setSubjectDailyLimit,
                                              onAddSubject = mainViewModel::addSubject,
                                              onDeleteSubject = mainViewModel::deleteSubject,
                                              onUpdateOfficialBank = mainViewModel::updateOfficialBank,
                                              homeExpanded = mainViewModel.homeExpanded,
                                              onHomeExpandedChange = mainViewModel::setHomeExpanded,
                                              homeTopSubjectId = mainViewModel.homeTopSubjectId,
                                              onHomeTopSubjectIdChange = mainViewModel::setHomeTopSubjectId,

                                            setTopbarTitle = {
                                                mainViewModel.topbarTitle = it
                                            },
                                            onNavigate = {
                                                navController.simpleNavigate(it)
                                            }
                                        )
                                    }

                                    composable<Review>(
                                        enterTransition = { slideInWithScaleEnter() },
                                        exitTransition = { scaleOutExit() },
                                        popEnterTransition = { scaleInPopEnter() },
                                        popExitTransition = { slideOutWithScalePopExit() }
                                    ) {
                                        ReviewScreen(
                                            setTopbarTitle = { title ->
                                                mainViewModel.topbarTitle = title
                                            },
                                            setReviewProgress = mainViewModel::setReviewProgress,
                                            onNavigate = { route ->
                                                navController.simpleNavigate(route)
                                            }
                                        )
                                    }

                                    composable<AddQuestion>(
                                        enterTransition = { slideInWithScaleEnter() },
                                        exitTransition = { scaleOutExit() },
                                        popEnterTransition = { scaleInPopEnter() },
                                        popExitTransition = { slideOutWithScalePopExit() }
                                    ) {
                                        AddQuestionScreen(
                                            setTopbarTitle = { title ->
                                                mainViewModel.topbarTitle = title
                                            },
                                            onBack = { navController.popBackStack() }
                                        )
                                    }

                                    composable<ManageQuestions>(
                                        enterTransition = { slideInWithScaleEnter() },
                                        exitTransition = { scaleOutExit() },
                                        popEnterTransition = { scaleInPopEnter() },
                                        popExitTransition = { slideOutWithScalePopExit() }
                                    ) {
                                        ManageQuestionsScreen(
                                            setTopbarTitle = { title ->
                                                mainViewModel.topbarTitle = title
                                            },
                                            // 管理页 -> 编辑页需要压栈，返回时才能回到管理页
                                            onNavigate = { route ->
                                                navController.navigate(route)
                                            },
                                            onBack = { navController.popBackStack() }
                                        )
                                    }
                                }

                                // 显示顶部标题栏
                                if (showTopBar) {
                                    CenterAlignedTopAppBar(
                                        title = { Text(text = mainViewModel.topbarTitle) },
                                        actions = {
                                            mainViewModel.reviewProgress?.let { progress ->
                                                RingProgress(
                                                    progress = progress,
                                                    modifier = Modifier.padding(end = 6.dp),
                                                    size = 36.dp,
                                                    strokeWidth = 4.dp,
                                                    text = "",
                                                    onClick = {}
                                                )
                                            }
                                        },
                                        colors = TopAppBarDefaults.topAppBarColors(
                                            containerColor = Color.Transparent,
                                            scrolledContainerColor = Color.Transparent
                                        ),
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .height(80.dp)
                                            .fillMaxWidth()
                                            .shadow(elevation = 0.dp)
                                            .drawBackdrop(
                                                backdrop = backdrop,
                                                shape = { RoundedCornerShape(0.dp) },
                                                effects = {
                                                    vibrancy()
                                                    blur(4f.dp.toPx())
                                                    lens(12f.dp.toPx(), 8f.dp.toPx())
                                                },
                                            )
                                    )
                                }

                            }
                        }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}