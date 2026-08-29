package com.example.adbtoolbox.common

import androidx.compose.runtime.mutableStateOf

// 全局数据缓存，预加载后各页面直接读取，避免重复加载导致卡顿
object AppCache {
    // 应用列表缓存
    val installedApps = mutableStateOf<List<AppInfoData>>(emptyList())
    val appsLoaded = mutableStateOf(false)

    // 设备信息缓存
    val deviceInfo = mutableStateOf<DeviceInfoData?>(null)
    val cpuInfo = mutableStateOf("")
    val cpuCores = mutableStateOf(0)
    val screenResolution = mutableStateOf("")
    val kernelVersion = mutableStateOf("")
    val deviceInfoLoaded = mutableStateOf(false)

    // 权限缓存: packageName -> permissions
    private val permissionCache = mutableMapOf<String, List<PermissionInfoData>>()

    // 预加载状态
    val isPreloading = mutableStateOf(false)
    val preloadProgress = mutableStateOf(0f) // 0~1

    // ADB模块文件路径（由MainActivity的文件选择器设置）
    val selectedModulePath = mutableStateOf<String?>(null)
    val pickModuleFileTrigger = mutableStateOf(0)

    // 插件安装相关
    val pickPluginFileTrigger = mutableStateOf(0)
    val pluginInstallResult = mutableStateOf<String?>(null)
    val pluginInstalling = mutableStateOf(false)

    // 终端初始命令（由其他页面跳转时设置，终端页面启动时自动执行）
    val terminalInitialCommand = mutableStateOf<String?>(null)

    // 动态壁纸设置
    val dynamicWallpaperEnabled = mutableStateOf(false)
    val dynamicWallpaperVideoPath = mutableStateOf<String?>(null) // 视频动态壁纸文件路径
    val pickDynamicVideoTrigger = mutableStateOf(0) // 触发视频选择器

    // Dhizuku 使用开关（默认开启，激活后自动使用 Dhizuku 权限执行命令）
    val useDhizuku = mutableStateOf(true)

    // 临时 Root 提权包文件选择
    val selectedTempRootPath = mutableStateOf<String?>(null)
    val pickTempRootFileTrigger = mutableStateOf(0)

    fun getPermissions(packageName: String): List<PermissionInfoData>? {
        return permissionCache[packageName]
    }

    fun setPermissions(packageName: String, perms: List<PermissionInfoData>) {
        permissionCache[packageName] = perms
    }

    fun clearPermissions(packageName: String) {
        permissionCache.remove(packageName)
    }

    fun invalidateApps() {
        appsLoaded.value = false
    }

    fun invalidateDeviceInfo() {
        deviceInfoLoaded.value = false
    }

    fun clearAll() {
        installedApps.value = emptyList()
        appsLoaded.value = false
        deviceInfo.value = null
        cpuInfo.value = ""
        cpuCores.value = 0
        screenResolution.value = ""
        kernelVersion.value = ""
        deviceInfoLoaded.value = false
        permissionCache.clear()
    }
}
