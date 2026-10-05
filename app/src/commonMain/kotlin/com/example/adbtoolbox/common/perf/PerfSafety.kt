package com.example.adbtoolbox.common.perf

/**
 * 性能优化的**安全闸门**。
 *
 * 为什么需要它：这一版之前，部分优化项会执行 `pm disable-user` 去禁用厂商的
 * 后台/电池管理组件（例如 vivo 的后台管理、OPPO 的后台管控、华为的 powergenie），
 * 还会写 `settings put global background_process_limit 0`。这些操作会**动到系统的隐私与后台
 * 权限入口**：被禁用的组件正是"允许后台无限制运行"这类设置的承载者，禁掉之后系统设置里
 * 那些入口会直接消失，属于用户难以自行恢复的危险改动。
 *
 * 因此这里做两层保护，**执行层强制生效**（不依赖数据层是否写对）：
 *
 * 1. [isProtectedPackage]：系统组件、厂商的后台/电池/权限管理组件一律禁止被禁用或强停；
 * 2. [sanitize]：把命令按 `;` 拆开逐条检查，命中禁止操作的那一条会被**摘掉**，
 *    其余安全部分照常执行，并给出被摘掉的原文与原因（界面如实展示"跳过了什么"）。
 *
 * 注意：这里**只做拦截，不做静默修改**。任何被跳过的东西都会回传给调用方展示给用户，
 * 不假装执行成功。
 */
object PerfSafety {

    /**
     * 绝对不允许被禁用/强停的包。判断用"前缀或包含"匹配，覆盖同一组件的不同 ROM 包名。
     *
     * 分组说明：
     * - 系统与界面：禁掉会直接破坏手机可用性；
     * - 权限与设置：承载"应用权限""后台限制""电池优化"这些入口，正是用户反馈被弄没的东西；
     * - 厂商性能/电池服务：承载温控、后台管理、电池策略，禁掉会出现"设置项消失 / 无法后台运行"。
     */
    private val protectedExact: Set<String> = setOf(
        // --- 系统与界面 ---
        "android", "com.android.systemui", "com.android.settings", "com.android.shell",
        "com.android.providers.settings", "com.android.permissioncontroller",
        "com.android.packageinstaller", "com.google.android.permissioncontroller",
        "com.android.launcher3", "com.android.inputmethod.latin",
        "com.android.phone", "com.android.server.telecom", "com.android.providers.telephony",
        "com.android.bluetooth", "com.android.nfc", "com.android.wifi",
        // --- 厂商权限/设置/后台管理入口（用户反馈被屏蔽的就是这一类） ---
        "com.miui.securitycenter", "com.miui.powerkeeper", "com.miui.permcenter",
        "com.huawei.systemmanager", "com.huawei.powergenie", "com.huawei.android.hwaps",
        "com.coloros.oppoguardelf", "com.oplus.securitypermission", "com.oplus.battery",
        "com.oplus.osense", "com.coloros.safecenter", "com.coloros.phonemanager",
        "com.vivo.bgapp", "com.vivo.abe", "com.vivo.pem", "com.vivo.permissionmanager",
        "com.iqoo.secure", "com.vivo.managerservice",
        "com.samsung.android.lool", "com.samsung.android.sm", "com.sec.android.app.powerplanning",
        "com.meizu.safe", "com.meizu.power",
        "com.transsion.phonemaster", "com.zte.power", "com.asus.mobilemanager",
        "com.google.android.apps.turbo", "com.google.android.gms",
        // --- 输入法/桌面/应用商店这类禁掉会明显影响使用的 ---
        "com.baidu.input", "com.sohu.inputmethod.sogou", "com.iflytek.inputmethod",
        "com.tencent.mm" // 微信：无数人反馈被"优化"后收不到消息
    )

    /** 前缀匹配的保护规则（子包同样受保护）。刻意收窄：只覆盖系统/GMS 与桌面， */
    private val protectedPrefixes: List<String> = listOf(
        "com.android.",          // AOSP 全家
        "com.google.android.",   // GMS 全家
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.samsung.android.launcher",
        "com.meizu.flyme.launcher"
    )

    /** 是否受保护（禁止被禁用/强停）。 */
    fun isProtectedPackage(pkg: String): Boolean {
        val p = pkg.trim().trim('"', '\'').lowercase()
        if (p.isEmpty()) return false
        if (p in protectedExact) return true
        return protectedPrefixes.any { p.startsWith(it) }
    }

    /**
     * 全局后台策略类设置：这类值一旦写坏，会影响**整机所有应用**的后台运行，
     * 而且用户很难知道去哪儿改回来，所以一律禁止通过优化项修改。
     */
    private val forbiddenGlobalSettings: List<Regex> = listOf(
        Regex("""settings\s+put\s+global\s+background_process_limit\b""", RegexOption.IGNORE_CASE),
        Regex("""settings\s+put\s+global\s+max_cached_processes\b""", RegexOption.IGNORE_CASE),
        Regex("""settings\s+put\s+global\s+app_standby_enabled\b""", RegexOption.IGNORE_CASE),
        Regex("""settings\s+put\s+global\s+adaptive_battery_management_enabled\b""", RegexOption.IGNORE_CASE),
        Regex("""settings\s+put\s+global\s+app_auto_restriction_enabled\b""", RegexOption.IGNORE_CASE),
        Regex("""cmd\s+appops\s+set\s+\S+\s+RUN_ANY_IN_BACKGROUND\s+(ignore|deny)\b""", RegexOption.IGNORE_CASE),
        Regex("""cmd\s+appops\s+set\s+\S+\s+RUN_IN_BACKGROUND\s+(ignore|deny)\b""", RegexOption.IGNORE_CASE),
        Regex("""cmd\s+netpolicy\s+add\s+restrict-background\b""", RegexOption.IGNORE_CASE),
        Regex("""pm\s+suspend\b""", RegexOption.IGNORE_CASE),
        Regex("""am\s+set-inactive\s+\S+\s+true\b""", RegexOption.IGNORE_CASE),
        Regex("""cmd\s+deviceidle\s+whitelist\s+-\S+""", RegexOption.IGNORE_CASE)
    )

