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
    fun activateDhizuku(): CommandResult
    fun removeDhizuku(): CommandResult
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
    val isAdbEnabled: Boolean
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
