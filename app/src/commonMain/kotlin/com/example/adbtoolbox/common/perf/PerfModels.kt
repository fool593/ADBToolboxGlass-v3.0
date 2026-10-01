package com.example.adbtoolbox.common.perf

/**
 * 性能加速的"真实可执行指令"模型。
 *
 * 设计要点（很重要，别改成空壳）：
 * 1. [command] 必须是真正能生效的 shell 命令。凡是需要权限的，用 [requiresPermission] 标注，
 *    执行层（ADBTools.execPerfCommand）会自动按 Shizuku → Root → 普通 shell 三级回退，
 *    不会静默失败：失败原因会逐条回显在结果里。
 * 2. [verifyCommand] 用于体检"这个指令在本机是否真的能生效"，为空则跳过验证。
 * 3. [toggleOffCommand] 用于可逆操作（例如关闭动画后可以恢复），为空表示不可逆（例如清理缓存）。
 * 4. 所有展示文案都用 [AppStrings] 的 key，不能直接写死中文/英文，否则多语言会漏。
 */
data class PerfItem(
    /** 稳定 ID，用于保存勾选状态、日志与去重 */
    val id: String,
    /** 文案 key，前缀 perf_item_ */
    val nameKey: String,
    /** 说明文案 key，前缀 perf_item_ */
    val descKey: String,
    /** 真正执行的 shell 命令（可含 `;` 串联多条） */
    val command: String,
    /** 分类 id，见 [PerfCategory.id] */
    val category: String,
    /** 参数名占位符，例如 "scale"；执行时会把 ${'$'}{scale} 替换为 [paramValue] */
    val paramName: String? = null,
    /** 参数默认值 */
    val paramValue: String? = null,
    /** 权限要求：none / shizuku / root */
    val requiresPermission: String = "none",
    /** 风险等级：safe / caution / risky */
    val risk: String = "safe",
    /** 是否默认勾选 */
    val defaultSelected: Boolean = true,
    /** 体检验证命令：输出非空且包含 [verifyExpect] 则视为"本机可用" */
    val verifyCommand: String? = null,
    val verifyExpect: String? = null,
    /** 可逆操作的恢复命令；null 表示不可逆 */
    val toggleOffCommand: String? = null,
    /** true 表示这是"关掉某个东西"的项（如关闭动画），用于体检给出建议 */
    val isDisableAction: Boolean = false,
    /** 同一次一键加速内部的执行顺序，小的先执行 */
    val order: Int = 100
)

/** 性能加速分类。id 必须与 [PerfItem.category] 对应。 */
data class PerfCategory(
    val id: String,
    /** 文案 key，前缀 perf_cat_ */
    val nameKey: String,
    /** 分类颜色 0xAARRGGBB，用于卡片配色 */
    val color: Long
)

/** 品牌识别结果。 */
data class BrandProfile(
    /** 品牌 id：xiaomi / huawei / honor / oppo / realme / vivo / iqoo / oneplus / samsung / meizu / nubia / asus / sony / motorola / google / generic */
    val id: String,
    /** 文案 key，前缀 brand_ */
    val nameKey: String,
    /** 识别到的原始品牌串（Build.BRAND / ro.product.brand） */
    val rawBrand: String,
    /** 系统 UI 名称，例如 HyperOS / ColorOS / OriginOS / OneUI */
    val romName: String,
    /** 该品牌是否有专属调优指令 */
    val hasBrandSpecific: Boolean
)

/** 体检单项结果。 */
data class InspectResult(
    val id: String,
    val nameKey: String,
    /** OK = 可用，WARN = 可用但未开启/状态需要注意，FAIL = 本机不可用，SKIP = 不适用 */
    val status: String,
    /** 面向用户的结论（已由调用方完成多语言，或直接是命令原始输出摘要） */
    val detail: String,
    /** 建议执行的修复命令；null 表示无需处理 */
    val fixCommand: String? = null,
    /** 对应的 [PerfItem.id]，便于跳转到性能加速 */
    val perfItemId: String? = null
)

/** 体检分组：把同一主题的检测项放在一张玻璃卡片里。 */
data class InspectGroup(
    val id: String,
    val nameKey: String,
    val icon: String,
    val results: List<InspectResult>
)

/** 体检总体报告。 */
data class InspectReport(
    /** 0~100 综合得分 */
    val score: Int,
    /** 检测时间戳 */
    val timestamp: Long,
    val groups: List<InspectGroup>,
    /** 机型识别的原始属性快照，展示在报告顶部 */
    val deviceSummary: Map<String, String>,
    val brand: BrandProfile
) {
    val warnCount: Int get() = groups.sumOf { g -> g.results.count { it.status == "WARN" } }
    val failCount: Int get() = groups.sumOf { g -> g.results.count { it.status == "FAIL" } }
    val okCount: Int get() = groups.sumOf { g -> g.results.count { it.status == "OK" } }
}

/** 单条 [PerfItem] 的执行结果。 */
data class PerfRunResult(
    val id: String,
    val nameKey: String,
    val command: String,
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    /** 判定是否真的生效：退出码为 0，或输出里出现厂商成功标志。 */
    val succeeded: Boolean
        get() = exitCode == 0 || stdout.contains("Success", true) || stderr.contains("Success", true)
}

/** 批量执行汇总。 */
data class PerfRunReport(
    val results: List<PerfRunResult>,
    val startFreeMemoryMb: Long,
    val endFreeMemoryMb: Long,
    val durationMs: Long
) {
    val successCount: Int get() = results.count { it.succeeded }
    val failedCount: Int get() = results.size - successCount
    /** 释放出的内存（MB），可能为负，展示时按实际值显示 */
    val freedMemoryMb: Long get() = endFreeMemoryMb - startFreeMemoryMb
}
