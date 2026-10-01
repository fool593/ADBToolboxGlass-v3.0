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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.PluginData
import com.example.adbtoolbox.common.PluginManager
import com.example.adbtoolbox.common.copyToClipboard
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 语义色（DangerRed / OkGreen / MutedGray / BusyOrange）定义在同包的 PluginsScreen.kt，
// 两个页面共用一份，避免不同步。

/**
 * 插件详情页（Android 实现）。
 *
 * 这里能做的每一件事都对应 PluginManager 的一个真实接口：
 * 启用/停用（disable 标记文件）、运行 action.sh（真实 stdout/stderr/退出码）、
 * 卸载（二次确认后执行 uninstall.sh 并删除目录）、打开模块自带 WebUI（本地 file:// 页面）、
 * 复制插件目录路径（系统剪贴板）。没有占位按钮，也没有模拟数据。
 */
@Composable
actual fun PluginDetailScreen(
    plugin: PluginData,
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    var currentPlugin by remember { mutableStateOf(plugin) }
    // action.sh 的真实输出（含退出码与失败原因），null 表示还没执行过
    var actionOutput by remember { mutableStateOf<String?>(null) }
    var runningAction by remember { mutableStateOf(false) }
    // 已解析到的 WebUI 入口文件；null 表示没解析到（未提供或已被删除）
    var entryPath by remember { mutableStateOf<String?>(null) }
    var entryLoaded by remember { mutableStateOf(false) }
    var openingWebUI by remember { mutableStateOf(false) }
    // 非空表示 WebUI 宿主已打开
    var openWebUIUrl by remember { mutableStateOf<String?>(null) }
    // 一次性提示（复制成功、启用/停用结果等），几秒后自动消失
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusNonce by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var confirmUninstall by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // 同样的文案再次出现也要重新计时，否则第二次不会自动消失
    LaunchedEffect(statusNonce) {
        if (statusNonce > 0) {
            delay(3000)
            statusMessage = null
        }
    }

    fun setStatus(message: String) {
        statusMessage = message
        statusNonce++
    }

    // 插件目录里的文件可能被外部改动（脚本删掉 action.sh、加了 webui 等），
    // 每次操作后都重新读一遍真实状态，而不是相信传进来的旧对象。
    fun reloadPlugin() {
        scope.launch {
            val updated = try {
                withContext(Dispatchers.Default) { PluginManager.getInstalledPlugins() }
            } catch (e: Exception) {
                emptyList<PluginData>()
            }
            updated.find { it.id == plugin.id }?.let { currentPlugin = it }
        }
    }

    // 解析 WebUI 入口（文件 IO，放后台），用于展示"到底有没有界面文件"
    LaunchedEffect(currentPlugin.id, currentPlugin.hasWebUI) {
        entryLoaded = false
        entryPath = try {
            withContext(Dispatchers.Default) { PluginManager.getWebUIPath(currentPlugin.id) }
        } catch (e: Exception) {
            null
        }
        entryLoaded = true
    }

    fun setEnabled(enabled: Boolean) {
        if (busy) return
        busy = true
        statusMessage = null
        scope.launch {
            val ok = try {
                withContext(Dispatchers.Default) {
                    if (enabled) PluginManager.enablePlugin(plugin.id) else PluginManager.disablePlugin(plugin.id)
                }
            } catch (e: Exception) {
                false
            }
            busy = false
            if (ok) {
                setStatus(AppStrings.get(if (enabled) "enabled" else "disabled"))
                reloadPlugin()
            } else {
                setStatus("${AppStrings.get("operation_failed")}: ${AppStrings.get(if (enabled) "enable" else "disable")}")
            }
        }
    }

    fun runAction() {
        if (runningAction) return
        runningAction = true
        actionOutput = null
        statusMessage = null
        scope.launch {
            actionOutput = try {
                withContext(Dispatchers.Default) { PluginManager.runAction(plugin.id) }
            } catch (e: Exception) {
                "${AppStrings.get("operation_failed")}: ${e.message ?: e.javaClass.simpleName}"
            }
            runningAction = false
        }
    }

    fun uninstall() {
        if (busy) return
        busy = true
        scope.launch {
            val ok = try {
                withContext(Dispatchers.Default) { PluginManager.uninstallPlugin(plugin.id) }
            } catch (e: Exception) {
                false
            }
            busy = false
            if (ok) {
                onBack()
            } else {
                setStatus("${AppStrings.get("operation_failed")}: ${AppStrings.get("uninstall")}")
            }
        }
    }

    fun openWebUI() {
        if (openingWebUI) return
        openingWebUI = true
        statusMessage = null
        scope.launch {
            // 重新解析一次：以点击这一刻磁盘上的真实文件为准
            val path = try {
                withContext(Dispatchers.Default) { PluginManager.getWebUIPath(plugin.id) }
            } catch (e: Exception) {
                null
            }
            entryPath = path
            entryLoaded = true
            if (path.isNullOrBlank()) {
                setStatus(AppStrings.get("plugin_webui_missing"))
            } else {
                openWebUIUrl = path
            }
            openingWebUI = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16f.dp)
        ) {
            Spacer(Modifier.height(24f.dp))

            // 顶部：返回 + 模块名 + 状态徽标
            Row {
                GlassBackButton(
                    backdrop = backdrop,
                    contentColor = contentColor,
                    onBack = onBack
                )
                Spacer(Modifier.width(12f.dp))
                Column(Modifier.weight(1f)) {
                    BasicText(
                        currentPlugin.name,
                        style = TextStyle(contentColor, 20f.sp, FontWeight.Medium)
                    )
                    Spacer(Modifier.height(6f.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6f.dp)) {
                        PerfBadge(
                            AppStrings.get(if (currentPlugin.isEnabled) "enabled" else "disabled"),
                            if (currentPlugin.isEnabled) OkGreen else DangerRed
                        )
                        if (currentPlugin.hasWebUI) {
                            PerfBadge(AppStrings.get("plugin_has_webui"), AppTheme.accent)
                        }
                        if (currentPlugin.hasAction) {
                            PerfBadge(AppStrings.get("plugin_has_action"), AppTheme.accentAlt)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16f.dp))

            statusMessage?.let { message ->
                GlassCard(backdrop = backdrop, pageType = "plugins") {
                    Box(Modifier.padding(14f.dp).fillMaxWidth()) {
                        BasicText(message, style = TextStyle(contentColor, 12f.sp))
                    }
                }
                Spacer(Modifier.height(12f.dp))
            }

            // 基本信息：全部来自 module.prop 与插件目录的真实状态
            GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(18f.dp)) {
                    SectionTitle(AppStrings.get("plugin_section_basic"), contentColor)
                    InfoRow(AppStrings.get("plugin_id"), currentPlugin.id, contentColor)
                    InfoRow(
                        AppStrings.get("version"),
                        "v${currentPlugin.version} (${currentPlugin.versionCode})",
                        contentColor
                    )
                    InfoRow(AppStrings.get("author"), currentPlugin.author, contentColor)
                    InfoRow(
                        AppStrings.get("plugin_size"),
                        currentPlugin.size.ifBlank { AppStrings.get("unknown") },
                        contentColor
                    )
                    InfoRow(
                        AppStrings.get("plugin_update_time"),
                        formatPluginTime(currentPlugin.updateTime),
                        contentColor
                    )
                    InfoRow(AppStrings.get("plugin_dir"), currentPlugin.pluginDir, contentColor)
                    if (currentPlugin.description.isNotBlank()) {
                        Spacer(Modifier.height(10f.dp))
                        BasicText(
                            currentPlugin.description,
                            style = TextStyle(contentColor.copy(alpha = 0.7f), 12f.sp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(12f.dp))

            // 状态与能力：WebUI / action.sh 是否存在，以及解析到的入口文件
            GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(18f.dp)) {
                    SectionTitle(AppStrings.get("plugin_section_capability"), contentColor)
                    InfoRow(
                        AppStrings.get("status"),
                        AppStrings.get(if (currentPlugin.isEnabled) "enabled" else "disabled"),
                        contentColor
                    )
                    InfoRow(
                        AppStrings.get("plugin_has_webui"),
                        AppStrings.get(if (currentPlugin.hasWebUI) "yes" else "no"),
                        contentColor
                    )
                    if (currentPlugin.hasWebUI) {
                        val entryText = entryPath
                            ?: if (entryLoaded) AppStrings.get("plugin_webui_missing") else AppStrings.get("loading")
                        InfoRow(
                            AppStrings.get("plugin_webui_file"),
                            entryText,
                            contentColor
                        )
                    }
                    InfoRow(
                        AppStrings.get("plugin_has_action"),
                        AppStrings.get(if (currentPlugin.hasAction) "yes" else "no"),
                        contentColor
                    )
                    InfoRow(
                        AppStrings.get("plugin_axeron"),
                        AppStrings.get(if (currentPlugin.axeronPlugin > 0) "yes" else "no"),
                        contentColor
                    )
                }
            }
            Spacer(Modifier.height(12f.dp))

            // 操作
            GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(18f.dp)) {
                    SectionTitle(AppStrings.get("actions"), contentColor)
                    if (!currentPlugin.isEnabled) {
                        BasicText(
                            AppStrings.get("plugin_enable_first"),
                            style = TextStyle(BusyOrange, 12f.sp)
                        )
                        Spacer(Modifier.height(10f.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
                        LiquidButton(
                            onClick = { setEnabled(!currentPlugin.isEnabled) },
                            backdrop = backdrop,
                            modifier = Modifier.height(40f.dp).weight(1f),
                            tint = if (currentPlugin.isEnabled) BusyOrange else OkGreen
                        ) {
                            BasicText(
                                AppStrings.get(if (currentPlugin.isEnabled) "disable" else "enable"),
                                style = TextStyle(AppTheme.onAccent, 12f.sp)
                            )
                        }
                        if (currentPlugin.hasAction) {
                            LiquidButton(
                                onClick = { runAction() },
                                backdrop = backdrop,
                                modifier = Modifier.height(40f.dp).weight(1f),
                                tint = if (runningAction) MutedGray else AppTheme.accentAlt
                            ) {
                                BasicText(
                                    AppStrings.get(if (runningAction) "executing" else "run_action"),
                                    style = TextStyle(AppTheme.onAccent, 12f.sp)
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10f.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10f.dp)) {
                        if (currentPlugin.hasWebUI) {
                            LiquidButton(
                                onClick = { openWebUI() },
                                backdrop = backdrop,
                                modifier = Modifier.height(40f.dp).weight(1f),
                                tint = if (openingWebUI) MutedGray else AppTheme.accent
                            ) {
                                BasicText(
                                    AppStrings.get(if (openingWebUI) "plugin_opening_webui" else "plugin_open_webui"),
                                    style = TextStyle(AppTheme.onAccent, 12f.sp)
                                )
                            }
                        }
                        LiquidButton(
                            onClick = {
                                val ok = copyToClipboard(currentPlugin.pluginDir)
                                setStatus(AppStrings.get(if (ok) "plugin_dir_copied" else "plugin_copy_failed"))
                            },
                            backdrop = backdrop,
                            modifier = Modifier.height(40f.dp).weight(1f),
                            tint = MutedGray
                        ) {
                            BasicText(
                                AppStrings.get("plugin_copy_dir"),
                                style = TextStyle(Color.White, 12f.sp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10f.dp))
                    // 卸载：破坏性操作，先弹确认
                    LiquidButton(
                        onClick = { confirmUninstall = true },
                        backdrop = backdrop,
                        modifier = Modifier.height(40f.dp).fillMaxWidth(),
                        tint = DangerRed
                    ) {
                        BasicText(
                            AppStrings.get("uninstall"),
                            style = TextStyle(Color.White, 12f.sp)
                        )
                    }
                }
            }

            // action.sh 的真实输出
            actionOutput?.let { output ->
                Spacer(Modifier.height(12f.dp))
                GlassCard(backdrop = backdrop, pageType = "plugins") {
                    Column(Modifier.padding(18f.dp)) {
                        Row {
                            BasicText(
                                AppStrings.get("action_output"),
                                Modifier.weight(1f),
                                style = TextStyle(contentColor, 16f.sp, FontWeight.Medium)
                            )
                            BasicText(
                                AppStrings.get("clear"),
                                Modifier
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .clickable { actionOutput = null },
                                style = TextStyle(AppTheme.accent, 12.sp)
                            )
                        }
                        Spacer(Modifier.height(10f.dp))
                        val exitCode = parseActionExitCode(output)
                        Row(horizontalArrangement = Arrangement.spacedBy(6f.dp)) {
                            PerfBadge(
                                AppStrings.get(if (exitCode == 0) "success" else "failed"),
                                if (exitCode == 0) OkGreen else DangerRed
                            )
                            if (exitCode != null) {
                                PerfBadge(
                                    "${AppStrings.get("exit_code")}: $exitCode",
                                    if (exitCode == 0) OkGreen else DangerRed
                                )
                            }
                        }
                        Spacer(Modifier.height(10f.dp))
                        if (exitCode != 0) {
                            BasicText(
                                AppStrings.get("run_failed_reason"),
                                style = TextStyle(DangerRed, 12f.sp, FontWeight.Medium)
                            )
                            Spacer(Modifier.height(6f.dp))
                        }
                        PerfTextBox(text = output, contentColor = contentColor)
                    }
                }
            }

            Spacer(Modifier.height(80f.dp))
        }

        // 模块自带界面：宿主盖住详情页，关闭后回到详情页
        val activeWebUIUrl = openWebUIUrl
        if (activeWebUIUrl != null) {
            ModuleWebUIHost(
                url = activeWebUIUrl,
                onClose = { openWebUIUrl = null }
            )
        }
    }

    if (confirmUninstall) {
        PerfConfirmDialog(
            title = AppStrings.get("plugin_confirm_uninstall"),
            message = "${AppStrings.get("plugin_uninstall_warning")}\n\n${currentPlugin.name}\n${currentPlugin.pluginDir}",
            confirmLabel = AppStrings.get("uninstall"),
            contentColor = contentColor,
            onConfirm = {
                confirmUninstall = false
                uninstall()
            },
            onDismiss = { confirmUninstall = false }
        )
    }
}

/**
 * 从 PluginManager.runAction 的返回文本里读出退出码。
 *
 * runAction 的格式是固定的首行 `Exit code: N`；不是这个格式（例如
 * `Error: This plugin has no action.sh`、`Execution failed: ...`）说明
 * 脚本根本没跑起来，返回 null，界面按"失败"展示并把原文完整贴出来。
 */
private fun parseActionExitCode(output: String): Int? {
    val firstLine = output.lineSequence().firstOrNull()?.trim() ?: return null
    val prefix = "Exit code: "
    if (!firstLine.startsWith(prefix)) return null
    return firstLine.removePrefix(prefix).trim().toIntOrNull()
}

/** 更新时间格式化；取不到时间戳时显示"未知"，不显示 1970 这种假数据。 */
private fun formatPluginTime(millis: Long): String {
    if (millis <= 0L) return AppStrings.get("unknown")
    return try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    } catch (e: Exception) {
        AppStrings.get("unknown")
    }
}

@Composable
private fun InfoRow(label: String, value: String, contentColor: Color) {
    Row(Modifier.padding(vertical = 4.dp)) {
        BasicText(
            "$label",
            Modifier.width(92.dp),
            style = TextStyle(contentColor.copy(alpha = 0.5f), 12f.sp)
        )
        BasicText(
            value.ifBlank { AppStrings.get("unknown") },
            Modifier.weight(1f),
            style = TextStyle(contentColor, 12f.sp)
        )
    }
}
