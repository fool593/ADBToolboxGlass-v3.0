package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.perf.HuaweiPerf
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 华为（HarmonyOS / EMUI）专属深度性能加速页。
 *
 * 与通用"一键加速"页的区别：
 * - 顶部先如实展示识别到的机型 / 系统代际 / 权限状态，非华为机型会给出明确提示；
 * - 每条方法都能看到原理、真实命令原文、验证命令、恢复命令、风险与权限；
 * - 执行结果逐条回显 exitCode / 耗时 / 真实 stderr，并在底部汇总为可复制的原始日志；
 * - "验证本机可用性"会逐条跑验证命令，把结果标为 有效 / 无效 / 需权限 / 不适用。
 */
@Composable
fun HuaweiBoostScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit,
    onRunInTerminal: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val methods = remember { HuaweiPerf.methods }

    var info by remember { mutableStateOf<HuaweiPerf.HuaweiDeviceInfo?>(null) }
    var loading by remember { mutableStateOf(true) }

    val selected = remember { mutableStateListOf<String>() }
    val resultById = remember { mutableStateMapOf<String, HuaweiPerf.HuaweiRunResult>() }
    val verifyById = remember { mutableStateMapOf<String, HuaweiPerf.HuaweiVerifyResult>() }
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    var running by remember { mutableStateOf(false) }
    var verifying by remember { mutableStateOf(false) }
    var runningId by remember { mutableStateOf<String?>(null) }
    var progressIndex by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<HuaweiPerf.HuaweiRunReport?>(null) }
    var rollbackLog by remember { mutableStateOf<String?>(null) }

    var confirmMessage by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        info = HuaweiPerf.loadDeviceInfo()
        selected.clear()
        selected.addAll(HuaweiPerf.defaultSelection())
        loading = false
    }

    /** 是否包含高风险项，需要二次确认。 */
    fun needsConfirm(targets: List<HuaweiPerf.HuaweiMethod>): Boolean =
        targets.any { it.risk == HuaweiPerf.RISK_RISKY || it.risk == HuaweiPerf.RISK_CAUTION }

    fun runMethods(targets: List<HuaweiPerf.HuaweiMethod>) {
        if (targets.isEmpty() || running || verifying) return
        running = true
        report = null
        rollbackLog = null
        progressIndex = 0
        progressTotal = targets.size
        scope.launch {
            val result = HuaweiPerf.runAll(targets, info) { index, total, method ->
                progressIndex = index
                progressTotal = total
                runningId = method.id
            }
            withContext(Dispatchers.Main) {
                result.results.forEach { resultById[it.id] = it }
                report = result
                runningId = null
                running = false
            }
        }
    }

    fun runSingle(method: HuaweiPerf.HuaweiMethod) {
        if (running || verifying) return
        running = true
        runningId = method.id
        scope.launch {
            val result = HuaweiPerf.runOne(method, info)
            withContext(Dispatchers.Main) {
                resultById[method.id] = result
                runningId = null
                running = false
            }
        }
    }

    fun runRollback(method: HuaweiPerf.HuaweiMethod) {
        if (running || verifying) return
        running = true
        runningId = method.id
        scope.launch {
            val result = HuaweiPerf.runRollback(method)
            withContext(Dispatchers.Main) {
                if (result != null) {
                    resultById[method.id] = result
                    rollbackLog = buildString {
                        appendLine(AppStrings.get("hw_rollback_run") + ": " + AppStrings.get(method.titleKey))
                        appendLine("${'$'} " + result.command)
                        appendLine(result.stdout.trim().ifBlank { AppStrings.get("hw_no_output") })
                        if (result.stderr.isNotBlank()) appendLine("stderr: " + result.stderr.trim())
                    }.trim()
                    toast = AppStrings.get("hw_rollback_done")
                }
                runningId = null
                running = false
            }
        }
    }

    fun verifyAll() {
        if (running || verifying) return
        verifying = true
        scope.launch {
            val list = HuaweiPerf.verifyAll(methods, info)
            withContext(Dispatchers.Main) {
                verifyById.clear()
                list.forEach { verifyById[it.id] = it }
                verifying = false
            }
        }
    }

    /** 交给终端执行的脚本：优先当前勾选项，没有勾选就用全部方法。 */
    fun terminalScript(): String {
        val targets = methods.filter { it.id in selected }.ifEmpty { methods }
        return targets.joinToString("\n") { HuaweiPerf.resolveCommand(it, info) }
    }

    val rawLog = buildString {
        val ordered = ArrayList<HuaweiPerf.HuaweiRunResult>()
        report?.results?.let { ordered.addAll(it) }
        resultById.values.forEach { res ->
            if (!res.isRollback && ordered.none { it.id == res.id }) ordered.add(res)
        }
        ordered.forEach { res ->
            appendLine(
                "[" + AppStrings.get(res.titleKey) + "] exit=" + res.exitCode +
                        (if (res.succeeded) " OK" else if (res.skipped) " SKIP" else " FAIL")
            )
            appendLine("${'$'} " + res.command)
            if (res.stdout.isNotBlank()) appendLine(res.stdout.trim().take(600))
            if (res.stderr.isNotBlank()) appendLine("stderr: " + res.stderr.trim().take(600))
            appendLine()
        }
        rollbackLog?.let { appendLine(it) }
    }.trim()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(24.dp))

        // ---------------- 标题 ----------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    AppStrings.get("hw_boost_title"),
                    style = TextStyle(contentColor, 22.sp, FontWeight.Bold)
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    AppStrings.get("hw_boost_subtitle"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), 11.sp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- 设备卡 ----------------
        GlassCard(backdrop = backdrop, pageType = "default") {
            Column(Modifier.padding(16.dp)) {
                BasicText(
                    AppStrings.get("hw_device_card"),
                    style = TextStyle(contentColor, 15.sp, FontWeight.Bold)
                )
                Spacer(Modifier.height(8.dp))
                val dev = info
                if (loading || dev == null) {
                    BasicText(
                        AppStrings.get("loading"),
                        style = TextStyle(contentColor.copy(alpha = 0.6f), 13.sp)
                    )
                } else {
                    PerfInfoRow(
                        AppStrings.get("hw_label_brand_model"),
                        dev.brand + " · " + dev.model,
                        contentColor
                    )
                    PerfInfoRow(AppStrings.get("hw_label_system"), dev.osLabel, contentColor)
                    PerfInfoRow(
                        AppStrings.get("hw_label_emui"),
                        dev.emuiVersion.ifBlank { AppStrings.get("hw_not_provided") },
                        contentColor
                    )
                    PerfInfoRow(
                        AppStrings.get("hw_label_harmony"),
                        dev.harmonyVersion.ifBlank { AppStrings.get("hw_not_provided") },
                        contentColor
                    )
                    PerfInfoRow(AppStrings.get("hw_label_soc"), dev.soc, contentColor)
                    PerfInfoRow(
                        AppStrings.get("hw_label_sdk"),
                        "Android " + dev.androidVersion + " (SDK " + dev.sdk + ") · " + dev.device,
                        contentColor
                    )
                    PerfInfoRow(
                        AppStrings.get("hw_label_ram"),
                        hwFormat(
                            AppStrings.get("hw_ram_value"),
                            (dev.totalRamMb / 1024).toString() + " GB",
                            (dev.availRamMb / 1024).toString() + " GB"
                        ),
                        contentColor
                    )
                    PerfInfoRow(
                        AppStrings.get("hw_label_thermal"),
                        hwFormat(AppStrings.get("hw_thermal_value"), dev.thermalZoneCount),
                        contentColor
                    )

                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        AppStrings.get("hw_label_permission"),
                        style = TextStyle(contentColor.copy(alpha = 0.6f), 11.sp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HwPermBadge(AppStrings.get("hw_perm_shizuku"), dev.hasShizuku)
                        HwPermBadge(AppStrings.get("hw_perm_root"), dev.hasRoot)
                        HwPermBadge(AppStrings.get("hw_perm_dhizuku"), dev.isDhizukuActive)
                    }

                    if (!dev.hasAnyPrivilege) {
                        Spacer(Modifier.height(8.dp))
                        BasicText(
                            AppStrings.get("no_permission_hint"),
                            style = TextStyle(HwTheme.warn, 11.sp)
                        )
                    }
                }
            }
        }

        // ---------------- 非华为机型提示 ----------------
        val devForWarn = info
        if (devForWarn != null && !devForWarn.isHuawei) {
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(HwTheme.warn.copy(alpha = 0.16f))
                    .padding(12.dp)
            ) {
                BasicText(
                    AppStrings.get("hw_not_huawei_warning"),
                    style = TextStyle(HwTheme.warn, 12.sp, FontWeight.Medium)
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- 操作区 ----------------
        GlassCard(backdrop = backdrop, pageType = "default") {
            Column(Modifier.padding(16.dp)) {
                BasicText(
                    AppStrings.get("hw_actions"),
                    style = TextStyle(contentColor, 15.sp, FontWeight.Bold)
                )
                Spacer(Modifier.height(8.dp))
                BasicText(
                    when {
                        running && progressTotal > 0 ->
                            hwFormat(AppStrings.get("hw_progress"), progressIndex, progressTotal)
                        verifying -> AppStrings.get("hw_verifying")
                        else -> hwFormat(
                            AppStrings.get("hw_selected_count"),
                            selected.size,
                            methods.size
                        )
                    },
                    style = TextStyle(contentColor.copy(alpha = 0.7f), 12.sp, FontWeight.Medium)
                )
                runningId?.let { id ->
                    val current = methods.firstOrNull { it.id == id }
                    if (current != null) {
                        Spacer(Modifier.height(2.dp))
                        BasicText(
                            AppStrings.get("running_item") + ": " + AppStrings.get(current.titleKey),
                            style = TextStyle(HwTheme.accent, 11.sp)
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                LiquidButton(
                    onClick = {
                        val targets = methods.filter { it.id in HuaweiPerf.defaultSelection() }
                        if (needsConfirm(targets)) {
                            confirmMessage = AppStrings.get("hw_confirm_risky")
                            pendingAction = { runMethods(targets) }
                        } else {
                            runMethods(targets)
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    tint = if (running || verifying) HwTheme.muted else HwTheme.accent
                ) {
                    BasicText(
                        AppStrings.get("hw_run_deep"),
                        Modifier.padding(horizontal = 10.dp),
                        style = TextStyle(HwTheme.onAccent, 15.sp, FontWeight.Bold)
                    )
                }

                Spacer(Modifier.height(10.dp))
                PerfButtonRow(
                    backdrop = backdrop,
                    leftLabel = AppStrings.get("hw_run_safe"),
                    leftTint = HwTheme.ok,
                    onLeft = {
                        val targets = HuaweiPerf.safeOnly()
                        if (needsConfirm(targets)) {
                            confirmMessage = AppStrings.get("hw_confirm_risky")
                            pendingAction = { runMethods(targets) }
                        } else {
                            runMethods(targets)
                        }
                    },
                    rightLabel = AppStrings.get("hw_verify_device"),
                    rightTint = HwTheme.accentAlt,
                    onRight = { verifyAll() },
                    leftEnabled = !running && !verifying,
                    rightEnabled = !running && !verifying
                )

                Spacer(Modifier.height(10.dp))
                PerfButtonRow(
                    backdrop = backdrop,
                    leftLabel = AppStrings.get("select_all"),
                    leftTint = HwTheme.accentAlt,
                    onLeft = {
                        selected.clear()
                        selected.addAll(methods.map { it.id })
                    },
                    rightLabel = AppStrings.get("select_none"),
                    rightTint = HwTheme.muted,
                    onRight = { selected.clear() },
                    leftEnabled = !running,
                    rightEnabled = !running
                )

                Spacer(Modifier.height(10.dp))
                BasicText(
                    AppStrings.get("hw_doc_hint"),
                    style = TextStyle(contentColor.copy(alpha = 0.45f), 10.sp)
                )
            }
        }

        // ---------------- 执行报告 ----------------
        if (report != null || rollbackLog != null || resultById.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            GlassCard(backdrop = backdrop, pageType = "default") {
                Column(Modifier.padding(16.dp)) {
                    BasicText(
                        AppStrings.get("hw_report_title"),
                        style = TextStyle(contentColor, 15.sp, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))
                    report?.let { r ->
                        PerfInfoRow(
                            AppStrings.get("hw_label_success"),
                            r.successCount.toString(),
                            contentColor
                        )
                        PerfInfoRow(
                            AppStrings.get("hw_label_failed"),
                            r.failedCount.toString(),
                            contentColor
                        )
                        PerfInfoRow(
                            AppStrings.get("hw_label_skipped"),
                            r.skippedCount.toString(),
                            contentColor
                        )
                        PerfInfoRow(
                            AppStrings.get("hw_label_freed"),
                            hwFormat(AppStrings.get("hw_value_mb"), r.freedMemoryMb),
                            contentColor
                        )
                        PerfInfoRow(
                            AppStrings.get("hw_label_duration"),
                            hwFormat(AppStrings.get("hw_value_ms"), r.durationMs),
                            contentColor
                        )
                    }
                    if (report == null) {
                        PerfInfoRow(
                            AppStrings.get("hw_label_ran"),
                            resultById.size.toString(),
                            contentColor
                        )
                    }
                    if (rawLog.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        BasicText(
                            AppStrings.get("hw_raw_log"),
                            style = TextStyle(contentColor.copy(alpha = 0.6f), 11.sp)
                        )
                        Spacer(Modifier.height(4.dp))
                        PerfTextBox(text = rawLog, contentColor = contentColor)
                    }
                    Spacer(Modifier.height(10.dp))
                    LiquidButton(
                        onClick = { onRunInTerminal(terminalScript()) },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        tint = HwTheme.muted
                    ) {
                        BasicText(
                            AppStrings.get("hw_open_terminal"),
                            Modifier.padding(horizontal = 10.dp),
                            style = TextStyle(HwTheme.onAccent, 13.sp, FontWeight.Medium)
                        )
                    }
                }
            }
        }

        // ---------------- 方法列表 ----------------
        HuaweiPerf.groups.forEach { group ->
            val groupMethods = methods.filter { it.group == group.id }
            if (groupMethods.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                GlassCard(backdrop = backdrop, pageType = "default") {
                    Column(Modifier.padding(14.dp)) {
                        HwGroupHeader(
                            title = AppStrings.get(group.nameKey),
                            selectedCount = groupMethods.count { it.id in selected },
                            total = groupMethods.size,
                            expanded = expandedGroups[group.id] == true,
                            contentColor = contentColor,
                            backdrop = backdrop,
                            onToggle = { expandedGroups[group.id] = expandedGroups[group.id] != true }
                        )
                        if (expandedGroups[group.id] == true) {
                            Spacer(Modifier.height(10.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                groupMethods.forEach { method ->
                                    HwMethodCard(
                                        method = method,
                                        selected = method.id in selected,
                                        onToggle = {
                                            if (method.id in selected) selected.remove(method.id)
                                            else selected.add(method.id)
                                        },
                                        result = resultById[method.id],
                                        verify = verifyById[method.id],
                                        running = runningId == method.id,
                                        busy = running || verifying,
                                        backdrop = backdrop,
                                        contentColor = contentColor,
                                        onRun = { runSingle(method) },
                                        onRollback = {
                                            confirmMessage = AppStrings.get("hw_confirm_rollback")
                                            pendingAction = { runRollback(method) }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 底部导航栏遮挡留白
        Spacer(Modifier.height(80.dp))
    }

    // ---------------- 高风险确认 ----------------
    confirmMessage?.let { message ->
        PerfConfirmDialog(
            title = AppStrings.get("confirm_run"),
            message = message,
            contentColor = contentColor,
            onConfirm = {
                val action = pendingAction
                confirmMessage = null
                pendingAction = null
                action?.invoke()
            },
            onDismiss = {
                confirmMessage = null
                pendingAction = null
            }
        )
    }

    // ---------------- 临时提示 ----------------
    toast?.let { message ->
        LaunchedEffect(message) {
            delay(3000)
            toast = null
        }
        Dialog(
            onDismissRequest = { toast = null },
            properties = DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(HwTheme.deep.copy(alpha = 0.96f))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                BasicText(message, style = TextStyle(contentColor, 13.sp))
            }
        }
    }
}

/** 权限状态徽章。 */
@Composable
private fun HwPermBadge(label: String, ready: Boolean) {
    PerfBadge(
        label + " " + AppStrings.get(if (ready) "hw_perm_on" else "hw_perm_off"),
        if (ready) HwTheme.ok else HwTheme.muted
    )
}
