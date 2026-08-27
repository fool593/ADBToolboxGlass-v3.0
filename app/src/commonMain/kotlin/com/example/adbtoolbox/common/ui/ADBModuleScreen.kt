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
fun ADBModuleScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onPickFile: () -> Unit,
    onBack: () -> Unit,
    selectedFilePath: String?
) {
    var installLog by remember { mutableStateOf("") }
    var isInstalling by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun installModule() {
        if (selectedFilePath.isNullOrBlank()) return
        isInstalling = true
        installLog = "${AppStrings.get("module_installing")}...\n"
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                ADBTools.installModuleViaRoot(selectedFilePath)
            }
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

        // Root模式提示
        GlassCard(backdrop = backdrop, pageType = "apps") {
            Column(Modifier.padding(20f.dp)) {
                BasicText(
                    AppStrings.get("root_mode_hint"),
                    style = TextStyle(contentColor.copy(alpha = 0.7f), 12f.sp)
                )
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 文件选择
        GlassCard(backdrop = backdrop, pageType = "apps") {
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

        // 刷入按钮
        if (!selectedFilePath.isNullOrBlank()) {
            LiquidButton(
                onClick = { if (!isInstalling) installModule() },
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().height(50f.dp),
                tint = if (isInstalling) Color(0xFF8E8E93) else Color(0xFF34C759)
            ) {
                BasicText(
                    if (isInstalling) AppStrings.get("module_installing") else AppStrings.get("flash_module"),
                    style = TextStyle(Color.White, 16f.sp, androidx.compose.ui.text.font.FontWeight.Medium)
                )
            }
            Spacer(Modifier.height(16f.dp))
        }

        // 刷入日志
        if (installLog.isNotEmpty()) {
            GlassCard(backdrop = backdrop, pageType = "apps") {
                Column(Modifier.padding(20f.dp)) {
                    SectionTitle(AppStrings.get("install_log"), contentColor)
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
