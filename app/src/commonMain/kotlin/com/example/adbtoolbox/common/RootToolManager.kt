package com.example.adbtoolbox.common

// KSU 官网地址
const val KSU_OFFICIAL_URL = "https://kernelsu.org/"
const val KSU_GITHUB_URL = "https://github.com/tiann/KernelSU"
const val MAGISK_GITHUB_URL = "https://github.com/topjohnwu/Magisk"

// 一键 Root 工具管理器 expect 声明
expect object RootToolManager {
    // 检测设备信息
    fun detectDevice(): DeviceRootInfo

    // 提取 boot.img
    fun extractBootImage(): RootResult

    // 使用 Magisk 修补 boot.img
    fun patchWithMagisk(bootPath: String): RootResult

    // 使用 KernelSU 修补 boot.img
    fun patchWithKernelSU(bootPath: String): RootResult

    // 刷入修补后的 boot.img
    fun flashBootImage(bootPath: String): RootResult

    // 临时 root（基于漏洞，支持特定机型）
    fun tempRoot(): RootResult

    // 检查是否已 root
    fun isRooted(): Boolean

    // 检查 Bootloader 是否解锁
    fun isBootloaderUnlocked(): Boolean

    // 获取支持临时 root 的机型列表
    fun getTempRootSupportedDevices(): List<String>

    // 检查 KernelSU 是否已安装
    fun isKernelSUInstalled(): Boolean

    // 检查 Magisk 是否已安装
    fun isMagiskInstalled(): Boolean

    // 获取 KernelSU 版本
    fun getKernelSUVersion(): String

    // 获取 Magisk 版本
    fun getMagiskVersion(): String
}
