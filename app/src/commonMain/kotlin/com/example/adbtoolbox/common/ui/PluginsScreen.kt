package com.example.adbtoolbox.common.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.PluginData
import com.example.adbtoolbox.common.PluginManager
import com.example.adbtoolbox.common.theme.AppMotion
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 语义色：危险/成功/进行中不跟主题走（与 PerfWidgets 的约定一致），主题色只用于主操作与强调。
// 列表页与详情页（androidMain）共用这一份定义，避免同一个包里出现两套同样的色值。
internal val DangerRed = Color(0xFFFF3B30)
internal val OkGreen = Color(0xFF34C759)
internal val MutedGray = Color(0xFF8E8E93)
internal val BusyOrange = Color(0xFFFF9500)

@Composable
fun PluginsScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onPickPlugin: () -> Unit,
    onPluginClick: (PluginData) -> Unit
) {
    var plugins by remember { mutableStateOf<List<PluginData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshTrigger by remember { mutableStateOf(0) }
    var runningPluginId by remember { mutableStateOf<String?>(null) }
    var runResult by remember { mutableStateOf<Pair<String, String>?>(null) } // pluginId to result
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    // AppCache 里的安装结果不会自动清空；这里记录"本次会话已展示过"的那条，
    // 否则历史上成功的安装提示会永久挂在页面顶部（取消选择文件后也一直显示）。
    var shownInstallResult by remember { mutableStateOf<String?>(null) }
    // 正在后台解析 WebUI 入口文件的插件 id（getWebUIPath 是文件 IO）
    var openingWebUIId by remember { mutableStateOf<String?>(null) }
    // 非空表示 WebUI 宿主已打开
    var webUIUrl by remember { mutableStateOf<String?>(null) }
    // 待确认卸载的插件：删除是破坏性操作，先确认再执行
    var pendingUninstall by remember { mutableStateOf<PluginData?>(null) }

    fun loadPlugins() {
        isLoading = true
        scope.launch {
            try {
                plugins = withContext(Dispatchers.Default) { PluginManager.getInstalledPlugins() }
                errorMessage = null
            } catch (e: Exception) {
                // 原来把异常整个吞掉，界面只会显示"暂无插件"，用户无从判断是空目录还是出错
                plugins = emptyList()
                errorMessage = "${AppStrings.get("operation_failed")}: ${e.message ?: e.javaClass.simpleName}"
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit, refreshTrigger) {
        loadPlugins()
    }

    // 监听插件安装结果
    val installResult = AppCache.pluginInstallResult.value
    val isInstalling = AppCache.pluginInstalling.value
    LaunchedEffect(installResult) {
        if (installResult != null) {
            loadPlugins()
        }
    }

    fun toggleEnable(plugin: PluginData) {
        errorMessage = null
        scope.launch {
            val ok = try {
                withContext(Dispatchers.Default) {
                    if (plugin.isEnabled) PluginManager.disablePlugin(plugin.id)
                    else PluginManager.enablePlugin(plugin.id)
                }
            } catch (e: Exception) {
                false
            }
            if (ok) {
                refreshTrigger++
            } else {
                // 以前 catch(_: Exception) {} + 无视返回值 => 开关点了完全没反应
                errorMessage = "${AppStrings.get("operation_failed")}: ${plugin.name} (${AppStrings.get("enable")}/${AppStrings.get("disable")})"
            }
        }
    }

    fun runPlugin(plugin: PluginData) {
        if (runningPluginId != null) return // 防止重复点击
        errorMessage = null
        scope.launch {
            runningPluginId = plugin.id
            runResult = null
            try {
                val result = withContext(Dispatchers.Default) { PluginManager.runAction(plugin.id) }
                // 未安装 action.sh 等"逻辑失败"是从返回值里回来的，不是异常，以前只在插件卡片里显示，
                // 这里补一条统一提示，界面其他位置也能看到。
                if (result.contains("Error", ignoreCase = true) || result.contains("failed", ignoreCase = true)) {
                    errorMessage = "${plugin.name}: ${result.lineSequence().first().trim()}"
                }
                runResult = Pair(plugin.id, result)
            } catch (e: Exception) {
                errorMessage = "${AppStrings.get("operation_failed")}: ${e.message ?: e.javaClass.simpleName}"
                runResult = Pair(plugin.id, "Error: ${e.message}")
            }
            runningPluginId = null
        }
    }

    fun deletePlugin(plugin: PluginData) {
        errorMessage = null
        scope.launch {
            val ok = try {
                withContext(Dispatchers.Default) { PluginManager.uninstallPlugin(plugin.id) }
            } catch (e: Exception) {
                false
            }
            if (ok) {
                refreshTrigger++
            } else {
                // 以前完全静默：删除失败时列表不会变，用户以为按钮坏了
                errorMessage = "${AppStrings.get("operation_failed")}: ${AppStrings.get("uninstall")} - ${plugin.name}"
            }
        }
    }

    // 打开模块自带界面：路径为空时必须给出明确原因，不能点了没反应
    fun openWebUI(plugin: PluginData) {
        if (openingWebUIId != null) return
        errorMessage = null
        openingWebUIId = plugin.id
        scope.launch {
            val path = try {
                withContext(Dispatchers.Default) { PluginManager.getWebUIPath(plugin.id) }
            } catch (e: Exception) {
                null
            }
            if (path.isNullOrBlank()) {
                errorMessage = "${plugin.name}: ${AppStrings.get("plugin_webui_missing")}"
            } else {
                webUIUrl = path
            }
            openingWebUIId = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16f.dp)
        ) {
            Spacer(Modifier.height(24f.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    AppStrings.get("adb_plugins"),
                    style = TextStyle(contentColor, 24f.sp, FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                LiquidButton(
                    onClick = {
                        AppCache.pickPluginFileTrigger.value++
                        // 重新选择文件时清掉上一次的错误提示，让状态可预期
                        errorMessage = null
                    },
                    backdrop = backdrop,
                    modifier = Modifier.height(36f.dp),
                    tint = AppTheme.accentAlt
                ) {
                    BasicText(
                        AppStrings.get("install_plugin"),
                        Modifier.padding(horizontal = 12f.dp),
                        style = TextStyle(AppTheme.onAccent, 12f.sp)
                    )
                }
            }
            Spacer(Modifier.height(8f.dp))
            BasicText(
                AppStrings.get("plugins_hint"),
                style = TextStyle(contentColor.copy(alpha = 0.5f), 12f.sp)
            )
            Spacer(Modifier.height(12f.dp))

            if (isInstalling) {
                GlassCard(backdrop = backdrop, pageType = "plugins") {
                    Box(Modifier.padding(16f.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        BasicText(AppStrings.get("installing"), style = TextStyle(contentColor, 14f.sp))
                    }
                }
                Spacer(Modifier.height(8f.dp))
            }

            installResult?.let { result ->
                if (result != shownInstallResult) {
                    GlassCard(backdrop = backdrop, pageType = "plugins") {
                        Column(Modifier.padding(16f.dp).fillMaxWidth()) {
                            BasicText(
                                result,
                                style = TextStyle(
                                    if (result.contains("success", ignoreCase = true) || result.contains("成功")) OkGreen else DangerRed,
                                    12f.sp
                                )
                            )
                            Spacer(Modifier.height(8f.dp))
                            LiquidButton(
                                onClick = { shownInstallResult = result },
                                backdrop = backdrop,
                                modifier = Modifier.height(32f.dp),
                                tint = MutedGray
                            ) {
                                BasicText(
                                    AppStrings.get("clear"),
                                    Modifier.padding(horizontal = 12f.dp),
                                    style = TextStyle(Color.White, 12f.sp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8f.dp))
                }
            }

            errorMessage?.let { message ->
                GlassCard(backdrop = backdrop, pageType = "plugins") {
                    Box(Modifier.padding(16f.dp).fillMaxWidth()) {
                        BasicText(
                            message,
                            style = TextStyle(DangerRed, 12f.sp)
                        )
                    }
                }
                Spacer(Modifier.height(8f.dp))
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(AppStrings.get("loading"), style = TextStyle(contentColor.copy(alpha = 0.5f), 16f.sp))
                }
            } else if (plugins.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        BasicText(AppStrings.get("no_plugins"), style = TextStyle(contentColor.copy(alpha = 0.5f), 16f.sp))
                        Spacer(Modifier.height(8f.dp))
                        BasicText(AppStrings.get("no_plugins_hint"), style = TextStyle(contentColor.copy(alpha = 0.3f), 12f.sp))
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10f.dp)) {
                    items(plugins, key = { it.id }) { plugin ->
                        PluginListItem(
                            plugin = plugin,
                            backdrop = backdrop,
                            contentColor = contentColor,
                            onClick = { onPluginClick(plugin) },
                            onToggleEnable = { toggleEnable(plugin) },
                            onRun = { runPlugin(plugin) },
                            onDelete = { pendingUninstall = plugin },
                            onOpenWebUI = { openWebUI(plugin) },
                            isRunning = runningPluginId == plugin.id,
                            isOpeningWebUI = openingWebUIId == plugin.id,
                            runResult = runResult?.takeIf { it.first == plugin.id }?.second
                        )
                    }
                    item { Spacer(Modifier.height(100f.dp)) }
                }
            }
        }

        // 右下角悬浮 + 按钮：用真正的液态玻璃可点控件。
        // 原来是一层不透明的 accent 色块（alpha 0.9）+ 静态 Highlight.Plain，看起来就是"一张图片"、
        // 按下去没有任何液态玻璃反馈；liquidGlassItem 自带模糊/折射/按下光斑/缩放/边缘高光。
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20f.dp)
                .size(56f.dp)
                .liquidGlassItem(
                    backdrop = backdrop,
                    corner = 28f.dp,
                    tint = AppTheme.accent,
                    onClick = {
                        AppCache.pickPluginFileTrigger.value++
                        errorMessage = null
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            BasicText("+", style = TextStyle(AppTheme.onAccent, 28f.sp, FontWeight.Bold))
        }

        // 模块自带界面：宿主盖住列表（含自己的关闭按钮），关闭后回到列表
        val activeWebUIUrl = webUIUrl
        if (activeWebUIUrl != null) {
            ModuleWebUIHost(
                url = activeWebUIUrl,
                onClose = { webUIUrl = null }
            )
        }
    }

    // 卸载前二次确认
    pendingUninstall?.let { target ->
        PerfConfirmDialog(
            title = AppStrings.get("plugin_confirm_uninstall"),
            message = "${AppStrings.get("plugin_uninstall_warning")}\n\n${target.name}\n${target.pluginDir}",
            confirmLabel = AppStrings.get("uninstall"),
            contentColor = contentColor,
            onConfirm = {
                pendingUninstall = null
                deletePlugin(target)
            },
            onDismiss = { pendingUninstall = null }
        )
    }
}

@Composable
fun PluginListItem(
    plugin: PluginData,
    backdrop: Backdrop,
    contentColor: Color,
    onClick: () -> Unit,
    onToggleEnable: () -> Unit = {},
    onRun: () -> Unit = {},
    onDelete: () -> Unit = {},
    onOpenWebUI: () -> Unit = {},
    isRunning: Boolean = false,
    isOpeningWebUI: Boolean = false,
    runResult: String? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlassItem(backdrop = backdrop, corner = 20.dp, onClick = onClick)
            .padding(16f.dp)
    ) {
        // 顶部行：徽标 + 名称 + 开关
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                // 徽标第一行：目录大小 + 启用状态（这两项一定存在，永远有内容）
                Row(horizontalArrangement = Arrangement.spacedBy(6f.dp)) {
                    if (plugin.size.isNotBlank()) {
                        PerfBadge(plugin.size, AppTheme.accent)
                    }
                    PerfBadge(
                        AppStrings.get(if (plugin.isEnabled) "enabled" else "disabled"),
                        if (plugin.isEnabled) OkGreen else DangerRed
                    )
                }
                // 徽标第二行：模块能力，只有真的存在才显示，避免出现空徽标
                if (plugin.hasWebUI || plugin.hasAction) {
                    Spacer(Modifier.height(6f.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6f.dp)) {
                        if (plugin.hasWebUI) {
                            PerfBadge(AppStrings.get("plugin_has_webui"), AppTheme.accent)
                        }
                        if (plugin.hasAction) {
                            PerfBadge(AppStrings.get("plugin_has_action"), AppTheme.accentAlt)
                        }
                    }
                }
                Spacer(Modifier.height(8f.dp))
                // 插件名称
                BasicText(
                    plugin.name,
                    style = TextStyle(contentColor, 18f.sp, FontWeight.Medium),
                    maxLines = 1
                )
                Spacer(Modifier.height(4f.dp))
                // 版本和作者
                BasicText(
                    "${AppStrings.get("version")}: ${plugin.version}",
                    style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
                )
                BasicText(
                    "${AppStrings.get("author")}: ${plugin.author}",
                    style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
                )
            }
            Spacer(Modifier.width(12f.dp))
            // 右上角：启用/禁用开关
            // 改成真正的液态玻璃可点控件：原来是一层静态色块（onDrawSurface 画死颜色）+
            // contentAlignment 瞬间跳位，点下去既没有液态玻璃反馈、滑块也不动，看着像一张图。
            // 现在：liquidGlassItem 提供玻璃与按压反馈，滑块位移与轨道颜色都平滑过渡。
            val knobProgress by animateFloatAsState(
                targetValue = if (plugin.isEnabled) 1f else 0f,
                animationSpec = tween(durationMillis = AppMotion.fast, easing = AppMotion.enter),
                label = "pluginSwitchKnob"
            )
            val trackTint by animateColorAsState(
                targetValue = if (plugin.isEnabled) AppTheme.accent else Color(0xFF8E8E93),
                animationSpec = tween(durationMillis = AppMotion.fast, easing = AppMotion.enter),
                label = "pluginSwitchTrack"
            )
            Box(
                modifier = Modifier
                    .size(52f.dp, 32f.dp)
                    .liquidGlassItem(
                        backdrop = backdrop,
                        corner = 16f.dp,
                        blurPx = with(LocalDensity.current) { 8f.dp.toPx() },
                        tint = trackTint,
                        onClick = onToggleEnable
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = (3f + 23f * knobProgress).dp)
                        .size(26f.dp)
                        .clip(RoundedRectangle(13f.dp))
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(13f.dp) },
                            effects = { blur(4f.dp.toPx()) },
                            onDrawSurface = { drawRect(Color.White) }
                        )
                )
            }
        }

        // 描述
        if (plugin.description.isNotBlank()) {
            Spacer(Modifier.height(8f.dp))
            BasicText(
                plugin.description,
                style = TextStyle(contentColor.copy(alpha = 0.7f), 13f.sp),
                maxLines = 2
            )
        }

        // 底部：运行状态和结果显示
        AnimatedVisibility(
            visible = isRunning || runResult != null,
            enter = fadeIn(tween(durationMillis = AppMotion.normal, easing = AppMotion.enter)),
            exit = fadeOut(tween(durationMillis = AppMotion.fast, easing = AppMotion.exit))
        ) {
            Column {
                Spacer(Modifier.height(8f.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedRectangle(10f.dp))
                        .drawBackdrop(
                            backdrop = backdrop,
                            shape = { RoundedRectangle(10f.dp) },
                            effects = { blur(8f.dp.toPx()) },
                            onDrawSurface = {
                                drawRect(if (isRunning) BusyOrange.copy(alpha = 0.3f) else OkGreen.copy(alpha = 0.3f))
                            }
                        )
                        .padding(10f.dp)
                ) {
                    Column {
                        if (isRunning) {
                            BasicText(
                                AppStrings.get("executing"),
                                style = TextStyle(BusyOrange, 12f.sp)
                            )
                        } else if (runResult != null) {
                            BasicText(
                                AppStrings.get("execution_result"),
                                style = TextStyle(OkGreen, 12f.sp, FontWeight.Bold)
                            )
                            Spacer(Modifier.height(4f.dp))
                            BasicText(
                                runResult.take(200),
                                style = TextStyle(contentColor.copy(alpha = 0.8f), 10f.sp, fontFamily = FontFamily.Monospace)
                            )
                        }
                    }
                }
            }
        }

        // 底部按钮：打开界面 / 运行 action.sh / 卸载
        Spacer(Modifier.height(12f.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
            if (plugin.hasWebUI) {
                LiquidButton(
                    onClick = { if (!isOpeningWebUI) onOpenWebUI() },
                    backdrop = backdrop,
                    modifier = Modifier.height(40f.dp).weight(1f),
                    tint = if (isOpeningWebUI) MutedGray else AppTheme.accent
                ) {
                    BasicText(
                        AppStrings.get(if (isOpeningWebUI) "plugin_opening_webui" else "plugin_open_webui"),
                        style = TextStyle(AppTheme.onAccent, 12f.sp)
                    )
                }
            }
            if (plugin.hasAction) {
                LiquidButton(
                    onClick = { if (!isRunning) onRun() },
                    backdrop = backdrop,
                    modifier = Modifier.height(40f.dp).weight(1f),
                    tint = if (isRunning) MutedGray else AppTheme.accentAlt
                ) {
                    BasicText(
                        AppStrings.get(if (isRunning) "executing" else "run_action"),
                        style = TextStyle(AppTheme.onAccent, 12f.sp)
                    )
                }
            }
            LiquidButton(
                onClick = { onDelete() },
                backdrop = backdrop,
                modifier = Modifier.height(40f.dp).weight(1f),
                tint = DangerRed
            ) {
                BasicText(AppStrings.get("uninstall"), style = TextStyle(Color.White, 12f.sp))
            }
        }
    }
}
