package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ADBPanelScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    var resultOutput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun runAction(label: String, command: String) {
        resultOutput = "${AppStrings.get("execute")}: $label..."
        scope.launch {
            val result = withContext(Dispatchers.Default) { ADBTools.execCommand(command) }
            resultOutput = buildString {
                appendLine("[$label]")
                appendLine("${AppStrings.get("success")}: ${result.exitCode}")
                if (result.output.isNotBlank()) appendLine("${AppStrings.get("output")}: ${result.output.trim()}")
                if (result.error.isNotBlank()) appendLine("${AppStrings.get("failed")}: ${result.error.trim()}")
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16f.dp)
    ) {
        Spacer(Modifier.height(24f.dp))
        BasicText(AppStrings.get("adb_quick_panel"), style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold))
        Spacer(Modifier.height(16f.dp))

        // 设备控制
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("device_control"), contentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    ActionButton(backdrop, AppStrings.get("reboot"), Color(0xFFFF9500)) { runAction(AppStrings.get("reboot"), "reboot") }
                    ActionButton(backdrop, AppStrings.get("recovery"), Color(0xFFFF3B30)) { runAction(AppStrings.get("recovery"), "reboot recovery") }
                    ActionButton(backdrop, AppStrings.get("bootloader"), Color(0xFFAF52DE)) { runAction(AppStrings.get("bootloader"), "reboot bootloader") }
                }
                Spacer(Modifier.height(8f.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    ActionButton(backdrop, AppStrings.get("shutdown"), Color(0xFF1C1C1E)) { runAction(AppStrings.get("shutdown"), "reboot -p") }
                    ActionButton(backdrop, AppStrings.get("restart_systemui"), Color(0xFF0088FF)) { runAction(AppStrings.get("restart_systemui"), "pkill com.android.systemui") }
                    ActionButton(backdrop, AppStrings.get("screenshot"), Color(0xFF34C759)) { runAction(AppStrings.get("screenshot"), "screencap -p /sdcard/screenshot.png") }
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 系统设置
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("system_settings"), contentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    ActionButton(backdrop, AppStrings.get("enable_adb"), Color(0xFF34C759)) { runAction(AppStrings.get("enable_adb"), "settings put global adb_enabled 1") }
                    ActionButton(backdrop, AppStrings.get("disable_adb"), Color(0xFFFF3B30)) { runAction(AppStrings.get("disable_adb"), "settings put global adb_enabled 0") }
                    ActionButton(backdrop, AppStrings.get("install_unknown_apps"), Color(0xFFFF9500)) { runAction(AppStrings.get("install_unknown_apps"), "settings put global install_non_market_apps 1") }
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 清理操作
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("cleanup_operations"), contentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    ActionButton(backdrop, AppStrings.get("clean_all_cache"), Color(0xFF0088FF)) {
                        val ok = ADBTools.clearAllCache()
                        resultOutput = if (ok) AppStrings.get("cache_cleared") else AppStrings.get("operation_failed")
                    }
                    ActionButton(backdrop, AppStrings.get("clean_logcat"), Color(0xFFAF52DE)) {
                        ADBTools.clearLogcat()
                        resultOutput = AppStrings.get("clear_logcat_done")
                    }
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 执行结果
        if (resultOutput.isNotEmpty()) {
            GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(20f.dp)) {
                    SectionTitle(AppStrings.get("output"), contentColor)
                    BasicText(
                        resultOutput,
                        style = TextStyle(contentColor, 12f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                }
            }
        }

        Spacer(Modifier.height(80f.dp))
    }
}

@Composable
fun RowScope.ActionButton(backdrop: Backdrop, label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = modifier.height(44f.dp).weight(1f),
        tint = tint
    ) {
        BasicText(label, Modifier.padding(horizontal = 4f.dp), style = TextStyle(Color.White, 12f.sp))
    }
}
