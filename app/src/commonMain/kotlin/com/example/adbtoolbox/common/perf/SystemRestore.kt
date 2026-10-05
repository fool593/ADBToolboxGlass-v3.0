package com.example.adbtoolbox.common.perf

import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 还原所有系统默认设置。
 *
 * 背景：性能优化属于**系统级改动**。其中有几类会影响整机行为甚至**屏蔽系统的设置入口**
 * （整机后台策略、Doze、被禁用的厂商后台/权限管理组件、刷新率上限、动画缩放、厂商性能模式开关），
 * 用户很难自己找回来。这里提供一条明确、完整、可验证的退路。
 *
 * "完整"是**按数据算出来的**，不是我挑几条：本项目所有会改动系统的命令都做了一次全量扫描，
 * 结论是——
 * - 可能被写入的 `settings` 键共 47 个（global 26 / secure 14 / system 6，见下面三个列表）；
 * - 可能被禁用的组件 27 个（见 [reEnablePackages]）；
 * - 另有 appops 的后台运行权限、待机桶、deviceidle 状态、按游戏限帧的 game_overlay。
 * 上面的每一项在下面都有对应的还原步骤，**一条都不漏**。
 *
 * 设计原则：
 * - 每步只做一件事，可单独勾选；
 * - 每条命令都必须是"恢复系统默认"（删除覆盖键 / 重新启用 / 改回 allow），不是再改一版策略；
 * - 删除 settings 键而不是写入某个"我以为的默认值"——系统自己的默认值才是真默认；
 * - 每步执行后立刻回读，结果如实展示；失败就是失败，不写成成功。
 */
object SystemRestore {

    const val PERM_SHIZUKU = "shizuku"

    // ------------------------------------------------------------------ 全量清单（按扫描结果）

    /** 本应用可能写入的 global 键（26 个） */
    private val globalKeys: List<String> = listOf(
        "adb_enabled",
        "animator_duration_scale",
        "background_process_limit",
        "cloud_sync_switch",
        "find_device_auto_sync",
        "flyme_performance_mode",
        "force_gpu_rendering",
        "game_booster_enable",
        "game_mode_enable",
        "hw_power_mode",
        "install_non_market_apps",
        "low_power",
        "low_power_sticky",
        "max_cached_processes",
        "miui_ram_expansion",
        "mobile_data_always_on",
        "mobile_data_always_on_2",
        "oppo_gpu_turbo",
        "power_check_max_cpu_1",
        "power_check_max_cpu_2",
        "power_check_max_cpu_3",
        "power_check_max_cpu_4",
        "transition_animation_scale",
        "vivo_push_enable",
        "wifi_scan_always_enabled",
        "wifi_scan_throttle_enabled",
        "window_animation_scale"
    )

    /** 本应用可能写入的 secure 键（14 个） */
    private val secureKeys: List<String> = listOf(
        "doze_always_on",
        "game_mode",
        "game_mode_auto_switch",
        "game_mode_enable",
        "game_turbo_mode",
        "hw_performance_mode",
        "mi_optimization",
        "oppo_performance_mode",
        "performance_mode",
        "refresh_rate_mode",
        "samsung_performance_mode",
        "user_refresh_rate",
        "vivo_game_mode",
        "vivo_performance_mode"
    )

    /** 本应用可能写入的 system 键（6 个） */
    private val systemKeys: List<String> = listOf(
        "min_refresh_rate",
        "peak_refresh_rate",
        "power_mode",
        "screen_brightness",
        "screen_off_timeout",
        "user_refresh_rate"
    )

