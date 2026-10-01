package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.perf.PerfItem
import com.example.adbtoolbox.common.perf.PerfRunResult
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidToggle

/**
 * 性能加速 / 手机体检 两个新页面共用的玻璃组件。
 * 这里的每个组件都真实绑定状态与回调，不存在"只画不干活"的空壳。
 */

/** 风险等级 → 颜色。 */
fun riskColor(risk: String): Color = when (risk) {
    "risky" -> Color(0xFFFF3B30)
    "caution" -> Color(0xFFFF9500)
    else -> Color(0xFF34C759)
}

/** 风险等级 → 文案。 */
fun riskLabel(risk: String): String = when (risk) {
    "risky" -> AppStrings.get("risk_risky")
    "caution" -> AppStrings.get("risk_caution")
    else -> AppStrings.get("risk_safe")
}

/** 权限要求 → 文案，无要求返回 null。 */
fun permissionLabel(requiresPermission: String): String? = when (requiresPermission) {
    "root" -> AppStrings.get("requires_root")
    "shizuku" -> AppStrings.get("requires_shizuku")
    else -> null
}

@Composable
fun PerfBadge(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        BasicText(text, style = TextStyle(color, 10f.sp, FontWeight.Medium))
    }
}

/**
 * 一条 [PerfItem] 的选择行：左侧勾选框 + 名称 + 说明 + 徽章，右侧可展开查看真实命令。
 */
@Composable
fun PerfItemRow(
    item: PerfItem,
    selected: Boolean,
    onToggle: () -> Unit,
    contentColor: Color,
    backdrop: Backdrop,
    running: Boolean = false,
    result: PerfRunResult? = null
) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (running) AppTheme.accent.copy(alpha = 0.18f)
                else contentColor.copy(alpha = 0.05f)
            )
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (selected) AppTheme.accent else contentColor.copy(alpha = 0.15f)
                    )
                    .clickable(onClick = onToggle),
                contentAlignment = Alignment.Center
            ) {
                if (selected) BasicText("✓", style = TextStyle(Color.White, 13f.sp, FontWeight.Bold))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    AppStrings.get(item.nameKey),
                    style = TextStyle(contentColor, 14f.sp, FontWeight.Medium)
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    AppStrings.get(item.descKey),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), 11f.sp)
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.risk != "safe") PerfBadge(riskLabel(item.risk), riskColor(item.risk))
                    permissionLabel(item.requiresPermission)?.let {
                        PerfBadge(it, AppTheme.accentAlt)
                    }
                    if (result != null) {
                        PerfBadge(
                            if (result.succeeded) "OK" else "FAIL",
                            if (result.succeeded) Color(0xFF34C759) else Color(0xFFFF3B30)
                        )
                    }
                    if (running) PerfBadge(AppStrings.get("running_item"), AppTheme.accent)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                BasicText(
                    if (expanded) AppStrings.get("hide_details") else AppStrings.get("view_details"),
                    style = TextStyle(contentColor.copy(alpha = 0.7f), 10f.sp)
                )
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            BasicText(
                "$ ${item.command}",
                style = TextStyle(
                    contentColor.copy(alpha = 0.75f), 10f.sp, fontFamily = FontFamily.Monospace
                )
            )
            if (result != null && !result.succeeded) {
                Spacer(Modifier.height(6.dp))
                val reason = listOf(result.stderr, result.stdout)
                    .firstOrNull { it.isNotBlank() }?.trim()?.take(400)
                    ?: "exitCode=${result.exitCode}"
                BasicText(
                    "${AppStrings.get("run_failed_reason")}: $reason",
                    style = TextStyle(Color(0xFFFF6B6B), 10f.sp, fontFamily = FontFamily.Monospace)
                )
            }
            if (item.toggleOffCommand != null) {
                Spacer(Modifier.height(4.dp))
                BasicText(
                    "↺ ${item.toggleOffCommand}",
                    style = TextStyle(
                        contentColor.copy(alpha = 0.5f), 10f.sp, fontFamily = FontFamily.Monospace
                    )
                )
            }
        }
    }
}

/** 一行"标签 : 值"。 */
@Composable
fun PerfInfoRow(label: String, value: String, contentColor: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(label, style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
        Spacer(Modifier.width(12.dp))
        BasicText(value, style = TextStyle(contentColor, 12f.sp), maxLines = 1)
    }
}

/** 带左右两个动作按钮的一行。 */
@Composable
fun PerfButtonRow(
    backdrop: Backdrop,
    leftLabel: String,
    leftTint: Color,
    onLeft: () -> Unit,
    modifier: Modifier = Modifier,
    rightLabel: String? = null,
    rightTint: Color = Color(0xFF5AC8FA),
    onRight: (() -> Unit)? = null,
    leftEnabled: Boolean = true,
    rightEnabled: Boolean = true
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LiquidButton(
            onClick = { if (leftEnabled) onLeft() },
            backdrop = backdrop,
            modifier = Modifier.weight(1f).height(46.dp),
            tint = if (leftEnabled) leftTint else Color(0xFF8E8E93)
        ) {
            BasicText(
                leftLabel,
                Modifier.padding(horizontal = 6.dp),
                style = TextStyle(Color.White, 13f.sp, FontWeight.Medium)
            )
        }
        if (rightLabel != null && onRight != null) {
            LiquidButton(
                onClick = { if (rightEnabled) onRight() },
                backdrop = backdrop,
                modifier = Modifier.weight(1f).height(46.dp),
                tint = if (rightEnabled) rightTint else Color(0xFF8E8E93)
            ) {
                BasicText(
                    rightLabel,
                    Modifier.padding(horizontal = 6.dp),
                    style = TextStyle(Color.White, 13f.sp, FontWeight.Medium)
                )
            }
        }
    }
}

/** 带开关的一行设置。 */
@Composable
fun PerfToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    backdrop: Backdrop,
    contentColor: Color
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicText(label, Modifier.weight(1f), style = TextStyle(contentColor, 13f.sp))
        LiquidToggle(
            selected = { checked },
            onSelect = onCheckedChange,
            backdrop = backdrop,
            modifier = Modifier.size(51f.dp, 31f.dp)
        )
    }
}

/**
 * 确认对话框。用于所有高风险/不可逆操作，避免误触。
 * [onConfirm] 只有用户明确点"确定执行"才会调用。
 */
@Composable
fun PerfConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String = AppStrings.get("confirm_run"),
    cancelLabel: String = AppStrings.get("cancel"),
    contentColor: Color = Color.White,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xF21C1C1E))
                .padding(20.dp)
        ) {
            BasicText(title, style = TextStyle(contentColor, 17f.sp, FontWeight.Bold))
            Spacer(Modifier.height(10.dp))
            BasicText(message, style = TextStyle(contentColor.copy(alpha = 0.8f), 13f.sp))
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(contentColor.copy(alpha = 0.12f))
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(cancelLabel, style = TextStyle(contentColor, 14f.sp))
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFFF3B30))
                        .clickable(onClick = onConfirm),
                    contentAlignment = Alignment.Center
                ) {
                    BasicText(confirmLabel, style = TextStyle(Color.White, 14f.sp, FontWeight.Medium))
                }
            }
        }
    }
}

/** 可滚动的文本详情框，用于展示执行日志与体检明细。 */
@Composable
fun PerfTextBox(text: String, contentColor: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.35f))
            .padding(10.dp)
    ) {
        BasicText(
            text,
            Modifier.verticalScroll(rememberScrollState()),
            style = TextStyle(contentColor.copy(alpha = 0.9f), 11f.sp, fontFamily = FontFamily.Monospace)
        )
    }
}
