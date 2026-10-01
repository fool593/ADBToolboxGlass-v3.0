package com.example.adbtoolbox.common.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.RootModuleData
import com.example.adbtoolbox.common.RootModuleError
import com.example.adbtoolbox.common.RootModuleInstallResult
import com.example.adbtoolbox.common.RootModuleManager
import com.example.adbtoolbox.common.copyToClipboard
import com.example.adbtoolbox.common.theme.AppMotion
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Root 模块管理页（Magisk / KernelSU）。
 *
 * 与 [ADBModuleScreen] 的分工：
 * - [ADBModuleScreen] 只管"把一个 zip 刷进设备"（走 ADBTools.installModuleViaRoot / ViaADB）；
 * - 本页管"设备上已经装了哪些模块、它们的开关与 action.sh"（走 [RootModuleManager]）。
 *
 * 这里所有按钮背后都是真实的 [RootModuleManager] 调用：
 * - 启用 = 删除 `/data/adb/modules/<id>/disable` 标记文件；
 * - 停用 = 创建该标记文件（模块本身不删除，重启后不加载）；
 * - 卸载 = 创建 `/data/adb/modules/<id>/remove` 标记文件（Magisk/KernelSU 约定：**下次重启时**才真正删除）；
 * - 执行 = `cd <moduleDir> && MODDIR=<moduleDir> sh action.sh`，原样回显 exit code / stdout / stderr。
 *
 * 因此界面文案必须写明"需要重启"，不能让用户以为点完就立即生效。
 */
private data class RootModuleCapability(
    val rootChecked: Boolean = false,
    val rootUsable: Boolean = false,
    val magisk: Boolean = false
)

/** 单个模块上正在进行的操作。 */
private enum class RootOps(val busyKey: String, val doneKey: String) {
    ENABLE("module_op_enable", "module_enable_done"),
    DISABLE("module_op_disable", "module_disable_done"),
    UNINSTALL("module_op_uninstall", "module_uninstall_done"),
    ACTION("module_op_action", "module_action_done");

    val isUninstall: Boolean get() = this == UNINSTALL // 只有卸载需要二次确认
}

