package com.example.adbtoolbox.common

import java.io.File

// Root 模块管理器 actual 实现
// Root 模块（Magisk/KernelSU）通常安装在 /data/adb/modules 目录下
actual object RootModuleManager {

    private fun getModulesDir(): String {
        // Magisk 模块目录
        return "/data/adb/modules"
    }

    actual fun installModule(zipFilePath: String): RootModuleInstallResult {
        return try {
            // 检查是否有 Root 权限
            val rootCheck = ADBTools.execCommand("id")
            if (!rootCheck.output.contains("uid=0")) {
                return RootModuleInstallResult(false, "Root permission required")
            }
            // 检查 zip 文件是否存在
            val zipFile = File(zipFilePath)
            if (!zipFile.exists()) {
                return RootModuleInstallResult(false, "Zip file not found")
            }
            // 解压到临时目录
            val tempDir = File("${appContext.cacheDir}/root_module_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            ADBTools.execCommand("unzip -o \"$zipFilePath\" -d \"${tempDir.absolutePath}\"")
            // 检查 module.prop
            val moduleProp = File(tempDir, "module.prop")
            if (!moduleProp.exists()) {
                tempDir.deleteRecursively()
                return RootModuleInstallResult(false, "Invalid module: module.prop not found")
            }
            // 读取模块 ID
            var moduleId = ""
            moduleProp.readLines().forEach { line ->
                if (line.startsWith("id=")) {
                    moduleId = line.substringAfter("=").trim()
                }
            }
            if (moduleId.isEmpty()) {
                tempDir.deleteRecursively()
                return RootModuleInstallResult(false, "Invalid module: id not found in module.prop")
            }
            // 复制到模块目录
            val targetDir = File("${getModulesDir()}/$moduleId")
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            ADBTools.execCommand("cp -r \"${tempDir.absolutePath}\" \"${targetDir.absolutePath}\"")
            // 设置权限
            ADBTools.execCommand("chmod -R 755 \"${targetDir.absolutePath}\"")
            // 清理临时目录
            tempDir.deleteRecursively()
            RootModuleInstallResult(true, "Module installed successfully: $moduleId")
        } catch (e: Exception) {
            RootModuleInstallResult(false, "Installation failed: ${e.message}")
        }
    }

    actual fun getInstalledModules(): List<RootModuleData> {
        return try {
            val modulesDir = File(getModulesDir())
            if (!modulesDir.exists()) return emptyList()
            modulesDir.listFiles()?.filter { it.isDirectory }?.mapNotNull { dir ->
                val moduleProp = File(dir, "module.prop")
                if (!moduleProp.exists()) return@mapNotNull null
                var name = dir.name
                var version = ""
                var author = ""
                var description = ""
                moduleProp.readLines().forEach { line ->
                    when {
                        line.startsWith("name=") -> name = line.substringAfter("=").trim()
                        line.startsWith("version=") -> version = line.substringAfter("=").trim()
                        line.startsWith("author=") -> author = line.substringAfter("=").trim()
                        line.startsWith("description=") -> description = line.substringAfter("=").trim()
                    }
                }
                val isEnabled = !File(dir, "disable").exists()
                val hasAction = File(dir, "action.sh").exists()
                val size = getDirSize(dir)
                RootModuleData(
                    id = dir.name,
                    name = name,
                    version = version,
                    author = author,
                    description = description,
                    isEnabled = isEnabled,
                    moduleDir = dir.absolutePath,
                    hasAction = hasAction,
                    updateTime = dir.lastModified(),
                    size = size
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual fun enableModule(moduleId: String): Boolean {
        return try {
            val disableFile = File("${getModulesDir()}/$moduleId/disable")
            if (disableFile.exists()) {
                disableFile.delete()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    actual fun disableModule(moduleId: String): Boolean {
        return try {
            val disableFile = File("${getModulesDir()}/$moduleId/disable")
            disableFile.createNewFile()
            true
        } catch (e: Exception) {
            false
        }
    }

    actual fun uninstallModule(moduleId: String): Boolean {
        return try {
            val moduleDir = File("${getModulesDir()}/$moduleId")
            if (moduleDir.exists()) {
                // 创建 remove 文件，Magisk 会在重启时删除模块
                File(moduleDir, "remove").createNewFile()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    actual fun runAction(moduleId: String): String {
        return try {
            val moduleDir = File("${getModulesDir()}/$moduleId")
            val actionSh = File(moduleDir, "action.sh")
            if (!actionSh.exists()) {
                return "Error: This module has no action.sh"
            }
            if (File(moduleDir, "disable").exists()) {
                return "Error: Module is disabled, please enable it first"
            }
            val result = ADBTools.execCommand(
                "cd \"${moduleDir.absolutePath}\" && MODDIR=\"${moduleDir.absolutePath}\" sh action.sh",
                timeout = 120
            )
            buildString {
                appendLine("Exit code: ${result.exitCode}")
                if (result.output.isNotBlank()) {
                    appendLine("Output:")
                    appendLine(result.output)
                }
                if (result.error.isNotBlank()) {
                    appendLine("Error:")
                    appendLine(result.error)
                }
            }
        } catch (e: Exception) {
            "Execution failed: ${e.message}"
        }
    }

    private fun getDirSize(dir: File): String {
        return try {
            var size = 0L
            dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
            when {
                size > 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024.0))
                size > 1024 -> String.format("%.1f KB", size / 1024.0)
                else -> "$size B"
            }
        } catch (e: Exception) {
            "0 B"
        }
    }
}
