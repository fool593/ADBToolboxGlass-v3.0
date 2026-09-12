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
                return RootResult(false, "Root or Fastboot is required to extract boot.img", "extract")
            }

            val bootPath = "/sdcard/boot.img"
            // 尝试通过 dd 提取 boot 分区
            val result = exec("dd if=/dev/block/by-name/boot of=$bootPath 2>&1")
            if (File(bootPath).exists() && File(bootPath).length() > 0) {
                RootResult(true, "boot.img extracted to: $bootPath", "extract")
            } else {
                // 尝试其他路径
                val result2 = exec("dd if=/dev/block/bootdevice/by-name/boot of=$bootPath 2>&1")
                if (File(bootPath).exists() && File(bootPath).length() > 0) {
                    RootResult(true, "boot.img extracted to: $bootPath", "extract")
                } else {
                    // 尝试 init_boot（Android 13+）
                    val initBootPath = "/sdcard/init_boot.img"
                    exec("dd if=/dev/block/by-name/init_boot of=$initBootPath 2>&1")
                    if (File(initBootPath).exists() && File(initBootPath).length() > 0) {
                        RootResult(true, "init_boot.img extracted to: $initBootPath", "extract")
                    } else {
                        RootResult(false, "Failed to extract boot.img, please extract manually", "extract")
                    }
                }
            }
        } catch (e: Exception) {
            RootResult(false, "Extraction failed: ${e.message}", "extract")
        }
    }

    actual fun patchWithMagisk(bootPath: String): RootResult {
        return try {
            // 检查 Magisk 是否安装
            val magiskInstalled = exec("pm list packages | grep com.topjohnwu.magisk").isNotEmpty() ||
                                   exec("pm list packages | grep com.topjohnwu.magisk").contains("magisk", ignoreCase = true)
            if (!magiskInstalled) {
                return RootResult(false, "Please install Magisk app first", "patch")
            }

            // Magisk 修补需要在 APP 内操作，这里尝试命令行
            val result = exec("magisk --patch-boot-image $bootPath 2>&1")
            // 查找修补后的文件
            val patchedFiles = exec("ls /sdcard/Download/magisk_patched-*.img 2>/dev/null").trim()
            val patchedFiles2 = exec("ls /sdcard/magisk_patched-*.img 2>/dev/null").trim()

            val allPatched = listOf(patchedFiles, patchedFiles2).filter { it.isNotEmpty() }
            if (allPatched.isNotEmpty()) {
                RootResult(true, "Magisk patch success: ${allPatched.first()}", "patch")
            } else {
                RootResult(false, "Magisk patch failed, please select boot.img manually in Magisk app", "patch")
            }
        } catch (e: Exception) {
            RootResult(false, "Patch failed: ${e.message}", "patch")
        }
    }

    actual fun patchWithKernelSU(bootPath: String): RootResult {
        return try {
            // 检查 KernelSU 是否安装
            val ksuInstalled = exec("pm list packages | grep me.weishu.kernelsu").isNotEmpty() ||
                               exec("pm list packages | grep me.weishu.kernelsu").contains("kernelsu", ignoreCase = true)
            if (!ksuInstalled) {
                return RootResult(false, "Please install KernelSU app first", "patch")
            }

            // KernelSU 修补 boot.img
            val result = exec("ksud patch-boot $bootPath 2>&1")
            val patchedFiles = exec("ls /sdcard/Download/ksu_patched-*.img 2>/dev/null").trim()
            val patchedFiles2 = exec("ls /sdcard/ksu_patched-*.img 2>/dev/null").trim()

            val allPatched = listOf(patchedFiles, patchedFiles2).filter { it.isNotEmpty() }
            if (allPatched.isNotEmpty()) {
                RootResult(true, "KernelSU patch success: ${allPatched.first()}", "patch")
            } else {
                RootResult(false, "KernelSU patch failed, please patch manually in KernelSU app", "patch")
            }
        } catch (e: Exception) {
            RootResult(false, "Patch failed: ${e.message}", "patch")
        }
    }

    actual fun flashBootImage(bootPath: String): RootResult {
        return try {
            val bootFile = File(bootPath)
            if (!bootFile.exists()) {
                return RootResult(false, "boot.img not found: $bootPath", "flash")
            }

            // 检查是否在 fastboot 模式
            val inFastboot = exec("getprop sys.bootloader").contains("fastboot", ignoreCase = true)

            if (inFastboot) {
                // fastboot 模式下刷入
                val result = exec("fastboot flash boot $bootPath 2>&1")
                if (result.contains("OKAY", ignoreCase = true) || result.contains("success", ignoreCase = true) || result.contains("Sending", ignoreCase = true)) {
                    RootResult(true, "boot.img flashed successfully, please reboot", "flash")
                } else {
                    RootResult(false, "Flash failed: $result", "flash")
                }
            } else {
                // 需要 root 权限直接刷入 boot 分区
                if (!isRooted()) {
                    return RootResult(false, "Root or Fastboot is required to flash boot.img", "flash")
                }
                val result = exec("dd if=$bootPath of=/dev/block/by-name/boot 2>&1")
                RootResult(true, "boot.img flashed via dd, please reboot", "flash")
            }
        } catch (e: Exception) {
            RootResult(false, "Flash failed: ${e.message}", "flash")
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

            RootResult(false, "Current device ($device) does not support temp root", "temproot")
        } catch (e: Exception) {
            RootResult(false, "Temp root failed: ${e.message}", "temproot")
        }
    }

    // 一加 GhostLock 临时 root（CVE-2026-43499）
    private fun ghostLockTempRoot(): RootResult {
        return try {
            val exploitPath = "/data/local/tmp/a/e"

            // 检查 exploit 二进制文件是否存在
            val exploitFile = File(exploitPath)
            if (!exploitFile.exists()) {
                return RootResult(false, "GhostLock exploit not found, please download ghostlock binary and push to $exploitPath", "ghostlock")
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
                RootResult(true, "GhostLock temp root success! Based on CVE-2026-43499, lost after reboot\n\n$result", "ghostlock")
            } else {
                RootResult(false, "GhostLock temp root failed\n\n$result", "ghostlock")
            }
        } catch (e: Exception) {
            RootResult(false, "GhostLock failed: ${e.message}", "ghostlock")
        }
    }

    // 小米 TempRoot 临时 root
    private fun xiaomiTempRoot(): RootResult {
        return try {
            val tempRootScript = File(appContext.filesDir, "temproot.sh")
            if (!tempRootScript.exists()) {
                // 自动初始化基础脚本，避免报 "not initialized" 错误
                tempRootScript.writeText("""#!/system/bin/sh
# TempRoot 基础初始化脚本
echo "=== TempRoot Initializer ==="
echo "Device: $(getprop ro.product.model)"
echo "Android: $(getprop ro.build.version.release)"
echo "Kernel: $(uname -r)"
if command -v su >/dev/null 2>&1; then
    echo "su binary found, testing root..."
    su -c id 2>&1
else
    echo "No su binary found"
    echo "Please download matching TempRoot exploit for your device"
fi
""")
                tempRootScript.setExecutable(true)
            }

            val result = exec("sh ${tempRootScript.absolutePath} 2>&1")
            val idResult = exec("id")
            // 只看 uid=0 判断真正的 root，不用 result.contains("root") 避免匹配 "TempRoot" 误判
            if (idResult.contains("uid=0")) {
                RootResult(true, "Temp root success! Note: lost after reboot\n\n$result", "temproot")
            } else {
                RootResult(false, "Temp root failed - no root access obtained\n\n$result", "temproot")
            }
        } catch (e: Exception) {
            RootResult(false, "Temp root failed: ${e.message}", "temproot")
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

    // 检查 KernelSU 是否已安装（多维度检测：包名+命令+su二进制+特征文件+内核接口）
    actual fun isKernelSUInstalled(): Boolean {
        return try {
            // 1. 检查 KernelSU 应用包名（官方及各分支）
            val pm = appContext.packageManager
            val packages = pm.getInstalledPackages(0)
            val hasKSUApp = packages.any {
                it.packageName == "me.weishu.kernelsu" ||
                it.packageName == "com.kernelsu" ||
                it.packageName == "me.bmax.kernelsu" ||
                it.packageName.contains("kernelsu", ignoreCase = true)
            }
            // 2. 用 pm 命令检测（比 PackageManager 更可靠，不依赖权限）
            val pmCheck = exec("pm list packages 2>/dev/null")
            val hasKSUPm = pmCheck.contains("kernelsu", ignoreCase = true) ||
                           pmCheck.contains("me.weishu", ignoreCase = true)
            // 3. 检查 KSU 的 su 二进制（KSU 安装后会有 /data/adb/ksu/bin/su）
            val ksuSu = exec("ls /data/adb/ksu/bin/su 2>/dev/null || ls /data/adb/ksud 2>/dev/null || echo ''")
            val hasKSUSu = ksuSu.contains("su") || ksuSu.contains("ksud")
            // 4. 检查 su 命令是否来自 KSU（执行 su -V 看版本信息）
            val suVersion = exec("su -V 2>/dev/null || su --version 2>/dev/null || echo ''")
            val hasKSUSuCmd = suVersion.contains("ksu", ignoreCase = true) ||
                              suVersion.contains("kernelsu", ignoreCase = true)
            // 5. 检查内核接口 /proc/ksu_version
            val ksuVersion = exec("cat /proc/ksu_version 2>/dev/null || echo ''")
            val hasKSUProc = ksuVersion.isNotBlank()
            // 6. 检查 /data/adb/ksu 目录
            val ksuDir = exec("ls -d /data/adb/ksu 2>/dev/null || echo ''")
            val hasKSUDir = ksuDir.contains("/data/adb/ksu")
            hasKSUApp || hasKSUPm || hasKSUSu || hasKSUSuCmd || hasKSUProc || hasKSUDir
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

    // 已检测到 KSU 时，直接通过 KSU 获取 root 权限
    actual fun rootWithKSU(): RootResult {
        return try {
            // KSU 提供了 su，直接执行 su -c id 验证 root
            val result = exec("su -c id 2>&1")
            if (result.contains("uid=0")) {
                RootResult(true, "Root acquired via KernelSU!\n\n$result", "ksu")
            } else {
                // 尝试 ksud 方式
                val ksudResult = exec("ksud root 2>&1 || su -c 'id' 2>&1")
                if (ksudResult.contains("uid=0") || ksudResult.contains("root", ignoreCase = true)) {
                    RootResult(true, "Root acquired via KernelSU!\n\n$ksudResult", "ksu")
                } else {
                    RootResult(false, "KernelSU detected but root access failed. Please grant root permission in KernelSU app.\n\n$result", "ksu")
                }
            }
        } catch (e: Exception) {
            RootResult(false, "KernelSU root failed: ${e.message}", "ksu")
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

    // ==================== Root 方法自动检测与执行 ====================

    // 所有内置的 root 方法列表（基于网上公开的漏洞与教程）
    private val allRootMethods = listOf(
        RootMethodInfo(
            id = "vivo_mtk_ldpreload",
            name = "vivo/iQOO 天玑 LD_PRELOAD 临时Root",
            brand = "vivo",
            chipset = "mediatek",
            principle = "利用 LD_PRELOAD 环境变量注入 preload.so，在系统开机阶段绕过权限校验获取临时Root",
            riskLevel = "medium",
            requiresComputer = true,
            requiresKSU = true,
            supportedDevices = "iQOO Neo10 Pro+/Neo11/Z10 Turbo Pro/Neo9 等天玑机型",
            description = "降级指定版本→推送preload.so→锁屏重启→锁屏状态提权→加载KSU late-load。重启后失效，需重新操作。"
        ),
        RootMethodInfo(
            id = "xiaomi_mtk_ldpreload",
            name = "小米/红米 天玑 LDPRELOAD 临时Root",
            brand = "xiaomi",
            chipset = "mediatek",
            principle = "利用Linux内核LDPRELOAD环境变量提权漏洞，开机阶段注入动态链接库绕过权限校验",
            riskLevel = "medium",
            requiresComputer = true,
            requiresKSU = true,
            supportedDevices = "红米Turbo4/Turbo5等新款天玑机型（注：天玑8100/Note11T Pro不支持此漏洞）",
            description = "全程在系统相册完成操作，不用进入MTK底层刷机模式。拿到临时Root后可刷入KSU。注意：此漏洞仅支持部分新款天玑机型，老款天玑8100不受影响。"
        ),
        RootMethodInfo(
            id = "redmi_note11tpro_misaka_temp_root",
            name = "红米Note11T Pro 天玑8100 临时Root (酷安@御坂114515)",
            brand = "xiaomi",
            chipset = "mediatek",
            principle = "利用ADB权限调用route权限提权，SELinux切宽容模式后加载KSU LKM模块获取临时root，无需电脑、无需解BL",
            riskLevel = "medium",
            requiresComputer = false,
            requiresKSU = true,
            supportedDevices = "红米Note 11T Pro/Pro+ (天玑8100)、红米K50/K60系列、Turbo3/4、Redmi 13/14/15等",
            description = "酷安@御坂114515 开发的天玑临时root工具。1.从酷安下载对应提权工具 2.安装KSU管理器 3.授予ADB权限 4.执行提权脚本，SELinux自动切宽容 5.加载KSU LKM模块获得临时root。重启后失效，需重新执行。支持天玑8100/9000/9200/9300/9400/9500等。"
        ),
        RootMethodInfo(
            id = "redmi_note11tpro_lkb_unlock",
            name = "红米Note11T Pro LKB单刷解BL",
            brand = "xiaomi",
            chipset = "mediatek",
            principle = "利用专属定制的LKB单刷文件，在线刷工具中只勾选LKB项刷入修改版LKB镜像，绕过官方解锁等待期",
            riskLevel = "high",
            requiresComputer = true,
            requiresKSU = false,
            supportedDevices = "红米Note 11T Pro (天玑8100)、Note 12T Pro、小米CV3",
            description = "1.下载对应机型专属LKB单刷文件 2.手机进入fastboot模式 3.用MiFlash工具只勾选LKB项刷入 4.刷入修改版LKB镜像后用配套工具完成最终解锁 5.解BL后刷入KSU/Magisk获取root。注意：解BL会清除全部数据，请先备份。"
        ),
        RootMethodInfo(
            id = "xiaomi_fastboot_cmdline",
            name = "小米 fastboot cmdline 免解BL Root",
            brand = "xiaomi",
            chipset = "generic",
            principle = "利用fastboot cmdline漏洞修改启动参数使SELinux宽容，再利用小米质量服务漏洞以root运行ksud",
            riskLevel = "high",
            requiresComputer = true,
            requiresKSU = true,
            supportedDevices = "小米K80/红米系列等支持fastboot cmdline漏洞的机型",
            description = "获取ksud→fastboot修改cmdline→SELinux宽容→利用miui.mqsas漏洞运行ksud→late-load模式→软重启注入Zygisk。"
        ),
        RootMethodInfo(
            id = "oneplus_qualcomm_jailbreak",
            name = "一加/小米 骁龙 越狱模式临时Root",
            brand = "oneplus",
            chipset = "qualcomm",
            principle = "KernelSU官方越狱模式，利用fastboot漏洞临时启动修改后的boot获取root",
            riskLevel = "low",
            requiresComputer = true,
            requiresKSU = true,
            supportedDevices = "骁龙8gen2及以上机型（一加Ace6T/小米14等）",
            description = "下载可越狱版本KSU→进入fastboot→残芯ADB一键临时root→重启后KSU显示已root。需过深度测试。"
        ),
        RootMethodInfo(
            id = "samsung_wssyncmldm",
            name = "三星锁BL Root (wssyncmldm漏洞)",
            brand = "samsung",
            chipset = "generic",
            principle = "利用wssyncmldm系统服务漏洞，通过Root My Galaxy应用触发提权",
            riskLevel = "medium",
            requiresComputer = false,
            requiresKSU = true,
            supportedDevices = "部分三星锁BL机型",
            description = "安装Root My Galaxy→点击Security Check→安装KernelSU→按提示完成。可能需要多次点击Security Check。"
        ),
        RootMethodInfo(
            id = "qualcomm_cmdline_injection",
            name = "高通骁龙 SELinux宽容模式提权 (cmdline注入)",
            brand = "generic",
            chipset = "qualcomm",
            principle = "利用fastboot oem set-gpu-preemption命令注入漏洞，修改启动参数使SELinux变为宽容模式，再利用系统服务漏洞提权",
            riskLevel = "high",
            requiresComputer = true,
            requiresKSU = true,
            supportedDevices = "高通骁龙8 Gen2及以上全品牌机型（小米/一加/OPPO/vivo等）",
            description = "全品牌通用方法。1.进入fastboot模式 2.执行 fastboot oem set-gpu-preemption 0 androidboot.selinux=permissive 3.重启后SELinux宽容 4.利用miui.mqsas或其他系统服务漏洞运行ksud 5.KSU late-load获取root。注意：需2026年2月前安全补丁。"
        ),
        RootMethodInfo(
            id = "xiaomi_qc_temp_root",
            name = "小米高通QC免解BL临时Root",
            brand = "xiaomi",
            chipset = "qualcomm",
            principle = "小米QC免解BL工具，利用高通平台漏洞在fastboot模式下临时启动修改后的boot获取root",
            riskLevel = "medium",
            requiresComputer = true,
            requiresKSU = true,
            supportedDevices = "骁龙8 Gen1到8e5的小米/红米机型（需2月补丁之前）",
            description = "1.下载对应机型的QC免解BL工具包 2.手机进入fastboot模式 3.电脑执行一键root脚本 4.手机自动重启 5.打开KSU管理器点击越狱。注意：老机型(8Gen1/8Gen2)降级后成功率更高。"
        ),
        RootMethodInfo(
            id = "ghostlock_oneplus",
            name = "GhostLock 一加锁BL越狱",
            brand = "oneplus",
            chipset = "qualcomm",
            principle = "利用一加Bootloader漏洞，在锁BL状态下临时启动修改后的boot获取root权限",
            riskLevel = "high",
            requiresComputer = true,
            requiresKSU = true,
            supportedDevices = "OnePlus Ace 6T、OnePlus 15、小米17（骁龙8 Elite/8Gen5）",
            description = "1.下载GhostLock工具和对应机型的boot镜像 2.手机进入fastboot模式 3.电脑执行 ghostlock boot modified_boot.img 4.手机临时启动修改后的boot 5.打开KSU获取root。重启后root失效，需重新操作。"
        ),
        RootMethodInfo(
            id = "temproot_hyperos",
            name = "TempRoot HyperOS一键临时Root",
            brand = "xiaomi",
            chipset = "generic",
            principle = "HyperOS专用一键临时Root应用，内置多机型exploit，自动检测设备并执行对应提权",
            riskLevel = "medium",
            requiresComputer = false,
            requiresKSU = true,
            supportedDevices = "Redmi K60/K60E/K50/K50 Pro等HyperOS机型",
            description = "1.从GitHub(314xxx/Temproot)下载TempRoot APK 2.安装并打开 3.授予ADB/Shizuku权限 4.点击一键临时Root 5.自动执行exploit并加载KSU。支持机型：mondrian(K60)、rembrandt(K60E)、rubens(K50)、matisse(K50 Pro)。"
        ),
        RootMethodInfo(
            id = "vivo_dimensity_9400_temp_root",
            name = "vivo天玑9400免拆临时Root",
            brand = "vivo",
            chipset = "mediatek",
            principle = "vivo X200 Pro等天玑9400新机的免拆临时Root方案，不碰硬件不丢保修重启清零",
            riskLevel = "medium",
            requiresComputer = true,
            requiresKSU = true,
            supportedDevices = "vivo X200 Pro、X200、iQOO 13等天玑9400机型",
            description = "1.下载对应机型的天玑9400临时Root工具包 2.手机开启USB调试连接电脑 3.执行提权脚本推送preload文件 4.锁屏状态下重启 5.锁屏状态执行提权命令 6.亮屏后加载KSU获取临时root。注意：操作有变砖风险，请谨慎。"
        ),
        RootMethodInfo(
            id = "dirtypipe_cve_2022_0847",
            name = "DirtyPipe (CVE-2022-0847) 临时Root",
            brand = "generic",
            chipset = "generic",
            principle = "Linux内核DirtyPipe漏洞，非root用户可覆盖任意只读文件，通过覆盖su二进制获取临时root",
            riskLevel = "medium",
            requiresComputer = false,
            requiresKSU = false,
            supportedDevices = "Linux内核5.8~5.16.11的Android设备（2022年3月前补丁）",
            description = "1.下载DirtyPipe exploit二进制 2.推送到/data/local/tmp/ 3.chmod +x 4.执行exploit覆盖/system/bin/su 5.执行su获取root。注意：此漏洞在2022年3月安全补丁中已修复，仅老设备可用。"
        ),
        RootMethodInfo(
            id = "samsung_root_my_galaxy_s25",
            name = "三星Root My Galaxy S25临时Root",
            brand = "samsung",
            chipset = "qualcomm",
            principle = "利用三星系统服务漏洞，通过Root My Galaxy应用触发提权，不触发Knox不解锁BL",
            riskLevel = "medium",
            requiresComputer = false,
            requiresKSU = true,
            supportedDevices = "Galaxy S25 Ultra（完全支持）、S25/S25+/S24系列（测试中）",
            description = "1.从GitHub下载Root My Galaxy APK 2.安装并打开 3.点击Security Check按钮（可能需要多次点击） 4.按提示安装KernelSU 5.完成后获得root权限。注意：不触发Knox，不解锁BL，重启后root失效。Exynos处理器机型不支持。"
        ),
        RootMethodInfo(
            id = "gbl_root_canoe",
            name = "GBL Root Canoe 通用Bootloader漏洞",
            brand = "generic",
            chipset = "qualcomm",
            principle = "利用GBL(Generic Bootloader Loader)漏洞，让真实ABL加载嵌入式superfastboot BDS，实现Fake Locked Bootloader状态",
            riskLevel = "high",
            requiresComputer = true,
            requiresKSU = false,
            supportedDevices = "骁龙8 Gen5/8 Elite(Gen5)机型",
            description = "1.下载GBL Root Canoe工具 2.手机进入fastboot模式 3.电脑执行漏洞利用脚本 4.ABL加载嵌入式superfastboot BDS 5.实现Fake Locked状态并启动修改后的boot。注意：此方法较新，支持机型有限，操作有变砖风险。"
        ),
        RootMethodInfo(
            id = "mtk_generic_old",
            name = "MTK通用临时Root (老漏洞)",
            brand = "generic",
            chipset = "mediatek",
            principle = "XDA大神针对MTK的提权漏洞，2020年3月安全更新后被修复",
            riskLevel = "low",
            requiresComputer = true,
            requiresKSU = false,
            supportedDevices = "2020年3月前未打安全补丁的MTK机型",
            description = "仅适用于老款MTK机型，新系统已修复此漏洞。"
        ),
        RootMethodInfo(
            id = "cve_2025_21479",
            name = "CVE-2025-21479 vivo Neo9 提权",
            brand = "vivo",
            chipset = "mediatek",
            principle = "利用CVE-2025-21479内核漏洞提权，推送exploit二进制执行",
            riskLevel = "high",
            requiresComputer = true,
            requiresKSU = false,
            supportedDevices = "vivo iQOO Neo9 (特定固件版本)",
            description = "推送exploit_vivo_neo9/rootc/su到/data/local/tmp→设置可执行→重启获取→执行exploit提权。"
        ),
        RootMethodInfo(
            id = "ksu_late_load",
            name = "KernelSU late-load 模式",
            brand = "generic",
            chipset = "generic",
            principle = "通过已获取的临时root权限，加载KSU的ksud并使用late-load模式注入系统",
            riskLevel = "low",
            requiresComputer = false,
            requiresKSU = true,
            supportedDevices = "所有已安装KSU且已获取临时root的设备",
            description = "找到libksud.so路径→执行late-load --allow-shell --package-name me.weishu.kernelsu→KSU弹框授权→获得root。"
        ),
        RootMethodInfo(
            id = "magisk_patch_boot",
            name = "Magisk 修补boot (需解BL)",
            brand = "generic",
            chipset = "generic",
            principle = "提取boot.img→Magisk修补→fastboot刷入修补后的boot",
            riskLevel = "low",
            requiresComputer = true,
            requiresKSU = false,
            supportedDevices = "所有已解锁Bootloader的设备",
            description = "标准Magisk root流程，需先解锁BL。提取boot→Magisk修补→fastboot flash boot→重启。"
        ),
        RootMethodInfo(
            id = "ksu_patch_boot",
            name = "KernelSU 修补boot (需解BL)",
            brand = "generic",
            chipset = "generic",
            principle = "提取boot.img→KernelSU修补→fastboot刷入修补后的boot",
            riskLevel = "low",
            requiresComputer = true,
            requiresKSU = true,
            supportedDevices = "所有已解锁Bootloader且内核支持KSU的设备",
            description = "标准KernelSU root流程，需先解锁BL。提取boot→KSU修补→fastboot flash boot→重启安装KSU管理器。"
        )
    )

    // 获取所有内置的 root 方法
    actual fun getAllRootMethods(): List<RootMethodInfo> = allRootMethods

    // 获取当前设备可用的 root 方法（根据品牌/芯片/系统版本匹配）
    actual fun getAvailableRootMethods(): List<RootMethodInfo> {
        return try {
            val info = detectDevice()
            val brand = info.brand.lowercase()
            // 直接用命令检测芯片类型
            val hardware = exec("getprop ro.hardware 2>/dev/null").lowercase()
            val chipsetName = exec("getprop ro.board.platform 2>/dev/null").lowercase()
            val isMTK = hardware.contains("mt") || chipsetName.contains("mt") ||
                        chipsetName.contains("mediatek") || chipsetName.contains("dimensity") ||
                        hardware.contains("mediatek")
            val isQualcomm = hardware.contains("qcom") || chipsetName.contains("sm") ||
                             chipsetName.contains("qualcomm") || chipsetName.contains("snapdragon") ||
                             hardware.contains("qualcomm")

            allRootMethods.filter { method ->
                // 品牌匹配
                val brandMatch = method.brand == "generic" ||
                    method.brand == brand ||
                    (brand.contains("redmi") && method.brand == "xiaomi") ||
                    (brand.contains("poco") && method.brand == "xiaomi") ||
                    (brand.contains("iqoo") && method.brand == "vivo") ||
                    (brand.contains("realme") && method.brand == "oneplus")
                // 芯片匹配
                val chipMatch = method.chipset == "generic" ||
                    (method.chipset == "mediatek" && isMTK) ||
                    (method.chipset == "qualcomm" && isQualcomm)
                brandMatch && chipMatch
            }
        } catch (e: Exception) {
            allRootMethods.filter { it.brand == "generic" }
        }
    }

    // 自动检测设备并推荐最合适的 root 方法
    actual fun detectRootMethod(): RootMethodInfo {
        return try {
            val available = getAvailableRootMethods()
            // 优先推荐：已装KSU的late-load > 品牌专用临时root > 通用方法
            val hasKSU = isKernelSUInstalled()
            if (hasKSU) {
                val lateLoad = available.find { it.id == "ksu_late_load" }
                if (lateLoad != null) return lateLoad
            }
            // 优先品牌专用的临时root方法（风险低/中）
            val tempRoot = available.find { it.id.contains("temp") || it.id.contains("ldpreload") || it.id.contains("jailbreak") }
            if (tempRoot != null) return tempRoot
            // 其次品牌专用
            val brandSpecific = available.find { it.brand != "generic" }
            if (brandSpecific != null) return brandSpecific
            // 最后通用方法
            available.firstOrNull() ?: allRootMethods.last()
        } catch (e: Exception) {
            allRootMethods.find { it.id == "magisk_patch_boot" } ?: allRootMethods.last()
        }
    }

    // 执行指定的 root 方法
    actual fun executeRootMethod(methodId: String): RootResult {
        return try {
            when (methodId) {
                "ksu_late_load" -> {
                    // KernelSU late-load 模式：找到 libksud.so 并执行 late-load
                    val findResult = exec("find /data/app -name libksud.so 2>/dev/null | grep me.weishu.kernelsu | head -n 1")
                    if (findResult.isBlank()) {
                        RootResult(false, "未找到 KernelSU 的 libksud.so，请先安装 KernelSU 管理器", "ksu_late_load")
                    } else {
                        val ksudPath = findResult.trim()
                        val result = exec("$ksudPath late-load --allow-shell --package-name me.weishu.kernelsu 2>&1")
                        val idResult = exec("id")
                        if (idResult.contains("uid=0")) {
                            RootResult(true, "KernelSU late-load 成功！已获得root权限\n\n$result", "ksu_late_load")
                        } else {
                            RootResult(false, "KernelSU late-load 执行完成但未获得root，请在KSU管理器中授权\n\n$result", "ksu_late_load")
                        }
                    }
                }
                "vivo_mtk_ldpreload", "xiaomi_mtk_ldpreload" -> {
                    // LD_PRELOAD 临时root：需要 preload.so 文件，检测是否存在
                    val preloadExists = exec("ls /data/local/tmp/preload.so 2>/dev/null || ls /data/local/tmp/libpreload.so 2>/dev/null || echo ''")
                    if (preloadExists.isBlank()) {
                        RootResult(false, "未找到 preload.so 文件。请先从对应教程下载 preload.so 并推送到 /data/local/tmp/\n\n操作步骤：\n1. 下载对应机型的 preload.so\n2. adb push preload.so /data/local/tmp/\n3. 锁屏状态下重启\n4. 锁屏状态执行提权命令\n5. 亮屏后加载KSU", methodId)
                    } else {
                        // 执行 LD_PRELOAD 提权
                        val result = exec("LD_PRELOAD=/data/local/tmp/preload.so /system/bin/sh -c 'id' 2>&1")
                        if (result.contains("uid=0")) {
                            RootResult(true, "LD_PRELOAD 提权成功！已获得临时root\n\n$result\n\n接下来请执行 KSU late-load 加载模块", methodId)
                        } else {
                            RootResult(false, "LD_PRELOAD 提权失败，请确认：\n1. 系统版本是否在支持范围内\n2. 是否在锁屏状态下执行\n3. preload.so 是否匹配当前机型\n\n输出：$result", methodId)
                        }
                    }
                }
                "oneplus_qualcomm_jailbreak" -> {
                    // 骁龙越狱模式：需要进入 fastboot，这里只提供指引
                    RootResult(false, "骁龙越狱模式需要在 fastboot 模式下操作：\n\n1. 下载可越狱版本的 KernelSU\n2. 手机进入 fastboot 模式（关机后按住音量下+电源）\n3. 电脑执行 fastboot 启动修改后的 boot\n4. 重启后打开 KSU 管理器\n\n注意：此操作需要电脑配合，无法在手机端直接完成", methodId)
                }
                "xiaomi_fastboot_cmdline" -> {
                    // fastboot cmdline 漏洞：需要电脑操作
                    RootResult(false, "fastboot cmdline 免解BL Root 需要电脑操作：\n\n1. 从 KSU APK 提取 libksud.so 重命名为 ksud\n2. adb push ksud /data/local/tmp/\n3. 进入 fastboot：adb reboot bootloader\n4. fastboot oem cdmslot-info（查看槽位）\n5. fastboot reboot fastboot\n6. 修改 cmdline 使 SELinux 宽容\n7. 利用 miui.mqsas 漏洞运行 ksud\n\n注意：此操作风险较高，建议先备份数据", methodId)
                }
                "magisk_patch_boot" -> {
                    // Magisk 修补 boot
                    val extractResult = extractBootImage()
                    if (!extractResult.success) {
                        RootResult(false, "提取 boot.img 失败：${extractResult.message}", methodId)
                    } else {
                        val patchResult = patchWithMagisk(extractResult.message)
                        patchResult
                    }
                }
                "ksu_patch_boot" -> {
                    // KSU 修补 boot
                    val extractResult = extractBootImage()
                    if (!extractResult.success) {
                        RootResult(false, "提取 boot.img 失败：${extractResult.message}", methodId)
                    } else {
                        val patchResult = patchWithKernelSU(extractResult.message)
                        patchResult
                    }
                }
                "samsung_wssyncmldm" -> {
                    RootResult(false, "三星锁BL Root 需要安装 Root My Galaxy 应用：\n\n1. 从 GitHub 下载 Root My Galaxy\n2. 安装并打开\n3. 点击 Security Check 按钮（可能需要多次点击）\n4. 按提示安装 KernelSU\n5. 完成后获得 root 权限\n\n注意：此方法仅支持部分三星机型", methodId)
                }
                "cve_2025_21479" -> {
                    val exploitExists = exec("ls /data/local/tmp/exploit_vivo_neo9 2>/dev/null || echo ''")
                    if (exploitExists.isBlank()) {
                        RootResult(false, "未找到 CVE-2025-21479 exploit 文件。\n\n请从 GitHub (reaizuguo/vivo_iqoo_neo_9_root_research_on_cve-2025-21479) 下载：\n1. exploit_vivo_neo9\n2. rootc\n3. su\n推送到 /data/local/tmp/ 并设置可执行后重试", methodId)
                    } else {
                        val result = exec("/data/local/tmp/exploit_vivo_neo9 2>&1")
                        val idResult = exec("id")
                        if (idResult.contains("uid=0")) {
                            RootResult(true, "CVE-2025-21479 提权成功！\n\n$result", methodId)
                        } else {
                            RootResult(false, "CVE-2025-21479 提权失败：$result", methodId)
                        }
                    }
                }
                "redmi_note11tpro_misaka_temp_root" -> {
                    // 红米 Note 11T Pro 天玑8100 临时Root（酷安@御坂114515）
                    // 自动扫描所有常见提权脚本路径
                    val scriptScan = exec("ls /data/local/tmp/*.sh 2>/dev/null; ls /sdcard/Download/*.sh 2>/dev/null; ls /sdcard/*.sh 2>/dev/null; echo '---END---'")
                    val scriptLines = scriptScan.split("\n").map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("---") && it.endsWith(".sh") }
                    val scriptPath = scriptLines.firstOrNull() ?: ""

                    if (scriptPath.isBlank()) {
                        RootResult(false, "未检测到提权脚本。请从酷安 @御坂114515 下载红米临时root工具，将 .sh 脚本放到 /data/local/tmp/ 或 /sdcard/Download/ 目录后重试。\n\n支持机型：Note 11T Pro/Pro+、K50/K60、Turbo3/4、Redmi 13/14/15等天玑机型", methodId)
                    } else {
                        // 第一步：设置脚本可执行
                        exec("chmod 755 $scriptPath 2>/dev/null")
                        // 第二步：执行提权脚本（密码在终端里由用户输入）
                        val result = exec("sh $scriptPath 2>&1")
                        // 第三步：检查 SELinux 状态
                        val selinux = exec("getenforce 2>/dev/null").trim()
                        // 第四步：自动加载 KSU LKM 模块
                        val ksudPath = exec("find /data/app -name libksud.so 2>/dev/null | grep me.weishu.kernelsu | head -n 1").trim()
                        var ksuLoadResult = ""
                        if (ksudPath.isNotBlank()) {
                            ksuLoadResult = exec("$ksudPath late-load --allow-shell --package-name me.weishu.kernelsu 2>&1")
                        } else {
                            // 尝试 ksud 命令
                            ksuLoadResult = exec("ksud late-load --allow-shell --package-name me.weishu.kernelsu 2>&1")
                        }
                        // 第五步：验证 root
                        val idResult = exec("id").trim()
                        val hasRoot = idResult.contains("uid=0") || selinux.contains("Permissive", ignoreCase = true)

                        val output = buildString {
                            appendLine("=== 提权脚本执行 ===")
                            appendLine("脚本路径: $scriptPath")
                            appendLine()
                            appendLine(result.take(500))
                            appendLine()
                            appendLine("=== SELinux 状态: $selinux ===")
                            appendLine()
                            appendLine("=== KSU LKM 加载 ===")
                            appendLine(ksuLoadResult.take(300))
                            appendLine()
                            appendLine("=== Root 验证 ===")
                            appendLine(idResult)
                        }

                        if (hasRoot) {
                            RootResult(true, "提权成功！已自动执行脚本并加载 KSU LKM 模块。\n\n$output", methodId)
                        } else {
                            RootResult(false, "脚本已执行但未获得 root。可能原因：\n1. 脚本不匹配当前机型/系统版本\n2. 需要在终端中输入密码（请在终端执行时输入）\n3. ADB 权限不足\n\n$output", methodId)
                        }
                    }
                }
                "redmi_note11tpro_lkb_unlock" -> {
                    val lkbExists = exec("ls /data/local/tmp/lkb.img 2>/dev/null || echo ''")
                    if (lkbExists.isBlank()) {
                        RootResult(false, "红米Note11T Pro LKB单刷解BL需要电脑配合：\n\n1. 下载对应机型专属LKB单刷文件（搜索 红米Note11T Pro LKB单刷）\n2. 手机进入fastboot模式（关机后按住音量下+电源）\n3. 电脑打开MiFlash工具，只勾选LKB项\n4. 刷入修改版LKB镜像\n5. 用配套工具完成最终解锁（会清除全部数据，请先备份）\n6. 解BL后刷入KSU/Magisk获取root", methodId)
                    } else {
                        RootResult(false, "检测到 lkb.img，但LKB单刷需要在fastboot模式下用电脑MiFlash工具刷入，无法在手机端直接执行。", methodId)
                    }
                }
                "qualcomm_cmdline_injection" -> {
                    // 高通骁龙 SELinux 宽容模式提权（cmdline注入）
                    RootResult(false, "高通骁龙 SELinux宽容模式提权（cmdline注入）\n\n操作步骤（需电脑配合）：\n1. 手机进入fastboot模式（关机后按住音量下+电源）\n2. 电脑执行：fastboot oem set-gpu-preemption 0 androidboot.selinux=permissive\n3. 手机自动重启，SELinux变为宽容模式\n4. 利用系统服务漏洞运行ksud（如小米miui.mqsas）\n5. 执行 KSU late-load 获取root\n\n注意：需2026年2月前安全补丁；操作有变砖风险。", methodId)
                }
                "xiaomi_qc_temp_root" -> {
                    // 小米高通QC免解BL临时Root
                    RootResult(false, "小米高通QC免解BL临时Root\n\n操作步骤（需电脑配合）：\n1. 酷安搜索 @莫离然然 下载对应机型的QC免解BL工具包\n2. 手机降级到2月补丁之前的版本（如已在旧版本可跳过）\n3. 手机进入fastboot模式\n4. 电脑执行一键root脚本（run.bat或flash_all.sh）\n5. 手机自动重启\n6. 打开KernelSU管理器点击越狱\n\n支持：骁龙8 Gen1到8e5的小米/红米机型。注意：老机型(8Gen1/8Gen2)成功率更高。", methodId)
                }
                "ghostlock_oneplus" -> {
                    // GhostLock 一加锁BL越狱
                    RootResult(false, "GhostLock 一加锁BL越狱\n\n操作步骤（需电脑配合）：\n1. 从GitHub(joinchang/ghostlock-oneplus)下载GhostLock工具\n2. 下载对应机型的修改版boot镜像\n3. 手机进入fastboot模式\n4. 电脑执行：ghostlock boot modified_boot.img\n5. 手机临时启动修改后的boot（不刷入，重启后恢复）\n6. 打开KernelSU获取root\n\n支持：OnePlus Ace 6T、OnePlus 15、小米17（骁龙8 Elite/8Gen5）。注意：重启后root失效，需重新操作。", methodId)
                }
                "temproot_hyperos" -> {
                    // TempRoot HyperOS一键临时Root
                    val apkExists = exec("pm list packages 2>/dev/null | grep -i temproot")
                    if (apkExists.isNotBlank()) {
                        // 已安装TempRoot，尝试启动
                        RootResult(false, "检测到已安装TempRoot应用。请手动打开TempRoot应用，点击一键临时Root按钮执行。\n\n应用包名：${apkExists.trim()}", methodId)
                    } else {
                        RootResult(false, "TempRoot HyperOS一键临时Root\n\n操作步骤：\n1. 从GitHub(314xxx/Temproot)下载TempRoot APK\n2. 安装并打开TempRoot应用\n3. 授予ADB/Shizuku权限\n4. 点击一键临时Root按钮\n5. 自动执行exploit并加载KSU\n\n支持机型：Redmi K60(mondrian)、K60E(rembrandt)、K50(rubens)、K50 Pro(matisse)。", methodId)
                    }
                }
                "vivo_dimensity_9400_temp_root" -> {
                    // vivo天玑9400免拆临时Root
                    RootResult(false, "vivo天玑9400免拆临时Root\n\n操作步骤（需电脑配合）：\n1. 下载对应机型的天玑9400临时Root工具包（酷安搜索）\n2. 手机开启USB调试，连接电脑\n3. 执行提权脚本推送preload文件到手机\n4. 锁屏状态下重启手机（关键：不要解锁屏幕）\n5. 锁屏状态下执行提权命令\n6. 出现success后再亮屏解锁\n7. 加载KSU获取临时root\n\n支持：vivo X200 Pro、X200、iQOO 13等天玑9400机型。注意：操作有变砖风险。", methodId)
                }
                "dirtypipe_cve_2022_0847" -> {
                    // DirtyPipe 漏洞提权
                    val exploitExists = exec("ls /data/local/tmp/dirtypipe 2>/dev/null || ls /data/local/tmp/dirtypipe* 2>/dev/null || echo ''")
                    if (exploitExists.isNotBlank()) {
                        val exploitPath = exploitExists.trim().split("\n").firstOrNull() ?: "/data/local/tmp/dirtypipe"
                        val result = exec("chmod 755 $exploitPath && $exploitPath 2>&1")
                        val idResult = exec("id")
                        if (idResult.contains("uid=0")) {
                            RootResult(true, "DirtyPipe提权成功！\n\n$result\n\n$idResult", methodId)
                        } else {
                            RootResult(false, "DirtyPipe执行完成但未获得root。可能原因：\n1. 内核版本不在5.8~5.16.11范围内\n2. 安全补丁已修复此漏洞\n3. exploit不匹配当前设备\n\n输出：$result\n$idResult", methodId)
                        }
                    } else {
                        RootResult(false, "DirtyPipe (CVE-2022-0847) 临时Root\n\n操作步骤：\n1. 下载DirtyPipe exploit二进制（适配你的设备架构）\n2. 推送到 /data/local/tmp/dirtypipe\n3. 点击此方法自动执行\n\n注意：此漏洞在2022年3月安全补丁中已修复，仅内核5.8~5.16.11的老设备可用。", methodId)
                    }
                }
                "samsung_root_my_galaxy_s25" -> {
                    // 三星Root My Galaxy S25
                    val apkExists = exec("pm list packages 2>/dev/null | grep -i -E 'rootmygalaxy|root_my_galaxy'")
                    if (apkExists.isNotBlank()) {
                        RootResult(false, "检测到已安装Root My Galaxy应用。请手动打开应用，点击Security Check按钮（可能需要多次点击），按提示完成root。", methodId)
                    } else {
                        RootResult(false, "三星Root My Galaxy S25临时Root\n\n操作步骤：\n1. 从GitHub下载Root My Galaxy APK\n2. 安装并打开\n3. 点击Security Check按钮（可能需要多次点击才能生效）\n4. 按提示安装KernelSU\n5. 完成后获得root权限\n\n支持：Galaxy S25 Ultra（完全支持）、S25/S25+/S24系列（测试中）。注意：不触发Knox，不解锁BL，重启后root失效。Exynos机型不支持。", methodId)
                    }
                }
                "gbl_root_canoe" -> {
                    // GBL Root Canoe
                    RootResult(false, "GBL Root Canoe 通用Bootloader漏洞\n\n操作步骤（需电脑配合）：\n1. 从GitHub(stnt04/gbl_root_canoe)下载工具\n2. 手机进入fastboot模式\n3. 电脑执行漏洞利用脚本\n4. ABL加载嵌入式superfastboot BDS\n5. 实现Fake Locked状态\n6. 启动修改后的boot获取root\n\n支持：骁龙8 Gen5/8 Elite(Gen5)机型。注意：此方法较新，支持机型有限，操作有变砖风险，请谨慎。", methodId)
                }
                "mtk_generic_old" -> {
                    RootResult(false, "MTK通用老漏洞已在2020年3月安全更新中修复，当前系统大概率不受影响。\n\n如果您的设备是2020年前的老款MTK机型且未更新安全补丁，可以尝试从XDA下载对应exploit。", methodId)
                }
                else -> {
                    RootResult(false, "未知的 root 方法：$methodId", methodId)
                }
            }
        } catch (e: Exception) {
            RootResult(false, "执行 root 方法失败：${e.message}", methodId)
        }
    }

    // 检测临时 root 脚本是否存在，返回脚本路径
    actual fun findTempRootScript(): String? {
        return try {
            // 搜索目录列表（按优先级排序，优先搜用户下载目录，最后才搜app私有目录）
            val searchDirs = listOf(
                "/sdcard/Download",
                "/sdcard/download",
                "/storage/emulated/0/Download",
                "/data/local/tmp",
                "/data/local",
                appContext.getExternalFilesDir(null)?.absolutePath ?: "",
                appContext.filesDir.absolutePath
            ).filter { it.isNotBlank() }
            // 需要排除的基础初始化脚本（app自己生成的，不是用户下载的提权工具）
            val excludeFiles = listOf("temproot.sh", "temp_root.sh", "init.sh", "initializer.sh")
            // 匹配关键词（文件名包含这些词之一即认为是临时 root 工具）
            val keywords = listOf(
                "root", "temp", "misaka", "exploit", "提权",
                "temproot", "ksu", "kernelsu", "越狱", "jailbreak",
                "cve", "patch", "boot", "mi_", "mt6", "mt68",
                "6895", "6893", "6877", "8100", "8200", "8250",
                "9000", "9200", "9300", "9400", "9500", "dimensity",
                "天玑", "临时", "组织"
            )
            // 支持的文件扩展名（空字符串表示无扩展名也支持）
            val extensions = listOf(".sh", ".zip", ".bin", ".apk", ".img", ".tar", ".gz", "")

            // 递归搜索函数
            fun searchDir(dir: java.io.File, depth: Int): String? {
                if (depth > 3) return null  // 最多递归3层
                val files = dir.listFiles() ?: return null
                // 先检查当前目录的文件
                for (file in files) {
                    if (!file.isFile) continue
                    val fileName = file.name.lowercase()
                    // 排除基础初始化脚本
                    if (excludeFiles.any { fileName == it.lowercase() }) continue
                    val hasKeyword = keywords.any { fileName.contains(it.lowercase()) }
                    val hasValidExt = extensions.any { ext ->
                        if (ext.isEmpty()) !fileName.contains(".") else fileName.endsWith(ext)
                    }
                    if (hasKeyword && hasValidExt) {
                        return file.absolutePath
                    }
                }
                // 再递归搜索子目录
                for (file in files) {
                    if (file.isDirectory) {
                        val result = searchDir(file, depth + 1)
                        if (result != null) return result
                    }
                }
                return null
            }

            for (dirPath in searchDirs) {
                val dir = java.io.File(dirPath)
                if (!dir.exists() || !dir.isDirectory) continue
                val result = searchDir(dir, 0)
                if (result != null) return result
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // 生成临时 root 的终端执行命令
    actual fun buildTempRootTerminalCommand(scriptPath: String): String {
        return buildString {
            appendLine("echo '========================================'")
            appendLine("echo '  红米Note11T Pro 天玑8100 临时Root'")
            appendLine("echo '  酷安@御坂114515'")
            appendLine("echo '========================================'")
            appendLine("echo ''")
            appendLine("echo '[1/4] 检查脚本权限...'")
            appendLine("chmod 755 $scriptPath")
            appendLine("echo '[2/4] 正在执行提权脚本...'")
            appendLine("echo '      脚本可能会提示输入密码，请在下方输入'")
            appendLine("echo ''")
            appendLine("sh $scriptPath")
            appendLine("echo ''")
            appendLine("echo '[3/4] 检查 SELinux 状态...'")
            appendLine("getenforce")
            appendLine("echo '[4/4] 检查 root 权限...'")
            appendLine("id")
            appendLine("echo ''")
            appendLine("echo '========================================'")
            appendLine("echo '  如果显示 uid=0 或 SELinux=Permissive'")
            appendLine("echo '  说明提权成功！接下来执行 KSU late-load'")
            appendLine("echo '========================================'")
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
