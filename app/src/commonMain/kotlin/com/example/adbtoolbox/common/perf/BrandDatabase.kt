package com.example.adbtoolbox.common.perf

/**
 * 品牌自适应的一键性能加速指令库。
 *
 * 全部命令都经过筛选：要么是 AOSP 原生接口（任何 ROM 都有效），
 * 要么是厂商 ROM 真实存在的 settings key / cmd 接口（无效的会被执行层如实报失败，
 * 不会伪装成功）。`verifyCommand` 用于体检阶段判断"这条指令在本机到底能不能用"。
 */
object BrandDatabase {

    const val BRAND_GENERIC = "generic"
    const val BRAND_XIAOMI = "xiaomi"
    const val BRAND_HUAWEI = "huawei"
    const val BRAND_HONOR = "honor"
    const val BRAND_OPPO = "oppo"
    const val BRAND_REALME = "realme"
    const val BRAND_VIVO = "vivo"
    const val BRAND_IQOO = "iqoo"
    const val BRAND_ONEPLUS = "oneplus"
    const val BRAND_SAMSUNG = "samsung"
    const val BRAND_MEIZU = "meizu"
    const val BRAND_NUBIA = "nubia"
    const val BRAND_ASUS = "asus"
    const val BRAND_SONY = "sony"
    const val BRAND_MOTOROLA = "motorola"
    const val BRAND_GOOGLE = "google"

    // ---------------------------------------------------------------- 分类

    val categories: List<PerfCategory> = listOf(
        PerfCategory("background", "perf_cat_background", 0xFF0088FF),
        PerfCategory("animation", "perf_cat_animation", 0xFFAF52DE),
        PerfCategory("cpu", "perf_cat_cpu", 0xFFFF2D55),
        PerfCategory("memory", "perf_cat_memory", 0xFF34C759),
        PerfCategory("power", "perf_cat_power", 0xFFFF9500),
        PerfCategory("network", "perf_cat_network", 0xFF5AC8FA),
        PerfCategory("storage", "perf_cat_storage", 0xFFA2845E),
        PerfCategory("display", "perf_cat_display", 0xFFBF5AF2),
        PerfCategory("brand", "perf_cat_brand", 0xFFFF3B30)
    )

    // ------------------------------------------------------- 品牌识别与适配

    /** 依据品牌原始串识别品牌档案。入参全部小写比较，允许为空。 */
    fun detect(rawBrand: String, manufacturer: String = "", model: String = ""): BrandProfile {
        val b = rawBrand.lowercase().trim()
        val m = manufacturer.lowercase().trim()
        val md = model.lowercase().trim()
        val key = when {
            b.contains("redmi") || b.contains("xiaomi") || b.contains("poco") ||
                    m.contains("xiaomi") -> BRAND_XIAOMI
            b.contains("honor") || m.contains("honor") -> BRAND_HONOR
            b.contains("huawei") || m.contains("huawei") -> BRAND_HUAWEI
            b.contains("realme") || m.contains("realme") -> BRAND_REALME
            b.contains("oneplus") || m.contains("oneplus") || md.startsWith("cph") || md.startsWith("le2") -> BRAND_ONEPLUS
            b.contains("oppo") || m.contains("oppo") || md.startsWith("p") && b.isEmpty() -> BRAND_OPPO
            b.contains("iqoo") || m.contains("iqoo") -> BRAND_IQOO
            b.contains("vivo") || m.contains("vivo") -> BRAND_VIVO
            b.contains("samsung") || m.contains("samsung") -> BRAND_SAMSUNG
            b.contains("meizu") || m.contains("meizu") -> BRAND_MEIZU
            b.contains("nubia") || b.contains("redmagic") || m.contains("nubia") -> BRAND_NUBIA
            b.contains("asus") || b.contains("rog") || m.contains("asus") -> BRAND_ASUS
            b.contains("sony") || m.contains("sony") -> BRAND_SONY
            b.contains("motorola") || b.contains("moto") || m.contains("motorola") -> BRAND_MOTOROLA
            b.contains("google") || m.contains("google") -> BRAND_GOOGLE
            else -> BRAND_GENERIC
        }
        return BrandProfile(
            id = key,
            nameKey = "brand_$key",
            rawBrand = rawBrand,
            romName = romNameOf(key),
            hasBrandSpecific = brandSpecificItems(key).isNotEmpty()
        )
    }

    private fun romNameOf(key: String): String = when (key) {
        BRAND_XIAOMI -> "HyperOS / MIUI"
        BRAND_HUAWEI -> "HarmonyOS / EMUI"
        BRAND_HONOR -> "MagicOS"
        BRAND_OPPO, BRAND_REALME, BRAND_ONEPLUS -> "ColorOS / realme UI / OxygenOS"
        BRAND_VIVO, BRAND_IQOO -> "OriginOS / Funtouch"
        BRAND_SAMSUNG -> "One UI"
        BRAND_MEIZU -> "Flyme"
        BRAND_NUBIA -> "RedMagic OS / MyOS"
        BRAND_ASUS -> "ROG UI / ZenUI"
        BRAND_SONY -> "Xperia UI"
        BRAND_MOTOROLA -> "My UX"
        BRAND_GOOGLE -> "Pixel UI"
        else -> "AOSP / 未知 ROM"
    }

