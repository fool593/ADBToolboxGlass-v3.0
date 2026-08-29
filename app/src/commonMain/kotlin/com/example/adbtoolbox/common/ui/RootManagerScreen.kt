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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RootManagerScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    var isRooted by remember { mutableStateOf(false) }
    var suVersion by remember { mutableStateOf("") }
    var busyboxVersion by remember { mutableStateOf("") }
    var resultOutput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isRooted = withContext(Dispatchers.Default) { ADBTools.isRooted() }
        suVersion = withContext(Dispatchers.Default) { ADBTools.getSuVersion() }
        busyboxVersion = withContext(Dispatchers.Default) { ADBTools.getBusyBoxVersion() }
    }

    fun runRootCommand(label: String, command: String) {
        resultOutput = "${AppStrings.get("executing")}: $label..."
        scope.launch {
            // 直接使用 su 执行 Root 命令，不经过 Shizuku
            val result = withContext(Dispatchers.Default) {
                var exitCode = -1
                var output = ""
                var error = ""
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
                    output = process.inputStream.bufferedReader().readText()
                    error = process.errorStream.bufferedReader().readText()
                    exitCode = process.waitFor()
                } catch (e: Exception) {
                    error = e.message ?: "Unknown error"
                }
                Triple(exitCode, output, error)
            }
            resultOutput = buildString {
                appendLine("[$label]")
                appendLine("${AppStrings.get("exit_code")}: ${result.first}")
                if (result.second.isNotBlank()) appendLine("${AppStrings.get("output")}: ${result.second.trim()}")
                if (result.third.isNotBlank()) appendLine("${AppStrings.get("error_output")}: ${result.third.trim()}")
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
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
            Spacer(Modifier.height(12f.dp))
            BasicText(AppStrings.get("root_manager"), style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold))
        }
        Spacer(Modifier.height(16f.dp))

        GlassCard(backdrop = backdrop, pageType = "plugins") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("root_status"), contentColor)
                InfoRow(AppStrings.get("root_permission"), if (isRooted) AppStrings.get("root_obtained") else AppStrings.get("root_not_obtained"), contentColor)
                InfoRow(AppStrings.get("su_version"), suVersion, contentColor)
                InfoRow(AppStrings.get("busybox_version"), busyboxVersion, contentColor)
                Spacer(Modifier.height(12f.dp))
                LiquidButton(
                    onClick = {
                        scope.launch {
                            val ok = withContext(Dispatchers.Default) { ADBTools.requestRootPermission() }
                            resultOutput = if (ok) AppStrings.get("root_verified") else AppStrings.get("root_verify_failed")
                            isRooted = withContext(Dispatchers.Default) { ADBTools.isRooted() }
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.height(44f.dp),
                    tint = Color(0xFFAF52DE)
                ) {
                    BasicText(AppStrings.get("verify_root"), Modifier.padding(horizontal = 8f.dp), style = TextStyle(Color.White, 14f.sp))
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        GlassCard(backdrop = backdrop, pageType = "plugins") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("root_quick_commands"), contentColor)
                Spacer(Modifier.height(8f.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    ActionButton(backdrop, AppStrings.get("view_uid"), Color(0xFF0088FF)) { runRootCommand(AppStrings.get("view_uid"), "id") }
                    ActionButton(backdrop, AppStrings.get("mount_rw"), Color(0xFFFF9500)) { runRootCommand(AppStrings.get("mount_rw"), "mount -o remount,rw /system") }
                    ActionButton(backdrop, AppStrings.get("mount_ro"), Color(0xFF34C759)) { runRootCommand(AppStrings.get("mount_ro"), "mount -o remount,ro /system") }
                }
                Spacer(Modifier.height(8f.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    ActionButton(backdrop, AppStrings.get("modify_hosts"), Color(0xFFFF3B30)) { runRootCommand(AppStrings.get("modify_hosts"), "echo '127.0.0.1 localhost' > /etc/hosts") }
                    ActionButton(backdrop, AppStrings.get("clear_data"), Color(0xFFAF52DE)) { runRootCommand(AppStrings.get("clear_data"), "pm clear com.android.chrome") }
                    ActionButton(backdrop, AppStrings.get("view_processes"), Color(0xFF5AC8FA)) { runRootCommand(AppStrings.get("view_processes"), "ps") }
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        if (resultOutput.isNotEmpty()) {
            GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(20f.dp)) {
                    SectionTitle(AppStrings.get("execution_result"), contentColor)
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
fun RowScope.ActionButton(backdrop: Backdrop, label: String, tint: Color, onClick: () -> Unit) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = Modifier.height(40f.dp).weight(1f),
        tint = tint
    ) {
        BasicText(label, Modifier.padding(horizontal = 4f.dp), style = TextStyle(Color.White, 11f.sp))
    }
}
