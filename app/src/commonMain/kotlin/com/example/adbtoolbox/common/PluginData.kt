package com.example.adbtoolbox.common

// 插件数据模型，对应 AxManager 插件的 module.prop
data class PluginData(
    val id: String,
    val name: String,
    val version: String,
    val versionCode: Int,
    val author: String,
    val description: String,
    val axeronPlugin: Int = 0,
    val isEnabled: Boolean = true,
    val isInstalled: Boolean = true,
    val pluginDir: String = "",
    val hasWebUI: Boolean = false,
    val hasAction: Boolean = false,
    val updateTime: Long = 0L,
    val size: String = ""
)

// 插件安装结果
data class PluginInstallResult(
    val success: Boolean,
    val message: String,
    val plugin: PluginData? = null
)
