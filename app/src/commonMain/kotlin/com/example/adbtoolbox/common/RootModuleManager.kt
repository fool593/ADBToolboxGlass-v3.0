package com.example.adbtoolbox.common

// Root模块管理器 expect 声明，actual 实现在 androidMain
expect object RootModuleManager {
    fun installModule(zipFilePath: String): RootModuleInstallResult
    fun getInstalledModules(): List<RootModuleData>
    fun enableModule(moduleId: String): Boolean
    fun disableModule(moduleId: String): Boolean
    fun uninstallModule(moduleId: String): Boolean
    fun runAction(moduleId: String): String

    /**
     * 是否真的拿到了可用的 Root（`id` 返回 uid=0）。
     *
     * 与 [ADBTools.isRooted] 的区别：后者靠 su 文件路径/which 猜测，可能在"装了 Magisk 但本应用
     * 未被授权"时返回 true，导致界面把按钮点亮但每个操作都失败。这里直接验证真实提权结果。
     */
    fun canUseRoot(): Boolean
}
