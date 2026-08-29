package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBDestination
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.DeviceInfoData
import com.example.adbtoolbox.common.GlassEffectConfig
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun HomeScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onNavigate: (ADBDestination) -> Unit
) {
    var deviceInfo by remember { mutableStateOf<DeviceInfoData?>(null) }
    var shizukuAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 优先从预加载缓存读取，秒开
        if (AppCache.deviceInfoLoaded.value) {
            deviceInfo = AppCache.deviceInfo.value
        } else {
            // 耗时操作移到后台线程，避免主线程阻塞导致ANR
            try {
                deviceInfo = withContext(Dispatchers.Default) { ADBTools.getDeviceInfo() }
                AppCache.deviceInfo.value = deviceInfo
                AppCache.deviceInfoLoaded.value = true
            } catch (e: Exception) {
                deviceInfo = null
            }
        }
        try {
            shizukuAvailable = withContext(Dispatchers.Default) { ADBTools.isShizukuAvailable() }
        } catch (e: Exception) {
            shizukuAvailable = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16f.dp),
        verticalArrangement = Arrangement.spacedBy(16f.dp)
    ) {
        Spacer(Modifier.height(24f.dp))

        BasicText(
            AppStrings.get("adb_toolbox"),
            style = TextStyle(contentColor, 28f.sp, androidx.compose.ui.text.font.FontWeight.Bold)
        )

        BasicText(
            if (shizukuAvailable) AppStrings.get("shizuku_connected") else AppStrings.get("shizuku_not_connected"),
            style = TextStyle(
                if (shizukuAvailable) Color(0xFF34C759) else Color(0xFFFF9500),
                14f.sp
            )
        )

        // 设备信息卡片
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(20f.dp)) {
                BasicText(AppStrings.get("device_info"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Spacer(Modifier.height(12f.dp))
                deviceInfo?.let { info ->
                    InfoRow(AppStrings.get("model"), info.model, contentColor)
                    InfoRow(AppStrings.get("brand"), info.brand, contentColor)
                    InfoRow(AppStrings.get("android_version"), info.androidVersion, contentColor)
                    InfoRow(AppStrings.get("sdk"), info.sdkVersion.toString(), contentColor)
                    InfoRow(AppStrings.get("memory"), "${info.availableMemory} / ${info.totalMemory}", contentColor)
                    InfoRow(AppStrings.get("storage"), "${info.availableStorage} / ${info.totalStorage}", contentColor)
                    InfoRow(AppStrings.get("battery"), "${info.batteryLevel}%", contentColor)
                    InfoRow(AppStrings.get("root"), if (info.isRooted) AppStrings.get("root_obtained") else AppStrings.get("root_not_obtained"), contentColor)
                }
                Spacer(Modifier.height(8f.dp))
                LiquidButton(
                    onClick = { onNavigate(ADBDestination.DeviceInfo) },
                    backdrop = backdrop,
                    modifier = Modifier.height(44f.dp),
                    tint = Color(0xFF0088FF)
                ) {
                    BasicText(AppStrings.get("view_details"), Modifier.padding(horizontal = 8f.dp), style = TextStyle(Color.White, 14f.sp))
                }
            }
        }

        // 快捷功能
        BasicText(AppStrings.get("quick_actions"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12f.dp)) {
            QuickActionButton(backdrop, AppStrings.get("device_info"), Color(0xFF0088FF), contentColor, Modifier.weight(1f)) { onNavigate(ADBDestination.DeviceInfo) }
            QuickActionButton(backdrop, AppStrings.get("adb_panel"), Color(0xFFFF9500), contentColor, Modifier.weight(1f)) { onNavigate(ADBDestination.ADBPanel) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12f.dp)) {
            QuickActionButton(backdrop, AppStrings.get("permissions"), Color(0xFFFF3B30), contentColor, Modifier.weight(1f)) { onNavigate(ADBDestination.Permissions) }
            QuickActionButton(backdrop, AppStrings.get("root_manager"), Color(0xFFAF52DE), contentColor, Modifier.weight(1f)) { onNavigate(ADBDestination.RootManager) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12f.dp)) {
            QuickActionButton(backdrop, AppStrings.get("shell_executor"), Color(0xFF34C759), contentColor, Modifier.weight(1f)) { onNavigate(ADBDestination.ShellExecutor) }
            QuickActionButton(backdrop, AppStrings.get("app_manager"), Color(0xFF5AC8FA), contentColor, Modifier.weight(1f)) { onNavigate(ADBDestination.Apps) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12f.dp)) {
            QuickActionButton(backdrop, AppStrings.get("root_tool"), Color(0xFFFF2D55), contentColor, Modifier.weight(1f)) { onNavigate(ADBDestination.RootTool) }
            QuickActionButton(backdrop, AppStrings.get("adb_module"), Color(0xFFBF5AF2), contentColor, Modifier.weight(1f)) { onNavigate(ADBDestination.ADBModule) }
        }

        Spacer(Modifier.height(16f.dp))
    }
}

