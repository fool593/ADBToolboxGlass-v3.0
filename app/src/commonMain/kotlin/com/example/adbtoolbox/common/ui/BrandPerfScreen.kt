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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.perf.BrandDatabase
import com.example.adbtoolbox.common.perf.PerfChannels
import com.example.adbtoolbox.common.perf.PerfRunner
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton

/**
 * v2.8 机型独立分类入口。
 *
 * 用户要求「每个机型的独立分类，而不是一个仅用于性能加速的选项，比如 vivo iQOO 性能优化、
 * 小米红米性能优化必须分开来使用」，本页就是这条要求的入口：一个品牌一张卡，
 * 各自独立进入，互不影响。
 *
 * 数据全部来自 [BrandDatabase] 的真实接口，界面不写死任何条数：
 * - 品牌名：`AppStrings.get(BrandDatabase.nameKeyOf(brandId))`（brand_<id>）
 * - 系统 UI 名：[BrandDatabase.romNameFor]（HyperOS / OriginOS / ColorOS ...）
 * - 专属条数：[BrandDatabase.brandSpecificItems]（generic 为全机型通用项）
 * - 根 / ADB 条数：[PerfChannels.rootItems] / [PerfChannels.adbItems]
 *
 * [onOpenBrand] 的参数是 brandId；空串（[BrandDatabase.BRAND_ALL]）表示"全机型通用"。
 */