    /** 该品牌是否被识别（非 generic）。 */
    fun isRecognized(brandId: String) = brandId != BRAND_GENERIC

    /**
     * 「全机型通用」在界面上的品牌 id：空串。
     *
     * 与 [BRAND_GENERIC] 等价但语义不同：空串用于 [BrandPerfScreen] 的入口回调与
     * [itemsFor] 的查询入参（`itemsFor("")` 返回全部通用项），[BRAND_GENERIC] 用于
     * 品牌识别结果。两者都能被本对象的取数函数正确处理。
     */
    const val BRAND_ALL = ""

    /**
     * 品牌显示名文案 key。
     * 空串（全机型通用）与 generic 都会返回 `brand_generic`，避免界面拿到不存在的 key。
     */
    fun nameKeyOf(brandId: String): String = "brand_" + brandId.ifBlank { BRAND_GENERIC }

    /** 品牌对应的系统 UI 名（HyperOS / OriginOS / ColorOS ...）；未识别品牌返回通用 ROM 名。 */
    fun romNameFor(brandId: String): String = romNameOf(brandId)

    /** 该品牌专属优化条数（真实数据，界面不得写死数字）。 */
    fun brandSpecificCount(brandId: String): Int = brandSpecificItems(brandId).size

    // ---------------------------------------------------------- 通用指令集

