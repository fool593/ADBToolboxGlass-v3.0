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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.perf.BrandDatabase
import com.example.adbtoolbox.common.perf.InspectGroup
import com.example.adbtoolbox.common.perf.InspectReport
import com.example.adbtoolbox.common.perf.InspectResult
import com.example.adbtoolbox.common.perf.PerfChannels
import com.example.adbtoolbox.common.perf.PerfItem
import com.example.adbtoolbox.common.perf.PerfRunner
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v2.9 手机体检（"检查员"）。
 *
 * 作用：先检测机型/品牌/系统 → 再逐项验证"哪些指令在这台手机上真的能生效"，
 * 输出可执行项与结论，避免用户在一台设备上跑无效指令（即"普通 ADB 指令"的通病）。
 *
 * 明细结构（v2.9 用户要求）：每个体检分组内部再按**提权通道**分成两块，
 * `Root 专属` 与 `ADB / Shizuku 可用` 各占自己的区块，不再把两类混在同一段里；
 * 本机缺某条通道时，块标题上直接写明，块内条目也标明"因缺提权不可用"。
 */
@Composable
fun PhoneInspectorScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit,
    onOpenBoost: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var deviceInfo by remember { mutableStateOf<PerfRunner.PerfDeviceInfo?>(null) }
    var report by remember { mutableStateOf<InspectReport?>(null) }
    var running by remember { mutableStateOf(false) }
    var phaseIndex by remember { mutableIntStateOf(0) }
    var phaseTotal by remember { mutableIntStateOf(5) }
    var phaseLabel by remember { mutableStateOf("") }
    var toast by remember { mutableStateOf<String?>(null) }
    var confirmMessage by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 每条 fix 的执行结果：id -> 结果文本
    val fixResults = remember { mutableStateMapOf<String, String>() }

    fun runInspect() {
        if (running) return
        running = true
        report = null
        fixResults.clear()
        scope.launch {
            val info = deviceInfo ?: PerfRunner.loadDeviceInfo().also { deviceInfo = it }
            val r = PerfRunner.inspect(info) { phase, total, label ->
                phaseIndex = phase
                phaseTotal = total
                phaseLabel = label
            }
            withContext(Dispatchers.Main) {
                report = r
                running = false
            }
        }
    }

    LaunchedEffect(Unit) {
        deviceInfo = PerfRunner.loadDeviceInfo()
        runInspect()
    }

    fun requestConfirm(message: String, action: () -> Unit) {
        confirmMessage = message
        pendingAction = action
    }

    /** 执行一条修复命令，并把真实结果写回该条结果行。 */
    fun executeFix(resultId: String, command: String) {
        scope.launch {
            val outcome = withContext(Dispatchers.Default) {
                if (command == "__FIX_REFRESH__") {
                    val (ok, msg) = try { ADBTools.fixRefreshRateLock() } catch (e: Exception) {
                        false to "${e.javaClass.simpleName}: ${e.message}"
                    }
                    (if (ok) "OK " else "FAIL ") + msg
                } else {
                    try {
                        val r = ADBTools.execPerfCommand(command, timeout = 30)
                        val detail = listOf(r.output, r.error).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                        (if (r.exitCode == 0) "OK" else "FAIL(exit=${r.exitCode})") +
                                (if (detail.isNotBlank()) " · " + detail.take(200) else "")
                    } catch (e: Exception) {
                        "FAIL · ${e.javaClass.simpleName}: ${e.message}"
                    }
                }
            }
            fixResults[resultId] = outcome
            // 修复后重新体检，让分数与状态反映真实情况
            delay(600)
            if (command == "__FIX_REFRESH__") {
                deviceInfo = PerfRunner.loadDeviceInfo()
                runInspect()
            }
        }
    }

    val info = deviceInfo

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16f.dp)
    ) {
        Spacer(Modifier.height(24f.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                AppStrings.get("phone_inspector"),
                style = TextStyle(contentColor, 24f.sp, FontWeight.Bold)
            )
            Spacer(Modifier.weight(1f))
            LiquidButton(
                onClick = onBack,
                backdrop = backdrop,
                modifier = Modifier.height(36.dp),
                tint = Color(0xFF8E8E93)
            ) {
                BasicText(
                    AppStrings.get("back"),
                    Modifier.padding(horizontal = 8.dp),
                    style = TextStyle(Color.White, 12f.sp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            AppStrings.get("phone_inspector_hint"),
            style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
        )
        Spacer(Modifier.height(16.dp))

        // ---------------- 得分与机型 ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(18.dp)) {
                if (running) {
                    BasicText(
                        AppStrings.get("inspect_running") + " · $phaseIndex / $phaseTotal",
                        style = TextStyle(contentColor, 15f.sp, FontWeight.Medium)
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        phaseLabel,
                        style = TextStyle(Color(0xFF5AC8FA), 12f.sp)
                    )
                }
                report?.let { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            "${r.score}",
                            style = TextStyle(scoreColor(r.score), 40f.sp, FontWeight.Bold)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            BasicText(
                                AppStrings.get("inspect_score"),
                                style = TextStyle(contentColor, 14f.sp, FontWeight.Medium)
                            )
                            BasicText(
                                "${AppStrings.get(info?.brandKey ?: "unknown")} · ${info?.romName ?: ""}",
                                style = TextStyle(contentColor.copy(alpha = 0.6f), 11f.sp)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PerfBadge("${r.okCount}  ${AppStrings.get("available")}", Color(0xFF34C759))
                        PerfBadge("${r.warnCount}  ${AppStrings.get("risk_caution")}", Color(0xFFFF9500))
                        PerfBadge("${r.failCount}  ${AppStrings.get("unsupported")}", Color(0xFFFF3B30))
                    }
                }
                if (info != null) {
                    Spacer(Modifier.height(12.dp))
                    BasicText(
                        AppStrings.get("inspect_device_summary"),
                        style = TextStyle(contentColor, 14f.sp, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(6.dp))
                    info.deviceSummaryPreview().forEach { (k, v) ->
                        PerfInfoRow(k, v, contentColor)
                    }
                }
                Spacer(Modifier.height(12.dp))
                PerfButtonRow(
                    backdrop = backdrop,
                    leftLabel = AppStrings.get("re_inspect"),
                    leftTint = AppTheme.accent,
                    onLeft = { runInspect() },
                    rightLabel = AppStrings.get("inspect_fix_all"),
                    rightTint = Color(0xFFFF3B30),
                    onRight = {
                        // 缺提权的项不再提交：它们已经在各自区块里标明"因缺提权不可用"，
                        // 提交过去只会得到一次必然失败的执行结果。
                        val actionable = report?.groups?.flatMap { it.results }
                            ?.filter { it.fixCommand != null && missingPrivilegeNote(it, info) == null }
                            ?: emptyList()
                        if (actionable.isEmpty()) {
                            toast = AppStrings.get("inspect_all_fixed").substringBefore('%').trim()
                        } else {
                            requestConfirm(AppStrings.get("confirm_run_risky")) {
                                actionable.forEach { res ->
                                    res.fixCommand?.let { cmd -> executeFix(res.id, cmd) }
                                }
                                toast = AppStrings.get("inspect_all_fixed").substringBefore('%').trim()
                            }
                        }
                    },
                    leftEnabled = !running,
                    rightEnabled = !running && report != null
                )
            }
        }

        // ---------------- 体检分组明细：组内再按提权通道分成两块 ----------------
        report?.groups?.forEach { group ->
            Spacer(Modifier.height(14.dp))
            GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(18.dp)) {
                    BasicText(
                        AppStrings.get(group.nameKey),
                        style = TextStyle(contentColor, 16f.sp, FontWeight.Bold)
                    )
                    val byChannel = group.results.groupBy { channelOfResult(it) }
                    listOf(PerfChannels.ROOT, PerfChannels.ADB).forEach { channel ->
                        val channelResults = byChannel[channel].orEmpty()
                        // 空通道不画标题，避免出现"Root 专属 0 条"的噪音区块
                        if (channelResults.isEmpty()) return@forEach
                        val channelMissing = info != null && PerfChannels.missingOn(info, channel)
                        Spacer(Modifier.height(12.dp))
                        PerfChannelHeader(
                            title = AppStrings.get(PerfChannels.titleKey(channel)),
                            countText = String.format(AppStrings.get("perf_group_count"), channelResults.size),
                            barColor = if (channel == PerfChannels.ROOT) Color(0xFFFF3B30) else AppTheme.accent,
                            contentColor = contentColor,
                            warning = when {
                                !channelMissing -> null
                                channel == PerfChannels.ROOT -> AppStrings.get("perf_group_root_missing")
                                else -> AppStrings.get("perf_group_adb_missing")
                            },
                            note = when {
                                channel == PerfChannels.ROOT -> AppStrings.get("perf_group_root_note")
                                else -> AppStrings.get("perf_group_adb_note")
                            }
                        )
                        Spacer(Modifier.height(8.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            channelResults.forEach { res ->
                                // 缺提权时不再给出"执行修复"按钮：改成明确写出原因，避免点了没反应
                                val unavailableNote = missingPrivilegeNote(res, info)
                                val fixAction: (() -> Unit)? = res.fixCommand?.let { cmd ->
                                    {
                                        if (cmd == "__FIX_REFRESH__") {
                                            requestConfirm(AppStrings.get("refresh_rate_confirm")) {
                                                executeFix(res.id, cmd)
                                            }
                                        } else {
                                            executeFix(res.id, cmd)
                                        }
                                    }
                                }
                                InspectResultRow(
                                    title = announceTitle(res.id, group),
                                    status = res.status,
                                    detail = res.detail,
                                    fixResult = fixResults[res.id],
                                    contentColor = contentColor,
                                    backdrop = backdrop,
                                    unavailableNote = unavailableNote,
                                    onFix = if (unavailableNote != null) null else fixAction
                                )
                            }
                        }
                    }
                }
            }
        }

        if (report == null && !running) {
            Spacer(Modifier.height(14.dp))
            GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(18.dp)) {
                    BasicText(
                        AppStrings.get("no_permission_hint"),
                        style = TextStyle(Color(0xFFFF9500), 13f.sp)
                    )
                    Spacer(Modifier.height(10.dp))
                    LiquidButton(
                        onClick = { runInspect() },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        tint = AppTheme.accent
                    ) {
                        BasicText(
                            AppStrings.get("re_inspect"),
                            Modifier.padding(horizontal = 8.dp),
                            style = TextStyle(Color.White, 13f.sp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(90.dp))
    }

    confirmMessage?.let { msg ->
        PerfConfirmDialog(
            title = AppStrings.get("confirm_run"),
            message = msg,
            contentColor = contentColor,
            onConfirm = {
                val action = pendingAction
                confirmMessage = null
                pendingAction = null
                action?.invoke()
            },
            onDismiss = { confirmMessage = null; pendingAction = null }
        )
    }

    toast?.let { msg ->
        LaunchedEffect(msg) {
            delay(2500)
            toast = null
        }
        Dialog(
            onDismissRequest = { toast = null },
            properties = androidx.compose.ui.window.DialogProperties()
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xF21C1C1E))
                    .padding(horizontal = 18.dp, vertical = 12.dp)
            ) {
                BasicText(msg, style = TextStyle(contentColor, 13f.sp))
            }
        }
    }
}

