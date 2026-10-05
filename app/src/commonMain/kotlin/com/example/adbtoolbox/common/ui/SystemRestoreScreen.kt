package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.adbtoolbox.common.GameFpsStore
import com.example.adbtoolbox.common.perf.PerfSafety
import com.example.adbtoolbox.common.perf.SystemRestore
import com.example.adbtoolbox.common.theme.AppLayout
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 还原所有系统默认设置。
 *
 * 页面的第一件事就是**提醒**：性能优化里有整机级的改动，如果不想要了（或者发现
 * 系统设置里的某些入口不见了），在这里可以一键改回系统默认。
 *
 * 三条纪律：
 * 1. 每条还原步骤都能单独勾选，界面上写清"它到底把什么改回去了"；
 * 2. 执行后立刻回读并把回读结果展示出来，用户能自己核对，不是我说成功就成功；
 * 3. 没有提权时明确说明做不到，不静默失败。
 */
@Composable
fun SystemRestoreScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(SystemRestore.steps.map { it.id }.toSet()) }
    var includeGameOverlays by remember { mutableStateOf(true) }
    var running by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<SystemRestore.RestoreResult>>(emptyList()) }
    var overlayResults by remember { mutableStateOf<List<String>>(emptyList()) }

    val configuredGames = remember { GameFpsStore.load().keys.toList() }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppLayout.screenH)
    ) {
        Spacer(Modifier.height(AppLayout.screenTop))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
            Spacer(Modifier.width(AppLayout.headerGap))
            BasicText(
                AppStrings.get("restore_title"),
                style = TextStyle(contentColor, AppLayout.titleSize, FontWeight.Bold)
            )
        }
        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 提醒（醒目） ----------------
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x33FF3B30))
                .padding(AppLayout.cardPadCompact)
        ) {
            Column {
                BasicText(
                    AppStrings.get("restore_warn_title"),
                    style = TextStyle(Color(0xFFFF6B60), AppLayout.bodySize, FontWeight.Bold)
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    AppStrings.get("restore_warn_body"),
                    style = TextStyle(contentColor.copy(alpha = 0.85f), AppLayout.captionSize)
                )
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 安全检查表 ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("restore_steps_title"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                Spacer(Modifier.height(6.dp))
                BasicText(
                    AppStrings.get("restore_full_scope") + " (" +
                            SystemRestore.totalKeyCount + " + " + SystemRestore.totalPackageCount + ")",
                    style = TextStyle(Color(0xFF34C759), AppLayout.captionSize)
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    AppStrings.get("restore_steps_hint"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                )
                Spacer(Modifier.height(12.dp))
                SystemRestore.steps.forEach { step ->
                    val checked = step.id in selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = if (checked) selected - step.id else selected + step.id
                            }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (checked) AppTheme.accent else contentColor.copy(alpha = 0.15f)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (checked) {
                                BasicText("✓", style = TextStyle(AppTheme.onAccent, 12.sp, FontWeight.Bold))
                            }
                        }
                        Spacer(Modifier.width(AppLayout.innerGap))
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                AppStrings.get(step.nameKey),
                                style = TextStyle(contentColor, AppLayout.bodySize, FontWeight.Medium)
                            )
                            BasicText(
                                AppStrings.get(step.descKey),
                                style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                            )
                        }
                        if (step.needsReboot) {
                            PerfBadge(AppStrings.get("restore_needs_reboot"), Color(0xFFFF9500))
                        }
                    }
                }

                if (configuredGames.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            BasicText(
                                AppStrings.get("restore_step_overlay"),
                                style = TextStyle(contentColor, AppLayout.bodySize, FontWeight.Medium)
                            )
                            BasicText(
                                AppStrings.get("restore_step_overlay_desc"),
                                style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                            )
                        }
                        LiquidToggle(
                            selected = { includeGameOverlays },
                            onSelect = { includeGameOverlays = it },
                            backdrop = backdrop,
                            modifier = Modifier.size(51.dp, 31.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 执行 ----------------
        LiquidButton(
            onClick = { if (!running) confirmVisible = true },
            backdrop = backdrop,
            modifier = Modifier.height(50.dp).fillMaxWidth(),
            tint = if (running) Color(0xFF8E8E93) else Color(0xFFFF3B30)
        ) {
            BasicText(
                if (running) AppStrings.get("restore_running") else AppStrings.get("restore_start"),
                style = TextStyle(Color.White, 15.sp, FontWeight.Medium)
            )
        }
        Spacer(Modifier.height(8.dp))
        BasicText(
            AppStrings.get("restore_requires_privilege"),
            style = TextStyle(contentColor.copy(alpha = 0.55f), AppLayout.captionSize)
        )

        // 二次确认：把"将要执行什么"逐条列出来，避免误触
        if (confirmVisible) {
            Spacer(Modifier.height(AppLayout.sectionGap))
            GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(AppLayout.cardPad)) {
                    BasicText(
                        AppStrings.get("restore_confirm_title"),
                        style = TextStyle(Color(0xFFFF6B60), AppLayout.sectionTitleSize, FontWeight.Medium)
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        AppStrings.get("restore_confirm_body"),
                        style = TextStyle(contentColor.copy(alpha = 0.85f), AppLayout.bodySize)
                    )
                    Spacer(Modifier.height(8.dp))
                    SystemRestore.steps.filter { it.id in selected }.forEach { step ->
                        BasicText(
                            "· " + AppStrings.get(step.nameKey),
                            style = TextStyle(contentColor.copy(alpha = 0.8f), AppLayout.captionSize)
                        )
                    }
                    if (includeGameOverlays && configuredGames.isNotEmpty()) {
                        BasicText(
                            "· " + AppStrings.get("restore_step_overlay"),
                            style = TextStyle(contentColor.copy(alpha = 0.8f), AppLayout.captionSize)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(AppLayout.innerGap)) {
                        LiquidButton(
                            onClick = { confirmVisible = false },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).height(46.dp),
                            tint = Color(0xFF8E8E93)
                        ) {
                            BasicText(AppStrings.get("cancel"), style = TextStyle(Color.White, 14.sp))
                        }
                        LiquidButton(
                            onClick = {
                                confirmVisible = false
                                running = true
                                results = emptyList()
                                overlayResults = emptyList()
                                scope.launch {
                                    val chosen = SystemRestore.steps.filter { it.id in selected }
                                    results = SystemRestore.run(chosen)
                                    if (includeGameOverlays && configuredGames.isNotEmpty()) {
                                        overlayResults = SystemRestore.clearGameOverlays(configuredGames)
                                        // 系统级配置已清除，本地的按游戏配置也一并清掉，避免两边不一致
                                        withContext(Dispatchers.Default) { GameFpsStore.save(emptyMap()) }
                                    }
                                    running = false
                                }
                            },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1.4f).height(46.dp),
                            tint = Color(0xFFFF3B30)
                        ) {
                            BasicText(
                                AppStrings.get("restore_confirm_ok"),
                                style = TextStyle(Color.White, 14.sp, FontWeight.Medium)
                            )
                        }
                    }
                }
            }
        }

        // ---------------- 结果（含真实回读） ----------------
        if (results.isNotEmpty() || overlayResults.isNotEmpty()) {
            Spacer(Modifier.height(AppLayout.sectionGap))
            GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(AppLayout.cardPad)) {
                    BasicText(
                        AppStrings.get("restore_result_title"),
                        style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                    )
                    Spacer(Modifier.height(8.dp))
                    results.forEach { r ->
                        val ok = r.exitCode == 0
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BasicText(
                                    AppStrings.get(r.nameKey),
                                    Modifier.weight(1f),
                                    style = TextStyle(contentColor, AppLayout.bodySize, FontWeight.Medium)
                                )
                                PerfBadge(
                                    if (ok) AppStrings.get("restore_done") else AppStrings.get("restore_failed"),
                                    if (ok) Color(0xFF34C759) else Color(0xFFFF3B30)
                                )
                            }
                            BasicText(
                                AppStrings.get("restore_readback") + ": " +
                                        r.verifyOutput.ifBlank { AppStrings.get("unknown") },
                                style = TextStyle(
                                    contentColor.copy(alpha = 0.75f),
                                    11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            )
                            if (!ok && r.error.isNotBlank()) {
                                BasicText(
                                    r.error.take(200),
                                    style = TextStyle(Color(0xFFFF6B60), 11.sp)
                                )
                            }
                        }
                    }
                    overlayResults.forEach { line ->
                        BasicText(
                            line,
                            style = TextStyle(contentColor.copy(alpha = 0.7f), 11.sp, fontFamily = FontFamily.Monospace)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    BasicText(
                        AppStrings.get("restore_reboot_hint"),
                        style = TextStyle(Color(0xFFFF9500), AppLayout.captionSize)
                    )
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 现在受保护的组件（让用户知道不会再被"优化"掉） ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("restore_protected_title"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(6.dp))
                BasicText(
                    AppStrings.get("restore_protected_body"),
                    style = TextStyle(contentColor.copy(alpha = 0.7f), AppLayout.captionSize)
                )
                Spacer(Modifier.height(8.dp))
                listOf(
                    "com.android.settings",
                    "com.miui.securitycenter",
                    "com.coloros.oppoguardelf",
                    "com.vivo.bgapp",
                    "com.huawei.powergenie",
                    "com.oplus.osense",
                    "com.samsung.android.lool"
                ).forEach { pkg ->
                    val protected = PerfSafety.isProtectedPackage(pkg)
                    BasicText(
                        (if (protected) "✓ " else "· ") + pkg,
                        style = TextStyle(
                            contentColor.copy(alpha = if (protected) 0.8f else 0.5f),
                            11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}
