package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.background
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
    var availableMethods by remember { mutableStateOf<List<RootMethodInfo>>(emptyList()) }
    var recommendedMethod by remember { mutableStateOf<RootMethodInfo?>(null) }
    var executingMethodId by remember { mutableStateOf<String?>(null) }
    var methodResult by remember { mutableStateOf<RootResult?>(null) }
    var expandedMethodId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        deviceInfo = withContext(Dispatchers.Default) { RootToolManager.detectDevice() }
        isRooted = withContext(Dispatchers.Default) { RootToolManager.isRooted() }
        availableMethods = withContext(Dispatchers.Default) { RootToolManager.getAvailableRootMethods() }
        recommendedMethod = withContext(Dispatchers.Default) { RootToolManager.detectRootMethod() }
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

        // 自动检测到的可用 Root 方法（基于网上公开漏洞与教程）
        if (availableMethods.isNotEmpty()) {
            GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(16.dp)) {
                    BasicText("可用 Root 方法 (${availableMethods.size})", style = TextStyle(contentColor, 16.sp, FontWeight.Bold))
                    Spacer(Modifier.height(4.dp))
                    if (recommendedMethod != null) {
                        BasicText("推荐: ${recommendedMethod!!.name}", style = TextStyle(Color(0xFFAF52DE), 13.sp, FontWeight.Bold))
                    }
                    Spacer(Modifier.height(12.dp))

                    availableMethods.forEach { method ->
                        val isExecuting = executingMethodId == method.id
                        val isExpanded = expandedMethodId == method.id
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 左边：方法名称和简介（点击展开详情）
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .clickable {
                                            expandedMethodId = if (isExpanded) null else method.id
                                        }
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        BasicText(
                                            if (isExpanded) "▼ " else "▶ ",
                                            style = TextStyle(Color(0xFFAF52DE), 10.sp, FontWeight.Bold)
                                        )
                                        BasicText(method.name, style = TextStyle(contentColor, 14.sp, FontWeight.Bold))
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    BasicText(
                                        "${method.principle.take(50)}...",
                                        style = TextStyle(contentColor.copy(alpha = 0.5f), 11.sp)
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    BasicText(
                                        "风险:${method.riskLevel} | ${if (method.requiresComputer) "需电脑" else "手机端"} | ${if (method.requiresKSU) "需KSU" else "无需KSU"}",
                                        style = TextStyle(contentColor.copy(alpha = 0.4f), 10.sp)
                                    )
                                }
                                // 右边：紫色玻璃执行按钮
                                LiquidButton(
                                    onClick = {
                                        scope.launch {
                                            executingMethodId = method.id
                                            methodResult = null
                                            // 判断是否为需要脚本的自动执行方法
                                            val isAutoMethod = method.autoExecute ||
                                                method.id == "redmi_note11tpro_misaka_temp_root" ||
                                                method.id == "xiaomi_mtk_ldpreload" ||
                                                method.id == "vivo_mtk_ldpreload" ||
                                                method.id == "dirtypipe_cve_2022_0847"
                                            // 需要电脑的方法：跳转到终端显示完整命令列表，方便复制
                                            if (method.requiresComputer && !isAutoMethod) {
                                                val terminalCmd = withContext(Dispatchers.Default) { RootToolManager.buildComputerMethodCommand(method.id) }
                                                methodResult = RootResult(true, "✓ 已生成电脑端操作命令\n正在跳转到终端显示...\n\n你可以在终端中直接复制命令到电脑执行。", method.id)
                                                executingMethodId = null
                                                onNavigateToTerminal(terminalCmd)
                                            } else if (isAutoMethod) {
                                                // 第一步：检测本地是否已有脚本
                                                val scriptPath = withContext(Dispatchers.Default) { RootToolManager.findTempRootScript() }
                                                if (scriptPath != null) {
                                                    val targetPath = if (method.scriptFileName.isNotBlank()) {
                                                        withContext(Dispatchers.Default) { RootToolManager.moveScriptToTempDir(method.scriptFileName) }
                                                    } else { scriptPath }
                                                    if (targetPath != null) {
                                                        val terminalCmd = withContext(Dispatchers.Default) { RootToolManager.buildOneClickRootCommand(targetPath, method.id) }
                                                        methodResult = RootResult(true, "✓ 检测到脚本: $scriptPath\n✓ 已转移到: $targetPath\n✓ 正在跳转到终端执行...", method.id)
                                                        executingMethodId = null
                                                        onNavigateToTerminal(terminalCmd)
                                                    } else {
                                                        methodResult = RootResult(false, "✗ 脚本转移失败！\n检测到脚本: $scriptPath\n但无法转移到 /data/local/tmp/\n可能原因：目录不存在/无写入权限/存储空间不足\n请手动将脚本复制到 /data/local/tmp/ 后重试", method.id)
                                                        executingMethodId = null
                                                    }
                                                } else {
                                                    if (method.downloadUrl.isNotBlank()) {
                                                        methodResult = RootResult(false, "✗ 未检测到脚本！\n正在跳转到浏览器下载...\n下载地址: ${method.downloadUrl}\n下载完成后放到 /sdcard/Download/ 目录，重新点击执行按钮。", method.id)
                                                        executingMethodId = null
                                                        val opened = withContext(Dispatchers.Default) { RootToolManager.openUrl(method.downloadUrl) }
                                                        if (!opened) {
                                                            methodResult = RootResult(false, "✗ 未检测到脚本！\n无法自动打开浏览器，请手动访问：\n${method.downloadUrl}\n下载后放到 /sdcard/Download/ 目录重试。", method.id)
                                                        }
                                                    } else {
                                                        methodResult = RootResult(false, "✗ 未检测到脚本！\n搜索目录：/sdcard/Download、/data/local/tmp\n请下载对应提权脚本放到 /sdcard/Download/ 目录。\n支持关键词：root、temp、misaka、exploit、提权、ksu、dirtypipe", method.id)
                                                        executingMethodId = null
                                                    }
                                                }
                                            } else {
                                                methodResult = withContext(Dispatchers.Default) { RootToolManager.executeRootMethod(method.id) }
                                                executingMethodId = null
                                            }
                                        }
                                    },
                                    backdrop = backdrop,
                                    modifier = Modifier.height(36.dp),
                                    tint = Color(0xFFAF52DE)
                                ) {
                                    if (isExecuting) {
                                        BasicText("...", Modifier.padding(horizontal = 16.dp), style = TextStyle(Color.White, 12.sp, FontWeight.Bold))
                                    } else {
                                        BasicText("执行", Modifier.padding(horizontal = 16.dp), style = TextStyle(Color.White, 12.sp, FontWeight.Bold))
                                    }
                                }
                            }
                            // 展开详情区域
                            if (isExpanded) {
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFAF52DE).copy(alpha = 0.08f))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        BasicText("【原理】", style = TextStyle(Color(0xFFAF52DE), 12.sp, FontWeight.Bold))
                                        Spacer(Modifier.height(2.dp))
                                        BasicText(method.principle, style = TextStyle(contentColor.copy(alpha = 0.8f), 11.sp))
                                        Spacer(Modifier.height(8.dp))
                                        BasicText("【支持机型】", style = TextStyle(Color(0xFFAF52DE), 12.sp, FontWeight.Bold))
                                        Spacer(Modifier.height(2.dp))
                                        BasicText(method.supportedDevices, style = TextStyle(contentColor.copy(alpha = 0.8f), 11.sp))
                                        Spacer(Modifier.height(8.dp))
                                        BasicText("【风险等级】${method.riskLevel} | ${if (method.requiresComputer) "需要电脑配合" else "可直接在手机执行"} | ${if (method.requiresKSU) "需要先安装KSU" else "无需KSU"}", style = TextStyle(contentColor.copy(alpha = 0.7f), 11.sp))
                                        Spacer(Modifier.height(8.dp))
                                        BasicText("【操作步骤】", style = TextStyle(Color(0xFFAF52DE), 12.sp, FontWeight.Bold))
                                        Spacer(Modifier.height(2.dp))
                                        BasicText(method.description, style = TextStyle(contentColor.copy(alpha = 0.8f), 11.sp))
                                        if (method.downloadUrl.isNotBlank()) {
                                            Spacer(Modifier.height(6.dp))
                                            BasicText("【下载地址】${method.downloadUrl}", style = TextStyle(Color(0xFF0088FF), 10.sp))
                                        }
                                    }
                                }
                            }
                            // 执行结果显示
                            if (methodResult != null && executingMethodId == null && methodResult!!.step == method.id) {
                                Spacer(Modifier.height(6.dp))
                                BasicText(
                                    if (methodResult!!.success) "✓ ${methodResult!!.message.take(300)}" else "✗ ${methodResult!!.message.take(300)}",
                                    style = TextStyle(if (methodResult!!.success) Color(0xFF34C759) else Color(0xFFFF3B30), 11.sp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

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
                                    // 先检测 KSU 包名，检测到就直接用 KSU 获取 root，不再跳官网
                                    val hasKSU = withContext(Dispatchers.Default) { RootToolManager.isKernelSUInstalled() }
                                    val tempResult = if (hasKSU) {
                                        // 已检测到 KSU，直接通过 KSU 获取 root 权限
                                        withContext(Dispatchers.Default) { RootToolManager.rootWithKSU() }
                                    } else {
                                        // 没有检测到 KSU，执行临时 root
                                        withContext(Dispatchers.Default) { RootToolManager.tempRoot() }
                                    }
                                    result = tempResult
                                    currentStep = if (tempResult.success) RootStep.DONE else RootStep.ERROR
                                    // 不再自动跳转到 KSU 官网
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