/** 体检项的显示标题：优先用 PerfItem 的文案，识别不到的用「分组 + 序号」。 */
private fun announceTitle(id: String, group: InspectGroup): String {
    val item = PerfItemLookup.all[id]
    if (item != null) return AppStrings.get(item.nameKey)
    return when (id) {
        "dev_rom" -> AppStrings.get("android_version")
        "dev_cpu" -> AppStrings.get("cpu")
        "dev_ram" -> AppStrings.get("memory")
        "dev_storage" -> AppStrings.get("storage")
        "env_shizuku" -> AppStrings.get("shizuku_service")
        "env_root" -> AppStrings.get("root")
        "env_dhizuku" -> AppStrings.get("use_dhizuku")
        "ref_current" -> AppStrings.get("refresh_rate")
        "ref_peak" -> "peak_refresh_rate"
        "brand_none" -> AppStrings.get("brand")
        "brand_total" -> AppStrings.get("perf_cat_brand")
        else -> id
    }
}

/**
 * 体检项归入哪条提权通道：查得到 [PerfItem] 的按它的 `requiresPermission` 判定；
 * 设备信息 / 环境检测这类没有对应指令的项不需要提权，归入 ADB 通道（该通道本身就含
 * "无需提权"的项）。判定统一走 [PerfChannels]，页面不自己写规则。
 */
