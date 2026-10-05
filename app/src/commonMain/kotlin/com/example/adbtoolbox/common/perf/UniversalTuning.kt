package com.example.adbtoolbox.common.perf

/**
 * 全机型通用深度性能项 + 本机适用性判断。
 *
 * 与 [BrandDatabase.genericItems] 的分工：
 * - `genericItems` 是早期的 AOSP 基础项（9 条，偏"一键加速"）；
 * - 本文件是后补的**全机型通用**项，覆盖后台唤醒、CPU 调度、内存、存储/IO、
 *   渲染、网络与待机；全部为真实可执行的 shell 接口（settings / cmd / am / pm /
 *   sysfs / procfs），每条都能回读状态，可逆的都给恢复命令。
 *
 * 诚实性约束（与 HuaweiPerf 同一标准）：
 * 1. 命令链末尾一律追加 `echo "键=$(...)"` 状态回读，接口不存在时输出为空或报错，
 *    执行结果会如实显示失败，不伪装成功；
 * 2. 拿不准是否所有 ROM 都有的接口：`defaultSelected = false`、风险等级上调，
 *    并在 [PerfItem.uncertain] 上标记，便于界面与汇报明确区分；
 * 3. 不写任何会毁设备的项（不碰"清空数据 / 禁用系统 UI / 禁用输入法 / 禁用桌面"）。
 *
 * 接入方式：由 [BrandDatabase.itemsFor] 合并进通用指令集，因此现有的
 * PerformanceBoostScreen 与 PhoneInspectorScreen 不改代码即可看到全部新项。
 */
object UniversalTuning {

    // ---------------- 权限等级（与 PerfItem.requiresPermission 一致） ----------------
    const val PERM_NONE = "none"
    const val PERM_SHIZUKU = "shizuku"
    const val PERM_ROOT = "root"

    // ---------------- 风险等级 ----------------
    const val RISK_SAFE = "safe"
    const val RISK_CAUTION = "caution"
    const val RISK_RISKY = "risky"

    // ---------------- 分类（与 BrandDatabase.categories 的 id 对应） ----------------
    const val CAT_BACKGROUND = "background"
    const val CAT_ANIMATION = "animation"
    const val CAT_CPU = "cpu"
    const val CAT_MEMORY = "memory"
    const val CAT_POWER = "power"
    const val CAT_NETWORK = "network"
    const val CAT_STORAGE = "storage"
    const val CAT_DISPLAY = "display"

    // ---------------- 适用性结论 ----------------
    const val STATE_APPLICABLE = "applicable"
    const val STATE_NEEDS_SHIZUKU = "needs_shizuku"
    const val STATE_NEEDS_ROOT = "needs_root"
    const val STATE_SDK_NEWER = "sdk_newer"
    const val STATE_SDK_OLDER = "sdk_older"
    const val STATE_SOC_MISMATCH = "soc_mismatch"
    const val STATE_BRAND_ONLY = "brand_only"
    const val STATE_UNKNOWN = "unknown"

    /** 厂商 id，用于判断"哪些项只对某家 SoC 有意义"。 */
    const val VENDOR_QUALCOMM = "qualcomm"
    const val VENDOR_MEDIATEK = "mediatek"
    const val VENDOR_SAMSUNG = "samsung"
    const val VENDOR_GOOGLE = "google"
    const val VENDOR_HISILICON = "hisilicon"
    const val VENDOR_UNISOC = "unisoc"
    const val VENDOR_OTHER = "other"

    /**
     * 构造一条全机型通用项：命令末尾自动追加唯一完成标记，避免手写标记不一致。
     * 命令正文里已经带了状态回读，标记只用于判定"命令链是否跑到最后一条"。
     */
    private fun u(
        id: String,
        command: String,
        verifyCommand: String? = null,
        verifyExpect: String? = null,
        rollbackCommand: String? = null,
        requiresPermission: String = PERM_SHIZUKU,
        risk: String = RISK_SAFE,
        defaultSelected: Boolean = false,
        isDisableAction: Boolean = false,
        minSdk: Int = 0,
        maxSdk: Int = 0,
        socVendor: String? = null,
        brandOnly: String? = null,
        uncertain: Boolean = false,
        timeoutMs: Int = 20000,
        order: Int
    ): PerfItem = PerfItem(
        id = id,
        nameKey = "perf_item_$id",
        descKey = "perf_item_${id}_desc",
        command = command.trimEnd().trimEnd(';').trimEnd() + "; echo \"${id}_MARKER_DONE\"",
        category = categoryOf(id),
        requiresPermission = requiresPermission,
        risk = risk,
        defaultSelected = defaultSelected,
        verifyCommand = verifyCommand,
        verifyExpect = verifyExpect,
        toggleOffCommand = rollbackCommand,
        isDisableAction = isDisableAction,
        minSdk = minSdk,
        maxSdk = maxSdk,
        socVendor = socVendor,
        brandOnly = brandOnly,
        uncertain = uncertain,
        timeoutMs = timeoutMs,
        order = order
    )

