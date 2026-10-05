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
import com.example.adbtoolbox.common.perf.PerfCategory
import com.example.adbtoolbox.common.perf.PerfChannels
import com.example.adbtoolbox.common.perf.PerfItem
import com.example.adbtoolbox.common.perf.PerfRunReport
import com.example.adbtoolbox.common.perf.PerfRunResult
import com.example.adbtoolbox.common.perf.PerfRunner
import com.example.adbtoolbox.common.perf.UniversalTuning
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * v2.9 品牌自适应一键性能加速。
 *
 * 与"普通 ADB 指令"的区别：
 * - 先识别本机品牌/ROM，再匹配该品牌真实存在的 settings/cmd 接口；
 * - 每条指令都能展开看到原文，执行结果逐条回显 stdout/stderr，失败不静默；
 * - "一键关闭后台"是单独的真实实现（ActivityManager + am force-stop + trim-caches）。
 *
 * 列表结构（v2.9 用户要求）：
 * - 按**提权通道**分成两个区块：`Root 专属` 与 `ADB / Shizuku 可用`，各自标题、条数、
 *   全选/清空按钮，绝不混在一段里（判定统一走 [PerfChannels]）；
 * - 每个通道区块内部再按原有的 [BrandDatabase.categories] 分类分段，既有分类与条目一条都没删。
 *
 * [brandFilter] 为 null 表示"全机型"（通用项 + 本机品牌专属项，即原有行为）；
 * 传具体品牌 id（[BrandDatabase.BRAND_ALL] 空串表示全机型通用）时只显示该品牌的
 * 专属项 + 通用项，并在顶部显示"当前分类"与"看全部机型"的切换入口。
 */
