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
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.ADBDestination
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.perf.BrandDatabase
import com.example.adbtoolbox.common.perf.PerfItem
import com.example.adbtoolbox.common.perf.PerfRunReport
import com.example.adbtoolbox.common.perf.PerfRunResult
import com.example.adbtoolbox.common.perf.PerfRunner
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v2.8 品牌自适应一键性能加速。
 *
 * 与"普通 ADB 指令"的区别：
 * - 先识别本机品牌/ROM，再匹配该品牌真实存在的 settings/cmd 接口；
 * - 每条指令都能展开看到原文，执行结果逐条回显 stdout/stderr，失败不静默；
 * - "一键关闭后台"是单独的真实实现（ActivityManager + am force-stop + trim-caches）。
 */
@Composable
fun PerformanceBoostScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit,
    onOpenInspector: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var deviceInfo by remember { mutableStateOf<PerfRunner.PerfDeviceInfo?>(null) }
    var loading by remember { mutableStateOf(true) }

    // 选中状态：用 id 的集合保存，切换品牌后依然可用
    val selected = remember { mutableStateListOf<String>() }
    var includeBrandSpecific by remember { mutableStateOf(true) }

    // 运行状态
    var running by remember { mutableStateOf(false) }
    var progressIndex by remember { mutableIntStateOf(0) }
    var progressTotal by remember { mutableIntStateOf(0) }
    var runningItemId by remember { mutableStateOf<String?>(null) }
    var report by remember { mutableStateOf<PerfRunReport?>(null) }
    val resultById = remember { mutableStateMapOf<String, PerfRunResult>() }

    // 确认对话框（高风险操作）
    var confirmMessage by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 临时提示（关闭后台等操作的反馈）
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        val info = PerfRunner.loadDeviceInfo()
        deviceInfo = info
        // 默认勾选：按 PerfItem.defaultSelected，且默认包含品牌专属项
        val defaults = BrandDatabase.itemsFor(info.brandId, includeBrandSpecific = true)
            .filter { it.defaultSelected }
            .map { it.id }
        selected.clear()
        selected.addAll(defaults)
        loading = false
    }

    val info = deviceInfo
    val items: List<PerfItem> = remember(info?.brandId, includeBrandSpecific, loading) {
        if (info == null) emptyList()
        else BrandDatabase.itemsFor(info.brandId, includeBrandSpecific)
    }
    val grouped = remember(items) {
        BrandDatabase.categories.mapNotNull { cat ->
            val ofCat = items.filter { it.category == cat.id }
            if (ofCat.isEmpty()) null else cat to ofCat
        }
    }

    fun requestConfirm(message: String, action: () -> Unit) {
        confirmMessage = message
        pendingAction = action
    }

    /** 真正执行一组指令：逐条跑、逐条记录，最后生成报告。 */
    fun runItems(toRun: List<PerfItem>) {
        if (toRun.isEmpty() || running) return
        val currentInfo = info ?: return
        running = true
        report = null
        resultById.clear()
        progressIndex = 0
        progressTotal = toRun.size
        scope.launch {
            val r = PerfRunner.run(toRun, currentInfo) { index, total, item ->
                progressIndex = index
                progressTotal = total
                runningItemId = item.id
            }
            withContext(Dispatchers.Main) {
                r.results.forEach { resultById[it.id] = it }
                report = r
                runningItemId = null
                running = false
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

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                AppStrings.get("performance_boost"),
                style = TextStyle(contentColor, 24f.sp, FontWeight.Bold)
            )
            Spacer(Modifier.weight(1f))
            LiquidButton(
                onClick = onBack,
                backdrop = backdrop,
                modifier = Modifier.height(36f.dp),
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
            AppStrings.get("performance_boost_hint"),
            style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
        )
        Spacer(Modifier.height(16.dp))

        // ---------------- 设备与品牌识别 ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(18.dp)) {
                if (loading || info == null) {
                    BasicText(
                        AppStrings.get("loading"),
                        style = TextStyle(contentColor.copy(alpha = 0.6f), 14f.sp)
                    )
                } else {
                    BasicText(
                        AppStrings.get(info.brandKey),
                        style = TextStyle(contentColor, 18f.sp, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        info.romName,
                        style = TextStyle(Color(0xFF5AC8FA), 12f.sp)
                    )
                    Spacer(Modifier.height(10.dp))
                    PerfInfoRow(AppStrings.get("model"), info.model, contentColor)
                    PerfInfoRow(
                        AppStrings.get("android_version"),
                        "${info.androidVersion} (SDK ${info.sdk})",
                        contentColor
                    )
                    PerfInfoRow(
                        AppStrings.get("cpu"),
                        "${info.cpuModel} · ${info.socVendor}",
                        contentColor
                    )
                    PerfInfoRow(
                        AppStrings.get("memory"),
                        "${info.totalRamMb / 1024} GB / ${AppStrings.get("available")} ${info.availRamMb / 1024} GB",
                        contentColor
                    )
                    PerfInfoRow(
                        AppStrings.get("refresh_rate"),
                        "${if (info.currentRefreshRate > 1f) info.currentRefreshRate.toInt() else 0} Hz / " +
                                "${if (info.maxRefreshRate > 1f) info.maxRefreshRate.toInt() else 0} Hz",
                        contentColor
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PerfBadge(
                            AppStrings.get("generic_count").trim(),
                            Color(0xFF0088FF)
                        )
                        if (info.hasBrandSpecific) {
                            PerfBadge(
                                AppStrings.get(info.brandKey),
                                Color(0xFFFF3B30)
                            )
                        }
                        if (!info.hasAnyPrivilege) {
                            PerfBadge(AppStrings.get("no_permission_hint").take(14), Color(0xFFFF9500))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PerfButtonRow(
                        backdrop = backdrop,
                        leftLabel = AppStrings.get("phone_inspector"),
                        leftTint = Color(0xFFAF52DE),
                        onLeft = onOpenInspector,
                        rightLabel = AppStrings.get("grant_shizuku"),
                        rightTint = Color(0xFF34C759),
                        onRight = {
                            scope.launch {
                                withContext(Dispatchers.Default) { ADBTools.requestShizukuPermission() }
                                toast = AppStrings.get("shizuku_permission_denied")
                                // 重新读取权限状态，让界面反映真实结果
                                deviceInfo = PerfRunner.loadDeviceInfo()
                            }
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ---------------- 一键操作区 ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(18.dp)) {
                val runningText = if (running) {
                    "${AppStrings.get("run_progress")}: $progressIndex / $progressTotal"
                } else {
                    "${AppStrings.get("selected_count")}: ${selected.size}"
                }
                BasicText(runningText, style = TextStyle(contentColor, 13f.sp, FontWeight.Medium))
                Spacer(Modifier.height(10.dp))

                LiquidButton(
                    onClick = {
                        val toRun = items.filter { it.id in selected }
                        if (toRun.any { it.risk == "risky" }) {
                            requestConfirm(AppStrings.get("confirm_run_risky")) { runItems(toRun) }
                        } else {
                            runItems(toRun)
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    tint = if (running) Color(0xFF8E8E93) else Color(0xFF0088FF)
                ) {
                    BasicText(
                        if (running) AppStrings.get("running_item") else AppStrings.get("one_tap_boost"),
                        Modifier.padding(horizontal = 10.dp),
                        style = TextStyle(Color.White, 15f.sp, FontWeight.Bold)
                    )
                }

                Spacer(Modifier.height(10.dp))
                PerfButtonRow(
                    backdrop = backdrop,
                    leftLabel = AppStrings.get("select_all"),
                    leftTint = Color(0xFF5AC8FA),
                    onLeft = {
                        selected.clear()
                        selected.addAll(items.map { it.id })
                    },
                    rightLabel = AppStrings.get("select_none"),
                    rightTint = Color(0xFF8E8E93),
                    onRight = { selected.clear() },
                    leftEnabled = !running,
                    rightEnabled = !running
                )

                Spacer(Modifier.height(10.dp))
                PerfButtonRow(
                    backdrop = backdrop,
                    leftLabel = AppStrings.get("run_selected"),
                    leftTint = Color(0xFF34C759),
                    onLeft = {
                        val toRun = items.filter { it.id in selected }
                        if (toRun.any { it.risk == "risky" }) {
                            requestConfirm(AppStrings.get("confirm_run_risky")) { runItems(toRun) }
                        } else {
                            runItems(toRun)
                        }
                    },
                    rightLabel = AppStrings.get("run_all"),
                    rightTint = Color(0xFFFF3B30),
                    onRight = {
                        requestConfirm(AppStrings.get("confirm_run_risky")) { runItems(items) }
                    },
                    leftEnabled = !running && selected.isNotEmpty(),
                    rightEnabled = !running
                )

                Spacer(Modifier.height(10.dp))
                // 「一键关闭后台」：独立真实实现，不依赖上面的勾选项
                LiquidButton(
                    onClick = {
                        requestConfirm(AppStrings.get("confirm_run")) {
                            scope.launch {
                                running = true
                                progressTotal = 1
                                progressIndex = 1
                                runningItemId = "generic_kill_bg"
                                val killed = withContext(Dispatchers.Default) {
                                    try { ADBTools.killBackgroundProcesses() } catch (e: Exception) { emptyList() }
                                }
                                runningItemId = null
                                running = false
                                toast = "${AppStrings.get("boost_done_title")}: " +
                                        String.format(AppStrings.get("boost_success_count"), killed.size)
                            }
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    tint = if (running) Color(0xFF8E8E93) else Color(0xFFFF9500)
                ) {
                    BasicText(
                        AppStrings.get("perf_item_kill_bg"),
                        Modifier.padding(horizontal = 10.dp),
                        style = TextStyle(Color.White, 14f.sp, FontWeight.Medium)
                    )
                }

                Spacer(Modifier.height(12.dp))
                PerfToggleRow(
                    label = AppStrings.get("perf_cat_brand"),
                    checked = includeBrandSpecific,
                    onCheckedChange = { includeBrandSpecific = it },
                    backdrop = backdrop,
                    contentColor = contentColor
                )
            }
        }

        // ---------------- 执行结果 ----------------
        report?.let { r ->
            Spacer(Modifier.height(14.dp))
            GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(18.dp)) {
                    BasicText(
                        AppStrings.get("boost_done_title"),
                        style = TextStyle(contentColor, 17f.sp, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(10.dp))
                    PerfInfoRow(
                        AppStrings.get("boost_success_count").substringBefore('%').trim().ifBlank { "OK" },
                        "${r.successCount}",
                        contentColor
                    )
                    PerfInfoRow(
                        AppStrings.get("boost_failed_count").substringBefore('%').trim().ifBlank { "FAIL" },
                        "${r.failedCount}",
                        contentColor
                    )
                    PerfInfoRow(
                        AppStrings.get("freed_memory").substringBefore('%').trim().ifBlank { "Memory" },
                        "${r.freedMemoryMb} MB",
                        contentColor
                    )
                    PerfInfoRow(
                        AppStrings.get("used_time").substringBefore('%').trim().ifBlank { "Time" },
                        "${r.durationMs} ms",
                        contentColor
                    )
                    Spacer(Modifier.height(10.dp))
                    PerfTextBox(
                        text = r.results.joinToString("\n\n") { res ->
                            buildString {
                                appendLine("[${AppStrings.get(res.nameKey)}] exit=${res.exitCode} " +
                                        if (res.succeeded) "OK" else "FAIL")
                                appendLine("$ ${res.command}")
                                if (res.stdout.isNotBlank()) appendLine(res.stdout.trim().take(600))
                                if (res.stderr.isNotBlank()) appendLine("stderr: " + res.stderr.trim().take(600))
                            }
                        },
                        contentColor = contentColor
                    )
                }
            }
        }

        // ---------------- 指令清单 ----------------
        grouped.forEach { (cat, catItems) ->
            Spacer(Modifier.height(14.dp))
            GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(cat.color))
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicText(
                            AppStrings.get(cat.nameKey),
                            style = TextStyle(contentColor, 16f.sp, FontWeight.Bold)
                        )
                        Spacer(Modifier.weight(1f))
                        BasicText(
                            "${catItems.count { it.id in selected }} / ${catItems.size}",
                            style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        catItems.forEach { item ->
                            PerfItemRow(
                                item = item,
                                selected = item.id in selected,
                                onToggle = {
                                    if (item.id in selected) selected.remove(item.id)
                                    else selected.add(item.id)
                                },
                                contentColor = contentColor,
                                backdrop = backdrop,
                                running = runningItemId == item.id,
                                result = resultById[item.id]
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(90.dp))
    }

    // 高风险操作确认
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
            onDismiss = {
                confirmMessage = null
                pendingAction = null
            }
        )
    }

    // 临时提示，3 秒后自动消失
    toast?.let { msg ->
        LaunchedEffect(msg) {
            kotlinx.coroutines.delay(3000)
            toast = null
        }
        Dialog(
            onDismissRequest = { toast = null },
            properties = androidx.compose.ui.window.DialogProperties(
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
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
