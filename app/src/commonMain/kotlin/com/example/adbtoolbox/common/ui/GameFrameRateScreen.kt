package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.GameAppInfo
import com.example.adbtoolbox.common.GameFpsStore
import com.example.adbtoolbox.common.perf.PerfRunner
import com.example.adbtoolbox.common.theme.AppLayout
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidToggle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 游戏帧率（全机型）。
 *
 * 两件事：
 * 1. **全局**：把显示刷新率固定到指定档位（30~165Hz，具体档位来自本机真实支持的列表）。
 * 2. **按游戏**：给指定游戏单独配一个帧率，游戏进入前台时自动切过去，离开后恢复自适应。
 *
 * 写入通道按机型自动选择，这正是"只有华为需要 ADB"的技术原因：
 * - **非华为 / 荣耀**：`peak_refresh_rate` / `min_refresh_rate` 在 system 命名空间，
 *   拿到「修改系统设置」(`WRITE_SETTINGS`) 后应用可**直接写，不需要 ADB、也不需要 root**。
 * - **华为 / 荣耀**：厂商把刷新率键挪到应用写不到的位置，只能走 Shizuku(ADB) 或 Root。
 *
 * 另外提供一个**有提权时**才可用的增强项：Android 13+ 的系统级按游戏限帧
 * （`device_config put game_overlay <包名> mode=2,fps=N`），由系统自己按游戏生效，
 * 不需要工具箱一直待在前台。没有提权时界面不会假装能用。
 *
 * 关于"监听"的诚实说明：应用内监听只在**本页在前台时**生效（不做常驻后台服务，
 * 因为那需要前台服务权限与常驻通知）。要长期生效请用系统级 game_overlay。
 */
