package com.example.adbtoolbox

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * 全局崩溃捕获器。
 *
 * 目标：高安卓版（15/16）仍有"进入应用闪退"的反馈，但崩溃点可能不在开屏视频。
 * 用全局 UncaughtExceptionHandler 把崩溃堆栈写入：
 * 1) filesDir/crash.txt（应用内：设置页可查看、Toast 弹出）；
 * 2) 下载/Download 文件夹 ADBToolbox_crash_*.txt（Android 10+ 用 MediaStore，免存储权限）——
 *    方便用户直接用文件管理器/微信取出发给开发者。
 *
 * 注意：只记录，不吞异常（仍调用上一个 handler 正常终止），不会掩盖问题。
 */
object CrashGuard {
    private const val FILE = "crash.txt"

    fun install(context: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val text = buildString {
                appendLine("time=${System.currentTimeMillis()}")
                appendLine("thread=${thread.name}")
                appendLine(throwable.stackTraceToString())
            }
            try {
                File(context.filesDir, FILE).writeText(text)
            } catch (_: Exception) {
                // 记录失败不影响原有行为
            }
            try {
                writeCrashToDownload(context, text)
            } catch (_: Exception) {
            }
            prev?.uncaughtException(thread, throwable)
        }
    }

    /** 写一份崩溃日志到 Download 文件夹（MediaStore，Android 10+ 免存储权限）。 */
    private fun writeCrashToDownload(context: Context, text: String) {
        val name = "ADBToolbox_crash_" + System.currentTimeMillis() + ".txt"
        if (Build.VERSION.SDK_INT >= 29) {
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI, values
            ) ?: return
            try {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(text.toByteArray())
                }
            } catch (e: Exception) {
                context.contentResolver.delete(uri, null, null)
            }
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            if (dir != null) {
                dir.mkdirs()
                File(dir, name).writeText(text)
            }
        }
    }

    /** 上次崩溃的堆栈（读取后不删除，便于反复查看）。 */
    fun lastCrash(context: Context): String? {
        return try {
            File(context.filesDir, FILE).takeIf { it.exists() }?.readText()
        } catch (_: Exception) {
            null
        }
    }
}