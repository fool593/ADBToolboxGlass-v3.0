package com.example.adbtoolbox

import android.content.Context
import java.io.File

/**
 * 全局崩溃捕获器。
 *
 * 目标：高安卓版（15/16）仍有"进入应用闪退"的反馈，但崩溃点可能不在开屏视频。
 * 用全局 UncaughtExceptionHandler 把崩溃堆栈写入 filesDir/crash.txt，
 * 下次启动在 MainActivity 用 Toast 把堆栈前 600 字显示出来 —— 用户把这段文字发回来，
 * 我们就能按真实堆栈定位根因，而不是盲改。
 *
 * 注意：只记录，不吞异常（仍调用上一个 handler 正常终止），不会掩盖问题。
 */
object CrashGuard {
    private const val FILE = "crash.txt"

    fun install(context: Context) {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                File(context.filesDir, FILE).writeText(
                    buildString {
                        appendLine("time=${System.currentTimeMillis()}")
                        appendLine("thread=${thread.name}")
                        appendLine(throwable.stackTraceToString())
                    }
                )
            } catch (_: Exception) {
                // 记录失败不影响原有行为
            }
            prev?.uncaughtException(thread, throwable)
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