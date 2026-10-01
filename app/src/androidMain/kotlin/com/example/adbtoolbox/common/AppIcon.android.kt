package com.example.adbtoolbox.common

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** Android 侧实现：Base64 → 字节 → BitmapFactory → ImageBitmap。 */
actual fun decodeBase64ToImageBitmap(base64: String): ImageBitmap? {
    return try {
        if (base64.isBlank()) return null
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        if (bytes.isEmpty()) return null
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}