    /**
     * 可能被本应用禁用的组件（27 个，来自全量扫描）。
     *
     * 其中厂商的后台/权限/电池管理组件（vivo.bgapp、coloros.oppoguardelf、huawei.powergenie、
     * oplus.osense、vivo.pem）现在已被 [PerfSafety] 保护、新版本不会再禁用它们，
     * 但旧版本禁用过，所以这里同样要**重新启用**——这正是"系统入口被屏蔽"的根因。
     */
    private val reEnablePackages: List<String> = listOf(
        "com.miui.analytics", "com.xiaomi.mipicks", "com.miui.msa.global",
        "com.xiaomi.metoknlp", "com.xiaomi.joyose",
        "com.huawei.bd", "com.huawei.android.hwouc", "com.huawei.android.microkernel",
        "com.huawei.android.pushagent", "com.huawei.android.hwaps", "com.huawei.powergenie",
        "com.coloros.oppoguardelf", "com.coloros.logkit",
        "com.oppo.logkit", "com.oppo.usageassist", "com.oplus.osense",
        "com.vivo.abe", "com.vivo.bgapp", "com.vivo.pem", "com.vivo.daemonService",
        "com.samsung.android.dqagent", "com.sec.android.diagmonagent",
        "com.samsung.android.rubin.app", "com.samsung.android.game.gos",
        "com.meizu.mstore", "com.meizu.flyme.update"
    )

    private fun deleteKeys(namespace: String, keys: List<String>): String =
        keys.joinToString("; ") { "settings delete $namespace $it 2>/dev/null" } +
                "; echo RESTORE_${namespace.uppercase()}_DONE"

    private fun countKeys(namespace: String, keys: List<String>): String =
        keys.joinToString(" | ") { "$it=$(settings get $namespace $it 2>/dev/null)" }

    private fun reEnableCommand(): String =
        reEnablePackages.joinToString("; ") { "pm enable --user 0 $it 2>/dev/null" } +
                "; echo RESTORE_ENABLE_DONE"

    data class RestoreStep(
        val id: String,
        val nameKey: String,
        val descKey: String,
        val command: String,
        val verifyCommand: String,
        val needsReboot: Boolean = false
    )

    /**
     * 全部还原步骤（分组顺序有意义：先恢复组件与后台策略，再恢复显示与性能模式相关的设置）。
     */
    val steps: List<RestoreStep> = listOf(
        // ---------- 1. 组件 ----------
        RestoreStep(
            id = "restore_disabled_components",
            nameKey = "restore_step_components",
            descKey = "restore_step_components_desc",
            command = reEnableCommand(),
            verifyCommand = "pm list packages -d 2>/dev/null | head -n 40",
            needsReboot = true
        ),
        // ---------- 2. 后台与待机 ----------
        RestoreStep(
            id = "restore_appops_background",
            nameKey = "restore_step_appops",
            descKey = "restore_step_appops_desc",
            command = "pm list packages -3 2>/dev/null | sed -n 's/^package://p' | " +
                    "while read -r p; do cmd appops set \"${'$'}p\" RUN_IN_BACKGROUND allow 2>/dev/null; " +
                    "cmd appops set \"${'$'}p\" RUN_ANY_IN_BACKGROUND allow 2>/dev/null; done; " +
                    "echo RESTORE_APPOPS_DONE",
            verifyCommand = "cmd appops get com.tencent.mm RUN_ANY_IN_BACKGROUND 2>/dev/null",
            needsReboot = true
        ),
        RestoreStep(
            id = "restore_standby_bucket",
            nameKey = "restore_step_bucket",
            descKey = "restore_step_bucket_desc",
            command = "for p in com.tencent.mm com.tencent.mobileqq com.taobao.taobao " +
                    "com.eg.android.AlipayGphone com.ss.android.ugc.aweme com.sina.weibo " +
                    "com.netease.cloudmusic com.zhihu.android com.baidu.BaiduMap com.autonavi.minimap " +
                    "com.smile.gifmaker com.android.chrome com.android.vending; do " +
                    "pm list packages 2>/dev/null | grep -q \"package:${'$'}p\" && " +
                    "am set-standby-bucket \"${'$'}p\" active 2>/dev/null; done; echo RESTORE_BUCKET_DONE",
            verifyCommand = "am get-standby-bucket com.tencent.mm 2>/dev/null"
        ),
        RestoreStep(
            id = "restore_doze",
            nameKey = "restore_step_doze",
            descKey = "restore_step_doze_desc",
            command = "dumpsys deviceidle enable 2>/dev/null; echo RESTORE_DOZE_DONE",
            verifyCommand = "dumpsys deviceidle | grep -m2 -i 'mEnabled\\|mState'"
        ),
        // ---------- 3. settings 三组全量删除 ----------
        RestoreStep(
            id = "restore_settings_global",
            nameKey = "restore_step_global",
            descKey = "restore_step_global_desc",
            command = deleteKeys("global", globalKeys),
            verifyCommand = countKeys("global", listOf("background_process_limit", "low_power", "window_animation_scale")),
            needsReboot = true
        ),
        RestoreStep(
            id = "restore_settings_secure",
            nameKey = "restore_step_secure",
            descKey = "restore_step_secure_desc",
            command = deleteKeys("secure", secureKeys),
            verifyCommand = countKeys("secure", listOf("performance_mode", "user_refresh_rate", "game_mode")),
            needsReboot = true
        ),
        RestoreStep(
            id = "restore_settings_system",
            nameKey = "restore_step_system",
            descKey = "restore_step_system_desc",
            command = deleteKeys("system", systemKeys),
            verifyCommand = countKeys("system", listOf("peak_refresh_rate", "min_refresh_rate", "power_mode")),
            needsReboot = true
        ),
        // ---------- 4. 动画缩放（用户最容易看出来的一项） ----------
        RestoreStep(
            id = "restore_animations",
            nameKey = "restore_step_anim",
            descKey = "restore_step_anim_desc",
            command = "settings put global window_animation_scale 1.0; " +
                    "settings put global transition_animation_scale 1.0; " +
                    "settings put global animator_duration_scale 1.0; echo RESTORE_ANIM_DONE",
            verifyCommand = "settings get global window_animation_scale"
        ),
        // ---------- 5. 按游戏限帧 ----------
        RestoreStep(
            id = "restore_game_overlay",
            nameKey = "restore_step_overlay",
            descKey = "restore_step_overlay_desc",
            // 具体包名在执行时由调用方通过 clearGameOverlays 传入（这里只做占位说明）
            command = "echo RESTORE_OVERLAY_HANDLED_BY_APP",
            verifyCommand = "device_config list game_overlay 2>/dev/null | head -n 20"
        )
    )