@Composable
fun GlassCard(
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    pageType: String = "default", // "default","home","terminal","settings","apps"
    content: @Composable () -> Unit
) {
    // 从全局配置读取参数，根据页面类型选择对应参数
    val config = GlassEffectConfig
    val intensity = config.globalIntensity.value

    // 根据页面类型选择参数
    val blurRadius: Float
    val opacity: Float
    val corner: Float
    val refHeight: Float
    val refAmount: Float
    val enableVibrancy: Boolean
    when (pageType) {
        "home" -> {
            blurRadius = config.homeBlurRadius.value
            opacity = config.homeOpacity.value
            corner = config.homeCornerRadius.value
            refHeight = config.homeRefractionHeight.value
            refAmount = config.homeRefractionAmount.value
            enableVibrancy = config.homeEnableVibrancy.value
        }
        "terminal" -> {
            blurRadius = config.terminalBlurRadius.value
            opacity = config.terminalOpacity.value
            corner = config.terminalCornerRadius.value
            refHeight = config.terminalRefractionHeight.value
            refAmount = config.terminalRefractionAmount.value
            enableVibrancy = config.terminalEnableVibrancy.value
        }
        "settings" -> {
            blurRadius = config.settingsBlurRadius.value
            opacity = config.settingsOpacity.value
            corner = config.settingsCornerRadius.value
            refHeight = config.settingsRefractionHeight.value
            refAmount = config.settingsRefractionAmount.value
            enableVibrancy = config.settingsEnableVibrancy.value
        }
        "apps" -> {
            blurRadius = config.appsBlurRadius.value
            opacity = config.appsOpacity.value
            corner = config.appsCornerRadius.value
            refHeight = config.appsRefractionHeight.value
            refAmount = config.appsRefractionAmount.value
            enableVibrancy = config.appsEnableVibrancy.value
        }
        else -> {
            blurRadius = config.cardBlurRadius.value
            opacity = config.cardOpacity.value
            corner = config.cardCornerRadius.value
            refHeight = config.refractionHeight.value
            refAmount = config.refractionAmount.value
            enableVibrancy = config.enableVibrancy.value
        }
    }

    val cornerDp = 24f.dp * corner * intensity
    val glassColor = config.glassColor.value

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedRectangle(cornerDp))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(cornerDp) },
                effects = {
                    val minDim = size.minDimension
                    if (enableVibrancy) vibrancy()
                    blur(blurRadius.dp.toPx() * intensity)
                    lens(
                        refractionHeight = refHeight * minDim * 1.0f * intensity,
                        refractionAmount = refAmount * minDim * 1.5f * intensity,
                        depthEffect = true,
                        chromaticAberration = config.chromaticAberration.value > 0f
                    )
                },
                onDrawSurface = {
                    if (glassColor != Color.Transparent) {
                        drawRect(glassColor.copy(alpha = glassColor.alpha * opacity * intensity))
                    }
                }
            )
    ) {
        content()
    }
}

@Composable
fun InfoRow(label: String, value: String, contentColor: Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4f.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        BasicText(label, style = TextStyle(contentColor.copy(alpha = 0.6f), 14f.sp))
        BasicText(value, style = TextStyle(contentColor, 14f.sp))
    }
}

@Composable
fun QuickActionButton(
    backdrop: Backdrop,
    label: String,
    tint: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(80f.dp)
            .clip(RoundedRectangle(20f.dp))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(20f.dp) },
                effects = {
                    vibrancy()
                    blur(20f.dp.toPx())
                    lens(12f.dp.toPx(), 24f.dp.toPx())
                },
                onDrawSurface = {
                    drawRect(tint.copy(alpha = 0.15f))
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        BasicText(label, style = TextStyle(contentColor, 15f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
    }
}
