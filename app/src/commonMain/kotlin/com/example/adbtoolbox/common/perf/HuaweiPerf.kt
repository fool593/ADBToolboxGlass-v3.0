package com.example.adbtoolbox.common.perf

import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 华为（HarmonyOS / EMUI / MagicOS）专属深度性能引擎。
 *
 * 与通用"一键加速"的区别：
 * 1. 先按 `getprop` 批量读取真实属性判定机型与系统代际，非华为机型会明确标记出来；
 * 2. 每条方法都是本机真实存在的 shell 接口（settings / cmd / pm / sysfs / procfs），
 *    不存在"只画不做"的空壳，也不使用早已失效的伪命令（详见 docs/huawei_methods.md）；
 * 3. 每条方法都带验证命令与恢复命令，执行结果逐条回显 exitCode / stdout / stderr，失败不静默；
 * 4. 命令末尾统一追加唯一标记（[HuaweiMethod.marker]），用于判定"这条命令链是否真的跑完"。
 *
 * 命令链的退出码只反映最后一条命令，因此本引擎的判定规则是：
 * 未出现权限拒绝字样，且（出现标记 或 exitCode == 0）才算成功；实际生效与否由验证命令回答。
 */
object HuaweiPerf {

    // ---------------- 权限等级 ----------------
    const val PERM_NONE = "none"
    const val PERM_SHIZUKU = "shizuku"
    const val PERM_ROOT = "root"

    // ---------------- 风险等级 ----------------
    const val RISK_SAFE = "safe"
    const val RISK_CAUTION = "caution"
    const val RISK_RISKY = "risky"

    // ---------------- 分组 ----------------
    const val GROUP_BG = "bg"
    const val GROUP_MEM = "mem"
    const val GROUP_GPU = "gpu"
    const val GROUP_POWER = "power"
    const val GROUP_STORAGE = "storage"
    const val GROUP_NET = "net"
    const val GROUP_SYS = "sys"

    // ---------------- 验证结论 ----------------
    const val VERIFY_VALID = "valid"
    const val VERIFY_INVALID = "invalid"
    const val VERIFY_PERMISSION = "permission"
    const val VERIFY_NOT_APPLICABLE = "not_applicable"

    /** 命令主动放弃执行时的标记（例如读不到物理分辨率）。 */
    const val SKIP_MARKER = "HWM_SKIP"

    // ============================================================ 数据模型

    /** 分组定义；nameKey 对应 hw_group_* 文案。 */
    data class HuaweiGroup(val id: String, val nameKey: String, val order: Int)

    /**
     * 一条华为专属性能方法。
     *
     * [command] 与 [resolveCommand] 的返回值才是真正下发给 shell 的字符串（含结尾标记）。
     * [rollbackCommand] 为 null 表示不可逆（一次性操作或自愈型操作）。
     */
    data class HuaweiMethod(
        val id: String,
        val titleKey: String,
        val principleKey: String,
        val gainKey: String,
        val command: String,
        val verifyCommand: String?,
        val verifyExpect: String?,
        val rollbackCommand: String?,
        val requiresPermission: String,
        val risk: String,
        val group: String,
        val defaultSelected: Boolean,
        val order: Int,
        /** true 表示这是华为/荣耀专有接口，非华为机型不适用。 */
        val huaweiOnly: Boolean = false,
        /** 单条命令的执行超时（秒）。 */
        val timeoutSec: Int = 25
    ) {
        /** 命令末尾的唯一完成标记。 */
        val marker: String get() = "HWM_" + id.uppercase() + "_DONE"

        val isIrreversible: Boolean get() = rollbackCommand == null
    }

    /** 设备识别快照。 */
    data class HuaweiDeviceInfo(
        val isHuawei: Boolean,
        val isHonor: Boolean,
        val brand: String,
        val model: String,
        /** 归一化后的 EMUI 版本，例如 "12.0.0"；未提供时为空串。 */
        val emuiVersion: String,
        /** HarmonyOS / 平台版本，例如 "4.0.0"；未提供时为空串。 */
        val harmonyVersion: String,
        /** 供界面直接展示的系统标识。 */
        val osLabel: String,
        val androidVersion: String,
        val sdk: Int,
        val soc: String,
        val device: String,
        val buildId: String,
        val totalRamMb: Long,
        val availRamMb: Long,
        val thermalZoneCount: Int,
        val screenWidth: Int,
        val screenHeight: Int,
        val hasShizuku: Boolean,
        val hasRoot: Boolean,
        val isDhizukuActive: Boolean,
        val rawProps: Map<String, String>
    ) {
        val hasAnyPrivilege: Boolean get() = hasShizuku || hasRoot || isDhizukuActive
    }