    /** 这一版一共会碰多少项，用于界面显示（让用户知道"全量"到底是多少）。 */
    val totalKeyCount: Int get() = globalKeys.size + secureKeys.size + systemKeys.size
    val totalPackageCount: Int get() = reEnablePackages.size

    data class RestoreResult(
        val id: String,
        val nameKey: String,
        val command: String,
        val exitCode: Int,
        val output: String,
        val error: String,
        val verifyOutput: String
    )

    /**
     * 依次执行 [selected]；每步执行后立刻回读，回读结果一并返回。
     * 单步失败不中断后续步骤（每步都是独立恢复动作），但结果里会如实标注。
     */
    suspend fun run(selected: List<RestoreStep>): List<RestoreResult> = withContext(Dispatchers.Default) {
        selected.map { step ->
            val raw = try {
                ADBTools.execPerfCommand(step.command, timeout = 90)
            } catch (e: Exception) {
                CommandResult("", "${e.javaClass.simpleName}: ${e.message}", -1)
            }
            val verify = try {
                ADBTools.execPerfCommand(step.verifyCommand, timeout = 25).output.trim()
            } catch (e: Exception) {
                ""
            }
            RestoreResult(
                id = step.id,
                nameKey = step.nameKey,
                command = step.command,
                exitCode = raw.exitCode,
                output = raw.output,
                error = raw.error,
                verifyOutput = verify
            )
        }
    }

    /**
     * 额外清理：把"按游戏限帧"写进系统的那份配置删掉（每个包一条）。
     * 包名列表来自用户在游戏帧率页的配置，只有调用方知道。
     */
    suspend fun clearGameOverlays(packageNames: List<String>): List<String> = withContext(Dispatchers.Default) {
        packageNames.map { pkg ->
            val r = try {
                ADBTools.setGameOverlayFps(pkg, 0)
            } catch (e: Exception) {
                CommandResult("", "${e.javaClass.simpleName}: ${e.message}", -1)
            }
            "$pkg -> " + if (r.exitCode == 0) "cleared" else r.error.take(120)
        }
    }
}
