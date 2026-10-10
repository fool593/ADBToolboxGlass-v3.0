package com.example.adbtoolbox.common

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipFile

// 插件管理器：管理 AxManager 风格插件的安装、卸载、启用、禁用、脚本执行
//
// 安装流程的几条硬约束（都是真实设备上会踩到的坑）：
// 1. 解压只用 java.util.zip，不调用设备端 unzip / busybox —— 大量设备没有 unzip，
//    旧实现因此把"设备上没有 unzip"报成了"module.prop not found"这类假原因；
// 2. module.prop 允许在 zip 内的任意一层目录（有的包多套一层文件夹）：整包递归查找，
//    并优先认带安装器落点标记的那一份（Magisk 的 META-INF/com/google/android/update-binary、
//    KernelSU / APatch 的 customize.sh）；
// 3. 失败一律返回「稳定错误码 + 真实异常/命令输出」，由界面用 AppStrings 翻译，
//    绝不返回"安装失败"这种没有信息量的文案；
// 4. 安装完成后回读一次真实列表确认插件确实出现，否则如实报失败。
actual object PluginManager {

    /** 插件 id 必须能直接当目录名用：不允许路径分隔符、`..` 与空值。 */
    private val ID_PATTERN = Regex("^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$")

    /** WebUI 入口候选（相对模块目录，按优先级）。只认真正存在的文件。 */
    private val WEBUI_ENTRY_CANDIDATES = listOf(
        "webroot/index.html",
        "webroot/index.htm",
        "webui/index.html",
        "webui/index.htm",
        "index.html",
        "index.htm"
    )

    /** 目录内的入口文件名（module.prop 显式声明的 webui 目录里用）。 */
    private val WEBUI_INDEX_FILES = listOf("index.html", "index.htm")

    /** 探测 WebUI 时最多检查的目录项，避免异常大包把列表页拖死。 */
    private const val MAX_SCAN_ENTRIES = 4000

    /** 安装失败的稳定错误码载体：码给界面翻译，detail 是真实原因。 */
    private class InstallFailure(val code: String, val detail: String = "") : Exception(code)

    private fun failure(code: String, detail: String = ""): PluginInstallResult =
        PluginInstallResult(false, if (detail.isBlank()) code else "$code\n$detail")

    /** 单个 shell 参数的安全引用（空格、中文、单引号、$ 都能原样传过去）。 */
    private fun shq(value: String): String = "'" + value.replace("'", "'\\''") + "'"

    // 插件根目录（应用私有目录，不需要额外权限）
    private fun getPluginsDir(): String {
        return "${appContext.filesDir.absolutePath}/plugins"
    }

    // 确保插件目录存在
    private fun ensurePluginsDir() {
        val dir = File(getPluginsDir())
        if (!dir.exists()) dir.mkdirs()
    }

    // ==================== zip 解析 ====================

    // zip 结构探测统一交给本文件末尾的 ModuleZipInspector：插件安装（这里）、
    // Root 模块安装（RootModuleManager）、ADB / Shizuku 刷入（ADBTools）三条路径共用一份规则。
    // 否则同一个包在插件页能装、在 Root 页却说"没找到 module.prop"，判据会各自漂移。

    /** zip 条目名是否可以直接落到文件系统上：拒绝绝对路径、`..`、Windows 盘符（zip slip）。 */
    private fun isUnsafeEntryName(rawName: String): Boolean {
        val name = rawName.replace('\\', '/')
        if (name.isBlank() || name.startsWith("/")) return true
        if (name.length >= 2 && name[1] == ':') return true
        return name.split('/').any { it == ".." }
    }

    /** 解压整个 zip 到临时目录；写文件前逐条检查真实路径，防止 ../ 逃逸。 */
    private fun extractZip(zip: ZipFile, destDir: File) {
        val destRoot = destDir.canonicalFile
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name.replace('\\', '/')
            if (isUnsafeEntryName(name)) throw InstallFailure("E_ZIP_SLIP", entry.name)
            val target = File(destRoot, name).canonicalFile
            if (target != destRoot && !target.path.startsWith(destRoot.path + File.separator)) {
                throw InstallFailure("E_ZIP_SLIP", entry.name)
            }
            if (entry.isDirectory) {
                if (!target.exists() && !target.mkdirs()) {
                    throw InstallFailure("E_EXTRACT_FAILED", target.absolutePath)
                }
            } else {
                if (target.parentFile?.mkdirs() == false && target.parentFile?.isDirectory != true) {
                    throw InstallFailure("E_EXTRACT_FAILED", target.absolutePath)
                }
                try {
                    zip.getInputStream(entry).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                } catch (e: IOException) {
                    throw InstallFailure(
                        "E_EXTRACT_FAILED",
                        "${entry.name}: ${e.message ?: e.javaClass.simpleName}"
                    )
                }
            }
        }
    }

    // ==================== module.prop ====================

    /** 解析 module.prop 文本；兼容 BOM、CRLF 与行内注释。 */
    private fun parseModuleProp(text: String): Map<String, String> {
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

    /** 解析磁盘上的 module.prop；读不到时返回空表（调用方按缺失处理）。 */
    private fun parseModuleProp(file: File): Map<String, String> {
        return try {
            parseModuleProp(file.readText(Charsets.UTF_8))
        } catch (e: Exception) {
            emptyMap()
        }
    }

    /** 递归查找 module.prop；先看当前目录，再按稳定顺序进子目录。 */
    private fun findModuleProp(dir: File): File? {
        val direct = File(dir, "module.prop")
        if (direct.isFile) return direct
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            null
        } ?: return null
        children.sortedBy { it.name.lowercase() }.forEach { child ->
            if (child.isDirectory && !child.name.startsWith(".")) {
                val found = findModuleProp(child)
                if (found != null) return found
            }
        }
        return null
    }

    // ==================== WebUI / action 探测 ====================

    private fun isHtmlFile(file: File): Boolean {
        val name = file.name.lowercase()
        return name.endsWith(".html") || name.endsWith(".htm")
    }

    /** 有深度上限的 html 收集：跳过 system / META-INF / 隐藏目录，顺序稳定，条目数封顶。 */
    private fun collectHtmlFiles(dir: File, maxDepth: Int): List<File> {
        val result = mutableListOf<File>()
        val visited = HashSet<String>()
        var inspected = 0

        fun walk(current: File, depth: Int) {
            if (depth > maxDepth || inspected >= MAX_SCAN_ENTRIES) return
            val canonical = try {
                current.canonicalPath
            } catch (e: Exception) {
                current.absolutePath
            }
            if (!visited.add(canonical)) return
            val children = try {
                current.listFiles()
            } catch (e: Exception) {
                null
            } ?: return
            children.sortedBy { it.name.lowercase() }.forEach { child ->
                inspected++
                if (inspected > MAX_SCAN_ENTRIES) return
                if (child.isDirectory) {
                    val lower = child.name.lowercase()
                    if (lower == "system" || lower == "meta-inf" || lower == "node_modules" || child.name.startsWith(".")) {
                        return@forEach
                    }
                    walk(child, depth + 1)
                } else if (child.isFile && isHtmlFile(child)) {
                    result.add(child)
                }
            }
        }

        walk(dir, 1)
        return result
    }

    /**
     * 解析模块自带的 WebUI 入口文件。
     *
     * 只认真实存在的文件，顺序：
     * 1. module.prop 里显式声明的 `webui=` / `webuiRoot=` / `webroot=`（KernelSU / APatch 约定）；
     * 2. 约定路径 webroot/index.html、webui/index.html、index.html 及 .htm 变体；
     * 3. webroot / webui 目录下的任意 html（这两个目录名本身就是"界面目录"约定）；
     * 4. 更深一层目录里的 index.html / index.htm。
     *
     * 找不到返回 null —— 界面据此完全不显示"打开界面"按钮（不是灰掉、不是点了报错）。
     */
    private fun findWebUIEntry(moduleDir: File, props: Map<String, String>): File? {
        listOf("webui", "webuiRoot", "webui_root", "webroot").forEach { key ->
            val raw = props[key]?.trim().orEmpty()
            if (raw.isEmpty()) return@forEach
            val rel = raw.trimStart('/').trimEnd('/')
            if (rel.isEmpty()) return@forEach
            val declared = File(moduleDir, rel)
            if (declared.isFile && isHtmlFile(declared)) return declared
            if (declared.isDirectory) {
                WEBUI_INDEX_FILES.forEach { name ->
                    val entry = File(declared, name)
                    if (entry.isFile) return entry
                }
                collectHtmlFiles(declared, 2).firstOrNull()?.let { return it }
            }
        }

        WEBUI_ENTRY_CANDIDATES.forEach { rel ->
            val entry = File(moduleDir, rel)
            if (entry.isFile) return entry
        }

        listOf("webroot", "webui").forEach { dirName ->
            val dir = File(moduleDir, dirName)
            if (dir.isDirectory) collectHtmlFiles(dir, 2).firstOrNull()?.let { return it }
        }

        return collectHtmlFiles(moduleDir, 3).firstOrNull { file ->
            val name = file.name.lowercase()
            name == "index.html" || name == "index.htm"
        }
    }

    // ==================== 安装 ====================

    // 安装插件（zip 文件）
    actual fun installPlugin(zipFilePath: String): PluginInstallResult {
        val zipFile = File(zipFilePath)
        if (!zipFile.isFile) return failure("E_FILE_NOT_FOUND", zipFilePath)
        if (!zipFile.name.endsWith(".zip", ignoreCase = true)) return failure("E_NOT_ZIP", zipFile.name)

        ensurePluginsDir()

        var tempDir: File? = null
        var stagingDir: File? = null
        var targetDir: File? = null
        var moved = false
        // 失败时必须能回答"哪一步失败、包到底长什么样"：
        // layout 是结构探测结果，step 是当前失败步骤，两者一起拼进错误详情。
        var layout: ModuleZipInspector.Layout? = null
        var step = "module_step_scan"

        return try {
            val archive = try {
                ZipFile(zipFile)
            } catch (e: ZipException) {
                return failure("E_ZIP_INVALID", e.message.orEmpty())
            } catch (e: IOException) {
                return failure("E_ZIP_INVALID", e.message.orEmpty())
            }

            archive.use { zip ->
                // 结构探测：module.prop 允许在任意一层；没有 module.prop 时，
                // 只要包里还有确定的安装落点（安装脚本 / ap_patch / 自带 APK），一样当成可安装的包。
                val inspected = ModuleZipInspector.inspect(zip, zipFile.nameWithoutExtension)
                layout = inspected
                if (inspected.unsafeEntry != null) {
                    // 越界路径（../ 或绝对路径）一律拒绝：宁可装不上，也不能写到 plugins 目录之外
                    throw InstallFailure("E_ZIP_SLIP", inspected.unsafeEntry)
                }

                // 包里没有 module.prop 时：落点确定就合成一份，而不是直接放弃
                val syntheticProp = !inspected.hasModuleProp
                var pluginId = inspected.id
                if (pluginId.isEmpty()) {
                    if (!syntheticProp) {
                        throw InstallFailure("E_NO_MODULE_ID", inspected.diagnostics(step, ""))
                    }
                    if (!inspected.hasInstallTarget || inspected.suggestedId.isBlank()) {
                        throw InstallFailure("E_NO_INSTALL_TARGET", inspected.diagnostics(step, ""))
                    }
                    pluginId = inspected.suggestedId
                }
                if (!ID_PATTERN.matches(pluginId)) throw InstallFailure("E_ID_UNSAFE", pluginId)

                step = "module_step_extract"
                val temp = File(getPluginsDir(), "temp_${System.currentTimeMillis()}")
                tempDir = temp
                if (!temp.exists() && !temp.mkdirs()) throw InstallFailure("E_EXTRACT_FAILED", temp.absolutePath)
                extractZip(zip, temp)

                val rootDir = if (inspected.rootPrefix.isEmpty()) temp else File(temp, inspected.rootPrefix.removeSuffix("/"))
                if (!File(rootDir, "module.prop").isFile && !syntheticProp) {
                    throw InstallFailure("E_NO_MODULE_PROP", inspected.modulePropEntry.orEmpty())
                }

                // 先在临时目录里拷好再整体换名，避免把半成品直接写进正式目录
                step = "module_step_stage"
                val staging = File(getPluginsDir(), "temp_install_${pluginId}_${System.currentTimeMillis()}")
                stagingDir = staging
                if (!rootDir.copyRecursively(staging, overwrite = true)) {
                    throw InstallFailure("E_COPY_FAILED", "${rootDir.absolutePath} -> ${staging.absolutePath}")
                }
                if (syntheticProp && !File(staging, "module.prop").isFile) {
                    // 没有 module.prop 的目录对插件列表来说等于不存在（readPlugin 直接返回 null），
                    // 必须补一份最小可用的，否则"装好了但列表里什么都没有"。
                    try {
                        File(staging, "module.prop")
                            .writeText(synthModulePropText(pluginId, zipFile.nameWithoutExtension), Charsets.UTF_8)
                    } catch (e: Exception) {
                        throw InstallFailure(
                            "E_COPY_FAILED",
                            "cannot write module.prop: ${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                        )
                    }
                }

                step = "module_step_copy"
                val target = File(getPluginsDir(), pluginId)
                targetDir = target
                if (target.exists() && !target.deleteRecursively()) {
                    throw InstallFailure("E_COPY_FAILED", "cannot remove old directory: ${target.absolutePath}")
                }
                if (!staging.renameTo(target)) {
                    if (!staging.copyRecursively(target, overwrite = true)) {
                        throw InstallFailure("E_COPY_FAILED", "${staging.absolutePath} -> ${target.absolutePath}")
                    }
                    staging.deleteRecursively()
                }
                stagingDir = null
                moved = true

                // 回读校验：必须真的出现在已安装列表里，否则不算装好
                step = "module_step_verify"
                val verified = try {
                    getInstalledPlugins().any { it.id == pluginId }
                } catch (e: Exception) {
                    throw InstallFailure(
                        "E_VERIFY_FAILED",
                        "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                    )
                }
                if (!verified) {
                    target.deleteRecursively()
                    moved = false
                    throw InstallFailure("E_VERIFY_FAILED", target.absolutePath)
                }

                listOf("post-fs-data.sh", "service.sh", "action.sh", "uninstall.sh", "customize.sh").forEach { name ->
                    val scriptFile = File(target, name)
                    if (scriptFile.isFile) scriptFile.setExecutable(true, false)
                }

                // customize.sh（KernelSU / 部分 AxManager 包靠它初始化）：失败必须如实回传，不能静默吞掉
                step = "module_step_script"
                val customizeError = runCustomizeIfPresent(target)

                // 合成过 module.prop 的话，元信息以真正落盘的那一份为准
                val finalProps = if (syntheticProp) parseModuleProp(File(target, "module.prop")) else inspected.props
                val plugin = PluginData(
                    id = pluginId,
                    name = finalProps["name"]?.ifBlank { pluginId } ?: pluginId,
                    version = finalProps["version"]?.ifBlank { "1.0" } ?: "1.0",
                    versionCode = finalProps["versionCode"]?.trim()?.toIntOrNull() ?: 1,
                    author = finalProps["author"]?.ifBlank { "Unknown" } ?: "Unknown",
                    description = finalProps["description"].orEmpty(),
                    axeronPlugin = finalProps["axeronPlugin"]?.trim()?.toIntOrNull() ?: 0,
                    isEnabled = !File(target, "disable").exists(),
                    isInstalled = true,
                    pluginDir = target.absolutePath,
                    hasWebUI = findWebUIEntry(target, finalProps) != null,
                    hasAction = File(target, "action.sh").isFile,
                    updateTime = System.currentTimeMillis(),
                    size = formatDirSize(target)
                )

                val message = buildString {
                    append("E_OK_PLUGIN")
                    append('\n')
                    append(plugin.name)
                    if (syntheticProp) {
                        // 如实说明"这份 module.prop 是补出来的"，用户才知道 id 是从哪来的
                        append('\n')
                        append(moduleDiagFmt(AppStrings.get("plugin_warn_synth_prop"), pluginId))
                    }
                    if (customizeError.isNotBlank()) {
                        append('\n')
                        append("W_CUSTOMIZE_FAILED")
                        append('\n')
                        append(customizeError)
                    }
                }
                PluginInstallResult(true, message, plugin)
            }
        } catch (f: InstallFailure) {
            if (moved) targetDir?.deleteRecursively()
            failure(f.code, diagnoseWith(f.detail, layout, step))
        } catch (e: Exception) {
            if (moved) targetDir?.deleteRecursively()
            failure("E_EXCEPTION", diagnoseWith("${e.javaClass.simpleName}: ${e.message.orEmpty()}", layout, step))
        } finally {
            tempDir?.deleteRecursively()
            stagingDir?.deleteRecursively()
        }
    }

    /** 执行 customize.sh；没装、装好、失败三种情况分别返回 ""/""/真实原因。
     *  AxManager 感知（按其源码/server 语义）：
     *  - 若设备装了 AxManager（/data/user_de/0/com.android.shell/axeron/bin/busybox），
     *    用它的 BusyBox ash 运行，并注入 AXERON=true / AXERONVER，脚本才能正确初始化；
     *  - 脚本执行前统一转 LF（AxManager 与 KernelSU 规范都要求 UNIX 换行，
     *    CRLF 会让 Android sh 解析失败 → 退出码 1，这正是"脚本刷入返回值 1"的元凶之一）。 */
    private fun runCustomizeIfPresent(moduleDir: File): String {
        val script = File(moduleDir, "customize.sh")
        if (!script.isFile) return ""
        return try {
            // 1) LF 规范化：.sh / module.prop 必须是 UNIX 换行
            try {
                if (script.exists() && script.length() > 0) {
                    val txt = script.readText()
                    if (txt.contains("\r\n")) script.writeText(txt.replace("\r\n", "\n"))
                }
                val prop = File(moduleDir, "module.prop")
                if (prop.exists()) {
                    val pt = prop.readText()
                    if (pt.contains("\r\n")) prop.writeText(pt.replace("\r\n", "\n"))
                }
            } catch (_: Exception) {}
            // 2) 补全标准安装环境（根因修复，非补丁）：脚本靠这些变量决定逻辑，
            //    缺失会导致 [ "$ARCH" = arm64 ]、$ZIPFILE、$API 等引用落空 -> exit 1。
            val dir = moduleDir.absolutePath
            val is64 = android.os.Build.SUPPORTED_64_BIT_ABIS.isNotEmpty()
            val env = buildString {
                append("MODPATH=${shq(dir)} ")
                append("TMPDIR=${shq(dir)} ")
                append("ZIPFILE=${shq("$dir/installer.zip")} ")
                append("ARCH=").append(if (is64) "arm64" else "arm").append(' ')
                append("IS64BIT=").append(if (is64) "true" else "false").append(' ')
                append("API=").append(android.os.Build.VERSION.SDK_INT).append(' ')
                append("BOOTMODE=true ")
                // OUTFD：Magisk/AxManager 标准脚本的 ui_print 走 fd 3（echo >&3），
                // 没有 fd 3 时脚本一写 UI 日志就 "Bad file descriptor" -> abort -> exit 1。
                append("OUTFD=3 ")
            }
            val axBox = detectAxManagerBusybox()
            val shell = if (axBox != null) "${shq(axBox)} sh" else "sh"
            val axEnv = if (axBox != null) "AXERON=true AXERONVER=10400 " else ""
            val result = ADBTools.execCommand(
                "cd ${shq(dir)} && ${axEnv}${env}$shell customize.sh 3>&1",
                timeout = 120
            )
            if (result.exitCode == 0) {
                ""
            } else {
                listOf("exit=${result.exitCode}", result.output.trim(), result.error.trim())
                    .filter { it.isNotBlank() }
                    .joinToString("\n")
            }
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
        }
    }

    /** 检测 AxManager BusyBox 是否存在；存在则返回其路径（其插件/脚本要求在该环境下运行）。 */
    private fun detectAxManagerBusybox(): String? {
        return try {
            val p = "/data/user_de/0/com.android.shell/axeron/bin/busybox"
            val r = ADBTools.execCommand("test -x ${shq(p)} && echo yes", 8)
            if (r.output.contains("yes")) p else null
        } catch (e: Exception) {
            null
        }
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

    // 列出所有已安装插件。
    // 这里不再吞异常：列表页会 catch 并把真实原因显示出来，比"暂无插件"这种假空列表有用得多。
    actual fun getInstalledPlugins(): List<PluginData> {
        ensurePluginsDir()
        val pluginsDir = File(getPluginsDir())
        return pluginsDir.listFiles()
            ?.asSequence()
            ?.filter { it.isDirectory && !it.name.startsWith("temp_") && !it.name.contains("_backup_") }
            ?.mapNotNull { dir -> readPlugin(dir) }
            ?.sortedBy { it.name.lowercase() }
            ?.toList()
            ?: emptyList()
    }

    /** 读取单个插件目录；没有 module.prop 的目录不是插件，返回 null。 */
    private fun readPlugin(dir: File): PluginData? {
        val propFile = File(dir, "module.prop").takeIf { it.isFile } ?: findModuleProp(dir) ?: return null
        val props = parseModuleProp(propFile)
        val moduleDir = propFile.parentFile ?: dir
        return PluginData(
            id = props["id"]?.trim()?.takeIf { it.isNotEmpty() } ?: dir.name,
            name = props["name"]?.ifBlank { dir.name } ?: dir.name,
            version = props["version"]?.ifBlank { "1.0" } ?: "1.0",
            versionCode = props["versionCode"]?.trim()?.toIntOrNull() ?: 1,
            author = props["author"]?.ifBlank { "Unknown" } ?: "Unknown",
            description = props["description"].orEmpty(),
            axeronPlugin = props["axeronPlugin"]?.trim()?.toIntOrNull() ?: 0,
            isEnabled = !File(dir, "disable").exists(),
            isInstalled = true,
            pluginDir = dir.absolutePath,
            hasWebUI = findWebUIEntry(moduleDir, props) != null,
            hasAction = File(moduleDir, "action.sh").isFile,
            updateTime = dir.lastModified(),
            size = formatDirSize(dir)
        )
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
            if (uninstallSh.isFile) {
                val dir = pluginDir.absolutePath
                ADBTools.execCommand(
                    "cd ${shq(dir)} && MODPATH=${shq(dir)} sh uninstall.sh",
                    timeout = 60
                )
            }
            // 创建 remove 标记（模拟 AxManager 的下次重启移除）
            val removeFile = File(pluginDir, "remove")
            removeFile.createNewFile()
            // 直接删除
            pluginDir.deleteRecursively()
        } catch (e: Exception) {
            false
        }
    }

    // 执行插件的 action.sh。
    // 约定：真的跑起来了首行一定是 `Exit code: N`；跑不起来（没有脚本 / 已停用 / 异常）
    // 首行是本地化的原因，界面据此区分"执行失败"和"根本没执行"。
    actual fun runAction(pluginId: String): String {
        return try {
            val pluginDir = File("${getPluginsDir()}/$pluginId")
            val actionSh = File(pluginDir, "action.sh")
            if (!actionSh.isFile) return AppStrings.get("module_err_no_action")
            if (File(pluginDir, "disable").exists()) return AppStrings.get("module_err_action_disabled")
            val dir = pluginDir.absolutePath
            val result = ADBTools.execCommand(
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
                if (result.output.isBlank() && result.error.isBlank()) {
                    // 空输出也要说清楚，不能让用户对着一片空白猜
                    appendLine(AppStrings.get("hw_no_output"))
                }
            }
        } catch (e: Exception) {
            "${AppStrings.get("module_err_action_exception")}: ${e.javaClass.simpleName}: ${e.message.orEmpty()}"
        }
    }

    // 执行所有已启用插件的开机脚本（由 BootReceiver 调用）

    // 获取插件 WebUI 的入口文件路径（不存在返回 null，界面因此不显示"打开界面"）
    actual fun getWebUIPath(pluginId: String): String? {
        val pluginDir = File(getPluginsDir(), pluginId)
        if (!pluginDir.isDirectory) return null
        val propFile = File(pluginDir, "module.prop").takeIf { it.isFile } ?: findModuleProp(pluginDir)
        val props = propFile?.let { parseModuleProp(it) } ?: emptyMap()
        val moduleDir = propFile?.parentFile ?: pluginDir
        return findWebUIEntry(moduleDir, props)?.absolutePath
    }
}

// ==================== 三条安装路径共用的 zip 结构探测 ====================

/** 诊断行格式化：替换 %1$s 这类占位符（与界面层 hwFormat 的约定一致）。 */
private fun moduleDiagFmt(template: String, vararg args: Any?): String {
    var out = template
    args.forEachIndexed { index, value ->
        out = out.replace("%${index + 1}\$s", value?.toString() ?: "")
    }
    return out
}

/** 失败详情 = 原始原因 + 结构化诊断块；两者都可能为空。 */
private fun diagnoseWith(detail: String, layout: ModuleZipInspector.Layout?, step: String): String {
    val block = layout?.diagnostics(step, "") ?: ""
    val reason = detail.trim()
    return when {
        reason.isEmpty() -> block
        block.isEmpty() -> reason
        else -> reason + "\n" + block
    }
}

/**
 * 合成一份最小可用的 module.prop。
 *
 * 什么时候需要它：zip 里只有安装脚本（service.sh / post-fs-data.sh / customize.sh）、
 * 只有 ap_patch，或者只有自带 APK —— 安装落点是确定的，但包里没有 module.prop。
 * Magisk / KernelSU / 插件列表都靠 module.prop 认模块，不补这一份就等于"装进去了但没人看得见"。
 */
internal fun synthModulePropText(id: String, displayName: String): String = buildString {
    append("id=").append(id).append('\n')
    append("name=").append(displayName.ifBlank { id }).append('\n')
    append("version=1.0").append('\n')
    append("versionCode=1").append('\n')
    append("author=ADBToolbox").append('\n')
    append("description=").append(AppStrings.get("module_synth_prop_desc")).append('\n')
}

/**
 * zip 结构探测器（PluginManager / RootModuleManager / ADBTools 三条安装路径共用）。
 *
 * 为什么必须共用一份：过去三处各自实现了一套"找 module.prop"的规则，
 * 同一个包在插件页能装、在 Root 页却报"没有 module.prop"，用户完全无从判断谁对。
 *
 * 覆盖的包结构（都是真实存在的玩法）：
 * 1. module.prop 在 zip 内任意一层（包经常多套一层目录）；
 * 2. Magisk / Recovery 风格：META-INF/com/google/android/update-binary；
 * 3. KernelSU / APatch 风格：customize.sh（可与 module.prop 同时存在）；
 * 4. 只有脚本的模块：service.sh / post-fs-data.sh（没有 module.prop 也认）；
 * 5. APatch 风格：ap_patch（以及自带的 APK）；
 * 6. 只有自带 APK 的包：按 APK 安装，落点同样由这里给出。
 *
 * 安全边界不在这里放松：越界条目（绝对路径、盘符、`..`）不改名、不丢弃，
 * 原样回传 [Layout.unsafeEntry]，由调用方按自己的错误码拒绝。
 */
internal object ModuleZipInspector {

    /** 安装落点标记：命中哪一个，就说明这个前缀目录是真正的模块根。 */
    private val MARKER_WEIGHTS = linkedMapOf(
        // Magisk / Recovery 的安装器落点
        "META-INF/com/google/android/update-binary" to 6,
        // KernelSU / APatch 的安装脚本
        "customize.sh" to 4,
        // 只有脚本的模块（没有 module.prop 也很常见）
        "service.sh" to 3,
        "post-fs-data.sh" to 3,
        // APatch 的补丁文件
        "ap_patch" to 3,
        "action.sh" to 1,
        "uninstall.sh" to 1,
        "system.prop" to 1
    )

    /** 诊断里最多列出多少条顶层条目。 */
    private const val MAX_TOP_ENTRIES = 30

    /** 诊断里最多列出多少个 APK。 */
    private const val MAX_APK_ENTRIES = 20

    /** 连续短横线压缩用；提到外面避免每次探测都重新编译正则。 */
    private val REPEATED_DASH = Regex("-{2,}")

    /**
     * 一次结构探测的完整结果。
     *
     * @param rootPrefix module.prop 所在目录在 zip 内的前缀（形如 "sub/dir/"），根目录是空串
     * @param modulePropEntry 探测到的 module.prop 条目名；包里确实没有时为 null
     * @param props module.prop 的解析结果（没有就是空表）
     * @param markers 命中 [MARKER_WEIGHTS] 的标记（相对 rootPrefix）
     * @param topEntries 顶层条目（已截断）
     * @param topEntryTotal 顶层条目总数（用于说明截断了多少）
     * @param entryCount 文件条目总数
     * @param apkEntries 包内 APK 条目（已截断）
     * @param unsafeEntry 第一个越界条目名；非 null 表示这个包必须被拒绝
     * @param suggestedId 依据 zip 文件名推导出的合法 id（没有 module.prop 时使用）
     */
    class Layout(
        val rootPrefix: String,
        val modulePropEntry: String?,
        val props: Map<String, String>,
        val markers: List<String>,
        val topEntries: List<String>,
        val topEntryTotal: Int,
        val entryCount: Int,
        val apkEntries: List<String>,
        val unsafeEntry: String?,
        val suggestedId: String
    ) {
        val id: String get() = props["id"]?.trim().orEmpty()
        val name: String get() = props["name"]?.trim().orEmpty()
        val version: String get() = props["version"]?.trim().orEmpty()
        val hasModuleProp: Boolean get() = modulePropEntry != null
        val hasUpdateBinary: Boolean get() = markers.contains("META-INF/com/google/android/update-binary")

        /** 除 module.prop 之外，包里还有没有能确定安装落点的东西。 */
        val hasInstallTarget: Boolean
            get() = hasModuleProp || markers.isNotEmpty() || apkEntries.isNotEmpty()

        /**
         * 结构化诊断块：失败时原样拼进错误详情。
         *
         * 为什么必须带上这些：用户报"装不进去"时，只回一句"安装失败"谁都查不出来。
         * 顶层条目 + module.prop 落点 + id / 名称 / 版本 + rootPrefix + 命中的标记 +
         * 失败步骤 + 原始输出，才够区分"包的结构不认识"和"设备侧失败（权限 / 空间 / 脚本报错）"。
         */
        fun diagnostics(stepKey: String, raw: String): String {
            val lines = mutableListOf<String>()
            lines.add(
                moduleDiagFmt(
                    AppStrings.get("module_diag_module"),
                    id.ifBlank { "-" },
                    name.ifBlank { "-" },
                    version.ifBlank { "-" }
                )
            )
            lines.add(
                moduleDiagFmt(
                    AppStrings.get("module_diag_module_prop"),
                    modulePropEntry ?: AppStrings.get("module_diag_none")
                )
            )
            lines.add(
                moduleDiagFmt(
                    AppStrings.get("module_diag_root_prefix"),
                    rootPrefix.ifEmpty { "/" }
                )
            )
            if (markers.isNotEmpty()) {
                lines.add(moduleDiagFmt(AppStrings.get("module_diag_markers"), markers.joinToString(", ")))
            }
            if (apkEntries.isNotEmpty()) {
                lines.add(moduleDiagFmt(AppStrings.get("module_diag_apk"), apkEntries.joinToString(", ")))
            }
            lines.add(
                moduleDiagFmt(
                    AppStrings.get("module_diag_top_entries"),
                    entryCount.toString(),
                    topEntries.joinToString(", ").ifBlank { AppStrings.get("module_diag_none") }
                )
            )
            if (topEntryTotal > topEntries.size) {
                lines.add(
                    moduleDiagFmt(
                        AppStrings.get("module_diag_truncated"),
                        (topEntryTotal - topEntries.size).toString()
                    )
                )
            }
            if (stepKey.isNotBlank()) {
                lines.add(moduleDiagFmt(AppStrings.get("module_diag_step"), AppStrings.get(stepKey)))
            }
            if (raw.isNotBlank()) {
                lines.add(moduleDiagFmt(AppStrings.get("module_diag_raw"), raw))
            }
            return lines.joinToString("\n")
        }
    }

    /**
     * 探测一个 zip 的安装落点。
     *
     * 判据：
     * 1. 有 module.prop：落点就是它所在的那一层（多个时按标记打分，同样得分取更浅的）；
     * 2. 没有 module.prop：落点由安装标记 / 自带 APK 推导；
     * 3. 顶层条目、越界条目、APK 清单一并回传，供失败时原样展示。
     *
     * 这里不抛异常：越界条目回传 [Layout.unsafeEntry]，由调用方按自己的错误码拒绝。
     */
    fun inspect(zip: ZipFile, fallbackName: String): Layout {
        val names = LinkedHashSet<String>()
        val entriesByName = HashMap<String, ZipEntry>()
        val topLevel = LinkedHashSet<String>()
        val propEntries = mutableListOf<String>()
        val apkEntries = mutableListOf<String>()
        var unsafe: String? = null
        var fileCount = 0

        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val name = entry.name.replace('\\', '/')
            fileCount++
            if (!name.contains('/')) topLevel.add(name)
            if (isUnsafeName(name)) {
                if (unsafe == null) unsafe = entry.name
                continue
            }
            if (!names.add(name)) continue
            entriesByName[name] = entry
            val base = name.substringAfterLast('/')
            if (base == "module.prop") propEntries.add(name)
            if (base.endsWith(".apk", ignoreCase = true)) apkEntries.add(name)
        }

        // 候选落点：有 module.prop 就以它所在目录为准，否则由标记 / APK 推导
        val candidates = LinkedHashSet<String>()
        if (propEntries.isNotEmpty()) {
            propEntries.forEach { candidates.add(prefixOf(it)) }
        } else {
            candidates.add("")
            names.forEach { name ->
                MARKER_WEIGHTS.keys.forEach { marker ->
                    if (name == marker) {
                        candidates.add("")
                    } else if (name.endsWith("/$marker")) {
                        // 落点 = 去掉标记本身后剩下的目录。不能直接用父目录：
                        // update-binary 这种多段标记的父目录是 META-INF 的内部路径，不是模块根。
                        val root = name.removeSuffix("/$marker")
                        candidates.add(if (root.isEmpty()) "" else "$root/")
                    }
                }
            }
            apkEntries.forEach { candidates.add(prefixOf(it)) }
        }

        fun scoreOf(prefix: String): Int {
            var score = if (names.contains("${prefix}module.prop")) 8 else 0
            MARKER_WEIGHTS.forEach { (marker, weight) ->
                if (names.contains("$prefix$marker")) score += weight
            }
            if (names.any { it.startsWith("${prefix}system/") }) score += 2
            if (names.any { it.startsWith("${prefix}webroot/") || it.startsWith("${prefix}webui/") }) score += 1
            if (apkEntries.any { prefixOf(it) == prefix }) score += 1
            // 路径越浅越可能是真正的模块根
            return score - minOf(depthOf(prefix), 3)
        }

        val best = candidates.sortedWith(
            compareByDescending<String> { scoreOf(it) }
                .thenBy { depthOf(it) }
                .thenBy { it }
        ).firstOrNull() ?: ""

        val propEntry = propEntries.firstOrNull { it == "${best}module.prop" }
        // 显式标注类型：emptyMap() 的类型推断会让后面的 Layout 构造变得不确定
        val props: Map<String, String> = try {
            val entry = propEntry?.let { entriesByName[it] }
            if (entry == null) {
                emptyMap()
            } else {
                zip.getInputStream(entry).use { stream ->
                    parseProps(stream.readBytes().toString(Charsets.UTF_8))
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
        val markers = MARKER_WEIGHTS.keys.filter { names.contains("$best$it") }.toList()

        return Layout(
            rootPrefix = best,
            modulePropEntry = propEntry,
            props = props,
            markers = markers,
            topEntries = topLevel.take(MAX_TOP_ENTRIES).toList(),
            topEntryTotal = topLevel.size,
            entryCount = fileCount,
            apkEntries = apkEntries.take(MAX_APK_ENTRIES).toList(),
            unsafeEntry = unsafe,
            suggestedId = suggestId(fallbackName)
        )
    }

    /** 目录前缀：取到最后一个斜杠（含），根目录返回空串。 */
    private fun prefixOf(name: String): String {
        val index = name.lastIndexOf('/')
        return if (index < 0) "" else name.substring(0, index + 1)
    }

    /** 前缀深度 = 斜杠个数，用于"同样得分时取更浅的那一层"。 */
    private fun depthOf(prefix: String): Int = prefix.count { it == '/' }

    /** 越界判断，与调用方的规则保持一致：绝对路径 / 盘符 / `..` 一律不接受。 */
    private fun isUnsafeName(rawName: String): Boolean {
        val name = rawName.replace('\\', '/')
        if (name.isBlank() || name.startsWith("/")) return true
        if (name.length >= 2 && name[1] == ':') return true
        return name.split('/').any { it == ".." }
    }

    /**
     * 按 zip 文件名推导一个合法 id（包里没有 module.prop 时用）。
     *
     * 规则与 PluginManager.ID_PATTERN 对齐：首字符必须是字母或数字，只允许字母数字与 . _ + -，
     * 长度不超过 64。文件名全是中文或符号时退化成 module-<时间戳>，仍然是合法 id。
     */
    private fun suggestId(rawName: String): String {
        val cleaned = rawName.lowercase()
            .map { ch ->
                if ((ch.isLetterOrDigit() && ch.code < 128) || ch == '.' || ch == '_' || ch == '+' || ch == '-') {
                    ch
                } else {
                    '-'
                }
            }
            .joinToString("")
            .replace(REPEATED_DASH, "-")
            .trim('.', '-', '_', '+')
            .take(48)
        return if (cleaned.length >= 2 && cleaned.first().isLetterOrDigit()) {
            cleaned
        } else {
            "module-" + (System.currentTimeMillis() / 1000L).toString()
        }
    }

    /**
     * 解析 module.prop 文本，规则与 PluginManager.parseModuleProp 一致
     * （兼容 BOM、CRLF、空行、以 # 开头的注释行）。
     *
     * 单独放一份的原因：探测器是独立对象，不能让"探测"依赖调用方的私有成员；
     * 从磁盘读 module.prop 的路径（插件列表 / WebUI 入口）仍然用调用方自己的解析。
     */
    private fun parseProps(text: String): Map<String, String> {
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
}
