package com.example.adbtoolbox.common

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.zip.ZipInputStream

// 插件管理器：管理 AxManager 风格插件的安装、卸载、启用、禁用、脚本执行
actual object PluginManager {

    // 插件根目录（应用私有目录，不需要额外权限）
    private fun getPluginsDir(): String {
        return "${appContext.filesDir.absolutePath}/plugins"
    }

    // 确保插件目录存在
    private fun ensurePluginsDir() {
        val dir = File(getPluginsDir())
        if (!dir.exists()) dir.mkdirs()
    }

    // Java 原生 ZipInputStream 解压，不需要任何权限
    private fun unzipFile(zipFile: File, destDir: File): Boolean {
        return try {
            if (!destDir.exists()) destDir.mkdirs()
            ZipInputStream(FileInputStream(zipFile)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val entryFile = File(destDir, entry.name)
                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    // 安装插件（zip 文件）
    actual fun installPlugin(zipFilePath: String): PluginInstallResult {
        return try {
            val zipFile = File(zipFilePath)
            if (!zipFile.exists()) {
                return PluginInstallResult(false, "File not found: $zipFilePath")
            }
            if (!zipFile.name.endsWith(".zip", ignoreCase = true)) {
                return PluginInstallResult(false, "Only zip format plugin packages are supported")
            }

            ensurePluginsDir()

            // 创建临时解压目录
            val tempDir = File(getPluginsDir(), "temp_${System.currentTimeMillis()}")
            tempDir.mkdirs()

            // 用 Java 原生 ZipInputStream 解压，不需要任何权限
            val unzipSuccess = unzipFile(zipFile, tempDir)
            if (!unzipSuccess) {
                tempDir.deleteRecursively()
                return PluginInstallResult(false, "Unzip failed: file may be corrupted or not a valid zip package")
            }

            // 查找 module.prop（可能在根目录或子目录）
            val moduleProp = findModuleProp(tempDir)
            if (moduleProp == null) {
                tempDir.deleteRecursively()
                return PluginInstallResult(false, "module.prop not found, this is not a valid AxManager plugin")
            }

            // 解析 module.prop
            val props = parseModuleProp(moduleProp)
            val pluginId = props["id"]
            if (pluginId.isNullOrBlank()) {
                tempDir.deleteRecursively()
                return PluginInstallResult(false, "Missing id field in module.prop")
            }

            // 确定插件源目录（module.prop 所在目录）
            val pluginSourceDir = moduleProp.parentFile

            // 如果已存在同名插件，先备份再删除
            val targetDir = File(getPluginsDir(), pluginId)
            if (targetDir.exists()) {
                val backupDir = File(getPluginsDir(), "${pluginId}_backup_${System.currentTimeMillis()}")
                targetDir.renameTo(backupDir)
                targetDir.deleteRecursively()
                backupDir.deleteRecursively()
            }

            // 移动插件到目标目录
            pluginSourceDir?.copyRecursively(targetDir, overwrite = true)
            tempDir.deleteRecursively()

            // 执行 customize.sh（如果存在）
            val customizeSh = File(targetDir, "customize.sh")
            if (customizeSh.exists()) {
                ADBTools.execCommand(
                    "cd \"${targetDir.absolutePath}\" && MODPATH=\"${targetDir.absolutePath}\" TMPDIR=\"${targetDir.absolutePath}\" sh customize.sh",
                    timeout = 120
                )
            }

            // 设置脚本可执行权限
            listOf("post-fs-data.sh", "service.sh", "action.sh", "uninstall.sh").forEach { script ->
                val scriptFile = File(targetDir, script)
                if (scriptFile.exists()) {
                    scriptFile.setExecutable(true, false)
                }
            }

            // 检查是否有 WebUI
            val hasWebUI = File(targetDir, "webroot/index.html").exists() ||
                    File(targetDir, "webui/index.html").exists()

            // 检查是否有 action.sh
            val hasAction = File(targetDir, "action.sh").exists()

            val plugin = PluginData(
                id = pluginId,
                name = props["name"] ?: pluginId,
                version = props["version"] ?: "1.0",
                versionCode = props["versionCode"]?.toIntOrNull() ?: 1,
                author = props["author"] ?: "Unknown",
                description = props["description"] ?: "",
                axeronPlugin = props["axeronPlugin"]?.toIntOrNull() ?: 0,
                isEnabled = !File(targetDir, "disable").exists(),
                isInstalled = true,
                pluginDir = targetDir.absolutePath,
                hasWebUI = hasWebUI,
                hasAction = hasAction,
                updateTime = System.currentTimeMillis()
            )

            PluginInstallResult(true, "Plugin installed successfully: ${plugin.name}", plugin)
        } catch (e: Exception) {
            PluginInstallResult(false, "Installation failed: ${e.message}")
        }
    }

    // 查找 module.prop 文件
    private fun findModuleProp(dir: File): File? {
        val direct = File(dir, "module.prop")
        if (direct.exists()) return direct

        // 递归查找子目录
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val found = findModuleProp(file)
                if (found != null) return found
            }
        }
        return null
    }

    // 解析 module.prop
    private fun parseModuleProp(file: File): Map<String, String> {
        val props = mutableMapOf<String, String>()
        try {
            file.forEachLine { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                    val key = trimmed.substringBefore("=").trim()
                    val value = trimmed.substringAfter("=").trim()
                    props[key] = value
                }
            }
        } catch (e: Exception) {}
        return props
    }

    // 格式化目录大小
    private fun formatDirSize(dir: File): String {
        return try {
            var size = 0L
            dir.walkTopDown().forEach { if (it.isFile) size += it.length() }
            when {
                size < 1024 -> "$size B"
                size < 1024 * 1024 -> String.format("%.2f KB", size / 1024.0)
                size < 1024 * 1024 * 1024 -> String.format("%.2f MB", size / (1024.0 * 1024))
                else -> String.format("%.2f GB", size / (1024.0 * 1024 * 1024))
            }
        } catch (e: Exception) {
            ""
        }
    }

    // 列出所有已安装插件
    actual fun getInstalledPlugins(): List<PluginData> {
        return try {
            ensurePluginsDir()
            val pluginsDir = File(getPluginsDir())
            pluginsDir.listFiles()?.filter { it.isDirectory && !it.name.startsWith("temp_") && !it.name.endsWith("_backup_") }
                ?.mapNotNull { dir ->
                    val moduleProp = File(dir, "module.prop")
                    if (!moduleProp.exists()) return@mapNotNull null
                    val props = parseModuleProp(moduleProp)
                    val hasWebUI = File(dir, "webroot/index.html").exists() ||
                            File(dir, "webui/index.html").exists()
                    val hasAction = File(dir, "action.sh").exists()
                    PluginData(
                        id = props["id"] ?: dir.name,
                        name = props["name"] ?: dir.name,
                        version = props["version"] ?: "1.0",
                        versionCode = props["versionCode"]?.toIntOrNull() ?: 1,
                        author = props["author"] ?: "Unknown",
                        description = props["description"] ?: "",
                        axeronPlugin = props["axeronPlugin"]?.toIntOrNull() ?: 0,
                        isEnabled = !File(dir, "disable").exists(),
                        isInstalled = true,
                        pluginDir = dir.absolutePath,
                        hasWebUI = hasWebUI,
                        hasAction = hasAction,
                        updateTime = dir.lastModified(),
                        size = formatDirSize(dir)
                    )
                }?.sortedBy { it.name.lowercase() } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 启用插件
    actual fun enablePlugin(pluginId: String): Boolean {
        return try {
            val disableFile = File("${getPluginsDir()}/$pluginId/disable")
            if (disableFile.exists()) disableFile.delete()
            true
        } catch (e: Exception) {
            false
        }
    }

    // 禁用插件
    actual fun disablePlugin(pluginId: String): Boolean {
        return try {
            val disableFile = File("${getPluginsDir()}/$pluginId/disable")
            if (!disableFile.exists()) disableFile.createNewFile()
            true
        } catch (e: Exception) {
            false
        }
    }

    // 卸载插件
    actual fun uninstallPlugin(pluginId: String): Boolean {
        return try {
            val pluginDir = File("${getPluginsDir()}/$pluginId")
            // 执行 uninstall.sh
            val uninstallSh = File(pluginDir, "uninstall.sh")
            if (uninstallSh.exists()) {
                ADBTools.execCommand(
                    "cd \"${pluginDir.absolutePath}\" && sh uninstall.sh",
                    timeout = 60
                )
            }
            // 创建 remove 标记（模拟 AxManager 的下次重启移除）
            val removeFile = File(pluginDir, "remove")
            removeFile.createNewFile()
            // 直接删除
            pluginDir.deleteRecursively()
            true
        } catch (e: Exception) {
            false
        }
    }

    // 执行插件的 action.sh
    actual fun runAction(pluginId: String): String {
        return try {
            val pluginDir = File("${getPluginsDir()}/$pluginId")
            val actionSh = File(pluginDir, "action.sh")
            if (!actionSh.exists()) {
                return "Error: This plugin has no action.sh"
            }
            if (File(pluginDir, "disable").exists()) {
                return "Error: Plugin is disabled, please enable it first"
            }
            val result = ADBTools.execCommand(
                "cd \"${pluginDir.absolutePath}\" && MODDIR=\"${pluginDir.absolutePath}\" sh action.sh",
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

    // 执行所有已启用插件的开机脚本（由 BootReceiver 调用）
    fun runBootScripts(context: Context) {
        try {
            val plugins = getInstalledPlugins().filter { it.isEnabled }
            plugins.forEach { plugin ->
                val pluginDir = File(plugin.pluginDir)
                // 执行 post-fs-data.sh
                val postFsData = File(pluginDir, "post-fs-data.sh")
                if (postFsData.exists()) {
                    ADBTools.execCommand(
                        "cd \"${pluginDir.absolutePath}\" && MODDIR=\"${pluginDir.absolutePath}\" sh post-fs-data.sh",
                        timeout = 60
                    )
                }
                // 执行 service.sh（后台执行）
                val serviceSh = File(pluginDir, "service.sh")
                if (serviceSh.exists()) {
                    Thread {
                        ADBTools.execCommand(
                            "cd \"${pluginDir.absolutePath}\" && MODDIR=\"${pluginDir.absolutePath}\" sh service.sh",
                            timeout = 300
                        )
                    }.start()
                }
                // 加载 system.prop
                val systemProp = File(pluginDir, "system.prop")
                if (systemProp.exists()) {
                    systemProp.forEachLine { line ->
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                            val key = trimmed.substringBefore("=").trim()
                            val value = trimmed.substringAfter("=").trim()
                            ADBTools.execCommand("setprop $key \"$value\"")
                        }
                    }
                }
            }
        } catch (e: Exception) {}
    }

    // 获取插件 WebUI 的 index.html 路径
    actual fun getWebUIPath(pluginId: String): String? {
        val pluginDir = File("${getPluginsDir()}/$pluginId")
        val webroot = File(pluginDir, "webroot/index.html")
        if (webroot.exists()) return webroot.absolutePath
        val webui = File(pluginDir, "webui/index.html")
        if (webui.exists()) return webui.absolutePath
        return null
    }
}
