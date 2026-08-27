package com.example.adbtoolbox.common

actual object ADBTools {
    actual fun isShizukuAvailable(): Boolean = false
    actual fun requestShizukuPermission() {}
    actual fun execCommand(command: String, timeout: Int): CommandResult = CommandResult("", "Not supported on this platform", -1)
    actual fun getDeviceInfo(): DeviceInfoData = DeviceInfoData("Unknown","Unknown","Unknown",0,"Unknown","Unknown","Unknown","0 GB","0 GB","0 GB","0 GB",-1,false,false)
    actual fun getInstalledApps(): List<AppInfoData> = emptyList()
    actual fun freezeApp(packageName: String): Boolean = false
    actual fun unfreezeApp(packageName: String): Boolean = false
    actual fun uninstallApp(packageName: String): Boolean = false
    actual fun clearCache(packageName: String): Boolean = false
    actual fun forceStop(packageName: String): Boolean = false
    actual fun clearAllCache(): Boolean = false
    actual fun isRooted(): Boolean = false
    actual fun requestRootPermission(): Boolean = false
    actual fun getSuVersion(): String = "Unknown"
    actual fun getBusyBoxVersion(): String = "Not installed"
    actual fun getLogcat(lines: Int): String = ""
    actual fun clearLogcat() {}
    actual fun getAppPermissions(packageName: String): List<PermissionInfoData> = emptyList()
    actual fun grantPermission(packageName: String, permission: String): Boolean = false
    actual fun revokePermission(packageName: String, permission: String): Boolean = false
    actual fun rebootDevice(): Boolean = false
    actual fun rebootRecovery(): Boolean = false
    actual fun rebootBootloader(): Boolean = false
    actual fun setBrightness(value: Int): Boolean = false
    actual fun getBrightness(): Int = 128
    actual fun setScreenTimeout(seconds: Int): Boolean = false
    actual fun getScreenTimeout(): Int = 30
    actual fun getCpuInfo(): String = "Unknown"
    actual fun getCpuCores(): Int = 0
    actual fun getScreenResolution(): String = "Unknown"
    actual fun getKernelVersion(): String = "Unknown"
    actual fun getBatteryLevel(): Int = -1
    actual fun getTotalMemory(): String = "0 GB"
    actual fun getAvailableMemory(): String = "0 GB"
    actual fun getTotalStorage(): String = "0 GB"
    actual fun getAvailableStorage(): String = "0 GB"
}