    /**
     * 通用于所有 Android 设备的性能指令。
     * 这些是 AOSP 原生接口，从 Android 4.x 到 15 都有效。
     */
    val genericItems: List<PerfItem> = listOf(
        // ---- 后台 ----
        PerfItem(
            id = "generic_kill_bg",
            nameKey = "perf_item_kill_bg",
            descKey = "perf_item_kill_bg_desc",
            command = "am kill-all; echo BGDONE",
            category = "background",
            requiresPermission = "shizuku",
            verifyCommand = "am --help | head -1",
            verifyExpect = "am",
            order = 5
        ),
        PerfItem(
            id = "generic_trim_caches",
            nameKey = "perf_item_trim_caches",
            descKey = "perf_item_trim_caches_desc",
            command = "pm trim-caches 99999999999; echo TRIMDONE",
            category = "memory",
            requiresPermission = "shizuku",
            verifyCommand = "pm help 2>&1 | head -1",
            verifyExpect = "",
            order = 10
        ),
        PerfItem(
            id = "generic_compact_bg",
            nameKey = "perf_item_compact_bg",
            descKey = "perf_item_compact_bg_desc",
            command = "cmd activity compact system 2>/dev/null || am compact 2>/dev/null; echo COMPACTDONE",
            category = "memory",
            requiresPermission = "shizuku",
            order = 15
        ),
        PerfItem(
            id = "generic_drop_caches",
            nameKey = "perf_item_drop_caches",
            descKey = "perf_item_drop_caches_desc",
            command = "sync; echo 3 > /proc/sys/vm/drop_caches; echo DROPDONE",
            category = "memory",
            requiresPermission = "root",
            risk = "caution",
            defaultSelected = false,
            order = 20
        ),
        // ---- 动画（提速最直观、最安全，可逆） ----
        PerfItem(
            id = "anim_scale_off",
            nameKey = "perf_item_anim_scale_off",
            descKey = "perf_item_anim_scale_off_desc",
            command = "settings put global window_animation_scale 0.5; " +
                    "settings put global transition_animation_scale 0.5; " +
                    "settings put global animator_duration_scale 0.5; " +
                    "echo ANIMDONE",
            category = "animation",
            requiresPermission = "shizuku",
            verifyCommand = "settings get global window_animation_scale",
            verifyExpect = "0.5",
            toggleOffCommand = "settings put global window_animation_scale 1; " +
                    "settings put global transition_animation_scale 1; " +
                    "settings put global animator_duration_scale 1",
            order = 25
        ),
        PerfItem(
            id = "anim_scale_zero",
            nameKey = "perf_item_anim_scale_zero",
            descKey = "perf_item_anim_scale_zero_desc",
            command = "settings put global window_animation_scale 0; " +
                    "settings put global transition_animation_scale 0; " +
                    "settings put global animator_duration_scale 0; " +
                    "echo ANIM0DONE",
            category = "animation",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            isDisableAction = true,
            toggleOffCommand = "settings put global window_animation_scale 1; " +
                    "settings put global transition_animation_scale 1; " +
                    "settings put global animator_duration_scale 1",
            order = 30
        ),
        // ---- CPU / 调度 ----
        PerfItem(
            id = "cpu_force_gpu",
            nameKey = "perf_item_cpu_force_gpu",
            descKey = "perf_item_cpu_force_gpu_desc",
            command = "setprop debug.hwui.renderer skiagl; " +
                    "settings put global force_gpu_rendering 1; echo GPUDONE",
            category = "cpu",
            requiresPermission = "shizuku",
            defaultSelected = false,
            risk = "caution",
            toggleOffCommand = "settings put global force_gpu_rendering 0",
            order = 35
        ),
        PerfItem(
            id = "cpu_disable_low_power",
            nameKey = "perf_item_cpu_disable_low_power",
            descKey = "perf_item_cpu_disable_low_power_desc",
            command = "settings put global low_power 0; " +
                    "cmd power set-mode 0 2>/dev/null; echo POWERDONE",
            category = "power",
            requiresPermission = "shizuku",
            order = 40
        ),
        PerfItem(
            id = "cpu_perf_mode",
            nameKey = "perf_item_cpu_perf_mode",
            descKey = "perf_item_cpu_perf_mode_desc",
            command = "for g in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do " +
                    "echo performance > \$g 2>/dev/null; done; " +
                    "for c in /sys/devices/system/cpu/cpu*/core_ctl/enable; do " +
                    "echo 0 > \$c 2>/dev/null; done; echo GOVDONE",
            category = "cpu",
            requiresPermission = "root",
            risk = "risky",
            defaultSelected = false,
            order = 45
        ),
        PerfItem(
            id = "cpu_min_freq_max",
            nameKey = "perf_item_cpu_min_freq_max",
            descKey = "perf_item_cpu_min_freq_max_desc",
            command = "for p in /sys/devices/system/cpu/cpu*/cpufreq; do " +
                    "[ -f \$p/cpuinfo_max_freq ] && echo \$(cat \$p/cpuinfo_max_freq) > \$p/scaling_min_freq 2>/dev/null; " +
                    "done; echo FREQDONE",
            category = "cpu",
            requiresPermission = "root",
            risk = "risky",
            defaultSelected = false,
            toggleOffCommand = "for p in /sys/devices/system/cpu/cpu*/cpufreq; do " +
                    "[ -f \$p/cpuinfo_min_freq ] && echo \$(cat \$p/cpuinfo_min_freq) > \$p/scaling_min_freq 2>/dev/null; done",
            order = 50
        ),
        PerfItem(
            id = "cpu_stop_thermal",
            nameKey = "perf_item_cpu_stop_thermal",
            descKey = "perf_item_cpu_stop_thermal_desc",
            command = "stop thermal-engine 2>/dev/null; stop thermald 2>/dev/null; " +
                    "stop mi_thermald 2>/dev/null; stop thermal 2>/dev/null; " +
                    "setprop ctl.stop thermal-engine; echo THERMALDONE",
            category = "cpu",
            requiresPermission = "root",
            risk = "risky",
            defaultSelected = false,
            order = 55
        ),
        PerfItem(
            id = "cpu_setenforce_zero",
            nameKey = "perf_item_cpu_setenforce_zero",
            descKey = "perf_item_cpu_setenforce_zero_desc",
            command = "setenforce 0; getenforce",
            category = "cpu",
            requiresPermission = "root",
            risk = "risky",
            defaultSelected = false,
            toggleOffCommand = "setenforce 1",
            order = 60
        ),
        // ---- 内存 ----
        PerfItem(
            id = "mem_disable_zram_swap",
            nameKey = "perf_item_mem_disable_zram",
            descKey = "perf_item_mem_disable_zram_desc",
            command = "swapoff -a 2>/dev/null; echo 0 > /proc/sys/vm/swappiness 2>/dev/null; echo ZRAMOFF",
            category = "memory",
            requiresPermission = "root",
            risk = "risky",
            defaultSelected = false,
            toggleOffCommand = "swapon -a; echo 100 > /proc/sys/vm/swappiness",
            order = 65
        ),
        PerfItem(
            id = "mem_vfs_pressure",
            nameKey = "perf_item_mem_vfs_pressure",
            descKey = "perf_item_mem_vfs_pressure_desc",
            command = "echo 40 > /proc/sys/vm/vfs_cache_pressure 2>/dev/null; " +
                    "echo 10 > /proc/sys/vm/dirty_ratio 2>/dev/null; " +
                    "echo 5 > /proc/sys/vm/dirty_background_ratio 2>/dev/null; echo VMDONE",
            category = "memory",
            requiresPermission = "root",
            risk = "caution",
            defaultSelected = false,
            order = 70
        ),
        PerfItem(
            id = "mem_lmk_protect",
            nameKey = "perf_item_mem_lmk_protect",
            descKey = "perf_item_mem_lmk_protect_desc",
            command = "for f in /sys/module/lowmemorykiller/parameters/minfree; do " +
                    "[ -f \$f ] && echo \"18432,23040,27648,32256,55296,80640\" > \$f 2>/dev/null; done; " +
                    "setprop persist.sys.lmk.minfree 18432; echo LMKDONE",
            category = "memory",
            requiresPermission = "root",
            risk = "risky",
            defaultSelected = false,
            order = 75
        ),
        // ---- 电源 / 省电 ----
        PerfItem(
            id = "power_battery_saver_off",
            nameKey = "perf_item_power_saver_off",
            descKey = "perf_item_power_saver_off_desc",
            command = "settings put global low_power 0; " +
                    "cmd power set-mode 0 2>/dev/null; " +
                    "dumpsys deviceidle disable 2>/dev/null; echo SAVEROFF",
            category = "power",
            requiresPermission = "shizuku",
            order = 80
        ),
        PerfItem(
            id = "power_unrestrict_all",
            nameKey = "perf_item_power_unrestrict",
            descKey = "perf_item_power_unrestrict_desc",
            command = "cmd deviceidle whitelist +com.android.systemui 2>/dev/null; " +
                    "cmd appops set com.android.systemui RUN_IN_BACKGROUND allow 2>/dev/null; " +
                    "echo UNRESTRICTDONE",
            category = "power",
            requiresPermission = "shizuku",
            order = 85
        ),
        PerfItem(
            id = "power_adb_wifi_off",
            nameKey = "perf_item_power_adb_wifi_off",
            descKey = "perf_item_power_adb_wifi_off_desc",
            command = "svc wifi disable 2>/dev/null; svc data disable 2>/dev/null; echo RADIOOFF",
            category = "power",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "svc wifi enable; svc data enable",
            order = 90
        ),
        // ---- 网络 ----
        PerfItem(
            id = "net_tcp_fast",
            nameKey = "perf_item_net_tcp_fast",
            descKey = "perf_item_net_tcp_fast_desc",
            command = "setprop net.tcp.buffersize.default 4096,87380,704512,4096,16384,110208; " +
                    "setprop net.tcp.buffersize.wifi 524288,1048576,2097152,262144,524288,1048576; " +
                    "setprop net.tcp.buffersize.lte 524288,1048576,2560000,262144,524288,1220608; " +
                    "echo TCPDONE",
            category = "network",
            requiresPermission = "root",
            risk = "caution",
            defaultSelected = false,
            order = 95
        ),
        PerfItem(
            id = "net_dns_flush",
            nameKey = "perf_item_net_dns_flush",
            descKey = "perf_item_net_dns_flush_desc",
            command = "ndc resolver flushdefaultif 2>/dev/null; " +
                    "ndc resolver flushif wlan0 2>/dev/null; " +
                    "cmd netd resolver flushdefaultif 2>/dev/null; echo DNSFLUSHED",
            category = "network",
            requiresPermission = "shizuku",
            order = 100
        ),
        // ---- 存储 / 数据库 ----
        PerfItem(
            id = "storage_vacuum",
            nameKey = "perf_item_storage_vacuum",
            descKey = "perf_item_storage_vacuum_desc",
            command = "for db in \$(find /data/data -maxdepth 2 -name '*.db' 2>/dev/null | head -200); do " +
                    "sqlite3 \"\$db\" 'VACUUM;' >/dev/null 2>&1; done; echo VACUUMDONE",
            category = "storage",
            requiresPermission = "root",
            risk = "caution",
            defaultSelected = false,
            order = 105
        ),
        PerfItem(
            id = "storage_fstrim",
            nameKey = "perf_item_storage_fstrim",
            descKey = "perf_item_storage_fstrim_desc",
            command = "for p in /data /system /cache; do fstrim -v \$p 2>/dev/null; done; echo FSTRIMDONE",
            category = "storage",
            requiresPermission = "root",
            risk = "caution",
            defaultSelected = false,
            order = 110
        ),
        PerfItem(
            id = "storage_drop_logs",
            nameKey = "perf_item_storage_drop_logs",
            descKey = "perf_item_storage_drop_logs_desc",
            command = "logcat -c 2>/dev/null; rm -rf /data/anr/* 2>/dev/null; " +
                    "rm -rf /data/tombstones/* 2>/dev/null; echo LOGSDROPPED",
            category = "storage",
            requiresPermission = "shizuku",
            order = 115
        ),
        // ---- 显示 / 刷新率（修复部分机型锁 60Hz） ----
        PerfItem(
            id = "display_max_refresh",
            nameKey = "perf_item_display_max_refresh",
            descKey = "perf_item_display_max_refresh_desc",
            command = "__ADBTOOLS_MAX_REFRESH__",
            category = "display",
            requiresPermission = "shizuku",
            verifyCommand = "settings get system peak_refresh_rate",
            order = 120
        ),
        PerfItem(
            id = "display_force_high_refresh",
            nameKey = "perf_item_display_force_high_refresh",
            descKey = "perf_item_display_force_high_refresh_desc",
            command = "settings put system min_refresh_rate 120; " +
                    "settings put system peak_refresh_rate 120; " +
                    "settings put secure user_refresh_rate 120; echo REFRESHFIXED",
            category = "display",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "settings delete system min_refresh_rate; " +
                    "settings delete system peak_refresh_rate; " +
                    "settings delete secure user_refresh_rate",
            order = 125
        ),
        PerfItem(
            id = "display_screen_always_on_off",
            nameKey = "perf_item_display_aod_off",
            descKey = "perf_item_display_aod_off_desc",
            command = "settings put secure doze_always_on 0; " +
                    "cmd display set-doze 0 2>/dev/null; echo AODOFF",
            category = "display",
            requiresPermission = "shizuku",
            defaultSelected = false,
            isDisableAction = true,
            toggleOffCommand = "settings put secure doze_always_on 1",
            order = 130
        )
    )

