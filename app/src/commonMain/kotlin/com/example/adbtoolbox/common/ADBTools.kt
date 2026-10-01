package com.example.adbtoolbox.common

expect object ADBTools {
    fun isShizukuAvailable(): Boolean
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
