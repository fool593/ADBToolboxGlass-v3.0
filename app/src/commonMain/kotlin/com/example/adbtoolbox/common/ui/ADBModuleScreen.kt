package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppStrings
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 刷入模式与当前设备可用权限的检测结果。 */
private data class ModuleCapability(
    val rooted: Boolean = false,
    val magisk: Boolean = false,
    val shizuku: Boolean = false,
    val checked: Boolean = false
) {
    val rootUsable: Boolean get() = rooted
    val adbUsable: Boolean get() = shizuku
    val anyUsable: Boolean get() = rootUsable || adbUsable
}

@Composable
fun ADBModuleScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onPickFile: () -> Unit,
    onBack: () -> Unit,
    selectedFilePath: String?
) {
    var installLog by remember { mutableStateOf("") }
    var isInstalling by remember { mutableStateOf(false) }
    // 是否使用 Root 模式刷入（false = ADB/Shizuku 模式）
    var useRootMode by remember { mutableStateOf(true) }
    var capability by remember { mutableStateOf(ModuleCapability()) }
    var isSuccess by remember { mutableStateOf(false) }
    var failureMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 打开页面即检测 Root / Magisk / Shizuku 是否可用，避免用户点了按钮才发现没权限
    LaunchedEffect(Unit) {
        val detected = withContext(Dispatchers.Default) {
            ModuleCapability(
                rooted = ADBTools.isRooted(),
                magisk = ADBTools.hasMagisk(),
                shizuku = ADBTools.isShizukuAvailable(),
                checked = true
            )
        }
        capability = detected
        // 默认选择真正可用的模式；只有都没权限时才保持 Root 并提示
        useRootMode = when {
            detected.rootUsable -> true
            detected.adbUsable -> false
            else -> true
        }
        if (!detected.anyUsable) {
            failureMessage = AppStrings.get("need_root_or_shizuku")
        }
    }

    fun installModule() {
        if (selectedFilePath.isNullOrBlank()) {
            // 以前这里直接 return，按钮点了完全没反应
            failureMessage = AppStrings.get("no_file_selected")
            return
        }
        if (isInstalling) return
        if (!capability.rootUsable && !capability.adbUsable) {
            failureMessage = AppStrings.get("need_root_or_shizuku")
            return
        }

        isSuccess = false
        failureMessage = null
        isInstalling = true
        installLog = "${AppStrings.get("module_installing")}...\n"
        val path = selectedFilePath
        val rootMode = useRootMode && capability.rootUsable
        scope.launch {
            val result = try {
                withContext(Dispatchers.Default) {
                    if (rootMode) ADBTools.installModuleViaRoot(path)
                    else ADBTools.installModuleViaADB(path)
                }
            } catch (e: Exception) {
                // 以前异常会直接抛出协程，日志永远停在"正在刷入..."，用户看不到任何失败原因
                "${AppStrings.get("operation_failed")}: ${e.javaClass.simpleName}: ${e.message}"
            }
            // 注意：不能简单匹配任意 "Error:"，因为 ADB 模式的正常日志里也会出现
            // "Warning: Magisk not detected"；只认真正表示终止失败的几行。
            val failureLines = result.lineSequence()
                .map { it.trim() }
                .filter { line ->
                    line.startsWith("Error:") ||
                        line.contains("Operation failed") ||
                        line.contains("Installation failed")
                }
                .toList()
            val failed = failureLines.isNotEmpty()
            isSuccess = !failed && result.isNotBlank()
            failureMessage = failureLines.firstOrNull()
                ?: if (failed) AppStrings.get("operation_failed") else null
            installLog = result
            isInstalling = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16f.dp)
    ) {
        Spacer(Modifier.height(24f.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
            Spacer(Modifier.height(12f.dp))
            BasicText(
                AppStrings.get("root_module"),
                style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold)
            )
        }
        Spacer(Modifier.height(12f.dp))

        // 刷入模式 + 权限提示
        GlassCard(backdrop = backdrop, pageType = "adbmodule") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("flash_mode"), contentColor)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        if (useRootMode) AppStrings.get("root_mode") else AppStrings.get("adb_mode"),
                        style = TextStyle(contentColor, 14f.sp)
                    )
                    Spacer(Modifier.width(12f.dp))
                    LiquidToggle(
                        selected = { useRootMode },
                        onSelect = { root ->
                            // 不可用的模式不允许打开，并直接说明原因
                            if (root && capability.checked && !capability.rootUsable) {
                                failureMessage = "${AppStrings.get("root_not_obtained")}\n${AppStrings.get("need_root_or_shizuku")}"
                            } else if (!root && capability.checked && !capability.adbUsable) {
                                failureMessage = "${AppStrings.get("shizuku_not_connected")}\n${AppStrings.get("wireless_debugging_hint")}"
                            } else {
                                useRootMode = root
                            }
                        },
                        backdrop = backdrop,
                        modifier = Modifier.size(51f.dp, 31f.dp)
                    )
                }
                Spacer(Modifier.height(8f.dp))
                BasicText(
                    if (useRootMode) AppStrings.get("root_mode_hint") else AppStrings.get("adb_mode_hint"),
                    style = TextStyle(contentColor.copy(alpha = 0.7f), 12f.sp)
                )
                if (capability.checked) {
                    Spacer(Modifier.height(8f.dp))
                    val status = buildString {
                        append(if (capability.rootUsable) "Root: OK" else "Root: ${AppStrings.get("root_not_obtained")}")
                        if (capability.rootUsable) {
                            append(if (capability.magisk) " / Magisk: OK" else " / Magisk: --")
                        }
                        append("   Shizuku: ")
                        append(if (capability.shizuku) AppStrings.get("shizuku_connected") else AppStrings.get("shizuku_not_connected"))
                    }
                    BasicText(
                        status,
                        style = TextStyle(
                            if (capability.anyUsable) Color(0xFF34C759) else Color(0xFFFF3B30),
                            11f.sp
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 文件选择
        GlassCard(backdrop = backdrop, pageType = "adbmodule") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("select_module_file"), contentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    LiquidButton(
                        onClick = onPickFile,
                        backdrop = backdrop,
                        modifier = Modifier.height(44f.dp).weight(1f),
                        tint = Color(0xFF0088FF)
                    ) {
                        BasicText(
                            AppStrings.get("choose_file"),
                            Modifier.padding(horizontal = 8f.dp),
                            style = TextStyle(Color.White, 13f.sp)
                        )
                    }
                }
                Spacer(Modifier.height(12f.dp))
                if (selectedFilePath.isNullOrBlank()) {
                    BasicText(
                        AppStrings.get("no_file_selected"),
                        style = TextStyle(contentColor.copy(alpha = 0.4f), 12f.sp)
                    )
                } else {
                    BasicText(
                        selectedFilePath,
                        style = TextStyle(contentColor, 11f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        maxLines = 2
                    )
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 刷入按钮（未选文件时也可点击，会明确提示"未选择文件"）
        LiquidButton(
            onClick = { installModule() },
            backdrop = backdrop,
            modifier = Modifier.fillMaxWidth().height(50f.dp),
            tint = when {
                isInstalling -> Color(0xFF8E8E93)
                selectedFilePath.isNullOrBlank() -> Color(0xFF8E8E93)
                else -> Color(0xFF34C759)
            }
        ) {
            BasicText(
                if (isInstalling) AppStrings.get("module_installing") else AppStrings.get("flash_module"),
                style = TextStyle(Color.White, 16f.sp, androidx.compose.ui.text.font.FontWeight.Medium)
            )
        }
        Spacer(Modifier.height(16f.dp))

        // 失败原因（必须让用户看到，而不是只在日志里）
        failureMessage?.let { message ->
            GlassCard(backdrop = backdrop, pageType = "adbmodule") {
                Column(Modifier.padding(20f.dp)) {
                    SectionTitle(AppStrings.get("failed"), contentColor)
                    BasicText(
                        message,
                        style = TextStyle(Color(0xFFFF3B30), 12f.sp)
                    )
                }
            }
            Spacer(Modifier.height(16f.dp))
        }

        // 刷入日志
        if (installLog.isNotEmpty()) {
            GlassCard(backdrop = backdrop, pageType = "adbmodule") {
                Column(Modifier.padding(20f.dp)) {
                    SectionTitle(
                        if (isSuccess) "${AppStrings.get("install_log")} - ${AppStrings.get("success")}"
                        else AppStrings.get("install_log"),
                        contentColor
                    )
                    BasicText(
                        installLog,
                        style = TextStyle(contentColor, 11f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                }
            }
        }

        Spacer(Modifier.height(80f.dp))
    }
}