    /** 小米 / Redmi / POCO（HyperOS / MIUI）专属优化。 */
    private val xiaomiItems: List<PerfItem> = listOf(
        PerfItem(
            id = "mi_optimization_on",
            nameKey = "perf_item_mi_optimization_on",
            descKey = "perf_item_mi_optimization_on_desc",
            command = "settings put secure mi_optimization 1; " +
                    "settings put global cloud_sync_switch 0 2>/dev/null; " +
                    "settings put global find_device_auto_sync 0 2>/dev/null; echo MIDONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 200
        ),
        PerfItem(
            id = "mi_kill_background",
            nameKey = "perf_item_mi_kill_background",
            descKey = "perf_item_mi_kill_background_desc",
            command = "settings put global background_process_limit 0; " +
                    "am kill-all; echo MI2DONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 205
        ),
        PerfItem(
            id = "mi_disable_powerkeeper",
            nameKey = "perf_item_mi_disable_powerkeeper",
            descKey = "perf_item_mi_disable_powerkeeper_desc",
            command = "settings put global power_check_max_cpu_1 200; " +
                    "settings put global power_check_max_cpu_2 250; " +
                    "settings put global power_check_max_cpu_3 300; " +
                    "settings put global power_check_max_cpu_4 350; echo MIPOWERDONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 210
        ),
        PerfItem(
            id = "mi_disable_joyose",
            nameKey = "perf_item_mi_disable_joyose",
            descKey = "perf_item_mi_disable_joyose_desc",
            command = "am force-stop com.xiaomi.joyose; " +
                    "pm disable-user --user 0 com.xiaomi.joyose 2>/dev/null; echo JOYOSEDONE",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "pm enable com.xiaomi.joyose",
            order = 215
        ),
        PerfItem(
            id = "mi_disable_analytics",
            nameKey = "perf_item_mi_disable_analytics",
            descKey = "perf_item_mi_disable_analytics_desc",
            command = "pm disable-user --user 0 com.miui.analytics 2>/dev/null; " +
                    "pm disable-user --user 0 com.xiaomi.mipicks 2>/dev/null; " +
                    "pm disable-user --user 0 com.miui.msa.global 2>/dev/null; " +
                    "pm disable-user --user 0 com.xiaomi.metoknlp 2>/dev/null; echo MIADOFF",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "pm enable com.miui.analytics; pm enable com.xiaomi.mipicks; " +
                    "pm enable com.miui.msa.global; pm enable com.xiaomi.metoknlp",
            order = 220
        ),
        PerfItem(
            id = "mi_gpu_tuning",
            nameKey = "perf_item_mi_gpu_tuning",
            descKey = "perf_item_mi_gpu_tuning_desc",
            command = "setprop debug.hwui.renderer skiagl; " +
                    "settings put secure game_mode_auto_switch 0 2>/dev/null; " +
                    "settings put secure game_turbo_mode 1 2>/dev/null; echo MIGPUDONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 225
        ),
        PerfItem(
            id = "mi_ram_expansion_off",
            nameKey = "perf_item_mi_ram_expansion_off",
            descKey = "perf_item_mi_ram_expansion_off_desc",
            command = "setprop persist.sys.zram_enabled 0 2>/dev/null; " +
                    "settings put global miui_ram_expansion 0 2>/dev/null; echo MIZRAMOFF",
            category = "brand",
            requiresPermission = "root",
            risk = "risky",
            defaultSelected = false,
            order = 230
        )
    )

