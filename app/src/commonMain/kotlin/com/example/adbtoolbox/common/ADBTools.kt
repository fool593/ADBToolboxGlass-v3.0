package com.example.adbtoolbox.common

expect object ADBTools {
    fun isShizukuAvailable(): Boolean

    /**
     * Shizuku 三态，用于界面如实显示当前状态（避免"服务已启动但没授权"被显示成"未连接"）：
     * - `granted`      ：服务在跑且已授权给本应用，可以真正执行命令
     * - `no_permission`：服务在跑，但本应用还没拿到授权（界面应提示去授权，而不是说未连接）
     * - `not_running`  ：服务没起来 / 未安装 / 调用异常
     */
    fun getShizukuState(): String
    fun requestShizukuPermission()
    fun getShizukuDiagnostics(): String
    fun execCommand(command: String, timeout: Int = 15): CommandResult
    fun getDeviceInfo(): DeviceInfoData
    fun getInstalledApps(): List<AppInfoData>
    fun freezeApp(packageName: String): Boolean
    fun unfreezeApp(packageName: String): Boolean
    fun uninstallApp(packageName: String): Boolean
    fun clearCache(packageName: String): Boolean

    /**
     * 清除应用**全部数据**（等价系统设置里的"清除数据"，不可逆）。
     * 与 [clearCache] 严格区分：只在界面明确二次确认后调用，绝不作为清缓存的兜底。
     */
    fun clearAppData(packageName: String): Boolean
    fun forceStop(packageName: String): Boolean
    fun clearAllCache(): Boolean
    fun isRooted(): Boolean
    fun requestRootPermission(): Boolean
    fun getSuVersion(): String
    fun getBusyBoxVersion(): String
    fun getLogcat(lines: Int = 100): String
    fun clearLogcat()
    fun getAppPermissions(packageName: String): List<PermissionInfoData>
    fun grantPermission(packageName: String, permission: String): Boolean
    fun revokePermission(packageName: String, permission: String): Boolean
    fun rebootDevice(): Boolean
    fun rebootRecovery(): Boolean
    fun rebootBootloader(): Boolean
    fun setBrightness(value: Int): Boolean
    fun getBrightness(): Int
    fun setScreenTimeout(seconds: Int): Boolean
    fun getScreenTimeout(): Int
    fun getCpuInfo(): String
    fun getCpuCores(): Int
    fun getScreenResolution(): String
    fun getKernelVersion(): String
    fun getBatteryLevel(): Int
    fun getTotalMemory(): String
    fun getAvailableMemory(): String
    fun getTotalStorage(): String
    fun getAvailableStorage(): String
    fun installModuleViaADB(localFilePath: String): String
    fun installModuleViaRoot(localFilePath: String): String
    fun hasMagisk(): Boolean
    // Dhizuku 设备所有者
    fun isDhizukuInstalled(): Boolean
    fun isDhizukuActive(): Boolean
    fun isDhizukuPermissionGranted(): Boolean
    fun requestDhizukuPermission(): Boolean
    fun activateDhizuku(): CommandResult
    fun removeDhizuku(): CommandResult

    // 临时 Root（提权）相关
    fun getCpuModel(): String // 返回处理器型号，如 "Dimensity 9200" / "Snapdragon 8 Gen 2"
    fun getCpuVendor(): String // 返回 "mediatek" / "qualcomm" / "other"
    fun flashTempRootModule(zipPath: String): CommandResult // 刷入临时 Root 提权包

    // ==================== 性能加速 / 手机体检 基础设施 ====================

    /** 读取 `getprop <key>`，失败返回空串。用于品牌/机型/屏幕特征识别。 */
    fun getProp(key: String): String

    /** 批量读取 getprop：一次 shell 调用解析全部，避免逐条 exec 造成卡顿。 */
    fun getProps(keys: List<String>): Map<String, String>

    /**
     * 当前屏幕真实刷新率（Hz）。优先取 Display.Mode.refreshRate（API 23+），
     * 回退 Display.refreshRate，再回退 `dumpsys display` 解析。取不到返回 0。
     */
    fun getCurrentRefreshRate(): Float

    /** 设备屏幕支持的最高刷新率（Hz），取不到返回 0。 */
    fun getMaxRefreshRate(): Float

    /** 设备屏幕支持的全部刷新率，升序去重。 */
    fun getSupportedRefreshRates(): List<Float>

    /** 读取系统设置原始值（namespace: system/secure/global）。 */
    fun getSystemSetting(namespace: String, key: String): String

    /** 写入系统设置，返回是否成功。 */
    fun putSystemSetting(namespace: String, key: String, value: String): Boolean

    /**
     * 把峰值/最低刷新率设置为目标值，解除厂商对 60Hz 的锁定。
     * [both] 为 true 时同时设置 peak_refresh_rate 与 min_refresh_rate（强制固定高刷）。
     */
    fun setRefreshRate(target: Float, both: Boolean = false): CommandResult

    /** 一键恢复刷新率为系统自适应（删除 peak/min/user_refresh_rate 设置）。 */
    fun resetRefreshRateToAuto(): CommandResult

    // ==================== 游戏帧率：应用权限直写通道 ====================

    /**
     * 本应用是否已被授予「修改系统设置」（`WRITE_SETTINGS`，Manifest 里已声明，需用户在系统设置里手动开）。
     *
     * 用途：非华为机型可以直接用这个权限写 `peak_refresh_rate` / `min_refresh_rate`，**不需要 ADB**；
     * 华为 EMUI / HarmonyOS 通常不认这条通道，必须走 Shizuku/Root —— 这正是"华为需要 ADB 权限、
     * 其余机型不需要"的技术原因，界面据此如实提示用户该走哪条路。
     */
    fun isWriteSettingsGranted(): Boolean

    /** 读取"修改系统设置"授权页的 Intent action（供 Activity 拉起系统设置页）。 */
    fun writeSettingsSettingsAction(): String

    /**
     * 不经过 shell、直接用应用自身权限写刷新率（system 命名空间的 peak/min/user_refresh_rate）。
     * 未授权或 ROM 不支持时返回 false，调用方应回退到 [setRefreshRate]（Shizuku/Root）。
     */
    fun setRefreshRateDirect(target: Float): Boolean

    // ---------------- 游戏帧率：按游戏单独设置所需的能力 ----------------

    /**
     * 列出本机**被系统标记为游戏**的应用（`ApplicationInfo.category == CATEGORY_GAME`）。
     * [includeAll] 为 true 时返回全部已安装应用，方便给没被正确分类的游戏手动指定。
     * 只做真实枚举，不做任何猜测。
     */
    fun listGameApps(includeAll: Boolean = false): List<GameAppInfo>

    /** 是否已授予「使用情况访问」权限（按游戏自动切换帧率需要它来判断前台应用）。 */
    fun hasUsageAccess(): Boolean

    /** 「使用情况访问」授权页的 Intent action。 */
    fun usageAccessSettingsAction(): String

    /** 当前前台应用包名；没有权限或读不到时返回空串。 */
    fun getForegroundPackage(): String

    /**
     * 系统级"按游戏限制帧率"（Android 13+ 的 GameManagerService game_overlay）。
     * [fps] <= 0 表示清除该游戏的覆盖配置。
     * 需要 shell / Root（`device_config` 是受保护命令），因此这条**只作为有提权时的增强项**，
     * 没有提权时界面不会假装能用。
     */
    fun setGameOverlayFps(packageName: String, fps: Int): CommandResult

    /** 读回某个游戏的 game_overlay 配置，用于如实校验；无配置返回空串。 */
    fun getGameOverlayFps(packageName: String): String

    // ---------------- 内核提权（运行用户自备的 exploit） ----------------

    /**
     * 把本机上的一个文件推送到设备 `/data/local/tmp/<remoteName>` 并 `chmod 755`。
     *
     * 用途：内核提权 exploit 通常是 GitHub 上编译好的 arm64 可执行文件，需要先推上设备再执行。
     * 走的是与临时提权包相同的"分块 base64 追加 + 字节数校验"方式，
     * **不依赖设备端是否有 unzip**；校验不通过会如实返回失败，不会假装推送成功。
     *
     * 返回值 [CommandResult.output] 为远端路径（成功时），失败时 error 里是真实原因。
     * 本应用**不内置、不下载任何 exploit**，只负责把用户自己选择的文件送上去。
     */
    fun pushLocalFileToTemp(localPath: String, remoteName: String): CommandResult

    /**
     * 准备并推送**一整套** exploit 工具包（zip 或单个文件）到设备，返回入口文件的远端路径。
     *
     * 公开的内核提权套件（例如 GhostLock / CVE-2026-43499 的各机型移植）多是一个压缩包，
     * 内含可执行文件 + shell 脚本 + 说明文件，需要整体推送、统一给可执行权限、再执行入口脚本。
     * 传 zip 时在本机解压（不依赖设备端 unzip），入口优先取 run.sh / root.sh / start.sh /
     * install.sh / exploit，其次带 ghost/ghostlock 字样的文件，最后取唯一文件。
     *
     * 返回值 [CommandResult.output] 首行为入口远端路径，其后是设备上的文件清单（便于核对）。
     */
    fun prepareAndPushKit(localPath: String, remoteDirName: String): CommandResult

    /**
     * 列出**随应用一起打包**的内核提权工具包目录名（`assets/exploits/<名字>/`）。
     *
     * 用途：让"把越狱做进去"成为一条真实可用的通道——只要把对应机型的工具包放进
     * `androidApp/src/main/assets/exploits/<机型>/`，重新构建后应用里就会列出来，
     * 可以一键解出、推送并执行，不需要用户自己在手机上找文件。
     *
     * 注意：源码仓库里**不预置任何 exploit**，这个目录默认只有说明文件；
     * 是否放入、放哪一个机型的包，由使用者自己决定并自行承担风险。
     */
    fun listAssetKits(): List<String>

    /**
     * 把 `assets/exploits/<assetDir>/` 下的整套文件解到应用私有目录，返回解出后的本地目录路径。
     * 之后可直接交给 [prepareAndPushKit] 推送执行。失败时 error 是真实原因。
     */
    fun extractAssetKit(assetDir: String): CommandResult

    /**
     * 强制结束全部后台进程（保留前台与本应用），等效厂商"一键清理"。
     * 返回被结束的包名列表。
     */
    fun killBackgroundProcesses(): List<String>

    /** 综合修复刷新率锁 60Hz。返回 (是否成功, 人类可读说明)。 */
    fun fixRefreshRateLock(): Pair<Boolean, String>

    /** 直接执行 shell（Shizuku/Root/普通三级回退），供性能加速与体检逐条执行使用。 */
    fun execPerfCommand(command: String, timeout: Int = 20): CommandResult

    /**
     * 读取应用图标的 Base64（WebP，已降采样）。
     *
     * 为什么单独提供而不塞进 [getInstalledApps]：一次加载 300+ 个图标会明显拖慢启动，
     * 列表页按需逐项调用并把结果缓存进 AppCache 才是正确做法。
     * 返回 null 表示该应用没有可用图标（调用方应回退到首字母占位）。
     */
    fun getAppIconBase64(packageName: String): String?
}

data class CommandResult(
    val output: String,
    val error: String,
    val exitCode: Int
)

data class DeviceInfoData(
    val model: String,
    val brand: String,
    val androidVersion: String,
    val sdkVersion: Int,
    val kernelVersion: String,
    val buildNumber: String,
    val cpuAbi: String,
    val totalMemory: String,
    val availableMemory: String,
    val totalStorage: String,
    val availableStorage: String,
    val batteryLevel: Int,
    val isRooted: Boolean,
    val isAdbEnabled: Boolean,
    val refreshRate: String = "Unknown"
)

/**
 * 用于"按游戏设置帧率"的应用条目。
 *
 * [isGame] 表示系统把它归类为游戏（`ApplicationInfo.category == CATEGORY_GAME`）；
 * 用户开启"显示全部应用"时列表里会出现 isGame=false 的条目，用于给分类不准的游戏手动指定。
 */
data class GameAppInfo(
    val packageName: String,
    val label: String,
    val isGame: Boolean,
    val isSystem: Boolean
)

data class AppInfoData(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystem: Boolean,
    val isFrozen: Boolean,
    val iconBase64: String? = null
)

data class PermissionInfoData(
    val permission: String,
    val name: String,
    val isGranted: Boolean
)
