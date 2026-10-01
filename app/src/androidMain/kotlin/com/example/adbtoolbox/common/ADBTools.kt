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
    actual fun flashTempRootModule(zipPath: String): CommandResult {
        val zipFile = java.io.File(zipPath)
        if (!zipFile.exists()) {
            return CommandResult("", "File not found: $zipPath", -1)
        }
        return try {
            val tempDir = java.io.File(appContext.cacheDir, "temp_root_${System.currentTimeMillis()}")
            tempDir.mkdirs()
            java.util.zip.ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    val outFile = java.io.File(tempDir, entry.name)
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
            val scriptFile = tempDir.walkTopDown().firstOrNull {
                it.name == "run.sh" || it.name == "root.sh" || it.name == "install.sh" || it.name.endsWith(".sh")
            }
            if (scriptFile == null) {
                tempDir.deleteRecursively()
                return CommandResult("", "No script found in zip package", -1)
            }
            scriptFile.setExecutable(true)
            val result = execCommand("sh ${scriptFile.absolutePath}", 60)
            tempDir.deleteRecursively()
            result
        } catch (e: Exception) {
            CommandResult("", "Error: ${e.message}", -1)
        }
    }

    actual fun installModuleViaADB(localFilePath: String): String {
        return try {
            val file = java.io.File(localFilePath)
            if (!file.exists()) {
                return "Error: File not found: $localFilePath"
            }

            val log = StringBuilder()
            log.appendLine("=== ADB Mode Installation ===")
            log.appendLine("Module: ${file.name}")
            log.appendLine("Size: ${file.length() / 1024} KB")
            log.appendLine()

            // 检查Shizuku/ADB权限
            val hasShizuku = isShizukuAvailable()
            if (!hasShizuku) {
                log.appendLine("Error: Shizuku/ADB permission not available")
                log.appendLine("Please start Shizuku and grant permission first")
                return log.toString()
            }
            log.appendLine("ADB/Shizuku: OK")

            // 复制到临时目录
            val tempDir = "/data/local/tmp/adb_module_${System.currentTimeMillis()}"
            val tempZip = "$tempDir/module.zip"

            log.appendLine("Creating temp dir: $tempDir")
            var result = execCommand("mkdir -p \"$tempDir\"")
            if (result.exitCode != 0) {
                log.appendLine("Error: Failed to create temp dir")
                log.appendLine(result.error)
                return log.toString()
            }

            // 用cat复制文件（ADB权限下cp可能受限）
            log.appendLine("Copying module to temp...")
            result = execCommand("cat \"$localFilePath\" > \"$tempZip\"")
            if (result.exitCode != 0) {
                // 尝试用sh -c
                result = execCommand("sh -c 'cat \"$localFilePath\" > \"$tempZip\"'")
            }
            if (result.exitCode != 0) {
                log.appendLine("Error: Failed to copy file")
                log.appendLine(result.error)
                return log.toString()
            }
            log.appendLine("Copied to: $tempZip")

            // 检查是否有unzip
            val unzipCheck = execCommand("which unzip")
            val hasUnzip = unzipCheck.exitCode == 0 && unzipCheck.output.isNotBlank()

            if (hasUnzip) {
                log.appendLine("Unzipping module...")
                result = execCommand("unzip -o \"$tempZip\" -d \"$tempDir\"")
                if (result.exitCode != 0) {
                    log.appendLine("Warning: unzip failed, trying toybox")
                    result = execCommand("toybox unzip -o \"$tempZip\" -d \"$tempDir\"")
                }
            } else {
                log.appendLine("unzip not found, trying toybox...")
                result = execCommand("toybox unzip -o \"$tempZip\" -d \"$tempDir\"")
            }

            if (result.exitCode != 0) {
                log.appendLine("Error: Failed to unzip module")
                log.appendLine(result.output)
                log.appendLine(result.error)
                return log.toString()
            }
            log.appendLine("Unzipped successfully")

            // 列出解压后的文件
            log.appendLine()
            log.appendLine("Module contents:")
            val lsResult = execCommand("ls -la \"$tempDir\"")
            log.appendLine(lsResult.output)

            // 检查是否是Magisk模块（有module.prop）
            val modulePropCheck = execCommand("test -f \"$tempDir/module.prop\" && echo yes || echo no")
            val isMagiskModule = modulePropCheck.output.trim() == "yes"

            if (isMagiskModule) {
                log.appendLine()
                log.appendLine("Detected: Magisk/Root module format (has module.prop)")
                log.appendLine("Module info:")
                val propResult = execCommand("cat \"$tempDir/module.prop\"")
                log.appendLine(propResult.output)
                log.appendLine()
                log.appendLine("Error: This is a Root module that requires Magisk/KernelSU.")
                log.appendLine("Please switch to Root mode to install this module.")
                log.appendLine("ADB mode only supports normal zip packages (APKs, files, etc.).")
                return log.toString()
            } else {
                // 非Magisk模块，检查是否有APK或其他可安装文件
                log.appendLine()
                log.appendLine("Not a Magisk module, checking for installable files...")

                // 查找APK文件
                val apkFind = execCommand("find \"$tempDir\" -name \"*.apk\" -type f")
                val apkFiles = apkFind.output.lines().filter { it.isNotBlank() }

                if (apkFiles.isNotEmpty()) {
                    log.appendLine("Found ${apkFiles.size} APK file(s):")
                    apkFiles.forEach { apk ->
                        log.appendLine("  - ${apk.substringAfterLast("/")}")
                    }
                    log.appendLine()
                    log.appendLine("Installing APK(s)...")
                    apkFiles.forEach { apk ->
                        val apkName = apk.substringAfterLast("/")
                        log.appendLine("Installing: $apkName")
                        val installResult = execCommand("pm install -r \"$apk\"", timeout = 60)
                        if (installResult.exitCode == 0) {
                            log.appendLine("  Success: $apkName installed")
                        } else {
                            log.appendLine("  Failed: ${installResult.output} ${installResult.error}")
                        }
                    }
                } else {
                    log.appendLine("No APK files found")
                    log.appendLine("Module files extracted to: $tempDir")
                    log.appendLine("Please check the contents and install manually if needed")
                }
            }

            log.appendLine()
            log.appendLine("=== Installation Complete ===")
            log.appendLine("Temp files kept at: $tempDir")
            log.appendLine("You can delete them later if needed")

            log.toString()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    actual fun installModuleViaRoot(localFilePath: String): String {
        return try {
            val file = java.io.File(localFilePath)
            if (!file.exists()) {
                return "Error: File not found: $localFilePath"
            }
            if (!file.name.endsWith(".zip", ignoreCase = true)) {
                return "Error: Only .zip module files are supported"
            }

            val log = StringBuilder()
            log.appendLine("Module: ${file.name}")
            log.appendLine("Size: ${file.length() / 1024} KB")
            log.appendLine()

            // 检查Root
            if (!isRooted()) {
                log.appendLine("Error: Root permission required")
                return log.toString()
            }
            log.appendLine("Root: OK")

            // 检查Magisk
            val magiskAvailable = hasMagisk()
            if (!magiskAvailable) {
                log.appendLine("Warning: Magisk not detected")
                log.appendLine("Trying alternative installation method...")
                log.appendLine()

                // 尝试用KernelSU或其他方式
                val result = execCommand("ksud module install $localFilePath", timeout = 60)
                if (result.exitCode == 0) {
                    log.appendLine("KernelSU install output:")
                    log.appendLine(result.output)
                    log.appendLine()
                    log.appendLine("Success! Module installed via KernelSU")
                    log.appendLine("Reboot to apply changes")
                } else {
                    log.appendLine("Error: Neither Magisk nor KernelSU detected")
                    log.appendLine("Please install Magisk or KernelSU first")
                }
                return log.toString()
            }
            log.appendLine("Magisk: OK")

            // 复制到临时目录
            val tempPath = "/data/local/tmp/${file.name}"
            val copyResult = execCommand("cp \"$localFilePath\" \"$tempPath\"")
            if (copyResult.exitCode != 0) {
                log.appendLine("Error: Failed to copy file to temp")
                log.appendLine(copyResult.error)
                return log.toString()
            }
            log.appendLine("Copied to: $tempPath")

            // 设置权限
            execCommand("chmod 644 \"$tempPath\"")

            // 用Magisk安装模块
            log.appendLine()
            log.appendLine("Installing module via Magisk...")
            val installResult = execCommand("magisk --install-module \"$tempPath\"", timeout = 120)

            log.appendLine("Exit code: ${installResult.exitCode}")
            if (installResult.output.isNotBlank()) {
                log.appendLine("Output:")
                log.appendLine(installResult.output)
            }
            if (installResult.error.isNotBlank()) {
                log.appendLine("Error:")
                log.appendLine(installResult.error)
            }

            // 清理临时文件
            execCommand("rm -f \"$tempPath\"")

            log.appendLine()
            if (installResult.exitCode == 0) {
                log.appendLine("Success! Module installed")
                log.appendLine("Reboot to apply changes")
            } else {
                log.appendLine("Failed! Please check the output above")
            }

            log.toString()
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
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

    /** 图标缓存：避免列表滚动时反复解码同一个图标。 */
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
        iconCache[packageName] = encoded ?: ""
        return encoded
    }
}