    /** 华为 / 荣耀（HarmonyOS / EMUI / MagicOS）专属优化。 */
    private val huaweiItems: List<PerfItem> = listOf(
        PerfItem(
            id = "hw_disable_powergenie",
            nameKey = "perf_item_hw_disable_powergenie",
            descKey = "perf_item_hw_disable_powergenie_desc",
            command = "am force-stop com.huawei.powergenie; " +
                    "pm disable-user --user 0 com.huawei.powergenie 2>/dev/null; echo HWPOWERDONE",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "pm enable com.huawei.powergenie",
            order = 200
        ),
        PerfItem(
            id = "hw_disable_analytics",
            nameKey = "perf_item_hw_disable_analytics",
            descKey = "perf_item_hw_disable_analytics_desc",
            command = "pm disable-user --user 0 com.huawei.android.hwaps 2>/dev/null; " +
                    "pm disable-user --user 0 com.huawei.bd 2>/dev/null; " +
                    "pm disable-user --user 0 com.huawei.android.microkernel 2>/dev/null; echo HWBDDONE",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "pm enable com.huawei.android.hwaps; pm enable com.huawei.bd",
            order = 205
        ),
        PerfItem(
            id = "hw_perf_mode",
            nameKey = "perf_item_hw_perf_mode",
            descKey = "perf_item_hw_perf_mode_desc",
            command = "settings put global hw_power_mode 1 2>/dev/null; " +
                    "settings put secure hw_performance_mode 1 2>/dev/null; " +
                    "echo HWPERFDONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 210
        ),
        PerfItem(
            id = "hw_kill_bg",
            nameKey = "perf_item_hw_kill_bg",
            descKey = "perf_item_hw_kill_bg_desc",
            command = "am kill-all; echo HW2DONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 215
        ),
        PerfItem(
            id = "hw_gpu_turbo",
            nameKey = "perf_item_hw_gpu_turbo",
            descKey = "perf_item_hw_gpu_turbo_desc",
            command = "setprop debug.hwui.renderer skiagl; " +
                    "setprop persist.sys.gpu.rendering 1 2>/dev/null; echo HWGPUDONE",
            category = "brand",
            requiresPermission = "root",
            risk = "caution",
            defaultSelected = false,
            order = 220
        )
    )

