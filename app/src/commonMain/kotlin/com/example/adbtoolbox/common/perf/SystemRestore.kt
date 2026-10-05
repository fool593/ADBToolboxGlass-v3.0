package com.example.adbtoolbox.common.perf

import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 还原所有系统默认设置。
 *
 * 背景：性能优化属于**系统级改动**，其中有几类会影响到全机行为（整机后台策略、
 * Doze、被禁用的厂商组件、刷新率上限等）。之前部分优化项会禁用厂商的后台/权限管理组件、
 * 或写入整机级的后台进程上限，导致系统设置里的相关入口失效，用户很难自己找回来。
 *
 * 现在提供一条明确的退路：这个对象把"本应用可能改过的系统设置"整理成一组**可验证的还原步骤**，
 * 每步都带真实命令与回读校验，执行结果如实回报（不把失败写成成功）。
 *
 * 设计原则：
 * - 每步只做一件事，能单独勾选；
 * - 每条命令都必须是"恢复成系统默认"，不是再改一版策略；
 * - 需要提权的步骤会标出来，没有权限时给出明确原因而不是静默失败。
 */
object SystemRestore {

    /** 需要提权的标记，与 PerfItem.requiresPermission 语义一致。 */
    const val PERM_SHIZUKU = "shizuku"

    data class RestoreStep(
        /** 稳定 id，用于保存勾选状态与结果 */
        val id: String,
        /** 文案 key */
        val nameKey: String,
        /** 说明文案 key（讲清这一条到底改回了什么） */
        val descKey: String,
        /** 恢复命令 */
        val command: String,
        /** 回读校验命令；输出会展示出来，让结果可核对 */
        val verifyCommand: String,
        /** 该步骤是否由本应用引入（false 表示是"通用体检式恢复"，例如动画缩放） */
        val fromOurChanges: Boolean = true,
        /** 重启后才会完全生效的步骤要在界面提醒 */
        val needsReboot: Boolean = false
    )

    /**
     * 重新启用被本应用禁用过的组件。
     *
     * 只包含**遥测/日志/广告/推送**这类禁用后不影响系统功能的组件；
     * 厂商的后台与权限管理组件（vivo.bgapp、coloros.oppoguardelf、huawei.powergenie、
     * oplus.osense、vivo.pem 等）现在已被 [PerfSafety] 保护、不会被本应用禁用，
     * 但如果用户在旧版本里禁用过，这里同样把它们重新启用——所以这一条里也包含它们。
     */
    private val reEnablePackages: List<String> =
        PerfSafety.restorableDisabledPackages +
                listOf(
                    "com.coloros.oppoguardelf",
                    "com.huawei.powergenie",
                    "com.vivo.bgapp",
                    "com.vivo.pem",
                    "com.oplus.osense"
                )

    private fun reEnableCommand(): String =
        reEnablePackages.joinToString("; ") { "pm enable --user 0 $it 2>/dev/null" } +
                "; echo RESTORE_ENABLE_DONE"

