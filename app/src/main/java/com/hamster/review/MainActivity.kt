package com.hamster.review

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
import androidx.navigation.toRoute
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.hamster.review.compose.scaleInPopEnter
import com.hamster.review.compose.scaleOutExit
import com.hamster.review.compose.slideInWithScaleEnter
import com.hamster.review.compose.slideOutWithScalePopExit
import com.hamster.review.screen.MainScreen
import com.hamster.review.screen.ReviewScreenNew
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob


class MainActivity : FragmentActivity() {
    // 跟随应用生命周期的协程作用域
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val requestAudioPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean -> }

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
                    restoreState = true // 恢复之前的状态
                }
            }

            val hazeState = remember { HazeState() }
            val backdrop = rememberLayerBackdrop {
                drawContent()
            }

            var showBlur by remember { mutableStateOf(false) }
            val blurRadius by animateDpAsState(
                targetValue = if (showBlur) 8.dp else 0.dp,
                animationSpec = tween(100),
                label = "blur"
            )

            var showTopBar by remember { mutableStateOf(true) }

            var showLoading by remember { mutableStateOf(false) }
            val loadingComposition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.loading_anim))
            val loadingProgress by animateLottieCompositionAsState(
                composition = loadingComposition,
                iterations = LottieConstants.IterateForever
            )

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
                                              subjects = subjects,

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
                                        ReviewScreenNew(
                                              subjectId = it.toRoute<Review>().subjectId,
                                            setTopbarTitle = {
                                                mainViewModel.topbarTitle = it
                                            },
                                            onNavigate = {
                                                navController.simpleNavigate(it)
                                            }
                                        )
                                    }
                                }

                                // 高斯模糊层
                                if (blurRadius > 0.dp) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .hazeEffect(
                                                state = hazeState,
                                                style = HazeStyle(
                                                    blurRadius = blurRadius,
                                                    tint = HazeTint(Color.Transparent),
                                                    noiseFactor = 0f
                                                )
                                            )
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                showBlur = false
                                            }
                                    )
                                }

                                // 显示顶部标题栏
                                if (showTopBar) {
                                    CenterAlignedTopAppBar(
                                        title = { Text(text = mainViewModel.topbarTitle) },
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

                                // 显示加载
                                if (showLoading) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
//                                                showLoading = false
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LottieAnimation(
                                            composition = loadingComposition,
                                            progress = { loadingProgress },
                                            modifier = Modifier.size(196.dp)
                                        )
                                    }
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