@Composable
fun BrandPerfScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit,
    onOpenBrand: (String) -> Unit
) {
    var deviceInfo by remember { mutableStateOf<PerfRunner.PerfDeviceInfo?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        deviceInfo = PerfRunner.loadDeviceInfo()
        loading = false
    }

    val info = deviceInfo

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16f.dp)
    ) {
        Spacer(Modifier.height(24f.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                AppStrings.get("brand_perf_title"),
                style = TextStyle(contentColor, 24f.sp, FontWeight.Bold)
            )
            Spacer(Modifier.weight(1f))
            LiquidButton(
                onClick = onBack,
                backdrop = backdrop,
                modifier = Modifier.height(36.dp),
                tint = Color(0xFF8E8E93)
            ) {
                BasicText(
                    AppStrings.get("back"),
                    Modifier.padding(horizontal = 8.dp),
                    style = TextStyle(Color.White, 12f.sp)
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            AppStrings.get("brand_perf_hint"),
            style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
        )
        Spacer(Modifier.height(16.dp))

        // ---------------- 本机识别 ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(18.dp)) {
                BasicText(
                    AppStrings.get("brand_perf_local_device"),
                    style = TextStyle(contentColor, 15f.sp, FontWeight.Bold)
                )
                Spacer(Modifier.height(6.dp))
                if (loading || info == null) {
                    BasicText(
                        AppStrings.get("loading"),
                        style = TextStyle(contentColor.copy(alpha = 0.6f), 13f.sp)
                    )
                } else {
                    PerfInfoRow(
                        AppStrings.get("brand"),
                        "${info.brandRaw} · ${AppStrings.get(info.brandKey)}",
                        contentColor
                    )
                    PerfInfoRow(AppStrings.get("model"), info.model, contentColor)
                    PerfInfoRow(AppStrings.get("brand_perf_rom"), info.romName, contentColor)
                    Spacer(Modifier.height(8.dp))
                    val rootState = if (info.hasRoot) {
                        AppStrings.get("available")
                    } else {
                        AppStrings.get("not_detected")
                    }
                    val shizukuState = if (info.hasShizuku) {
                        AppStrings.get("available")
                    } else {
                        AppStrings.get("not_detected")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PerfBadge(
                            AppStrings.get("requires_root") + ": " + rootState,
                            if (info.hasRoot) Color(0xFF34C759) else Color(0xFF8E8E93)
                        )
                        PerfBadge(
                            AppStrings.get("requires_shizuku") + ": " + shizukuState,
                            if (info.hasShizuku) Color(0xFF34C759) else Color(0xFF8E8E93)
                        )
                    }
                }
            }
        }

        // ---------------- 每个机型一张独立分类卡 ----------------
        brandOrder.forEach { brandId ->
            val isUniversal = brandId == BrandDatabase.BRAND_GENERIC
            // 回调 id：全机型通用传空串，其余传真实品牌 id
            val callbackId = if (isUniversal) BrandDatabase.BRAND_ALL else brandId
            val specific = BrandDatabase.brandSpecificItems(brandId)
            val universal = BrandDatabase.universalItems
            val rootCount = if (isUniversal) PerfChannels.rootItems(universal).size
            else PerfChannels.rootItems(specific).size
            val adbCount = if (isUniversal) PerfChannels.adbItems(universal).size
            else PerfChannels.adbItems(specific).size
            val isLocal = info != null && !isUniversal && info.brandId == brandId

            Spacer(Modifier.height(14.dp))
            GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .width(4.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isLocal) AppTheme.accent else Color(0xFF8E8E93))
                        )
                        Spacer(Modifier.width(8.dp))
                        BasicText(
                            AppStrings.get(BrandDatabase.nameKeyOf(brandId)),
                            style = TextStyle(contentColor, 16f.sp, FontWeight.Bold)
                        )
                        Spacer(Modifier.weight(1f))
                        if (isLocal) {
                            PerfBadge(AppStrings.get("brand_perf_this_device"), AppTheme.accent)
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    BasicText(
                        BrandDatabase.romNameFor(brandId),
                        style = TextStyle(Color(0xFF5AC8FA), 12f.sp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isUniversal) {
                            PerfBadge(
                                String.format(
                                    AppStrings.get("brand_perf_universal_count"),
                                    universal.size
                                ),
                                AppTheme.accentAlt
                            )
                        } else {
                            PerfBadge(
                                String.format(
                                    AppStrings.get("brand_perf_specific_count"),
                                    specific.size
                                ),
                                Color(0xFFFF3B30)
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PerfBadge(
                            String.format(AppStrings.get("brand_perf_root_count"), rootCount),
                            Color(0xFFFF3B30)
                        )
                        PerfBadge(
                            String.format(AppStrings.get("brand_perf_adb_count"), adbCount),
                            AppTheme.accent
                        )
                    }
                    if (!isUniversal && specific.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            AppStrings.get("brand_perf_no_specific"),
                            style = TextStyle(contentColor.copy(alpha = 0.6f), 11f.sp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    LiquidButton(
                        onClick = { onOpenBrand(callbackId) },
                        backdrop = backdrop,
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        tint = if (isLocal) AppTheme.accent else AppTheme.accentAlt
                    ) {
                        BasicText(
                            AppStrings.get(
                                when {
                                    isUniversal -> "brand_perf_open_universal"
                                    isLocal -> "brand_perf_open_local"
                                    else -> "brand_perf_open_brand"
                                }
                            ),
                            Modifier.padding(horizontal = 8.dp),
                            style = TextStyle(Color.White, 13f.sp, FontWeight.Medium)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(90.dp))
    }
}

/**
 * 分类卡的展示顺序：先按用户点名的机型顺序（全机型通用 → 小米 → 华为 → 荣耀 → OPPO →
 * realme → 一加 → vivo → iQOO → 三星 → 魅族），再把 [BrandDatabase.allBrandIds] 里
 * 其余已支持品牌按数据层顺序追加。两个集合求并集，因此品牌库新增品牌也不会被界面漏掉。
 */
private val brandOrder: List<String> = run {
    val preferred = listOf(
        BrandDatabase.BRAND_GENERIC,
        BrandDatabase.BRAND_XIAOMI,
        BrandDatabase.BRAND_HUAWEI,
        BrandDatabase.BRAND_HONOR,
        BrandDatabase.BRAND_OPPO,
        BrandDatabase.BRAND_REALME,
        BrandDatabase.BRAND_ONEPLUS,
        BrandDatabase.BRAND_VIVO,
        BrandDatabase.BRAND_IQOO,
        BrandDatabase.BRAND_SAMSUNG,
        BrandDatabase.BRAND_MEIZU
    )
    val known = BrandDatabase.allBrandIds
    preferred.filter { it in known } + known.filter { it !in preferred }
}
