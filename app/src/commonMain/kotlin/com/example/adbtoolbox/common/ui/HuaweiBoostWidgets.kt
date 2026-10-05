package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.perf.HuaweiPerf
import com.example.adbtoolbox.common.theme.AppLayout
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton

/**
 * 华为深度加速页的取色入口。
 *
 * 主色 / 次色 / 深色底 / 主色文字色全部转发到 [AppTheme]，因此主题切换（经典 / 国庆 / 华为）
 * 会自动带动本页配色，页面里不再出现硬编码的强调色。
 * [muted] / [ok] / [warn] / [bad] 是状态语义色，不属于主题色，保持固定值。
 */
object HwTheme {
    /** 主题主色（按钮/强调） */
    val accent: Color get() = AppTheme.accent

    /** 主题次色 */
    val accentAlt: Color get() = AppTheme.accentAlt

    /** 深色底（横幅/浮层） */
    val deep: Color get() = AppTheme.deep

    /** 主色上的文字色 */
    val onAccent: Color get() = AppTheme.onAccent

    val muted: Color = Color(0xFF8E8E93)
    val ok: Color = Color(0xFF34C759)
    val warn: Color = Color(0xFFFF9500)
    val bad: Color = Color(0xFFFF3B30)
}

/**
 * `%1$s` / `%1$d` 占位符替换。
 *
 * 不用 String.format：它在 Kotlin 公共代码里不是多平台 API，而本模块同时有 js / wasmJs / ios 目标。
 */
internal fun hwFormat(template: String, vararg args: Any?): String {
    var out = template
    args.forEachIndexed { index, value ->
        val slot = index + 1
        val text = value?.toString() ?: ""
        out = out.replace("%${slot}\$s", text).replace("%${slot}\$d", text)
    }
    return out
}

/** 勾选框。 */
@Composable
fun HwCheckBox(checked: Boolean, onToggle: () -> Unit, backdrop: Backdrop) {
    Box(
        Modifier
            .size(20.dp)
            // 原先是静态底色 + 无反馈点击：改成液态玻璃可点项，保留原选中色/未选中底色
            // （liquidGlassItem 按 tint.alpha * 0.45f 着色，这里除以 0.45f 还原原来浓度）
            .liquidGlassItem(
                backdrop = backdrop,
                corner = 5.dp,
                tint = if (checked) HwTheme.accent
                else Color.White.copy(alpha = (0.14f / 0.45f).coerceAtMost(1f)),
                onClick = onToggle
            )
            .border(
                1.dp,
                if (checked) HwTheme.accent else Color.White.copy(alpha = 0.24f),
                RoundedCornerShape(5.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) BasicText("✓", style = TextStyle(HwTheme.onAccent, 12.sp, FontWeight.Bold))
    }
}

/** 等宽文本块，用于原样展示 shell 命令与原始输出。 */
@Composable
fun HwMonoBlock(text: String, contentColor: Color, tint: Color = Color.Unspecified) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.32f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        BasicText(
            text,
            style = TextStyle(
                if (tint == Color.Unspecified) contentColor.copy(alpha = 0.85f) else tint,
                10.sp,
                fontFamily = FontFamily.Monospace
            )
        )
    }
}

/** 详情里的一行"标签"，与内容分两行展示，避免长命令被挤压。 */
@Composable
fun HwLabel(text: String, contentColor: Color) {
    BasicText(
        text,
        style = TextStyle(contentColor.copy(alpha = 0.5f), 10.sp, FontWeight.Medium)
    )
}

/** 验证结论徽章；[status] 为 null 表示尚未验证。 */
@Composable
fun HwVerifyChip(status: String?) {
    if (status == null) return
    val label: String
    val color: Color
    when (status) {
        HuaweiPerf.VERIFY_VALID -> {
            label = AppStrings.get("hw_verify_valid")
            color = HwTheme.ok
        }
        HuaweiPerf.VERIFY_INVALID -> {
            label = AppStrings.get("hw_verify_invalid")
            color = HwTheme.warn
        }
        HuaweiPerf.VERIFY_PERMISSION -> {
            label = AppStrings.get("hw_verify_need_permission")
            color = Color(0xFFAF52DE)
        }
        else -> {
            label = AppStrings.get("hw_verify_not_applicable")
            color = HwTheme.muted
        }
    }
    PerfBadge(label, color)
}

