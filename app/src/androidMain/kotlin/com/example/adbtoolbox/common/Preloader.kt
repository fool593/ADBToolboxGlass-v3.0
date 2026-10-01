package com.example.adbtoolbox.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// 预加载管理器：应用启动时后台加载常用数据，页面切换秒开
object Preloader {
    private var preloadJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    fun start() {
        if (preloadJob?.isActive == true) return
        AppCache.isPreloading.value = true
        AppCache.preloadProgress.value = 0f

        preloadJob = scope.launch {
            try {
                // 阶段1: 加载应用列表（最耗时，优先加载）
                withContext(Dispatchers.Default) {
                    val apps = ADBTools.getInstalledApps()
                    AppCache.installedApps.value = apps
                    // 只有真的读到应用才标记"已加载"：失败时若置位，页面会永远显示空列表且不再重试
                    if (apps.isNotEmpty()) AppCache.appsLoaded.value = true
                }
                AppCache.preloadProgress.value = 0.5f

                // 阶段2: 加载设备信息
                withContext(Dispatchers.Default) {
                    AppCache.deviceInfo.value = ADBTools.getDeviceInfo()
                    AppCache.cpuInfo.value = ADBTools.getCpuInfo()
                    AppCache.cpuCores.value = ADBTools.getCpuCores()
                    AppCache.screenResolution.value = ADBTools.getScreenResolution()
                    AppCache.kernelVersion.value = ADBTools.getKernelVersion()
                    AppCache.deviceInfoLoaded.value = true
                }
                AppCache.preloadProgress.value = 1f
            } catch (e: Exception) {
                // 预加载失败不影响使用，页面会自行加载
            } finally {
                AppCache.isPreloading.value = false
            }
        }
    }

    fun refreshApps() {
        scope.launch {
            val apps = ADBTools.getInstalledApps()
            AppCache.installedApps.value = apps
            // 同上：空结果不代表加载成功，不能置位 appsLoaded，否则永远不再重试
            if (apps.isNotEmpty()) AppCache.appsLoaded.value = true
        }
    }

    fun refreshDeviceInfo() {
        scope.launch {
            AppCache.deviceInfo.value = ADBTools.getDeviceInfo()
            AppCache.cpuInfo.value = ADBTools.getCpuInfo()
            AppCache.cpuCores.value = ADBTools.getCpuCores()
            AppCache.screenResolution.value = ADBTools.getScreenResolution()
            AppCache.kernelVersion.value = ADBTools.getKernelVersion()
            AppCache.deviceInfoLoaded.value = true
        }
    }

    fun cancel() {
        preloadJob?.cancel()
        AppCache.isPreloading.value = false
    }
}
