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
import kotlinx.coroutines.launch

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
    onOpenAccessibility: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var scanning by remember { mutableStateOf(false) }
    var hits by remember { mutableStateOf<List<String>>(emptyList()) }
    var statusText by remember { mutableStateOf<String?>(null) }
    var busyDelete by remember { mutableStateOf<String?>(null) } // 正在删除的路径
    var confirmDelete by remember { mutableStateOf<String?>(null) } // 等待二次确认的路径

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

        // ---------------- 无障碍拦截（识别新装应用；删除仍在本页确认） ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("shield_a11y_title"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    AppStrings.get("shield_a11y_desc"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                )
                Spacer(Modifier.height(10.dp))
                LiquidButton(
                    onClick = onOpenAccessibility,
                    backdrop = backdrop,
                    modifier = Modifier.height(42.dp).fillMaxWidth(),
                    tint = AppTheme.accent
                ) {
                    BasicText(AppStrings.get("shield_a11y_open"), style = TextStyle(AppTheme.onAccent, 13.sp))
                }
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