    /** 会"禁用/停用/隐藏"某个包的语句。 */
    private val packageDisableRegex = Regex(
        """\b(pm|cmd\s+package)\s+(disable-user|disable|hide|suspend)\b([^;]*)""",
        RegexOption.IGNORE_CASE
    )

    /** 会强制停止某个包的语句。 */
    private val forceStopRegex = Regex(
        """\bam\s+force-stop\s+(\S+)""",
        RegexOption.IGNORE_CASE
    )

    /** 一次命令检查的结果。 */
    data class SanitizeResult(
        /** 过滤后真正可以执行的命令；空串表示整条都被拦下了 */
        val command: String,
        /** 被拦下的片段（原文），界面要如实展示 */
        val blocked: List<String>,
        /** 每条被拦下的原因（与 [blocked] 一一对应） */
        val reasons: List<String>
    ) {
        val hasBlocked: Boolean get() = blocked.isNotEmpty()
    }

    /**
     * 检查并过滤一条由 `;` 串联的命令。
     *
     * 只摘掉命中的那一段，不放弃整条命令；被摘掉的内容与原因一并回传，
     * 由调用方写进执行结果里（用户能看到"这条命令跳过了什么、为什么"）。
     */
    fun sanitize(command: String): SanitizeResult {
        if (command.isBlank()) return SanitizeResult(command, emptyList(), emptyList())
        val segments = command.split(';')
        val kept = ArrayList<String>(segments.size)
        val blocked = ArrayList<String>()
        val reasons = ArrayList<String>()

        for (raw in segments) {
            val seg = raw.trim()
            if (seg.isEmpty()) continue
            val reason = blockReason(seg)
            if (reason == null) {
                kept.add(seg)
            } else {
                blocked.add(seg)
                reasons.add(reason)
            }
        }
        return SanitizeResult(kept.joinToString("; "), blocked, reasons)
    }

    /** 返回非 null 表示这条语句必须被拦下，字符串是给用户看的原因。 */
    private fun blockReason(segment: String): String? {
        // 1) 全局后台/待机策略
        forbiddenGlobalSettings.forEach { re ->
            if (re.containsMatchIn(segment)) {
                return "该语句会修改整机的后台运行策略，可能让系统设置里的后台/电池选项失效，已跳过"
            }
        }
        // 2) 禁用受保护的包
        val disable = packageDisableRegex.find(segment)
        if (disable != null) {
            val pkgs = packagesIn(disable.groupValues.getOrNull(3).orEmpty())
            val hit = pkgs.firstOrNull { isProtectedPackage(it) }
            if (hit != null) {
                return "$hit 是系统/厂商的后台与权限管理组件，禁用会让系统设置里的相关入口消失，已跳过"
            }
            if (pkgs.isEmpty()) {
                return "该语句会禁用系统组件但无法确认目标包名，出于安全已跳过"
            }
        }
        // 3) 强停受保护的包
        val forceStop = forceStopRegex.find(segment)
        if (forceStop != null) {
            val pkg = forceStop.groupValues.getOrNull(1).orEmpty().trim()
            if (isProtectedPackage(pkg)) {
                return "$pkg 是系统/厂商的关键组件，强制停止可能影响来电、通知、权限管理等，已跳过"
            }
        }
        return null
    }

    /** 从 `pm disable-user --user 0 com.a --user 0 com.b` 这类片段里取出包名。 */
    private fun packagesIn(tail: String): List<String> {
        return Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+""")
            .findAll(tail)
            .map { it.value }
            .filter { it.count { c -> c == '.' } >= 1 }
            .toList()
    }

    /**
     * 供"还原系统默认设置"复用：本应用可能禁用过的组件清单（用于重新启用）。
     * 只列遥测/日志/广告这类**禁用后不影响系统功能**的组件，不包含受保护的后台/权限管理组件。
     */
    val restorableDisabledPackages: List<String> = listOf(
        "com.miui.analytics", "com.xiaomi.mipicks", "com.miui.msa.global", "com.xiaomi.metoknlp",
        "com.xiaomi.joyose",
        "com.oppo.logkit", "com.coloros.logkit", "com.oppo.usageassist",
        "com.vivo.abe", "com.vivo.daemonService",
        "com.samsung.android.dqagent", "com.sec.android.diagmonagent", "com.samsung.android.rubin.app",
        "com.samsung.android.game.gos",
        "com.meizu.mstore", "com.meizu.flyme.update",
        "com.huawei.bd", "com.huawei.android.hwouc", "com.huawei.android.microkernel",
        "com.huawei.android.pushagent"
    )
}