@Composable
fun GameFrameRateScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onOpenTerminal: (String) -> Unit,
    onOpenUsageAccess: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()

    var info by remember { mutableStateOf<PerfRunner.PerfDeviceInfo?>(null) }
    var writeGranted by remember { mutableStateOf(false) }
    var usageGranted by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    var games by remember { mutableStateOf<List<GameAppInfo>>(emptyList()) }
    var includeAll by remember { mutableStateOf(false) }
    var selectedPkg by remember { mutableStateOf("") }
    var perGame by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var selectedHz by remember { mutableStateOf(0f) }
    var perGameHz by remember { mutableStateOf(0f) }

    var applying by remember { mutableStateOf(false) }
    var beforeHz by remember { mutableStateOf(0f) }
    var afterHz by remember { mutableStateOf(0f) }
    var resultText by remember { mutableStateOf<String?>(null) }
    var systemOverlayText by remember { mutableStateOf<String?>(null) }

    var watchEnabled by remember { mutableStateOf(false) }
    var watchStatus by remember { mutableStateOf("") }

    suspend fun reload() {
        val loaded = withContext(Dispatchers.Default) { PerfRunner.loadDeviceInfo() }
        val granted = withContext(Dispatchers.Default) { ADBTools.isWriteSettingsGranted() }
        val usage = withContext(Dispatchers.Default) { ADBTools.hasUsageAccess() }
        val current = withContext(Dispatchers.Default) { ADBTools.getCurrentRefreshRate() }
        val stored = withContext(Dispatchers.Default) { GameFpsStore.load() }
        info = loaded
        writeGranted = granted
        usageGranted = usage
        beforeHz = current
        afterHz = current
        perGame = stored
        if (selectedHz <= 0f) selectedHz = loaded.maxRefreshRate
        loading = false
    }

    suspend fun loadGames(all: Boolean) {
        games = withContext(Dispatchers.Default) { ADBTools.listGameApps(all) }
    }

    LaunchedEffect(Unit) {
        reload()
        loadGames(false)
    }
    LaunchedEffect(includeAll) { loadGames(includeAll) }

    val isHuaweiLike = info?.brandId == "huawei" || info?.brandId == "honor"
    val hasPrivilege = info?.hasAnyPrivilege == true

    /** 档位：标准阶梯 ∪ 本机真实支持的刷新率，去重升序。用户要求最高支持 165。 */
    val options: List<Int> = remember(info) {
        val ladder = listOf(30, 45, 60, 90, 120, 144, 165)
        val panel = info?.supportedRefreshRates.orEmpty().filter { it > 1f }.map { it.toInt() }
        (ladder + panel).distinct().sorted()
    }
    val panelMax = info?.maxRefreshRate?.toInt() ?: 0

    /** 真正写入一次刷新率：华为走 shell；其余机型先用应用权限直写，失败再回退 shell。 */
    suspend fun writeRefresh(target: Float): String = withContext(Dispatchers.Default) {
        try {
            val direct = if (isHuaweiLike) null else ADBTools.setRefreshRateDirect(target)
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

    fun outcomeText(outcome: String): String = when (outcome) {
        "direct" -> AppStrings.get("gfr_result_direct")
        "shell" -> AppStrings.get("gfr_result_shell")
        else -> "${AppStrings.get("operation_failed")}: $outcome"
    }

    // ---------------- 应用内监听：本页在前台时，游戏切到前台就套用它配置的帧率 ----------------
    LaunchedEffect(watchEnabled, perGame, info?.hasAnyPrivilege) {
        if (!watchEnabled) {
            watchStatus = ""
            return@LaunchedEffect
        }
        var lastPkg = ""
        var lastHz = 0f
        while (true) {
            val fg = withContext(Dispatchers.Default) { ADBTools.getForegroundPackage() }
            if (fg.isNotEmpty()) {
                val target = perGame[fg]
                if (target != null) {
                    val hz = target.toFloat()
                    if (lastPkg != fg || lastHz != hz) {
                        val outcome = writeRefresh(hz)
                        afterHz = withContext(Dispatchers.Default) { ADBTools.getCurrentRefreshRate() }
                        lastPkg = fg
                        lastHz = hz
                        watchStatus = "${AppStrings.get("gfr_watch_applied")} ${target}Hz · ${outcomeText(outcome)}"
                    }
                } else if (lastPkg.isNotEmpty()) {
                    // 离开了配置过的游戏 → 恢复自适应，避免影响别的应用
                    withContext(Dispatchers.Default) { ADBTools.resetRefreshRateToAuto() }
                    lastPkg = ""
                    lastHz = 0f
                    watchStatus = AppStrings.get("gfr_watch_restored")
                }
            }
            delay(2000)
        }
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
                    PerfInfoRow(AppStrings.get("gfr_max"), "$panelMax Hz", contentColor)
                    PerfInfoRow(
                        AppStrings.get("gfr_supported"),
                        if (options.isEmpty()) AppStrings.get("unknown") else options.joinToString(" / ") + " Hz",
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
                    BasicText(AppStrings.get("gfr_path_huawei_adb"), style = TextStyle(Color(0xFFFF9500), AppLayout.bodySize))
                    Spacer(Modifier.height(6.dp))
                    PerfBadge(
                        if (hasPrivilege) AppStrings.get("gfr_adb_ready") else AppStrings.get("gfr_adb_missing"),
                        if (hasPrivilege) Color(0xFF34C759) else Color(0xFFFF3B30)
                    )
                } else {
                    BasicText(
                        if (writeGranted) AppStrings.get("gfr_path_direct") else AppStrings.get("gfr_path_need_grant"),
                        style = TextStyle(
                            if (writeGranted) Color(0xFF34C759) else Color(0xFFFF9500),
                            AppLayout.bodySize
                        )
                    )
                    if (!writeGranted) {
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
                    }
                    Spacer(Modifier.height(6.dp))
                    BasicText(
                        AppStrings.get("gfr_no_root_needed"),
                        style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                    )
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 全局帧率 ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("gfr_global_title"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    AppStrings.get("gfr_global_hint"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                )
                Spacer(Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.chunked(4).forEach { rowItems ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            rowItems.forEach { hz ->
                                val isSelected = selectedHz.toInt() == hz
                                val overPanel = panelMax in 1 until hz
                                LiquidButton(
                                    onClick = { selectedHz = hz.toFloat() },
                                    backdrop = backdrop,
                                    modifier = Modifier.weight(1f).height(42.dp),
                                    tint = if (isSelected) AppTheme.accent else Color(0xFF8E8E93)
                                ) {
                                    BasicText(
                                        if (overPanel) "$hz*" else "$hz",
                                        style = TextStyle(if (isSelected) AppTheme.onAccent else Color.White, 13.sp)
                                    )
                                }
                            }
                            repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                BasicText(
                    AppStrings.get("gfr_over_panel_note"),
                    style = TextStyle(contentColor.copy(alpha = 0.55f), AppLayout.captionSize)
                )
                Spacer(Modifier.height(12.dp))
                LiquidButton(
                    onClick = {
                        if (applying || selectedHz <= 0f) return@LiquidButton
                        applying = true
                        resultText = null
                        scope.launch {
                            val outcome = writeRefresh(selectedHz)
                            afterHz = withContext(Dispatchers.Default) { ADBTools.getCurrentRefreshRate() }
                            resultText = outcomeText(outcome)
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
                            resultText = outcomeText(outcome)
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

        Spacer(Modifier.height(AppLayout.sectionGap))

        // ---------------- 按游戏设置 ----------------
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("gfr_per_game_title"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    AppStrings.get("gfr_per_game_hint"),
                    style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                )
                Spacer(Modifier.height(10.dp))

                // 使用情况访问（监听前台应用需要它）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PerfBadge(
                        if (usageGranted) AppStrings.get("gfr_usage_granted") else AppStrings.get("gfr_usage_need"),
                        if (usageGranted) Color(0xFF34C759) else Color(0xFFFF9500)
                    )
                    if (!usageGranted) {
                        Spacer(Modifier.width(8.dp))
                        LiquidButton(
                            onClick = onOpenUsageAccess,
                            backdrop = backdrop,
                            modifier = Modifier.height(34.dp),
                            tint = AppTheme.accent
                        ) {
                            BasicText(
                                AppStrings.get("gfr_grant_usage"),
                                Modifier.padding(horizontal = 8.dp),
                                style = TextStyle(AppTheme.onAccent, 12.sp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                // 只显示游戏 / 显示全部应用
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(
                        AppStrings.get("gfr_show_all_apps"),
                        style = TextStyle(contentColor, AppLayout.bodySize),
                        modifier = Modifier.weight(1f)
                    )
                    LiquidToggle(
                        selected = { includeAll },
                        onSelect = { includeAll = it },
                        backdrop = backdrop,
                        modifier = Modifier.size(51.dp, 31.dp)
                    )
                }
                Spacer(Modifier.height(10.dp))

                if (games.isEmpty()) {
                    BasicText(
                        AppStrings.get("gfr_games_empty"),
                        style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.bodySize)
                    )
                } else {
                    // 固定高度 + LazyColumn：本页外层是 verticalScroll，内嵌列表必须限高
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(games, key = { it.packageName }) { game ->
                            val configured = perGame[game.packageName]
                            val isSelected = selectedPkg == game.packageName
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .liquidGlassItem(
                                        backdrop = backdrop,
                                        corner = 12.dp,
                                        // 选中用主题主色；未选中用中性灰的低透明度，深浅模式下都能看清文字
                                        tint = if (isSelected) AppTheme.accent else Color(0xFF8E8E93).copy(alpha = 0.35f),
                                        onClick = {
                                            selectedPkg = game.packageName
                                            perGameHz = (configured ?: panelMax).toFloat()
                                        }
                                    )
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    BasicText(
                                        game.label,
                                        style = TextStyle(contentColor, AppLayout.bodySize, FontWeight.Medium),
                                        maxLines = 1
                                    )
                                    BasicText(
                                        game.packageName,
                                        style = TextStyle(
                                            contentColor.copy(alpha = 0.5f),
                                            10.sp,
                                            fontFamily = FontFamily.Monospace
                                        ),
                                        maxLines = 1
                                    )
                                }
                                if (configured != null) {
                                    PerfBadge("${configured}Hz", AppTheme.accentAlt)
                                }
                            }
                        }
                    }
                }

                if (selectedPkg.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    BasicText(
                        "${AppStrings.get("gfr_set_for")}: $selectedPkg",
                        style = TextStyle(contentColor, AppLayout.bodySize, FontWeight.Medium)
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        options.chunked(4).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                rowItems.forEach { hz ->
                                    val isSelected = perGameHz.toInt() == hz
                                    LiquidButton(
                                        onClick = { perGameHz = hz.toFloat() },
                                        backdrop = backdrop,
                                        modifier = Modifier.weight(1f).height(40.dp),
                                        tint = if (isSelected) AppTheme.accent else Color(0xFF8E8E93)
                                    ) {
                                        BasicText(
                                            "$hz",
                                            style = TextStyle(if (isSelected) AppTheme.onAccent else Color.White, 12.sp)
                                        )
                                    }
                                }
                                repeat(4 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LiquidButton(
                        onClick = {
                            val target = perGameHz.toInt()
                            if (target <= 0) return@LiquidButton
                            val updated = perGame + (selectedPkg to target)
                            perGame = updated
                            GameFpsStore.save(updated)
                            resultText = AppStrings.get("gfr_saved_for_game")
                        },
                        backdrop = backdrop,
                        modifier = Modifier.height(44.dp).fillMaxWidth(),
                        tint = AppTheme.accentAlt
                    ) {
                        BasicText(AppStrings.get("gfr_save_for_game"), style = TextStyle(AppTheme.onAccent, 13.sp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidButton(
                            onClick = {
                                val updated = perGame - selectedPkg
                                perGame = updated
                                GameFpsStore.save(updated)
                                resultText = AppStrings.get("gfr_cleared_for_game")
                            },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).height(42.dp),
                            tint = Color(0xFF8E8E93)
                        ) {
                            BasicText(AppStrings.get("gfr_clear_for_game"), style = TextStyle(Color.White, 12.sp))
                        }
                        LiquidButton(
                            onClick = {
                                scope.launch {
                                    val target = perGame[selectedPkg] ?: perGameHz.toInt()
                                    if (target > 0) {
                                        val outcome = writeRefresh(target.toFloat())
                                        afterHz = withContext(Dispatchers.Default) { ADBTools.getCurrentRefreshRate() }
                                        resultText = outcomeText(outcome)
                                    }
                                }
                            },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).height(42.dp),
                            tint = AppTheme.accent
                        ) {
                            BasicText(AppStrings.get("gfr_apply_now"), style = TextStyle(AppTheme.onAccent, 12.sp))
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                // 应用内监听（只在本页前台时有效，诚实标注）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        BasicText(
                            AppStrings.get("gfr_watch_title"),
                            style = TextStyle(contentColor, AppLayout.bodySize, FontWeight.Medium)
                        )
                        BasicText(
                            AppStrings.get("gfr_watch_note"),
                            style = TextStyle(contentColor.copy(alpha = 0.55f), AppLayout.captionSize)
                        )
                    }
                    LiquidToggle(
                        selected = { watchEnabled },
                        onSelect = { watchEnabled = it },
                        backdrop = backdrop,
                        modifier = Modifier.size(51.dp, 31.dp)
                    )
                }
                if (watchStatus.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    BasicText(watchStatus, style = TextStyle(AppTheme.accentAlt, AppLayout.captionSize))
                }
            }
        }

        // ---------------- 系统级按游戏限帧（仅在有提权时可用） ----------------
        Spacer(Modifier.height(AppLayout.sectionGap))
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(AppLayout.cardPad)) {
                BasicText(
                    AppStrings.get("gfr_advanced_title"),
                    style = TextStyle(contentColor, AppLayout.sectionTitleSize, FontWeight.Medium)
                )
                Spacer(Modifier.height(4.dp))
                BasicText(
                    if (hasPrivilege) AppStrings.get("gfr_advanced_hint") else AppStrings.get("gfr_advanced_need_privilege"),
                    style = TextStyle(
                        if (hasPrivilege) contentColor.copy(alpha = 0.6f) else Color(0xFFFF9500),
                        AppLayout.captionSize
                    )
                )
                if (hasPrivilege && selectedPkg.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LiquidButton(
                            onClick = {
                                scope.launch {
                                    val target = perGame[selectedPkg] ?: perGameHz.toInt()
                                    val r = withContext(Dispatchers.Default) {
                                        ADBTools.setGameOverlayFps(selectedPkg, target)
                                    }
                                    systemOverlayText = if (r.exitCode == 0) {
                                        "${AppStrings.get("gfr_system_ok")} ${r.output.take(160)}"
                                    } else {
                                        "${AppStrings.get("gfr_system_failed")} ${r.error.take(200)}"
                                    }
                                }
                            },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).height(42.dp),
                            tint = AppTheme.accent
                        ) {
                            BasicText(AppStrings.get("gfr_apply_system"), style = TextStyle(AppTheme.onAccent, 12.sp))
                        }
                        LiquidButton(
                            onClick = {
                                scope.launch {
                                    val r = withContext(Dispatchers.Default) {
                                        ADBTools.setGameOverlayFps(selectedPkg, 0)
                                    }
                                    systemOverlayText = if (r.exitCode == 0) {
                                        AppStrings.get("gfr_system_cleared")
                                    } else {
                                        "${AppStrings.get("gfr_system_failed")} ${r.error.take(200)}"
                                    }
                                }
                            },
                            backdrop = backdrop,
                            modifier = Modifier.weight(1f).height(42.dp),
                            tint = Color(0xFF8E8E93)
                        ) {
                            BasicText(AppStrings.get("gfr_clear_system"), style = TextStyle(Color.White, 12.sp))
                        }
                    }
                }
                systemOverlayText?.let {
                    Spacer(Modifier.height(8.dp))
                    BasicText(it, style = TextStyle(contentColor.copy(alpha = 0.8f), AppLayout.captionSize))
                }
            }
        }

        // ---------------- 执行结果（真实回读） ----------------
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
                            onOpenTerminal(
                                "settings get system peak_refresh_rate\n" +
                                        "settings get system min_refresh_rate\n" +
                                        if (selectedPkg.isNotEmpty()) "device_config get game_overlay $selectedPkg" else "dumpsys display | grep -m1 refreshRate"
                            )
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
