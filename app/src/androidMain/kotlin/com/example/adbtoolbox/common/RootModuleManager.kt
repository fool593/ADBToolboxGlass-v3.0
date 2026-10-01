package com.example.adbtoolbox.common

import android.util.Base64
import java.io.File

// Root 模块管理器 actual 实现
// Root 模块（Magisk/KernelSU）安装在 /data/adb/modules 目录下。
//
// 重要：应用进程本身无权直接读写 /data/adb（属 shell:shell / 0700），
// 之前用 java.io.File 去 listFiles / createNewFile / readLines，结果必然是
// 「列表永远为空 + 启用/禁用/卸载静默失败（返回 false 但界面无任何反应）」。
// 因此这里所有读写都改走 ADBTools.execCommand（Shizuku → Dhizuku → su → shell 四级回退），
// 与 RootToolManager 的实现方式保持一致。
actual object RootModuleManager {

    private const val MODULES_DIR = "/data/adb/modules"

    private fun shq(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun run(cmd: String, timeout: Int = 20): CommandResult {
        return try {
            ADBTools.execCommand(cmd, timeout)
        } catch (e: Exception) {
            CommandResult("", "Command failed: ${e.message}", -1)
        }
    }

    /** 是否真的拿到了 Root（uid=0）。非 root 环境下操作 /data/adb 一定失败，需要提前告诉用户。 */
    private fun hasRootAccess(): Boolean {
        return run("id").output.contains("uid=0")
    }

    /**
     * 读取单个文本文件内容。
     * 用 base64 编码后一次性带回，避免多行内容在拼装 shell 命令时被截断。
     */
    private fun readTextFile(path: String): String {
        val result = run("cat ${shq(path)} 2>/dev/null | base64")
        val encoded = result.output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains(" ") }
            .joinToString("")
        if (encoded.isEmpty()) return ""
        return try {
            String(Base64.decode(encoded, Base64.DEFAULT), Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    /** 优先用 Root 执行，失败则退回普通 shell（execCommand 内部仍会自动尝试 Shizuku/Dhizuku）。 */
    private fun runCommand(cmd: String, timeout: Int = 60): CommandResult {
        val root = run("su -c ${shq(cmd)}", timeout)
        if (root.exitCode == 0 && !root.output.contains("Permission denied", ignoreCase = true)) {
            return root
        }
        val direct = run(cmd, timeout)
        if (direct.exitCode == 0) return direct
        return if (root.output.isNotBlank() || root.error.isNotBlank()) root else direct
    }

    actual fun installModule(zipFilePath: String): RootModuleInstallResult {
        return try {
            if (!hasRootAccess()) {
                return RootModuleInstallResult(false, "Root permission required")
            }
            val zipFile = File(zipFilePath)
            if (!zipFile.exists()) {
                return RootModuleInstallResult(false, "Zip file not found")
            }
            if (!zipFilePath.endsWith(".zip", ignoreCase = true)) {
                return RootModuleInstallResult(false, "Only .zip module files are supported")
            }

            // 优先走 Magisk / KernelSU 官方安装器（会正确处理模块目录、权限与状态文件）
            val magisk = runCommand("magisk --install-module ${shq(zipFilePath)}", timeout = 180)
            if (magisk.exitCode == 0) {
                return RootModuleInstallResult(true, "Module installed successfully via Magisk")
            }
            val ksud = runCommand("ksud module install ${shq(zipFilePath)}", timeout = 180)
            if (ksud.exitCode == 0) {
                return RootModuleInstallResult(true, "Module installed successfully via KernelSU")
            }

            // 兜底：手工解压到 /data/adb/modules/<id>
            val stage = "/data/local/tmp/rmm_${System.currentTimeMillis()}"
            runCommand("mkdir -p ${shq("$stage/unzip")}")
            val unzip = runCommand("unzip -o ${shq(zipFilePath)} -d ${shq("$stage/unzip")}")
            if (unzip.exitCode != 0) {
                runCommand("rm -rf ${shq(stage)}")
                val detail = listOf(magisk.error, ksud.error).firstOrNull { it.isNotBlank() } ?: ""
                return RootModuleInstallResult(
                    false,
                    "Install failed: no Magisk/KernelSU installer and unzip failed" +
                        (if (detail.isBlank()) "" else "\n$detail")
                )
            }

            val propText = readTextFile("$stage/unzip/module.prop")
            if (propText.isBlank()) {
                runCommand("rm -rf ${shq(stage)}")
                return RootModuleInstallResult(false, "Invalid module: module.prop not found")
            }
            val moduleId = propText.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("id=") }
                ?.substringAfter("=")?.trim()
                .orEmpty()
            if (moduleId.isBlank()) {
                runCommand("rm -rf ${shq(stage)}")
                return RootModuleInstallResult(false, "Invalid module: id not found in module.prop")
            }

            val targetDir = "$MODULES_DIR/$moduleId"
            runCommand("rm -rf ${shq(targetDir)}")
            val copy = runCommand("mkdir -p ${shq(targetDir)} && cp -rf ${shq("$stage/unzip/.")} ${shq("$targetDir/")}")
            runCommand("chmod -R 755 ${shq(targetDir)}")
            runCommand("chown -R 0:0 ${shq(targetDir)}")
            runCommand("rm -rf ${shq(stage)}")

            if (copy.exitCode != 0) {
                return RootModuleInstallResult(false, "Install failed: could not copy module files\n${copy.error}")
            }
            RootModuleInstallResult(true, "Module installed successfully: $moduleId (reboot to apply)")
        } catch (e: Exception) {
            RootModuleInstallResult(false, "Installation failed: ${e.message}")
        }
    }

    actual fun getInstalledModules(): List<RootModuleData> {
        return try {
            // 用 shell 列出 /data/adb/modules（应用进程直接 File.listFiles 会因权限被拒 -> 以前永远是空列表）
            val ls = run("ls -1 ${shq(MODULES_DIR)} 2>/dev/null")
            if (ls.exitCode != 0 || ls.output.isBlank()) return emptyList()

            val ids = ls.output.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it.matches(Regex("[A-Za-z0-9._+\\-]+")) }
                .toList()
            if (ids.isEmpty()) return emptyList()

            // 反复用 `cat` 会把 module.prop 的多行内容混在一起无法解析，
            // 因此只取「首行值」：name/version/author/description 各一次，另外带出 disable / action.sh 状态标记。
            val script = StringBuilder()
            ids.forEach { id ->
                val dir = "$MODULES_DIR/$id"
                script.append("echo '@@@ID|$id'; ")
                listOf("name", "version", "versionCode", "author", "description").forEach { key ->
                    script.append(
                        "echo '@@@${key.uppercase()}|'\"$(sed -n 's/^$key=//p' ${shq("$dir/module.prop")} 2>/dev/null | head -n 1)\"; "
                    )
                }
                script.append("echo '@@@DISABLE|'\"$([ -f ${shq("$dir/disable")} ] && echo 1 || echo 0)\"; ")
                script.append("echo '@@@ACTION|'\"$([ -f ${shq("$dir/action.sh")} ] && echo 1 || echo 0)\"; ")
            }

            val dump = runCommand(script.toString(), timeout = 30)

            val modules = mutableListOf<RootModuleData>()
            var id = ""
            val props = mutableMapOf<String, String>()
            var disabled = false
            var hasAction = false

            fun flush() {
                if (id.isEmpty()) return
                modules.add(
                    RootModuleData(
                        id = id,
                        name = props["NAME"]?.ifBlank { id } ?: id,
                        version = props["VERSION"]?.ifBlank { "1.0" } ?: "1.0",
                        versionCode = props["VERSIONCODE"]?.trim()?.toIntOrNull() ?: 1,
                        author = props["AUTHOR"]?.ifBlank { "Unknown" } ?: "Unknown",
                        description = props["DESCRIPTION"].orEmpty(),
                        isEnabled = !disabled,
                        isInstalled = true,
                        moduleDir = "$MODULES_DIR/$id",
                        hasAction = hasAction,
                        updateTime = 0L,
                        size = ""
                    )
                )
            }

            dump.output.lines().forEach { raw ->
                val line = raw.trim()
                if (!line.startsWith("@@@")) return@forEach
                val directive = line.removePrefix("@@@")
                val key = directive.substringBefore("|")
                val value = directive.substringAfter("|", "").trim()
                when (key) {
                    "ID" -> {
                        flush()
                        id = value
                        props.clear()
                        disabled = false
                        hasAction = false
                    }
                    "DISABLE" -> disabled = value == "1"
                    "ACTION" -> hasAction = value == "1"
                    else -> if (id.isNotEmpty()) props[key] = value
                }
            }
            flush()
            modules.sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    actual fun enableModule(moduleId: String): Boolean {
        return try {
            val target = "$MODULES_DIR/$moduleId/disable"
            val result = runCommand("rm -f ${shq(target)}; [ ! -f ${shq(target)} ] && echo RMM_OK")
            result.output.contains("RMM_OK")
        } catch (e: Exception) {
            false
        }
    }

    actual fun disableModule(moduleId: String): Boolean {
        return try {
            val target = "$MODULES_DIR/$moduleId/disable"
            val result = runCommand("touch ${shq(target)}; [ -f ${shq(target)} ] && echo RMM_OK")
            result.output.contains("RMM_OK")
        } catch (e: Exception) {
            false
        }
    }

    actual fun uninstallModule(moduleId: String): Boolean {
        return try {
            // Magisk/KernelSU 约定：模块目录下存在 remove 文件表示下次重启时删除
            val marker = "$MODULES_DIR/$moduleId/remove"
            val result = runCommand("touch ${shq(marker)}; [ -f ${shq(marker)} ] && echo RMM_OK")
            result.output.contains("RMM_OK")
        } catch (e: Exception) {
            false
        }
    }

    actual fun runAction(moduleId: String): String {
        return try {
            val dir = "$MODULES_DIR/$moduleId"
            val guard = runCommand(
                "if [ ! -f ${shq("$dir/action.sh")} ]; then echo RMM_NO_ACTION; " +
                    "elif [ -f ${shq("$dir/disable")} ]; then echo RMM_DISABLED; " +
                    "else echo RMM_RUN_OK; fi"
            )
            val signal = guard.output
            when {
                signal.contains("RMM_NO_ACTION") -> "Error: This module has no action.sh"
                signal.contains("RMM_DISABLED") -> "Error: Module is disabled, please enable it first"
                !signal.contains("RMM_RUN_OK") -> buildString {
                    appendLine("Error: Operation failed (no permission to read $dir)")
                    if (guard.error.isNotBlank()) appendLine(guard.error)
                }
                else -> {
                    val result = runCommand(
                        "cd ${shq(dir)} && MODDIR=${shq(dir)} sh action.sh",
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
                }
            }
        } catch (e: Exception) {
            "Execution failed: ${e.message}"
        }
    }
}
