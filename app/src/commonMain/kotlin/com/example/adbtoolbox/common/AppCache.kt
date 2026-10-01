package com.example.adbtoolbox.common

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap

// 全局数据缓存，预加载后各页面直接读取，避免重复加载导致卡顿
object AppCache {
    // 应用列表缓存
    val installedApps = mutableStateOf<List<AppInfoData>>(emptyList())
    val appsLoaded = mutableStateOf(false)

    // 应用图标解码缓存（packageName -> ImageBitmap）。
    // 列表来回滚动时不必反复走 PackageManager 取图标 + 解码。
    private val appIconCache = mutableMapOf<String, ImageBitmap>()

    fun getAppIcon(packageName: String): ImageBitmap? = appIconCache[packageName]

    fun putAppIcon(packageName: String, icon: ImageBitmap) {
        appIconCache[packageName] = icon
    }

    fun clearAppIcons() = appIconCache.clear()

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

    // 自定义开屏动画视频
    val splashVideoPath = mutableStateOf<String?>(null) // 用户自定义开屏视频文件路径
    val pickSplashVideoTrigger = mutableStateOf(0) // 触发开屏视频选择器
    val clearSplashVideoTrigger = mutableStateOf(0) // 触发删除开屏视频

    // Dhizuku 使用开关（默认开启，激活后自动使用 Dhizuku 权限执行命令）
    val useDhizuku = mutableStateOf(true)

    // ---------------- Shizuku 连接状态（全局唯一数据源） ----------------
    // 以前每个页面各自在 LaunchedEffect(Unit) 里查一次 isShizukuAvailable()，只查一次：
    // 如果进页面时 Shizuku 还没起、或者用户在别处才授权成功，界面就永远显示"未连接"，
    // 只能杀进程重进才恢复。现在统一放这里，谁都可以请求刷新，所有页面同步。
    val shizukuAvailable = mutableStateOf(false)
    /** Shizuku 三态：granted / no_permission / not_running / unknown（还没查过） */
    val shizukuState = mutableStateOf("unknown")
    /** 是否已经真正查询过一次（用于区分"未连接"和"还没查"） */
    val shizukuChecked = mutableStateOf(false)
    /** 刷新请求计数：任何地方 +1 即可请求重新检测 Shizuku（由 MainContent 统一执行） */
    val shizukuRefreshTick = mutableStateOf(0)

    fun requestShizukuRefresh() {
        shizukuRefreshTick.value++
    }

    // 临时 Root 提权包文件选择
    val selectedTempRootPath = mutableStateOf<String?>(null)
    val pickTempRootFileTrigger = mutableStateOf(0)

    // Root 模块（Magisk / KernelSU）安装包选择：触发器由 RootModuleScreen 的"安装"按钮置位，
    // MainActivity 拉起文件选择器后把路径写进 selectedRootModulePath
    val selectedRootModulePath = mutableStateOf<String?>(null)
    val pickRootModuleFileTrigger = mutableStateOf(0)

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