    /** id 前缀 → 分类，避免逐条手写 category 时写错。 */
    private fun categoryOf(id: String): String = when {
        id.startsWith("bg_") -> CAT_BACKGROUND
        id.startsWith("cpu_") -> CAT_CPU
        id.startsWith("mem_") -> CAT_MEMORY
        id.startsWith("power_") -> CAT_POWER
        id.startsWith("storage_") -> CAT_STORAGE
        id.startsWith("render_") -> CAT_ANIMATION
        id.startsWith("net_") -> CAT_NETWORK
        id.startsWith("display_") -> CAT_DISPLAY
        else -> CAT_CPU
    }

    // ============================================================ 指令集

    /**
     * 全机型通用性能项（26 条：`bg_*` 9 条、`cpu_*` 5 条、`mem_*` 1 条、`power_*` 1 条、
     * `storage_*` 4 条、`render_*` 3 条、`net_*` 3 条；其中需要 Root 的 7 条）。
     * 条数由本文件真实构造，界面一律从 [BrandDatabase.universalItems] 取，不写死。
     *
     * 执行的顺序语义：后台/唤醒类先跑（order 140~156），
     * 再 CPU/IO（165~173）、内存（182）、电源（185）、存储（188~194）、
     * 渲染（196~198）、网络（200~204）。
     *
     * 已明确剔除、**故意不实现**的高风险项（避免毁设备）：
     * - 批量关闭 `/sys/devices/platform/<设备名>/power/wakeup`：会把电源键、USB 等唤醒源一起关掉，
     *   设备可能无法唤醒，恢复也只能靠重刷/长按电源硬重启；
     * - 直接写 `msm_performance` 全参数：没有可靠的逐项回读路径，无法给出可执行的恢复命令。
     * 宁可少两条，也不放无法安全回滚的项进来。
     */
    val items: List<PerfItem> = listOf(

        // ---------------------------------------------------- 后台与唤醒
        u(
            id = "bg_kill_background_all",
            order = 140,
            command = "am kill-all 2>&1 | head -n2; " +
                    "echo \"free=$(dumpsys meminfo 2>/dev/null | grep -m1 'Free RAM:' | tr -d ' ')\"",
            verifyCommand = "cmd activity help 2>&1 | grep -m1 kill-all",
            verifyExpect = "kill-all",
            rollbackCommand = null,
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            minSdk = 14,
            timeoutMs = 45000
        ),
        u(
            id = "bg_trim_all_caches",
            order = 142,
            command = "pm trim-caches 268435456000 2>&1 | head -n2; " +
                    "echo \"data=$(df /data 2>/dev/null | tail -n1 | tr -s ' ')\"",
            verifyCommand = "pm help 2>&1 | grep -m1 trim-caches",
            verifyExpect = "trim-caches",
            rollbackCommand = null,
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            minSdk = 14,
            timeoutMs = 60000
        ),
        u(
            id = "bg_standby_bucket_rare",
            order = 144,
            command = "for p in com.android.chrome com.android.vending com.facebook.katana " +
                    "com.tencent.mm com.tencent.mobileqq com.taobao.taobao com.eg.android.AlipayGphone " +
                    "com.ss.android.ugc.aweme com.sina.weibo com.netease.cloudmusic com.ximalaya.ting.android " +
                    "com.zhihu.android com.baidu.BaiduMap com.autonavi.minimap com.smile.gifmaker; do " +
                    "pm list packages 2>/dev/null | grep -q \"package:${'$'}p\" && " +
                    "am set-standby-bucket \"${'$'}p\" rare 2>/dev/null; done; " +
                    "echo \"mm=$(am get-standby-bucket com.tencent.mm 2>/dev/null)\"",
            verifyCommand = "am help 2>&1 | grep -m1 set-standby-bucket",
            verifyExpect = "set-standby-bucket",
            rollbackCommand = "for p in com.tencent.mm com.taobao.taobao com.ss.android.ugc.aweme; do " +
                    "am set-standby-bucket \"${'$'}p\" active 2>/dev/null; done",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            minSdk = 28,
            timeoutMs = 45000
        ),
        u(
            id = "bg_standby_bucket_extreme",
            order = 146,
            command = "for p in com.tencent.mm com.taobao.taobao com.ss.android.ugc.aweme " +
                    "com.eg.android.AlipayGphone com.android.chrome; do " +
                    "pm list packages 2>/dev/null | grep -q \"package:${'$'}p\" && " +
                    "am set-standby-bucket \"${'$'}p\" restricted 2>/dev/null; done; " +
                    "echo \"mm=$(am get-standby-bucket com.tencent.mm 2>/dev/null)\"",
            verifyCommand = "am help 2>&1 | grep -m1 set-standby-bucket",
            verifyExpect = "set-standby-bucket",
            rollbackCommand = "for p in com.tencent.mm com.taobao.taobao com.ss.android.ugc.aweme " +
                    "com.eg.android.AlipayGphone com.android.chrome; do " +
                    "am set-standby-bucket \"${'$'}p\" active 2>/dev/null; done",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            isDisableAction = true,
            minSdk = 28,
            uncertain = true,
            timeoutMs = 45000
        ),
        u(
            id = "bg_restrict_background_appops",
            order = 148,
            command = "pm list packages -3 2>/dev/null | sed -n 's/^package://p' | head -n 60 | " +
                    "while read -r p; do cmd appops set \"${'$'}p\" RUN_IN_BACKGROUND deny 2>/dev/null; " +
                    "cmd appops set \"${'$'}p\" RUN_ANY_IN_BACKGROUND deny 2>/dev/null; done; " +
                    "echo \"mm=$(cmd appops get com.tencent.mm RUN_ANY_IN_BACKGROUND 2>/dev/null | tr -s ' ')\"",
            verifyCommand = "cmd appops help 2>&1 | grep -m1 RUN_ANY_IN_BACKGROUND",
            verifyExpect = "RUN_ANY_IN_BACKGROUND",
            rollbackCommand = "pm list packages -3 2>/dev/null | sed -n 's/^package://p' | head -n 60 | " +
                    "while read -r p; do cmd appops set \"${'$'}p\" RUN_IN_BACKGROUND allow 2>/dev/null; " +
                    "cmd appops set \"${'$'}p\" RUN_ANY_IN_BACKGROUND allow 2>/dev/null; done",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            // 保守：会明显延迟第三方应用通知，交给用户显式勾选，不放进默认一键加速。
            defaultSelected = false,
            isDisableAction = true,
            minSdk = 21,
            timeoutMs = 45000
        ),
        u(
            id = "bg_deviceidle_whitelist_core",
            order = 150,
            command = "for p in com.android.systemui com.android.phone com.android.bluetooth " +
                    "com.android.providers.telephony com.android.shell; do " +
                    "pm list packages 2>/dev/null | grep -q \"package:${'$'}p\" && " +
                    "cmd deviceidle whitelist \"+${'$'}p\" 2>/dev/null; done; " +
                    "echo \"wl=$(cmd deviceidle whitelist 2>/dev/null | head -n1)\"",
            verifyCommand = "cmd deviceidle help 2>&1 | grep -m1 whitelist",
            verifyExpect = "whitelist",
            rollbackCommand = "for p in com.android.shell; do cmd deviceidle whitelist \"-${'$'}p\" 2>/dev/null; done",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            minSdk = 23,
            timeoutMs = 25000
        ),
        u(
            id = "bg_deviceidle_unforce",
            order = 152,
            command = "cmd deviceidle unforce 2>&1 | head -n2; " +
                    "dumpsys deviceidle 2>/dev/null | grep -m1 -i 'mState='",
            verifyCommand = "cmd deviceidle help 2>&1 | grep -m1 unforce",
            verifyExpect = "unforce",
            rollbackCommand = null,
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            minSdk = 23,
            timeoutMs = 20000
        ),
        u(
            id = "bg_deviceidle_disable",
            order = 154,
            command = "cmd deviceidle disable 2>&1 | head -n2; " +
                    "dumpsys deviceidle 2>/dev/null | grep -m1 -i 'mState='",
            verifyCommand = "cmd deviceidle help 2>&1 | grep -m1 disable",
            verifyExpect = "disable",
            rollbackCommand = "cmd deviceidle enable 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            isDisableAction = true,
            minSdk = 23,
            uncertain = true,
            timeoutMs = 20000
        ),
        u(
            id = "bg_oom_adj_policy",
            order = 156,
            command = "echo 0 > /proc/sys/vm/oom_kill_allocating_task 2>/dev/null; " +
                    "echo 1 > /proc/sys/vm/oom_dump_tasks 2>/dev/null; " +
                    "echo \"kill_alloc=$(cat /proc/sys/vm/oom_kill_allocating_task 2>/dev/null) " +
                    "dump=$(cat /proc/sys/vm/oom_dump_tasks 2>/dev/null)\"",
            verifyCommand = "ls /proc/sys/vm/oom_kill_allocating_task /proc/sys/vm/oom_dump_tasks 2>&1",
            verifyExpect = "oom_kill_allocating_task",
            rollbackCommand = "echo 0 > /proc/sys/vm/oom_dump_tasks 2>/dev/null",
            requiresPermission = PERM_ROOT,
            risk = RISK_CAUTION,
            defaultSelected = false,
            uncertain = true,
            timeoutMs = 20000
        ),

        // ---------------------------------------------------- CPU / 调度
        u(
            id = "cpu_fixed_perf_mode",
            order = 165,
            command = "cmd power set-fixed-performance-mode-enabled true 2>&1 | head -n3; " +
                    "dumpsys power 2>/dev/null | grep -m1 -i 'mFixedPerformanceMode'",
            verifyCommand = "cmd power help 2>&1 | grep -m1 set-fixed-performance-mode-enabled",
            verifyExpect = "fixed-performance-mode-enabled",
            rollbackCommand = "cmd power set-fixed-performance-mode-enabled false 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            minSdk = 29,
            timeoutMs = 25000
        ),
        u(
            id = "cpu_governor_performance",
            order = 167,
            command = "[ -f /data/local/tmp/adt_governor_backup ] || " +
                    "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do " +
                    "[ -f \"${'$'}g\" ] && echo \"${'$'}g ${'$'}(cat \"${'$'}g\")\" >> /data/local/tmp/adt_governor_backup; done; " +
                    "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do " +
                    "echo performance > \"${'$'}g\" 2>/dev/null; done; " +
                    "echo \"gov0=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null)\"",
            verifyCommand = "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null",
            verifyExpect = null,
            rollbackCommand = "while read -r p v; do [ -n \"${'$'}v\" ] && [ -w \"${'$'}p\" ] && " +
                    "echo \"${'$'}v\" > \"${'$'}p\" 2>/dev/null; done < /data/local/tmp/adt_governor_backup; " +
                    "echo \"gov0=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null)\"",
            requiresPermission = PERM_ROOT,
            risk = RISK_RISKY,
            defaultSelected = false,
            timeoutMs = 30000
        ),
        u(
            id = "cpu_min_freq_boost",
            order = 169,
            command = "[ -f /data/local/tmp/adt_minfreq_backup ] || " +
                    "for p in /sys/devices/system/cpu/cpu*/cpufreq; do " +
                    "[ -f \"${'$'}p/scaling_min_freq\" ] && echo \"${'$'}p ${'$'}(cat \"${'$'}p/scaling_min_freq\")\" " +
                    ">> /data/local/tmp/adt_minfreq_backup; done; " +
                    "for p in /sys/devices/system/cpu/cpu*/cpufreq; do " +
                    "[ -f \"${'$'}p/cpuinfo_max_freq\" ] && " +
                    "echo \"${'$'}(cat \"${'$'}p/cpuinfo_max_freq\")\" > \"${'$'}p/scaling_min_freq\" 2>/dev/null; done; " +
                    "echo \"min0=$(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq 2>/dev/null) " +
                    "max0=$(cat /sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq 2>/dev/null)\"",
            verifyCommand = "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq 2>/dev/null",
            verifyExpect = null,
            rollbackCommand = "while read -r p v; do [ -n \"${'$'}v\" ] && [ -w \"${'$'}p/scaling_min_freq\" ] && " +
                    "echo \"${'$'}v\" > \"${'$'}p/scaling_min_freq\" 2>/dev/null; done < /data/local/tmp/adt_minfreq_backup",
            requiresPermission = PERM_ROOT,
            risk = RISK_RISKY,
            defaultSelected = false,
            timeoutMs = 30000
        ),
        u(
            id = "cpu_schedutil_rate_limit",
            order = 171,
            command = "[ -f /data/local/tmp/adt_ratelimit_backup ] || " +
                    "for p in /sys/devices/system/cpu/cpufreq/*/rate_limit_us; do " +
                    "[ -f \"${'$'}p\" ] && echo \"${'$'}p ${'$'}(cat \"${'$'}p\")\" >> /data/local/tmp/adt_ratelimit_backup; " +
                    "done; for p in /sys/devices/system/cpu/cpufreq/*/rate_limit_us; do " +
                    "echo 2000 > \"${'$'}p\" 2>/dev/null; done; " +
                    "echo \"rl=$(cat /sys/devices/system/cpu/cpufreq/*/rate_limit_us 2>/dev/null | tr '\\n' ',')\"",
            verifyCommand = "ls /sys/devices/system/cpu/cpufreq/*/rate_limit_us 2>/dev/null | head -n1",
            verifyExpect = "rate_limit_us",
            rollbackCommand = "while read -r p v; do [ -n \"${'$'}v\" ] && [ -w \"${'$'}p\" ] && " +
                    "echo \"${'$'}v\" > \"${'$'}p\" 2>/dev/null; done < /data/local/tmp/adt_ratelimit_backup",
            requiresPermission = PERM_ROOT,
            risk = RISK_CAUTION,
            defaultSelected = false,
            uncertain = true,
            timeoutMs = 20000
        ),
        u(
            id = "cpu_io_scheduler_none",
            order = 173,
            command = "[ -f /data/local/tmp/adt_io_sched_backup ] || " +
                    "for q in /sys/block/sd*/queue/scheduler /sys/block/mmcblk*/queue/scheduler " +
                    "/sys/block/dm-*/queue/scheduler /sys/block/vda/queue/scheduler; do " +
                    "v=${'$'}(sed -n 's/.*\\[\\(.*\\)\\].*/\\1/p' \"${'$'}q\" 2>/dev/null); " +
                    "[ -n \"${'$'}v\" ] && echo \"${'$'}q ${'$'}v\" >> /data/local/tmp/adt_io_sched_backup; done; " +
                    "for q in /sys/block/sd*/queue/scheduler /sys/block/mmcblk*/queue/scheduler " +
                    "/sys/block/dm-*/queue/scheduler /sys/block/vda/queue/scheduler; do " +
                    "echo none > \"${'$'}q\" 2>/dev/null; done; " +
                    "echo \"sched=$(cat /sys/block/sda/queue/scheduler 2>/dev/null)" +
                    "${'$'}(cat /sys/block/mmcblk0/queue/scheduler 2>/dev/null)" +
                    "${'$'}(cat /sys/block/vda/queue/scheduler 2>/dev/null)\"",
            verifyCommand = "ls -d /sys/block/sd* /sys/block/mmcblk* /sys/block/vda 2>/dev/null | head -n2",
            verifyExpect = null,
            rollbackCommand = "while read -r q v; do [ -n \"${'$'}v\" ] && [ -w \"${'$'}q\" ] && " +
                    "echo \"${'$'}v\" > \"${'$'}q\" 2>/dev/null; done < /data/local/tmp/adt_io_sched_backup",
            requiresPermission = PERM_ROOT,
            risk = RISK_CAUTION,
            defaultSelected = false,
            uncertain = true,
            timeoutMs = 20000
        ),

        // ---------------------------------------------------- 内存
        u(
            id = "mem_swappiness_low",
            order = 182,
            command = "b=${'$'}(cat /proc/sys/vm/swappiness 2>/dev/null); " +
                    "[ -n \"${'$'}b\" ] && [ ! -f /data/local/tmp/adt_swappiness_backup ] && " +
                    "echo \"${'$'}b\" > /data/local/tmp/adt_swappiness_backup; " +
                    "echo 10 > /proc/sys/vm/swappiness 2>/dev/null; " +
                    "echo 150 > /proc/sys/vm/page-cluster 2>/dev/null; " +
                    "echo \"swappiness=$(cat /proc/sys/vm/swappiness 2>/dev/null) " +
                    "page_cluster=$(cat /proc/sys/vm/page-cluster 2>/dev/null)\"",
            verifyCommand = "cat /proc/sys/vm/swappiness 2>/dev/null",
            verifyExpect = null,
            rollbackCommand = "v=${'$'}(cat /data/local/tmp/adt_swappiness_backup 2>/dev/null); " +
                    "[ -n \"${'$'}v\" ] && echo \"${'$'}v\" > /proc/sys/vm/swappiness 2>/dev/null; " +
                    "echo 3 > /proc/sys/vm/page-cluster 2>/dev/null",
            requiresPermission = PERM_ROOT,
            risk = RISK_CAUTION,
            defaultSelected = false,
            timeoutMs = 20000
        ),

        // ---------------------------------------------------- 电源与待机
        u(
            id = "power_perf_profile",
            order = 185,
            command = "cmd power set-mode 0 2>&1 | head -n2; " +
                    "cmd power set-profile 0 2>&1 | head -n2; " +
                    "dumpsys power 2>/dev/null | grep -m1 -i 'mProfilePowerState\\|mMode='",
            verifyCommand = "cmd power help 2>&1 | grep -m1 set-mode",
            verifyExpect = "set-mode",
            rollbackCommand = "cmd power set-mode 1 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            minSdk = 21,
            uncertain = true,
            timeoutMs = 25000
        ),

        // ---------------------------------------------------- 存储 / IO
        u(
            id = "storage_bg_dexopt_now",
            order = 188,
            command = "cmd package bg-dexopt-job 2>&1 | head -n5",
            verifyCommand = "cmd package help 2>&1 | grep -m1 bg-dexopt-job",
            verifyExpect = "bg-dexopt-job",
            rollbackCommand = null,
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            minSdk = 24,
            timeoutMs = 600000
        ),
        u(
            id = "storage_compile_speed_profile",
            order = 190,
            command = "cmd package compile -m speed-profile -a 2>&1 | tail -n5",
            verifyCommand = "cmd package help 2>&1 | grep -m1 'compile '",
            verifyExpect = "compile",
            rollbackCommand = "cmd package compile --reset -a 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            minSdk = 24,
            timeoutMs = 900000
        ),
        u(
            id = "storage_trim_cached_packages",
            order = 192,
            command = "cmd package trim-caches 268435456000 2>&1 | head -n3; " +
                    "echo \"data=$(df /data 2>/dev/null | tail -n1 | tr -s ' ')\"",
            verifyCommand = "cmd package help 2>&1 | grep -m1 trim-caches",
            verifyExpect = "trim-caches",
            rollbackCommand = null,
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            minSdk = 26,
            uncertain = true,
            timeoutMs = 180000
        ),
        u(
            id = "storage_drop_old_logs",
            order = 194,
            command = "logcat -b all -c 2>/dev/null; " +
                    "rm -f /data/anr/*.txt /data/tombstones/tombstone_* 2>/dev/null; " +
                    "rm -f /data/system/dropbox/*.txt 2>/dev/null; " +
                    "echo \"anr=$(ls /data/anr 2>/dev/null | wc -l) tomb=$(ls /data/tombstones 2>/dev/null | wc -l)\"",
            verifyCommand = "ls -d /data/anr /data/tombstones 2>/dev/null | head -n2",
            verifyExpect = null,
            rollbackCommand = null,
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            minSdk = 14,
            timeoutMs = 40000
        ),

        // ---------------------------------------------------- 渲染
        u(
            id = "render_animator_scale_half",
            order = 196,
            command = "for k in window_animation_scale transition_animation_scale animator_duration_scale; do " +
                    "settings put global \"${'$'}k\" 0.5 2>/dev/null; done; " +
                    "echo \"anim=$(settings get global window_animation_scale 2>/dev/null)" +
                    "/${'$'}(settings get global transition_animation_scale 2>/dev/null)" +
                    "/${'$'}(settings get global animator_duration_scale 2>/dev/null)\"",
            verifyCommand = "settings list global 2>&1 | grep -m1 window_animation_scale",
            verifyExpect = "window_animation_scale",
            rollbackCommand = "for k in window_animation_scale transition_animation_scale animator_duration_scale; do " +
                    "settings put global \"${'$'}k\" 1 2>/dev/null; done",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            minSdk = 14,
            timeoutMs = 20000
        ),
        u(
            id = "render_animator_scale_off",
            order = 197,
            command = "for k in window_animation_scale transition_animation_scale animator_duration_scale; do " +
                    "settings put global \"${'$'}k\" 0 2>/dev/null; done; " +
                    "echo \"anim=$(settings get global window_animation_scale 2>/dev/null)" +
                    "/${'$'}(settings get global transition_animation_scale 2>/dev/null)" +
                    "/${'$'}(settings get global animator_duration_scale 2>/dev/null)\"",
            verifyCommand = "settings list global 2>&1 | grep -m1 animator_duration_scale",
            verifyExpect = "animator_duration_scale",
            rollbackCommand = "for k in window_animation_scale transition_animation_scale animator_duration_scale; do " +
                    "settings put global \"${'$'}k\" 1 2>/dev/null; done",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            isDisableAction = true,
            minSdk = 14,
            timeoutMs = 20000
        ),
        u(
            id = "render_force_gpu",
            order = 198,
            command = "settings put global force_gpu_rendering 1 2>/dev/null; " +
                    "setprop debug.hwui.renderer skiagl 2>/dev/null; " +
                    "echo \"gpu=$(settings get global force_gpu_rendering 2>/dev/null) " +
                    "renderer=$(getprop debug.hwui.renderer 2>/dev/null)\"",
            verifyCommand = "settings list global 2>&1 | grep -m1 force_gpu_rendering",
            verifyExpect = "force_gpu_rendering",
            rollbackCommand = "settings put global force_gpu_rendering 0 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            minSdk = 14,
            uncertain = true,
            timeoutMs = 20000
        ),

        // ---------------------------------------------------- 网络与待机
        u(
            id = "net_wifi_scan_always_off",
            order = 200,
            command = "settings put global wifi_scan_always_enabled 0 2>/dev/null; " +
                    "settings put global wifi_scan_throttle_enabled 0 2>/dev/null; " +
                    "echo \"scan=$(settings get global wifi_scan_always_enabled 2>/dev/null) " +
                    "throttle=$(settings get global wifi_scan_throttle_enabled 2>/dev/null)\"",
            verifyCommand = "settings list global 2>&1 | grep -m1 wifi_scan_always_enabled",
            verifyExpect = "wifi_scan_always_enabled",
            rollbackCommand = "settings put global wifi_scan_always_enabled 1 2>/dev/null; " +
                    "settings put global wifi_scan_throttle_enabled 1 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            minSdk = 18,
            timeoutMs = 20000
        ),
        u(
            id = "net_mobile_data_always_off",
            order = 202,
            command = "settings put global mobile_data_always_on 0 2>/dev/null; " +
                    "settings put global mobile_data_always_on_2 0 2>/dev/null; " +
                    "echo \"mdao=$(settings get global mobile_data_always_on 2>/dev/null)\"",
            verifyCommand = "settings list global 2>&1 | grep -m1 mobile_data_always_on",
            verifyExpect = "mobile_data_always_on",
            rollbackCommand = "settings put global mobile_data_always_on 1 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            isDisableAction = true,
            minSdk = 21,
            timeoutMs = 20000
        ),
        u(
            id = "net_tcp_receive_window",
            order = 204,
            command = "setprop net.tcp.default_init_rwnd 60 2>/dev/null; " +
                    "echo \"rwnd=$(getprop net.tcp.default_init_rwnd 2>/dev/null) " +
                    "buffs=$(getprop net.tcp.buffersize.default 2>/dev/null)\"",
            verifyCommand = "getprop net.tcp.default_init_rwnd 2>/dev/null; getprop net.tcp.buffersize.default 2>/dev/null",
            verifyExpect = null,
            rollbackCommand = "setprop net.tcp.default_init_rwnd 10 2>/dev/null",
            requiresPermission = PERM_ROOT,
            risk = RISK_CAUTION,
            defaultSelected = false,
            uncertain = true,
            timeoutMs = 20000
        )
    ).sortedBy { it.order }

