package com.example.adbtoolbox.common

import androidx.compose.ui.graphics.ImageBitmap

/**
 * 把 Base64 编码的图片（PNG / WebP）解码为 [ImageBitmap]。
 *
 * 为什么需要这层 expect/actual：应用图标数据由 Android 侧的
 * [ADBTools.getAppIconBase64] 提供（已降采样成 96px WebP，避免一次加载 300+ 大图把内存打爆），
 * 但解码要读字节流，commonMain 没有可移植的解码 API，所以按平台实现。
 * 解码失败返回 null，调用方回退到首字母占位，不会崩。
 */
expect fun decodeBase64ToImageBitmap(base64: String): ImageBitmap?
