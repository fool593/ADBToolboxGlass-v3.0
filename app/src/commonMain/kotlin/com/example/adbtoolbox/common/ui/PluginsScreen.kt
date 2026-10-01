package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.PluginData
import com.example.adbtoolbox.common.PluginManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

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
                    style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold),
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
                    tint = Color(0xFF34C759)
                ) {
                    BasicText(
                        AppStrings.get("install_plugin"),
                        Modifier.padding(horizontal = 12f.dp),
                        style = TextStyle(Color.White, 12f.sp)
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
                                    if (result.contains("success", ignoreCase = true) || result.contains("成功")) Color(0xFF34C759) else Color(0xFFFF3B30),
                                    12f.sp
                                )
                            )
                            Spacer(Modifier.height(8f.dp))
                            LiquidButton(
                                onClick = { shownInstallResult = result },
                                backdrop = backdrop,
                                modifier = Modifier.height(32f.dp),
                                tint = Color(0xFF8E8E93)
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
                            style = TextStyle(Color(0xFFFF3B30), 12f.sp)
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
                            onDelete = { deletePlugin(plugin) },
                            isRunning = runningPluginId == plugin.id,
                            runResult = runResult?.takeIf { it.first == plugin.id }?.second
                        )
                    }
                    item { Spacer(Modifier.height(100f.dp)) }
                }
            }
        }

        // 右下角悬浮 + 按钮
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20f.dp)
                .size(56f.dp)
                .clip(RoundedRectangle(28f.dp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(28f.dp) },
                    effects = {
                        vibrancy()
                        blur(16f.dp.toPx())
                        lens(8f.dp.toPx(), 16f.dp.toPx())
                    },
                    highlight = { Highlight.Plain },
                    onDrawSurface = {
                        drawRect(Color(0xFFAF52DE).copy(alpha = 0.9f))
                    }
                )
                .clickable {
                    AppCache.pickPluginFileTrigger.value++
                    errorMessage = null
                },
            contentAlignment = Alignment.Center
        ) {
            BasicText("+", style = TextStyle(Color.White, 28f.sp, androidx.compose.ui.text.font.FontWeight.Bold))
        }
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
    isRunning: Boolean = false,
    runResult: String? = null
) {
    Column(
        Modifier
            .fillMaxWidth()
            .liquidGlassItem(backdrop = backdrop, corner = 20.dp, onClick = onClick)
            .padding(16f.dp)
    ) {
        // 顶部行：大小标签 + 名称 + 开关
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                // 左上角：大小和运行标签
                Row(horizontalArrangement = Arrangement.spacedBy(6f.dp)) {
                    if (plugin.size.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedRectangle(6f.dp))
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { RoundedRectangle(6f.dp) },
                                    effects = { blur(4f.dp.toPx()) },
                                    onDrawSurface = { drawRect(Color(0xFFAF52DE).copy(alpha = 0.6f)) }
                                )
                                .padding(horizontal = 6f.dp, vertical = 2f.dp)
                        ) {
                            BasicText(plugin.size, style = TextStyle(Color.White, 10f.sp))
                        }
                    }
                    if (plugin.hasAction) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedRectangle(6f.dp))
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { RoundedRectangle(6f.dp) },
                                    effects = { blur(4f.dp.toPx()) },
                                    onDrawSurface = { drawRect(Color(0xFF34C759).copy(alpha = 0.6f)) }
                                )
                                .padding(horizontal = 6f.dp, vertical = 2f.dp)
                        ) {
                            BasicText(AppStrings.get("run_action"), style = TextStyle(Color.White, 10f.sp))
                        }
                    }
                }
                Spacer(Modifier.height(8f.dp))
                // 插件名称
                BasicText(
                    plugin.name,
                    style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium),
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
            Box(
                modifier = Modifier
                    .size(52f.dp, 32f.dp)
                    .clip(RoundedRectangle(16f.dp))
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(16f.dp) },
                        effects = { blur(8f.dp.toPx()) },
                        onDrawSurface = {
                            drawRect(
                                if (plugin.isEnabled) Color(0xFFAF52DE) else Color(0xFF4A4A4A)
                            )
                        }
                    )
                    .clickable { onToggleEnable() },
                contentAlignment = if (plugin.isEnabled) Alignment.CenterEnd else Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3f.dp)
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
        if (isRunning || runResult != null) {
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
                            drawRect(if (isRunning) Color(0xFFFF9500).copy(alpha = 0.3f) else Color(0xFF34C759).copy(alpha = 0.3f))
                        }
                    )
                    .padding(10f.dp)
            ) {
                if (isRunning) {
                    BasicText(
                        "⏳ ${AppStrings.get("executing")}...",
                        style = TextStyle(Color(0xFFFF9500), 12f.sp)
                    )
                } else if (runResult != null) {
                    BasicText(
                        "✓ ${AppStrings.get("execution_result")}",
                        style = TextStyle(Color(0xFF34C759), 12f.sp, androidx.compose.ui.text.font.FontWeight.Bold)
                    )
                    Spacer(Modifier.height(4f.dp))
                    BasicText(
                        runResult.take(200),
                        style = TextStyle(contentColor.copy(alpha = 0.8f), 10f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                }
            }
        }

        // 底部：运行按钮 + 删除按钮
        Spacer(Modifier.height(12f.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
            if (plugin.hasAction) {
                LiquidButton(
                    onClick = { if (!isRunning) onRun() },
                    backdrop = backdrop,
                    modifier = Modifier.height(40f.dp).weight(1f),
                    tint = if (isRunning) Color(0xFF8E8E93) else Color(0xFFAF52DE)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRunning) {
                            BasicText("⏳ ", style = TextStyle(Color.White, 12f.sp))
                            BasicText(AppStrings.get("executing"), style = TextStyle(Color.White, 12f.sp))
                        } else {
                            BasicText("▶ ", style = TextStyle(Color.White, 12f.sp))
                            BasicText(AppStrings.get("run_action"), style = TextStyle(Color.White, 12f.sp))
                        }
                    }
                }
            }
            LiquidButton(
                onClick = { onDelete() },
                backdrop = backdrop,
                modifier = Modifier.height(40f.dp).then(if (plugin.hasAction) Modifier.size(40f.dp) else Modifier.weight(1f)),
                tint = Color(0xFFFF3B30)
            ) {
                BasicText("🗑", style = TextStyle(Color.White, 14f.sp))
            }
        }
    }
}
