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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        BasicText("临时 Root 提权", style = TextStyle(Color.White, 24.sp, FontWeight.Bold))

        // 处理器信息
        GlassCard(backdrop = backdrop, pageType = "plugins") {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText("处理器信息", style = TextStyle(Color.White, 18.sp, FontWeight.Medium))
                InfoRow("型号", cpuModel, Color.White)
                InfoRow("厂商", if (cpuVendor == "mediatek") "联发科 (天玑)" else if (cpuVendor == "qualcomm") "高通 (骁龙)" else cpuVendor, Color.White)
                BasicText(
                    "提示：请选择与您的处理器型号匹配的提权包，不同机型的提权方法不同。",
                    style = TextStyle(Color.White.copy(alpha = 0.6f), 12.sp)
                )
            }
        }

        // 选择提权包
        GlassCard(backdrop = backdrop, pageType = "plugins") {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText("提权包", style = TextStyle(Color.White, 18.sp, FontWeight.Medium))
                if (selectedFile != null) {
                    BasicText("已选择: ${selectedFile!!.substringAfterLast('/')}", style = TextStyle(Color(0xFF34C759), 14.sp))
                } else {
                    BasicText("未选择文件", style = TextStyle(Color.White.copy(alpha = 0.6f), 14.sp))
                }
                LiquidButton(
                    onClick = { AppCache.pickTempRootFileTrigger.value++ },
                    backdrop = backdrop,
                    modifier = Modifier.height(44.dp).fillMaxWidth(),
                    tint = Color(0xFF007AFF)
                ) {
                    BasicText("选择提权包 (ZIP)", style = TextStyle(Color.White, 14.sp))
                }
                if (permissionWarning) {
                    BasicText(
                        "提示：提权包需要以 shell/Root 身份执行脚本，当前未检测到 Shizuku(ADB) 或 Root 权限，执行大概率会失败。",
                        style = TextStyle(Color(0xFFFF9500), 12.sp)
                    )
                }
            }
        }

        // 执行提权
        if (selectedFile != null) {
            GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    BasicText("执行提权", style = TextStyle(Color.White, 18.sp, FontWeight.Medium))
                    BasicText(
                        "警告：提权操作有风险，可能导致设备无法启动。请确保已备份重要数据。",
                        style = TextStyle(Color(0xFFFF9500), 12.sp)
                    )
                    LiquidButton(
                        onClick = {
                            // 提权过程不可重入，避免并发刷入同一份提权包
                            if (!isFlashing) {
                                val zipPath = selectedFile
                                if (zipPath == null) {
                                    flashResult = "提权失败：未选择提权包"
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
                                            flashResult = "提权失败：执行异常，请确认 Shizuku/Root 权限后重试"
                                        } else {
                                            flashResult = if (result.exitCode == 0) {
                                                "提权成功！\n${result.output}"
                                            } else {
                                                "提权失败：${result.error}\n${result.output}"
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
                        BasicText(if (isFlashing) "正在提权..." else "开始提权", style = TextStyle(Color.White, 14.sp))
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