@Composable
fun PerformanceBoostScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit,
    onOpenInspector: () -> Unit,
    brandFilter: String? = null
) {
    val scope = rememberCoroutineScope()
    var deviceInfo by remember { mutableStateOf<PerfRunner.PerfDeviceInfo?>(null) }
    var loading by remember { mutableStateOf(true) }

    // 选中状态：用 id 的集合保存，切换品牌后依然可用
    val selected = remember { mutableStateListOf<String>() }
    var includeBrandSpecific by remember { mutableStateOf(true) }
    // 机型分类：由外部 brandFilter 初始化，页内"看全部机型"可切回全机型（null）
    var localFilter by remember(brandFilter) { mutableStateOf(brandFilter) }

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
        // 默认勾选：按 PerfItem.defaultSelected，并且只勾本机真正可执行的项
        // （权限 / SDK / SoC / 品牌四条都由数据层判定），避免默认选中一堆跑不了的项。
        val all = BrandDatabase.itemsFor(info.brandId, includeBrandSpecific = true)
        val runnableIds = UniversalTuning.applicability(info, all)
            .filter { it.itemId != null && it.state == UniversalTuning.STATE_APPLICABLE }
            .mapNotNull { it.itemId }
            .toSet()
        val defaults = all.filter { it.defaultSelected && it.id in runnableIds }.map { it.id }
        selected.clear()
        selected.addAll(defaults)
        loading = false
    }

    val info = deviceInfo
    val filter = localFilter
    val items: List<PerfItem> = remember(info?.brandId, filter, includeBrandSpecific, loading) {
        if (info == null) emptyList()
        else if (filter != null) BrandDatabase.itemsFor(filter, includeBrandSpecific = true)
        else BrandDatabase.itemsFor(info.brandId, includeBrandSpecific)
    }

    // 逐项适用性结论：权限、SDK、SoC、品牌四条都由数据层给，界面不自己猜。
    val noteById: Map<String, UniversalTuning.ApplicabilityNote> = remember(info, items) {
        val current = info
        if (current == null) emptyMap()
        else UniversalTuning.applicability(current, items)
            .filter { it.itemId != null }
            .associateBy { it.itemId ?: "" }
    }

    /** 本机执行不了这条指令的原因；null = 可执行。 */
    fun unavailableNoteOf(item: PerfItem): String? {
        val note = noteById[item.id] ?: return null
        return when (note.state) {
            UniversalTuning.STATE_APPLICABLE -> null
            UniversalTuning.STATE_NEEDS_ROOT, UniversalTuning.STATE_NEEDS_SHIZUKU ->
                AppStrings.get("perf_unavailable_no_privilege") + " · " + AppStrings.get(note.reasonKey)
            else -> AppStrings.get(note.reasonKey)
        }
    }

    // 真正可执行的项（用于默认值、全选与执行，避免选中了却什么都不发生）
    val runnableItems = remember(items, noteById) {
        items.filter { unavailableNoteOf(it) == null }
    }

    // 提权状态变化（例如 Shizuku 掉线后重新读取设备信息）后，把已经跑不了的项从选中集合移除，
    // 避免出现"勾着却标明因缺提权不可用"的矛盾状态。
    LaunchedEffect(noteById) {
        val unavailableIds = items.filter { unavailableNoteOf(it) != null }.map { it.id }
        if (unavailableIds.isNotEmpty()) selected.removeAll(unavailableIds)
    }

    // 按提权通道分行：Root 一组，ADB / Shizuku 一组；组内按分类分段。
    val channelBlocks = remember(items, noteById) {
        listOf(PerfChannels.ROOT, PerfChannels.ADB).mapNotNull { channel ->
            val ofChannel = items.filter { PerfChannels.of(it) == channel }
            if (ofChannel.isEmpty()) null
            else ChannelBlock(
                channel = channel,
                items = ofChannel,
                categories = BrandDatabase.categories.mapNotNull { cat ->
                    val ofCat = ofChannel.filter { it.category == cat.id }
                    if (ofCat.isEmpty()) null else cat to ofCat
                }
            )
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

        // ---------------- 当前机型分类（由 BrandPerfScreen 传入 brandFilter 时显示） ----------------
        if (filter != null) {
            GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(18.dp)) {
                    BasicText(
                        String.format(
                            AppStrings.get("perf_current_category"),
                            AppStrings.get(BrandDatabase.nameKeyOf(filter))
                        ),
                        style = TextStyle(contentColor, 15f.sp, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        AppStrings.get("perf_filter_applied"),
                        style = TextStyle(contentColor.copy(alpha = 0.6f), 11f.sp)
                    )
                    // 诚实提示：看的不是本机品牌时，不能让人以为这些厂商接口在本机一定存在
                    val foreignBrand = info != null &&
                            filter != BrandDatabase.BRAND_ALL &&
                            filter != info.brandId
                    if (foreignBrand) {
                        Spacer(Modifier.height(4.dp))
                        BasicText(
                            AppStrings.get("perf_filter_foreign_brand"),
                            style = TextStyle(Color(0xFFFF9500), 11f.sp)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    LiquidButton(
                        onClick = { localFilter = null },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        tint = Color(0xFF8E8E93)
                    ) {
                        BasicText(
                            AppStrings.get("perf_show_all_devices"),
                            Modifier.padding(horizontal = 8.dp),
                            style = TextStyle(Color.White, 13f.sp, FontWeight.Medium)
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

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

                    // ---- 自动识别机型：适用性判断（真实读数，不是猜的）----
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        AppStrings.get("apply_applicability"),
                        style = TextStyle(contentColor, 13f.sp, FontWeight.Medium)
                    )
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        PerfRunner.applicabilitySummary(info),
                        style = TextStyle(contentColor.copy(alpha = 0.65f), 11f.sp)
                    )
                    // 因缺少提权而跑不了的项如实列出来，避免用户点了没反应却不知道为什么
                    val blocked = remember(info) { PerfRunner.blockedByPermission(info) }
                    if (blocked.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            "${AppStrings.get("apply_blocked")}：${blocked.size}",
                            style = TextStyle(Color(0xFFFF9500), 11f.sp)
                        )
                        blocked.take(4).forEach { (blockedItem, note) ->
                            BasicText(
                                "· ${AppStrings.get(blockedItem.nameKey)}（${note.detail}）",
                                style = TextStyle(contentColor.copy(alpha = 0.55f), 10f.sp)
                            )
                        }
                        if (blocked.size > 4) {
                            BasicText(
                                "· …",
                                style = TextStyle(contentColor.copy(alpha = 0.55f), 10f.sp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PerfBadge(
                            AppStrings.get("generic_count").trim(),
                            AppTheme.accent
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
                        leftTint = AppTheme.accentAlt,
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
                // 已选条数按"当前列表里真实存在且本机可执行"的项统计
                val selectedCount = items.count { it.id in selected && unavailableNoteOf(it) == null }
                val runningText = if (running) {
                    "${AppStrings.get("run_progress")}: $progressIndex / $progressTotal"
                } else {
                    String.format(AppStrings.get("selected_count"), selectedCount)
                }
                BasicText(runningText, style = TextStyle(contentColor, 13f.sp, FontWeight.Medium))
                Spacer(Modifier.height(10.dp))

                LiquidButton(
                    onClick = {
                        val toRun = runnableItems.filter { it.id in selected }
                        if (toRun.any { it.risk == "risky" }) {
                            requestConfirm(AppStrings.get("confirm_run_risky")) { runItems(toRun) }
                        } else {
                            runItems(toRun)
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    tint = if (running) Color(0xFF8E8E93) else AppTheme.accent
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
                    leftTint = AppTheme.accentAlt,
                    onLeft = {
                        selected.clear()
                        // 只全选本机真正可执行的项：缺提权的项在各自区块里已标明原因
                        selected.addAll(runnableItems.map { it.id })
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
                        val toRun = runnableItems.filter { it.id in selected }
                        if (toRun.any { it.risk == "risky" }) {
                            requestConfirm(AppStrings.get("confirm_run_risky")) { runItems(toRun) }
                        } else {
                            runItems(toRun)
                        }
                    },
                    rightLabel = AppStrings.get("run_all"),
                    rightTint = Color(0xFFFF3B30),
                    onRight = {
                        requestConfirm(AppStrings.get("confirm_run_risky")) { runItems(runnableItems) }
                    },
                    leftEnabled = !running && selectedCount > 0,
                    rightEnabled = !running && runnableItems.isNotEmpty()
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
                // 指定了机型分类时，不再提供"包含品牌专属项"开关：该分类下品牌项本来就属于本区
                if (filter == null) {
                    PerfToggleRow(
                        label = AppStrings.get("perf_cat_brand"),
                        checked = includeBrandSpecific,
                        onCheckedChange = { includeBrandSpecific = it },
                        backdrop = backdrop,
                        contentColor = contentColor
                    )
                }
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

        // ---------------- 指令清单：先按提权通道分行，组内再按分类分段 ----------------
        channelBlocks.forEach { block ->
            Spacer(Modifier.height(14.dp))
            val channelMissing = info != null && PerfChannels.missingOn(info, block.channel)
            GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(18.dp)) {
                    PerfChannelHeader(
                        title = AppStrings.get(PerfChannels.titleKey(block.channel)),
                        countText = String.format(AppStrings.get("perf_group_count"), block.items.size),
                        barColor = if (block.channel == PerfChannels.ROOT) Color(0xFFFF3B30) else AppTheme.accent,
                        contentColor = contentColor,
                        warning = when {
                            !channelMissing -> null
                            block.channel == PerfChannels.ROOT -> AppStrings.get("perf_group_root_missing")
                            else -> AppStrings.get("perf_group_adb_missing")
                        },
                        note = when {
                            block.channel == PerfChannels.ROOT -> AppStrings.get("perf_group_root_note")
                            // 只有 Root 没有 Shizuku 时必须说明回退行为，不能让人以为本组全废
                            info != null && !info.hasShizuku -> AppStrings.get("perf_group_adb_note")
                            else -> null
                        }
                    )
                    Spacer(Modifier.height(10.dp))
                    PerfButtonRow(
                        backdrop = backdrop,
                        leftLabel = AppStrings.get("perf_select_group"),
                        leftTint = AppTheme.accentAlt,
                        onLeft = {
                            selected.addAll(block.items.filter { unavailableNoteOf(it) == null }.map { it.id })
                        },
                        rightLabel = AppStrings.get("perf_clear_group"),
                        rightTint = Color(0xFF8E8E93),
                        onRight = { selected.removeAll(block.items.map { it.id }) },
                        leftEnabled = !running,
                        rightEnabled = !running
                    )

                    block.categories.forEach { (cat, catItems) ->
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .width(4.dp)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(cat.color))
                            )
                            Spacer(Modifier.width(8.dp))
                            BasicText(
                                AppStrings.get(cat.nameKey),
                                style = TextStyle(contentColor, 14f.sp, FontWeight.Bold)
                            )
                            Spacer(Modifier.weight(1f))
                            BasicText(
                                "${catItems.count { it.id in selected }} / ${catItems.size}",
                                style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
                            )
                        }
                        Spacer(Modifier.height(8.dp))
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
                                    result = resultById[item.id],
                                    unavailableNote = unavailableNoteOf(item)
                                )
                            }
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

/**
 * 一条提权通道下的指令区块：通道 id + 该通道的全部项 + 组内按分类分段后的项。
 * 分类顺序沿用 [BrandDatabase.categories]，因此原有分类一个都没少，只是各自归入了
 * Root 或 ADB 通道的区块里。
 */
private data class ChannelBlock(
    val channel: String,
    val items: List<PerfItem>,
    val categories: List<Pair<PerfCategory, List<PerfItem>>>
)