    // ============================================================ 适用性判断

    /**
     * 一条"本机是否适用"的结论。
     *
     * [detail] 是给人看的事实说明（英文键 + 真实读数），[reasonKey] 留给界面做多语言。
     */
    data class ApplicabilityNote(
        /** 结论作用的对象：perf item id 或 "device" / "privilege" / "brand" / "soc" */
        val scope: String,
        /** 结论类型：applicable / needs_shizuku / needs_root / sdk_newer / sdk_older / soc_mismatch / brand_only / unknown */
        val state: String,
        /** 文案 key（前缀 perf_apply_），界面可直接 AppStrings.get */
        val reasonKey: String,
        /** 事实描述：真实读数拼接，不猜测 */
        val detail: String,
        /** 与哪条指令有关（可选） */
        val itemId: String? = null
    )

    /**
     * 依据 [PerfRunner.PerfDeviceInfo] 产出"本机适用性判断"。
     *
     * 规则（全部基于已经真实读到的字段，任一项读不到就按 unknown 如实说明）：
     * 1. 品牌：识别到 [BrandDatabase.isRecognized] 之外的具体品牌 → 该品牌专属项可用；
     *    识别不出（generic）→ 只有通用项可用，品牌专属项会在界面标记为不适用；
     * 2. SoC：高通（qualcomm/qcom/msm/snapdragon）、联发科（mediatek/mtk/dimensity/helio）、
     *    三星（exynos/samsung）、海思（hisilicon/kirin）、紫光展锐（unisoc/sprd）分别只让对应厂商的项可用；
     *    厂商读不到（other/空）→ 所有带厂商限定的项一律按"无法确认"处理，不假装可用；
     * 3. SDK：低于 [PerfItem.minSdk] 的项 → sdk_older；高于 [PerfItem.maxSdk]（非 0 时）→ sdk_newer；
     * 4. 权限：需要 root 但无 root → needs_root；需要 shizuku 但三条提权通道都没有 → needs_shizuku；
     *    Shizuku 项在只有 root 时依然可用（execCommand 会回退到 su），这一点如实写进 detail；
     * 5. 缺失信息：SDK / SoC / 品牌任一为未知，都不推断，直接给出 unknown 结论。
     */
    fun applicability(
        info: PerfRunner.PerfDeviceInfo,
        items: List<PerfItem> = BrandDatabase.itemsFor(info.brandId, includeBrandSpecific = false)
    ): List<ApplicabilityNote> {
        val notes = ArrayList<ApplicabilityNote>()

        // ---- 1. 品牌 ----
        val brandRecognized = BrandDatabase.isRecognized(info.brandId)
        notes += ApplicabilityNote(
            scope = "brand",
            state = if (brandRecognized) STATE_APPLICABLE else STATE_UNKNOWN,
            reasonKey = if (brandRecognized) "perf_apply_brand_detected" else "perf_apply_brand_unknown",
            detail = "brandId=${info.brandId} raw=${info.brandRaw} rom=${info.romName} " +
                    "brandSpecific=${if (info.hasBrandSpecific) "yes" else "no"}"
        )

        // ---- 2. SoC ----
        val vendor = normalizeVendor(info.socVendor)
        notes += ApplicabilityNote(
            scope = "soc",
            state = if (vendor == VENDOR_OTHER) STATE_UNKNOWN else STATE_APPLICABLE,
            reasonKey = if (vendor == VENDOR_OTHER) "perf_apply_soc_unknown" else "perf_apply_soc_detected",
            detail = "soc=${info.cpuModel} vendor=${info.socVendor} normalized=${vendor}"
        )

        // ---- 3. 权限 ----
        notes += ApplicabilityNote(
            scope = "privilege",
            state = when {
                info.hasRoot -> STATE_APPLICABLE
                info.hasShizuku -> STATE_APPLICABLE
                info.isDhizukuActive -> STATE_NEEDS_ROOT
                else -> STATE_NEEDS_SHIZUKU
            },
            reasonKey = when {
                info.hasRoot -> "perf_apply_priv_root"
                info.hasShizuku -> "perf_apply_priv_shizuku"
                info.isDhizukuActive -> "perf_apply_priv_dhizuku_only"
                else -> "perf_apply_priv_none"
            },
            detail = "shizuku=${info.hasShizuku} root=${info.hasRoot} dhizuku=${info.isDhizukuActive}"
        )

        // ---- 4. 逐项 ----
        items.forEach { item ->
            val minSdk = item.minSdk
            val maxSdk = item.maxSdk
            val vendorLimit = item.socVendor
            val brandLimit = item.brandOnly

            val note = when {
                minSdk > 0 && info.sdk <= 0 -> ApplicabilityNote(
                    item.id, STATE_UNKNOWN, "perf_apply_sdk_unknown",
                    "sdk=unknown required>=${minSdk}", item.id
                )
                minSdk > 0 && info.sdk < minSdk -> ApplicabilityNote(
                    item.id, STATE_SDK_OLDER, "perf_apply_sdk_too_old",
                    "sdk=${info.sdk} required>=${minSdk}", item.id
                )
                maxSdk > 0 && info.sdk > maxSdk -> ApplicabilityNote(
                    item.id, STATE_SDK_NEWER, "perf_apply_sdk_too_new",
                    "sdk=${info.sdk} supported<=${maxSdk}", item.id
                )
                brandLimit != null && brandLimit != info.brandId -> ApplicabilityNote(
                    item.id, STATE_BRAND_ONLY, "perf_apply_brand_only",
                    "brandOnly=${brandLimit} local=${info.brandId}", item.id
                )
                vendorLimit != null && vendor != vendorLimit -> ApplicabilityNote(
                    item.id, STATE_SOC_MISMATCH, "perf_apply_soc_mismatch",
                    "socRequired=${vendorLimit} local=${info.socVendor}", item.id
                )
                item.requiresPermission == PERM_ROOT && !info.hasRoot -> ApplicabilityNote(
                    item.id, STATE_NEEDS_ROOT, "perf_apply_need_root",
                    "root=not available", item.id
                )
                item.requiresPermission == PERM_SHIZUKU &&
                        !(info.hasShizuku || info.hasRoot || info.isDhizukuActive) -> ApplicabilityNote(
                    item.id, STATE_NEEDS_SHIZUKU, "perf_apply_need_shizuku",
                    "shizuku=${info.hasShizuku} root=${info.hasRoot} dhizuku=${info.isDhizukuActive}", item.id
                )
                else -> ApplicabilityNote(
                    item.id, STATE_APPLICABLE,
                    if (item.uncertain) "perf_apply_uncertain" else "perf_apply_ok",
                    "perm=${item.requiresPermission} sdk=${info.sdk} risk=${item.risk} uncertain=${item.uncertain}",
                    item.id
                )
            }
            notes += note
        }
        return notes
    }