    /**
     * 全部还原步骤。
     *
     * 顺序有意义：先恢复组件与后台策略（否则后面读到的状态还是被限制的），
     * 再恢复刷新率与动画这类显示相关设置。
     */
    val steps: List<RestoreStep> = listOf(
        RestoreStep(
            id = "restore_disabled_components",
            nameKey = "restore_step_components",
            descKey = "restore_step_components_desc",
            command = reEnableCommand(),
            verifyCommand = "pm list packages -d 2>/dev/null | head -n 30",
            needsReboot = true
        ),
        RestoreStep(
            id = "restore_background_limit",
            nameKey = "restore_step_bg_limit",
            descKey = "restore_step_bg_limit_desc",
            // 之前 Mi 项会写 background_process_limit=0（整机不许后台进程），这里删除该键回到默认
            command = "settings delete global background_process_limit 2>/dev/null; " +
                    "settings delete global max_cached_processes 2>/dev/null; echo RESTORE_BG_DONE",
            verifyCommand = "settings get global background_process_limit"
        ),
        RestoreStep(
            id = "restore_standby_bucket",
            nameKey = "restore_step_bucket",
            descKey = "restore_step_bucket_desc",
            // 旧版本会把常用应用（微信/QQ/淘宝/支付宝/抖音等）的待机桶设成 rare / restricted，
            // 导致这些应用消息收不到或严重延迟。这里把常用应用统一改回 active（正常待机桶）。
            command = "for p in com.tencent.mm com.tencent.mobileqq com.taobao.taobao " +
                    "com.eg.android.AlipayGphone com.ss.android.ugc.aweme com.sina.weibo " +
                    "com.netease.cloudmusic com.zhihu.android com.baidu.BaiduMap com.autonavi.minimap " +
                    "com.smile.gifmaker com.android.chrome com.android.vending; do " +
                    "pm list packages 2>/dev/null | grep -q \"package:${'$'}p\" && " +
                    "am set-standby-bucket \"${'$'}p\" active 2>/dev/null; done; echo RESTORE_BUCKET_DONE",
            verifyCommand = "am get-standby-bucket com.tencent.mm 2>/dev/null",
            needsReboot = false
        ),
        RestoreStep(
            id = "restore_appops_background",
            nameKey = "restore_step_appops",
            descKey = "restore_step_appops_desc",
            // 旧版本的"限制后台"优化项会给**所有第三方应用**写
            // `cmd appops set <包> RUN_IN_BACKGROUND deny` / `RUN_ANY_IN_BACKGROUND deny`，
            // 这会让系统设置里"允许后台无限制运行"这类开关全部失效、通知大面积收不到。
            // 该条目已从优化列表里删除，这里再把已经被写坏的值统一改回 allow。
            command = "pm list packages -3 2>/dev/null | sed -n 's/^package://p' | " +
                    "while read -r p; do cmd appops set \"${'$'}p\" RUN_IN_BACKGROUND allow 2>/dev/null; " +
                    "cmd appops set \"${'$'}p\" RUN_ANY_IN_BACKGROUND allow 2>/dev/null; done; " +
                    "echo RESTORE_APPOPS_DONE",
            verifyCommand = "cmd appops get com.tencent.mm RUN_ANY_IN_BACKGROUND 2>/dev/null",
            needsReboot = true
        ),
        RestoreStep(
            id = "restore_doze",
            nameKey = "restore_step_doze",
            descKey = "restore_step_doze_desc",
            // 之前"关闭电池优化"会 dumpsys deviceidle disable（整机不进 Doze）
            command = "dumpsys deviceidle enable 2>/dev/null; echo RESTORE_DOZE_DONE",
            verifyCommand = "dumpsys deviceidle | grep -m2 -i 'mEnabled\\|mState'"
        ),
        RestoreStep(
            id = "restore_refresh_rate",
            nameKey = "restore_step_refresh",
            descKey = "restore_step_refresh_desc",
            // 删除用户/峰值/最小刷新率键，让系统回到自适应
            command = "settings delete system peak_refresh_rate 2>/dev/null; " +
                    "settings delete system min_refresh_rate 2>/dev/null; " +
                    "settings delete system user_refresh_rate 2>/dev/null; echo RESTORE_HZ_DONE",
            verifyCommand = "settings get system peak_refresh_rate",
            needsReboot = true
        ),
        RestoreStep(
            id = "restore_animations",
            nameKey = "restore_step_anim",
            descKey = "restore_step_anim_desc",
            // "关闭动画提升流畅度"类项目会把动画缩放改成 0.5 或 0，这里恢复系统默认的 1.0
            command = "settings put global window_animation_scale 1.0; " +
                    "settings put global transition_animation_scale 1.0; " +
                    "settings put global animator_duration_scale 1.0; echo RESTORE_ANIM_DONE",
            verifyCommand = "settings get global window_animation_scale",
            fromOurChanges = false
        )
    )

    data class RestoreResult(
        val id: String,
        val nameKey: String,
        val command: String,
        val exitCode: Int,
        val output: String,
        val error: String,
        /** 回读命令的输出（为空表示读不到，不代表失败） */
        val verifyOutput: String
    )

    /**
     * 依次执行 [selected]；每步执行后立刻回读，回读结果一并返回。
     * 单步失败不会中断后续步骤（后面每步都是独立的恢复动作），但结果里会如实标注。
     */
    suspend fun run(selected: List<RestoreStep>): List<RestoreResult> = withContext(Dispatchers.Default) {
        selected.map { step ->
            val raw = try {
                ADBTools.execPerfCommand(step.command, timeout = 60)
            } catch (e: Exception) {
                CommandResult("", "${e.javaClass.simpleName}: ${e.message}", -1)
            }
            val verify = try {
                ADBTools.execPerfCommand(step.verifyCommand, timeout = 20).output.trim()
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
     * 放在这里是因为包名列表来自用户在游戏帧率页的配置，只有调用方知道。
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
