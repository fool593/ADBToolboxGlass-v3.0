package com.example.adbtoolbox.common

import java.io.File

// 一键 Root 工具管理器 actual 实现
actual object RootToolManager {

    // 支持 GhostLock（一加）的机型列表
    private val ghostLockDevices = listOf(
        "oneplus13",     // 一加 13
        "oneplus15",     // 一加 15
        "oneplusace3",   // 一加 Ace 3
        "oneplusace5",   // 一加 Ace 5
        "oneplusace6",   // 一加 Ace 6
        "oneplus12",     // 一加 12
        "oneplus11",     // 一加 11
        "oneplus10pro",  // 一加 10 Pro
        "oneplus9",      // 一加 9
        "oneplus9pro",   // 一加 9 Pro
        "oneplus8",      // 一加 8
        "oneplus8pro",   // 一加 8 Pro
        "oneplus8t",     // 一加 8T
        "oneplus7",      // 一加 7
        "oneplus7pro",   // 一加 7 Pro
        "oneplus7t",     // 一加 7T
        "oneplus7tpro",  // 一加 7T Pro
        "oneplus6",      // 一加 6
        "oneplus6t",     // 一加 6T
        "oneplus5",      // 一加 5
        "oneplus5t",     // 一加 5T
        "oneplus3",      // 一加 3
        "oneplus3t",     // 一加 3T
        "oneplus2",      // 一加 2
        "oneplus1",      // 一加 1
        "oneplusnord",   // 一加 Nord
        "oneplusnord2",  // 一加 Nord 2
        "oneplusnordce", // 一加 Nord CE
        "op592dl1",      // 一加 13 国内版
        "op5943l1",      // 一加 15
        "op58f1l1",      // 一加 Ace 5
        "op5958l1",      // 一加 Ace 6
        "op56f9l1",      // 一加 12
        "op550bl1",      // 一加 11
        "op535dl1",      // 一加 10 Pro
        "op5251l1",      // 一加 9
        "op5253l1",      // 一加 9 Pro
        "op515bl1",      // 一加 8
        "op5157l1",      // 一加 8 Pro
        "op515al1",      // 一加 8T
        "op5071l1",      // 一加 7
        "op5071l1",      // 一加 7 Pro
        "op507fl1",      // 一加 7T
        "op507fl1"       // 一加 7T Pro
    ).distinct()

    // 支持 TempRoot（小米 HyperOS）的机型列表
    private val tempRootDevices = listOf(
        "munch",      // Redmi K40S / POCO F4
        "marble",     // Redmi Note 12 Turbo / POCO F5
        "mayfly",     // 小米12S
        "thor",       // Redmi K50 Ultra
        "rubens",     // Redmi K50 Pro
        "mondrian",   // 小米13
        "fuxi",       // 小米13 Pro
        "nuwa",       // 小米13 Ultra
        "shennong",   // Redmi K60
        "xaga",       // Redmi K60 Pro
        "rembrandt",  // 小米12S Pro
        "diting",     // 红米K60 至尊版
        "corot",      // 小米14
        "manet",      // Redmi Note 13 Pro+
        "gold",       // 小米14 Pro
        "houji",      // 小米14 Ultra
        "sapphire",   // Redmi Note 13 Pro
        "sunstone",   // Redmi Note 12 4G
        "topaz",      // Redmi Note 12 5G
        "tapas",      // Redmi Note 12 Pro
        "ruby",       // Redmi Note 12 Pro+
        "light",      // 小米 Civi 3
        "zijin",      // 小米 Civi 4 Pro
        "duchamp",    // 小米13 Lite
        "veux",       // POCO X4 Pro
        "peux",       // POCO X4 GT
        "ingres",     // POCO X5 Pro
        "redwood",    // POCO X5
        "moonstone",  // POCO X6
        "garnet",     // POCO X6 Pro
        "emerald",    // POCO X6 Neo
        "odin",       // Redmi K70
        "manet",      // Redmi K70 Pro
        "diting",     // Redmi K70E
        "aurora",     // Redmi K70 Ultra
        "shennong",   // Redmi K60
        "mondrian",   // 小米13
        "fuxi",       // 小米13 Pro
        "nuwa",       // 小米13 Ultra
        "corot",      // 小米14
        "gold",       // 小米14 Pro
        "houji",      // 小米14 Ultra
        "ruyi",       // 小米15
        "lumen",      // 小米15 Pro
        "shennong"    // Redmi K60
    ).distinct()

    actual fun detectDevice(): DeviceRootInfo {
        return try {
            val brand = exec("getprop ro.product.brand").trim()
            val model = exec("getprop ro.product.model").trim()
            val device = exec("getprop ro.product.device").trim().lowercase()
            val androidVersion = exec("getprop ro.build.version.release").trim()
            val kernelVersion = exec("uname -r").trim()

            // 判断是否 GKI 设备（内核版本 >= 5.10）
            val isGKI = try {
                val major = kernelVersion.substringBefore(".").toIntOrNull() ?: 0
                val minor = kernelVersion.substringAfter(".").substringBefore(".").toIntOrNull() ?: 0
                major > 5 || (major == 5 && minor >= 10)
            } catch (e: Exception) { false }

            val bootloaderUnlocked = isBootloaderUnlocked()

            // 检测临时 root 支持
            val isOnePlus = brand.equals("oneplus", ignoreCase = true) ||
                            brand.equals("一加", ignoreCase = true) ||
                            ghostLockDevices.any { device.contains(it, ignoreCase = true) }
            val isXiaomi = brand.equals("xiaomi", ignoreCase = true) ||
                           brand.equals("redmi", ignoreCase = true) ||
                           brand.equals("poco", ignoreCase = true) ||
                           brand.equals("小米", ignoreCase = true) ||
                           brand.equals("红米", ignoreCase = true)

            val tempRootType = when {
                isOnePlus -> TempRootType.GHOSTLOCK
                isXiaomi && tempRootDevices.any { device.contains(it, ignoreCase = true) } -> TempRootType.TEMPROOT
                else -> TempRootType.NONE
            }
            val tempRootSupported = tempRootType != TempRootType.NONE

            val supportedMethods = mutableListOf<RootMethod>()
            if (bootloaderUnlocked) {
                supportedMethods.add(RootMethod.MAGISK)
                if (isGKI) supportedMethods.add(RootMethod.KERNELSU)
            }
            if (tempRootSupported) supportedMethods.add(RootMethod.TEMP_ROOT)

            DeviceRootInfo(
                brand = brand,
                model = model,
                device = device,
                androidVersion = androidVersion,
                kernelVersion = kernelVersion,
                isBootloaderUnlocked = bootloaderUnlocked,
                isGKI = isGKI,
                supportedMethods = supportedMethods,
                tempRootSupported = tempRootSupported,
                tempRootType = tempRootType
            )
        } catch (e: Exception) {
            DeviceRootInfo()
        }
    }

    actual fun extractBootImage(): RootResult {
        return try {
            // 检查是否有 root 权限（提取 boot.img 需要 root 或 fastboot）
            if (!isRooted()) {
                return RootResult(false, "需要 Root 权限或 Fastboot 模式才能提取 boot.img", "extract")
            }

            val bootPath = "/sdcard/boot.img"
            // 尝试通过 dd 提取 boot 分区
            val result = exec("dd if=/dev/block/by-name/boot of=$bootPath 2>&1")
            if (File(bootPath).exists() && File(bootPath).length() > 0) {
                RootResult(true, "boot.img 已提取到: $bootPath", "extract")
            } else {
                // 尝试其他路径
                val result2 = exec("dd if=/dev/block/bootdevice/by-name/boot of=$bootPath 2>&1")
                if (File(bootPath).exists() && File(bootPath).length() > 0) {
                    RootResult(true, "boot.img 已提取到: $bootPath", "extract")
                } else {
                    // 尝试 init_boot（Android 13+）
                    val initBootPath = "/sdcard/init_boot.img"
                    exec("dd if=/dev/block/by-name/init_boot of=$initBootPath 2>&1")
                    if (File(initBootPath).exists() && File(initBootPath).length() > 0) {
                        RootResult(true, "init_boot.img 已提取到: $initBootPath", "extract")
                    } else {
                        RootResult(false, "提取 boot.img 失败，请手动提取", "extract")
                    }
                }
            }
        } catch (e: Exception) {
            RootResult(false, "提取失败: ${e.message}", "extract")
        }
    }

    actual fun patchWithMagisk(bootPath: String): RootResult {
        return try {
            // 检查 Magisk 是否安装
            val magiskInstalled = exec("pm list packages | grep com.topjohnwu.magisk").isNotEmpty() ||
                                   exec("pm list packages | grep com.topjohnwu.magisk").contains("magisk", ignoreCase = true)
            if (!magiskInstalled) {
                return RootResult(false, "请先安装 Magisk APP", "patch")
            }

            // Magisk 修补需要在 APP 内操作，这里尝试命令行
            val result = exec("magisk --patch-boot-image $bootPath 2>&1")
            // 查找修补后的文件
            val patchedFiles = exec("ls /sdcard/Download/magisk_patched-*.img 2>/dev/null").trim()
            val patchedFiles2 = exec("ls /sdcard/magisk_patched-*.img 2>/dev/null").trim()

            val allPatched = listOf(patchedFiles, patchedFiles2).filter { it.isNotEmpty() }
            if (allPatched.isNotEmpty()) {
                RootResult(true, "Magisk 修补成功: ${allPatched.first()}", "patch")
            } else {
                RootResult(false, "Magisk 修补失败，请在 Magisk APP 中手动选择 boot.img 进行修补", "patch")
            }
        } catch (e: Exception) {
            RootResult(false, "修补失败: ${e.message}", "patch")
        }
    }

    actual fun patchWithKernelSU(bootPath: String): RootResult {
        return try {
            // 检查 KernelSU 是否安装
            val ksuInstalled = exec("pm list packages | grep me.weishu.kernelsu").isNotEmpty() ||
                               exec("pm list packages | grep me.weishu.kernelsu").contains("kernelsu", ignoreCase = true)
            if (!ksuInstalled) {
                return RootResult(false, "请先安装 KernelSU APP", "patch")
            }

            // KernelSU 修补 boot.img
            val result = exec("ksud patch-boot $bootPath 2>&1")
            val patchedFiles = exec("ls /sdcard/Download/ksu_patched-*.img 2>/dev/null").trim()
            val patchedFiles2 = exec("ls /sdcard/ksu_patched-*.img 2>/dev/null").trim()

            val allPatched = listOf(patchedFiles, patchedFiles2).filter { it.isNotEmpty() }
            if (allPatched.isNotEmpty()) {
                RootResult(true, "KernelSU 修补成功: ${allPatched.first()}", "patch")
            } else {
                RootResult(false, "KernelSU 修补失败，请在 KernelSU APP 中手动修补", "patch")
            }
        } catch (e: Exception) {
            RootResult(false, "修补失败: ${e.message}", "patch")
        }
    }

    actual fun flashBootImage(bootPath: String): RootResult {
        return try {
            val bootFile = File(bootPath)
            if (!bootFile.exists()) {
                return RootResult(false, "boot.img 文件不存在: $bootPath", "flash")
            }

            // 检查是否在 fastboot 模式
            val inFastboot = exec("getprop sys.bootloader").contains("fastboot", ignoreCase = true)

            if (inFastboot) {
                // fastboot 模式下刷入
                val result = exec("fastboot flash boot $bootPath 2>&1")
                if (result.contains("OKAY", ignoreCase = true) || result.contains("success", ignoreCase = true) || result.contains("Sending", ignoreCase = true)) {
                    RootResult(true, "boot.img 刷入成功，建议重启设备", "flash")
                } else {
                    RootResult(false, "刷入失败: $result", "flash")
                }
            } else {
                // 需要 root 权限直接刷入 boot 分区
                if (!isRooted()) {
                    return RootResult(false, "需要 Root 权限或 Fastboot 模式才能刷入 boot.img", "flash")
                }
                val result = exec("dd if=$bootPath of=/dev/block/by-name/boot 2>&1")
                RootResult(true, "boot.img 已通过 dd 刷入，建议重启设备", "flash")
            }
        } catch (e: Exception) {
            RootResult(false, "刷入失败: ${e.message}", "flash")
        }
    }

    actual fun tempRoot(): RootResult {
        return try {
            val device = exec("getprop ro.product.device").trim().lowercase()
            val brand = exec("getprop ro.product.brand").trim()

            // 判断临时 root 类型
            val isOnePlus = brand.equals("oneplus", ignoreCase = true) ||
                            brand.equals("一加", ignoreCase = true) ||
                            ghostLockDevices.any { device.contains(it, ignoreCase = true) }

            if (isOnePlus) {
                return ghostLockTempRoot()
            }

            // 小米 TempRoot
            if (tempRootDevices.any { device.contains(it, ignoreCase = true) }) {
                return xiaomiTempRoot()
            }

            RootResult(false, "当前机型 ($device) 不支持临时 Root", "temproot")
        } catch (e: Exception) {
            RootResult(false, "临时 Root 失败: ${e.message}", "temproot")
        }
    }

    // 一加 GhostLock 临时 root（CVE-2026-43499）
    private fun ghostLockTempRoot(): RootResult {
        return try {
            val exploitPath = "/data/local/tmp/a/e"

            // 检查 exploit 二进制文件是否存在
            val exploitFile = File(exploitPath)
            if (!exploitFile.exists()) {
                return RootResult(false, "GhostLock exploit 未找到，请先下载 ghostlock 二进制文件并推送到 $exploitPath", "ghostlock")
            }

            // 设置权限
            exec("chmod 755 $exploitPath")

            // 启用 ADB TCP（GhostLock 需要）
            exec("setprop persist.adb.tcp.port 5555")
            exec("stop adbd && start adbd")

            // 运行 exploit
            val result = exec("$exploitPath 2>&1")

            // 检查是否成功获取 root
            val idResult = exec("id")
            if (idResult.contains("uid=0") || result.contains("root", ignoreCase = true) || result.contains("success", ignoreCase = true)) {
                RootResult(true, "GhostLock 临时 Root 成功！基于 CVE-2026-43499，重启后失效\n\n$result", "ghostlock")
            } else {
                RootResult(false, "GhostLock 临时 Root 失败\n\n$result", "ghostlock")
            }
        } catch (e: Exception) {
            RootResult(false, "GhostLock 失败: ${e.message}", "ghostlock")
        }
    }

    // 小米 TempRoot 临时 root
    private fun xiaomiTempRoot(): RootResult {
        return try {
            val tempRootScript = File(appContext.filesDir, "temproot.sh")
            if (!tempRootScript.exists()) {
                return RootResult(false, "TempRoot 工具未初始化，请先下载资源", "temproot")
            }

            val result = exec("sh ${tempRootScript.absolutePath} 2>&1")
            val idResult = exec("id")
            if (idResult.contains("uid=0") || result.contains("root", ignoreCase = true)) {
                RootResult(true, "临时 Root 成功！注意：重启后失效\n\n$result", "temproot")
            } else {
                RootResult(false, "临时 Root 失败\n\n$result", "temproot")
            }
        } catch (e: Exception) {
            RootResult(false, "临时 Root 失败: ${e.message}", "temproot")
        }
    }

    actual fun isRooted(): Boolean {
        return try {
            val result = exec("id")
            result.contains("uid=0")
        } catch (e: Exception) {
            false
        }
    }

    actual fun isBootloaderUnlocked(): Boolean {
        return try {
            val result = exec("getprop ro.boot.flash.locked").trim()
            result == "0"
        } catch (e: Exception) {
            false
        }
    }

    actual fun getTempRootSupportedDevices(): List<String> {
        return (ghostLockDevices + tempRootDevices).distinct()
    }

    // 获取 GhostLock 支持的一加机型
    fun getGhostLockSupportedDevices(): List<String> = ghostLockDevices

    // 检查 KernelSU 是否已安装
    actual fun isKernelSUInstalled(): Boolean {
        return try {
            // 检查 KernelSU 应用包名
            val pm = appContext.packageManager
            val packages = pm.getInstalledPackages(0)
            val hasKSUApp = packages.any {
                it.packageName == "me.weishu.kernelsu" ||
                it.packageName == "com.kernelsu" ||
                it.packageName.contains("kernelsu", ignoreCase = true)
            }
            // 检查内核是否有 KernelSU
            val ksuCheck = exec("cat /proc/ksu_version 2>/dev/null || echo ''")
            hasKSUApp || ksuCheck.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    // 检查 Magisk 是否已安装
    actual fun isMagiskInstalled(): Boolean {
        return try {
            val result = exec("magisk -v 2>/dev/null || echo ''")
            result.isNotBlank()
        } catch (e: Exception) {
            false
        }
    }

    // 获取 KernelSU 版本
    actual fun getKernelSUVersion(): String {
        return try {
            val version = exec("cat /proc/ksu_version 2>/dev/null").trim()
            if (version.isNotBlank()) version else "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    // 获取 Magisk 版本
    actual fun getMagiskVersion(): String {
        return try {
            val version = exec("magisk -v 2>/dev/null").trim()
            if (version.isNotBlank()) version else "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    // 执行命令
    private fun exec(command: String): String {
        return try {
            ADBTools.execCommand(command).output
        } catch (e: Exception) {
            ""
        }
    }
}
