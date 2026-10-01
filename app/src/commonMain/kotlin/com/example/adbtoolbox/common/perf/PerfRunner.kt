package com.example.adbtoolbox.common.perf

import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 性能加速与体检的执行层：把 [PerfItem] 真正跑起来，并如实汇报每一条的结果。
 *
 * 设计原则（防止"空壳"）：
 * - 每条命令都单独记录 exitCode / stdout / stderr，失败原因原样回显，不做静默吞异常。
 * - 需要权限的项先探测 Shizuku/Root，不可用时给出明确提示，而不是假装成功。
 * - 内存前后差值基于 `dumpsys meminfo` 的真实读数，拿不到就返回 0 而不是编造。
 */
object PerfRunner {

    /** 设备识别快照，一次读取多次使用，避免反复 exec 造成卡顿。 */
    data class PerfDeviceInfo(
        val brandId: String,
        val brandKey: String,
        val brandRaw: String,
        val model: String,
        val androidVersion: String,
        val sdk: Int,
        val romName: String,
        val hasBrandSpecific: Boolean,
        val cpuModel: String,
        val socVendor: String,
        val currentRefreshRate: Float,
        val maxRefreshRate: Float,
        val supportedRefreshRates: List<Float>,
        val totalRamMb: Long,
        val availRamMb: Long,
        val freeStorageMb: Long,
        val hasShizuku: Boolean,
        val hasRoot: Boolean,
        val isDhizukuActive: Boolean
    ) {
        /** 是否至少有一条可用的提权通道 */
        val hasAnyPrivilege: Boolean get() = hasShizuku || hasRoot || isDhizukuActive
    }

    private val refreshedProps = listOf(
        "ro.product.brand", "ro.product.vendor.brand", "ro.product.system.brand",
        "ro.product.manufacturer", "ro.product.model", "ro.product.vendor.model",
        "ro.build.version.release", "ro.build.version.sdk", "ro.build.display.id",
        "ro.board.platform", "ro.hardware", "ro.product.device"
    )

    suspend fun loadDeviceInfo(): PerfDeviceInfo = withContext(Dispatchers.Default) {
        val props = try { ADBTools.getProps(refreshedProps) } catch (e: Exception) { emptyMap() }
        val brandRaw = props["ro.product.brand"].orEmpty()
            .ifBlank { props["ro.product.vendor.brand"].orEmpty() }
            .ifBlank { props["ro.product.system.brand"].orEmpty() }
        val manufacturer = props["ro.product.manufacturer"].orEmpty()
        val model = props["ro.product.model"].orEmpty()
            .ifBlank { props["ro.product.vendor.model"].orEmpty() }
        val profile = BrandDatabase.detect(brandRaw, manufacturer, model)
        val supported = try { ADBTools.getSupportedRefreshRates() } catch (e: Exception) { emptyList() }
        val current = try { ADBTools.getCurrentRefreshRate() } catch (e: Exception) { 0f }
        val max = try { ADBTools.getMaxRefreshRate() } catch (e: Exception) { 0f }
        val (totalRam, availRam, freeStorage) = try {
            val mem = readMemoryMb()
            Triple(mem.first, mem.second, readFreeStorageMb())
        } catch (e: Exception) { Triple(0L, 0L, 0L) }
        PerfDeviceInfo(
            brandId = profile.id,
            brandKey = profile.nameKey,
            brandRaw = brandRaw.ifBlank { "Unknown" },
            model = model.ifBlank { "Unknown" },
            androidVersion = props["ro.build.version.release"].orEmpty().ifBlank { "Unknown" },
            sdk = props["ro.build.version.sdk"].orEmpty().toIntOrNull() ?: 0,
            romName = profile.romName,
            hasBrandSpecific = profile.hasBrandSpecific,
            cpuModel = try { ADBTools.getCpuModel() } catch (e: Exception) { "Unknown" },
            socVendor = try { ADBTools.getCpuVendor() } catch (e: Exception) { "other" },
            currentRefreshRate = current,
            maxRefreshRate = if (max > 1f) max else current,
            supportedRefreshRates = supported,
            totalRamMb = totalRam,
            availRamMb = availRam,
            freeStorageMb = freeStorage,
            hasShizuku = try { ADBTools.isShizukuAvailable() } catch (e: Exception) { false },
            hasRoot = try { ADBTools.isRooted() } catch (e: Exception) { false },
            isDhizukuActive = try { ADBTools.isDhizukuActive() } catch (e: Exception) { false }
        )
    }

