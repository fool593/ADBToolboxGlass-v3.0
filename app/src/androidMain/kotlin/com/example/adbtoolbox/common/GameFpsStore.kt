package com.example.adbtoolbox.common

import android.content.Context

/** Android 侧实现：SharedPreferences 里存一行行 `包名=帧率`。 */
actual object GameFpsStore {
    private const val PREFS_NAME = "game_fps"
    private const val KEY_PER_GAME = "per_game_fps"

    private fun prefs() = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    actual fun load(): Map<String, Int> {
        return try {
            val raw = prefs().getString(KEY_PER_GAME, null)
            if (raw.isNullOrBlank()) {
                emptyMap()
            } else {
                raw.split('\n').mapNotNull { line ->
                    val i = line.indexOf('=')
                    if (i <= 0) {
                        null
                    } else {
                        val pkg = line.substring(0, i).trim()
                        val fps = line.substring(i + 1).trim().toIntOrNull()
                        if (pkg.isEmpty() || fps == null) null else pkg to fps
                    }
                }.toMap()
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    actual fun save(settings: Map<String, Int>) {
        try {
            val text = settings.entries.joinToString("\n") { "${it.key}=${it.value}" }
            prefs().edit().putString(KEY_PER_GAME, text).apply()
        } catch (e: Exception) {
            // 保存失败不影响本次会话的生效（内存里仍然有效）
        }
    }
}