    /** 把 `ro.board.platform` / `getCpuVendor()` 的原始串归一化成厂商 id。 */
    fun normalizeVendor(raw: String): String {
        val v = raw.lowercase().trim()
        return when {
            v.isBlank() -> VENDOR_OTHER
            v.contains("qualcomm") || v.contains("qcom") || v.contains("snapdragon") ||
                    v.contains("msm") || v.contains("sm8") || v.contains("sm7") -> VENDOR_QUALCOMM
            v.contains("mediatek") || v.contains("mtk") || v.contains("dimensity") ||
                    v.contains("helio") || v.startsWith("mt") -> VENDOR_MEDIATEK
            v.contains("exynos") || v.contains("samsung") || v.contains("s5e") -> VENDOR_SAMSUNG
            v.contains("hisilicon") || v.contains("kirin") || v.contains("hi3") -> VENDOR_HISILICON
            v.contains("unisoc") || v.contains("sprd") || v.contains("sc9") -> VENDOR_UNISOC
            v.contains("google") || v.contains("tensor") -> VENDOR_GOOGLE
            else -> VENDOR_OTHER
        }
    }

    /** 当前设备上真正可执行的项（权限 + SDK + 品牌 + SoC 四条都满足）。 */
    fun applicableItems(
        info: PerfRunner.PerfDeviceInfo,
        items: List<PerfItem> = BrandDatabase.itemsFor(info.brandId, includeBrandSpecific = true)
    ): List<PerfItem> {
        val states = applicability(info, items).associateBy { it.itemId ?: it.scope }
        return items.filter { states[it.id]?.state == STATE_APPLICABLE }
    }