private fun channelOfResult(res: InspectResult): String {
    val item = res.perfItemId?.let { PerfItemLookup.all[it] } ?: return PerfChannels.ADB
    return PerfChannels.of(item)
}

/**
 * 该体检项是否"因缺提权不可用"。
 * 三个条件同时成立才算：本机缺这条通道、该项确实需要提权、执行层判定为 FAIL。
 * 只在满足时返回文案，其余情况返回 null（不夸大、不误标）。
 */
private fun missingPrivilegeNote(res: InspectResult, info: PerfRunner.PerfDeviceInfo?): String? {
    val current = info ?: return null
    if (res.status != "FAIL") return null
    val item = res.perfItemId?.let { PerfItemLookup.all[it] } ?: return null
    if (item.requiresPermission == "none") return null
    return if (PerfChannels.missingOn(current, PerfChannels.of(item))) {
        AppStrings.get("perf_unavailable_no_privilege")
    } else {
        null
    }
}

/** PerfItem 按 id 查表，供体检结果复用指令名称/说明。 */
private object PerfItemLookup {
    val all: Map<String, PerfItem> by lazy {
        val m = HashMap<String, PerfItem>()
        BrandDatabase.allBrandIds.forEach { b ->
            BrandDatabase.itemsFor(b, includeBrandSpecific = true).forEach { m[it.id] = it }
        }
        BrandDatabase.itemsFor("generic", includeBrandSpecific = false).forEach { m[it.id] = it }
        m
    }
}