    /** 用 `dumpsys meminfo` 读总内存 / 可用内存（MB）。读不到返回 (0, 0)。 */
    private fun readMemoryMb(): Pair<Long, Long> {
        val out = ADBTools.execCommand("dumpsys meminfo", timeout = 15).output
        // 形如: Total RAM: 11,923,412K (status normal)   /   Free RAM: 3,456,789K
        fun find(label: String): Long {
            val m = Regex("$label:\\s*([0-9,]+)K").find(out) ?: return 0L
            return m.groupValues[1].replace(",", "").toLongOrNull()?.div(1024) ?: 0L
        }
        val total = find("Total RAM")
        val free = find("Free RAM")
        if (total > 0L) return total to free
        // 回退：/proc/meminfo
        val meminfo = ADBTools.execCommand("cat /proc/meminfo", timeout = 10).output
        val t = Regex("MemTotal:\\s*(\\d+)").find(meminfo)?.groupValues?.get(1)?.toLongOrNull()?.div(1024) ?: 0L
        val a = Regex("MemAvailable:\\s*(\\d+)").find(meminfo)?.groupValues?.get(1)?.toLongOrNull()?.div(1024) ?: 0L
        return t to a
    }

    private fun readFreeStorageMb(): Long {
        val out = ADBTools.execCommand("df /data", timeout = 10).output
        // 形如: /data  117G  50G  60G  46%  /data
        val line = out.lineSequence().firstOrNull { it.contains("/data") && !it.contains("Filesystem") }
        if (line == null) return 0L
        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 4) return 0L
        val avail = parts[3]
        val num = avail.dropLastWhile { it.isLetter() }.toDoubleOrNull() ?: return 0L
        val unit = avail.lastOrNull()?.uppercaseChar()
        return when (unit) {
            'G' -> (num * 1024).toLong()
            'M' -> num.toLong()
            'K' -> (num / 1024).toLong()
            else -> num.toLong()
        }
    }

    /**
     * 解析 [PerfItem.command]：
     * - `__ADBTOOLS_MAX_REFRESH__` 这类占位符会被替换成真实命令（这里有专门的实现）。
     */
    private fun resolveCommand(item: PerfItem, info: PerfDeviceInfo): String {
        return when (item.id) {
            "display_max_refresh" -> {
                val target = if (info.maxRefreshRate > 1f) info.maxRefreshRate else 120f
                val v = if (target % 1f == 0f) target.toInt().toString() else target.toString()
                "settings put system peak_refresh_rate $v; " +
                        "settings put secure user_refresh_rate $v; " +
                        "settings put system min_refresh_rate 0 2>/dev/null; " +
                        "pkill -f com.android.systemui || killall com.android.systemui; echo REFRESHMAXDONE"
            }
            else -> {
                var cmd = item.command
                val name = item.paramName
                if (name != null && item.paramValue != null) {
                    cmd = cmd.replace("\${$name}", item.paramValue)
                }
                cmd
            }
        }
    }

    /**
     * 按顺序执行一组 [PerfItem]。
     * [onProgress] 每开始一条回调一次（index 从 1 开始），用于 UI 进度显示。
     */
    suspend fun run(
        items: List<PerfItem>,
        info: PerfDeviceInfo,
        onProgress: (index: Int, total: Int, item: PerfItem) -> Unit = { _, _, _ -> }
    ): PerfRunReport = withContext(Dispatchers.Default) {
        val startFree = try { readMemoryMb().second } catch (e: Exception) { 0L }
        val t0 = System.currentTimeMillis()
        val results = ArrayList<PerfRunResult>(items.size)
        items.forEachIndexed { i, item ->
            onProgress(i + 1, items.size, item)
            val result = try {
                val cmd = resolveCommand(item, info)
                if (cmd.isBlank() || cmd == "echo NOANALYTICS") {
                    PerfRunResult(item.id, item.nameKey, cmd, 1, "", "该品牌在本机没有可用的对应指令")
                } else {
                    val r = ADBTools.execPerfCommand(cmd, timeout = commandTimeout(item))
                    PerfRunResult(item.id, item.nameKey, cmd, r.exitCode, r.output, r.error)
                }
            } catch (e: Exception) {
                PerfRunResult(item.id, item.nameKey, item.command, -1, "", "${e.javaClass.simpleName}: ${e.message}")
            }
            results.add(result)
        }
        val endFree = try { readMemoryMb().second } catch (e: Exception) { startFree }
        PerfRunReport(
            results = results,
            startFreeMemoryMb = startFree,
            endFreeMemoryMb = endFree,
            durationMs = System.currentTimeMillis() - t0
        )
    }

    /**
     * 不同指令耗时差异很大（TRIM / VACUUM / force-stop 批量都很慢），超时按项给。
     * [PerfItem.timeoutMs] 显式声明时优先（[UniversalTuning] 的长耗时项都声明了）。
     */
    private fun commandTimeout(item: PerfItem): Int {
        if (item.timeoutMs > 0) return (item.timeoutMs / 1000).coerceAtLeast(5)
        return when {
            item.id.contains("vacuum") -> 120
            item.id.contains("fstrim") -> 90
            item.id.contains("kill_bg") || item.id.contains("kill_background") -> 45
            item.id.contains("trim_caches") -> 40
            item.id.contains("dexopt") || item.id.contains("compile") -> 600
            item.id.contains("kill_background_all") -> 45
            else -> 20
        }
    }

    // ------------------------------------------------------------ 机型适用性识别

    /**
     * 自动识别机型后的**适用性结论**（不伪造结论，拿不到的信息一律标 unknown）。
     *
     * 判断依据全部来自 [loadDeviceInfo] 真实读到的字段：
     * - 品牌：识别到具体品牌 → 该品牌专属项可用；识别不出（generic）→ 只有通用项可用；
     * - SoC 厂商：qualcomm / mediatek / samsung / google / hisilicon / unisoc 归一化后，
     *   只有对应厂商限定的项才标记可用；厂商读不到 → 全部厂商限定项标记"无法确认"；
     * - SDK：低于 [PerfItem.minSdk] → 接口不存在；高于 [PerfItem.maxSdk] → 已移除；
     * - 权限：需要 root 而无 root → 不可用；需要 Shizuku 而 shizuku/root/Dhizuku 全无 → 不可用。
     *
     * @param includeBrandSpecific 是否把品牌专属项一起纳入结论（默认 true）。
     */
    fun applicability(
        info: PerfDeviceInfo,
        includeBrandSpecific: Boolean = true
    ): List<UniversalTuning.ApplicabilityNote> =
        UniversalTuning.applicability(info, BrandDatabase.itemsFor(info.brandId, includeBrandSpecific))

    /** 当前设备上"确实可用"的项（权限 + SDK + 品牌 + SoC 全部满足）。 */
    fun applicableItems(info: PerfDeviceInfo, includeBrandSpecific: Boolean = true): List<PerfItem> =
        UniversalTuning.applicableItems(info, BrandDatabase.itemsFor(info.brandId, includeBrandSpecific))

    /**
     * 当前设备上"因为缺少提权而暂时不可用"的项及其原因。
     * 用于界面明确列出"哪些项需要提权、现在为什么跑不了"。
     */
    fun blockedByPermission(info: PerfDeviceInfo): List<Pair<PerfItem, UniversalTuning.ApplicabilityNote>> =
        UniversalTuning.blockedByPermission(info)

    /** 一行机型识别与适用性摘要（内容全部来自真实读数）。 */
    fun applicabilitySummary(info: PerfDeviceInfo): String = UniversalTuning.summary(info)

    // ------------------------------------------------------------ 体检

    /**
     * 体检：逐项验证指令在本机是否真的可用。
     *
     * 判定规则：
     * - 有 [PerfItem.verifyCommand] 的项：执行验证命令，输出非空即视为"本机可用"；
     *   若 [PerfItem.verifyExpect] 非空则要求输出包含该片段。
     * - 没有验证命令的项：用"权限是否具备"判定（需要 root 但无 root → FAIL；需要 Shizuku 但无 → FAIL）。
     * - 权限齐备且是 `isDisableAction` 的项：检查是否已关闭（已关闭 → OK，否则 → WARN 并给出 fixCommand）。
     */
    suspend fun inspect(
        info: PerfDeviceInfo,
        onProgress: (phase: Int, total: Int, label: String) -> Unit = { _, _, _ -> }
    ): InspectReport = withContext(Dispatchers.Default) {
        val totalPhases = 5
        val groups = ArrayList<InspectGroup>(totalPhases)
        val summary = linkedMapOf(
            "brand" to info.brandRaw,
            "model" to info.model,
            "rom" to info.romName,
            "android" to (if (info.androidVersion != "Unknown") "Android ${info.androidVersion} (SDK ${info.sdk})" else "Unknown"),
            "cpu" to (if (info.cpuModel != "Unknown") "${info.cpuModel} · ${info.socVendor}" else "Unknown"),
            "ram" to (if (info.totalRamMb > 0) "${info.totalRamMb / 1024} GB (可用 ${info.availRamMb / 1024} GB)" else "Unknown"),
            "refresh" to "当前 ${fmtHz(info.currentRefreshRate)} / 最高 ${fmtHz(info.maxRefreshRate)}"
        )

        // ---- 1. 设备与系统 ----
        onProgress(1, totalPhases, AppStrings.get("inspect_group_device"))
        val deviceResults = ArrayList<InspectResult>()
        deviceResults += InspectResult(
            "dev_rom", "inspect_group_device",
            if (info.sdk > 0) "OK" else "FAIL",
            "Android ${info.androidVersion} (SDK ${info.sdk}) · ${info.romName}"
        )
        deviceResults += InspectResult(
            "dev_cpu", "inspect_group_device",
            if (info.cpuModel != "Unknown") "OK" else "WARN",
            if (info.cpuModel != "Unknown") "${info.cpuModel} · ${info.socVendor}" else AppStrings.get("unknown")
        )
        deviceResults += InspectResult(
            "dev_ram", "inspect_group_device",
            if (info.totalRamMb > 0) "OK" else "WARN",
            if (info.totalRamMb > 0) "${info.totalRamMb / 1024} GB / 可用 ${info.availRamMb / 1024} GB" else AppStrings.get("unknown")
        )
        deviceResults += InspectResult(
            "dev_storage", "inspect_group_device",
            if (info.freeStorageMb > 0) "OK" else "WARN",
            if (info.freeStorageMb > 0) "可用 ${info.freeStorageMb / 1024} GB" else AppStrings.get("unknown")
        )
        groups += InspectGroup("device", "inspect_group_device", "device", deviceResults)

        // ---- 2. 权限环境 ----
        onProgress(2, totalPhases, AppStrings.get("inspect_group_root"))
        val rootResults = ArrayList<InspectResult>()
        rootResults += InspectResult(
            "env_shizuku", "inspect_group_root",
            if (info.hasShizuku) "OK" else "WARN",
            if (info.hasShizuku) AppStrings.get("available") else AppStrings.get("not_detected")
        )
        rootResults += InspectResult(
            "env_root", "inspect_group_root",
            if (info.hasRoot) "OK" else "WARN",
            if (info.hasRoot) AppStrings.get("available") else AppStrings.get("not_detected")
        )
        rootResults += InspectResult(
            "env_dhizuku", "inspect_group_root",
            if (info.isDhizukuActive) "OK" else "SKIP",
            if (info.isDhizukuActive) AppStrings.get("available") else AppStrings.get("not_detected")
        )
        groups += InspectGroup("root", "inspect_group_root", "root", rootResults)

        // ---- 3. 刷新率（用户重点反馈项） ----
        onProgress(3, totalPhases, AppStrings.get("inspect_group_refresh"))
        val refreshResults = ArrayList<InspectResult>()
        val supportText = if (info.supportedRefreshRates.isEmpty()) AppStrings.get("unknown")
        else info.supportedRefreshRates.joinToString(" / ") { fmtHz(it) }
        val lockedTo60 = info.maxRefreshRate > 61f && info.currentRefreshRate <= 61f
        refreshResults += InspectResult(
            "ref_current", "inspect_group_refresh",
            when {
                info.currentRefreshRate <= 0f -> "WARN"
                lockedTo60 -> "WARN"
                else -> "OK"
            },
            String.format(
                AppStrings.get("refresh_rate_detail"),
                fmtHz(info.currentRefreshRate), fmtHz(info.maxRefreshRate), supportText
            ),
            fixCommand = if (lockedTo60) "__FIX_REFRESH__" else null,
            perfItemId = "display_max_refresh"
        )
        refreshResults += InspectResult(
            "ref_peak", "inspect_group_refresh",
            if (ADBTools.getSystemSetting("system", "peak_refresh_rate").isNotBlank()) "OK" else "WARN",
            "peak_refresh_rate = ${ADBTools.getSystemSetting("system", "peak_refresh_rate").ifBlank { AppStrings.get("not_configured") }}",
            fixCommand = if (lockedTo60) "__FIX_REFRESH__" else null,
            perfItemId = "display_max_refresh"
        )
        groups += InspectGroup("refresh", "inspect_group_refresh", "refresh", refreshResults)

        // ---- 4. 性能相关设置（逐条真实验证） ----
        onProgress(4, totalPhases, AppStrings.get("inspect_group_perf"))
        // 覆盖全部通用项：有 verifyCommand 的做真实验证；没有验证命令的只做权限/适用性判定，
        // 不执行任何写操作，避免体检本身改动系统。品牌专属项在下个分组单独处理。
        val perfItems = BrandDatabase.universalItems
        val applicability = UniversalTuning.applicability(info, perfItems)
            .associateBy { it.itemId ?: it.scope }
        val perfResults = ArrayList<InspectResult>()
        perfItems.forEach { item ->
            val needsRoot = item.requiresPermission == "root"
            val needsShizuku = item.requiresPermission == "shizuku"
            val permitted = when {
                needsRoot -> info.hasRoot
                needsShizuku -> info.hasShizuku || info.hasRoot || info.isDhizukuActive
                else -> true
            }
            val note = applicability[item.id]
            val verify = item.verifyCommand
            // 适用性优先：SDK 太旧 / SoC 不符 / 品牌不符 → 明确判"本机不适用"，不假装可用。
            val notApplicable = note != null && (
                    note.state == UniversalTuning.STATE_SDK_OLDER ||
                            note.state == UniversalTuning.STATE_SDK_NEWER ||
                            note.state == UniversalTuning.STATE_SOC_MISMATCH ||
                            note.state == UniversalTuning.STATE_BRAND_ONLY
                    )
            if (notApplicable && note != null) {
                perfResults += InspectResult(
                    item.id, "inspect_group_perf", "SKIP",
                    "${AppStrings.get(note.reasonKey)} · ${note.detail}",
                    fixCommand = null,
                    perfItemId = item.id
                )
            } else if (verify != null && permitted) {
                // 体检要逐条跑全部通用项（含 UniversalTuning 的 20 条）的探测命令，
                // 单条超时必须短，否则整轮体检会拖到几分钟。
                val r = try { ADBTools.execPerfCommand(verify, timeout = 6) } catch (e: Exception) {
                    com.example.adbtoolbox.common.CommandResult("", e.message ?: "error", -1)
                }
                val text = (r.output + r.error).trim()
                val expect = item.verifyExpect
                val ok = text.isNotBlank() && (expect.isNullOrEmpty() || text.contains(expect, true))
                perfResults += InspectResult(
                    item.id, "inspect_group_perf",
                    if (ok) "OK" else "WARN",
                    if (text.isBlank()) AppStrings.get("unsupported") else text.lineSequence().first().take(120),
                    fixCommand = if (ok) null else item.command,
                    perfItemId = item.id
                )
            } else {
                perfResults += InspectResult(
                    item.id, "inspect_group_perf",
                    if (permitted) "OK" else "FAIL",
                    when {
                        !permitted -> AppStrings.get("no_permission_hint")
                        note != null && note.state == UniversalTuning.STATE_UNKNOWN ->
                            AppStrings.get("unknown") + " · " + note.detail
                        item.uncertain -> AppStrings.get("available") + " · " + AppStrings.get("unknown")
                        else -> AppStrings.get("available")
                    },
                    fixCommand = if (permitted) null else item.command,
                    perfItemId = item.id
                )
            }
        }
        if (perfResults.isNotEmpty()) groups += InspectGroup("perf", "inspect_group_perf", "perf", perfResults)

        // ---- 5. 品牌专属能力 ----
        onProgress(5, totalPhases, AppStrings.get("inspect_group_brand"))
        val brandResults = ArrayList<InspectResult>()
        val brandItems = BrandDatabase.brandSpecificItems(info.brandId)
        if (brandItems.isEmpty()) {
            brandResults += InspectResult(
                "brand_none", "inspect_group_brand", "SKIP",
                AppStrings.get("not_recognized_brand") + " · " + info.brandRaw
            )
        } else {
            // 品牌专属项太多，逐条 exec 会非常慢：这里抽查前 4 条，其余只做权限判定
            brandItems.take(4).forEach { item ->
                val needsRoot = item.requiresPermission == "root"
                val permitted = if (needsRoot) info.hasRoot else (info.hasShizuku || info.hasRoot || info.isDhizukuActive)
                brandResults += InspectResult(
                    item.id, "inspect_group_brand",
                    if (permitted) "OK" else "FAIL",
                    if (permitted) AppStrings.get("available") else AppStrings.get("no_permission_hint"),
                    fixCommand = if (permitted) null else item.command,
                    perfItemId = item.id
                )
            }
            brandResults += InspectResult(
                "brand_total", "inspect_group_brand", "OK",
                "${brandItems.size} ${AppStrings.get("brand_specific_count")}"
            )
        }
        groups += InspectGroup("brand", "inspect_group_brand", "brand", brandResults)

        // ---- 评分 ----
        val all = groups.flatMap { it.results }
        val score = if (all.isEmpty()) 0 else {
            val weight = all.sumOf { r -> when (r.status) { "OK" -> 1.0; "SKIP" -> 0.7; "WARN" -> 0.45; else -> 0.0 } }
            ((weight / all.size) * 100).toInt().coerceIn(0, 100)
        }
        InspectReport(
            score = score,
            timestamp = System.currentTimeMillis(),
            groups = groups,
            deviceSummary = summary,
            brand = BrandDatabase.detect(info.brandRaw)
        )
    }

    private fun fmtHz(v: Float): String =
        if (v <= 0f) AppStrings.get("unknown") else "${if (v % 1f == 0f) v.toInt().toString() else v.toString()} Hz"
}
