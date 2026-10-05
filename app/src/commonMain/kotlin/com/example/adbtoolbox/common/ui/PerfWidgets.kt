package com.example.adbtoolbox.common.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.perf.PerfItem
import com.example.adbtoolbox.common.perf.PerfRunResult
import com.example.adbtoolbox.common.theme.AppLayout
import com.example.adbtoolbox.common.theme.AppMotion
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
 * 提权通道区块的标题行：色条 + 标题 + 条数 + 缺通道警告 + 通道说明。
 *
 * 性能加速页与体检页共用同一个标题组件，保证「Root 一行、ADB / Shizuku 一行」
 * 的呈现与措辞完全一致；条数由调用方从数据层算好传入，组件本身不推断任何数字。
 */
@Composable
fun PerfChannelHeader(
    title: String,
    countText: String,
    barColor: Color,
    contentColor: Color,
    warning: String? = null,
    note: String? = null
) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barColor)
            )
            Spacer(Modifier.width(8.dp))
            BasicText(title, style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Bold))
            Spacer(Modifier.weight(1f))
            BasicText(countText, style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
        }
        if (warning != null) {
            Spacer(Modifier.height(4.dp))
            BasicText(warning, style = TextStyle(Color(0xFFFF9500), AppLayout.captionSize))
        }
        if (note != null) {
            Spacer(Modifier.height(2.dp))
            BasicText(note, style = TextStyle(contentColor.copy(alpha = 0.55f), 10f.sp))
        }
    }
}

/**
 * 一条 [PerfItem] 的选择行：左侧勾选框 + 名称 + 说明 + 徽章，右侧可展开查看真实命令。
 *
 * [unavailableNote] 非空表示本机缺少这条指令需要的提权通道（或 SDK / SoC / 品牌不适用）：
 * 勾选会被禁用并在徽章里写明原因，避免用户"点了没反应"却不知道为什么不生效。
 */
@Composable
fun PerfItemRow(
    item: PerfItem,
    selected: Boolean,
    onToggle: () -> Unit,
    contentColor: Color,
    backdrop: Backdrop,
    running: Boolean = false,
    result: PerfRunResult? = null,
    unavailableNote: String? = null
) {
    var expanded by remember { mutableStateOf(false) }
    // 不可用行的整体淡化（保留原本的 contentColor alpha，不覆盖调用方传入的透明度）
    val rowAlpha = if (unavailableNote != null) 0.62f else 1f
    // 运行中的高亮：按全局动效规范淡入淡出，避免背景硬切。
    // 用 animateFloatAsState + drawBehind 在绘制阶段读取进度，逐帧只重绘、不重组整行。
    val runningHighlight by animateFloatAsState(
        targetValue = if (running) 1f else 0f,
        animationSpec = if (running) AppMotion.fadeIn else AppMotion.fadeOut,
        label = "perfItemRunningHighlight"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .drawBehind {
                drawRect(
                    lerp(
                        contentColor.copy(alpha = 0.05f),
                        AppTheme.accent.copy(alpha = 0.18f),
                        runningHighlight
                    )
                )
            }
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    // 勾选框原先是静态底色 + 无反馈点击：改成液态玻璃可点项，保留原选中色/未选中底色
                    // （liquidGlassItem 按 tint.alpha * 0.45f 着色，这里除以 0.45f 还原原来浓度）
                    .liquidGlassItem(
                        backdrop = backdrop,
                        corner = 6.dp,
                        tint = when {
                            // 不可用：不给选中色，保持"灰掉的空框"
                            unavailableNote != null -> contentColor.copy(alpha = (0.08f / 0.45f).coerceAtMost(1f))
                            selected -> AppTheme.accent
                            else -> contentColor.copy(alpha = (0.15f / 0.45f).coerceAtMost(1f))
                        },
                        // 不可用时不再响应勾选：onClick = null 让 liquidGlassItem 不挂点击手势
                        onClick = if (unavailableNote == null) onToggle else null
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (selected) BasicText("✓", style = TextStyle(Color.White, 13f.sp, FontWeight.Bold))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                BasicText(
                    AppStrings.get(item.nameKey),
                    style = TextStyle(contentColor.copy(alpha = contentColor.alpha * rowAlpha), AppLayout.bodySize, FontWeight.Medium)
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    AppStrings.get(item.descKey),
                    style = TextStyle(contentColor.copy(alpha = contentColor.alpha * 0.6f * rowAlpha), AppLayout.captionSize)
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (item.risk != "safe") PerfBadge(riskLabel(item.risk), riskColor(item.risk))
                    permissionLabel(item.requiresPermission)?.let {
                        PerfBadge(it, AppTheme.accentAlt)
                    }
                    if (unavailableNote != null) {
                        PerfBadge(unavailableNote, Color(0xFFFF9500))
                    }
                    if (result != null) {
                        // 结果徽标出现：小幅上移 + 淡入（AppMotion.fadeIn）
                        val resultAppear = remember { Animatable(0f) }
                        LaunchedEffect(Unit) { resultAppear.animateTo(1f, AppMotion.fadeIn) }
                        PerfBadge(
                            if (result.succeeded) "OK" else "FAIL",
                            if (result.succeeded) Color(0xFF34C759) else Color(0xFFFF3B30),
                            Modifier.graphicsLayer {
                                alpha = resultAppear.value
                                translationY = (1f - resultAppear.value) * 4.dp.toPx()
                            }
                        )
                    }
                    if (running) PerfBadge(AppStrings.get("running_item"), AppTheme.accent)
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier
                    // 原先是纯文字点击区：改成液态玻璃可点项，尺寸/内边距不变
                    .liquidGlassItem(backdrop = backdrop, corner = 6.dp, onClick = { expanded = !expanded })
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
        Spacer(Modifier.width(AppLayout.innerGap))
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
                .padding(AppLayout.cardPad)
        ) {
            BasicText(title, style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Bold))
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
                    BasicText(cancelLabel, style = TextStyle(contentColor, AppLayout.bodySize))
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
                    BasicText(confirmLabel, style = TextStyle(Color.White, AppLayout.bodySize, FontWeight.Medium))
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
            style = TextStyle(contentColor.copy(alpha = 0.9f), AppLayout.captionSize, fontFamily = FontFamily.Monospace)
        )
    }
}
