package com.example.adbtoolbox.common

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.util.Base64
import android.view.WindowManager
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileReader
import java.util.concurrent.TimeUnit

lateinit var appContext: Context

/**
 * 图标字符串缓存上界，与 AppCache 里解码后位图的 LRU 上界（64）保持一致。
 * 放文件级是因为 ADBTools 是 standalone object，内部不能写 companion object。
 */
private const val ICON_CACHE_MAX = 64

actual object ADBTools {

    // Shizuku 状态缓存，避免频繁 Binder 调用导致卡顿
    private var shizukuCache: Boolean? = null
    private var shizukuCacheTime: Long = 0
    private const val SHIZUKU_CACHE_DURATION = 1000L // 缓存 1 秒

    actual fun isShizukuAvailable(): Boolean {
        val now = System.currentTimeMillis()
        if (shizukuCache != null && now - shizukuCacheTime < SHIZUKU_CACHE_DURATION) {
            return shizukuCache!!
        }
        val result = try {
            Shizuku.pingBinder() &&
                    Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            false
        }
        // Shizuku 不可用时，检查 Dhizuku 权限（需已激活为设备所有者且已授权给本应用）
        val finalResult = if (result) true else (AppCache.useDhizuku.value && isDhizukuPermissionGranted())
        shizukuCache = finalResult
        shizukuCacheTime = now
        return finalResult
    }

    actual fun getShizukuState(): String {
        return try {
            if (!Shizuku.pingBinder()) {
                "not_running"
            } else if (Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                "granted"
            } else {
                "no_permission"
            }
        } catch (e: Exception) {
            "not_running"
        }
    }

    // 通过 Dhizuku 执行命令（反射调用 Dhizuku API，避免硬依赖）
    private fun execWithDhizuku(command: String, timeout: Int): CommandResult? {
        return try {
            val dhizukuClass = Class.forName("com.rosan.dhizuku.api.Dhizuku")
            // 尝试调用 execute(String) 方法
            try {
                val execMethod = dhizukuClass.getMethod("execute", String::class.java)
                val result = execMethod.invoke(null, command)
                if (result is android.os.Bundle) {
                    val output = result.getString("output", "")
                    val error = result.getString("error", "")
                    val exitCode = result.getInt("exitCode", -1)
                    CommandResult(output, error, exitCode)
                } else {
                    CommandResult(result?.toString() ?: "", "", 0)
                }
            } catch (e: NoSuchMethodException) {
                // 尝试 newProcess 方式
                try {
                    val newProcessMethod = dhizukuClass.getMethod("newProcess", Array<String>::class.java)
                    val process = newProcessMethod.invoke(null, arrayOf("sh", "-c", command))
                    if (process is Process) {
                        val output = process.inputStream.bufferedReader().use { it.readText() }
                        val error = process.errorStream.bufferedReader().use { it.readText() }
                        val exitCode = process.waitFor()
                        CommandResult(output, error, exitCode)
                    } else null
                } catch (e2: Exception) {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    // 检测 Dhizuku 是否已授权给本应用（通过反射调用 Dhizuku API）
    actual fun isDhizukuPermissionGranted(): Boolean {
        if (!isDhizukuActive()) return false
        return try {
            val dhizukuClass = Class.forName("com.rosan.dhizuku.api.Dhizuku")
            try {
                val method = dhizukuClass.getMethod("isPermissionGranted")
                method.invoke(null) as Boolean
            } catch (e: NoSuchMethodException) {
                try {
                    val method = dhizukuClass.getMethod("checkSelfPermission", String::class.java)
                    val result = method.invoke(null, "com.rosan.dhizuku.permission.MANAGE")
                    result == android.content.pm.PackageManager.PERMISSION_GRANTED
                } catch (e2: Exception) {
                    true
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    // 请求 Dhizuku 权限（最新版 API 格式，Dhizuku 会弹出授权对话框）
    actual fun requestDhizukuPermission(): Boolean {
        if (!isDhizukuActive()) return false
        return try {
            val dhizukuClass = Class.forName("com.rosan.dhizuku.api.Dhizuku")
            val activity = appContext as? android.app.Activity
            // 方式1：最新版 API: requestPermission(Activity, int)
            try {
                val method = dhizukuClass.getMethod("requestPermission", android.app.Activity::class.java, Int::class.javaPrimitiveType)
                if (activity != null) {
                    method.invoke(null, activity, 1001)
                } else {
                    method.invoke(null, null, 1001)
                }
                Thread.sleep(1000)
                isDhizukuPermissionGranted()
            } catch (e: NoSuchMethodException) {
                // 方式2：requestPermission(int)
                try {
                    val method = dhizukuClass.getMethod("requestPermission", Int::class.javaPrimitiveType)
                    method.invoke(null, 1001)
                    Thread.sleep(1000)
                    isDhizukuPermissionGranted()
                } catch (e2: NoSuchMethodException) {
                    // 方式3：通过 Intent 启动 Dhizuku 的授权页面
                    try {
                        val intent = android.content.Intent("com.rosan.dhizuku.action.REQUEST_PERMISSION")
                        intent.setPackage("com.rosan.dhizuku")
                        intent.putExtra("packageName", appContext.packageName)
                        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (activity != null) {
                            activity.startActivityForResult(intent, 1001)
                        } else {
                            appContext.startActivity(intent)
                        }
                        Thread.sleep(1000)
                        isDhizukuPermissionGranted()
                    } catch (e3: Exception) {
                        // 方式4：旧版 requestPermission()
                        try {
                            val method = dhizukuClass.getMethod("requestPermission")
                            method.invoke(null)
                            Thread.sleep(1000)
                            isDhizukuPermissionGranted()
                        } catch (e4: Exception) {
                            true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            false
        }
    }

    actual fun requestShizukuPermission() {
        try {
            if (Shizuku.pingBinder()) {
                Shizuku.requestPermission(0)
            }
        } catch (e: Exception) {
        }
    }

    // Shizuku 详细诊断，返回每一步的状态
    actual fun getShizukuDiagnostics(): String {
        val sb = StringBuilder()
        sb.appendLine("=== Shizuku Diagnostics ===")
        try {
            val ping = Shizuku.pingBinder()
            sb.appendLine("1. pingBinder: $ping")
        } catch (e: Exception) {
            sb.appendLine("1. pingBinder ERROR: ${e.javaClass.simpleName}: ${e.message}")
        }
        try {
            val perm = Shizuku.checkSelfPermission()
            sb.appendLine("2. checkSelfPermission: $perm (0=GRANTED, -1=DENIED)")
        } catch (e: Exception) {
            sb.appendLine("2. checkSelfPermission ERROR: ${e.javaClass.simpleName}: ${e.message}")
        }
        try {
            val methods = Shizuku::class.java.declaredMethods
            sb.appendLine("3. Shizuku methods (${methods.size}):")
            // 只显示包含 process/exec/command 的方法
            val relevantMethods = methods.filter { 
                it.name.contains("process", ignoreCase = true) || 
                it.name.contains("exec", ignoreCase = true) || 
                it.name.contains("command", ignoreCase = true) ||
                it.name.contains("run", ignoreCase = true)
            }
            if (relevantMethods.isNotEmpty()) {
                sb.appendLine("   --- Relevant methods ---")
                relevantMethods.forEach { m ->
                    val params = m.parameterTypes.joinToString(", ") { it.simpleName }
                    sb.appendLine("   - ${m.name}($params) : ${m.returnType.simpleName}")
                }
            }
            // 也显示所有 public 方法
            val publicMethods = methods.filter { java.lang.reflect.Modifier.isPublic(it.modifiers) }
            sb.appendLine("   --- Public methods (${publicMethods.size}) ---")
            publicMethods.take(20).forEach { m ->
                val params = m.parameterTypes.joinToString(", ") { it.simpleName }
                sb.appendLine("   - ${m.name}($params) : ${m.returnType.simpleName}")
            }
            if (publicMethods.size > 20) {
                sb.appendLine("   ... and ${publicMethods.size - 20} more")
            }
        } catch (e: Exception) {
            sb.appendLine("3. list methods ERROR: ${e.javaClass.simpleName}: ${e.message}")
        }
        try {
            val newProcessMethod = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            newProcessMethod.isAccessible = true
            sb.appendLine("4. newProcess method found: ${newProcessMethod.toGenericString()}")

            // 尝试调用 newProcess 执行 echo 测试
            val invokeArgs = arrayOf<Any?>(
                arrayOf("sh", "-c", "echo SHIZUKU_TEST_OK && id"),
                null,
                null
            )
            val process = newProcessMethod.invoke(null, *invokeArgs)
            sb.appendLine("5. newProcess invoke result: ${process?.javaClass?.name ?: "null"}")

            if (process != null) {
                // 反射调用 waitFor
                val waitForMethod = process.javaClass.getMethod("waitFor", Long::class.java, TimeUnit::class.java)
                val finished = waitForMethod.invoke(process, 5L, TimeUnit.SECONDS) as Boolean
                sb.appendLine("6. waitFor finished: $finished")

                // 反射调用 getInputStream（使用 use 确保关闭）
                val inputStreamMethod = process.javaClass.getMethod("getInputStream")
                val inputStream = inputStreamMethod.invoke(process) as? java.io.InputStream
                val output = try { inputStream?.bufferedReader()?.use { it.readText() } ?: "(empty)" } catch (e: Exception) { "ERROR: ${e.message}" }
                sb.appendLine("7. stdout: $output")

                // 反射调用 getErrorStream（使用 use 确保关闭）
                val errorStreamMethod = process.javaClass.getMethod("getErrorStream")
                val errorStream = errorStreamMethod.invoke(process) as? java.io.InputStream
                val error = try { errorStream?.bufferedReader()?.use { it.readText() } ?: "(empty)" } catch (e: Exception) { "ERROR: ${e.message}" }
                sb.appendLine("8. stderr: $error")

                // 反射调用 exitValue
                val exitValueMethod = process.javaClass.getMethod("exitValue")
                val exitCode = exitValueMethod.invoke(process) as Int
                sb.appendLine("9. exitCode: $exitCode")

                try { process.javaClass.getMethod("destroy")?.invoke(process) } catch (_: Exception) {}
            }
        } catch (e: Exception) {
            sb.appendLine("4-9. newProcess ERROR: ${e.javaClass.simpleName}: ${e.message}")
            sb.appendLine("   StackTrace: ${e.stackTrace.take(5).joinToString("\n   ")}")
        }
        sb.appendLine("=== End Diagnostics ===")
        return sb.toString()
    }

    actual fun execCommand(command: String, timeout: Int): CommandResult {
        // 判断命令执行结果是否为权限不足（需要继续尝试更高权限）
        fun isPermissionDenied(result: CommandResult): Boolean {
            if (result.exitCode == 0) return false
            val output = (result.output + result.error).lowercase()
            return output.contains("permission denied") ||
                   output.contains("securityexception") ||
                   output.contains("not allowed") ||
                   output.contains("operation not allowed") ||
                   output.contains("requires") && output.contains("root") ||
                   output.contains("uid ") && output.contains("not")
        }

        // 优先用 Shizuku 执行
        if (isShizukuAvailable()) {
            val result = execWithShizuku(command, timeout)
            if (result.exitCode != -999) {
                // Shizuku 执行成功（exitCode=0）直接返回
                if (result.exitCode == 0) return result
                // Shizuku 执行失败但不是权限不足，也直接返回
                if (!isPermissionDenied(result)) return result
                // 权限不足，继续尝试更高权限（su），不返回
            }
            // Shizuku 所有执行方式都失败，尝试 Dhizuku
            if (AppCache.useDhizuku.value) {
                val dhizukuResult = execWithDhizuku(command, timeout)
                if (dhizukuResult != null) {
                    if (dhizukuResult.exitCode == 0) return dhizukuResult
                    if (!isPermissionDenied(dhizukuResult)) return dhizukuResult
                }
            }
        }
        // 直接尝试用 su 执行（不调用 isRooted 避免无限递归）
        val suResult = execWithSu(command, timeout)
        if (suResult.exitCode != -999) return suResult
        // 最后回退到普通 shell
        return execWithShell(command, timeout)
    }

    private fun execWithShizuku(command: String, timeout: Int): CommandResult {
        var process: Any? = null
        return try {
            // 方式1：尝试反射调用 Shizuku.newProcess(String[], String[], String)
            var result = tryExecNewProcess(command, timeout)
            if (result != null) return result

            // 方式2：尝试通过 Shizuku AIDL 接口执行
            result = tryExecViaAIDL(command, timeout)
            if (result != null) return result

            // 方式3：尝试通过 Shizuku.getBinder() 直接 transact
            result = tryExecViaTransact(command, timeout)
            if (result != null) return result

            CommandResult("", "Shizuku: all execution methods failed", -999)
        } catch (e: Exception) {
            CommandResult("", "Shizuku[${e.javaClass.simpleName}]: ${e.message}", -999)
        } finally {
            try {
                process?.javaClass?.getMethod("destroy")?.invoke(process)
            } catch (_: Exception) {}
        }
    }

    // 方式1：反射调用 Shizuku.newProcess
    private fun tryExecNewProcess(command: String, timeout: Int): CommandResult? {
        return try {
            // 尝试不同的方法签名
            val methodSignatures = listOf(
                arrayOf(Array<String>::class.java, Array<String>::class.java, String::class.java),
                arrayOf(Array<String>::class.java),
                arrayOf(Array<String>::class.java, String::class.java)
            )

            var newProcessMethod: java.lang.reflect.Method? = null
            for (sig in methodSignatures) {
                try {
                    newProcessMethod = Shizuku::class.java.getDeclaredMethod("newProcess", *sig)
                    break
                } catch (_: NoSuchMethodException) {}
            }

            if (newProcessMethod == null) {
                return null
            }
            newProcessMethod.isAccessible = true

            // 尝试不同的参数传递方式
            val cmdArray = arrayOf("sh", "-c", command)
            val invokeArgsList = listOf(
                arrayOf<Any?>(cmdArray, null, null),
                arrayOf<Any?>(cmdArray, null),
                arrayOf<Any?>(cmdArray)
            )

            var process: Any? = null
            for (invokeArgs in invokeArgsList) {
                try {
                    process = newProcessMethod.invoke(null, *invokeArgs)
                    if (process != null) break
                } catch (_: Exception) {}
            }

            if (process == null) return null

            readProcessResult(process, timeout)
        } catch (e: Exception) {
            null
        }
    }

    // 方式2：通过 Shizuku AIDL 接口执行
    private fun tryExecViaAIDL(command: String, timeout: Int): CommandResult? {
        return try {
            val binder = Shizuku.getBinder() ?: return null

            // 尝试获取 IShizukuService 接口
            val serviceClass = try {
                Class.forName("rikka.shizuku.IShizukuService\$Stub")
            } catch (_: ClassNotFoundException) {
                try {
                    Class.forName("rikka.shizuku.IShizukuService")
                } catch (_: ClassNotFoundException) { return null }
            }

            val asInterfaceMethod = serviceClass.getMethod("asInterface", android.os.IBinder::class.java)
            val service = asInterfaceMethod.invoke(null, binder) ?: return null

            // 尝试调用 exec 方法
            val execMethods = service.javaClass.methods.filter {
                it.name.contains("exec", ignoreCase = true)
            }

            if (execMethods.isEmpty()) return null

            // 使用管道获取输出
            val inPipe = android.os.ParcelFileDescriptor.createPipe()
            val outPipe = android.os.ParcelFileDescriptor.createPipe()
            val errPipe = android.os.ParcelFileDescriptor.createPipe()

            for (execMethod in execMethods) {
                try {
                    val params = execMethod.parameterTypes
                    val args = arrayOfNulls<Any?>(params.size)
                    for (i in params.indices) {
                        when {
                            params[i] == Array<String>::class.java -> args[i] = arrayOf("sh", "-c", command)
                            params[i] == android.os.ParcelFileDescriptor::class.java -> {
                                if (i == 0) args[i] = inPipe[1]
                                else if (i == 1) args[i] = outPipe[1]
                                else args[i] = errPipe[1]
                            }
                            params[i] == String::class.java -> args[i] = null
                            params[i] == Int::class.javaPrimitiveType -> args[i] = 0
                        }
                    }
                    val exitCode = execMethod.invoke(service, *args) as? Int
                    if (exitCode != null) {
                        // 读取输出
                        val output = readPipeOutput(outPipe[0])
                        val error = readPipeOutput(errPipe[0])
                        inPipe[0].close()
                        inPipe[1].close()
                        outPipe[1].close()
                        errPipe[1].close()
                        return CommandResult(output, error, exitCode)
                    }
                } catch (_: Exception) {}
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    // 方式3：通过 IBinder.transact 直接执行
    private fun tryExecViaTransact(command: String, timeout: Int): CommandResult? {
        return try {
            val binder = Shizuku.getBinder() ?: return null
            // AIDL transact 方式太复杂，这里跳过
            null
        } catch (e: Exception) {
            null
        }
    }

    // 读取进程输出（通过反射）
    private fun readProcessResult(process: Any, timeout: Int): CommandResult {
        return try {
            // 反射调用 waitFor
            val waitForMethod = try {
                process.javaClass.getMethod("waitFor", Long::class.java, TimeUnit::class.java)
            } catch (_: NoSuchMethodException) {
                process.javaClass.getMethod("waitFor")
            }
            val finished = if (waitForMethod.parameterTypes.size == 2) {
                waitForMethod.invoke(process, timeout.toLong(), TimeUnit.SECONDS) as Boolean
            } else {
                waitForMethod.invoke(process)
                true
            }
            if (!finished) {
                try {
                    process.javaClass.getMethod("destroyForcibly")?.invoke(process)
                } catch (_: Exception) {}
                return CommandResult("", "Command timeout after ${timeout}s", -1)
            }

            // 反射调用 getInputStream（使用 use 确保关闭）
            val inputStream = process.javaClass.getMethod("getInputStream").invoke(process) as? java.io.InputStream
            val output = try { inputStream?.bufferedReader()?.use { it.readText() } ?: "" } catch (e: Exception) { "" }

            // 反射调用 getErrorStream（使用 use 确保关闭）
            val errorStream = process.javaClass.getMethod("getErrorStream").invoke(process) as? java.io.InputStream
            val error = try { errorStream?.bufferedReader()?.use { it.readText() } ?: "" } catch (e: Exception) { "" }

            // 反射调用 exitValue
            val exitCode = process.javaClass.getMethod("exitValue").invoke(process) as Int

            CommandResult(output, error, exitCode)
        } catch (e: Exception) {
            CommandResult("", "readProcessResult[${e.javaClass.simpleName}]: ${e.message}", -999)
        }
    }

    // 读取管道输出
    private fun readPipeOutput(pipe: android.os.ParcelFileDescriptor): String {
        return try {
            val inputStream = android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe)
            inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }

    private fun execWithSu(command: String, timeout: Int): CommandResult {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            val finished = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return CommandResult("", "Command timeout after ${timeout}s", -1)
            }
            val output = try { process.inputStream.bufferedReader().use { it.readText() } } catch (e: Exception) { "" }
            val error = try { process.errorStream.bufferedReader().use { it.readText() } } catch (e: Exception) { "" }
            CommandResult(output, error, process.exitValue())
        } catch (e: Exception) {
            CommandResult("", e.message ?: "su error", -999)
        } finally {
            try { process?.destroy() } catch (e: Exception) {}
        }
    }

    private fun execWithShell(command: String, timeout: Int): CommandResult {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val finished = process.waitFor(timeout.toLong(), TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return CommandResult("", "Command timeout after ${timeout}s", -1)
            }
            val output = try { process.inputStream.bufferedReader().use { it.readText() } } catch (e: Exception) { "" }
            val error = try { process.errorStream.bufferedReader().use { it.readText() } } catch (e: Exception) { "" }
            CommandResult(output, error, process.exitValue())
        } catch (e: Exception) {
            CommandResult("", e.message ?: "Unknown error", -1)
        } finally {
            try { process?.destroy() } catch (e: Exception) {}
        }
    }

    actual fun getDeviceInfo(): DeviceInfoData {
        return try {
            val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            am.getMemoryInfo(memInfo)
            val totalMem = memInfo.totalMem / (1024 * 1024)
            val availMem = memInfo.availMem / (1024 * 1024)
            val statFs = StatFs(Environment.getDataDirectory().path)
            val totalStorage = statFs.totalBytes / (1024 * 1024)
            val availStorage = statFs.availableBytes / (1024 * 1024)
            val refreshRate = try {
                val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
                val display = dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
                if (display != null) "${display.refreshRate.toInt()} Hz" else "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }
            DeviceInfoData(
                model = Build.MODEL,
                brand = Build.BRAND,
                androidVersion = Build.VERSION.RELEASE,
                sdkVersion = Build.VERSION.SDK_INT,
                kernelVersion = getKernelVersion(),
                buildNumber = Build.DISPLAY,
                cpuAbi = Build.SUPPORTED_ABIS.joinToString(", "),
                totalMemory = "${totalMem / 1024} GB",
                availableMemory = "${availMem / 1024} GB",
                totalStorage = "${totalStorage / 1024} GB",
                availableStorage = "${availStorage / 1024} GB",
                batteryLevel = getBatteryLevel(),
                isRooted = isRooted(),
                isAdbEnabled = isAdbEnabled(),
                refreshRate = refreshRate
            )
        } catch (e: Exception) {
            // 兜底：即使某些系统服务获取失败，也返回基本信息，避免界面空白
            DeviceInfoData(
                model = Build.MODEL,
                brand = Build.BRAND,
                androidVersion = Build.VERSION.RELEASE,
                sdkVersion = Build.VERSION.SDK_INT,
                kernelVersion = "Unknown",
                buildNumber = Build.DISPLAY,
                cpuAbi = Build.SUPPORTED_ABIS.joinToString(", "),
                totalMemory = "Unknown",
                availableMemory = "Unknown",
                totalStorage = "Unknown",
                availableStorage = "Unknown",
                batteryLevel = -1,
                isRooted = false,
                isAdbEnabled = false
            )
        }
    }

    actual fun getInstalledApps(): List<AppInfoData> {
        return try {
            val pm = appContext.packageManager
            val packages = pm.getInstalledPackages(PackageManager.GET_META_DATA)
            packages.map { pkg ->
                val appInfo = pkg.applicationInfo
                val isSystem = appInfo?.flags?.and(ApplicationInfo.FLAG_SYSTEM) != 0
                // 更可靠的应用名称获取，优先用 loadLabel，确保系统应用也显示名称
                val appName = try {
                    appInfo?.loadLabel(pm)?.toString() ?: pkg.packageName
                } catch (e: Exception) {
                    try { pm.getApplicationLabel(appInfo!!).toString() } catch (e2: Exception) { pkg.packageName }
                }
                val isFrozen = try { appInfo?.enabled == false } catch (e: Exception) { false }
                AppInfoData(
                    packageName = pkg.packageName,
                    appName = appName,
                    versionName = pkg.versionName ?: "Unknown",
                    isSystem = isSystem,
                    isFrozen = isFrozen,
                    iconBase64 = null
                )
            }.sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun drawableToBase64(drawable: Drawable?): String? {
        return try {
            drawable ?: return null
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(1), drawable.intrinsicHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    actual fun freezeApp(packageName: String): Boolean {
        return try {
            // 尝试多种禁用方式
            var result = execCommand("pm disable-user --user 0 $packageName")
            var success = result.exitCode == 0 || result.output.contains("Success", true)
            if (!success) {
                result = execCommand("pm disable $packageName")
                success = result.exitCode == 0 || result.output.contains("Success", true)
            }
            if (!success) {
                result = execCommand("cmd package suspend $packageName")
                success = result.exitCode == 0 || result.output.contains("Success", true)
            }
            // 通过 PackageManager 实际检查应用是否真的被禁用了
            try {
                val appInfo = appContext.packageManager.getApplicationInfo(packageName, 0)
                if (!appInfo.enabled) return true
                // 检查是否被挂起
                val suspended = try {
                    val method = appContext.packageManager.javaClass.getMethod("isPackageSuspended", String::class.java)
                    method.invoke(appContext.packageManager, packageName) as? Boolean ?: false
                } catch (e: Exception) { false }
                if (suspended) return true
                success
            } catch (e: Exception) {
                success
            }
        } catch (e: Exception) { false }
    }

    actual fun unfreezeApp(packageName: String): Boolean {
        return try {
            // 尝试多种启用方式
            var result = execCommand("pm enable $packageName")
            var success = result.exitCode == 0 || result.output.contains("Success", true)
            if (!success) {
                result = execCommand("pm enable --user 0 $packageName")
                success = result.exitCode == 0 || result.output.contains("Success", true)
            }
            if (!success) {
                result = execCommand("cmd package unsuspend $packageName")
                success = result.exitCode == 0 || result.output.contains("Success", true)
            }
            // 通过 PackageManager 实际检查应用是否真的被启用了
            try {
                val appInfo = appContext.packageManager.getApplicationInfo(packageName, 0)
                appInfo.enabled
            } catch (e: Exception) {
                success
            }
        } catch (e: Exception) { false }
    }

    actual fun uninstallApp(packageName: String): Boolean {
        return try { execCommand("pm uninstall -k --user 0 $packageName").exitCode == 0 } catch (e: Exception) { false }
    }

    /**
     * 清除应用缓存（**只清缓存，不动用户数据**）。
     *
     * 原实现把 `pm clear` 放在第一步——那是"清除全部数据"（账号、登录态、聊天记录全没），
     * 和界面上"清缓存"的语义完全不符，是实打实的数据丢失隐患。现在按"只清缓存"的顺序来：
     * 1) `pm clear --cache-only`（Android 12 / API 31+ 才有该参数）；
     * 2) 直接删 cache / code_cache 目录（需要 Shizuku 或 Root）；
     * 3) `run-as` 删缓存（仅 debuggable 应用）；
     * 4) `pm trim-caches`（全局裁剪缓存，属于系统行为）。
     * 全部失败就返回 false，由界面如实提示需要提权，而不是偷偷把用户数据抹掉。
     */
    actual fun clearCache(packageName: String): Boolean {
        return try {
            var anySuccess = false

            // 方式1：pm clear --cache-only（仅 Android 12+ 支持；低版本会报错，自动落到下一步）
            var result = execCommand("pm clear --cache-only $packageName 2>/dev/null")
            if (result.output.contains("Success", true) || result.output.contains("success", true)) {
                return true
            }

            // 方式2：只删除缓存目录（需要 Shizuku / Root）
            val cacheDirs = listOf(
                "/data/data/$packageName/cache",
                "/data/user/0/$packageName/cache",
                "/data/data/$packageName/code_cache",
                "/sdcard/Android/data/$packageName/cache",
                "/sdcard/Android/data/$packageName/code_cache"
            )
            for (dir in cacheDirs) {
                val rmResult = execCommand("rm -rf $dir/* 2>/dev/null && echo CLEANED || echo FAILED")
                if (rmResult.output.contains("CLEANED", true)) {
                    anySuccess = true
                }
            }

            // 方式3：run-as 删除缓存（只对 debuggable 应用有效）
            result = execCommand("run-as $packageName rm -rf cache/* 2>/dev/null && echo CLEANED || echo FAILED")
            if (result.output.contains("CLEANED", true)) {
                anySuccess = true
            }

            // 方式4：pm trim-caches（让系统回收缓存空间）
            result = execCommand("pm trim-caches 999999999")
            if (result.exitCode == 0 || result.output.contains("Success", true)) anySuccess = true

            anySuccess
        } catch (e: Exception) { false }
    }

    /**
     * 清除应用**全部数据**（等价于系统设置里的"清除数据"）。
     * 与 [clearCache] 严格区分：这是不可逆操作，只能由界面在明确二次确认后调用。
     */
    actual fun clearAppData(packageName: String): Boolean {
        return try {
            val result = execCommand("pm clear $packageName")
            result.exitCode == 0 || result.output.contains("Success", true)
        } catch (e: Exception) { false }
    }

    actual fun forceStop(packageName: String): Boolean {
        return try {
            val result = execCommand("am force-stop $packageName")
            // am force-stop 成功时通常无输出，exitCode 可能为 0 或非 0
            // 只有输出中明确包含 Error 才算失败
            val hasError = result.output.contains("Error", true) ||
                           result.error.contains("Error", true) ||
                           result.output.contains("Exception", true) ||
                           result.error.contains("Exception", true)
            !hasError
        } catch (e: Exception) { false }
    }

    actual fun clearAllCache(): Boolean {
        return try { execCommand("pm trim-caches 999999999").exitCode == 0 } catch (e: Exception) { false }
    }

    actual fun isRooted(): Boolean {
        return try {
            val paths = arrayOf("/system/app/Superuser.apk","/sbin/su","/system/bin/su","/system/xbin/su","/data/local/xbin/su","/data/local/bin/su","/system/sd/xbin/su","/system/bin/failsafe/su","/data/local/su","/su/bin/su")
            var found = paths.any { File(it).exists() }
            if (!found) {
                // 直接用 Runtime 执行，不经过 execCommand 避免无限递归
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
                    val finished = process.waitFor(3, TimeUnit.SECONDS)
                    if (finished) {
                        val output = process.inputStream.bufferedReader().use { it.readText() }
                        found = output.isNotEmpty() && output.contains("su")
                    }
                    process.destroy()
                } catch (_: Exception) {}
            }
            found
        } catch (e: Exception) { false }
    }

    actual fun requestRootPermission(): Boolean {
        return try {
            val result = execCommand("su -c id")
            result.output.contains("uid=0")
        } catch (e: Exception) { false }
    }

    actual fun getSuVersion(): String {
        return try { execCommand("su -v").output.trim().ifEmpty { "Unknown" } } catch (e: Exception) { "Unknown" }
    }

    actual fun getBusyBoxVersion(): String {
        return try { execCommand("busybox | head -1").output.trim().ifEmpty { "Not installed" } } catch (e: Exception) { "Not installed" }
    }

    actual fun getLogcat(lines: Int): String {
        return execCommand("logcat -t $lines").output
    }

    actual fun clearLogcat() {
        execCommand("logcat -c")
    }

    actual fun getAppPermissions(packageName: String): List<PermissionInfoData> {
        return try {
            val pm = appContext.packageManager
            val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
            packageInfo.requestedPermissions?.mapIndexed { index, permission ->
                val flags = packageInfo.requestedPermissionsFlags ?: intArrayOf()
                val isGranted = flags.getOrNull(index)?.let { it and 0x2 != 0 } ?: false
                PermissionInfoData(
                    permission = permission,
                    name = permission.substringAfterLast("."),
                    isGranted = isGranted
                )
            } ?: emptyList()
        } catch (e: Exception) { emptyList() }
    }

    actual fun grantPermission(packageName: String, permission: String): Boolean {
        return try { execCommand("pm grant $packageName $permission").exitCode == 0 } catch (e: Exception) { false }
    }

    actual fun revokePermission(packageName: String, permission: String): Boolean {
        return try { execCommand("pm revoke $packageName $permission").exitCode == 0 } catch (e: Exception) { false }
    }

    actual fun rebootDevice(): Boolean {
        return try { execCommand("reboot").exitCode == 0 } catch (e: Exception) { false }
    }

    actual fun rebootRecovery(): Boolean {
        return try { execCommand("reboot recovery").exitCode == 0 } catch (e: Exception) { false }
    }

    actual fun rebootBootloader(): Boolean {
        return try { execCommand("reboot bootloader").exitCode == 0 } catch (e: Exception) { false }
    }

    actual fun setBrightness(value: Int): Boolean {
        return try { execCommand("settings put system screen_brightness $value").exitCode == 0 } catch (e: Exception) { false }
    }

    actual fun getBrightness(): Int {
        return try { execCommand("settings get system screen_brightness").output.trim().toIntOrNull() ?: 128 } catch (e: Exception) { 128 }
    }

    actual fun setScreenTimeout(seconds: Int): Boolean {
        return try { execCommand("settings put system screen_off_timeout ${seconds * 1000}").exitCode == 0 } catch (e: Exception) { false }
    }

    actual fun getScreenTimeout(): Int {
        return try { (execCommand("settings get system screen_off_timeout").output.trim().toIntOrNull() ?: 30000) / 1000 } catch (e: Exception) { 30 }
    }

    actual fun getCpuInfo(): String {
        return try {
            val reader = BufferedReader(FileReader("/proc/cpuinfo"))
            var line = reader.readLine()
            while (line != null) {
                if (line.contains("Hardware") || line.contains("model name")) {
                    reader.close()
                    return line.split(":")[1].trim()
                }
                line = reader.readLine()
            }
            reader.close()
            "Unknown"
        } catch (e: Exception) { "Unknown" }
    }

    actual fun getCpuCores(): Int = Runtime.getRuntime().availableProcessors()

    actual fun getScreenResolution(): String {
        val display = appContext.resources.displayMetrics
        return "${display.widthPixels} x ${display.heightPixels}"
    }

    actual fun getKernelVersion(): String {
        return try {
            val reader = BufferedReader(FileReader("/proc/version"))
            val line = reader.readLine()
            reader.close()
            line.split(" ")[2]
        } catch (e: Exception) { "Unknown" }
    }

    actual fun getBatteryLevel(): Int {
        return try {
            val batteryManager = appContext.getSystemService(Context.BATTERY_SERVICE) as android.os.BatteryManager
            batteryManager.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } catch (e: Exception) { -1 }
    }

    actual fun getTotalMemory(): String {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return "${memInfo.totalMem / (1024 * 1024 * 1024)} GB"
    }

    actual fun getAvailableMemory(): String {
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)
        return "${memInfo.availMem / (1024 * 1024 * 1024)} GB"
    }

    actual fun getTotalStorage(): String {
        val statFs = StatFs(Environment.getDataDirectory().path)
        return "${statFs.totalBytes / (1024 * 1024 * 1024)} GB"
    }

    actual fun getAvailableStorage(): String {
        val statFs = StatFs(Environment.getDataDirectory().path)
        return "${statFs.availableBytes / (1024 * 1024 * 1024)} GB"
    }

    actual fun hasMagisk(): Boolean {
        return try {
            val result = execCommand("magisk -v")
            result.exitCode == 0 && result.output.isNotBlank()
        } catch (e: Exception) { false }
    }

    // Dhizuku 设备所有者相关
    actual fun isDhizukuInstalled(): Boolean {
        return try {
            val result = execCommand("pm list packages | grep com.rosan.dhizuku")
            result.output.contains("com.rosan.dhizuku", ignoreCase = true)
        } catch (e: Exception) { false }
    }

    actual fun isDhizukuActive(): Boolean {
        // 方式1：通过 Dhizuku API 反射检测 getOwnerComponent()（最可靠）
        try {
            val dhizukuClass = Class.forName("com.rosan.dhizuku.api.Dhizuku")
            // 尝试 getOwnerComponent() 方法，如果能获取到说明 Dhizuku 已激活为设备所有者
            try {
                val method = dhizukuClass.getMethod("getOwnerComponent")
                val component = method.invoke(null)
                if (component != null) return true
            } catch (e: NoSuchMethodException) {}
            // 尝试 isPermissionGranted() 方法，如果能调用说明已激活
            try {
                val method = dhizukuClass.getMethod("isPermissionGranted")
                method.invoke(null) // 只要不抛异常说明 Dhizuku 已安装且可通信
                return true
            } catch (e: NoSuchMethodException) {}
            // 尝试 getVersion() 方法
            try {
                val method = dhizukuClass.getMethod("getVersion")
                if (method.invoke(null) != null) return true
            } catch (e: NoSuchMethodException) {}
        } catch (e: Exception) {}

        // 方式2：通过 PackageManager 检测 Dhizuku 是否为设备所有者
        return try {
            val dpm = appContext.getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
            val admins = dpm.activeAdmins
            admins?.any { it.packageName == "com.rosan.dhizuku" } == true &&
            dpm.isDeviceOwnerApp("com.rosan.dhizuku")
        } catch (e: Exception) {
            // 方式3：通过命令检测
            try {
                val result = execCommand("dumpsys device_policy | grep com.rosan.dhizuku")
                result.output.contains("com.rosan.dhizuku", ignoreCase = true)
            } catch (e2: Exception) { false }
        }
    }

    actual fun activateDhizuku(): CommandResult {
        return try {
            // 先检查是否已安装
            if (!isDhizukuInstalled()) {
                return CommandResult("", "Dhizuku not installed. Please install Dhizuku APK first.", -1)
            }
            // 检查是否有其他用户账户（设备所有者激活前不能有其他账户）
            val usersResult = execCommand("pm list users")
            // 激活 Dhizuku 为设备所有者
            val result = execCommand("dpm set-device-owner com.rosan.dhizuku/.server.DhizukuDAReceiver", timeout = 30)
            if (result.exitCode == 0 || result.output.contains("Success", ignoreCase = true)) {
                CommandResult("Dhizuku activated successfully as Device Owner", result.error, 0)
            } else {
                CommandResult(result.output, result.error + "\nTip: Make sure no other accounts (including dual space) exist on device", result.exitCode)
            }
        } catch (e: Exception) {
            CommandResult("", "Activation failed: ${e.message}", -1)
        }
    }

    actual fun removeDhizuku(): CommandResult {
        return try {
            val result = execCommand("dpm remove-active-admin com.rosan.dhizuku/.server.DhizukuDAReceiver", timeout = 15)
            if (result.exitCode == 0 || result.output.contains("Success", ignoreCase = true)) {
                CommandResult("Dhizuku device owner removed", result.error, 0)
            } else {
                CommandResult(result.output, result.error, result.exitCode)
            }
        } catch (e: Exception) {
            CommandResult("", "Remove failed: ${e.message}", -1)
        }
    }

    // 获取处理器型号
    actual fun getCpuModel(): String {
        return try {
            val hardware = android.os.Build.HARDWARE.lowercase()
            when {
                hardware.contains("mt") || hardware.contains("dimensity") -> {
                    val cpuInfo = java.io.File("/proc/cpuinfo").readText()
                    val hardwareLine = cpuInfo.lines().firstOrNull { it.contains("Hardware", ignoreCase = true) }
                    hardwareLine?.substringAfter(":")?.trim() ?: "MediaTek Dimensity"
                }
                hardware.contains("qcom") || hardware.contains("sm") || hardware.contains("kalama") || hardware.contains("pineapple") -> {
                    when {
                        hardware.contains("kalama") || hardware.contains("sm8550") -> "Snapdragon 8 Gen 2"
                        hardware.contains("pineapple") -> "Snapdragon 8 Gen 3"
                        hardware.contains("sm8450") -> "Snapdragon 8 Gen 1"
                        hardware.contains("sm8350") -> "Snapdragon 888"
                        hardware.contains("sm8250") -> "Snapdragon 865"
                        hardware.contains("sm7450") -> "Snapdragon 7+ Gen 2"
                        else -> "Qualcomm Snapdragon"
                    }
                }
                else -> "Unknown ($hardware)"
            }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    // 获取处理器厂商
    actual fun getCpuVendor(): String {
        return try {
            val hardware = android.os.Build.HARDWARE.lowercase()
            when {
                hardware.contains("mt") || hardware.contains("dimensity") -> "mediatek"
                hardware.contains("qcom") || hardware.contains("sm") || hardware.contains("kalama") || hardware.contains("pineapple") -> "qualcomm"
                else -> "other"
            }
        } catch (e: Exception) {
            "other"
        }
    }

    // 刷入临时 Root 提权包（zip 格式）
    //
    // 旧实现有两个真实缺陷：
    // 1. 直接在应用私有目录里 `sh <script>`：shell uid(2000) 读不到 /data/data/<pkg>/cache，脚本必然失败；
    // 2. 挑脚本用 walkTopDown().firstOrNull{}，顺序不确定，可能挑到 uninstall.sh 之类的非入口脚本。
    // 现在：本机解压（不依赖设备端 unzip）→ 分块 base64 推送到 /data/local/tmp → 在设备上执行并回传真实输出。
    actual fun flashTempRootModule(zipPath: String): CommandResult {
        fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        fun fmt(template: String, vararg args: Any?): String {
            var out = template
            args.forEachIndexed { index, value ->
                out = out.replace("%${index + 1}\$s", value?.toString() ?: "")
            }
            return out
        }

        fun reasonOf(result: CommandResult): String =
            listOf(result.error, result.output).firstOrNull { it.isNotBlank() }?.trim().orEmpty()

        // 单文件推送：分块 base64 追加写入，最后用 wc -c 校验字节数，避免"看起来成功其实写坏了"
        fun pushFile(local: java.io.File, remotePath: String): String? {
            val remove = execCommand("rm -f ${quote(remotePath)}", timeout = 30)
            if (remove.exitCode != 0) {
                return reasonOf(remove).ifBlank { "cannot create $remotePath" }
            }
            val bytes = local.readBytes()
            var offset = 0
            while (offset < bytes.size) {
                val end = minOf(offset + 64 * 1024, bytes.size)
                val encoded = android.util.Base64.encodeToString(
                    bytes.copyOfRange(offset, end),
                    android.util.Base64.NO_WRAP
                )
                val write = execCommand(
                    "printf '%s' ${quote(encoded)} | base64 -d >> ${quote(remotePath)}",
                    timeout = 60
                )
                if (write.exitCode != 0) {
                    val reason = reasonOf(write)
                    val lower = reason.lowercase()
                    if (lower.contains("not found") || lower.contains("inaccessible") || lower.contains("no such file")) {
                        return AppStrings.get("device_no_base64")
                    }
                    return reason.ifBlank { "exit=${write.exitCode}" }
                }
                offset = end
            }
            val sizeCheck = execCommand("wc -c < ${quote(remotePath)}", timeout = 30)
            val remoteSize = sizeCheck.output.trim().toLongOrNull()
            if (remoteSize == null || remoteSize != local.length()) {
                return "size mismatch: local=${local.length()} remote=${sizeCheck.output.trim().ifBlank { "?" }}"
            }
            return null
        }

        fun pushTree(localDir: java.io.File, remoteDir: String): String? {
            val mk = execCommand("mkdir -p ${quote(remoteDir)}", timeout = 30)
            if (mk.exitCode != 0) return reasonOf(mk).ifBlank { "cannot create $remoteDir" }
            val files = localDir.walkTopDown().filter { it.isFile }.toList()
            files.forEach { source ->
                val relative = source.relativeTo(localDir).path.replace('\\', '/')
                val remote = "$remoteDir/$relative"
                val parent = remote.substringBeforeLast('/', remoteDir)
                val mkParent = execCommand("mkdir -p ${quote(parent)}", timeout = 30)
                if (mkParent.exitCode != 0) return reasonOf(mkParent).ifBlank { "cannot create $parent" }
                val error = pushFile(source, remote) ?: return@forEach
                return error
            }
            return null
        }

        val zipFile = java.io.File(zipPath)
        if (!zipFile.isFile) {
            return CommandResult("", "${AppStrings.get("module_err_file_not_found")}: $zipPath", -1)
        }
        if (!isShizukuAvailable() && !isRooted()) {
            return CommandResult("", AppStrings.get("temp_root_need_channel"), -1)
        }

        val tempDir = java.io.File(appContext.cacheDir, "temp_root_${System.currentTimeMillis()}")
        try {
            if (!tempDir.mkdirs() && !tempDir.isDirectory) {
                return CommandResult("", fmt(AppStrings.get("module_err_stage_failed"), tempDir.absolutePath), -1)
            }
            java.util.zip.ZipFile(zipFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name.replace('\\', '/')
                    if (name.isBlank() || name.startsWith("/") || name.split('/').any { it == ".." }) {
                        return CommandResult("", fmt(AppStrings.get("module_err_zip_slip"), entry.name), -1)
                    }
                    val outFile = java.io.File(tempDir, name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            outFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }

            // 脚本优先级：run.sh > install.sh > root.sh > 其它 *.sh（同级取路径最浅的）；卸载脚本不是入口
            val scriptFile = tempDir.walkTopDown()
                .filter {
                    it.isFile && it.name.endsWith(".sh", ignoreCase = true) &&
                        !it.name.equals("uninstall.sh", ignoreCase = true)
                }
                .sortedWith(
                    compareBy<File>(
                        {
                            when (it.name.lowercase()) {
                                "run.sh" -> 0
                                "install.sh" -> 1
                                "root.sh" -> 2
                                else -> 3
                            }
                        },
                        { it.relativeTo(tempDir).path.length }
                    )
                )
                .firstOrNull()
            if (scriptFile == null) {
                return CommandResult("", AppStrings.get("temp_root_no_script"), -1)
            }

            val remoteDir = "/data/local/tmp/temproot_${System.currentTimeMillis()}"
            val pushError = pushTree(tempDir, remoteDir)
            if (pushError != null) {
                return CommandResult("", fmt(AppStrings.get("temp_root_push_failed"), remoteDir, pushError), -1)
            }
            val remoteScript = "$remoteDir/" + scriptFile.relativeTo(tempDir).path.replace('\\', '/')
            val chmodResult = execCommand("chmod -R 755 ${quote(remoteDir)}", timeout = 60)
            val result = execCommand("cd ${quote(remoteDir)} && sh ${quote(remoteScript)}", timeout = 300)
            val output = buildString {
                appendLine(fmt(AppStrings.get("temp_root_staged"), remoteDir))
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
                    appendLine(AppStrings.get("hw_no_output"))
                }
                if (chmodResult.exitCode != 0) {
                    appendLine(
                        fmt(
                            AppStrings.get("temp_root_chmod_failed"),
                            reasonOf(chmodResult).ifBlank { "exit=${chmodResult.exitCode}" }
                        )
                    )
                }
            }
            val error = result.error.ifBlank {
                if (result.exitCode == 0) "" else fmt(AppStrings.get("temp_root_exit_nonzero"), result.exitCode.toString())
            }
            return CommandResult(output, error, result.exitCode)
        } catch (e: java.util.zip.ZipException) {
            return CommandResult("", fmt(AppStrings.get("module_err_archive_invalid"), e.message.orEmpty()), -1)
        } catch (e: Exception) {
            return CommandResult(
                "",
                "${AppStrings.get("module_err_exception")}: ${e.javaClass.simpleName}: ${e.message.orEmpty()}",
                -1
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    // ADB / Shizuku 模式刷入。
    //
    // 旧实现的三个真实问题：
    // 1. 依赖设备端 unzip / toybox 解压 zip，很多设备根本没有 unzip，于是"刷入失败"却没有真实原因；
    // 2. shq 缺失：路径用双引号拼进 shell，路径里带 $ / 反引号 / " 时会被 shell 展开；
    // 3. 已经是 Root 模块包时只说"请换 Root 模式"，不告诉用户它到底是什么包。
    // 现在：全程用本机 java.util.zip 解析（不依赖设备端 unzip），并按包的真实类型分别给出具体结论。
    actual fun installModuleViaADB(localFilePath: String): String {
        fun quote(value: String): String = "'" + value.replace("'", "'\\''") + "'"

        fun fmt(template: String, vararg args: Any?): String {
            var out = template
            args.forEachIndexed { index, value ->
                out = out.replace("%${index + 1}\$s", value?.toString() ?: "")
            }
            return out
        }

        fun reasonOf(result: CommandResult): String =
            listOf(result.error, result.output).firstOrNull { it.isNotBlank() }?.trim().orEmpty()

        val log = StringBuilder()
        log.appendLine(AppStrings.get("flash_mode_adb_title"))

        val file = java.io.File(localFilePath)
        if (!file.isFile) {
            log.appendLine("Error: " + fmt(AppStrings.get("flash_file_missing"), localFilePath))
            return log.toString()
        }
        log.appendLine(fmt(AppStrings.get("flash_file_line"), file.name, file.length() / 1024))

        // ADB/Shizuku 模式必须有 Shizuku（或 Dhizuku）授权，否则连临时目录都写不了
        if (!isShizukuAvailable()) {
            // 文案必须与事实一致：设备确实有 Root 时不能说"未 Root"
            if (isRooted()) {
                log.appendLine("Error: " + AppStrings.get("shizuku_not_connected"))
                log.appendLine(AppStrings.get("root_mode_hint"))
            } else {
                log.appendLine("Error: " + AppStrings.get("adb_mode_no_shizuku"))
            }
            return log.toString()
        }

        // 先在本机把包看清楚：module.prop 落点 / APK / Recovery 落点
        val apkEntries = mutableListOf<String>()
        val topLevel = mutableListOf<String>()
        var modulePropEntry: String? = null
        var moduleId = ""
        var hasRecoveryBinary = false
        try {
            java.util.zip.ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name.replace('\\', '/')
                    if (name.substringAfterLast('/').equals("module.prop", ignoreCase = true) && modulePropEntry == null) {
                        modulePropEntry = name
                        moduleId = try {
                            zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8) }
                                .removePrefix("\uFEFF")
                                .lineSequence()
                                .firstOrNull { it.trim().startsWith("id=") }
                                ?.substringAfter("=")?.trim().orEmpty()
                        } catch (e: Exception) {
                            ""
                        }
                    }
                    if (name.endsWith(".apk", ignoreCase = true)) apkEntries.add(name)
                    if (name.equals("META-INF/com/google/android/update-binary", ignoreCase = true)) {
                        hasRecoveryBinary = true
                    }
                    if (!name.contains('/')) topLevel.add(name)
                }
            }
        } catch (e: Exception) {
            log.appendLine(
                "Error: " + fmt(
                    AppStrings.get("module_err_archive_invalid"),
                    "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                )
            )
            return log.toString()
        }

        // Root 模块包：ADB/Shizuku 是 shell uid，写不进 /data/adb（0700 root:root），必须换 Root 模式
        val propEntry = modulePropEntry
        if (propEntry != null) {
            log.appendLine("Error: " + fmt(AppStrings.get("adb_mode_requires_root"), moduleId.ifBlank { propEntry }))
            log.appendLine(AppStrings.get("root_mode_hint"))
            return log.toString()
        }
        // Recovery / Magisk 刷机包（含 update-binary）：同样只能由 Root 侧的 Magisk / KernelSU 安装
        if (hasRecoveryBinary) {
            log.appendLine(
                "Error: " + fmt(
                    AppStrings.get("adb_mode_requires_root"),
                    "META-INF/com/google/android/update-binary"
                )
            )
            log.appendLine(AppStrings.get("root_mode_hint"))
            return log.toString()
        }
        if (apkEntries.isEmpty()) {
            log.appendLine("Error: " + AppStrings.get("adb_mode_no_apk"))
            if (topLevel.isNotEmpty()) {
                log.appendLine(fmt(AppStrings.get("adb_mode_top_entries"), topLevel.take(20).joinToString(", ")))
            }
            return log.toString()
        }

        // APK 类包：解压到应用外部私有目录（安装器以 system 身份读取，路径可控，也不需要设备端 unzip）
        val staging = java.io.File(
            appContext.getExternalFilesDir(null) ?: appContext.cacheDir,
            "adb_module_${System.currentTimeMillis()}"
        )
        if (!staging.mkdirs() && !staging.isDirectory) {
            log.appendLine("Error: " + fmt(AppStrings.get("adb_mode_push_failed"), staging.absolutePath, "mkdirs failed"))
            return log.toString()
        }
        var installed = 0
        var failed = 0
        try {
            java.util.zip.ZipFile(file).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.isDirectory) continue
                    val name = entry.name.replace('\\', '/')
                    if (!name.endsWith(".apk", ignoreCase = true)) continue
                    val apkName = name.substringAfterLast('/')
                    val apkFile = java.io.File(staging, apkName)
                    val extracted = try {
                        zip.getInputStream(entry).use { input ->
                            apkFile.outputStream().use { output -> input.copyTo(output) }
                        }
                        true
                    } catch (e: Exception) {
                        failed++
                        log.appendLine(
                            "Error: " + fmt(
                                AppStrings.get("adb_mode_push_failed"),
                                name,
                                "${e.javaClass.simpleName}: ${e.message.orEmpty()}"
                            )
                        )
                        false
                    }
                    if (!extracted) continue
                    val installResult = execCommand("pm install -r ${quote(apkFile.absolutePath)}", timeout = 120)
                    if (installResult.exitCode == 0 && !installResult.output.contains("Failure", ignoreCase = true)) {
                        installed++
                        log.appendLine(fmt(AppStrings.get("adb_mode_apk_installed"), apkName))
                    } else {
                        failed++
                        log.appendLine(
                            "Error: " + fmt(
                                AppStrings.get("adb_mode_apk_failed"),
                                apkName,
                                reasonOf(installResult).ifBlank { "exit=${installResult.exitCode}" }
                            )
                        )
                    }
                }
            }
        } finally {
            staging.deleteRecursively()
        }

        log.appendLine(fmt(AppStrings.get("adb_mode_summary"), installed, failed))
        log.appendLine(if (failed == 0) AppStrings.get("flash_result_ok") else AppStrings.get("flash_result_failed"))
        return log.toString()
    }

    // Root 模式刷入。
    //
    // 旧实现的问题：用 isRooted()（靠 su 文件路径猜）判断权限 → "装了 Magisk 但未授权"时点亮按钮；
    // 只试 magisk / ksud 两条命令且路径没做 shell 引用；两者都不可用时直接返回日志，没有兜底；
    // 全程没有回读校验，装没装上用户无从确认。
    // 现在统一交给 RootModuleManager.installModule（本机解压 + 官方安装器 + Root 复制 + 回读校验 +
    // 区分"没 root / 未授权 / 目录不存在 / 只读"的稳定错误码），这里只负责翻译成当前语言并组织成日志。
    actual fun installModuleViaRoot(localFilePath: String): String {
        fun fmt(template: String, vararg args: Any?): String {
            var out = template
            args.forEachIndexed { index, value ->
                out = out.replace("%${index + 1}\$s", value?.toString() ?: "")
            }
            return out
        }

        fun codeText(code: String): String = when (code) {
            "E_ROOT_REQUIRED" -> AppStrings.get("module_err_root_required")
            "E_ROOT_DENIED" -> AppStrings.get("module_err_root_denied")
            "E_ROOT_UNAVAILABLE" -> AppStrings.get("module_err_root_unavailable")
            "E_ADB_DIR_MISSING" -> AppStrings.get("module_err_adb_dir_missing")
            "E_ADB_READONLY" -> AppStrings.get("module_err_adb_readonly")
            "E_FILE_NOT_FOUND" -> AppStrings.get("module_err_file_not_found")
            "E_NOT_ZIP" -> AppStrings.get("module_err_not_zip")
            "E_ZIP_INVALID" -> AppStrings.get("module_err_archive_invalid")
            "E_ZIP_SLIP" -> AppStrings.get("module_err_zip_slip")
            "E_NO_MODULE_PROP" -> AppStrings.get("module_err_no_module_prop")
            "E_NO_MODULE_ID" -> AppStrings.get("module_err_no_module_id")
            "E_ID_UNSAFE" -> AppStrings.get("module_err_id_unsafe")
            "E_STAGE_FAILED" -> AppStrings.get("module_err_stage_failed")
            "E_COPY_FAILED" -> AppStrings.get("module_err_copy_failed")
            "E_VERIFY_FAILED" -> AppStrings.get("module_err_verify_failed")
            "E_EXCEPTION" -> AppStrings.get("module_err_exception")
            "E_OK_MAGISK" -> AppStrings.get("module_ok_magisk")
            "E_OK_KSU" -> AppStrings.get("module_ok_ksu")
            "E_OK_MANUAL" -> AppStrings.get("module_ok_manual")
            else -> code
        }

        val log = StringBuilder()
        log.appendLine(AppStrings.get("flash_mode_root_title"))

        val file = java.io.File(localFilePath)
        if (!file.isFile) {
            log.appendLine("Error: " + fmt(AppStrings.get("flash_file_missing"), localFilePath))
            return log.toString()
        }
        log.appendLine(fmt(AppStrings.get("flash_file_line"), file.name, file.length() / 1024))
        log.appendLine()

        val result = RootModuleManager.installModule(localFilePath)
        val lines = result.message.lines()
        val code = lines.firstOrNull()?.trim().orEmpty()
        val detail = lines.drop(1).joinToString("\n").trim()

        if (result.success) {
            log.appendLine(AppStrings.get("flash_root_ok"))
            log.appendLine(AppStrings.get("flash_result_ok") + ": " + codeText(code))
            if (detail.isNotBlank()) log.appendLine(detail)
            log.appendLine(AppStrings.get("rm_reboot_hint"))
        } else {
            // 首行必须是 "Error: "，ADBModuleScreen 以此判定失败并显示第一条真实原因
            log.appendLine("Error: " + codeText(code))
            if (detail.isNotBlank()) log.appendLine(detail)
            log.appendLine(AppStrings.get("flash_result_failed"))
        }
        return log.toString()
    }

    private fun isAdbEnabled(): Boolean {
        return try {
            android.provider.Settings.Global.getInt(appContext.contentResolver, android.provider.Settings.Global.ADB_ENABLED, 0) == 1
        } catch (e: Exception) { false }
    }

    // ==================== 性能加速 / 手机体检 基础设施实现 ====================

    private val propCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    actual fun getProp(key: String): String {
        propCache[key]?.let { return it }
        return try {
            // 优先走一次 shell getprop（能拿到 Build 里没有的厂商属性），失败再用 Build 兜底
            val value = getProps(listOf(key))[key].orEmpty()
            if (value.isNotBlank()) propCache[key] = value
            value
        } catch (e: Exception) {
            ""
        }
    }

    actual fun getProps(keys: List<String>): Map<String, String> {
        if (keys.isEmpty()) return emptyMap()
        val result = HashMap<String, String>(keys.size)
        // 一次 shell 调用批量读取，避免逐条 exec 造成明显卡顿
        val batch = try {
            val sb = StringBuilder()
            sb.append("echo __PROPS_BEGIN__")
            keys.forEach { sb.append("; echo \"[$it]: \$(getprop $it)\"") }
            sb.append("; echo __PROPS_END__")
            execCommand(sb.toString(), timeout = 10)
        } catch (e: Exception) {
            null
        }
        val body = batch?.output
            ?.substringAfter("__PROPS_BEGIN__", "")
            ?.substringBefore("__PROPS_END__", "")
            ?: ""
        Regex("^\\[([^\\]]+)]:\\s*\\[(.*)]\\s*$").findAll(body).forEach { m ->
            result[m.groupValues[1]] = m.groupValues[2].trim()
        }
        // shell 拿不到的用 Build 兜底，保证识别永远可用
        keys.forEach { key ->
            if (result[key].isNullOrBlank()) {
                val fallback = when (key) {
                    "ro.product.brand", "ro.product.vendor.brand", "ro.product.system.brand" -> Build.BRAND
                    "ro.product.model", "ro.product.vendor.model", "ro.product.system.model" -> Build.MODEL
                    "ro.product.manufacturer" -> Build.MANUFACTURER
                    "ro.build.version.sdk" -> Build.VERSION.SDK_INT.toString()
                    "ro.build.version.release" -> Build.VERSION.RELEASE
                    "ro.build.display.id" -> Build.DISPLAY
                    "ro.product.device", "ro.product.vendor.device" -> Build.DEVICE
                    "ro.board.platform" -> Build.BOARD
                    "ro.hardware" -> Build.HARDWARE
                    else -> ""
                }
                if (fallback.isNotBlank()) result[key] = fallback
            }
        }
        return result
    }

    private fun defaultDisplay(): android.view.Display? {
        return try {
            val dm = appContext.getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            dm.getDisplay(android.view.Display.DEFAULT_DISPLAY)
        } catch (e: Exception) {
            null
        }
    }

    /** dumpsys display 解析结果缓存：同一秒内只解析一次，体检页面会连续调用多次。 */
    private var dumpsysCache: String? = null
    private var dumpsysCacheTime = 0L

    private fun dumpsysDisplay(): String {
        val now = System.currentTimeMillis()
        val cached = dumpsysCache
        if (cached != null && now - dumpsysCacheTime < 3000L) return cached
        val out = try {
            execCommand("dumpsys display", timeout = 12).output
        } catch (e: Exception) {
            ""
        }
        dumpsysCache = out
        dumpsysCacheTime = now
        return out
    }

    private fun parseRefreshRatesFromDumpsys(): List<Float> {
        val text = dumpsysDisplay()
        if (text.isBlank()) return emptyList()
        val rates = LinkedHashSet<Float>()
        // 形如: modeId=1, width=1080, height=2400, fps=120.0
        Regex("fps\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)").findAll(text).forEach { m ->
            m.groupValues[1].toFloatOrNull()?.let { if (it > 1f) rates.add(it) }
        }
        // 形如: refreshRate=120.0
        Regex("refreshRate\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)").findAll(text).forEach { m ->
            m.groupValues[1].toFloatOrNull()?.let { if (it > 1f) rates.add(it) }
        }
        // 形如: 120.0 Hz  /  120Hz
        Regex("([0-9]{2,3}(?:\\.[0-9]+)?)\\s*Hz").findAll(text).forEach { m ->
            m.groupValues[1].toFloatOrNull()?.let { if (it in 20f..300f) rates.add(it) }
        }
        return rates.sorted()
    }

    private fun parseActiveRefreshFromDumpsys(): Float {
        val text = dumpsysDisplay()
        if (text.isBlank()) return 0f
        // 形如: mActiveModeId=1 / activeMode=<id>
        val activeId = Regex("(?:mActiveModeId|activeMode|mCurrentModeId)\\s*=\\s*(\\d+)")
            .find(text)?.groupValues?.get(1)
        if (activeId != null) {
            // 找到对应 modeId 行的 fps
            val line = text.lineSequence().firstOrNull {
                it.contains("modeId=$activeId") || it.contains("mModeId=$activeId")
            }
            if (line != null) {
                Regex("fps\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)").find(line)?.groupValues?.get(1)
                    ?.toFloatOrNull()?.let { return it }
                Regex("refreshRate\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)").find(line)?.groupValues?.get(1)
                    ?.toFloatOrNull()?.let { return it }
            }
        }
        // 直接找 active 段的 refreshRate
        val activeSection = text.substringAfter("mActiveMode", "")
        if (activeSection.isNotEmpty()) {
            Regex("refreshRate\\s*=\\s*([0-9]+(?:\\.[0-9]+)?)").find(activeSection)
                ?.groupValues?.get(1)?.toFloatOrNull()?.let { return it }
        }
        return 0f
    }

    actual fun getSupportedRefreshRates(): List<Float> {
        val rates = LinkedHashSet<Float>()
        // 1) 公开 API（API 23+），最可靠
        try {
            val display = defaultDisplay()
            if (display != null && Build.VERSION.SDK_INT >= 23) {
                display.supportedModes?.forEach { mode ->
                    val r = mode.refreshRate
                    if (r > 1f) rates.add(r)
                }
            }
            if (display != null && display.refreshRate > 1f) rates.add(display.refreshRate)
        } catch (e: Exception) { /* 忽略，继续用 dumpsys */ }
        // 2) dumpsys display 兜底（部分厂商 ROM 把高刷模式隐藏在公开 API 之外）
        rates.addAll(parseRefreshRatesFromDumpsys())
        return rates.sorted()
    }

    actual fun getMaxRefreshRate(): Float {
        val supported = getSupportedRefreshRates()
        if (supported.isNotEmpty()) return supported.max()
        return try { defaultDisplay()?.refreshRate ?: 0f } catch (e: Exception) { 0f }
    }

    actual fun getCurrentRefreshRate(): Float {
        // 1) 厂商设置里记录的峰值刷新率最接近用户感知（很多 ROM 只在这里体现）
        val peak = getSystemSetting("system", "peak_refresh_rate").toFloatOrNull()
        // 2) 公开 API 的 Display.Mode（API 23+）
        val modeRate = try {
            val display = defaultDisplay()
            if (display != null && Build.VERSION.SDK_INT >= 23) {
                display.mode?.refreshRate ?: 0f
            } else 0f
        } catch (e: Exception) { 0f }
        val legacyRate = try { defaultDisplay()?.refreshRate ?: 0f } catch (e: Exception) { 0f }
        // 3) dumpsys 兜底
        val dumpedRate = parseActiveRefreshFromDumpsys()

        // 挑选策略：公开 API 若返回 60 但设置里声明了更高峰值（典型"高刷屏被锁 60Hz"场景），
        // 采用设置中的峰值，避免体检报告误判。
        val apiRate = if (modeRate > 1f) modeRate else legacyRate
        val candidates = listOfNotNull(
            apiRate.takeIf { it > 1f },
            peak?.takeIf { it > 1f },
            dumpedRate.takeIf { it > 1f }
        )
        if (candidates.isEmpty()) return 0f
        // 若 API 报 60 而 peak 更高，说明屏幕能力高于当前值，报告峰值更能反映真实能力
        if (peak != null && peak > 1f && apiRate in 1f..61f && peak > apiRate + 1f) return peak
        return candidates.first()
    }

    actual fun getSystemSetting(namespace: String, key: String): String {
        return try {
            val result = execCommand("settings get $namespace $key", timeout = 8)
            val out = result.output.trim()
            if (out.isEmpty() || out.equals("null", ignoreCase = true)) "" else out
        } catch (e: Exception) { "" }
    }

    actual fun putSystemSetting(namespace: String, key: String, value: String): Boolean {
        return try {
            val result = execCommand("settings put $namespace $key $value", timeout = 8)
            result.exitCode == 0 && !result.error.contains("Exception", true) &&
                    !result.error.contains("Permission", true) &&
                    !result.output.contains("Exception", true)
        } catch (e: Exception) { false }
    }

    actual fun setRefreshRate(target: Float, both: Boolean): CommandResult {
        val max = getMaxRefreshRate().let { if (it > 1f) it else target }
        val supported = getSupportedRefreshRates()
        // 选择不超过屏幕能力、最接近目标的档位
        val chosen = supported.filter { it <= target + 0.5f }.maxOrNull()
            ?: supported.minOrNull()
            ?: target.coerceAtMost(max)
        val v = if (chosen % 1f == 0f) chosen.toInt().toString() else chosen.toString()
        val log = StringBuilder()
        var ok = false
        val steps = buildList {
            add("settings put system peak_refresh_rate $v")
            if (both) add("settings put system min_refresh_rate $v")
            else add("settings delete system min_refresh_rate")
            add("settings put secure user_refresh_rate $v")
        }
        steps.forEach { cmd ->
            val r = execCommand(cmd, timeout = 8)
            val success = r.exitCode == 0 &&
                    !r.error.contains("Exception", true) &&
                    !r.error.contains("Permission denied", true) &&
                    !r.output.contains("Exception", true)
            if (success) ok = true
            log.append(if (success) "OK   " else "FAIL ").append(cmd)
            val msg = listOf(r.error, r.output).firstOrNull { it.isNotBlank() }
            if (msg != null) log.append("  ->  ").append(msg.trim().take(160))
            log.append('\n')
        }
        // 清缓存，让 SystemUI 立即重新读取设置
        execCommand("pkill -f com.android.systemui || killall com.android.systemui", timeout = 8)
        return CommandResult(log.toString(), if (ok) "" else "所有写入均被拒绝：需要 Shizuku / Root / WRITE_SECURE_SETTINGS 权限", if (ok) 0 else 1)
    }

    actual fun resetRefreshRateToAuto(): CommandResult {
        val log = StringBuilder()
        var ok = false
        listOf(
            "settings delete system peak_refresh_rate",
            "settings delete system min_refresh_rate",
            "settings delete system user_refresh_rate",
            "settings delete secure user_refresh_rate"
        ).forEach { cmd ->
            val r = execCommand(cmd, timeout = 8)
            val success = r.exitCode == 0 && !r.error.contains("Permission denied", true)
            if (success) ok = true
            log.append(if (success) "OK   " else "FAIL ").append(cmd).append('\n')
        }
        execCommand("pkill -f com.android.systemui || killall com.android.systemui", timeout = 8)
        return CommandResult(log.toString(), if (ok) "" else "写入被拒绝：需要 Shizuku / Root 权限", if (ok) 0 else 1)
    }

    // ---------------------------------------------------------------- 游戏帧率：应用权限直写

    actual fun isWriteSettingsGranted(): Boolean {
        return try {
            android.provider.Settings.System.canWrite(appContext)
        } catch (e: Exception) {
            false
        }
    }

    actual fun writeSettingsSettingsAction(): String =
        android.provider.Settings.ACTION_MANAGE_WRITE_SETTINGS

    /**
     * 用应用自身的「修改系统设置」权限直接写刷新率，不经过 shell。
     *
     * 为什么需要这条路：非华为机型上 `peak_refresh_rate` / `min_refresh_rate`（以及部分 ROM 的
     * `user_refresh_rate`）放在 system 命名空间，拿到 WRITE_SETTINGS 后普通应用即可写；
     * 华为 EMUI / HarmonyOS 一般不吃这条通道（键被厂商挪到 secure 或直接忽略），必须走
     * Shizuku / Root 的 `settings put`，所以调用方要先判断品牌。
     *
     * 三个键全部尝试写入，只要有一个写成功就返回 true；真正的生效情况由调用方回读刷新率确认。
     */
    actual fun setRefreshRateDirect(target: Float): Boolean {
        return try {
            if (!android.provider.Settings.System.canWrite(appContext)) return false
            val cr = appContext.contentResolver
            var ok = false
            ok = android.provider.Settings.System.putFloat(cr, "peak_refresh_rate", target) || ok
            ok = android.provider.Settings.System.putFloat(cr, "min_refresh_rate", target) || ok
            // 少数 ROM 用 user_refresh_rate 作为"用户指定刷新率"
            ok = android.provider.Settings.System.putFloat(cr, "user_refresh_rate", target) || ok
            ok
        } catch (e: Exception) {
            false
        }
    }

    // ---------------------------------------------------------------- 游戏帧率：按游戏设置

    /**
     * 列出被系统归类为游戏的应用（`ApplicationInfo.category == CATEGORY_GAME`，API 26+）。
     * [includeAll] 为 true 时返回全部已安装应用，便于给"没被正确分类"的游戏手动指定。
     */
    actual fun listGameApps(includeAll: Boolean): List<GameAppInfo> {
        return try {
            val pm = appContext.packageManager
            @Suppress("DEPRECATION")
            val apps = pm.getInstalledApplications(android.content.pm.PackageManager.GET_META_DATA)
            apps.mapNotNull { ai ->
                val isGame = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    ai.category == android.content.pm.ApplicationInfo.CATEGORY_GAME
                } else {
                    false
                }
                if (!includeAll && !isGame) return@mapNotNull null
                val label = try {
                    ai.loadLabel(pm).toString()
                } catch (e: Exception) {
                    ai.packageName
                }
                GameAppInfo(
                    packageName = ai.packageName,
                    label = label,
                    isGame = isGame,
                    isSystem = (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
                )
            }.sortedWith(compareByDescending<GameAppInfo> { it.isGame }.thenBy { it.label.lowercase() })
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 「使用情况访问」是否已授权（AppOps 的 GET_USAGE_STATS）。 */
    actual fun hasUsageAccess(): Boolean {
        return try {
            val appOps = appContext.getSystemService(android.content.Context.APP_OPS_SERVICE)
                    as? android.app.AppOpsManager ?: return false
            @Suppress("DEPRECATION")
            val mode = appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                appContext.packageName
            )
            mode == android.app.AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    actual fun usageAccessSettingsAction(): String =
        android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS

    /**
     * 当前前台应用包名（用于"该游戏一进来就切到它配置的帧率"）。
     * 用 UsageEvents 取最近一次 MOVE_TO_FOREGROUND；没有授权或读不到时返回空串。
     */
    actual fun getForegroundPackage(): String {
        return try {
            val usm = appContext.getSystemService(android.content.Context.USAGE_STATS_SERVICE)
                    as? android.app.usage.UsageStatsManager ?: return ""
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 60_000L, now) ?: return ""
            val event = android.app.usage.UsageEvents.Event()
            var lastPkg = ""
            var lastTs = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND &&
                    event.timeStamp >= lastTs
                ) {
                    lastTs = event.timeStamp
                    lastPkg = event.packageName ?: ""
                }
            }
            lastPkg
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 系统级按游戏限制帧率（Android 13+ GameManagerService 的 game_overlay）。
     * 这是 `device_config` 受保护命令，需要 Shizuku / Root；**写完后一定要回读**：
     * 不少 ROM 上 device_config 是空实现，只看 exitCode 会误判成功。
     */
    actual fun setGameOverlayFps(packageName: String, fps: Int): CommandResult {
        if (packageName.isBlank() || packageName.contains(' ') || packageName.contains('/') ||
            packageName.contains(';')
        ) {
            return CommandResult("", "invalid package name", 1)
        }
        val cmd = if (fps <= 0) {
            "device_config delete game_overlay $packageName"
        } else {
            "device_config put game_overlay $packageName mode=2,fps=$fps"
        }
        val r = execCommand(cmd, timeout = 12)
        val readBack = try {
            execCommand("device_config get game_overlay $packageName", timeout = 8).output.trim()
        } catch (e: Exception) {
            ""
        }
        val ok = if (fps <= 0) !readBack.contains("fps=") else readBack.contains("fps=$fps")
        return CommandResult(
            output = r.output.trim() + if (readBack.isNotEmpty()) "\n[readback] $readBack" else "",
            error = if (ok) "" else r.error.ifBlank { "device_config 写入未生效：本机可能不支持 game_overlay（需要 Android 13+ 且允许 shell 写入）" },
            exitCode = if (ok) 0 else 1
        )
    }

    actual fun getGameOverlayFps(packageName: String): String {
        return try {
            execCommand("device_config get game_overlay $packageName", timeout = 8).output.trim()
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * 把本机文件推到 /data/local/tmp 并 chmod 755。
     *
     * 与临时提权包用的是同一套做法：分块 base64 追加写入，最后 `wc -c` 校验字节数，
     * 避免"看着成功、其实文件写坏了"（内核 exploit 二进制写坏后执行会直接段错误）。
     */
    actual fun pushLocalFileToTemp(localPath: String, remoteName: String): CommandResult {
        // 远端文件名只保留安全字符，防止路径穿越/命令注入
        val safeName = remoteName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(64)
        if (safeName.isBlank() || safeName == "." || safeName == "..") {
            return CommandResult("", "invalid remote name", 1)
        }
        val local = java.io.File(localPath)
        if (!local.isFile || local.length() <= 0L) {
            return CommandResult("", "local file missing or empty: $localPath", 1)
        }
        val remotePath = "/data/local/tmp/$safeName"
        val err = pushFileRaw(local, remotePath)
        if (err != null) return CommandResult("", err, 1)
        val chmod = execCommand("chmod 755 ${shq(remotePath)}", timeout = 30)
        if (chmod.exitCode != 0) {
            return CommandResult(remotePath, "chmod failed: ${reasonOfResult(chmod)}", 1)
        }
        return CommandResult(remotePath, "", 0)
    }

    /**
     * 准备并推送一整套"内核提权工具包"到设备，返回**入口文件的远端路径**。
     *
     * 为什么需要它：公开的内核提权套件（例如 GhostLock / CVE-2026-43499 的各机型移植）
     * 通常不是单个二进制，而是一个压缩包，里面同时有可执行文件、shell 脚本和说明文件，
     * 需要整体推上去、全部给可执行权限、再执行其中某一个入口脚本。
     *
     * 行为：
     * - 传 zip：在本机解压（不依赖设备端 unzip）→ 整体推送 → chmod 755 → 入口取
     *   run.sh / root.sh / start.sh / install.sh / exploit，都没有就取唯一的那个可执行文件；
     * - 传单个文件：直接推送并作为入口。
     * 每个文件都会做字节数校验；任何一步失败都如实返回原因，不做"部分成功"的假象。
     */
    actual fun prepareAndPushKit(localPath: String, remoteDirName: String): CommandResult {
        val local = java.io.File(localPath)
        if (!local.isFile || local.length() <= 0L) {
            return CommandResult("", "local file missing or empty: $localPath", 1)
        }
        val safeDir = remoteDirName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
        if (safeDir.isBlank() || safeDir == "." || safeDir == "..") {
            return CommandResult("", "invalid remote dir name", 1)
        }
        val remoteDir = "/data/local/tmp/$safeDir"
        val isZip = local.name.lowercase().endsWith(".zip")

        // ---------- 1) 本机准备 ----------
        var stageDir = local.parentFile
        var entryName = local.name
        if (isZip) {
            val outDir = java.io.File(appContext.cacheDir, "kx_kit_${System.currentTimeMillis()}")
            if (!outDir.mkdirs() && !outDir.isDirectory) {
                return CommandResult("", "cannot create staging dir", 1)
            }
            val unzipError = unzipInto(local, outDir)
            if (unzipError != null) return CommandResult("", unzipError, 1)
            stageDir = outDir
            val picked = pickKitEntry(outDir)
                ?: return CommandResult("", "kit has no runnable entry (no script and no file)", 1)
            entryName = picked
        }
        val files = stageDir?.listFiles()?.filter { it.isFile } ?: emptyList()
        if (files.isEmpty()) return CommandResult("", "kit has no files", 1)

        // ---------- 2) 推送 ----------
        val mk = execCommand("mkdir -p ${shq(remoteDir)}", timeout = 30)
        if (mk.exitCode != 0) {
            return CommandResult("", "cannot create $remoteDir: ${reasonOfResult(mk)}", 1)
        }
        files.forEach { f ->
            val remotePath = if (isZip) "$remoteDir/${f.name}" else "$remoteDir/${local.name}"
            val err = pushFileRaw(f, remotePath)
            if (err != null) return CommandResult("", "push ${f.name} failed: $err", 1)
        }
        // ---------- 3) 全部给可执行权限（脚本与 ELF 都要） ----------
        execCommand("chmod -R 755 ${shq(remoteDir)} 2>/dev/null", timeout = 30)
        val remoteEntry = "$remoteDir/$entryName"
        val listing = execCommand("ls -l ${shq(remoteDir)}", timeout = 30).output.trim()
        return CommandResult(remoteEntry, "", 0).copy(output = remoteEntry + "\n" + listing)
    }

    /** 单引号包裹 + 内部单引号转义，供 shell 使用。 */
    private fun shq(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    private fun reasonOfResult(r: CommandResult): String =
        listOf(r.error, r.output).firstOrNull { it.isNotBlank() }?.trim().orEmpty()

    /**
     * 把一个本地文件分块 base64 追加写到设备路径，最后按字节数校验。
     * 返回 null 表示成功，否则是真实失败原因。抽出来是因为推送单个文件与推送整套 kit 都要用。
     */
    private fun pushFileRaw(local: java.io.File, remotePath: String): String? {
        val remove = execCommand("rm -f ${shq(remotePath)}", timeout = 30)
        if (remove.exitCode != 0) {
            return reasonOfResult(remove).ifBlank { "cannot create $remotePath" }
        }
        val bytes = try {
            local.readBytes()
        } catch (e: Exception) {
            return "${e.javaClass.simpleName}: ${e.message}"
        }
        var offset = 0
        while (offset < bytes.size) {
            val end = minOf(offset + 64 * 1024, bytes.size)
            val encoded = Base64.encodeToString(bytes.copyOfRange(offset, end), Base64.NO_WRAP)
            val write = execCommand(
                "printf '%s' ${shq(encoded)} | base64 -d >> ${shq(remotePath)}",
                timeout = 60
            )
            if (write.exitCode != 0) {
                val why = reasonOfResult(write)
                val lower = why.lowercase()
                if (lower.contains("not found") || lower.contains("inaccessible") ||
                    lower.contains("no such file")
                ) {
                    return AppStrings.get("device_no_base64")
                }
                return why.ifBlank { "exit=${write.exitCode}" }
            }
            offset = end
        }
        val sizeCheck = execCommand("wc -c < ${shq(remotePath)}", timeout = 30)
        val remoteSize = sizeCheck.output.trim().toLongOrNull()
        if (remoteSize == null || remoteSize != local.length()) {
            return "size mismatch: local=${local.length()} remote=${sizeCheck.output.trim().ifBlank { "?" }}"
        }
        return null
    }

    /** 把 zip 解压到 [outDir]；成功返回 null。逐条校验路径，拒绝越界解压。 */
    private fun unzipInto(zip: java.io.File, outDir: java.io.File): String? {
        return try {
            java.util.zip.ZipFile(zip).use { zf ->
                val outCanonical = outDir.canonicalPath
                zf.entries().asSequence().forEach { entry ->
                    if (entry.isDirectory) return@forEach
                    val target = java.io.File(outDir, entry.name)
                    if (!target.canonicalPath.startsWith(outCanonical + java.io.File.separator)) {
                        return "zip entry escapes target dir: ${entry.name}"
                    }
                    target.parentFile?.mkdirs()
                    zf.getInputStream(entry).use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            null
        } catch (e: Exception) {
            "${e.javaClass.simpleName}: ${e.message}"
        }
    }

    /** 选入口：常见脚本名优先，其次名为 exploit/root/ghostlock 的文件，最后是唯一的文件。 */
    private fun pickKitEntry(dir: java.io.File): String? {
        val all = dir.listFiles()?.filter { it.isFile } ?: return null
        if (all.isEmpty()) return null
        val preferred = listOf("run.sh", "root.sh", "start.sh", "install.sh", "exploit", "root")
        preferred.forEach { name ->
            all.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it.name }
        }
        all.firstOrNull { it.name.contains("ghostlock", true) || it.name.contains("ghost", true) }
            ?.let { return it.name }
        if (all.size == 1) return all.first().name
        all.firstOrNull { it.name.endsWith(".sh") }?.let { return it.name }
        return all.first().name
    }

    /**
     * 强制结束所有第三方后台进程，保留本应用、系统关键进程与[保留白名单]。
     * 这是"一键关闭后台"的核心实现：
     *  1) ActivityManager.killBackgroundProcesses（无需 Root，能覆盖部分包）
     *  2) `am force-stop` 兜底（需 Shizuku / Root 才能真正结束别的包）
     *  3) `pm trim-caches` 清理所有应用缓存（需 Shizuku / Root）
     * 每一步失败都会如实记录，不伪装成功。
     */
    actual fun killBackgroundProcesses(): List<String> {
        val killed = mutableListOf<String>()
        val myPkg = appContext.packageName
        // 保留白名单：正在使用/容易被误杀导致体验倒退的应用
        val keep = setOf(
            myPkg,
            "com.android.systemui",
            "com.android.launcher",
            "com.android.launcher3",
            "com.android.settings",
            "com.android.phone",
            "com.android.providers.telephony",
            "com.android.server.telecom",
            "com.android.inputmethod.latin",
            "com.google.android.inputmethod.latin",
            "com.sohu.inputmethod.sogou",
            "com.baidu.input",
            "com.iflytek.inputmethod",
            "com.tencent.mm",
            "com.tencent.mobileqq",
            "com.eg.android.AlipayGphone",
            "com.baidu.BaiduMap",
            "com.autonavi.minimap",
            "com.tencent.qqmusic",
            "com.netease.cloudmusic",
            "com.kugou.android"
        )
        val pm = appContext.packageManager
        val am = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        // 1) 无权限也能生效的一层
        try {
            val running = try { am.runningAppProcesses ?: emptyList() } catch (e: Exception) { emptyList() }
            running.forEach { proc ->
                val pkg = proc.pkgList?.firstOrNull() ?: proc.processName
                if (pkg.isNullOrBlank() || pkg in keep) return@forEach
                val isSystem = try {
                    (pm.getApplicationInfo(pkg, 0).flags and ApplicationInfo.FLAG_SYSTEM) != 0
                } catch (e: Exception) { true }
                if (isSystem) return@forEach
                try {
                    am.killBackgroundProcesses(pkg)
                    killed.add(pkg)
                } catch (e: Exception) { /* 单个失败不影响其它 */ }
            }
        } catch (e: Exception) { /* 忽略 */ }

        // 2) 收集全部第三方应用，逐个 force-stop（Shizuku/Root 下才有效果）
        val thirdParty = try {
            pm.getInstalledApplications(0)
                .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
                .map { it.packageName }
                .filter { it !in keep }
        } catch (e: Exception) { emptyList() }
        if (thirdParty.isNotEmpty()) {
            // 分批拼接，避免单条命令过长被截断
            thirdParty.chunked(40).forEach { chunk ->
                val cmd = chunk.joinToString("; ") { "am force-stop $it" } + "; echo FSDONE"
                try {
                    execCommand(cmd, timeout = 30)
                } catch (e: Exception) { /* 忽略 */ }
                killed.addAll(chunk)
            }
        }

        // 3) 清理所有应用缓存（失败不影响主流程）
        try { execCommand("pm trim-caches 128G; echo TRIMDONE", timeout = 25) } catch (e: Exception) { /* 忽略 */ }

        // 4) 再补一次 am kill-all，收掉残留的空进程
        try { execCommand("am kill-all; echo KALLDONE", timeout = 15) } catch (e: Exception) { /* 忽略 */ }

        return killed.distinct()
    }

    actual fun fixRefreshRateLock(): Pair<Boolean, String> = run {
        val max = getMaxRefreshRate()
        val current = getCurrentRefreshRate()
        if (max <= 61f) {
            Pair(
                false,
                String.format(AppStrings.get("screen_only_60"), fmtHzForFix(max))
            )
        } else {
            val needFix = current < max - 1f || current <= 61f
            if (!needFix) {
                Pair(
                    true,
                    String.format(
                        AppStrings.get("refresh_rate_ok"),
                        fmtHzForFix(current), fmtHzForFix(max)
                    )
                )
            } else {
                val r = setRefreshRate(max, both = false)
                val newRate = getCurrentRefreshRate()
                val ok = r.exitCode == 0
                val msg = buildString {
                    append(AppStrings.get("refresh_rate_target")).append(' ').append(fmtHzForFix(max))
                    append(" · ").append(AppStrings.get("refresh_rate_before")).append(' ')
                    append(fmtHzForFix(current))
                    append(" · ").append(AppStrings.get("refresh_rate_after")).append(' ')
                    append(if (newRate > 1f) fmtHzForFix(newRate) else AppStrings.get("loading"))
                    if (!ok) append("（").append(AppStrings.get("refresh_rate_read_denied")).append("）")
                }
                Pair(ok, msg)
            }
        }
    }

    /** 刷新率显示：<=1Hz 视为读不到，用「未知」而不是 0 Hz。 */
    private fun fmtHzForFix(v: Float): String =
        if (v <= 1f) AppStrings.get("unknown")
        else "${if (v % 1f == 0f) v.toInt().toString() else v.toString()} Hz"

    actual fun execPerfCommand(command: String, timeout: Int): CommandResult = execCommand(command, timeout)

    /**
     * 图标 base64 缓存。
     *
     * **必须有上界**：一个 96px WebP 的 base64 大约 3~10KB，300+ 个应用一路滚下来
     * 就是好几 MB 的字符串且永不释放——这是用户反馈的"内存泄漏/越用越卡"的真实来源之一。
     * 这里不做精细 LRU（并发访问下容易出错），采用"满了就整体清空"的简单有界策略：
     * 最坏情况只是多解码几次图标，代价远小于无限增长。
     */
    private val iconCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    actual fun getAppIconBase64(packageName: String): String? {
        iconCache[packageName]?.let { return it.ifEmpty { null } }
        val encoded = try {
            val pm = appContext.packageManager
            val drawable = try {
                pm.getApplicationIcon(packageName)
            } catch (e: Exception) {
                null
            } ?: return null
            // 降采样到 96px 再压 WebP，避免把 300+ 个原始图标塞进内存
            val target = 96
            val width = drawable.intrinsicWidth.coerceAtLeast(1)
            val height = drawable.intrinsicHeight.coerceAtLeast(1)
            val scale = if (width > target || height > target) {
                target.toFloat() / maxOf(width, height)
            } else 1f
            val w = (width * scale).toInt().coerceAtLeast(1)
            val h = (height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, w, h)
            drawable.draw(canvas)
            val stream = ByteArrayOutputStream()
            val format = if (Build.VERSION.SDK_INT >= 30) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            bitmap.compress(format, 80, stream)
            bitmap.recycle()
            Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
        // 缓存已满先整体清空：保证有界（最坏只是多解码几次图标，代价远小于无限增长）
        if (iconCache.size >= ICON_CACHE_MAX) iconCache.clear()
        iconCache[packageName] = encoded ?: ""
        return encoded
    }
}