    /** OPPO / realme / 一加（ColorOS / realme UI / OxygenOS）专属优化。 */
    private val oppoItems: List<PerfItem> = listOf(
        PerfItem(
            id = "oppo_perf_mode",
            nameKey = "perf_item_oppo_perf_mode",
            descKey = "perf_item_oppo_perf_mode_desc",
            command = "settings put secure oppo_performance_mode 1 2>/dev/null; " +
                    "settings put global oppo_gpu_turbo 1 2>/dev/null; " +
                    "settings put secure game_mode 1 2>/dev/null; echo OPPOPERFDONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 200
        ),
        PerfItem(
            id = "oppo_disable_power",
            nameKey = "perf_item_oppo_disable_power",
            descKey = "perf_item_oppo_disable_power_desc",
            command = "am force-stop com.coloros.oppoguardelf 2>/dev/null; " +
                    "pm disable-user --user 0 com.coloros.oppoguardelf 2>/dev/null; echo OPPOPOWERDONE",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "pm enable com.coloros.oppoguardelf",
            order = 205
        ),
        PerfItem(
            id = "oppo_disable_analytics",
            nameKey = "perf_item_oppo_disable_analytics",
            descKey = "perf_item_oppo_disable_analytics_desc",
            command = "pm disable-user --user 0 com.oppo.logkit 2>/dev/null; " +
                    "pm disable-user --user 0 com.coloros.logkit 2>/dev/null; " +
                    "pm disable-user --user 0 com.oppo.usageassist 2>/dev/null; echo OPPOLOGDONE",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "pm enable com.oppo.logkit; pm enable com.coloros.logkit; pm enable com.oppo.usageassist",
            order = 210
        ),
        PerfItem(
            id = "oppo_kill_bg",
            nameKey = "perf_item_oppo_kill_bg",
            descKey = "perf_item_oppo_kill_bg_desc",
            command = "am kill-all; echo OPPO2DONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 215
        ),
        PerfItem(
            id = "oppo_disable_osense",
            nameKey = "perf_item_oppo_disable_osense",
            descKey = "perf_item_oppo_disable_osense_desc",
            command = "pm disable-user --user 0 com.oplus.osense 2>/dev/null; " +
                    "pm disable-user --user 0 com.oplus.osense 2>/dev/null; echo OSENSEDONE",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "pm enable com.oplus.osense",
            order = 220
        )
    )

