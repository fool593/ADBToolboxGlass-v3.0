package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.DeviceInfoData
import com.kyant.backdrop.Backdrop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun DeviceInfoScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    var deviceInfo by remember { mutableStateOf<DeviceInfoData?>(null) }
    var cpuInfo by remember { mutableStateOf("") }
    var cpuCores by remember { mutableStateOf(0) }
    var resolution by remember { mutableStateOf("") }
    var kernel by remember { mutableStateOf("") }
    var shizukuAvailable by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // 优先从预加载缓存读取，秒开
        if (AppCache.deviceInfoLoaded.value) {
            deviceInfo = AppCache.deviceInfo.value
            cpuInfo = AppCache.cpuInfo.value
            cpuCores = AppCache.cpuCores.value
            resolution = AppCache.screenResolution.value
            kernel = AppCache.kernelVersion.value
        } else {
            deviceInfo = withContext(Dispatchers.Default) { ADBTools.getDeviceInfo() }
            cpuInfo = withContext(Dispatchers.Default) { ADBTools.getCpuInfo() }
            cpuCores = withContext(Dispatchers.Default) { ADBTools.getCpuCores() }
            resolution = withContext(Dispatchers.Default) { ADBTools.getScreenResolution() }
            kernel = withContext(Dispatchers.Default) { ADBTools.getKernelVersion() }
            AppCache.deviceInfo.value = deviceInfo
            AppCache.cpuInfo.value = cpuInfo
            AppCache.cpuCores.value = cpuCores
            AppCache.screenResolution.value = resolution
            AppCache.kernelVersion.value = kernel
            AppCache.deviceInfoLoaded.value = true
        }
        shizukuAvailable = withContext(Dispatchers.Default) { ADBTools.isShizukuAvailable() }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16f.dp)
    ) {
        Spacer(Modifier.height(24f.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
            Spacer(Modifier.height(12f.dp))
            BasicText(AppStrings.get("device_info"), style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold))
        }
        Spacer(Modifier.height(16f.dp))

        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("device_info"), contentColor)
                deviceInfo?.let { info ->
                    InfoRow(AppStrings.get("model"), info.model, contentColor)
                    InfoRow(AppStrings.get("brand"), info.brand, contentColor)
                    InfoRow(AppStrings.get("android_version"), info.androidVersion, contentColor)
                    InfoRow(AppStrings.get("sdk"), info.sdkVersion.toString(), contentColor)
                    InfoRow("${AppStrings.get("sdk")} ${AppStrings.get("version")}", info.buildNumber, contentColor)
                    InfoRow(AppStrings.get("kernel_version"), kernel, contentColor)
                    InfoRow("CPU ${AppStrings.get("version")}", info.cpuAbi, contentColor)
                    InfoRow("CPU ${AppStrings.get("model")}", cpuInfo, contentColor)
                    InfoRow("CPU ${AppStrings.get("cpu_cores")}", "$cpuCores", contentColor)
                    InfoRow(AppStrings.get("screen_resolution"), resolution, contentColor)
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle("${AppStrings.get("storage")} & ${AppStrings.get("memory")}", contentColor)
                deviceInfo?.let { info ->
                    InfoRow("${AppStrings.get("memory")} (${AppStrings.get("version")})", info.totalMemory, contentColor)
                    InfoRow("${AppStrings.get("memory")} (${AppStrings.get("output")})", info.availableMemory, contentColor)
                    InfoRow("${AppStrings.get("storage")} (${AppStrings.get("version")})", info.totalStorage, contentColor)
                    InfoRow("${AppStrings.get("storage")} (${AppStrings.get("output")})", info.availableStorage, contentColor)
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("root_status"), contentColor)
                deviceInfo?.let { info ->
                    InfoRow("${AppStrings.get("battery")} (${AppStrings.get("version")})", "${info.batteryLevel}%", contentColor)
                    InfoRow(AppStrings.get("root"), if (info.isRooted) AppStrings.get("root_obtained") else AppStrings.get("root_not_obtained"), contentColor)
                    InfoRow("ADB", if (info.isAdbEnabled) AppStrings.get("enable_adb") else AppStrings.get("disable_adb"), contentColor)
                    InfoRow("Shizuku", if (shizukuAvailable) AppStrings.get("shizuku_connected") else AppStrings.get("shizuku_not_connected"), contentColor)
                }
            }
        }

        Spacer(Modifier.height(80f.dp))
    }
}

@Composable
fun SectionTitle(title: String, contentColor: Color) {
    BasicText(title, style = TextStyle(contentColor, 16f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
    Spacer(Modifier.height(12f.dp))
}
