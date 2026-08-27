package com.example.adbtoolbox.common

// Root 方案类型
enum class RootMethod {
    MAGISK,       // Magisk 修补 boot.img
    KERNELSU,     // KernelSU 内核级 root
    TEMP_ROOT,    // 临时 root（基于漏洞）
    UNKNOWN
}

// 临时 Root 类型
enum class TempRootType {
    NONE,
    GHOSTLOCK,   // 一加 GhostLock (CVE-2026-43499)
    TEMPROOT,    // 小米 HyperOS TempRoot
    UNKNOWN
}

// 设备信息
data class DeviceRootInfo(
    val brand: String = "",
    val model: String = "",
    val device: String = "",
    val androidVersion: String = "",
    val kernelVersion: String = "",
    val isBootloaderUnlocked: Boolean = false,
    val isGKI: Boolean = false, // 是否 GKI 设备（内核 >= 5.10）
    val supportedMethods: List<RootMethod> = emptyList(),
    val tempRootSupported: Boolean = false,
    val tempRootType: TempRootType = TempRootType.NONE
)

// Root 操作结果
data class RootResult(
    val success: Boolean,
    val message: String,
    val step: String = ""
)

// Root 步骤
enum class RootStep {
    IDLE,
    CHECKING_DEVICE,
    EXTRACTING_BOOT,
    PATCHING_BOOT,
    FLASHING_BOOT,
    REBOOTING,
    DONE,
    ERROR
}
