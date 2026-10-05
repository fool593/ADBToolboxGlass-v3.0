package com.example.adbtoolbox.common.perf

/**
 * 提权通道分组。
 *
 * 用户要求「root 跟 root 的放一行，ADB 跟 ADB 的放一行」，本对象是这条要求的
 * **唯一判定来源**：界面（PerformanceBoostScreen / PhoneInspectorScreen）不自己
 * 判断 [PerfItem.requiresPermission]，一律调用这里的方法，避免两个页面出现不一致的分组。
 *
 * 分组规则与执行层 `ADBTools.execPerfCommand` 的三级回退一致：
 * - [ROOT]：`requiresPermission == "root"`，必须有 Root 才能执行；
 * - [ADB]：`requiresPermission == "shizuku"` 或 `"none"`。这一类在只有 Root 的设备上
 *   同样可执行（执行层会回退到 su），因此界面必须在组标题里如实说明，
 *   而不是把它们一律标成"不可用"。
 */
object PerfChannels {

    const val ROOT = "root"
    const val ADB = "adb"

    /** 该条指令属于哪条通道。未知权限值一律归入 [ADB]（与执行层的"尽量执行、失败如实回显"一致）。 */
    fun of(item: PerfItem): String =
        if (item.requiresPermission == UniversalTuning.PERM_ROOT) ROOT else ADB

    /** 一组指令里的 Root 专属项。 */
    fun rootItems(items: List<PerfItem>): List<PerfItem> = items.filter { of(it) == ROOT }

    /** 一组指令里的 ADB / Shizuku 可用项（含无需提权的项）。 */
    fun adbItems(items: List<PerfItem>): List<PerfItem> = items.filter { of(it) == ADB }

    /**
     * 该通道在本机是否缺少提权。
     * [ROOT] 缺 Root；[ADB] 三条提权通道（Shizuku / Root / Dhizuku）全都没有时才算缺失。
     */
    fun missingOn(info: PerfRunner.PerfDeviceInfo, channel: String): Boolean = when (channel) {
        ROOT -> !info.hasRoot
        ADB -> !(info.hasShizuku || info.hasRoot || info.isDhizukuActive)
        else -> true
    }

    /** 通道组标题的文案 key（两个页面共用，保证标题一致）。 */
    fun titleKey(channel: String): String = when (channel) {
        ROOT -> "perf_group_root"
        else -> "perf_group_adb"
    }
}