    /** 当前设备上因缺少提权而暂时不可用的项，附带原因（供界面明确列出"哪些需要提权"）。 */
    fun blockedByPermission(info: PerfRunner.PerfDeviceInfo): List<Pair<PerfItem, ApplicabilityNote>> {
        val items = BrandDatabase.itemsFor(info.brandId, includeBrandSpecific = true)
        val notes = applicability(info, items)
        return items.mapNotNull { item ->
            val note = notes.firstOrNull { it.itemId == item.id } ?: return@mapNotNull null
            if (note.state == STATE_NEEDS_ROOT || note.state == STATE_NEEDS_SHIZUKU) item to note else null
        }
    }

    /** 汇总：一句话结论，内容全部来自真实读数。 */
    fun summary(info: PerfRunner.PerfDeviceInfo): String {
        val items = BrandDatabase.itemsFor(info.brandId, includeBrandSpecific = true)
        val notes = applicability(info, items)
        val applicable = notes.count { it.itemId != null && it.state == STATE_APPLICABLE }
        val needRoot = notes.count { it.state == STATE_NEEDS_ROOT }
        val needShizuku = notes.count { it.state == STATE_NEEDS_SHIZUKU }
        val sdkFiltered = notes.count { it.state == STATE_SDK_OLDER || it.state == STATE_SDK_NEWER }
        val socFiltered = notes.count { it.state == STATE_SOC_MISMATCH }
        val uncertain = items.count { it.uncertain }
        return "brand=${info.brandId}(${info.brandRaw}) soc=${info.cpuModel}/${normalizeVendor(info.socVendor)} " +
                "sdk=${info.sdk} shizuku=${info.hasShizuku} root=${info.hasRoot} | " +
                "total=${items.size} applicable=${applicable} " +
                "needRoot=${needRoot} needShizuku=${needShizuku} " +
                "sdkFiltered=${sdkFiltered} socFiltered=${socFiltered} uncertain=${uncertain}"
    }
}
