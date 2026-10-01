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
     * 界面用：真实提权检测（不猜 su 路径，直接看 `id` 是否 uid=0）。
     *
     * 为什么不让界面直接用 ADBTools.isRooted()：它靠 su 文件路径 / `which su` 判断，
     * "设备装了 Magisk 但本应用没被授权"时会返回 true，于是界面点亮所有按钮，
     * 用户点下去每个操作都返回 false 却没有任何解释。
     */
    actual fun canUseRoot(): Boolean {
        return try {
            hasRootAccess()
        } catch (e: Exception) {
            false
        }
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

    /**
     * 处理器的错误标识统一成 `E_XXX` 前缀的稳定错误码（不写死任何语言），
     * 界面再用 AppStrings 翻译成当前语言；`detail` 原样附带在错误码之后。
     */
    private fun err(code: String, detail: String = ""): String =
        if (detail.isBlank()) code else "$code\n$detail"

    actual fun installModule(zipFilePath: String): RootModuleInstallResult {
        return try {
            if (!hasRootAccess()) {
                return RootModuleInstallResult(false, err("E_ROOT_REQUIRED"))
            }
            val zipFile = File(zipFilePath)
            if (!zipFile.exists()) {
                return RootModuleInstallResult(false, err("E_FILE_NOT_FOUND", zipFilePath))
            }
            if (!zipFilePath.endsWith(".zip", ignoreCase = true)) {
                return RootModuleInstallResult(false, err("E_NOT_ZIP", zipFilePath))
            }

            // 优先走 Magisk / KernelSU 官方安装器（会正确处理模块目录、权限与状态文件）
            val magisk = runCommand("magisk --install-module ${shq(zipFilePath)}", timeout = 180)
            if (magisk.exitCode == 0) {
                return RootModuleInstallResult(true, err("E_OK_MAGISK"))
            }
            val ksud = runCommand("ksud module install ${shq(zipFilePath)}", timeout = 180)
            if (ksud.exitCode == 0) {
                return RootModuleInstallResult(true, err("E_OK_KSU"))
            }

            // 兜底：手工解压到 /data/adb/modules/<id>
            val stage = "/data/local/tmp/rmm_${System.currentTimeMillis()}"
            runCommand("mkdir -p ${shq("$stage/unzip")}")
            val unzip = runCommand("unzip -o ${shq(zipFilePath)} -d ${shq("$stage/unzip")}")
            if (unzip.exitCode != 0) {
                runCommand("rm -rf ${shq(stage)}")
                val detail = listOf(magisk.error, ksud.error, unzip.error)
                    .firstOrNull { it.isNotBlank() }
                    ?.trim()
                    .orEmpty()
                return RootModuleInstallResult(
                    false,
                    err("E_NO_INSTALLER_AND_UNZIP_FAILED", detail)
                )
            }

            val propText = readTextFile("$stage/unzip/module.prop")
            if (propText.isBlank()) {
                runCommand("rm -rf ${shq(stage)}")
                return RootModuleInstallResult(false, err("E_NO_MODULE_PROP"))
            }
            val moduleId = propText.lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("id=") }
                ?.substringAfter("=")?.trim()
                .orEmpty()
            if (moduleId.isBlank()) {
                runCommand("rm -rf ${shq(stage)}")
                return RootModuleInstallResult(false, err("E_NO_MODULE_ID"))
            }

            val targetDir = "$MODULES_DIR/$moduleId"
            runCommand("rm -rf ${shq(targetDir)}")
            val copy = runCommand("mkdir -p ${shq(targetDir)} && cp -rf ${shq("$stage/unzip/.")} ${shq("$targetDir/")}")
            runCommand("chmod -R 755 ${shq(targetDir)}")
            runCommand("chown -R 0:0 ${shq(targetDir)}")
            runCommand("rm -rf ${shq(stage)}")

            if (copy.exitCode != 0) {
                return RootModuleInstallResult(
                    false,
                    err("E_COPY_FAILED", copy.error.trim())
                )
            }
            // Magisk/KernelSU 都是重启后才会加载新模块，文案必须如实说明
            RootModuleInstallResult(true, err("E_OK_MANUAL", moduleId))
        } catch (e: Exception) {
            RootModuleInstallResult(false, err("E_EXCEPTION", e.message.orEmpty()))
        }
    }

    /**
     * 人类可读的大小。原实现固定给 `size = ""`，界面因此永远显示不出模块大小。
     * 这里在 shell 端用 `du -sk` 取 KB，格式化放在 Kotlin 侧，单位与当前语言无关。
     */
    private fun formatKb(kb: Long): String = when {
        kb <= 0L -> ""
        kb < 1024L -> "$kb KB"
        kb < 1024L * 1024L -> "${kb / 1024L}.${(kb % 1024L) * 10L / 1024L} MB"
        else -> "${kb / (1024L * 1024L)}.${(kb % (1024L * 1024L)) * 10L / (1024L * 1024L)} GB"
    }

    /**
     * 解析 `stat` 输出的秒级时间戳。
     *
     * 兼容两种格式：GNU/coreutils `stat -c %Y` 直接给 epoch 秒；
     * 部分 toybox/busybox 不认 `-c`，会打印 "Modify: 2024-05-01 12:00:00.000000000 +0800"，
     * 这时取 "Modify:" 后面的 "yyyy-MM-dd HH:mm:ss" 手工换算。取不到返回 0。
     */
    private fun parseStatOutput(text: String): Long {
        val lines = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        lines.forEach { line ->
            val tail = line.substringAfter("Modify:", "").trim()
            if (tail.isNotEmpty()) {
                val stamp = tail.substringBefore(".").trim()
                if (stamp.length >= 19) {
                    try {
                        val date = stamp.substring(0, 10)
                        val time = stamp.substring(11, 19)
                        val parsed = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                            .parse("$date $time")
                        if (parsed != null) return parsed.time
                    } catch (_: Exception) {
                    }
                }
            }
        }
        // 纯 epoch 秒（coreutils）
        lines.forEach { line ->
            val seconds = line.trim().toLongOrNull()
            if (seconds != null && seconds > 0L) return seconds * 1000L
        }
        return 0L
    }

    actual fun getInstalledModules(): List<RootModuleData> {
        return try {
            RootModuleData.setLastError(RootModuleError.None)
            // 用 shell 列出 /data/adb/modules（应用进程直接 File.listFiles 会因权限被拒 -> 以前永远是空列表）
            val ls = run("ls -1 ${shq(MODULES_DIR)} 2>/dev/null")
            if (ls.exitCode != 0 || ls.output.isBlank()) {
                // 区分「真的没有模块」与「读不到目录」：把失败原因记录下来交给界面显示，
                // 否则界面只能看到空列表，会给用户"设备上没有任何模块"的错误结论。
                val probe = run(
                    "if [ -d ${shq(MODULES_DIR)} ]; then echo RMM_DIR_OK; else echo RMM_DIR_MISSING; fi"
                )
                val probeText = probe.output
                when {
                    probeText.contains("RMM_DIR_OK") -> RootModuleData.setLastError(RootModuleError.None)
                    probeText.contains("RMM_DIR_MISSING") && probe.exitCode == 0 -> RootModuleData.setLastError(
                        RootModuleError.PermissionDenied,
                        probe.error.trim()
                    )
                    else -> RootModuleData.setLastError(
                        RootModuleError.CommandFailed,
                        listOf(probe.error, probe.output).firstOrNull { it.isNotBlank() }?.trim().orEmpty()
                    )
                }
                return emptyList()
            }

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
                // 模块体积与更新时间：原实现恒为空串/0L，界面显示不出这两个字段
                script.append("echo '@@@SIZE|'\"$(du -sk ${shq(dir)} 2>/dev/null | awk '{print ${'$'}1}' | head -n 1)\"; ")
                script.append("echo '@@@MTIME|'\"$(stat -c %Y ${shq("$dir/module.prop")} 2>/dev/null || stat ${shq("$dir/module.prop")} 2>/dev/null)\"; ")
            }

            val dump = runCommand(script.toString(), timeout = 30)

            val modules = mutableListOf<RootModuleData>()
            var id = ""
            val props = mutableMapOf<String, String>()
            var disabled = false
            var hasAction = false
            var sizeKb = 0L
            var mtimeMs = 0L

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
                        updateTime = mtimeMs,
                        size = formatKb(sizeKb)
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
                        sizeKb = 0L
                        mtimeMs = 0L
                    }
                    "DISABLE" -> disabled = value == "1"
                    "ACTION" -> hasAction = value == "1"
                    "SIZE" -> sizeKb = value.toLongOrNull() ?: 0L
                    "MTIME" -> mtimeMs = parseStatOutput(value)
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
                signal.contains("RMM_NO_ACTION") -> err("E_NO_ACTION")
                signal.contains("RMM_DISABLED") -> err("E_ACTION_DISABLED")
                !signal.contains("RMM_RUN_OK") -> err(
                    "E_ACTION_GUARD_FAILED",
                    listOf("$dir", guard.error.trim()).filter { it.isNotBlank() }.joinToString("\n")
                )
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
            err("E_ACTION_EXCEPTION", e.message.orEmpty())
        }
    }
}
