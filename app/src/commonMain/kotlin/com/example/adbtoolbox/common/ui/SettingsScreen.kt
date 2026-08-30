package com.example.adbtoolbox.common.ui

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
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppSettings
import com.example.adbtoolbox.common.AppStrings
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.shapes.RoundedRectangle

@Composable
fun SettingsScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onPickWallpaper: () -> Unit = {},
    onClearWallpaper: () -> Unit = {},
    onGlassPlayground: () -> Unit = {}
) {
    var brightness by remember { mutableFloatStateOf(128f) }
    var screenTimeout by remember { mutableIntStateOf(30) }
    var shizukuAvailable by remember { mutableStateOf(false) }
    var dhizukuInstalled by remember { mutableStateOf(false) }
    var dhizukuActive by remember { mutableStateOf(false) }
    var dhizukuMessage by remember { mutableStateOf("") }
    var dhizukuLoading by remember { mutableStateOf(false) }
    val dhizukuScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        brightness = ADBTools.getBrightness().toFloat()
        screenTimeout = ADBTools.getScreenTimeout()
        shizukuAvailable = ADBTools.isShizukuAvailable()
        dhizukuInstalled = ADBTools.isDhizukuInstalled()
        dhizukuActive = ADBTools.isDhizukuActive()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16f.dp)
    ) {
        Spacer(Modifier.height(24f.dp))
        BasicText(AppStrings.get("settings"), style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold))
        Spacer(Modifier.height(16f.dp))

        // 壁纸设置
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp)) {
                BasicText(AppStrings.get("wallpaper"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(12f.dp))
                LiquidButton(
                    onClick = onPickWallpaper,
                    backdrop = backdrop,
                    modifier = Modifier.height(44f.dp).fillMaxWidth(),
                    tint = Color(0xFF0088FF)
                ) {
                    BasicText(AppStrings.get("set_wallpaper"), Modifier.padding(horizontal = 8f.dp), style = TextStyle(Color.White, 14f.sp))
                }
                Spacer(Modifier.height(8f.dp))
                LiquidButton(
                    onClick = onClearWallpaper,
                    backdrop = backdrop,
                    modifier = Modifier.height(44f.dp).fillMaxWidth(),
                    tint = Color(0xFFFF3B30)
                ) {
                    BasicText(AppStrings.get("clear_wallpaper"), Modifier.padding(horizontal = 8f.dp), style = TextStyle(Color.White, 14f.sp))
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 动态壁纸设置
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp), verticalArrangement = Arrangement.spacedBy(12f.dp)) {
                BasicText(AppStrings.get("dynamic_wallpaper"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))

                // 动态壁纸开关
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(AppStrings.get("enable_dynamic_wallpaper"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                    BasicText(
                        if (AppCache.dynamicWallpaperEnabled.value) AppStrings.get("enabled") else AppStrings.get("disabled"),
                        style = TextStyle(if (AppCache.dynamicWallpaperEnabled.value) Color(0xFF34C759) else Color(0xFF8E8E93), 12f.sp)
                    )
                }
                LiquidSlider(
                    value = { if (AppCache.dynamicWallpaperEnabled.value) 1f else 0f },
                    onValueChange = {
                        val newVal = it > 0.5f
                        if (AppCache.dynamicWallpaperEnabled.value != newVal) {
                            AppCache.dynamicWallpaperEnabled.value = newVal
                            try { com.example.adbtoolbox.common.GlassEffectPersistence.saveAll() } catch (_: Exception) {}
                        }
                    },
                    valueRange = 0f..1f,
                    visibilityThreshold = 0.01f,
                    backdrop = backdrop
                )

                // 视频动态壁纸选择
                if (AppCache.dynamicWallpaperEnabled.value) {
                    BasicText(AppStrings.get("video_file"), style = TextStyle(contentColor, 14f.sp))
                    LiquidButton(
                        onClick = { AppCache.pickDynamicVideoTrigger.value++ },
                        backdrop = backdrop,
                        modifier = Modifier.height(44f.dp).fillMaxWidth(),
                        tint = Color(0xFF0088FF)
                    ) {
                        BasicText(
                            if (AppCache.dynamicWallpaperVideoPath.value != null) AppStrings.get("video_selected") else AppStrings.get("select_video"),
                            Modifier.padding(horizontal = 8f.dp),
                            style = TextStyle(Color.White, 14f.sp)
                        )
                    }
                    if (AppCache.dynamicWallpaperVideoPath.value != null) {
                        BasicText(
                            "${AppStrings.get("current_video")} ${AppCache.dynamicWallpaperVideoPath.value?.substringAfterLast('/')}",
                            style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
                        )
                        Spacer(Modifier.height(4f.dp))
                        LiquidButton(
                            onClick = {
                                AppCache.dynamicWallpaperVideoPath.value = null
                                AppCache.dynamicWallpaperEnabled.value = false
                                try { com.example.adbtoolbox.common.GlassEffectPersistence.saveAll() } catch (_: Exception) {}
                            },
                            backdrop = backdrop,
                            modifier = Modifier.height(40f.dp).fillMaxWidth(),
                            tint = Color(0xFFFF3B30)
                        ) {
                            BasicText(AppStrings.get("delete_video"), style = TextStyle(Color.White, 14f.sp))
                        }
                    }
                    BasicText(AppStrings.get("video_hint"), style = TextStyle(contentColor.copy(alpha = 0.5f), 11f.sp))
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 液态玻璃调节
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp)) {
                BasicText(AppStrings.get("glass_effect"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(12f.dp))
                LiquidButton(
                    onClick = onGlassPlayground,
                    backdrop = backdrop,
                    modifier = Modifier.height(44f.dp).fillMaxWidth(),
                    tint = Color(0xFF0088FF)
                ) {
                    BasicText(AppStrings.get("glass_playground"), Modifier.padding(horizontal = 8f.dp), style = TextStyle(Color.White, 14f.sp))
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 主题设置
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp)) {
                BasicText(AppStrings.get("display_settings"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(16f.dp))

                // 深色/浅色模式切换
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicText(if (AppSettings.isDarkMode) AppStrings.get("dark_mode") else AppStrings.get("light_mode"), style = TextStyle(contentColor, 14f.sp))
                    Spacer(Modifier.weight(1f))
                    LiquidToggle(
                        selected = { AppSettings.isDarkMode },
                        onSelect = { AppSettings.isDarkMode = it },
                        backdrop = backdrop,
                        modifier = Modifier.size(51f.dp, 31f.dp)
                    )
                }

                Spacer(Modifier.height(16f.dp))

                // 语言切换
                BasicText(AppStrings.get("language"), style = TextStyle(contentColor, 14f.sp))
                Spacer(Modifier.height(8f.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    val langs = listOf("zh" to AppStrings.get("chinese"), "en" to AppStrings.get("english"), "hi" to AppStrings.get("hindi"))
                    langs.forEach { (code, name) ->
                        val selected = AppSettings.language == code
                        LiquidButton(
                            onClick = { AppSettings.language = code },
                            backdrop = backdrop,
                            modifier = Modifier.height(36f.dp),
                            tint = if (selected) Color(0xFF0088FF) else Color.Unspecified
                        ) {
                            BasicText(name, Modifier.padding(horizontal = 12f.dp), style = TextStyle(if (selected) Color.White else contentColor, 12f.sp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // Shizuku 状态
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp)) {
                BasicText(AppStrings.get("shizuku_service"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(8f.dp))
                BasicText(
                    if (shizukuAvailable) AppStrings.get("shizuku_connected") else AppStrings.get("shizuku_disconnected"),
                    style = TextStyle(if (shizukuAvailable) Color(0xFF34C759) else Color(0xFFFF9500), 14f.sp)
                )
                Spacer(Modifier.height(12f.dp))
                LiquidButton(
                    onClick = { ADBTools.requestShizukuPermission() },
                    backdrop = backdrop,
                    modifier = Modifier.height(44f.dp),
                    tint = Color(0xFF0088FF)
                ) {
                    BasicText(AppStrings.get("request_shizuku"), Modifier.padding(horizontal = 8f.dp), style = TextStyle(Color.White, 14f.sp))
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // Dhizuku 设备所有者
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp)) {
                BasicText("Dhizuku (Device Owner)", style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(8f.dp))
                BasicText(
                    if (!dhizukuInstalled) AppStrings.get("dhizuku_not_installed") else if (dhizukuActive) AppStrings.get("dhizuku_active") else AppStrings.get("dhizuku_inactive"),
                    style = TextStyle(if (dhizukuActive) Color(0xFF34C759) else if (dhizukuInstalled) Color(0xFFFF9500) else Color(0xFFFF3B30), 14f.sp)
                )
                Spacer(Modifier.height(8f.dp))
                BasicText(AppStrings.get("dhizuku_hint"), style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                Spacer(Modifier.height(12f.dp))
                if (dhizukuMessage.isNotEmpty()) {
                    BasicText(dhizukuMessage, style = TextStyle(contentColor.copy(alpha = 0.8f), 12f.sp))
                    Spacer(Modifier.height(8f.dp))
                }
                // 两个按钮：请求权限 + 使用开关
                if (dhizukuInstalled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                        // 请求 Dhizuku 权限按钮（Dhizuku 作为设备所有者给应用授权）
                        LiquidButton(
                            onClick = {
                                dhizukuLoading = true
                                dhizukuMessage = if (dhizukuActive) AppStrings.get("dhizuku_requesting") else AppStrings.get("dhizuku_activate_hint")
                                dhizukuScope.launch {
                                    if (dhizukuActive) {
                                        val granted = withContext(Dispatchers.Default) { ADBTools.requestDhizukuPermission() }
                                        dhizukuMessage = if (granted) AppStrings.get("dhizuku_granted") else AppStrings.get("dhizuku_denied")
                                    }
                                    dhizukuActive = ADBTools.isDhizukuActive()
                                    dhizukuLoading = false
                                }
                            },
                            backdrop = backdrop,
                            modifier = Modifier.height(44f.dp).weight(1f),
                            tint = if (dhizukuActive) Color(0xFFAF52DE) else Color(0xFFFF9500)
                        ) {
                            BasicText(
                                if (dhizukuLoading) AppStrings.get("dhizuku_requesting") else if (dhizukuActive) AppStrings.get("dhizuku_request") else AppStrings.get("dhizuku_not_active"),
                                Modifier.padding(horizontal = 4f.dp),
                                style = TextStyle(Color.White, 13f.sp)
                            )
                        }
                        // 使用 Dhizuku 权限开关按钮
                        LiquidButton(
                            onClick = {
                                AppCache.useDhizuku.value = !AppCache.useDhizuku.value
                            },
                            backdrop = backdrop,
                            modifier = Modifier.height(44f.dp).weight(1f),
                            tint = if (AppCache.useDhizuku.value) Color(0xFF34C759) else Color(0xFF8E8E93)
                        ) {
                            BasicText(
                                if (AppCache.useDhizuku.value) AppStrings.get("use_dhizuku") + ": " + AppStrings.get("enabled") else AppStrings.get("use_dhizuku") + ": " + AppStrings.get("disabled"),
                                Modifier.padding(horizontal = 4f.dp),
                                style = TextStyle(Color.White, 13f.sp)
                            )
                        }
                    }
                } else {
                    BasicText(AppStrings.get("dhizuku_install_hint"), style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                }
                Spacer(Modifier.height(8f.dp))
                BasicText(AppStrings.get("dhizuku_notice"), style = TextStyle(contentColor.copy(alpha = 0.5f), 11f.sp))
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 显示设置
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp)) {
                BasicText("${AppStrings.get("screen_brightness")}: ${brightness.toInt()}", style = TextStyle(contentColor, 14f.sp))
                Spacer(Modifier.height(8f.dp))
                LiquidSlider(
                    value = { brightness / 255f },
                    onValueChange = { brightness = it * 255f },
                    valueRange = 0f..1f,
                    visibilityThreshold = 0.01f,
                    backdrop = backdrop,
                    modifier = Modifier.fillMaxWidth().height(44f.dp)
                )

                Spacer(Modifier.height(16f.dp))
                BasicText("${AppStrings.get("screen_timeout")}: ${screenTimeout}s", style = TextStyle(contentColor, 14f.sp))
                Spacer(Modifier.height(8f.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    listOf(15, 30, 60, 120, 300).forEach { sec ->
                        val selected = screenTimeout == sec
                        LiquidButton(
                            onClick = {
                                screenTimeout = sec
                                ADBTools.setScreenTimeout(sec)
                            },
                            backdrop = backdrop,
                            modifier = Modifier.height(36f.dp),
                            tint = if (selected) Color(0xFF0088FF) else Color.Unspecified
                        ) {
                            BasicText("${sec}s", Modifier.padding(horizontal = 8f.dp), style = TextStyle(if (selected) Color.White else contentColor, 12f.sp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 系统操作
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp)) {
                BasicText(AppStrings.get("system_actions"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(12f.dp))
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                    LiquidButton(
                        onClick = { ADBTools.rebootDevice() },
                        backdrop = backdrop,
                        modifier = Modifier.height(44f.dp),
                        tint = Color(0xFFFF9500)
                    ) {
                        BasicText(AppStrings.get("reboot"), Modifier.padding(horizontal = 4f.dp), style = TextStyle(Color.White, 14f.sp))
                    }
                    LiquidButton(
                        onClick = { ADBTools.rebootRecovery() },
                        backdrop = backdrop,
                        modifier = Modifier.height(44f.dp),
                        tint = Color(0xFFFF3B30)
                    ) {
                        BasicText(AppStrings.get("recovery"), Modifier.padding(horizontal = 4f.dp), style = TextStyle(Color.White, 14f.sp))
                    }
                    LiquidButton(
                        onClick = { ADBTools.rebootBootloader() },
                        backdrop = backdrop,
                        modifier = Modifier.height(44f.dp),
                        tint = Color(0xFFAF52DE)
                    ) {
                        BasicText(AppStrings.get("bootloader"), Modifier.padding(horizontal = 4f.dp), style = TextStyle(Color.White, 14f.sp))
                    }
                }
            }
        }

        Spacer(Modifier.height(80f.dp))
    }
}