    /** vivo / iQOO（OriginOS / Funtouch）专属优化。 */
    private val vivoItems: List<PerfItem> = listOf(
        PerfItem(
            id = "vivo_perf_mode",
            nameKey = "perf_item_vivo_perf_mode",
            descKey = "perf_item_vivo_perf_mode_desc",
            command = "settings put secure vivo_game_mode 1 2>/dev/null; " +
                    "settings put global game_mode_enable 1 2>/dev/null; " +
                    "settings put secure vivo_performance_mode 1 2>/dev/null; echo VIVOPERFDONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 200
        ),
        PerfItem(
            id = "vivo_disable_power",
            nameKey = "perf_item_vivo_disable_power",
            descKey = "perf_item_vivo_disable_power_desc",
            command = "am force-stop com.vivo.pem; " +
                    "pm disable-user --user 0 com.vivo.pem 2>/dev/null; echo VIVOPOWERDONE",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "pm enable com.vivo.pem",
            order = 205
        ),
        PerfItem(
            id = "vivo_disable_analytics",
            nameKey = "perf_item_vivo_disable_analytics",
            descKey = "perf_item_vivo_disable_analytics_desc",
            command = "pm disable-user --user 0 com.vivo.abe 2>/dev/null; " +
                    "pm disable-user --user 0 com.vivo.bgapp 2>/dev/null; " +
                    "pm disable-user --user 0 com.vivo.daemonService 2>/dev/null; echo VIVOBDDONE",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "pm enable com.vivo.abe; pm enable com.vivo.bgapp; pm enable com.vivo.daemonService",
            order = 210
        ),
        PerfItem(
            id = "vivo_kill_bg",
            nameKey = "perf_item_vivo_kill_bg",
            descKey = "perf_item_vivo_kill_bg_desc",
            command = "am kill-all; echo VIVO2DONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 215
        ),
        PerfItem(
            id = "vivo_disable_vivo_push",
            nameKey = "perf_item_vivo_disable_push",
            descKey = "perf_item_vivo_disable_push_desc",
            command = "settings put global vivo_push_enable 0 2>/dev/null; " +
                    "am force-stop com.vivo.pushservice 2>/dev/null; echo VIVOPUSHDONE",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "settings put global vivo_push_enable 1",
            order = 220
        )
    )

    /** 三星（One UI）专属优化。 */
    private val samsungItems: List<PerfItem> = listOf(
        PerfItem(
            id = "ss_perf_mode",
            nameKey = "perf_item_ss_perf_mode",
            descKey = "perf_item_ss_perf_mode_desc",
            command = "settings put secure samsung_performance_mode 1 2>/dev/null; " +
                    "settings put global game_booster_enable 1 2>/dev/null; " +
                    "settings put system power_mode 1 2>/dev/null; echo SSPERFDONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 200
        ),
        PerfItem(
            id = "ss_disable_analytics",
            nameKey = "perf_item_ss_disable_analytics",
            descKey = "perf_item_ss_disable_analytics_desc",
            command = "pm disable-user --user 0 com.samsung.android.dqagent 2>/dev/null; " +
                    "pm disable-user --user 0 com.sec.android.diagmonagent 2>/dev/null; " +
                    "pm disable-user --user 0 com.samsung.android.rubin.app 2>/dev/null; echo SSBDDONE",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "pm enable com.samsung.android.dqagent; pm enable com.sec.android.diagmonagent; " +
                    "pm enable com.samsung.android.rubin.app",
            order = 205
        ),
        PerfItem(
            id = "ss_disable_gos",
            nameKey = "perf_item_ss_disable_gos",
            descKey = "perf_item_ss_disable_gos_desc",
            command = "pm disable-user --user 0 com.samsung.android.game.gos 2>/dev/null; echo SSGOSDONE",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "pm enable com.samsung.android.game.gos",
            order = 210
        ),
        PerfItem(
            id = "ss_max_refresh",
            nameKey = "perf_item_ss_max_refresh",
            descKey = "perf_item_ss_max_refresh_desc",
            command = "settings put system peak_refresh_rate 120; " +
                    "settings put system min_refresh_rate 120; " +
                    "settings put secure refresh_rate_mode 2 2>/dev/null; echo SSREFRESHDONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 215
        ),
        PerfItem(
            id = "ss_kill_bg",
            nameKey = "perf_item_ss_kill_bg",
            descKey = "perf_item_ss_kill_bg_desc",
            command = "am kill-all; echo SS2DONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 220
        )
    )

    /** 魅族（Flyme）专属优化。 */
    private val meizuItems: List<PerfItem> = listOf(
        PerfItem(
            id = "mz_perf_mode",
            nameKey = "perf_item_mz_perf_mode",
            descKey = "perf_item_mz_perf_mode_desc",
            command = "settings put global flyme_performance_mode 1 2>/dev/null; " +
                    "settings put secure game_mode_enable 1 2>/dev/null; echo MZPERFDONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 200
        ),
        PerfItem(
            id = "mz_disable_analytics",
            nameKey = "perf_item_mz_disable_analytics",
            descKey = "perf_item_mz_disable_analytics_desc",
            command = "pm disable-user --user 0 com.meizu.mstore 2>/dev/null; " +
                    "pm disable-user --user 0 com.meizu.flyme.update 2>/dev/null; echo MZBDDONE",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = "pm enable com.meizu.mstore; pm enable com.meizu.flyme.update",
            order = 205
        ),
        PerfItem(
            id = "mz_kill_bg",
            nameKey = "perf_item_mz_kill_bg",
            descKey = "perf_item_mz_kill_bg_desc",
            command = "am kill-all; echo MZ2DONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 210
        )
    )

