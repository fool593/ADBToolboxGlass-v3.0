package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.theme.AppLayout
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TempRootScreen(backdrop: Backdrop) {
    val scope = rememberCoroutineScope()
    var cpuModel by remember { mutableStateOf("") }
    var cpuVendor by remember { mutableStateOf("") }
    var isFlashing by remember { mutableStateOf(false) }
    var flashResult by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<String?>(null) }
    var permissionWarning by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 读取 /proc/cpuinfo 属于磁盘 I/O，放后台线程，避免阻塞首帧
        cpuModel = withContext(Dispatchers.Default) { ADBTools.getCpuModel() }
        cpuVendor = withContext(Dispatchers.Default) { ADBTools.getCpuVendor() }
        // 进入页面时同步已选择的文件（选择结果保存在 AppCache 中）
        selectedFile = AppCache.selectedTempRootPath.value
        permissionWarning = withContext(Dispatchers.Default) {
            !(ADBTools.isShizukuAvailable() || ADBTools.isRooted())
        }
    }

    // 观察"已选择文件"本身而不是选择触发器：
    // MainActivity 在拉起选择器后会立刻把触发器重置为 0，若监听触发器，
    // 用户选完文件后页面永远读不到结果（表现为点了没反应、执行提权入口不出现）。
    LaunchedEffect(AppCache.selectedTempRootPath.value) {
        AppCache.selectedTempRootPath.value?.let { selectedFile = it }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(AppLayout.sectionGap)
    ) {
        BasicText(AppStrings.get("temp_root_title"), style = TextStyle(Color.White, AppLayout.titleSize, FontWeight.Bold))

        // 处理器信息
        GlassCard(backdrop = backdrop, pageType = "plugins") {
            Column(Modifier.padding(AppLayout.cardPad), verticalArrangement = Arrangement.spacedBy(AppLayout.innerGap)) {
                BasicText(AppStrings.get("cpu_info"), style = TextStyle(Color.White, AppLayout.sectionTitleSize, FontWeight.Medium))
                InfoRow(AppStrings.get("model"), cpuModel, Color.White)
                InfoRow(
                    AppStrings.get("temp_root_vendor"),
                    when (cpuVendor) {
                        "mediatek" -> AppStrings.get("temp_root_soc_mediatek")
                        "qualcomm" -> AppStrings.get("temp_root_soc_qualcomm")
                        else -> cpuVendor
                    },
                    Color.White
                )
                BasicText(
                    AppStrings.get("temp_root_cpu_hint"),
                    style = TextStyle(Color.White.copy(alpha = 0.6f), 12.sp)
                )
            }
        }

        // 选择提权包
        GlassCard(backdrop = backdrop, pageType = "plugins") {
            Column(Modifier.padding(AppLayout.cardPad), verticalArrangement = Arrangement.spacedBy(AppLayout.innerGap)) {
                BasicText(AppStrings.get("temp_root_package"), style = TextStyle(Color.White, AppLayout.sectionTitleSize, FontWeight.Medium))
                // 取到局部 val 再判空：委托属性（by remember）无法智能转换，用局部变量即可彻底去掉 !!
                val currentFile = selectedFile
                if (currentFile != null) {
                    BasicText(
                        String.format(AppStrings.get("temp_root_selected"), currentFile.substringAfterLast('/')),
                        style = TextStyle(Color(0xFF34C759), AppLayout.bodySize)
                    )
                } else {
                    BasicText(AppStrings.get("no_file_selected"), style = TextStyle(Color.White.copy(alpha = 0.6f), AppLayout.bodySize))
                }
                LiquidButton(
                    onClick = { AppCache.pickTempRootFileTrigger.value++ },
                    backdrop = backdrop,
                    modifier = Modifier.height(44.dp).fillMaxWidth(),
                    tint = Color(0xFF007AFF)
                ) {
                    BasicText(AppStrings.get("temp_root_pick_zip"), style = TextStyle(Color.White, AppLayout.bodySize))
                }
                if (permissionWarning) {
                    BasicText(
                        AppStrings.get("temp_root_perm_hint"),
                        style = TextStyle(Color(0xFFFF9500), 12.sp)
                    )
                }
            }
        }

        // 执行提权
        if (selectedFile != null) {
            GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(AppLayout.cardPad), verticalArrangement = Arrangement.spacedBy(AppLayout.innerGap)) {
                    BasicText(AppStrings.get("temp_root_flash"), style = TextStyle(Color.White, AppLayout.sectionTitleSize, FontWeight.Medium))
                    BasicText(
                        AppStrings.get("temp_root_warn"),
                        style = TextStyle(Color(0xFFFF9500), 12.sp)
                    )
                    LiquidButton(
                        onClick = {
                            // 提权过程不可重入，避免并发刷入同一份提权包
                            if (!isFlashing) {
                                val zipPath = selectedFile
                                if (zipPath == null) {
                                    flashResult = AppStrings.get("temp_root_fail_no_file")
                                } else {
                                    isFlashing = true
                                    flashResult = ""
                                    scope.launch {
                                        val result = try {
                                            withContext(Dispatchers.Default) { ADBTools.flashTempRootModule(zipPath) }
                                        } catch (e: Exception) {
                                            null
                                        }
                                        if (result == null) {
                                            flashResult = AppStrings.get("temp_root_fail_exception")
                                        } else {
                                            flashResult = if (result.exitCode == 0) {
                                                String.format(AppStrings.get("temp_root_ok"), result.output)
                                            } else {
                                                String.format(AppStrings.get("temp_root_fail"), result.error, result.output)
                                            }
                                        }
                                        isFlashing = false
                                    }
                                }
                            }
                        },
                        backdrop = backdrop,
                        modifier = Modifier.height(44.dp).fillMaxWidth(),
                        tint = if (isFlashing) Color(0xFF8E8E93) else Color(0xFFFF3B30)
                    ) {
                        BasicText(
                            if (isFlashing) AppStrings.get("temp_root_running") else AppStrings.get("temp_root_start"),
                            style = TextStyle(Color.White, AppLayout.bodySize)
                        )
                    }
                    if (flashResult.isNotEmpty()) {
                        BasicText(flashResult, style = TextStyle(Color.White, 12.sp))
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}
