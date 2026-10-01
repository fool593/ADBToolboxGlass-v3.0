package com.example.adbtoolbox.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ui.ADBModuleScreen
import com.example.adbtoolbox.common.ui.ADBPanelScreen
import com.example.adbtoolbox.common.ui.PluginDetailScreen
import com.example.adbtoolbox.common.ui.PluginsScreen
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.GlassEffectConfig
import com.example.adbtoolbox.common.ui.AppDetailScreen
import com.example.adbtoolbox.common.ui.AppsScreen
import com.example.adbtoolbox.common.ui.DeviceInfoScreen
import com.example.adbtoolbox.common.ui.GlassPlaygroundScreen
import com.example.adbtoolbox.common.ui.HomeScreen
import com.example.adbtoolbox.common.ui.PermissionsScreen
import com.example.adbtoolbox.common.ui.RootManagerScreen
import com.example.adbtoolbox.common.ui.SettingsScreen
import com.example.adbtoolbox.common.ui.ShellExecutorScreen
import com.example.adbtoolbox.common.ui.TerminalScreen
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.catalog.BackdropDemoScaffold
import com.kyant.backdrop.catalog.components.LiquidBottomTab
import com.kyant.backdrop.catalog.components.LiquidBottomTabs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MainContent() {
    val isLightTheme = !AppSettings.isDarkMode
    // 字体颜色：优先使用用户设置的玻璃字体色（必须非透明），否则跟随主题
    val fontColorSetting = GlassEffectConfig.fontColor.value
    val contentColor = if (fontColorSetting != Color.Unspecified && fontColorSetting.alpha > 0f) fontColorSetting
        else if (isLightTheme) Color.Black else Color.White

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var currentDestination by rememberSaveable { mutableStateOf(ADBDestination.Home) }
    var selectedAppPackage by remember { mutableStateOf<String?>(null) }
    var selectedPlugin by remember { mutableStateOf<com.example.adbtoolbox.common.PluginData?>(null) }
    var pickWallpaperTrigger by remember { mutableIntStateOf(0) }
    var clearWallpaperTrigger by remember { mutableIntStateOf(0) }
    val dynamicWallpaperEnabled = AppCache.dynamicWallpaperEnabled.value
    val dynamicWallpaperVideoPath = AppCache.dynamicWallpaperVideoPath.value

    // 当启用动态壁纸时，清除静态壁纸一次
    LaunchedEffect(dynamicWallpaperEnabled, dynamicWallpaperVideoPath) {
        if (dynamicWallpaperEnabled && dynamicWallpaperVideoPath != null) {
            clearWallpaperTrigger++
        }
    }

    // ---------------- Shizuku 状态：全局唯一检测点 ----------------
    // 以前每个页面各自查一次，进页面时没连上就永远显示"未连接"，只能杀进程重进。
    // 现在统一由这里检测，任何地方调用 AppCache.requestShizukuRefresh() 都会重新查并全局同步。
    LaunchedEffect(AppCache.shizukuRefreshTick.value) {
        val state = try {
            withContext(Dispatchers.Default) { ADBTools.getShizukuState() }
        } catch (e: Exception) {
            "not_running"
        }
        AppCache.shizukuState.value = state
        AppCache.shizukuAvailable.value = state == "granted"
        AppCache.shizukuChecked.value = true
    }
    // 每次回到首页都重查一次：用户很可能刚在系统里把 Shizuku 启动/授权完再回来
    LaunchedEffect(currentDestination) {
        if (currentDestination == ADBDestination.Home) AppCache.requestShizukuRefresh()
    }

    Box(Modifier.fillMaxSize()) {
        BackdropDemoScaffold(
            pickWallpaperTrigger = pickWallpaperTrigger,
            clearWallpaperTrigger = clearWallpaperTrigger,
            dynamicWallpaper = if (dynamicWallpaperEnabled && dynamicWallpaperVideoPath != null) {
                {
                    DynamicWallpaperBackground(
                        videoPath = dynamicWallpaperVideoPath,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else null
        ) { backdrop ->
            Box(Modifier.fillMaxSize()) {
                Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                Box(Modifier.weight(1f)) {
                    when (currentDestination) {
                        ADBDestination.Home -> HomeScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onNavigate = { currentDestination = it }
                        )
                        ADBDestination.Apps -> AppsScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onAppClick = { pkg ->
                                selectedAppPackage = pkg
                                currentDestination = ADBDestination.AppDetail
                            }
                        )
                        ADBDestination.Terminal -> TerminalScreen(
                            backdrop = backdrop,
                            contentColor = contentColor
                        )
                        ADBDestination.Settings -> SettingsScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onPickWallpaper = { pickWallpaperTrigger++ },
                            onClearWallpaper = { clearWallpaperTrigger++ },
                            onGlassPlayground = { currentDestination = ADBDestination.GlassPlayground }
                        )
                        ADBDestination.DeviceInfo -> DeviceInfoScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onBack = { currentDestination = ADBDestination.Home }
                        )
                        ADBDestination.ADBPanel -> ADBPanelScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onBack = { currentDestination = ADBDestination.Home }
                        )
                        ADBDestination.Permissions -> PermissionsScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onBack = { currentDestination = ADBDestination.Home }
                        )
                        ADBDestination.RootManager -> RootManagerScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onBack = { currentDestination = ADBDestination.Home }
                        )
                        ADBDestination.ShellExecutor -> ShellExecutorScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onBack = { currentDestination = ADBDestination.Home }
                        )
                        ADBDestination.AppDetail -> AppDetailScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            packageName = selectedAppPackage ?: "",
                            onBack = { currentDestination = ADBDestination.Apps }
                        )
                        ADBDestination.ADBModule -> ADBModuleScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onPickFile = { AppCache.pickModuleFileTrigger.value++ },
                            onBack = { currentDestination = ADBDestination.Home },
                            selectedFilePath = AppCache.selectedModulePath.value
                        )
                        ADBDestination.Plugins -> PluginsScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onPickPlugin = { AppCache.pickPluginFileTrigger.value++ },
                            onPluginClick = { plugin ->
                                selectedPlugin = plugin
                                currentDestination = ADBDestination.PluginDetail
                            }
                        )
                        ADBDestination.PluginDetail -> selectedPlugin?.let { plugin ->
                            PluginDetailScreen(
                                plugin = plugin,
                                backdrop = backdrop,
                                contentColor = contentColor,
                                onBack = { currentDestination = ADBDestination.Plugins }
                            )
                        }
                        ADBDestination.GlassPlayground -> GlassPlaygroundScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onBack = { currentDestination = ADBDestination.Settings }
                        )
                        ADBDestination.RootTool -> com.example.adbtoolbox.common.ui.RootToolScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onBack = { currentDestination = ADBDestination.Home },
                            onNavigateToTerminal = { command ->
                                AppCache.terminalInitialCommand.value = command
                                currentDestination = ADBDestination.Terminal
                            }
                        )
                        ADBDestination.TempRoot -> com.example.adbtoolbox.common.ui.TempRootScreen(
                            backdrop = backdrop,
                        )
                        // v2.8 品牌自适应一键性能加速
                        ADBDestination.PerformanceBoost -> com.example.adbtoolbox.common.ui.PerformanceBoostScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onBack = { currentDestination = ADBDestination.Home },
                            onOpenInspector = { currentDestination = ADBDestination.PhoneInspector }
                        )
                        // v2.8 手机体检（指令可用性检查员）
                        ADBDestination.PhoneInspector -> com.example.adbtoolbox.common.ui.PhoneInspectorScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onBack = { currentDestination = ADBDestination.Home },
                            onOpenBoost = { currentDestination = ADBDestination.PerformanceBoost }
                        )
                        // v2.8 华为深度优化（HarmonyOS / EMUI 专属）
                        ADBDestination.HuaweiBoost -> com.example.adbtoolbox.common.ui.HuaweiBoostScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onBack = { currentDestination = ADBDestination.Home },
                            onRunInTerminal = { command ->
                                AppCache.terminalInitialCommand.value = command
                                currentDestination = ADBDestination.Terminal
                            }
                        )
                        // v2.8 已安装 Root 模块管理（此前 RootModuleManager.getInstalledModules() 没有任何 UI 调用者）
                        ADBDestination.RootModules -> com.example.adbtoolbox.common.ui.RootModuleScreen(
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onBack = { currentDestination = ADBDestination.Home },
                            onInstallModule = { AppCache.pickRootModuleFileTrigger.value++ },
                            selectedFilePath = AppCache.selectedRootModulePath.value
                        )
                    }
                }

                LiquidBottomTabs(
                    selectedTabIndex = { selectedTabIndex },
                    onTabSelected = { index ->
                        selectedTabIndex = index
                        currentDestination = when (index) {
                            0 -> ADBDestination.Home
                            1 -> ADBDestination.Apps
                            2 -> ADBDestination.Plugins
                            3 -> ADBDestination.ADBModule
                            4 -> ADBDestination.Terminal
                            5 -> ADBDestination.Settings
                            else -> ADBDestination.Home
                        }
                    },
                    backdrop = backdrop,
                    tabsCount = 6,
                    indicatorHeight = GlassEffectConfig.navIndicatorHeight.value,
                    modifier = Modifier.padding(horizontal = 24f.dp, vertical = 12f.dp)
                ) {
                    LiquidBottomTab({
                        selectedTabIndex = 0
                        currentDestination = ADBDestination.Home
                    }) {
                        BasicText(AppStrings.get("home"), style = TextStyle(contentColor, 12f.sp))
                    }
                    LiquidBottomTab({
                        selectedTabIndex = 1
                        currentDestination = ADBDestination.Apps
                    }) {
                        BasicText(AppStrings.get("apps"), style = TextStyle(contentColor, 12f.sp))
                    }
                    LiquidBottomTab({
                        selectedTabIndex = 2
                        currentDestination = ADBDestination.Plugins
                    }) {
                        BasicText(AppStrings.get("adb_plugins"), style = TextStyle(contentColor, 12f.sp))
                    }
                    LiquidBottomTab({
                        selectedTabIndex = 3
                        currentDestination = ADBDestination.ADBModule
                    }) {
                        BasicText(AppStrings.get("root_module"), style = TextStyle(contentColor, 12f.sp))
                    }
                    LiquidBottomTab({
                        selectedTabIndex = 4
                        currentDestination = ADBDestination.Terminal
                    }) {
                        BasicText(AppStrings.get("terminal"), style = TextStyle(contentColor, 12f.sp))
                    }
                    LiquidBottomTab({
                        selectedTabIndex = 5
                        currentDestination = ADBDestination.Settings
                    }) {
                        BasicText(AppStrings.get("settings"), style = TextStyle(contentColor, 12f.sp))
                    }
                }
            }
        }
        }
    }
}
