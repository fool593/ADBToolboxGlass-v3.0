package com.example.adbtoolbox.common

// Root模块管理器 expect 声明，actual 实现在 androidMain
expect object RootModuleManager {
    fun installModule(zipFilePath: String): RootModuleInstallResult
    fun getInstalledModules(): List<RootModuleData>
    fun enableModule(moduleId: String): Boolean
    fun disableModule(moduleId: String): Boolean
    fun uninstallModule(moduleId: String): Boolean
    fun runAction(moduleId: String): String
}
