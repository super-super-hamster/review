package com.hamster.review

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.fragment.app.FragmentActivity
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hamster.review.compose.scaleInPopEnter
import com.hamster.review.compose.scaleOutExit
import com.hamster.review.compose.slideInWithScaleEnter
import com.hamster.review.compose.slideOutWithScalePopExit
import com.hamster.review.screen.MainScreen
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import dev.chrisbanes.haze.HazeState
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

    @SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalHazeMaterialsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            val navController = rememberNavController()

            val hazeState = remember { HazeState() }
            val backdrop = rememberLayerBackdrop {
                drawContent()
            }

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
                                        MainScreen()
                                    }
                                }

//                                // 高斯模糊层
//                                if (isMenuExpanded || blurRadius > 0.dp) {
//                                    Box(
//                                        modifier = Modifier
//                                            .fillMaxSize()
//                                            .hazeEffect(
//                                                state = hazeState,
//                                                style = HazeStyle(
//                                                    blurRadius = blurRadius,
//                                                    tint = HazeTint(Color.Transparent),
//                                                    noiseFactor = 0f
//                                                )
//                                            )
//                                            .clickable(
//                                                interactionSource = remember { MutableInteractionSource() },
//                                                indication = null
//                                            ) {
//                                                isMenuExpanded = false
//                                            }
//                                    )
//                                }
//
//                                // 底部菜单栏
//                                if (mainViewModel.showBottomMenu) {
//                                    Box(modifier = Modifier
//                                        .align(Alignment.BottomCenter)
//                                        .systemBarsPadding()) {
//                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                                            // 展开菜单栏
//                                            AnimatedVisibility(
//                                                visible = isMenuExpanded,
//                                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
//                                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
//                                                modifier = Modifier.padding(vertical = 6.dp)
//                                            ) {
//                                                Surface(
//                                                    shape = squircleShape,
//                                                    color = colorResource(R.color.bg_dialog),
//                                                    tonalElevation = 8.dp,
//                                                    shadowElevation = 8.dp,
//                                                    modifier = Modifier
//                                                        .fillMaxWidth()
//                                                        .height(512.dp)
//                                                        .padding(horizontal = 12.dp)
//                                                ) {
//                                                    ExpandedBottomMenu(
//                                                        mainViewModel = mainViewModel,
//                                                        selectedIndex = selectedIndex,
//                                                        setSelectedIndex = { selectedIndex = it },
//                                                        inputText = inputText,
//                                                        setInputText = { inputText = it },
//                                                        onNavigate = { navController.expandMenuNavigate(it) },
//                                                        onDragDown = { isMenuExpanded = false },
//                                                    )
//                                                }
//                                            }
//
//                                            // 底部菜单栏
//                                            Surface(
//                                                color = colorResource(R.color.bg_dialog),
//                                                shape = squircleShape,
//                                                tonalElevation = 8.dp,
//                                                shadowElevation = 8.dp,
//                                                modifier = Modifier
//                                                    .padding(horizontal = 12.dp, vertical = 6.dp)
//                                                    .height(64.dp)
//                                                    .fillMaxWidth()
//                                            ) {
//                                                Box(
//                                                    modifier = Modifier
//                                                        .fillMaxSize()
//                                                        .padding(horizontal = 48.dp)
//                                                ) {
//                                                    // 助手按钮
//                                                    Box(
//                                                        modifier = Modifier
//                                                            .size(48.dp)
//                                                            .align(Alignment.CenterStart)
//                                                            .clickable(
//                                                                onClick = {
//                                                                    if (!isMenuExpanded) {
//                                                                        selectedIndex = 0
//                                                                        isMenuExpanded = true
//                                                                    } else {
//                                                                        if (selectedIndex == 0) {
//                                                                            isMenuExpanded = false
//                                                                        } else {
//                                                                            selectedIndex = 0
//                                                                        }
//                                                                    }
//                                                                },
//                                                                indication = null,
//                                                                interactionSource = remember { MutableInteractionSource() } // 必须配合 interactionSource 使用
//                                                            ),
//                                                        contentAlignment = Alignment.Center
//                                                    ) {
//                                                        Icon(painterResource(R.drawable.ic_assistant), null, tint = Color.Gray)
//                                                    }
//
//                                                    // 通用按钮
//                                                    Box(modifier = Modifier
//                                                        .height(48.dp)
//                                                        .width(72.dp)
//                                                        .align(Alignment.Center), contentAlignment = Alignment.Center) {
//                                                        ButtonPro(
//                                                            icon = universalButtonIconId,
//                                                            onTap = {
//                                                                val currentHierarchy = navController.currentDestination?.hierarchy ?: return@ButtonPro
//
//                                                                when {
//                                                                    currentHierarchy.any { it.hasRoute<SetKeywords>() } -> {
//                                                                        mainViewModel.isShowAddKeywordDialog = true
//                                                                    }
//                                                                    currentHierarchy.any { it.hasRoute<Schedule>() } -> {
//                                                                        navController.navigate(ImportCurriculum)
//                                                                    }
//                                                                    currentHierarchy.any { it.hasRoute<Time>() } -> {
//                                                                        mainViewModel.changeStateOfIsSetInvisibleApp()
//                                                                    }
//                                                                    currentHierarchy.any { it.hasRoute<DiaryPreview>() } -> {
//                                                                        mainViewModel.showAddDiaryDialog = true
//                                                                    }
//                                                                    currentHierarchy.any { it.hasRoute<Diary>() } -> {
//                                                                        mainViewModel.isAddDiaryImage = true
//                                                                    }
//                                                                    currentHierarchy.any { it.hasRoute<DecibelMeter>() } -> {
//                                                                        mainViewModel.showDecibelMeterOffsetDialog = true
//                                                                    }
//                                                                } },
//                                                            onLongPressStart = {
//                                                                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && isModelReady) {
//                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
//                                                                    showRecording = true
//                                                                    speechManager.startListening()
//                                                                } else {
//                                                                    requestAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
//                                                                } },
//                                                            onLongPressEnd = {
//                                                                showRecording = false
//
//                                                                if (mainViewModel.speechFinalResult != "") {
//                                                                    selectedIndex = 0
//                                                                    isMenuExpanded = true
//                                                                    inputText = mainViewModel.speechFinalResult
//                                                                    mainViewModel.setSpeechFinalResult("")
//                                                                }
//
//                                                                speechManager.stopListening()
//                                                            }
//                                                        )
//                                                    }
//
//                                                    // 展开按钮
//                                                    Box(modifier = Modifier
//                                                        .height(48.dp)
//                                                        .width(72.dp)
//                                                        .align(Alignment.CenterEnd), contentAlignment = Alignment.CenterEnd) {
//                                                        AnimationButton(
//                                                            animation = R.raw.ic_arrow_anim,
//                                                            changed = isMenuExpanded,
//                                                            onClick = {
//                                                                selectedIndex = 1
//                                                                isMenuExpanded = !isMenuExpanded
//                                                            }
//                                                        )
//                                                    }
//                                                }
//                                            }
//                                        }
//                                    }
//                                }
//
//                                // 显示顶部标题栏
//                                if (showTopBar) {
//                                    CenterAlignedTopAppBar(
//                                        title = {
//                                            AnimatedContent (
//                                                targetState = showWeatherDetail,
//                                                transitionSpec = {
//                                                    if (showWeatherDetail) {
//                                                        (slideInHorizontally { width -> width } + fadeIn()) togetherWith
//                                                                (slideOutHorizontally { width -> -width } + fadeOut())
//                                                    } else {
//                                                        (slideInHorizontally { width -> -width } + fadeIn()) togetherWith
//                                                                (slideOutHorizontally { width -> width } + fadeOut())
//                                                    }
//                                                }
//                                            ) { targetIsWeather ->
//                                                if (targetIsWeather) {
//                                                    Text("${WeatherData.getLocation() ?: ""} ${WeatherData.getWeatherState() ?: ""}")
//                                                } else {
//                                                    Text(currentTitle)
//                                                }
//                                            }
//                                        },
//                                        actions = {
//                                            Weather(
//                                                onClick = {
//                                                    showWeatherDetail = !showWeatherDetail
//                                                }
//                                            )
//                                        },
//                                        colors = TopAppBarDefaults.topAppBarColors(
//                                            containerColor = Color.Transparent,
//                                            scrolledContainerColor = Color.Transparent
//                                        ),
//                                        modifier = Modifier
//                                            .align(Alignment.TopCenter)
//                                            .height(80.dp)
//                                            .fillMaxWidth()
//                                            .shadow(elevation = 0.dp)
//                                            .drawBackdrop(
//                                                backdrop = backdrop,
//                                                shape = { RoundedCornerShape(0.dp) },
//                                                effects = {
//                                                    vibrancy()
//                                                    blur(4f.dp.toPx())
//                                                    lens(12f.dp.toPx(), 8f.dp.toPx())
//                                                },
//                                            )
//                                    )
//                                }
//
//                                // 显示加载
//                                if (showLoading) {
//                                    Box(
//                                        modifier = Modifier
//                                            .fillMaxSize()
//                                            .clickable(
//                                                interactionSource = remember { MutableInteractionSource() },
//                                                indication = null
//                                            ) {
////                                                showLoading = false
//                                            },
//                                        contentAlignment = Alignment.Center
//                                    ) {
//                                        LottieAnimation(
//                                            composition = loadingComposition,
//                                            progress = { loadingProgress },
//                                            modifier = Modifier.size(196.dp)
//                                        )
//                                    }
//                                }
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