    /** 单条执行结果。 */
    data class HuaweiRunResult(
        val id: String,
        val titleKey: String,
        val command: String,
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val durationMs: Long,
        val marker: String,
        /** true 表示这是恢复命令的执行结果。 */
        val isRollback: Boolean = false
    ) {
        private val merged: String get() = (stdout + "\n" + stderr).lowercase()

        val permissionDenied: Boolean
            get() = merged.contains("permission denied") ||
                    merged.contains("operation not permitted") ||
                    merged.contains("not allowed") ||
                    merged.contains("securityexception")

        val markerSeen: Boolean
            get() = marker.isNotBlank() && (stdout.contains(marker) || stderr.contains(marker))

        val skipped: Boolean
            get() = stdout.contains(SKIP_MARKER) || stderr.contains(SKIP_MARKER)

        /** 未出现权限拒绝，且命令链确实跑到底（或退出码为 0）。 */
        val succeeded: Boolean
            get() = !skipped && !permissionDenied && (markerSeen || exitCode == 0)

        /** 供界面展示的一行输出摘要：取第一行真正有信息量的输出。 */
        val summary: String
            get() {
                val fromOut = stdout.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() && !(marker.isNotBlank() && it.contains(marker)) }
                val fromErr = stderr.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }
                return (fromOut ?: fromErr ?: "").take(200)
            }
    }

    /** 批量执行报告。 */
    data class HuaweiRunReport(
        val results: List<HuaweiRunResult>,
        val startFreeMemoryMb: Long,
        val endFreeMemoryMb: Long,
        val durationMs: Long
    ) {
        val successCount: Int get() = results.count { it.succeeded }
        val skippedCount: Int get() = results.count { it.skipped }
        val failedCount: Int get() = results.size - successCount - skippedCount
        /** 释放出的内存（MB），可能为负，按实际值展示。 */
        val freedMemoryMb: Long get() = endFreeMemoryMb - startFreeMemoryMb
    }

    /** 单条验证结果。 */
    data class HuaweiVerifyResult(
        val id: String,
        val status: String,
        /** 验证命令的原始输出（已截断）。 */
        val output: String,
        val expected: String?,
        val verifyCommand: String?
    ) {
        val isValid: Boolean get() = status == VERIFY_VALID
    }

    // ============================================================ 分组

    val groups: List<HuaweiGroup> = listOf(
        HuaweiGroup(GROUP_BG, "hw_group_bg", 10),
        HuaweiGroup(GROUP_MEM, "hw_group_mem", 20),
        HuaweiGroup(GROUP_GPU, "hw_group_gpu", 30),
        HuaweiGroup(GROUP_POWER, "hw_group_power", 40),
        HuaweiGroup(GROUP_STORAGE, "hw_group_storage", 50),
        HuaweiGroup(GROUP_NET, "hw_group_net", 60),
        HuaweiGroup(GROUP_SYS, "hw_group_sys", 70)
    )

    fun groupNameKey(groupId: String): String =
        groups.firstOrNull { it.id == groupId }?.nameKey ?: "hw_group_sys"

    // ============================================================ 方法库

    /**
     * 构造一条方法：命令末尾自动追加唯一完成标记，避免每条手写标记时出现不一致。
     */
    private fun m(
        id: String,
        group: String,
        order: Int,
        command: String,
        verifyCommand: String? = null,
        verifyExpect: String? = null,
        rollbackCommand: String? = null,
        requiresPermission: String = PERM_NONE,
        risk: String = RISK_SAFE,
        defaultSelected: Boolean = false,
        huaweiOnly: Boolean = false,
        timeoutSec: Int = 25
    ): HuaweiMethod {
        val marker = "HWM_" + id.uppercase() + "_DONE"
        return HuaweiMethod(
            id = id,
            titleKey = "hw_m_$id",
            principleKey = "hw_m_${id}_principle",
            gainKey = "hw_m_${id}_gain",
            command = command.trimEnd().trimEnd(';').trimEnd() + "; echo " + marker,
            verifyCommand = verifyCommand,
            verifyExpect = verifyExpect,
            rollbackCommand = rollbackCommand,
            requiresPermission = requiresPermission,
            risk = risk,
            group = group,
            defaultSelected = defaultSelected,
            order = order,
            huaweiOnly = huaweiOnly,
            timeoutSec = timeoutSec
        )
    }

    /** 全部方法，按 order 升序。 */
    val methods: List<HuaweiMethod> = listOf(
        // ---------------------------------------------------- 后台与自启
        m(
            id = "bg_kill_all",
            group = GROUP_BG,
            order = 10,
            command = "am kill-all 2>/dev/null; " +
                    "echo \"FreeRAM=${'$'}(dumpsys meminfo 2>/dev/null | grep -m1 'Free RAM:' | tr -d ' ')\"",
            verifyCommand = "am help 2>/dev/null | grep -m1 kill-all",
            verifyExpect = "kill-all",
            rollbackCommand = null,
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            timeoutSec = 45
        ),
        m(
            id = "bg_disable_ota",
            group = GROUP_BG,
            order = 20,
            command = "pm disable-user --user 0 com.huawei.android.hwouc 2>/dev/null; " +
                    "am force-stop com.huawei.android.hwouc 2>/dev/null; " +
                    "echo \"state=${'$'}(pm list packages -d 2>/dev/null | grep -m1 hwouc)\"",
            verifyCommand = "pm list packages 2>/dev/null | grep -m1 com.huawei.android.hwouc",
            verifyExpect = "com.huawei.android.hwouc",
            rollbackCommand = "pm enable com.huawei.android.hwouc 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            huaweiOnly = true,
            timeoutSec = 30
        ),
        m(
            id = "bg_disable_stats",
            group = GROUP_BG,
            order = 30,
            command = "pm disable-user --user 0 com.huawei.bd 2>/dev/null; " +
                    "am force-stop com.huawei.bd 2>/dev/null; " +
                    "echo \"state=${'$'}(pm list packages -d 2>/dev/null | grep -m1 com.huawei.bd)\"",
            verifyCommand = "pm list packages 2>/dev/null | grep -m1 com.huawei.bd",
            verifyExpect = "com.huawei.bd",
            rollbackCommand = "pm enable com.huawei.bd 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            huaweiOnly = true,
            timeoutSec = 30
        ),
        m(
            id = "bg_autostart_page",
            group = GROUP_BG,
            order = 40,
            command = "am start -n com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity 2>/dev/null || " +
                    "am start -n com.huawei.systemmanager/.optimize.process.ProcessManagerActivity 2>/dev/null; " +
                    "echo \"launched=${'$'}?\"",
            verifyCommand = "pm list packages 2>/dev/null | grep -m1 com.huawei.systemmanager",
            verifyExpect = "com.huawei.systemmanager",
            rollbackCommand = null,
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = false,
            huaweiOnly = true,
            timeoutSec = 20
        ),

        // ---------------------------------------------------- 内存与缓存
        m(
            id = "mem_trim_caches",
            group = GROUP_MEM,
            order = 50,
            command = "pm trim-caches 128G 2>/dev/null; " +
                    "echo \"data=${'$'}(df /data 2>/dev/null | tail -n1 | tr -s ' ')\"",
            verifyCommand = "cmd package help 2>/dev/null | grep -m1 trim-caches",
            verifyExpect = "trim-caches",
            rollbackCommand = null,
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            timeoutSec = 90
        ),
        m(
            id = "mem_drop_caches",
            group = GROUP_MEM,
            order = 60,
            command = "sync 2>/dev/null; echo 3 > /proc/sys/vm/drop_caches 2>/dev/null; " +
                    "echo \"cached=${'$'}(grep -m1 '^Cached:' /proc/meminfo 2>/dev/null | tr -s ' ')\"",
            verifyCommand = "ls /proc/sys/vm/drop_caches 2>/dev/null",
            verifyExpect = "drop_caches",
            rollbackCommand = null,
            requiresPermission = PERM_ROOT,
            risk = RISK_CAUTION,
            defaultSelected = false,
            timeoutSec = 40
        ),
        m(
            id = "mem_compact_memory",
            group = GROUP_MEM,
            order = 70,
            command = "sync 2>/dev/null; echo 1 > /proc/sys/vm/compact_memory 2>/dev/null; " +
                    "echo \"buddy=${'$'}(head -n1 /proc/buddyinfo 2>/dev/null)\"",
            verifyCommand = "ls /proc/sys/vm/compact_memory 2>/dev/null",
            verifyExpect = "compact_memory",
            rollbackCommand = null,
            requiresPermission = PERM_ROOT,
            risk = RISK_SAFE,
            defaultSelected = false,
            timeoutSec = 60
        ),
        m(
            id = "mem_lmk_tune",
            group = GROUP_MEM,
            order = 80,
            command = "[ -f /data/local/tmp/hw_lmk_backup ] || " +
                    "cat /sys/module/lowmemorykiller/parameters/minfree > /data/local/tmp/hw_lmk_backup 2>/dev/null; " +
                    "echo 1024,2048,3072,4096,6144,8192 > /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null; " +
                    "echo \"minfree=${'$'}(cat /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null)\"",
            verifyCommand = "ls /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null",
            verifyExpect = "minfree",
            rollbackCommand = "cat /data/local/tmp/hw_lmk_backup > /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null; " +
                    "echo \"minfree=${'$'}(cat /sys/module/lowmemorykiller/parameters/minfree 2>/dev/null)\"",
            requiresPermission = PERM_ROOT,
            risk = RISK_RISKY,
            defaultSelected = false,
            timeoutSec = 30
        ),
        m(
            id = "mem_zram_off",
            group = GROUP_MEM,
            order = 90,
            command = "cat /proc/swaps > /data/local/tmp/hw_swaps_backup 2>/dev/null; " +
                    "swapoff /dev/block/zram0 2>/dev/null; " +
                    "echo \"zram=${'$'}(grep -c zram /proc/swaps 2>/dev/null)\"",
            verifyCommand = "grep -m1 zram /proc/swaps 2>/dev/null",
            verifyExpect = "zram",
            rollbackCommand = "swapon /dev/block/zram0 2>/dev/null; " +
                    "echo \"zram=${'$'}(grep -c zram /proc/swaps 2>/dev/null)\"",
            requiresPermission = PERM_ROOT,
            risk = RISK_RISKY,
            defaultSelected = false,
            timeoutSec = 40
        ),

        // ---------------------------------------------------- 渲染与 GPU
        m(
            id = "gpu_anim_scale",
            group = GROUP_GPU,
            order = 100,
            command = "settings put global window_animation_scale 0.5 2>/dev/null; " +
                    "settings put global transition_animation_scale 0.5 2>/dev/null; " +
                    "settings put global animator_duration_scale 0.5 2>/dev/null; " +
                    "echo \"scale=${'$'}(settings get global window_animation_scale 2>/dev/null)" +
                    "/${'$'}(settings get global transition_animation_scale 2>/dev/null)" +
                    "/${'$'}(settings get global animator_duration_scale 2>/dev/null)\"",
            verifyCommand = "settings list global 2>/dev/null | grep -m1 window_animation_scale",
            verifyExpect = "window_animation_scale",
            rollbackCommand = "settings put global window_animation_scale 1 2>/dev/null; " +
                    "settings put global transition_animation_scale 1 2>/dev/null; " +
                    "settings put global animator_duration_scale 1 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            timeoutSec = 25
        ),
        m(
            id = "gpu_skiagl",
            group = GROUP_GPU,
            order = 110,
            command = "setprop debug.hwui.renderer skiagl 2>/dev/null; " +
                    "echo \"renderer=${'$'}(getprop debug.hwui.renderer 2>/dev/null)\"",
            verifyCommand = "getprop ro.hardware.egl 2>/dev/null; getprop debug.hwui.renderer 2>/dev/null",
            verifyExpect = null,
            rollbackCommand = "setprop debug.hwui.renderer \"\" 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            timeoutSec = 20
        ),
        m(
            id = "gpu_render_scale",
            group = GROUP_GPU,
            order = 120,
            command = "wm size 2>/dev/null; " +
                    "echo \"size=${'$'}(wm size 2>/dev/null | tail -n1 | tr -d ' ')\"",
            verifyCommand = "wm size 2>/dev/null",
            verifyExpect = "Physical size",
            rollbackCommand = "wm size reset 2>/dev/null; " +
                    "echo \"size=${'$'}(wm size 2>/dev/null | tail -n1 | tr -d ' ')\"",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            timeoutSec = 25
        ),
        m(
            id = "gpu_sf_latch",
            group = GROUP_GPU,
            order = 130,
            command = "setprop debug.sf.latch_unsignaled 1 2>/dev/null; " +
                    "echo \"latch=${'$'}(getprop debug.sf.latch_unsignaled 2>/dev/null)\"",
            verifyCommand = "dumpsys SurfaceFlinger 2>/dev/null | grep -m1 -i SurfaceFlinger",
            verifyExpect = "SurfaceFlinger",
            rollbackCommand = "setprop debug.sf.latch_unsignaled 0 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            timeoutSec = 20
        ),

        // ---------------------------------------------------- 性能模式与温控
        m(
            id = "power_mode_on",
            group = GROUP_POWER,
            order = 140,
            command = "settings put global hw_power_mode 1 2>/dev/null; " +
                    "settings put secure hw_performance_mode 1 2>/dev/null; " +
                    "echo \"global=${'$'}(settings get global hw_power_mode 2>/dev/null) " +
                    "secure=${'$'}(settings get secure hw_performance_mode 2>/dev/null)\"",
            verifyCommand = "settings list global 2>/dev/null | grep -m1 -E \"hw_power_mode|hw_performance_mode\"",
            verifyExpect = "hw_",
            rollbackCommand = "settings put global hw_power_mode 0 2>/dev/null; " +
                    "settings put secure hw_performance_mode 0 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = true,
            huaweiOnly = true,
            timeoutSec = 25
        ),
        m(
            id = "power_saver_off",
            group = GROUP_POWER,
            order = 150,
            command = "settings put global low_power 0 2>/dev/null; " +
                    "settings put global low_power_sticky 0 2>/dev/null; " +
                    "echo \"low_power=${'$'}(settings get global low_power 2>/dev/null)\"",
            verifyCommand = "settings list global 2>/dev/null | grep -m1 low_power",
            verifyExpect = "low_power",
            rollbackCommand = "settings put global low_power 1 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            timeoutSec = 25
        ),
        m(
            id = "power_fixed_perf",
            group = GROUP_POWER,
            order = 160,
            command = "cmd power set-fixed-performance-mode-enabled true 2>&1 | head -n3",
            verifyCommand = "cmd power help 2>/dev/null | grep -m1 fixed-performance-mode-enabled",
            verifyExpect = "fixed-performance-mode-enabled",
            rollbackCommand = "cmd power set-fixed-performance-mode-enabled false 2>&1 | head -n3",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            timeoutSec = 25
        ),
        m(
            id = "power_gov_performance",
            group = GROUP_POWER,
            order = 170,
            command = "[ -f /data/local/tmp/hw_gov_backup ] || " +
                    "for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do " +
                    "[ -w \"${'$'}c\" ] && echo \"${'$'}c ${'$'}(cat ${'$'}c)\" >> /data/local/tmp/hw_gov_backup; done; " +
                    "for c in /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor; do " +
                    "echo performance > \"${'$'}c\" 2>/dev/null; done; " +
                    "echo \"gov=${'$'}(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null)\"",
            verifyCommand = "cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null",
            verifyExpect = null,
            rollbackCommand = "while read -r p g; do [ -w \"${'$'}p\" ] && echo \"${'$'}g\" > \"${'$'}p\" 2>/dev/null; done " +
                    "< /data/local/tmp/hw_gov_backup; " +
                    "echo \"gov=${'$'}(cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor 2>/dev/null)\"",
            requiresPermission = PERM_ROOT,
            risk = RISK_RISKY,
            defaultSelected = false,
            timeoutSec = 30
        ),
        m(
            id = "power_thermal_off",
            group = GROUP_POWER,
            order = 180,
            command = "[ -f /data/local/tmp/hw_thermal_backup ] || " +
                    "for z in /sys/class/thermal/thermal_zone*/mode; do " +
                    "[ -w \"${'$'}z\" ] && echo \"${'$'}z ${'$'}(cat ${'$'}z)\" >> /data/local/tmp/hw_thermal_backup; done; " +
                    "for z in /sys/class/thermal/thermal_zone*/mode; do " +
                    "echo disabled > \"${'$'}z\" 2>/dev/null; done; " +
                    "echo \"mode0=${'$'}(cat /sys/class/thermal/thermal_zone0/mode 2>/dev/null)\"",
            verifyCommand = "cat /sys/class/thermal/thermal_zone0/mode 2>/dev/null",
            verifyExpect = null,
            rollbackCommand = "while read -r p v; do [ -w \"${'$'}p\" ] && echo \"${'$'}v\" > \"${'$'}p\" 2>/dev/null; done " +
                    "< /data/local/tmp/hw_thermal_backup; " +
                    "echo \"mode0=${'$'}(cat /sys/class/thermal/thermal_zone0/mode 2>/dev/null)\"",
            requiresPermission = PERM_ROOT,
            risk = RISK_RISKY,
            defaultSelected = false,
            timeoutSec = 30
        ),

        // ---------------------------------------------------- 存储与编译
        m(
            id = "storage_bg_dexopt",
            group = GROUP_STORAGE,
            order = 190,
            command = "cmd package bg-dexopt-job 2>&1 | head -n5",
            verifyCommand = "cmd package help 2>/dev/null | grep -m1 bg-dexopt-job",
            verifyExpect = "bg-dexopt-job",
            rollbackCommand = null,
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            timeoutSec = 240
        ),
        m(
            id = "storage_compile_all",
            group = GROUP_STORAGE,
            order = 200,
            command = "cmd package compile -m speed-profile -a 2>&1 | tail -n5",
            verifyCommand = "cmd package help 2>/dev/null | grep -m1 compile",
            verifyExpect = "compile",
            rollbackCommand = "cmd package compile --reset -a 2>&1 | tail -n5",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            timeoutSec = 900
        ),
        m(
            id = "storage_fstrim",
            group = GROUP_STORAGE,
            order = 210,
            command = "fstrim -v /data 2>&1 | head -n3",
            verifyCommand = "ls /system/bin/fstrim 2>/dev/null",
            verifyExpect = "fstrim",
            rollbackCommand = null,
            requiresPermission = PERM_ROOT,
            risk = RISK_CAUTION,
            defaultSelected = false,
            timeoutSec = 180
        ),
        m(
            id = "storage_clear_logs",
            group = GROUP_STORAGE,
            order = 220,
            command = "rm -rf /data/tombstones/* /data/system/dropbox/* /data/anr/* 2>/dev/null; " +
                    "logcat -c 2>/dev/null; " +
                    "echo \"tombstones=${'$'}(ls /data/tombstones 2>/dev/null | wc -l)\"",
            verifyCommand = "ls -d /data/tombstones 2>/dev/null",
            verifyExpect = "tombstones",
            rollbackCommand = null,
            requiresPermission = PERM_ROOT,
            risk = RISK_CAUTION,
            defaultSelected = false,
            timeoutSec = 60
        ),

        // ---------------------------------------------------- 网络与待机
        m(
            id = "net_wifi_scan_off",
            group = GROUP_NET,
            order = 230,
            command = "settings put global wifi_scan_always_enabled 0 2>/dev/null; " +
                    "echo \"scan=${'$'}(settings get global wifi_scan_always_enabled 2>/dev/null)\"",
            verifyCommand = "settings list global 2>/dev/null | grep -m1 wifi_scan_always_enabled",
            verifyExpect = "wifi_scan_always_enabled",
            rollbackCommand = "settings put global wifi_scan_always_enabled 1 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            timeoutSec = 20
        ),
        m(
            id = "net_mobile_always_on_off",
            group = GROUP_NET,
            order = 240,
            command = "settings put global mobile_data_always_on 0 2>/dev/null; " +
                    "echo \"mdao=${'$'}(settings get global mobile_data_always_on 2>/dev/null)\"",
            verifyCommand = "settings list global 2>/dev/null | grep -m1 mobile_data_always_on",
            verifyExpect = "mobile_data_always_on",
            rollbackCommand = "settings put global mobile_data_always_on 1 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_SAFE,
            defaultSelected = true,
            timeoutSec = 20
        ),
        m(
            id = "net_wifi_low_latency",
            group = GROUP_NET,
            order = 250,
            command = "cmd wifi force-low-latency-mode enabled 2>&1 | head -n3",
            verifyCommand = "cmd wifi 2>/dev/null | grep -m1 force-low-latency-mode",
            verifyExpect = "force-low-latency-mode",
            rollbackCommand = "cmd wifi force-low-latency-mode disabled 2>&1 | head -n3",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            timeoutSec = 25
        ),
        m(
            id = "net_tcp_rwnd",
            group = GROUP_NET,
            order = 260,
            command = "setprop net.tcp.default_init_rwnd 60 2>/dev/null; " +
                    "echo \"rwnd=${'$'}(getprop net.tcp.default_init_rwnd 2>/dev/null)\"",
            verifyCommand = "getprop net.tcp.default_init_rwnd 2>/dev/null",
            verifyExpect = null,
            rollbackCommand = "setprop net.tcp.default_init_rwnd 10 2>/dev/null",
            requiresPermission = PERM_ROOT,
            risk = RISK_CAUTION,
            defaultSelected = false,
            timeoutSec = 20
        ),

        // ---------------------------------------------------- 系统级（深度）
        m(
            id = "sys_disable_hwaps",
            group = GROUP_SYS,
            order = 270,
            command = "pm disable-user --user 0 com.huawei.android.hwaps 2>/dev/null; " +
                    "am force-stop com.huawei.android.hwaps 2>/dev/null; " +
                    "echo \"state=${'$'}(pm list packages -d 2>/dev/null | grep -m1 hwaps)\"",
            verifyCommand = "pm list packages 2>/dev/null | grep -m1 com.huawei.android.hwaps",
            verifyExpect = "com.huawei.android.hwaps",
            rollbackCommand = "pm enable com.huawei.android.hwaps 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_RISKY,
            defaultSelected = false,
            huaweiOnly = true,
            timeoutSec = 30
        ),
        m(
            id = "sys_disable_push",
            group = GROUP_SYS,
            order = 280,
            command = "pm disable-user --user 0 com.huawei.android.pushagent 2>/dev/null; " +
                    "am force-stop com.huawei.android.pushagent 2>/dev/null; " +
                    "echo \"state=${'$'}(pm list packages -d 2>/dev/null | grep -m1 pushagent)\"",
            verifyCommand = "pm list packages 2>/dev/null | grep -m1 com.huawei.android.pushagent",
            verifyExpect = "com.huawei.android.pushagent",
            rollbackCommand = "pm enable com.huawei.android.pushagent 2>/dev/null",
            requiresPermission = PERM_SHIZUKU,
            risk = RISK_CAUTION,
            defaultSelected = false,
            huaweiOnly = true,
            timeoutSec = 30
        ),
        m(
            id = "sys_io_scheduler",
            group = GROUP_SYS,
            order = 290,
            command = "[ -f /data/local/tmp/hw_io_backup ] || " +
                    "for q in /sys/block/sd*/queue/scheduler /sys/block/mmcblk*/queue/scheduler; do " +
                    "[ -w \"${'$'}q\" ] || continue; " +
                    "v=${'$'}(sed -n 's/.*\\[\\(.*\\)\\].*/\\1/p' \"${'$'}q\"); " +
                    "echo \"${'$'}q ${'$'}v\" >> /data/local/tmp/hw_io_backup; done; " +
                    "for q in /sys/block/sd*/queue/scheduler /sys/block/mmcblk*/queue/scheduler; do " +
                    "echo none > \"${'$'}q\" 2>/dev/null; done; " +
                    "echo \"sched=${'$'}(cat /sys/block/sda/queue/scheduler 2>/dev/null)" +
                    "${'$'}(cat /sys/block/mmcblk0/queue/scheduler 2>/dev/null)\"",
            verifyCommand = "ls -d /sys/block/sd* /sys/block/mmcblk* 2>/dev/null",
            verifyExpect = null,
            rollbackCommand = "while read -r q v; do [ -n \"${'$'}v\" ] && [ -w \"${'$'}q\" ] && " +
                    "echo \"${'$'}v\" > \"${'$'}q\" 2>/dev/null; done < /data/local/tmp/hw_io_backup; " +
                    "echo \"sched=${'$'}(cat /sys/block/sda/queue/scheduler 2>/dev/null)" +
                    "${'$'}(cat /sys/block/mmcblk0/queue/scheduler 2>/dev/null)\"",
            requiresPermission = PERM_ROOT,
            risk = RISK_CAUTION,
            defaultSelected = false,
            timeoutSec = 30
        ),
        m(
            id = "sys_readahead",
            group = GROUP_SYS,
            order = 300,
            command = "[ -f /data/local/tmp/hw_ra_backup ] || " +
                    "for q in /sys/block/sd*/queue/read_ahead_kb /sys/block/mmcblk*/queue/read_ahead_kb; do " +
                    "[ -w \"${'$'}q\" ] && echo \"${'$'}q ${'$'}(cat ${'$'}q)\" >> /data/local/tmp/hw_ra_backup; done; " +
                    "for q in /sys/block/sd*/queue/read_ahead_kb /sys/block/mmcblk*/queue/read_ahead_kb; do " +
                    "echo 2048 > \"${'$'}q\" 2>/dev/null; done; " +
                    "echo \"ra=${'$'}(cat /sys/block/sda/queue/read_ahead_kb 2>/dev/null)" +
                    "${'$'}(cat /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null)\"",
            verifyCommand = "ls /sys/block/sda/queue/read_ahead_kb /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null",
            verifyExpect = null,
            rollbackCommand = "while read -r p v; do [ -n \"${'$'}v\" ] && [ -w \"${'$'}p\" ] && " +
                    "echo \"${'$'}v\" > \"${'$'}p\" 2>/dev/null; done < /data/local/tmp/hw_ra_backup; " +
                    "echo \"ra=${'$'}(cat /sys/block/sda/queue/read_ahead_kb 2>/dev/null)" +
                    "${'$'}(cat /sys/block/mmcblk0/queue/read_ahead_kb 2>/dev/null)\"",
            requiresPermission = PERM_ROOT,
            risk = RISK_CAUTION,
            defaultSelected = false,
            timeoutSec = 30
        )
    ).sortedBy { it.order }

    /** 某个分组下的方法（已按 order 排序）。 */
    fun methodsOf(groupId: String): List<HuaweiMethod> = methods.filter { it.group == groupId }

    /** 默认勾选的 id 列表。 */
    fun defaultSelection(): List<String> = methods.filter { it.defaultSelected }.map { it.id }

    /** 仅安全项（risk == safe）。 */
    fun safeOnly(): List<HuaweiMethod> = methods.filter { it.risk == RISK_SAFE }

    // ============================================================ 设备识别

    private val deviceProps = listOf(
        "ro.product.brand",
        "ro.product.manufacturer",
        "ro.product.model",
        "ro.build.version.emui",
        "ro.build.version.release",
        "ro.build.version.sdk",
        "ro.board.platform",
        "ro.hardware",
        "ro.product.device",
        "ro.build.display.id",
        "ro.huawei.build.version",
        "hw_sc.build.platform.version",
        "ro.build.version.harmony",
        "persist.sys.huawei.softversion"
    )

    /**
     * 读取真实属性判定机型。所有字段要么来自 getprop / sysfs，要么为空串，
     * 不做任何"猜一个值填上"的处理；拿不到就让界面显示"未提供"。
     */
    suspend fun loadDeviceInfo(): HuaweiDeviceInfo = withContext(Dispatchers.Default) {
        val props = try {
            ADBTools.getProps(deviceProps)
        } catch (e: Exception) {
            emptyMap()
        }
        val brand = props["ro.product.brand"].orEmpty().ifBlank { props["ro.product.manufacturer"].orEmpty() }
        val manufacturer = props["ro.product.manufacturer"].orEmpty()
        val model = props["ro.product.model"].orEmpty()
        val device = props["ro.product.device"].orEmpty()
        val buildId = props["ro.build.display.id"].orEmpty()
        val emuiRaw = props["ro.build.version.emui"].orEmpty()
        val emui = normalizeVersion(emuiRaw)
        val harmony = listOf(
            props["hw_sc.build.platform.version"].orEmpty(),
            props["ro.huawei.build.version"].orEmpty(),
            props["ro.build.version.harmony"].orEmpty(),
            props["persist.sys.huawei.softversion"].orEmpty()
        ).firstOrNull { it.isNotBlank() }?.trim().orEmpty()

        val brandLower = brand.lowercase()
        val manuLower = manufacturer.lowercase()
        val modelLower = model.lowercase()
        val isHonor = brandLower.contains("honor") || manuLower.contains("honor") || modelLower.contains("honor")
        val isHuawei = brandLower.contains("huawei") || manuLower.contains("huawei") ||
                modelLower.contains("huawei") || isHonor ||
                emuiRaw.isNotBlank() || harmony.isNotBlank() ||
                device.lowercase().startsWith("huawei") || device.lowercase().startsWith("honor")

        val osLabel = when {
            harmony.isNotBlank() && (emui.isBlank() || majorOf(harmony) >= majorOf(emui)) -> "HarmonyOS $harmony"
            emui.isNotBlank() -> "EMUI $emui"
            harmony.isNotBlank() -> "HarmonyOS $harmony"
            else -> "Android " + props["ro.build.version.release"].orEmpty().ifBlank { "?" }
        }

        val platform = props["ro.board.platform"].orEmpty()
        val hardware = props["ro.hardware"].orEmpty()
        val soc = when {
            platform.isBlank() -> hardware
            hardware.isBlank() || hardware == platform -> platform
            else -> "$platform · $hardware"
        }

        val mem = try { readMemoryMb() } catch (e: Exception) { 0L to 0L }
        val screen = try { readScreenSize() } catch (e: Exception) { 0 to 0 }

        HuaweiDeviceInfo(
            isHuawei = isHuawei,
            isHonor = isHonor,
            brand = brand.ifBlank { "Unknown" },
            model = model.ifBlank { "Unknown" },
            emuiVersion = emui,
            harmonyVersion = harmony,
            osLabel = osLabel,
            androidVersion = props["ro.build.version.release"].orEmpty().ifBlank { "?" },
            sdk = props["ro.build.version.sdk"].orEmpty().toIntOrNull() ?: 0,
            soc = soc.ifBlank { "Unknown" },
            device = device.ifBlank { "Unknown" },
            buildId = buildId.ifBlank { "Unknown" },
            totalRamMb = mem.first,
            availRamMb = mem.second,
            thermalZoneCount = try { readThermalZoneCount() } catch (e: Exception) { 0 },
            screenWidth = screen.first,
            screenHeight = screen.second,
            hasShizuku = try { ADBTools.isShizukuAvailable() } catch (e: Exception) { false },
            hasRoot = try { ADBTools.isRooted() } catch (e: Exception) { false },
            isDhizukuActive = try { ADBTools.isDhizukuActive() } catch (e: Exception) { false },
            rawProps = props
        )
    }

    /** "EmotionUI_12.0.0" → "12.0.0"。 */
    private fun normalizeVersion(raw: String): String {
        if (raw.isBlank()) return ""
        val idx = raw.indexOf('_')
        return (if (idx >= 0) raw.substring(idx + 1) else raw).trim()
    }

    private fun majorOf(version: String): Int =
        version.substringBefore('.').filter { it.isDigit() }.toIntOrNull() ?: 0

    /** `dumpsys meminfo` 的 Total RAM / Free RAM，读不到回退 /proc/meminfo。 */
    private fun readMemoryMb(): Pair<Long, Long> {
        val out = safeExec("dumpsys meminfo", 15).output
        fun find(label: String): Long {
            val match = Regex("$label:\\s*([0-9,]+)K").find(out) ?: return 0L
            return match.groupValues[1].replace(",", "").toLongOrNull()?.div(1024) ?: 0L
        }
        val total = find("Total RAM")
        val free = find("Free RAM")
        if (total > 0L) return total to free
        val meminfo = safeExec("cat /proc/meminfo", 10).output
        val totalKb = Regex("MemTotal:\\s*(\\d+)").find(meminfo)?.groupValues?.get(1)?.toLongOrNull()
        val availKb = Regex("MemAvailable:\\s*(\\d+)").find(meminfo)?.groupValues?.get(1)?.toLongOrNull()
        if (totalKb == null) return 0L to 0L
        return totalKb.div(1024) to (availKb ?: 0L).div(1024)
    }

    private fun readFreeRamMb(): Long = try { readMemoryMb().second } catch (e: Exception) { 0L }

    /** `wm size` 的 Physical size，例如 "1224x2700"。 */
    private fun readScreenSize(): Pair<Int, Int> {
        val out = safeExec("wm size 2>/dev/null", 10).output
        val match = Regex("Physical size:\\s*(\\d+)x(\\d+)").find(out) ?: return 0 to 0
        return (match.groupValues[1].toIntOrNull() ?: 0) to (match.groupValues[2].toIntOrNull() ?: 0)
    }

    /** /sys/class/thermal 下的 thermal_zone 节点数量。 */
    private fun readThermalZoneCount(): Int {
        val out = safeExec("ls /sys/class/thermal/ 2>/dev/null", 10).output
        return out.lineSequence().count { it.contains("thermal_zone") }
    }

    private fun safeExec(command: String, timeoutSec: Int): CommandResult = try {
        ADBTools.execPerfCommand(command, timeoutSec)
    } catch (e: Exception) {
        CommandResult("", e.javaClass.simpleName + ": " + e.message, -1)
    }

    // ============================================================ 权限与命令解析

    /** 该方法在当前设备上是否具备所需权限。 */
    fun hasPermission(method: HuaweiMethod, info: HuaweiDeviceInfo): Boolean = when (method.requiresPermission) {
        PERM_ROOT -> info.hasRoot
        PERM_SHIZUKU -> info.hasShizuku || info.hasRoot || info.isDhizukuActive
        else -> true
    }

    /** 该方法在当前设备上是否适用（华为专有项在非华为机型上不适用）。 */
    fun isApplicable(method: HuaweiMethod, info: HuaweiDeviceInfo?): Boolean =
        !(method.huaweiOnly && info != null && !info.isHuawei)

    /**
     * 解析实际下发的命令。
     *
     * 目前只有"降低渲染分辨率"需要在运行时按物理分辨率计算目标值：
     * 固定写死分辨率会在 720p 机型上反而放大渲染负担，因此这里按 0.85 倍现算。
     * 读不到物理分辨率时返回 SKIP 标记，执行结果会明确显示"跳过"，不会假装成功。
     */
    fun resolveCommand(method: HuaweiMethod, info: HuaweiDeviceInfo?): String {
        if (method.id == "gpu_render_scale") {
            val w = info?.screenWidth ?: 0
            val h = info?.screenHeight ?: 0
            if (w <= 0 || h <= 0) return "echo " + SKIP_MARKER + "_NO_PHYSICAL_SIZE"
            val targetW = (w * 17 / 20 / 4) * 4
            val targetH = (h * 17 / 20 / 4) * 4
            if (targetW <= 0 || targetH <= 0) return "echo " + SKIP_MARKER + "_NO_PHYSICAL_SIZE"
            return "wm size ${targetW}x${targetH} 2>/dev/null; " +
                    "echo \"size=${'$'}(wm size 2>/dev/null | tail -n1 | tr -d ' ')\"; " +
                    "echo " + method.marker
        }
        return method.command
    }

    // ============================================================ 执行

    private fun runOneInternal(method: HuaweiMethod, info: HuaweiDeviceInfo): HuaweiRunResult {
        val command = resolveCommand(method, info)
        val started = System.currentTimeMillis()
        val raw = safeExec(command, method.timeoutSec)
        return HuaweiRunResult(
            id = method.id,
            titleKey = method.titleKey,
            command = command,
            exitCode = raw.exitCode,
            stdout = raw.output,
            stderr = raw.error,
            durationMs = System.currentTimeMillis() - started,
            marker = method.marker
        )
    }

    /** 执行单条方法。 */
    suspend fun runOne(method: HuaweiMethod, info: HuaweiDeviceInfo? = null): HuaweiRunResult =
        withContext(Dispatchers.Default) {
            runOneInternal(method, info ?: loadDeviceInfo())
        }

    /**
     * 逐条执行 [methods]，每开始一条回调一次 [onProgress]（index 从 1 开始）。
     * 前后各读一次可用内存，得出真实释放量（读不到就是 0，不编造）。
     */
    suspend fun runAll(
        methods: List<HuaweiMethod>,
        info: HuaweiDeviceInfo? = null,
        onProgress: (index: Int, total: Int, method: HuaweiMethod) -> Unit = { _, _, _ -> }
    ): HuaweiRunReport = withContext(Dispatchers.Default) {
        val device = info ?: loadDeviceInfo()
        val startFree = readFreeRamMb()
        val started = System.currentTimeMillis()
        val results = ArrayList<HuaweiRunResult>(methods.size)
        methods.forEachIndexed { index, method ->
            onProgress(index + 1, methods.size, method)
            results += runOneInternal(method, device)
        }
        HuaweiRunReport(
            results = results,
            startFreeMemoryMb = startFree,
            endFreeMemoryMb = readFreeRamMb(),
            durationMs = System.currentTimeMillis() - started
        )
    }

    /** 执行某条方法的恢复命令；没有恢复命令返回 null。 */
    suspend fun runRollback(method: HuaweiMethod): HuaweiRunResult? = withContext(Dispatchers.Default) {
        val command = method.rollbackCommand ?: return@withContext null
        val started = System.currentTimeMillis()
        val raw = safeExec(command, method.timeoutSec)
        HuaweiRunResult(
            id = method.id,
            titleKey = method.titleKey,
            command = command,
            exitCode = raw.exitCode,
            stdout = raw.output,
            stderr = raw.error,
            durationMs = System.currentTimeMillis() - started,
            marker = "",
            isRollback = true
        )
    }

    // ============================================================ 验证

    /**
     * 逐条跑 [HuaweiMethod.verifyCommand]。
     *
     * 判定规则（与 docs/huawei_methods.md 一致）：
     * - 缺少所需权限 → permission（需权限）；
     * - 华为专有项在非华为机型上 → not_applicable（不适用）；
     * - 验证命令输出为空，或不包含 verifyExpect → invalid（无效）；
     * - 其余 → valid（有效）。
     *
     * 注意：部分 debug/net 属性在未设置前 `getprop` 返回空串，这类项首次验证会显示
     * "无效"，执行一次后再次验证即显示属性值——这是如实反映，不是误判。
     */
    suspend fun verifyAll(
        methods: List<HuaweiMethod>,
        info: HuaweiDeviceInfo? = null
    ): List<HuaweiVerifyResult> = withContext(Dispatchers.Default) {
        val device = info ?: loadDeviceInfo()
        methods.map { method ->
            val verify = method.verifyCommand
            when {
                !hasPermission(method, device) -> HuaweiVerifyResult(
                    method.id, VERIFY_PERMISSION, "", method.verifyExpect, verify
                )
                !isApplicable(method, device) -> HuaweiVerifyResult(
                    method.id, VERIFY_NOT_APPLICABLE, "", method.verifyExpect, verify
                )
                verify.isNullOrBlank() -> HuaweiVerifyResult(
                    method.id, VERIFY_NOT_APPLICABLE, "", null, null
                )
                else -> {
                    val raw = safeExec(verify, 12)
                    val text = (raw.output + "\n" + raw.error).trim()
                    val expect = method.verifyExpect
                    val ok = text.isNotBlank() && (expect.isNullOrBlank() || text.contains(expect, true))
                    HuaweiVerifyResult(
                        id = method.id,
                        status = if (ok) VERIFY_VALID else VERIFY_INVALID,
                        output = text.take(300),
                        expected = expect,
                        verifyCommand = verify
                    )
                }
            }
        }
    }
}
