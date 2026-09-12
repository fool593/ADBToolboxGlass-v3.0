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

    // 已检测到 KSU 时，直接通过 KSU 获取 root 权限
    fun rootWithKSU(): RootResult

    // 检查 Magisk 是否已安装
    fun isMagiskInstalled(): Boolean

    // 获取 KernelSU 版本
    fun getKernelSUVersion(): String

    // 获取 Magisk 版本
    fun getMagiskVersion(): String

    // 自动检测设备并推荐最合适的 root 方法
    fun detectRootMethod(): RootMethodInfo

    // 获取当前设备可用的所有 root 方法列表
    fun getAvailableRootMethods(): List<RootMethodInfo>

    // 执行指定的 root 方法
    fun executeRootMethod(methodId: String): RootResult

    // 获取所有内置的 root 方法（含不支持当前设备的，用于展示）
    fun getAllRootMethods(): List<RootMethodInfo>

    // 检测临时 root 脚本是否存在，返回脚本路径（不存在返回null）
    fun findTempRootScript(): String?

    // 生成临时 root 的终端执行命令
    fun buildTempRootTerminalCommand(scriptPath: String): String
}

// Root 方法信息
data class RootMethodInfo(
    val id: String,
    val name: String,
    val brand: String,           // 支持品牌：vivo/xiaomi/oneplus/samsung/mtk/generic
    val chipset: String,         // 支持芯片：mediatek/qualcomm/generic
    val principle: String,       // 原理说明
    val riskLevel: String,       // 风险等级：low/medium/high
    val requiresComputer: Boolean,  // 是否需要电脑
    val requiresKSU: Boolean,    // 是否需要先装 KSU
    val supportedDevices: String, // 支持机型描述
    val description: String      // 详细描述
)