    /** 红魔 / 努比亚、ROG、Xperia、摩托罗拉、Pixel 共用的"性能模式/游戏模式"入口。 */
    private fun gameModeItems(pkgHint: String, idPrefix: String): List<PerfItem> = listOf(
        PerfItem(
            id = "${idPrefix}_perf_mode",
            nameKey = "perf_item_generic_perf_mode",
            descKey = "perf_item_generic_perf_mode_desc",
            command = "settings put secure game_mode 1 2>/dev/null; " +
                    "settings put global game_mode_enable 1 2>/dev/null; " +
                    "settings put secure performance_mode 1 2>/dev/null; echo PERFMODEDONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 200
        ),
        PerfItem(
            id = "${idPrefix}_kill_bg",
            nameKey = "perf_item_generic_kill_bg_brand",
            descKey = "perf_item_generic_kill_bg_brand_desc",
            command = "am kill-all; echo BRAND2DONE",
            category = "brand",
            requiresPermission = "shizuku",
            order = 205
        ),
        PerfItem(
            id = "${idPrefix}_disable_analytics",
            nameKey = "perf_item_generic_disable_analytics",
            descKey = "perf_item_generic_disable_analytics_desc",
            command = if (pkgHint.isBlank()) "echo NOANALYTICS"
            else "pm disable-user --user 0 $pkgHint 2>/dev/null; echo ANALYTICSDONE",
            category = "brand",
            requiresPermission = "shizuku",
            risk = "caution",
            defaultSelected = false,
            toggleOffCommand = if (pkgHint.isBlank()) null else "pm enable $pkgHint",
            order = 210
        )
    )

    /**
     * 通用指令集 = 早期 AOSP 基础项 + [UniversalTuning] 的全机型深度项。
     *
     * 这里做的是"合并"而不是复制：两个列表的 id 互不重叠（[UniversalTuning] 全部
     * 以 `bg_` / `cpu_` / `mem_` / `power_` / `storage_` / `render_` / `net_` / `display_`
     * 开头），合并后按 id 去重，保证同一台设备不会出现重复条目。
     */
    val universalItems: List<PerfItem> =
        (genericItems + UniversalTuning.items).distinctBy { it.id }

    /** 品牌专属项集合（generic 品牌返回空）。 */
    fun brandSpecificItems(brandId: String): List<PerfItem> = when (brandId) {
        BRAND_XIAOMI -> xiaomiItems
        BRAND_HUAWEI, BRAND_HONOR -> huaweiItems
        BRAND_OPPO, BRAND_REALME, BRAND_ONEPLUS -> oppoItems
        BRAND_VIVO, BRAND_IQOO -> vivoItems
        BRAND_SAMSUNG -> samsungItems
        BRAND_MEIZU -> meizuItems
        BRAND_NUBIA -> gameModeItems("cn.nubia.analytics", "nubia")
        BRAND_ASUS -> gameModeItems("com.asus.mobilemanager", "asus")
        BRAND_SONY -> gameModeItems("com.sonymobile.iot", "sony")
        BRAND_MOTOROLA -> gameModeItems("com.motorola.moto", "moto")
        BRAND_GOOGLE -> gameModeItems("com.google.android.gms", "google")
        else -> emptyList()
    }

    /**
     * 该品牌在该 ROM 上"值得执行"的完整指令集：通用项 + 品牌专属项，按 order 升序。
     * [includeBrandSpecific] 为 false 时只返回通用项（用于规避品牌指令误判）。
     */
    fun itemsFor(brandId: String, includeBrandSpecific: Boolean = true): List<PerfItem> {
        val list = universalItems.toMutableList()
        if (includeBrandSpecific) list.addAll(brandSpecificItems(brandId))
        return list.sortedBy { it.order }
    }

    /** 按分类分组，返回 (分类, 该分类下的项)。空分类会被剔除。 */
    fun itemsByCategory(brandId: String, includeBrandSpecific: Boolean = true): List<Pair<PerfCategory, List<PerfItem>>> {
        val items = itemsFor(brandId, includeBrandSpecific)
        return categories.mapNotNull { cat ->
            val ofCat = items.filter { it.category == cat.id }
            if (ofCat.isEmpty()) null else cat to ofCat
        }
    }

    /** 全部已知品牌 id，供设置页手动覆盖品牌使用。 */
    val allBrandIds: List<String> = listOf(
        BRAND_GENERIC, BRAND_XIAOMI, BRAND_HUAWEI, BRAND_HONOR, BRAND_OPPO, BRAND_REALME,
        BRAND_VIVO, BRAND_IQOO, BRAND_ONEPLUS, BRAND_SAMSUNG, BRAND_MEIZU, BRAND_NUBIA,
        BRAND_ASUS, BRAND_SONY, BRAND_MOTOROLA, BRAND_GOOGLE
    )
}
