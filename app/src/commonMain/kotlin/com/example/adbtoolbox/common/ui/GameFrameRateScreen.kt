package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.perf.PerfRunner
import com.example.adbtoolbox.common.theme.AppLayout
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 游戏帧率（全机型）。
 *
 * 做的事只有一件真事：把**显示刷新率**固定到你选的档位，游戏与其它所有应用都会按这个帧率跑。
 * 没有"只改某个游戏"的假开关——AOSP 并没有开放按应用改刷新率的接口，所以这里不做假选项。
 *
 * 两条写入通道，按机型自动选择（这正是"华为需要 ADB 权限、其余机型不需要"的技术原因）：
 * 1. **应用权限直写**（非华为机型）：`peak_refresh_rate` / `min_refresh_rate` 在 system 命名空间，
 *    本应用已在 Manifest 声明 `WRITE_SETTINGS`，用户授予「修改系统设置」后即可直接写，无需 ADB。
 * 2. **ADB / Root**（华为、荣耀，或未授权、直写失败时兜底）：走 Shizuku / Root 执行 `settings put`。
 *
 * 写入后会**真实回读**当前刷新率并如实显示；部分机型需要熄屏再亮或切后台才会真正切换，界面会说明。
 */
@Composable
fun GameFrameRateScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenTerminal: (String) -> Unit
) {
    val scope = rememberCoroutineScope()

    var info by remember { mutableStateOf<PerfRunner.PerfDeviceInfo?>(null) }
    var writeGranted by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var applying by remember { mutableStateOf(false) }
    var selectedHz by remember { mutableStateOf(0f) }
    var beforeHz by remember { mutableStateOf(0f) }
    var afterHz by remember { mutableStateOf(0f) }
    var resultText by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        val loaded = withContext(Dispatchers.Default) { PerfRunner.loadDeviceInfo() }
        val granted = withContext(Dispatchers.Default) { ADBTools.isWriteSettingsGranted() }
        val current = withContext(Dispatchers.Default) { ADBTools.getCurrentRefreshRate() }
        info = loaded
        writeGranted = granted
        beforeHz = current
        afterHz = current
        loading = false
        if (selectedHz <= 0f) selectedHz = loaded.maxRefreshRate
    }

    LaunchedEffect(Unit) { reload() }

    // 华为 / 荣耀：厂商把刷新率键挪到了应用写不到的地方，必须走 ADB/Shizuku 或 Root
    val isHuaweiLike = info?.brandId == "huawei" || info?.brandId == "honor"
    val hasPrivilege = info?.hasAnyPrivilege == true

    // 可选帧率：来自本机真实支持的档位（读不到就只给最高档），保证不会给出本机做不到的选项
    val options: List<Float> = remember(info) {
        val supported = info?.supportedRefreshRates.orEmpty().filter { it > 1f }.distinct().sorted()
        if (supported.isEmpty()) listOfNotNull(info?.maxRefreshRate?.takeIf { it > 1f }) else supported
    }

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
                AppStrings.get("game_frame_rate"),
                style = TextStyle(contentColor, AppLayout.titleSize, FontWeight.Bold)
            )
        }
        Spacer(Modifier.height(6.dp))
        BasicText(
            AppStrings.get("gfr_hint"),
            style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
        )
        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 本机屏幕 ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                if (loading || info == null) {
                    BasicText(
                        AppStrings.get("loading"),
                        style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.bodySize)
                    )
                } else {
                    val device = info!!
                    BasicText(
                        AppStrings.get("gfr_device"),
                        style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                    )
                    Spacer(Modifier.height(10.dp))
                    PerfInfoRow(AppStrings.get("brand"), "${AppStrings.get(device.brandKey)} · ${device.romName}", contentColor)
                    PerfInfoRow(AppStrings.get("model"), device.model, contentColor)
                    PerfInfoRow(AppStrings.get("gfr_current"), "${beforeHz.toInt()} Hz", contentColor)
                    PerfInfoRow(AppStrings.get("gfr_max"), "${device.maxRefreshRate.toInt()} Hz", contentColor)
                    PerfInfoRow(
                        AppStrings.get("gfr_supported"),
                        if (options.isEmpty()) AppStrings.get("unknown")
                        else options.joinToString(" / ") { it.toInt().toString() } + " Hz",
                        contentColor
                    )
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 写入通道 ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("gfr_path"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(8.dp))
                if (isHuaweiLike) {
                    BasicText(
                        AppStrings.get("gfr_path_huawei_adb"),
                        style = TextStyle(Color(0xFFFF9500), AppLayout.bodySize)
                    )
                    Spacer(Modifier.height(6.dp))
                    PerfBadge(
                        if (hasPrivilege) AppStrings.get("gfr_adb_ready") else AppStrings.get("gfr_adb_missing"),
                        if (hasPrivilege) Color(0xFF34C759) else Color(0xFFFF3B30)
                    )
                } else if (writeGranted) {
                    BasicText(
                        AppStrings.get("gfr_path_direct"),
                        style = TextStyle(Color(0xFF34C759), AppLayout.bodySize)
                    )
                } else {
                    BasicText(
                        AppStrings.get("gfr_path_need_grant"),
                        style = TextStyle(Color(0xFFFF9500), AppLayout.bodySize)
                    )
                    Spacer(Modifier.height(10.dp))
                    LiquidButton(
                        onClick = onOpenWriteSettings,
                        backdrop = backdrop,
                        modifier = Modifier.height(42.dp).fillMaxWidth(),
                        tint = AppTheme.accent
                    ) {
                        BasicText(
                            AppStrings.get("gfr_grant"),
                            Modifier.padding(horizontal = 8.dp),
                            style = TextStyle(AppTheme.onAccent, 13.sp)
                        )
                    }
                    if (hasPrivilege) {
                        Spacer(Modifier.height(6.dp))
                        BasicText(
                            AppStrings.get("gfr_adb_ready"),
                            style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 选择帧率 ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("gfr_choose"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.chunked(3).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { hz ->
                                val isSelected = selectedHz.toInt() == hz.toInt()
                                LiquidButton(
                                    onClick = { selectedHz = hz },
                                    backdrop = backdrop,
                                    modifier = Modifier.weight(1f).height(44.dp),
                                    tint = if (isSelected) AppTheme.accent else AppTheme.deep
                                ) {
                                    BasicText(
                                        "${hz.toInt()} Hz",
                                        style = TextStyle(if (isSelected) AppTheme.onAccent else contentColor, 13.sp)
                                    )
                                }
                            }
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                LiquidButton(
                    onClick = {
                        if (applying || selectedHz <= 0f) return@LiquidButton
                        applying = true
                        resultText = null
                        scope.launch {
                            val target = selectedHz
                            // 华为/荣耀走 ADB；其余机型先用应用自身权限直写，失败再回退到 ADB/Root。
                            // 两条路都会把真实结果带回来，不做"看起来成功"。
                            val outcome: String = withContext(Dispatchers.Default) {
                                try {
                                    val direct = if (isHuaweiLike) {
                                        // 华为：应用权限写不进去，直接用 ADB/Root
                                        null
                                    } else {
                                        ADBTools.setRefreshRateDirect(target)
                                    }
                                    if (direct == true) {
                                        "direct"
                                    } else {
                                        val r = ADBTools.setRefreshRate(target, both = true)
                                        if (r.exitCode == 0 && !r.error.contains("denied", true)) "shell"
                                        else r.error.ifBlank { r.output }.take(200)
                                    }
                                } catch (e: Exception) {
                                    "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                                }
                            }
                            // 写入后真实回读
                            afterHz = withContext(Dispatchers.Default) { ADBTools.getCurrentRefreshRate() }
                            resultText = when (outcome) {
                                "direct" -> AppStrings.get("gfr_result_direct")
                                "shell" -> AppStrings.get("gfr_result_shell")
                                else -> "${AppStrings.get("operation_failed")}: $outcome"
                            }
                            applying = false
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.height(48.dp).fillMaxWidth(),
                    tint = if (applying) Color(0xFF8E8E93) else AppTheme.accentAlt
                ) {
                    BasicText(
                        if (applying) AppStrings.get("gfr_applying") else AppStrings.get("gfr_apply"),
                        style = TextStyle(AppTheme.onAccent, 14.sp, FontWeight.Medium)
                    )
                }
                Spacer(Modifier.height(8.dp))
                LiquidButton(
                    onClick = {
                        if (applying) return@LiquidButton
                        applying = true
                        resultText = null
                        scope.launch {
                            val outcome = withContext(Dispatchers.Default) {
                                try {
                                    val r = ADBTools.resetRefreshRateToAuto()
                                    if (r.exitCode == 0) "shell" else r.error.ifBlank { r.output }.take(200)
                                } catch (e: Exception) {
                                    "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                                }
                            }
                            afterHz = withContext(Dispatchers.Default) { ADBTools.getCurrentRefreshRate() }
                            resultText = if (outcome == "shell") {
                                AppStrings.get("gfr_result_shell")
                            } else {
                                "${AppStrings.get("operation_failed")}: $outcome"
                            }
                            applying = false
                        }
                    },
                    backdrop = backdrop,
                    modifier = Modifier.height(44.dp).fillMaxWidth(),
                    tint = Color(0xFF8E8E93)
                ) {
                    BasicText(AppStrings.get("gfr_reset_auto"), style = TextStyle(Color.White, 13.sp))
                }
            }
        }

        // ---------------- 执行结果（真实回读）----------------
        resultText?.let { text ->
            Spacer(Modifier.height(AppLayout.sectionGap))
            GlassCard(backdrop = backdrop, pageType = "home") {
                Column(Modifier.padding(AppLayout.cardPad)) {
                    BasicText(
                        AppStrings.get("gfr_result"),
                        style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(text, style = TextStyle(contentColor.copy(alpha = 0.85f), AppLayout.bodySize))
                    Spacer(Modifier.height(6.dp))
                    PerfInfoRow(AppStrings.get("gfr_before"), "${beforeHz.toInt()} Hz", contentColor)
                    PerfInfoRow(AppStrings.get("gfr_after"), "${afterHz.toInt()} Hz", contentColor)
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        AppStrings.get("gfr_need_screen_toggle"),
                        style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                    )
                    Spacer(Modifier.height(10.dp))
                    LiquidButton(
                        onClick = {
                            scope.launch {
                                afterHz = withContext(Dispatchers.Default) { ADBTools.getCurrentRefreshRate() }
                            }
                        },
                        backdrop = backdrop,
                        modifier = Modifier.height(40.dp).fillMaxWidth(),
                        tint = AppTheme.accent
                    ) {
                        BasicText(AppStrings.get("gfr_recheck"), style = TextStyle(AppTheme.onAccent, 13.sp))
                    }
                    Spacer(Modifier.height(8.dp))
                    LiquidButton(
                        onClick = {
                            onOpenTerminal("settings get system peak_refresh_rate\nsettings get system min_refresh_rate")
                        },
                        backdrop = backdrop,
                        modifier = Modifier.height(40.dp).fillMaxWidth(),
                        tint = AppTheme.deep
                    ) {
                        BasicText(AppStrings.get("gfr_check_in_terminal"), style = TextStyle(contentColor, 13.sp))
                    }
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))
        BasicText(
            AppStrings.get("gfr_note"),
            style = TextStyle(contentColor.copy(alpha = 0.5f), AppLayout.captionSize)
        )
        Spacer(Modifier.height(80.dp))
    }
}