/** 小号操作按钮（执行 / 恢复 / 详情）。 */
@Composable
fun HwSmallButton(
    label: String,
    tint: Color,
    enabled: Boolean,
    backdrop: Backdrop,
    onClick: () -> Unit
) {
    LiquidButton(
        onClick = { if (enabled) onClick() },
        backdrop = backdrop,
        modifier = Modifier.height(32.dp),
        tint = if (enabled) tint else HwTheme.muted
    ) {
        BasicText(
            label,
            Modifier.padding(horizontal = 2.dp),
            style = TextStyle(HwTheme.onAccent, AppLayout.captionSize, FontWeight.Medium)
        )
    }
}

/** 分组标题行（点击折叠/展开）。 */
@Composable
fun HwGroupHeader(
    title: String,
    selectedCount: Int,
    total: Int,
    expanded: Boolean,
    contentColor: Color,
    backdrop: Backdrop,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            // 原先是纯文字点击区：改成液态玻璃可点项，行高与内边距不变
            .liquidGlassItem(backdrop = backdrop, corner = 10.dp, onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(HwTheme.accent)
        )
        Spacer(Modifier.width(8.dp))
        BasicText(title, style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Bold))
        Spacer(Modifier.weight(1f))
        BasicText(
            hwFormat(AppStrings.get("hw_group_count"), selectedCount, total),
            style = TextStyle(contentColor.copy(alpha = 0.55f), AppLayout.captionSize)
        )
        Spacer(Modifier.width(8.dp))
        BasicText(
            AppStrings.get(if (expanded) "hide_details" else "view_details"),
            style = TextStyle(contentColor.copy(alpha = 0.65f), 10.sp)
        )
    }
}

/**
 * 一条方法卡片：勾选 + 名称 + 收益 + 风险/权限/验证徽章 + 执行/恢复/详情。
 *
 * 命令原文、验证命令、恢复命令、真实输出全部可展开查看，失败时强制展示 stderr。
 */