@Composable
fun RootModuleScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit,
    onInstallModule: () -> Unit,
    selectedFilePath: String?
) {
    val scope = rememberCoroutineScope()

    var capability by remember { mutableStateOf(RootModuleCapability()) }
    var modules by remember { mutableStateOf<List<RootModuleData>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(RootModuleError.None) }
    var loadErrorDetail by remember { mutableStateOf("") }

    var installing by remember { mutableStateOf(false) }
    var installResult by remember { mutableStateOf<RootModuleInstallResult?>(null) }

    var pendingUninstall by remember { mutableStateOf<RootModuleData?>(null) }
    var busyModuleId by remember { mutableStateOf<String?>(null) }
    val opByModule = remember { mutableStateMapOf<String, RootOps>() }
    val resultByModule = remember { mutableStateMapOf<String, String>() }
    var actionOutput by remember { mutableStateOf("") }
    var actionModuleId by remember { mutableStateOf<String?>(null) }
    var clipboardFeedback by remember { mutableStateOf<String?>(null) }
    var clipboardFailed by remember { mutableStateOf(false) }

    /** 重新检测权限 + 重新读取模块列表。所有 shell 调用都在 Dispatchers.Default。 */
    suspend fun refresh() {
        loading = true
        val list = withContext(Dispatchers.Default) { RootModuleManager.getInstalledModules() }
        // getInstalledModules() 只能返回 List，失败原因通过 RootModuleData.lastError 带出
        val error = RootModuleData.lastError
        val detail = RootModuleData.lastErrorDetail
        val rootOk = withContext(Dispatchers.Default) { RootModuleManager.canUseRoot() }
        val magiskOk = withContext(Dispatchers.Default) { ADBTools.hasMagisk() }
        modules = list
        loadError = error
        loadErrorDetail = detail
        capability = RootModuleCapability(rootChecked = true, rootUsable = rootOk, magisk = magiskOk)
        loading = false
    }

    // 进入页面即检测并列出，不做"点了才知道没权限"的假按钮
    LaunchedEffect(Unit) { refresh() }

    // 选择文件后自动安装：selectedFilePath 变化即触发，装完刷新列表
    LaunchedEffect(selectedFilePath) {
        val path = selectedFilePath
        if (path.isNullOrBlank() || installing) return@LaunchedEffect
        installing = true
        installResult = null
        val result = withContext(Dispatchers.Default) {
            try {
                RootModuleManager.installModule(path)
            } catch (e: Exception) {
                RootModuleInstallResult(false, "E_EXCEPTION\n${e.message.orEmpty()}")
            }
        }
        installResult = result
        installing = false
        if (result.success) refresh()
    }

    fun launchOp(module: RootModuleData, op: RootOps) {
        if (installing || busyModuleId != null) return
        pendingUninstall = null
        if (!capability.rootUsable) {
            resultByModule[module.id] = AppStrings.get("module_root_required_op")
            return
        }
        busyModuleId = module.id
        opByModule[module.id] = op
        resultByModule.remove(module.id)
        scope.launch {
            val outcome: String = withContext(Dispatchers.Default) {
                try {
                    when (op) {
                        RootOps.ENABLE -> if (RootModuleManager.enableModule(module.id)) {
                            AppStrings.get(op.doneKey)
                        } else {
                            AppStrings.get("module_op_failed")
                        }
                        RootOps.DISABLE -> if (RootModuleManager.disableModule(module.id)) {
                            AppStrings.get(op.doneKey)
                        } else {
                            AppStrings.get("module_op_failed")
                        }
                        RootOps.UNINSTALL -> if (RootModuleManager.uninstallModule(module.id)) {
                            AppStrings.get(op.doneKey)
                        } else {
                            AppStrings.get("module_op_failed")
                        }
                        RootOps.ACTION -> {
                            val raw = RootModuleManager.runAction(module.id)
                            if (raw.startsWith("E_")) {
                                // 处理器返回稳定错误码，这里翻成当前语言，并保留原始 detail
                                val lines = raw.lines()
                                val code = lines.firstOrNull().orEmpty()
                                val detail = lines.drop(1).joinToString("\n").trim()
                                buildString {
                                    append(moduleActionErrorMessage(code))
                                    if (detail.isNotBlank()) {
                                        appendLine()
                                        append(detail)
                                    }
                                }
                            } else {
                                raw
                            }
                        }
                    }
                } catch (e: Exception) {
                    "${AppStrings.get("operation_failed")}: ${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                }
            }
            if (op == RootOps.ACTION) {
                actionModuleId = module.id
                actionOutput = outcome
            } else {
                resultByModule[module.id] = outcome
            }
            opByModule.remove(module.id)
            busyModuleId = null
            // 启用/停用/卸载都会改变 disable / remove 标记，重新读一次列表保证界面与实际一致
            if (op != RootOps.ACTION) refresh()
        }
    }

    fun copyPath(path: String) {
        val ok = copyToClipboard(path)
        clipboardFailed = !ok
        clipboardFeedback = if (ok) AppStrings.get("path_copied") else AppStrings.get("path_copy_failed")
    }

    // 复制结果 3 秒后自动消失（时长沿用全局动效规范）
    LaunchedEffect(clipboardFeedback) {
        if (clipboardFeedback != null) {
            kotlinx.coroutines.delay(AppMotion.slow.toLong() * 8)
            clipboardFeedback = null
        }
    }

    val anyOpRunning = installing || busyModuleId != null

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(24.dp))

        // ---------------- 标题 ----------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    AppStrings.get("root_module"),
                    style = TextStyle(contentColor, 22.sp, FontWeight.Bold)
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    AppStrings.get("rm_screen_subtitle"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), 11.sp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // ---------------- 顶部状态条 ----------------
        GlassCard(backdrop = backdrop, pageType = "plugins") {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PerfBadge(
                        AppStrings.get("root_short") + " " + statusWord(capability.rootChecked, capability.rootUsable),
                        when {
                            !capability.rootChecked -> Color(0xFF8E8E93)
                            capability.rootUsable -> Color(0xFF34C759)
                            else -> Color(0xFFFF3B30)
                        }
                    )
                    Spacer(Modifier.width(6.dp))
                    PerfBadge(
                        AppStrings.get("magisk_short") + " " + statusWord(capability.rootChecked, capability.magisk),
                        when {
                            !capability.rootChecked -> Color(0xFF8E8E93)
                            capability.magisk -> Color(0xFF34C759)
                            else -> AppTheme.accentAlt
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                BasicText(
                    if (!capability.rootChecked) {
                        AppStrings.get("loading")
                    } else if (!capability.rootUsable) {
                        AppStrings.get("need_root")
                    } else if (capability.magisk) {
                        AppStrings.get("rm_root_ok_hint")
                    } else {
                        AppStrings.get("rm_no_magisk_hint")
                    },
                    style = TextStyle(
                        if (capability.rootChecked && !capability.rootUsable) Color(0xFFFF9500) else contentColor.copy(alpha = 0.75f),
                        11.sp
                    )
                )
                Spacer(Modifier.height(10.dp))
                PerfButtonRow(
                    backdrop = backdrop,
                    leftLabel = AppStrings.get("retry_detect"),
                    leftTint = AppTheme.accentAlt,
                    onLeft = { scope.launch { refresh() } },
                    rightLabel = AppStrings.get("install_module_zip"),
                    rightTint = AppTheme.accent,
                    onRight = {
                        // 拉起系统文件选择：真正的选择与路径回填由 MainActivity 的 filePicker 负责
                        if (!installing) onInstallModule()
                    },
                    leftEnabled = !loading && !anyOpRunning,
                    rightEnabled = !installing
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    AppStrings.get("rm_install_hint"),
                    style = TextStyle(contentColor.copy(alpha = 0.45f), 10.sp)
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    if (selectedFilePath.isNullOrBlank()) {
                        AppStrings.get("no_file_selected")
                    } else {
                        hwFormat(AppStrings.get("rm_selected_file"), selectedFilePath)
                    },
                    style = TextStyle(contentColor.copy(alpha = 0.7f), 10.sp, fontFamily = FontFamily.Monospace),
                    maxLines = 2
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        // ---------------- 安装结果 ----------------
        if (installing) {
            GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle(AppStrings.get("module_installing"), contentColor)
                    BasicText(
                        AppStrings.get("rm_install_running"),
                        style = TextStyle(contentColor.copy(alpha = 0.7f), 11.sp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        installResult?.let { result ->
            GlassCard(backdrop = backdrop, pageType = "plugins") {
                Column(Modifier.padding(16.dp)) {
                    SectionTitle(
                        if (result.success) AppStrings.get("module_install_ok") else AppStrings.get("module_install_failed"),
                        contentColor
                    )
                    BasicText(
                        localizeMessage(result.message),
                        style = TextStyle(
                            if (result.success) Color(0xFF34C759) else Color(0xFFFF3B30),
                            11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    if (result.success) {
                        Spacer(Modifier.height(4.dp))
                        BasicText(
                            AppStrings.get("rm_reboot_hint"),
                            style = TextStyle(contentColor.copy(alpha = 0.6f), 10.sp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        // ---------------- 模块数量 ----------------
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                hwFormat(AppStrings.get("rm_installed_count"), modules.size),
                style = TextStyle(contentColor, 13.sp, FontWeight.Medium)
            )
            Spacer(Modifier.weight(1f))
            if (loading) {
                BasicText(
                    AppStrings.get("loading"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), 11.sp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        // ---------------- 列表 ----------------
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (loading && modules.isEmpty()) {
                item(key = "loading") {
                    GlassCard(backdrop = backdrop, pageType = "plugins") {
                        Column(Modifier.padding(16.dp)) {
                            BasicText(
                                AppStrings.get("rm_loading_modules"),
                                style = TextStyle(contentColor.copy(alpha = 0.75f), 12.sp)
                            )
                        }
                    }
                }
            } else if (loadError != RootModuleError.None && modules.isEmpty()) {
                item(key = "error") {
                    GlassCard(backdrop = backdrop, pageType = "plugins") {
                        Column(Modifier.padding(16.dp)) {
                            SectionTitle(AppStrings.get("rm_load_failed"), contentColor)
                            BasicText(
                                if (loadError == RootModuleError.PermissionDenied) {
                                    AppStrings.get("rm_err_permission")
                                } else {
                                    AppStrings.get("rm_err_command")
                                },
                                style = TextStyle(Color(0xFFFF3B30), 11.sp)
                            )
                            if (loadErrorDetail.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                PerfTextBox(text = loadErrorDetail, contentColor = contentColor)
                            }
                            Spacer(Modifier.height(10.dp))
                            LiquidButton(
                                onClick = { scope.launch { refresh() } },
                                backdrop = backdrop,
                                modifier = Modifier.fillMaxWidth().height(44.dp),
                                tint = if (anyOpRunning) Color(0xFF8E8E93) else AppTheme.accent
                            ) {
                                BasicText(
                                    AppStrings.get("retry_detect"),
                                    Modifier.padding(horizontal = 8.dp),
                                    style = TextStyle(AppTheme.onAccent, 13.sp, FontWeight.Medium)
                                )
                            }
                        }
                    }
                }
            } else if (modules.isEmpty()) {
                item(key = "empty") {
                    GlassCard(backdrop = backdrop, pageType = "plugins") {
                        Column(Modifier.padding(16.dp)) {
                            SectionTitle(AppStrings.get("rm_no_modules"), contentColor)
                            BasicText(
                                AppStrings.get("rm_no_modules_hint"),
                                style = TextStyle(contentColor.copy(alpha = 0.7f), 11.sp)
                            )
                        }
                    }
                }
            } else {
                items(items = modules, key = { it.id }) { module ->
                    // 列表项进场：透明度 + 轻微上移，时长取自 AppMotion
                    var appeared by remember(module.id) { mutableStateOf(false) }
                    LaunchedEffect(module.id) { appeared = true }
                    AnimatedVisibility(
                        visible = appeared,
                        enter = fadeIn(animationSpec = tween(AppMotion.normal, easing = AppMotion.enter)) +
                            slideInVertically(animationSpec = tween(AppMotion.normal, easing = AppMotion.enter)) { it / 12 }
                    ) {
                        RootModuleCard(
                            module = module,
                            backdrop = backdrop,
                            contentColor = contentColor,
                            busy = busyModuleId == module.id,
                            op = opByModule[module.id],
                            otherBusy = anyOpRunning && busyModuleId != module.id,
                            rootUsable = capability.rootUsable,
                            result = resultByModule[module.id],
                            onToggleEnabled = { launchOp(it, if (it.isEnabled) RootOps.DISABLE else RootOps.ENABLE) },
                            onUninstall = { pendingUninstall = it },
                            onRunAction = { launchOp(it, RootOps.ACTION) },
                            onCopyPath = { copyPath(it.moduleDir) }
                        )
                    }
                }
            }
        }
    }

    // ---------------- 卸载二次确认 ----------------
    pendingUninstall?.let { module ->
        PerfConfirmDialog(
            title = AppStrings.get("module_uninstall"),
            message = hwFormat(AppStrings.get("module_uninstall_confirm"), module.name, module.moduleDir),
            confirmLabel = AppStrings.get("module_uninstall"),
            cancelLabel = AppStrings.get("cancel"),
            contentColor = contentColor,
            onConfirm = { launchOp(module, RootOps.UNINSTALL) },
            onDismiss = { pendingUninstall = null }
        )
    }

    // ---------------- action.sh 原始输出 ----------------
    if (actionOutput.isNotBlank()) {
        RootActionOutputDialog(
            title = hwFormat(
                AppStrings.get("module_action_output_title"),
                actionModuleId.orEmpty()
            ),
            output = actionOutput,
            backdrop = backdrop,
            contentColor = contentColor,
            onDismiss = {
                actionOutput = ""
                actionModuleId = null
            }
        )
    }

    // ---------------- 复制结果提示 ----------------
    clipboardFeedback?.let { message ->
        Box(
            // 底部提示条。原来写的是 matchParentSize()，那是 BoxScope 的成员，
            // 而这里的外层是 Column（根布局），所以编译不过；这里用 fillMaxWidth()
            // 让提示条自己按内容撑高、贴在列表下方（列表有 weight(1f)，因此就在屏幕底部）。
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (clipboardFailed) Color(0xE6FF3B30) else AppTheme.deep.copy(alpha = 0.96f)
                    )
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                BasicText(
                    message,
                    style = TextStyle(
                        if (clipboardFailed) Color.White else contentColor,
                        12.sp
                    )
                )
            }
        }
    }
}

/** 单个模块卡片：状态、元信息与四个真实操作。 */
@Composable
private fun RootModuleCard(
    module: RootModuleData,
    backdrop: Backdrop,
    contentColor: Color,
    busy: Boolean,
    op: RootOps?,
    otherBusy: Boolean,
    rootUsable: Boolean,
    result: String?,
    onToggleEnabled: (RootModuleData) -> Unit,
    onUninstall: (RootModuleData) -> Unit,
    onRunAction: (RootModuleData) -> Unit,
    onCopyPath: (RootModuleData) -> Unit
) {
    // 其他模块有操作进行时整体降透明度，避免用户连点造成并发写 /data/adb
    val alpha = if (otherBusy) 0.55f else 1f
    val animatedAlpha by animateFloatAsState(targetValue = alpha, animationSpec = tween(AppMotion.fast))

    GlassCard(backdrop = backdrop, pageType = "plugins") {
        Column(Modifier.alpha(animatedAlpha).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    BasicText(
                        module.name,
                        style = TextStyle(contentColor, 15.sp, FontWeight.Bold),
                        maxLines = 1
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        module.id,
                        style = TextStyle(contentColor.copy(alpha = 0.55f), 10.sp, fontFamily = FontFamily.Monospace),
                        maxLines = 1
                    )
                }
                PerfBadge(
                    if (module.isEnabled) AppStrings.get("enabled") else AppStrings.get("disabled"),
                    if (module.isEnabled) Color(0xFF34C759) else Color(0xFFFF9500)
                )
                if (module.hasAction) {
                    Spacer(Modifier.width(6.dp))
                    PerfBadge(AppStrings.get("module_has_action"), AppTheme.accent)
                }
            }

            Spacer(Modifier.height(10.dp))
            PerfInfoRow(AppStrings.get("version"), module.version + " (" + module.versionCode + ")", contentColor)
            PerfInfoRow(AppStrings.get("author"), module.author, contentColor)
            PerfInfoRow(
                AppStrings.get("size"),
                module.size.ifBlank { AppStrings.get("unknown") },
                contentColor
            )
            PerfInfoRow(
                AppStrings.get("module_status"),
                if (module.isEnabled) AppStrings.get("module_status_enabled") else AppStrings.get("module_status_disabled"),
                contentColor
            )
            if (module.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                BasicText(
                    module.description,
                    style = TextStyle(contentColor.copy(alpha = 0.8f), 11.sp)
                )
            }
            Spacer(Modifier.height(6.dp))
            BasicText(
                AppStrings.get("module_dir"),
                style = TextStyle(contentColor.copy(alpha = 0.55f), 10.sp)
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                module.moduleDir,
                style = TextStyle(contentColor.copy(alpha = 0.85f), 10.sp, fontFamily = FontFamily.Monospace),
                maxLines = 2
            )

            if (!rootUsable) {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    AppStrings.get("need_root"),
                    style = TextStyle(Color(0xFFFF9500), 10.sp)
                )
            }

            Spacer(Modifier.height(10.dp))
            PerfButtonRow(
                backdrop = backdrop,
                leftLabel = if (module.isEnabled) AppStrings.get("disable") else AppStrings.get("enable"),
                leftTint = if (module.isEnabled) Color(0xFF8E8E93) else AppTheme.accentAlt,
                onLeft = { onToggleEnabled(module) },
                rightLabel = AppStrings.get("uninstall"),
                rightTint = Color(0xFFFF3B30),
                onRight = { onUninstall(module) },
                leftEnabled = !otherBusy,
                rightEnabled = !otherBusy
            )
            Spacer(Modifier.height(8.dp))
            PerfButtonRow(
                backdrop = backdrop,
                leftLabel = if (busy && op == RootOps.ACTION) {
                    AppStrings.get("module_op_action")
                } else {
                    AppStrings.get("module_run_action")
                },
                leftTint = if (module.hasAction) AppTheme.accent else Color(0xFF8E8E93),
                onLeft = { if (module.hasAction) onRunAction(module) },
                rightLabel = AppStrings.get("copy_path"),
                rightTint = AppTheme.accentAlt,
                onRight = { onCopyPath(module) },
                leftEnabled = !otherBusy && module.hasAction,
                rightEnabled = !otherBusy
            )

            // 执行中 / 执行结果：真实回显，不伪造
            if (busy && op != null) {
                Spacer(Modifier.height(8.dp))
                BasicText(
                    AppStrings.get(op.busyKey),
                    style = TextStyle(
                        if (op.isUninstall) Color(0xFFFF3B30) else AppTheme.accent,
                        11.sp
                    )
                )
            }
            result?.let { message ->
                val failed = message == AppStrings.get("module_op_failed")
                Spacer(Modifier.height(8.dp))
                BasicText(
                    message,
                    style = TextStyle(
                        if (failed) Color(0xFFFF3B30) else Color(0xFF34C759),
                        11.sp
                    )
                )
                if (message == AppStrings.get("module_uninstall_done") ||
                    message == AppStrings.get("module_enable_done") ||
                    message == AppStrings.get("module_disable_done")
                ) {
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        AppStrings.get("rm_reboot_hint"),
                        style = TextStyle(contentColor.copy(alpha = 0.6f), 10.sp)
                    )
                }
            }
        }
    }
}

/** action.sh 输出弹窗：等宽字体 + 可滚动，原样展示 exit code 与 stdout/stderr。 */
@Composable
private fun RootActionOutputDialog(
    title: String,
    output: String,
    backdrop: Backdrop,
    contentColor: Color,
    onDismiss: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF21C1C1E))
                .padding(20.dp)
        ) {
            BasicText(title, style = TextStyle(contentColor, 15.sp, FontWeight.Bold))
            Spacer(Modifier.height(10.dp))
            PerfTextBox(text = output, contentColor = contentColor)
            Spacer(Modifier.height(12.dp))
            LiquidButton(
                onClick = onDismiss,
                backdrop = backdrop,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                tint = AppTheme.accent
            ) {
                BasicText(
                    AppStrings.get("close"),
                    Modifier.padding(horizontal = 8.dp),
                    style = TextStyle(AppTheme.onAccent, 13.sp, FontWeight.Medium)
                )
            }
        }
    }
}

/** 已检测 / 未检测的简短状态词，避免各处理散写。 */
private fun statusWord(checked: Boolean, ok: Boolean): String = when {
    !checked -> AppStrings.get("unknown")
    ok -> AppStrings.get("status_available")
    else -> AppStrings.get("status_unavailable")
}

/** 把处理器返回的稳定错误码翻成当前语言。 */
private fun moduleActionErrorMessage(code: String): String = when (code) {
    "E_NO_ACTION" -> AppStrings.get("module_err_no_action")
    "E_ACTION_DISABLED" -> AppStrings.get("module_err_action_disabled")
    "E_ACTION_GUARD_FAILED" -> AppStrings.get("module_err_action_guard")
    "E_ACTION_EXCEPTION" -> AppStrings.get("module_err_action_exception")
    else -> AppStrings.get("operation_failed")
}

/** 安装结果文案：首行可能是稳定错误码 / 成功码，逐行翻译后拼接。 */
private fun localizeMessage(raw: String): String = raw.lines().joinToString("\n") { line ->
    val code = line.trim()
    when (code) {
        "E_ROOT_REQUIRED" -> AppStrings.get("module_err_root_required")
        "E_FILE_NOT_FOUND" -> AppStrings.get("module_err_file_not_found")
        "E_NOT_ZIP" -> AppStrings.get("module_err_not_zip")
        "E_NO_INSTALLER_AND_UNZIP_FAILED" -> AppStrings.get("module_err_no_installer")
        "E_NO_MODULE_PROP" -> AppStrings.get("module_err_no_module_prop")
        "E_NO_MODULE_ID" -> AppStrings.get("module_err_no_module_id")
        "E_COPY_FAILED" -> AppStrings.get("module_err_copy_failed")
        "E_EXCEPTION" -> AppStrings.get("module_err_exception")
        "E_OK_MAGISK" -> AppStrings.get("module_ok_magisk")
        "E_OK_KSU" -> AppStrings.get("module_ok_ksu")
        "E_OK_MANUAL" -> AppStrings.get("module_ok_manual")
        else -> line
    }
}
