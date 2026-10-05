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
    //
    // 上界：每个 ImageBitmap 背后是一整块原生位图（当前按 96px 降采样，单张约 36KB；
    // 但装 300+ 应用、来回滚几轮后会一直累积）。原来是无上界的 mutableMapOf，
    // 这些位图只在 clearAppIcons() 或进程结束时才释放 —— 属于"越用越大"的那类内存增长。
    // 现在用插入序 LinkedHashMap 做 LRU：访问即刷新到队尾，超过 MAX_ICON_CACHE 淘汰最久未用的。
    // 只在主线程访问（getAppIcon 在组合期、putAppIcon 在主线程协程里），因此不加锁。
    private val appIconCache = LinkedHashMap<String, ImageBitmap>()

    /** 图标缓存上界：64 张足够覆盖一屏可见项 + 来回滚动的回看，超出即淘汰最久未用的。 */
    private const val MAX_ICON_CACHE = 64

    fun getAppIcon(packageName: String): ImageBitmap? {
        val icon = appIconCache[packageName] ?: return null
        // 访问即刷新"最近使用"顺序
        appIconCache.remove(packageName)
        appIconCache[packageName] = icon
        return icon
    }

    fun putAppIcon(packageName: String, icon: ImageBitmap) {
        appIconCache.remove(packageName)
        appIconCache[packageName] = icon
        while (appIconCache.size > MAX_ICON_CACHE) {
            val oldest = appIconCache.keys.firstOrNull() ?: break
            appIconCache.remove(oldest)
        }
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
    // 加上界：每个应用的权限列表不大但也不小，用户一路点开几十个应用后会一直留着；
    // 满了整体清空（最坏只是重新读一次权限），避免只增不减。
    private val permissionCache = mutableMapOf<String, List<PermissionInfoData>>()
    private const val PERMISSION_CACHE_MAX = 32

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

    // 「修改系统设置」授权页触发（游戏帧率页在非华为机型上需要该权限才能直写刷新率）
    val openWriteSettingsTrigger = mutableStateOf(0)

    // 内核提权：用户自己从 GitHub 下载的 exploit / 工具包（本应用不内置、不下载任何 exploit）
    val selectedKernelExploitPath = mutableStateOf<String?>(null)
    val pickKernelExploitTrigger = mutableStateOf(0)

    // 「使用情况访问」授权页触发（按游戏自动切换帧率需要它来判断前台应用）
    val openUsageAccessTrigger = mutableStateOf(0)

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
        if (permissionCache.size >= PERMISSION_CACHE_MAX) permissionCache.clear()
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
