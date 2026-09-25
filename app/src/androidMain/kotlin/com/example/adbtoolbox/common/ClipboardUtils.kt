package com.example.adbtoolbox.common

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

// 剪贴板工具 actual 实现
actual fun copyToClipboard(text: String): Boolean {
    return try {
        val cm = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ADBToolbox", text))
        true
    } catch (e: Exception) {
        false
    }
}
