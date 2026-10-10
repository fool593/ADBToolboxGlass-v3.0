package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.example.adbtoolbox.common.Shield
import com.example.adbtoolbox.common.theme.AppLayout
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 游龙式安全护盾页：root 全盘恶意脚本扫描。
 *
 * 承诺（写进界面，也写进代码）：**只列出、只提示，绝不自动删除**。
 * 删除必须由用户对每一条目二次确认后才会执行；命中规则是启发式的，可能有误报，
 * 所以每一步都询问，删除前还会再次确认。
 */
@Composable
fun ShieldScanScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit,
    onRequestShizuku: () -> Unit = {},
    onOpenAccessibility: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<String>>(emptyList()) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var busyDelete by remember { mutableStateOf<String?>(null) } // 正在删除的路径
    var confirmDelete by remember { mutableStateOf<String?>(null) } // 等待二次确认的路径
    var confirmPanicStop by remember { mutableStateOf(false) }
    var panicMsg by remember { mutableStateOf<String?>(null) }
    var a11yOn by remember { mutableStateOf<Boolean?>(null) }
    var rootOn by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        a11yOn = Shield.accessibilityEnabled()
        rootOn = Shield.rootAvailable()
    }
    // 实时状态：无障碍服务自己维护的连接标记（androidMain 真实探测）+ settings 回读兜底
    val a11yOnLive = com.example.adbtoolbox.common.AppCache.accessibilityServiceConnected.value || a11yOn == true

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
                AppStrings.get("shield_title"),
                style = TextStyle(contentColor, AppLayout.titleSize, FontWeight.Bold)
            )
        }
        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 醒目说明 ----------------
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0x33FF9500))
                .padding(AppLayout.cardPadCompact)
        ) {
            BasicText(
                AppStrings.get("shield_warn"),
                style = TextStyle(contentColor.copy(alpha = 0.9f), AppLayout.captionSize)
            )
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 扫描与结果 ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                LiquidButton(
                    onClick = {
                        if (scanning) return@LiquidButton
                        scanning = true
                        hits = emptyList()
                        statusText = AppStrings.get("shield_run")
                        scope.launch {
                            val r = Shield.scan()
                            val lines = r.output.lineSequence().filter { it.isNotBlank() }.toList()
                            hits = lines
                            statusText = if (lines.isEmpty()) {
                                AppStrings.get("shield_clean")
                            } else {
                                AppStrings.get("shield_found") + ": " + lines.size
                            }
                            scanning = false
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.height(48.dp).fillMaxWidth(),
                    tint = if (scanning) Color(0xFF8E8E93) else Color(0xFFFF3B30)
                ) {
                    BasicText(
                        if (scanning) AppStrings.get("shield_running") else AppStrings.get("shield_start"),
                        style = TextStyle(Color.White, 15.sp, FontWeight.Medium)
                    )
                }
                statusText?.let { s ->
                    Spacer(Modifier.height(8.dp))
                    BasicText(s, style = TextStyle(AppTheme.accentAlt, AppLayout.bodySize))
                }
                if (hits.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    BasicText(
                        AppStrings.get("shield_ask_before_delete"),
                        style = TextStyle(Color(0xFFFF9500), AppLayout.captionSize)
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(hits, key = { it }) { path ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicText(
                                    path,
                                    Modifier.weight(1f),
                                    style = TextStyle(
                                        contentColor.copy(alpha = 0.85f),
                                        10.sp,
                                        fontFamily = FontFamily.Monospace
                                    ),
                                    maxLines = 2
                                )
                                Spacer(Modifier.width(6.dp))
                                LiquidButton(
                                    onClick = { confirmDelete = path },
                                    backdrop = backdrop,
                                    modifier = Modifier.height(32.dp),
                                    tint = Color(0xFFFF3B30)
                                ) {
                                    BasicText(
                                        if (busyDelete == path) AppStrings.get("shield_deleting") else AppStrings.get("shield_delete"),
                                        style = TextStyle(Color.White, 11.sp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 二次确认删除
        confirmDelete?.let { path ->
            Spacer(Modifier.height(AppLayout.sectionGap))
            GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(AppLayout.cardPad)) {
                    BasicText(
                        AppStrings.get("shield_confirm_title"),
                        style = TextStyle(Color(0xFFFF6B60), AppLayout.bodySize, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        path,
                        style = TextStyle(contentColor.copy(alpha = 0.85f), 11.sp, fontFamily = FontFamily.Monospace)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidButton(
                            onClick = { confirmDelete = null },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).height(44.dp),
                            tint = Color(0xFF8E8E93)
                        ) {
                            BasicText(AppStrings.get("cancel"), style = TextStyle(Color.White, 13.sp))
                        }
                        LiquidButton(
                            onClick = {
                                confirmDelete = null
                                busyDelete = path
                                scope.launch {
                                    val r = Shield.delete(path)
                                    statusText = if (r.exitCode == 0) {
                                        AppStrings.get("shield_deleted") + ": " + path
                                    } else {
                                        AppStrings.get("shield_delete_failed") + ": " + r.error
                                    }
                                    hits = hits - path
                                    busyDelete = null
                                }
                            },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1.4f).height(44.dp),
                            tint = Color(0xFFFF3B30)
                        ) {
                            BasicText(
                                AppStrings.get("shield_confirm_delete"),
                                style = TextStyle(Color.White, 13.sp, FontWeight.Medium)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 紧急逃生（音量键连按 5 次触发，继承游龙护盾） ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("shield_panic_title"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    AppStrings.get("shield_panic_desc"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                )
                Spacer(Modifier.height(10.dp))
                val panicCount = com.example.adbtoolbox.common.AppCache.panicTrigger.value
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        AppStrings.get("shield_panic_status") + panicCount,
                        style = TextStyle(contentColor, AppLayout.bodySize),
                        modifier = Modifier.weight(1f)
                    )
                    if (panicCount > 0) {
                        LiquidButton(
                            onClick = { confirmPanicStop = true },
                            backdrop = backdrop,
                            modifier = Modifier.height(36.dp),
                            tint = Color(0xFFFF6A3D)
                        ) {
                            BasicText(AppStrings.get("shield_panic_act"), style = TextStyle(Color.White, 12.sp))
                        }
                    }
                }
                if (confirmPanicStop) {
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        AppStrings.get("shield_panic_confirm"),
                        style = TextStyle(contentColor.copy(alpha = 0.7f), AppLayout.captionSize)
                    )
                    Spacer(Modifier.height(6.dp))
                    LiquidButton(
                        onClick = {
                            confirmPanicStop = false
                            scope.launch {
                                panicMsg = withContext(Dispatchers.Default) {
                                    com.example.adbtoolbox.common.Shield.forceStopLastNewApp()
                                }
                            }
                        },
                        backdrop = backdrop,
                        modifier = Modifier.height(38.dp).fillMaxWidth(),
                        tint = Color(0xFFE5484D)
                    ) {
                        BasicText(AppStrings.get("shield_panic_confirm_btn"), style = TextStyle(Color.White, 13.sp))
                    }
                }
                if (panicMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    BasicText(panicMsg!!, style = TextStyle(Color(0xFF34C759), AppLayout.captionSize))
                }
            }
        }

        // ---------------- 三项授权（Shizuku / 无障碍 / Root，真实状态） ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("shield_perm_title"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    AppStrings.get("shield_perm_desc"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                )
                Spacer(Modifier.height(10.dp))
                // Shizuku：读全局三态
                ShieldPermRow(
                    title = AppStrings.get("ob_perm_shizuku"),
                    desc = AppStrings.get("ob_perm_shizuku_desc"),
                    statusText = when (com.example.adbtoolbox.common.AppCache.shizukuState.value) {
                        "granted" -> AppStrings.get("ob_perm_ok")
                        "no_permission" -> AppStrings.get("ob_perm_shizuku_no_perm")
                        "not_running" -> AppStrings.get("ob_perm_shizuku_not_running")
                        else -> AppStrings.get("ob_perm_unknown")
                    },
                    ok = com.example.adbtoolbox.common.AppCache.shizukuState.value == "granted",
                    actionText = AppStrings.get("ob_perm_shizuku_action"),
                    onAction = onRequestShizuku,
                    backdrop = backdrop,
                    contentColor = contentColor
                )
                Spacer(Modifier.height(14.dp))
                // 无障碍：真实读系统已启用列表
                ShieldPermRow(
                    title = AppStrings.get("shield_a11y_title"),
                    desc = AppStrings.get("shield_a11y_desc"),
                    statusText = when (a11yOnLive) {
                        true -> AppStrings.get("shield_a11y_enabled")
                        false -> AppStrings.get("shield_a11y_disabled")
                        null -> AppStrings.get("ob_perm_unknown")
                    },
                    ok = a11yOnLive == true,
                    actionText = AppStrings.get("shield_a11y_open"),
                    onAction = onOpenAccessibility,
                    backdrop = backdrop,
                    contentColor = contentColor
                )
                Spacer(Modifier.height(14.dp))
                // Root：真实探针 su -c id
                ShieldPermRow(
                    title = AppStrings.get("ob_perm_root"),
                    desc = AppStrings.get("ob_perm_root_desc"),
                    statusText = when (rootOn) {
                        true -> AppStrings.get("shield_root_ok")
                        false -> AppStrings.get("ob_perm_root_optional")
                        null -> AppStrings.get("ob_perm_unknown")
                    },
                    ok = rootOn == true,
                    actionText = null,
                    onAction = {},
                    backdrop = backdrop,
                    contentColor = contentColor
                )
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))
        BasicText(
            AppStrings.get("shield_note"),
            style = TextStyle(contentColor.copy(alpha = 0.5f), AppLayout.captionSize)
        )
        Spacer(Modifier.height(80.dp))
    }
}
@Composable
private fun ShieldPermRow(
    title: String,
    desc: String,
    statusText: String,
    ok: Boolean,
    actionText: String?,
    onAction: () -> Unit,
    backdrop: Backdrop,
    contentColor: Color
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(title, style = TextStyle(contentColor, AppLayout.bodySize, FontWeight.Medium))
                BasicText(desc, style = TextStyle(contentColor.copy(alpha = 0.55f), AppLayout.captionSize))
            }
            PerfBadge(statusText, if (ok) Color(0xFF34C759) else Color(0xFFFF9500))
        }
        if (!ok && actionText != null) {
            Spacer(Modifier.height(8.dp))
            LiquidButton(
                onClick = onAction,
                backdrop = backdrop,
                modifier = Modifier.height(38.dp).fillMaxWidth(),
                tint = AppTheme.accent
            ) {
                BasicText(actionText, style = TextStyle(AppTheme.onAccent, 12.sp))
            }
        }
    }
}