@Composable
fun HwMethodCard(
    method: HuaweiPerf.HuaweiMethod,
    selected: Boolean,
    onToggle: () -> Unit,
    result: HuaweiPerf.HuaweiRunResult?,
    verify: HuaweiPerf.HuaweiVerifyResult?,
    running: Boolean,
    busy: Boolean,
    backdrop: Backdrop,
    contentColor: Color,
    onRun: () -> Unit,
    onRollback: () -> Unit
) {
    var details by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (running) HwTheme.accent.copy(alpha = 0.16f) else contentColor.copy(alpha = 0.05f)
            )
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HwCheckBox(checked = selected, onToggle = onToggle, backdrop = backdrop)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    AppStrings.get(method.titleKey),
                    style = TextStyle(contentColor, 13.sp, FontWeight.Medium)
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    AppStrings.get(method.gainKey),
                    style = TextStyle(contentColor.copy(alpha = 0.62f), AppLayout.captionSize)
                )
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PerfBadge(riskLabel(method.risk), riskColor(method.risk))
            permissionLabel(method.requiresPermission)?.let { PerfBadge(it, HwTheme.accentAlt) }
            if (method.isIrreversible) PerfBadge(AppStrings.get("hw_irreversible"), HwTheme.muted)
            HwVerifyChip(verify?.status)
            if (running) PerfBadge(AppStrings.get("running_item"), HwTheme.accent)
            result?.let {
                val label = when {
                    it.skipped -> AppStrings.get("hw_result_skip")
                    it.succeeded -> AppStrings.get("hw_result_ok")
                    else -> AppStrings.get("hw_result_fail")
                }
                val color = when {
                    it.skipped -> HwTheme.muted
                    it.succeeded -> HwTheme.ok
                    else -> HwTheme.bad
                }
                PerfBadge(label, color)
            }
        }

        result?.let { r ->
            Spacer(Modifier.height(5.dp))
            BasicText(
                hwFormat(AppStrings.get("hw_result_used"), r.durationMs) +
                        (if (r.summary.isNotBlank()) " · " + r.summary
                        else " · " + AppStrings.get("hw_no_output")),
                style = TextStyle(
                    if (r.succeeded) contentColor.copy(alpha = 0.7f) else HwTheme.bad,
                    10.sp,
                    fontFamily = FontFamily.Monospace
                )
            )
            if (!r.succeeded && !r.skipped) {
                val reason = listOf(r.stderr, r.stdout)
                    .firstOrNull { it.isNotBlank() }?.trim()?.take(400)
                    ?: ("exitCode=" + r.exitCode)
                Spacer(Modifier.height(3.dp))
                BasicText(
                    AppStrings.get("run_failed_reason") + ": " + reason,
                    style = TextStyle(HwTheme.bad, 10.sp, fontFamily = FontFamily.Monospace)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HwSmallButton(
                label = AppStrings.get("hw_run"),
                tint = HwTheme.accent,
                enabled = !busy,
                backdrop = backdrop,
                onClick = onRun
            )
            if (method.rollbackCommand != null) {
                HwSmallButton(
                    label = AppStrings.get("hw_rollback_run"),
                    tint = HwTheme.muted,
                    enabled = !busy,
                    backdrop = backdrop,
                    onClick = onRollback
                )
            }
            HwSmallButton(
                label = AppStrings.get(if (details) "hide_details" else "view_details"),
                tint = HwTheme.accentAlt,
                enabled = true,
                backdrop = backdrop,
                onClick = { details = !details }
            )
        }

        if (details) {
            Spacer(Modifier.height(8.dp))
            HwLabel(AppStrings.get("hw_principle"), contentColor)
            Spacer(Modifier.height(2.dp))
            BasicText(
                AppStrings.get(method.principleKey),
                style = TextStyle(contentColor.copy(alpha = 0.8f), AppLayout.captionSize)
            )

            Spacer(Modifier.height(6.dp))
            HwLabel(AppStrings.get("hw_command"), contentColor)
            Spacer(Modifier.height(2.dp))
            HwMonoBlock(method.command, contentColor)

            if (result != null && result.command != method.command) {
                Spacer(Modifier.height(4.dp))
                HwLabel(AppStrings.get("hw_command_actual"), contentColor)
                Spacer(Modifier.height(2.dp))
                HwMonoBlock(result.command, contentColor)
            }

            if (method.verifyCommand != null) {
                Spacer(Modifier.height(6.dp))
                HwLabel(AppStrings.get("hw_verify_command"), contentColor)
                Spacer(Modifier.height(2.dp))
                HwMonoBlock(method.verifyCommand, contentColor)
                if (!method.verifyExpect.isNullOrBlank()) {
                    Spacer(Modifier.height(3.dp))
                    BasicText(
                        AppStrings.get("hw_verify_expected") + ": " + method.verifyExpect,
                        style = TextStyle(contentColor.copy(alpha = 0.6f), 10.sp)
                    )
                }
                if (verify != null && verify.output.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    HwLabel(AppStrings.get("hw_verify_output"), contentColor)
                    Spacer(Modifier.height(2.dp))
                    HwMonoBlock(verify.output, contentColor)
                }
            }

            Spacer(Modifier.height(6.dp))
            HwLabel(AppStrings.get("hw_rollback"), contentColor)
            Spacer(Modifier.height(2.dp))
            if (method.rollbackCommand != null) {
                HwMonoBlock(method.rollbackCommand, contentColor)
            } else {
                BasicText(
                    AppStrings.get("hw_irreversible_hint"),
                    style = TextStyle(HwTheme.warn, 10.sp)
                )
            }

            result?.let { r ->
                if (r.stdout.isNotBlank() || r.stderr.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    HwLabel(AppStrings.get("hw_output"), contentColor)
                    Spacer(Modifier.height(2.dp))
                    val dump = buildString {
                        if (r.stdout.isNotBlank()) appendLine(r.stdout.trim().take(600))
                        if (r.stderr.isNotBlank()) appendLine("stderr: " + r.stderr.trim().take(600))
                    }
                    HwMonoBlock(dump.trim(), contentColor)
                }
            }
        }
    }
}
