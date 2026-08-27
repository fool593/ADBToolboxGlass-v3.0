package com.example.adbtoolbox.common

// Root模块数据模型，对应 Magisk/KernelSU 模块
data class RootModuleData(
    val id: String,
    val name: String,
    val version: String = "1.0",
    val versionCode: Int = 1,
    val author: String = "Unknown",
    val description: String = "",
    val isEnabled: Boolean = true,
    val isInstalled: Boolean = true,
    val moduleDir: String = "",
    val hasAction: Boolean = false,
    val updateTime: Long = 0L,
    val size: String = "0KB"
)

// Root模块安装结果
data class RootModuleInstallResult(
    val success: Boolean,
    val message: String
)
