package com.example.adbtoolbox.common

// 插件管理器 expect 声明，actual 实现在 androidMain
expect object PluginManager {
    fun installPlugin(zipFilePath: String): PluginInstallResult
    fun getInstalledPlugins(): List<PluginData>
    fun enablePlugin(pluginId: String): Boolean
    fun disablePlugin(pluginId: String): Boolean
    fun uninstallPlugin(pluginId: String): Boolean
    fun runAction(pluginId: String): String
    fun getWebUIPath(pluginId: String): String?
}