@Composable
private fun InspectResultRow(
    title: String,
    status: String,
    detail: String,
    fixResult: String?,
    contentColor: Color,
    backdrop: Backdrop,
    /** 非空表示本机缺这条检测项需要的提权通道：显示原因，并且不再提供"执行修复"按钮。 */
    unavailableNote: String? = null,
    onFix: (() -> Unit)?
) {
    val (statusText, color) = when (status) {
        "OK" -> "OK" to Color(0xFF34C759)
        "WARN" -> AppStrings.get("risk_caution") to Color(0xFFFF9500)
        "FAIL" -> AppStrings.get("unsupported") to Color(0xFFFF3B30)
        else -> AppStrings.get("unknown") to Color(0xFF8E8E93)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(contentColor.copy(alpha = 0.05f))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
            Spacer(Modifier.width(8.dp))
            BasicText(title, Modifier.weight(1f), style = TextStyle(contentColor, 13f.sp, FontWeight.Medium))
            PerfBadge(statusText, color)
        }
        if (detail.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            BasicText(
                detail,
                style = TextStyle(contentColor.copy(alpha = 0.65f), 11f.sp)
            )
        }
        if (fixResult != null) {
            Spacer(Modifier.height(5.dp))
            PerfBadge(fixResult.take(80), if (fixResult.startsWith("OK")) Color(0xFF34C759) else Color(0xFFFF3B30))
        }
        if (unavailableNote != null) {
            Spacer(Modifier.height(5.dp))
            PerfBadge(unavailableNote, Color(0xFFFF9500))
        }
        if (onFix != null && fixResult == null && unavailableNote == null) {
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    // 原先是静态强调色块 + 无反馈点击：改成液态玻璃可点项，尺寸/内边距不变
                    // （liquidGlassItem 按 tint.alpha * 0.45f 着色，tint 上限 1f，故等效填充约 0.45）
                    .liquidGlassItem(
                        backdrop = backdrop,
                        corner = 10.dp,
                        tint = AppTheme.accent.copy(alpha = (0.9f / 0.45f).coerceAtMost(1f)),
                        onClick = onFix
                    )
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                BasicText(
                    AppStrings.get("execute_fix"),
                    style = TextStyle(Color.White, 12f.sp, FontWeight.Medium)
                )
            }
        }
    }
}

private fun scoreColor(score: Int): Color = when {
    score >= 80 -> Color(0xFF34C759)
    score >= 55 -> Color(0xFFFF9500)
    else -> Color(0xFFFF3B30)
}

/** 体检报告顶部的机型摘要（只保留最关键的几项，避免卡片过长）。 */
private fun PerfRunner.PerfDeviceInfo.deviceSummaryPreview(): List<Pair<String, String>> = listOf(
    AppStrings.get("brand") to brandRaw,
    AppStrings.get("model") to model,
    "ROM" to romName,
    AppStrings.get("android_version") to "$androidVersion (SDK $sdk)",
    AppStrings.get("cpu") to cpuModel,
    AppStrings.get("memory") to "${totalRamMb / 1024} GB / ${availRamMb / 1024} GB",
    AppStrings.get("refresh_rate") to
            "${if (currentRefreshRate > 1f) currentRefreshRate.toInt() else 0} / " +
            "${if (maxRefreshRate > 1f) maxRefreshRate.toInt() else 0} Hz"
)
