package com.kyant.backdrop.catalog

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.adbtoolbox.common.AppSettings
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import java.io.File

@Composable
actual fun BackdropDemoScaffold(
    modifier: Modifier,
    pickWallpaperTrigger: Int,
    clearWallpaperTrigger: Int,
    dynamicWallpaper: (@Composable () -> Unit)?,
    content: @Composable BoxScope.(backdrop: LayerBackdrop) -> Unit
) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center
    ) {
        var painter: Painter? by remember { mutableStateOf(null) }
        val context = LocalContext.current
        val prefs = remember { context.getSharedPreferences("wallpaper", 0) }

        val pickMedia = rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->
            if (uri != null) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val imageBitmap = BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
                        if (imageBitmap != null) {
                            painter = BitmapPainter(imageBitmap)
                            // 保存壁纸到内部存储
                            try {
                                val wallpaperFile = File(context.filesDir, "wallpaper.jpg")
                                context.contentResolver.openInputStream(uri)?.use { input ->
                                    wallpaperFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                prefs.edit().putString("wallpaper_path", wallpaperFile.absolutePath).apply()
                            } catch (_: Exception) {}
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 启动时加载保存的壁纸
        LaunchedEffect(Unit) {
            val savedPath = prefs.getString("wallpaper_path", null)
            if (savedPath != null) {
                try {
                    val file = File(savedPath)
                    if (file.exists()) {
                        val bitmap = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                        if (bitmap != null) {
                            painter = BitmapPainter(bitmap)
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 监听壁纸选择触发
        LaunchedEffect(pickWallpaperTrigger) {
            if (pickWallpaperTrigger > 0) {
                pickMedia.launch("image/*")
            }
        }

        // 清除壁纸
        LaunchedEffect(clearWallpaperTrigger) {
            if (clearWallpaperTrigger > 0) {
                try {
                    val wallpaperFile = java.io.File(context.filesDir, "wallpaper.jpg")
                    if (wallpaperFile.exists()) wallpaperFile.delete()
                    prefs.edit().remove("wallpaper_path").apply()
                    painter = null
                } catch (_: Exception) {}
            }
        }

        val backdrop = rememberLayerBackdrop()

        // 没壁纸时根据主题决定默认背景：深色=深灰，浅色=白色
        val defaultBgColor = if (AppSettings.isDarkMode) {
            androidx.compose.ui.graphics.Color(0xFF1C1C1E)
        } else {
            androidx.compose.ui.graphics.Color(0xFFFFFFFF)
        }

        if (dynamicWallpaper != null) {
            // 动态壁纸模式：视频作为 backdrop 捕获层，液态玻璃反射视频画面
            Box(
                Modifier
                    .layerBackdrop(backdrop)
                    .then(modifier)
                    .fillMaxSize()
            ) {
                dynamicWallpaper()
            }
        } else {
            Image(
                painter ?: ColorPainter(defaultBgColor),
                null,
                Modifier
                    .layerBackdrop(backdrop)
                    .then(modifier)
                    .fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        content(backdrop)
    }
}
