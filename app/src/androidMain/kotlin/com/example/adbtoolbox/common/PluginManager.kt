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

    /** zip 内的一个候选落点（module.prop 所在目录）。 */
    private class ZipCandidate(
        val entry: ZipEntry,
        val prefix: String,
        val magiskMarker: Boolean,
        val customizeSh: Boolean
    ) {
        /** 打分：带安装器落点标记的目录优先，其次路径更浅。 */
        val score: Int get() = (if (magiskMarker) 2 else 0) + (if (customizeSh) 1 else 0)
    }

    /** 一个插件包在 zip 内的真实落点。 */
    private class ZipLayout(
        val modulePropEntry: String,
        val rootPrefix: String,
        val modulePropText: String,
        val magiskMarker: Boolean,
        val customizeSh: Boolean
    )

    /** zip 条目名是否可以直接落到文件系统上：拒绝绝对路径、`..`、Windows 盘符（zip slip）。 */
    private fun isUnsafeEntryName(rawName: String): Boolean {
        val name = rawName.replace('\\', '/')
        if (name.isBlank() || name.startsWith("/")) return true
        if (name.length >= 2 && name[1] == ':') return true
        return name.split('/').any { it == ".." }
    }

    /** 整包递归查找 module.prop，并按安装器落点标记 / 路径深度选出真正的那一份。 */
    private fun inspectZip(zip: ZipFile): ZipLayout {
        val names = LinkedHashSet<String>()
        val propEntries = mutableListOf<ZipEntry>()
        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            val name = entry.name.replace('\\', '/')
            if (isUnsafeEntryName(name)) throw InstallFailure("E_ZIP_SLIP", entry.name)
            if (entry.isDirectory) continue
            names.add(name)
            if (name.substringAfterLast('/') == "module.prop") propEntries.add(entry)
        }
        if (propEntries.isEmpty()) throw InstallFailure("E_NO_MODULE_PROP")

        val candidates = propEntries.map { entry ->
            val name = entry.name.replace('\\', '/')
            val dir = name.substringBeforeLast('/', "")
            val prefix = if (dir.isEmpty()) "" else "$dir/"
            ZipCandidate(
                entry = entry,
                prefix = prefix,
                magiskMarker = names.contains("${prefix}META-INF/com/google/android/update-binary") ||
                    names.contains("META-INF/com/google/android/update-binary"),
                customizeSh = names.contains("${prefix}customize.sh")
            )
        }.sortedWith(compareByDescending<ZipCandidate> { it.score }.thenBy { it.prefix.length })

        val best = candidates.first()
        val text = try {
            zip.getInputStream(best.entry).use { it.readBytes().toString(Charsets.UTF_8) }
        } catch (e: Exception) {
            throw InstallFailure("E_EXTRACT_FAILED", "${best.entry.name}: ${e.message ?: e.javaClass.simpleName}")
        }
        return ZipLayout(
            modulePropEntry = best.entry.name.replace('\\', '/'),
            rootPrefix = best.prefix,
            // 有些包是 Windows 下打的，module.prop 带 BOM；不剥掉会解析不出 id
            modulePropText = text.removePrefix("\uFEFF"),
            magiskMarker = best.magiskMarker,
            customizeSh = best.customizeSh
        )
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

        return try {
            val archive = try {
                ZipFile(zipFile)
            } catch (e: ZipException) {
                return failure("E_ZIP_INVALID", e.message.orEmpty())
            } catch (e: IOException) {
                return failure("E_ZIP_INVALID", e.message.orEmpty())
            }

            archive.use { zip ->
                val layout = inspectZip(zip)
                val props = parseModuleProp(layout.modulePropText)
                val pluginId = props["id"]?.trim().orEmpty()
                if (pluginId.isEmpty()) throw InstallFailure("E_NO_MODULE_ID")
                if (!ID_PATTERN.matches(pluginId)) throw InstallFailure("E_ID_UNSAFE", pluginId)

                val temp = File(getPluginsDir(), "temp_${System.currentTimeMillis()}")
                tempDir = temp
                if (!temp.exists() && !temp.mkdirs()) throw InstallFailure("E_EXTRACT_FAILED", temp.absolutePath)
                extractZip(zip, temp)

                val rootDir = if (layout.rootPrefix.isEmpty()) temp else File(temp, layout.rootPrefix.removeSuffix("/"))
                if (!File(rootDir, "module.prop").isFile) {
                    throw InstallFailure("E_NO_MODULE_PROP", layout.modulePropEntry)
                }

                // 先在临时目录里拷好再整体换名，避免把半成品直接写进正式目录
                val staging = File(getPluginsDir(), "temp_install_${pluginId}_${System.currentTimeMillis()}")
                stagingDir = staging
                if (!rootDir.copyRecursively(staging, overwrite = true)) {
                    throw InstallFailure("E_COPY_FAILED", "${rootDir.absolutePath} -> ${staging.absolutePath}")
                }

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
                val customizeError = runCustomizeIfPresent(target)

                val plugin = PluginData(
                    id = pluginId,
                    name = props["name"]?.ifBlank { pluginId } ?: pluginId,
                    version = props["version"]?.ifBlank { "1.0" } ?: "1.0",
                    versionCode = props["versionCode"]?.trim()?.toIntOrNull() ?: 1,
                    author = props["author"]?.ifBlank { "Unknown" } ?: "Unknown",
                    description = props["description"].orEmpty(),
                    axeronPlugin = props["axeronPlugin"]?.trim()?.toIntOrNull() ?: 0,
                    isEnabled = !File(target, "disable").exists(),
                    isInstalled = true,
                    pluginDir = target.absolutePath,
                    hasWebUI = findWebUIEntry(target, props) != null,
                    hasAction = File(target, "action.sh").isFile,
                    updateTime = System.currentTimeMillis(),
                    size = formatDirSize(target)
                )

                val message = buildString {
                    append("E_OK_PLUGIN")
                    append('\n')
                    append(plugin.name)
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
            failure(f.code, f.detail)
        } catch (e: Exception) {
            if (moved) targetDir?.deleteRecursively()
            failure("E_EXCEPTION", "${e.javaClass.simpleName}: ${e.message.orEmpty()}")
        } finally {
            tempDir?.deleteRecursively()
            stagingDir?.deleteRecursively()
        }
    }

    /** 执行 customize.sh；没装、装好、失败三种情况分别返回 ""/""/真实原因。 */
    private fun runCustomizeIfPresent(moduleDir: File): String {
        val script = File(moduleDir, "customize.sh")
        if (!script.isFile) return ""
        return try {
            val dir = moduleDir.absolutePath
            val result = ADBTools.execCommand(
                "cd ${shq(dir)} && MODPATH=${shq(dir)} TMPDIR=${shq(dir)} sh customize.sh",
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
    fun runBootScripts(context: Context) {
        try {
            val plugins = getInstalledPlugins().filter { it.isEnabled }
            plugins.forEach { plugin ->
                val pluginDir = File(plugin.pluginDir)
                // 执行 post-fs-data.sh
                val postFsData = File(pluginDir, "post-fs-data.sh")
                if (postFsData.isFile) {
                    val dir = pluginDir.absolutePath
                    ADBTools.execCommand(
                        "cd ${shq(dir)} && MODDIR=${shq(dir)} sh post-fs-data.sh",
                        timeout = 60
                    )
                }
                // 执行 service.sh（后台执行）
                val serviceSh = File(pluginDir, "service.sh")
                if (serviceSh.isFile) {
                    val dir = pluginDir.absolutePath
                    Thread {
                        ADBTools.execCommand(
                            "cd ${shq(dir)} && MODDIR=${shq(dir)} sh service.sh",
                            timeout = 300
                        )
                    }.start()
                }
                // 加载 system.prop
                val systemProp = File(pluginDir, "system.prop")
                if (systemProp.isFile) {
                    parseModuleProp(systemProp).forEach { (key, value) ->
                        ADBTools.execCommand("setprop ${shq(key)} ${shq(value)}")
                    }
                }
            }
        } catch (e: Exception) {
        }
    }

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
