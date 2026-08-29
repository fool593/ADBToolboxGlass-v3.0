package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.*
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RootToolScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit,
    onNavigateToTerminal: (String) -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var deviceInfo by remember { mutableStateOf<DeviceRootInfo?>(null) }
    var currentStep by remember { mutableStateOf(RootStep.IDLE) }
    var stepMessage by remember { mutableStateOf("") }
    var selectedMethod by remember { mutableStateOf(RootMethod.MAGISK) }
    var isLoading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RootResult?>(null) }
    var isRooted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        deviceInfo = withContext(Dispatchers.Default) { RootToolManager.detectDevice() }
        isRooted = withContext(Dispatchers.Default) { RootToolManager.isRooted() }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
        Spacer(Modifier.height(16.dp))

        BasicText(AppStrings.get("root_tool"), style = TextStyle(contentColor, 24.sp, FontWeight.Bold))
        Spacer(Modifier.height(4.dp))
        BasicText(AppStrings.get("root_tool_hint"), style = TextStyle(contentColor.copy(alpha = 0.6f), 14.sp))
        Spacer(Modifier.height(20.dp))

        // 设备信息卡片
        GlassCard(backdrop = backdrop, pageType = "plugins") {
            Column(Modifier.padding(16.dp)) {
                BasicText(AppStrings.get("device_info"), style = TextStyle(contentColor, 16.sp, FontWeight.Bold))
                Spacer(Modifier.height(12.dp))

                if (isLoading) {
                    BasicText(AppStrings.get("detecting"), style = TextStyle(contentColor.copy(alpha = 0.5f), 14.sp))
                } else if (deviceInfo != null) {
                    val info = deviceInfo!!
                    RootInfoRow(AppStrings.get("brand"), info.brand, contentColor)
                    RootInfoRow(AppStrings.get("model"), info.model, contentColor)
                    RootInfoRow(AppStrings.get("device_codename"), info.device, contentColor)
                    RootInfoRow(AppStrings.get("android_version"), info.androidVersion, contentColor)
                    RootInfoRow(AppStrings.get("kernel_version"), info.kernelVersion, contentColor)
                    RootInfoRow(AppStrings.get("bl_status"), if (info.isBootloaderUnlocked) AppStrings.get("unlocked") else AppStrings.get("locked"), contentColor)
                    RootInfoRow(AppStrings.get("gki_device"), if (info.isGKI) AppStrings.get("yes") else AppStrings.get("no"), contentColor)
                    RootInfoRow(AppStrings.get("rooted"), if (isRooted) AppStrings.get("yes") else AppStrings.get("no"), contentColor)
                    if (info.tempRootSupported) {
                        val tempType = when (info.tempRootType) {
                            TempRootType.GHOSTLOCK -> AppStrings.get("ghostlock")
                            TempRootType.TEMPROOT -> AppStrings.get("temproot_xiaomi")
                            else -> AppStrings.get("supported")
                        }
                        RootInfoRow(AppStrings.get("temp_root"), tempType, contentColor)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // Root 方案选择
        if (deviceInfo != null && deviceInfo!!.supportedMethods.isNotEmpty()) {
            GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(16.dp)) {
                    BasicText(AppStrings.get("select_root_method"), style = TextStyle(contentColor, 16.sp, FontWeight.Bold))
                    Spacer(Modifier.height(12.dp))

                    deviceInfo!!.supportedMethods.forEach { method ->
                        val isSelected = selectedMethod == method
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .then(
                                    if (isSelected) Modifier
                                        .drawBackdrop(
                                            backdrop = backdrop,
                                            shape = { RoundedRectangle(12.dp) },
                                            effects = { blur(8.dp.toPx()) },
                                            onDrawSurface = { drawRect(Color(0xFFAF52DE).copy(alpha = 0.3f)) }
                                        )
                                    else Modifier
                                )
                                .clickable { selectedMethod = method }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .drawBackdrop(
                                        backdrop = backdrop,
                                        shape = { RoundedRectangle(10.dp) },
                                        effects = { blur(4.dp.toPx()) },
                                        onDrawSurface = {
                                            drawRect(if (isSelected) Color(0xFFAF52DE) else Color(0xFF4A4A4A))
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    BasicText("✓", style = TextStyle(Color.White, 12.sp, FontWeight.Bold))
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                BasicText(
                                    when (method) {
                                        RootMethod.MAGISK -> "Magisk"
                                        RootMethod.KERNELSU -> "KernelSU"
                                        RootMethod.TEMP_ROOT -> AppStrings.get("temp_root_vuln")
                                        RootMethod.UNKNOWN -> AppStrings.get("unknown")
                                    },
                                    style = TextStyle(contentColor, 14.sp, FontWeight.Medium)
                                )
                                BasicText(
                                    when (method) {
                                        RootMethod.MAGISK -> AppStrings.get("magisk_desc")
                                        RootMethod.KERNELSU -> AppStrings.get("kernelsu_desc")
                                        RootMethod.TEMP_ROOT -> AppStrings.get("temproot_desc")
                                        RootMethod.UNKNOWN -> ""
                                    },
                                    style = TextStyle(contentColor.copy(alpha = 0.5f), 12.sp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // 进度显示
        if (currentStep != RootStep.IDLE && currentStep != RootStep.DONE && currentStep != RootStep.ERROR) {
            GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(16.dp)) {
                    BasicText(AppStrings.get("exec_progress"), style = TextStyle(contentColor, 16.sp, FontWeight.Bold))
                    Spacer(Modifier.height(12.dp))
                    BasicText(
                        when (currentStep) {
                            RootStep.CHECKING_DEVICE -> AppStrings.get("checking_device")
                            RootStep.EXTRACTING_BOOT -> AppStrings.get("extracting_boot")
                            RootStep.PATCHING_BOOT -> AppStrings.get("patching_boot")
                            RootStep.FLASHING_BOOT -> AppStrings.get("flashing_boot")
                            RootStep.REBOOTING -> AppStrings.get("rebooting")
                            else -> stepMessage
                        },
                        style = TextStyle(Color(0xFFFF9500), 14.sp)
                    )
                    if (stepMessage.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        BasicText(stepMessage, style = TextStyle(contentColor.copy(alpha = 0.7f), 12.sp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // 结果显示
        result?.let { r ->
            GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(16.dp)) {
                    BasicText(
                        if (r.success) AppStrings.get("op_success") else AppStrings.get("op_failed"),
                        style = TextStyle(if (r.success) Color(0xFF34C759) else Color(0xFFFF3B30), 16.sp, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(r.message, style = TextStyle(contentColor.copy(alpha = 0.8f), 13.sp))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // 操作按钮
        if (deviceInfo != null && deviceInfo!!.supportedMethods.isNotEmpty()) {
            LiquidButton(
                onClick = {
                    if (isLoading) return@LiquidButton
                    scope.launch {
                        isLoading = true
                        result = null
                        try {
                            when (selectedMethod) {
                                RootMethod.MAGISK -> {
                                    currentStep = RootStep.EXTRACTING_BOOT
                                    stepMessage = AppStrings.get("msg_extract_boot")
                                    val extractResult = withContext(Dispatchers.Default) { RootToolManager.extractBootImage() }
                                    if (!extractResult.success) {
                                        result = extractResult
                                        currentStep = RootStep.ERROR
                                        return@launch
                                    }

                                    currentStep = RootStep.PATCHING_BOOT
                                    stepMessage = AppStrings.get("msg_patch_magisk")
                                    val patchResult = withContext(Dispatchers.Default) { RootToolManager.patchWithMagisk("/sdcard/boot.img") }
                                    if (!patchResult.success) {
                                        result = patchResult
                                        currentStep = RootStep.ERROR
                                        return@launch
                                    }

                                    currentStep = RootStep.FLASHING_BOOT
                                    stepMessage = AppStrings.get("msg_flash_boot")
                                    val flashResult = withContext(Dispatchers.Default) { RootToolManager.flashBootImage("/sdcard/boot.img") }
                                    result = flashResult
                                    currentStep = if (flashResult.success) RootStep.DONE else RootStep.ERROR
                                }
                                RootMethod.KERNELSU -> {
                                    currentStep = RootStep.EXTRACTING_BOOT
                                    val extractResult = withContext(Dispatchers.Default) { RootToolManager.extractBootImage() }
                                    if (!extractResult.success) {
                                        result = extractResult
                                        currentStep = RootStep.ERROR
                                        return@launch
                                    }

                                    currentStep = RootStep.PATCHING_BOOT
                                    val patchResult = withContext(Dispatchers.Default) { RootToolManager.patchWithKernelSU("/sdcard/boot.img") }
                                    if (!patchResult.success) {
                                        result = patchResult
                                        currentStep = RootStep.ERROR
                                        return@launch
                                    }

                                    currentStep = RootStep.FLASHING_BOOT
                                    val flashResult = withContext(Dispatchers.Default) { RootToolManager.flashBootImage("/sdcard/boot.img") }
                                    result = flashResult
                                    currentStep = if (flashResult.success) RootStep.DONE else RootStep.ERROR
                                }
                                RootMethod.TEMP_ROOT -> {
                                    currentStep = RootStep.EXTRACTING_BOOT
                                    stepMessage = AppStrings.get("msg_temp_root")
                                    val tempResult = withContext(Dispatchers.Default) { RootToolManager.tempRoot() }
                                    result = tempResult
                                    currentStep = if (tempResult.success) RootStep.DONE else RootStep.ERROR

                                    // 临时 Root 成功后，自动检测 KSU
                                    if (tempResult.success) {
                                        stepMessage = AppStrings.get("msg_check_ksu")
                                        val hasKSU = withContext(Dispatchers.Default) { RootToolManager.isKernelSUInstalled() }
                                        if (!hasKSU) {
                                            // 没有 KSU，跳转到终端页面，生成 KSU 官网
                                            stepMessage = AppStrings.get("msg_no_ksu")
                                            val terminalCommand = buildString {
                                                appendLine("echo '========================================'")
                                                appendLine("echo '${AppStrings.get("ksu_terminal_banner")}'")
                                                appendLine("echo '========================================'")
                                                appendLine("echo ''")
                                                appendLine("echo '${AppStrings.get("ksu_terminal_website")}$KSU_OFFICIAL_URL'")
                                                appendLine("echo 'GitHub: $KSU_GITHUB_URL'")
                                                appendLine("echo ''")
                                                appendLine("echo '${AppStrings.get("ksu_terminal_copy")}'")
                                                appendLine("echo '${AppStrings.get("ksu_terminal_open")}'")
                                                appendLine("echo 'am start -a android.intent.action.VIEW -d $KSU_OFFICIAL_URL'")
                                                appendLine("echo ''")
                                                appendLine("am start -a android.intent.action.VIEW -d $KSU_OFFICIAL_URL 2>/dev/null")
                                                appendLine("echo '${AppStrings.get("ksu_terminal_tried")}'")
                                            }
                                            onNavigateToTerminal(terminalCommand)
                                        }
                                    }
                                }
                                RootMethod.UNKNOWN -> {}
                            }
                        } catch (e: Exception) {
                            result = RootResult(false, AppStrings.get("msg_error_prefix") + (e.message ?: ""))
                            currentStep = RootStep.ERROR
                        } finally {
                            isLoading = false
                        }
                    }
                },
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().height(50.dp),
                tint = Color(0xFFAF52DE)
            ) {
                BasicText(if (isLoading) AppStrings.get("executing") else AppStrings.get("start_root"), style = TextStyle(Color.White, 16.sp, FontWeight.Bold))
            }
            Spacer(Modifier.height(12.dp))

            LiquidButton(
                onClick = {
                    scope.launch {
                        isLoading = true
                        deviceInfo = withContext(Dispatchers.Default) { RootToolManager.detectDevice() }
                        isLoading = false
                        result = null
                        currentStep = RootStep.IDLE
                    }
                },
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                tint = Color(0xFF8E8E93)
            ) {
                BasicText(AppStrings.get("redetect"), style = TextStyle(Color.White, 14.sp))
            }
        } else if (!isLoading) {
            GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(16.dp)) {
                    BasicText(AppStrings.get("not_supported"), style = TextStyle(Color(0xFFFF9500), 16.sp, FontWeight.Bold))
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        AppStrings.get("unsupported_msg"),
                        style = TextStyle(contentColor.copy(alpha = 0.7f), 13.sp)
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // 风险提示
        GlassCard(backdrop = backdrop, pageType = "plugins") {
            Column(Modifier.padding(16.dp)) {
                BasicText(AppStrings.get("risk_warning"), style = TextStyle(Color(0xFFFF3B30), 14.sp, FontWeight.Bold))
                Spacer(Modifier.height(8.dp))
                BasicText(
                    AppStrings.get("risk_msg"),
                    style = TextStyle(contentColor.copy(alpha = 0.7f), 12.sp)
                )
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun RootInfoRow(label: String, value: String, contentColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(label, style = TextStyle(contentColor.copy(alpha = 0.6f), 13.sp))
        BasicText(value.ifEmpty { AppStrings.get("unknown") }, style = TextStyle(contentColor, 13.sp, FontWeight.Medium))
    }
}
