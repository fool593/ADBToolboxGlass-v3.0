package com.example.adbtoolbox.common

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

// Root 模块管理器 actual 实现
// Root 模块（Magisk/KernelSU/APatch）安装在 /data/adb/modules 目录下。
//
// 重要：应用进程本身无权直接读写 /data/adb（属 root:root / 0700），
// 之前用 java.io.File 去 listFiles / createNewFile / readLines，结果必然是
// 「列表永远为空 + 启用/禁用/卸载静默失败（返回 false 但界面无任何反应）」。
// 因此这里所有读写都改走 shell（Shizuku → Dhizuku → su → shell 四级回退），
// 与 RootToolManager 的实现方式保持一致。
//
// 安装路径的硬约束：
// 1. 不再依赖设备端 unzip / busybox：zip 一律在本机用 java.util.zip 解析与解压，
//    再以 Root 身份把解压结果复制到 /data/adb/modules/<id>；
// 2. module.prop 允许在 zip 内任意一层目录（有的包多套一层文件夹），并优先认带
//    安装器落点标记的那一份（Magisk 的 META-INF/com/google/android/update-binary、
//    KernelSU / APatch 的 customize.sh）；
// 3. 没 root / su 拒绝授权 / /data/adb 不存在 / 只读 / 写不进去，分别给不同错误码，
//    绝不统一成"安装失败"；
// 4. 安装完成必须回读 /data/adb/modules 校验，没出现就如实报失败。
actual object RootModuleManager {

    private const val MODULES_DIR = "/data/adb/modules"

    /** 模块 id 必须能直接当目录名用：不允许路径分隔符与 `..`。 */
    private val ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$")

    private fun shq(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    private fun run(cmd: String, timeout: Int = 20): CommandResult {
        return try {
            ADBTools.execCommand(cmd, timeout)
        } catch (e: Exception) {
            CommandResult("", "Command failed: ${e.message}", -1)
        }
    }

    /**
     * 只以 Root 身份执行（与 ADBTools.execCommand 的区别：这里不经过 Shizuku/普通 shell）。
     *
     * 为什么必须单独一条通道：Shizuku 是 shell uid，既找不到 magisk 命令也写不了 /data/adb，
     * 走 execCommand 会把"shell 做不到"误判成"设备上不存在 Magisk"，从而跳过官方安装器。
     * stderr 在 shell 侧合并进 stdout，避免两个管道互等造成死锁。
     */
    private fun rootExec(cmd: String, timeout: Int = 60): CommandResult {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", "$cmd 2>&1"))
            val finished = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return CommandResult("", "su timeout after ${timeout}s", -1)
            }
            val output = try {
                process.inputStream.bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                ""
            }
            CommandResult(output, "", process.exitValue())
        } catch (e: Exception) {
            CommandResult("", "su unavailable: ${e.message ?: e.javaClass.simpleName}", -999)
        } finally {
            try {
                process?.destroy()
            } catch (e: Exception) {
            }
        }
    }

    /** Root 探测结果。用来区分「没有 su」「su 拒绝授权」「真的拿到 uid=0」。 */
    private enum class RootProbe { OK, DENIED, NO_SU, UNKNOWN }

    /** 探测结果 + 真实回显（错误文案里要带上它，不能只说"失败"）。 */
    private class RootStatus(val state: RootProbe, val detail: String)

    private fun probeRoot(): RootStatus {
        val id = rootExec("id", timeout = 20)
        val text = id.output.trim()
        if (id.exitCode == 0 && text.contains("uid=0")) {
            return RootStatus(RootProbe.OK, text)
        }
        val lower = text.lowercase()
        val denied = lower.contains("permission denied") || lower.contains("denied") ||
            lower.contains("not allowed") || lower.contains("not granted") ||
            lower.contains("user rejected") || lower.contains("no permission")
        val detail = text.ifBlank { "su exited with code ${id.exitCode}" }
        if (denied) return RootStatus(RootProbe.DENIED, detail)
        if (id.exitCode == -999) return RootStatus(RootProbe.NO_SU, detail)
        if (lower.contains("not found") || lower.contains("no such file")) return RootStatus(RootProbe.NO_SU, detail)
        return RootStatus(RootProbe.UNKNOWN, detail)
    }

    /** 是否真的拿到了 Root（uid=0）。非 root 环境下操作 /data/adb 一定失败，需要提前告诉用户。 */
    private fun hasRootAccess(): Boolean = probeRoot().state == RootProbe.OK

    /**
     * 界面用：真实提权检测（不猜 su 路径，直接看 `su -c id` 是否 uid=0）。
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

    /** 取命令的真实失败原因：stderr + stdout，去掉内部用的 RMM_ 标记行。 */
    private fun detailOf(result: CommandResult): String {
        return (result.error + "\n" + result.output).lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("RMM_") }
            .joinToString("\n")
    }

    // ==================== 本机 zip 解析（不依赖设备端 unzip） ====================

    private class ModuleInstallFailure(val code: String, val detail: String = "") : Exception(code)

    /** zip 内的候选落点（module.prop 所在目录）。 */
    private class PropCandidate(
        val entry: ZipEntry,
        val prefix: String,
        val magiskMarker: Boolean,
        val customizeSh: Boolean
    ) {
        val score: Int get() = (if (magiskMarker) 2 else 0) + (if (customizeSh) 1 else 0)
    }

    /** 本机解析出的模块信息。 */
    private class ModuleArchive(
        val id: String,
        val rootPrefix: String,
        val modulePropEntry: String,
        val magiskMarker: Boolean,
        val customizeSh: Boolean
    )

    private fun isUnsafeEntryName(rawName: String): Boolean {
        val name = rawName.replace('\\', '/')
        if (name.isBlank() || name.startsWith("/")) return true
        if (name.length >= 2 && name[1] == ':') return true
        return name.split('/').any { it == ".." }
    }

    private fun parsePropText(text: String): Map<String, String> {
        val props = LinkedHashMap<String, String>()
        text.removePrefix("\uFEFF").lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) return@forEach
            val key = line.substringBefore("=").trim()
            val value = line.substringAfter("=").trim()
            if (key.isNotEmpty()) props[key] = value
        }
        return props
    }

    /** 整包递归找 module.prop，按安装器落点标记 / 路径深度选出真正的那一份，并校验 id。 */
    private fun inspectLocalZip(zip: ZipFile): ModuleArchive {
        val names = LinkedHashSet<String>()
        val propEntries = mutableListOf<ZipEntry>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name.replace('\\', '/')
            if (isUnsafeEntryName(name)) throw ModuleInstallFailure("E_ZIP_SLIP", entry.name)
            if (entry.isDirectory) continue
            names.add(name)
            if (name.substringAfterLast('/') == "module.prop") propEntries.add(entry)
        }
        if (propEntries.isEmpty()) {
            throw ModuleInstallFailure("E_NO_MODULE_PROP", "checked ${names.size} entries, no module.prop at any depth")
        }
        val best = propEntries.map { entry ->
            val name = entry.name.replace('\\', '/')
            val dir = name.substringBeforeLast('/', "")
            val prefix = if (dir.isEmpty()) "" else "$dir/"
            PropCandidate(
                entry = entry,
                prefix = prefix,
                magiskMarker = names.contains("${prefix}META-INF/com/google/android/update-binary") ||
                    names.contains("META-INF/com/google/android/update-binary"),
                customizeSh = names.contains("${prefix}customize.sh")
            )
        }.sortedWith(compareByDescending<PropCandidate> { it.score }.thenBy { it.prefix.length })
            .first()

        val text = try {
            zip.getInputStream(best.entry).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            throw ModuleInstallFailure(
                "E_NO_MODULE_PROP",
                "${best.entry.name}: ${e.message ?: e.javaClass.simpleName}"
            )
        }
        val props = parsePropText(text)
        val id = props["id"]?.trim().orEmpty()
        if (id.isEmpty()) throw ModuleInstallFailure("E_NO_MODULE_ID", best.entry.name)
        if (!ID_PATTERN.matches(id)) throw ModuleInstallFailure("E_ID_UNSAFE", id)
        return ModuleArchive(
            id = id,
            rootPrefix = best.prefix,
            modulePropEntry = best.entry.name.replace('\\', '/'),
            magiskMarker = best.magiskMarker,
            customizeSh = best.customizeSh
        )
    }

    /** 把 module.prop 所在目录整棵树解压到本机临时目录（逐条校验真实路径，防 zip slip）。 */
    private fun extractModuleRoot(zip: ZipFile, rootPrefix: String, destDir: File) {
        val destRoot = destDir.canonicalFile
        val entries = zip.entries()
        var written = 0
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val rawName = entry.name.replace('\\', '/')
            if (isUnsafeEntryName(rawName)) throw ModuleInstallFailure("E_ZIP_SLIP", entry.name)
            if (rootPrefix.isNotEmpty() && !rawName.startsWith(rootPrefix)) continue
            val rel = if (rootPrefix.isEmpty()) rawName else rawName.removePrefix(rootPrefix)
            if (rel.isEmpty()) continue
            val target = File(destRoot, rel).canonicalFile
            if (target != destRoot && !target.path.startsWith(destRoot.path + File.separator)) {
                throw ModuleInstallFailure("E_ZIP_SLIP", entry.name)
            }
            if (entry.isDirectory) {
                if (!target.exists() && !target.mkdirs()) {
                    throw ModuleInstallFailure("E_STAGE_FAILED", target.absolutePath)
                }
            } else {
                target.parentFile?.mkdirs()
                try {
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                } catch (e: IOException) {
                    throw ModuleInstallFailure(
                        "E_STAGE_FAILED",
                        "${entry.name}: ${e.message ?: e.javaClass.simpleName}"
                    )
                }
                written++
            }
        }
        if (written == 0) {
            throw ModuleInstallFailure("E_STAGE_FAILED", "no files extracted under '$rootPrefix'")
        }
    }

    /** 回读校验：模块目录与 module.prop 必须真的出现在 /data/adb/modules 下。返回空串表示通过。 */
    private fun verifyInstalled(moduleId: String): String {
        val target = "$MODULES_DIR/$moduleId"
        val check = rootExec(
            "if [ -f ${shq("$target/module.prop")} ]; then echo RMM_VERIFY_OK; " +
                "elif [ -d ${shq(target)} ]; then echo RMM_VERIFY_NO_PROP; else echo RMM_VERIFY_MISSING; fi",
            timeout = 30
        )
        val signal = check.output
        return when {
            signal.contains("RMM_VERIFY_OK") -> ""
            signal.contains("RMM_VERIFY_NO_PROP") -> "directory exists but module.prop is missing: $target"
            signal.contains("RMM_VERIFY_MISSING") -> "module directory not found: $target"
            else -> detailOf(check).ifBlank { "cannot read $target (exit=${check.exitCode})" }
        }
    }

    /** 把官方安装器的原始报错拼进详情，用户看到的是完整链路而不是最后一句话。 */
    private fun withInstaller(detail: String, installerErrors: List<String>): String {
        val parts = mutableListOf<String>()
        if (installerErrors.isNotEmpty()) parts.add("installer: " + installerErrors.joinToString(" | "))
        if (detail.isNotBlank()) parts.add(detail)
        return parts.joinToString("\n")
    }

    actual fun installModule(zipFilePath: String): RootModuleInstallResult {
        return try {
            val zipFile = File(zipFilePath)
            if (!zipFile.isFile) return RootModuleInstallResult(false, err("E_FILE_NOT_FOUND", zipFilePath))
            if (!zipFile.name.endsWith(".zip", ignoreCase = true)) {
                return RootModuleInstallResult(false, err("E_NOT_ZIP", zipFile.name))
            }

            // 1) 真实提权检测：没有 su / su 拒绝授权 / 真的 uid=0，三种情况文案不同
            val rootStatus = probeRoot()
            when (rootStatus.state) {
                RootProbe.OK -> {}
                RootProbe.DENIED -> return RootModuleInstallResult(false, err("E_ROOT_DENIED", rootStatus.detail))
                RootProbe.NO_SU -> return RootModuleInstallResult(false, err("E_ROOT_UNAVAILABLE", rootStatus.detail))
                RootProbe.UNKNOWN -> return RootModuleInstallResult(false, err("E_ROOT_REQUIRED", rootStatus.detail))
            }

            // 2) /data/adb 与 /data/adb/modules：目录不存在 / 只读 / 写不进去分别报不同错误
            val dirSignal = rootExec(
                "if [ -d ${shq(MODULES_DIR)} ]; then echo RMM_DIR_OK; " +
                    "elif [ -d /data/adb ]; then echo RMM_DIR_PARTIAL; else echo RMM_DIR_MISSING; fi",
                timeout = 30
            )
            when {
                dirSignal.output.contains("RMM_DIR_MISSING") ->
                    return RootModuleInstallResult(false, err("E_ADB_DIR_MISSING", detailOf(dirSignal)))
                dirSignal.output.contains("RMM_DIR_PARTIAL") -> {
                    val mk = rootExec("mkdir -p ${shq(MODULES_DIR)} && echo RMM_MKDIR_OK", timeout = 30)
                    if (!mk.output.contains("RMM_MKDIR_OK")) {
                        return RootModuleInstallResult(false, err("E_ADB_DIR_MISSING", detailOf(mk)))
                    }
                }
            }
            val writeProbe = rootExec(
                "p=${shq("$MODULES_DIR/.rmm_write_probe")}; " +
                    "if touch \"\$p\" 2>/dev/null; then rm -f \"\$p\"; echo RMM_WRITABLE; else echo RMM_NOT_WRITABLE; fi",
                timeout = 30
            )
            if (!writeProbe.output.contains("RMM_WRITABLE")) {
                val detail = detailOf(writeProbe)
                if (detail.contains("read-only", ignoreCase = true)) {
                    return RootModuleInstallResult(false, err("E_ADB_READONLY", detail))
                }
                if (detail.contains("denied", ignoreCase = true) || detail.contains("Permission", ignoreCase = true)) {
                    return RootModuleInstallResult(false, err("E_ROOT_DENIED", detail))
                }
                return RootModuleInstallResult(false, err("E_ADB_READONLY", detail.ifBlank { "cannot write $MODULES_DIR" }))
            }

            // 3) 先在本机把 zip 解析清楚（id / module.prop 落点），不依赖设备端 unzip
            val zip = try {
                ZipFile(zipFile)
            } catch (e: ZipException) {
                return RootModuleInstallResult(false, err("E_ZIP_INVALID", e.message.orEmpty()))
            } catch (e: IOException) {
                return RootModuleInstallResult(false, err("E_ZIP_INVALID", e.message.orEmpty()))
            }

            zip.use { archive ->
                val meta = try {
                    inspectLocalZip(archive)
                } catch (f: ModuleInstallFailure) {
                    return RootModuleInstallResult(false, err(f.code, f.detail))
                }

                // 4) 官方安装器优先（Magisk / KernelSU）
                val installerErrors = mutableListOf<String>()
                val magiskAvailable = rootExec("command -v magisk >/dev/null 2>&1 && echo RMM_HAS_MAGISK", timeout = 30)
                    .output.contains("RMM_HAS_MAGISK")
                if (magiskAvailable) {
                    val magisk = rootExec("magisk --install-module ${shq(zipFile.absolutePath)}", timeout = 300)
                    if (magisk.exitCode == 0) {
                        val verify = verifyInstalled(meta.id)
                        if (verify.isEmpty()) return RootModuleInstallResult(true, err("E_OK_MAGISK", meta.id))
                        installerErrors.add("magisk --install-module: exit=0 but verify failed: $verify")
                    } else {
                        installerErrors.add(
                            "magisk --install-module: " + detailOf(magisk).ifBlank { "exit=${magisk.exitCode}" }
                        )
                    }
                }
                val ksudAvailable = rootExec("command -v ksud >/dev/null 2>&1 && echo RMM_HAS_KSUD", timeout = 30)
                    .output.contains("RMM_HAS_KSUD")
                if (ksudAvailable) {
                    val ksud = rootExec("ksud module install ${shq(zipFile.absolutePath)}", timeout = 300)
                    if (ksud.exitCode == 0) {
                        val verify = verifyInstalled(meta.id)
                        if (verify.isEmpty()) return RootModuleInstallResult(true, err("E_OK_KSU", meta.id))
                        installerErrors.add("ksud module install: exit=0 but verify failed: $verify")
                    } else {
                        installerErrors.add("ksud module install: " + detailOf(ksud).ifBlank { "exit=${ksud.exitCode}" })
                    }
                }
                if (!magiskAvailable && !ksudAvailable) {
                    installerErrors.add("neither magisk nor ksud is available in Root PATH")
                }

                // 5) 兜底：本机解压 + Root 复制到 /data/adb/modules/<id>
                val stage = File(appContext.cacheDir, "rmm_stage_${System.currentTimeMillis()}")
                if (!stage.mkdirs() && !stage.isDirectory) {
                    return RootModuleInstallResult(false, err("E_STAGE_FAILED", stage.absolutePath))
                }
                try {
                    try {
                        extractModuleRoot(archive, meta.rootPrefix, stage)
                    } catch (f: ModuleInstallFailure) {
                        return RootModuleInstallResult(false, err(f.code, withInstaller(f.detail, installerErrors)))
                    }
                    val targetDir = "$MODULES_DIR/${meta.id}"
                    val copy = rootExec(
                        "rm -rf ${shq(targetDir)} && mkdir -p ${shq(targetDir)} && " +
                            "cp -rf ${shq(stage.absolutePath + "/.")} ${shq("$targetDir/")} && echo RMM_COPY_OK",
                        timeout = 300
                    )
                    if (!copy.output.contains("RMM_COPY_OK")) {
                        val detail = detailOf(copy).ifBlank { "exit=${copy.exitCode}" }
                        val code = when {
                            detail.contains("read-only", ignoreCase = true) -> "E_ADB_READONLY"
                            detail.contains("denied", ignoreCase = true) -> "E_ROOT_DENIED"
                            else -> "E_COPY_FAILED"
                        }
                        return RootModuleInstallResult(false, err(code, withInstaller(detail, installerErrors)))
                    }
                    rootExec("chmod -R 755 ${shq(targetDir)}", timeout = 120)
                    rootExec("chown -R 0:0 ${shq(targetDir)}", timeout = 120)

                    val verify = verifyInstalled(meta.id)
                    if (verify.isNotEmpty()) {
                        return RootModuleInstallResult(
                            false,
                            err("E_VERIFY_FAILED", withInstaller("${meta.id}: $verify", installerErrors))
                        )
                    }
                    // Magisk/KernelSU 都是重启后才会加载新模块，文案必须如实说明
                    RootModuleInstallResult(true, err("E_OK_MANUAL", meta.id))
                } finally {
                    stage.deleteRecursively()
                    rootExec("rm -rf ${shq(stage.absolutePath)}", timeout = 60)
                }
            }
        } catch (e: Exception) {
            RootModuleInstallResult(false, err("E_EXCEPTION", "${e.javaClass.simpleName}: ${e.message.orEmpty()}"))
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
                        "echo '@@@${key.uppercase()}|'\"$(sed -n 's/^$key=//p' ${shq("$dir/module.prop")} 2>/dev/null | head -n 1 | tr -d '\\r')\"; "
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

    // ==================== 模块自带界面（WebUI） ====================

    /**
     * 一次 shell 扫描所有模块的 WebUI 入口，只回传真实存在的文件。
     *
     * 为什么要批量：模块目录在 /data/adb（应用进程读不到），逐个模块探测要起 N 次 shell；
     * 界面只需要知道"哪些模块有界面"，一次扫描即可。
     */
    private fun webUiProbeScript(): String = buildString {
        append("for d in ${shq(MODULES_DIR)}/*/; do ")
        append("[ -d \"\$d\" ] || continue; ")
        append("id=\$(basename \"\$d\"); found=\"\"; ")
        append("w=\$(sed -n 's/^webui=//p' \"\${d}module.prop\" 2>/dev/null | head -n 1 | tr -d '\\r'); ")
        append("w=\${w%/}; w=\${w#/}; ")
        append("if [ -n \"\$w\" ] && [ -f \"\${d}\${w}\" ]; then found=\"\${d}\${w}\"; fi; ")
        append("if [ -z \"\$found\" ] && [ -n \"\$w\" ] && [ -f \"\${d}\${w}/index.html\" ]; then found=\"\${d}\${w}/index.html\"; fi; ")
        append("if [ -z \"\$found\" ]; then ")
        append("for c in webroot/index.html webroot/index.htm webui/index.html webui/index.htm index.html index.htm; do ")
        append("if [ -f \"\${d}\${c}\" ]; then found=\"\${d}\${c}\"; break; fi; ")
        append("done; fi; ")
        append("if [ -z \"\$found\" ]; then ")
        append("for c in webroot webui; do ")
        append("if [ -d \"\${d}\${c}\" ]; then ")
        append("h=\$(ls \"\${d}\${c}\"/*.html \"\${d}\${c}\"/*.htm 2>/dev/null | head -n 1); ")
        append("if [ -n \"\$h\" ]; then found=\"\$h\"; break; fi; fi; done; fi; ")
        append("if [ -n \"\$found\" ]; then echo \"RMM_WEBUI|\$id|\$found\"; fi; ")
        append("done")
    }

    actual fun getWebUIModuleIds(): Set<String> {
        return try {
            val ids = LinkedHashSet<String>()
            rootExec(webUiProbeScript(), timeout = 60).output.lineSequence().forEach { raw ->
                val line = raw.trim()
                if (!line.startsWith("RMM_WEBUI|")) return@forEach
                val id = line.removePrefix("RMM_WEBUI|").substringBefore('|').trim()
                if (id.isNotEmpty()) ids.add(id)
            }
            ids
        } catch (e: Exception) {
            emptySet()
        }
    }

    /** 在设备上解析单个模块的 WebUI 入口文件；不存在返回 null。 */
    private fun resolveRemoteWebUIEntry(moduleId: String): String? {
        val dir = "$MODULES_DIR/$moduleId"
        val script = buildString {
            append("d=${shq("$dir/")}; found=\"\"; ")
            append("w=\$(sed -n 's/^webui=//p' \"\${d}module.prop\" 2>/dev/null | head -n 1 | tr -d '\\r'); ")
            append("w=\${w%/}; w=\${w#/}; ")
            append("if [ -n \"\$w\" ] && [ -f \"\${d}\${w}\" ]; then found=\"\${d}\${w}\"; fi; ")
            append("if [ -z \"\$found\" ] && [ -n \"\$w\" ] && [ -f \"\${d}\${w}/index.html\" ]; then found=\"\${d}\${w}/index.html\"; fi; ")
            append("if [ -z \"\$found\" ]; then ")
            append("for c in webroot/index.html webroot/index.htm webui/index.html webui/index.htm index.html index.htm; do ")
            append("if [ -f \"\${d}\${c}\" ]; then found=\"\${d}\${c}\"; break; fi; done; fi; ")
            append("if [ -z \"\$found\" ]; then ")
            append("for c in webroot webui; do ")
            append("if [ -d \"\${d}\${c}\" ]; then ")
            append("h=\$(ls \"\${d}\${c}\"/*.html \"\${d}\${c}\"/*.htm 2>/dev/null | head -n 1); ")
            append("if [ -n \"\$h\" ]; then found=\"\$h\"; break; fi; fi; done; fi; ")
            append("if [ -n \"\$found\" ]; then echo \"RMM_WEBUI_ENTRY|\$found\"; fi")
        }
        val marker = "RMM_WEBUI_ENTRY|"
        val output = rootExec(script, timeout = 60).output
        val index = output.indexOf(marker)
        if (index < 0) return null
        val path = output.substring(index + marker.length).lineSequence().firstOrNull()?.trim().orEmpty()
        return path.ifEmpty { null }
    }

    /**
     * 把模块自带的界面复制到本应用可读的目录，返回本机入口文件路径。
     *
     * 为什么不直接把 /data/adb/... 的路径交给 WebView：那是 0700 root:root，应用进程读不到，
     * WebView 只会得到一个空白页（net::ERR_ACCESS_DENIED）。复制到本应用缓存目录才能真正显示。
     */
    actual fun prepareModuleWebUI(moduleId: String): ModuleWebUIResult {
        return try {
            if (moduleId.isBlank() || moduleId.contains('/')) {
                return ModuleWebUIResult(null, "E_WEBUI_MISSING", moduleId)
            }
            if (!hasRootAccess()) {
                return ModuleWebUIResult(null, "E_WEBUI_ROOT_REQUIRED")
            }
            val remote = resolveRemoteWebUIEntry(moduleId)
                ?: return ModuleWebUIResult(null, "E_WEBUI_MISSING", "$MODULES_DIR/$moduleId")

            val cacheDir = File(appContext.cacheDir, "module_webui/$moduleId")
            cacheDir.deleteRecursively()
            if (!cacheDir.mkdirs() && !cacheDir.isDirectory) {
                return ModuleWebUIResult(null, "E_WEBUI_COPY_FAILED", cacheDir.absolutePath)
            }

            val moduleDir = "$MODULES_DIR/$moduleId"
            val srcDir = remote.substringBeforeLast('/', "")
            val copy = if (srcDir == moduleDir) {
                // 入口就在模块根目录：整目录复制，但跳过 system / META-INF（模块体积主要在这里，界面用不到）
                rootExec(
                    "for f in ${shq(moduleDir)}/*; do " +
                        "case \"\$f\" in */system|*/META-INF) continue;; esac; " +
                        "cp -rf \"\$f\" ${shq(cacheDir.absolutePath + "/")}; done; echo RMM_WEBUI_COPIED",
                    timeout = 120
                )
            } else {
                rootExec(
                    "cp -rf ${shq("$srcDir/.")} ${shq(cacheDir.absolutePath + "/")} && echo RMM_WEBUI_COPIED",
                    timeout = 120
                )
            }
            if (!copy.output.contains("RMM_WEBUI_COPIED")) {
                return ModuleWebUIResult(
                    null,
                    "E_WEBUI_COPY_FAILED",
                    detailOf(copy).ifBlank { "exit=${copy.exitCode}" }
                )
            }

            val local = File(cacheDir, remote.removePrefix(srcDir).trimStart('/'))
            if (!local.isFile) {
                return ModuleWebUIResult(null, "E_WEBUI_COPY_FAILED", "local copy missing: ${local.absolutePath}")
            }
            ModuleWebUIResult(local.absolutePath)
        } catch (e: Exception) {
            ModuleWebUIResult(null, "E_WEBUI_EXCEPTION", "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
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
