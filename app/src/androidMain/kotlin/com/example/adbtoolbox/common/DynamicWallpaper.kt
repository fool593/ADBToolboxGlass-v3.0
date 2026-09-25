package com.example.adbtoolbox.common

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

// 视频动态壁纸背景 - Android 实现
// 使用 TextureView + MediaPlayer + Compose graphicsLayer 实现 CENTER_CROP（居中裁剪铺满全屏）、循环播放、静音
@Composable
actual fun DynamicWallpaperBackground(
    videoPath: String?,
    modifier: Modifier
) {
    if (videoPath == null) return
    val videoFile = File(videoPath)
    if (!videoFile.exists()) return

    val context = LocalContext.current
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var videoSize by remember { mutableStateOf(IntSize.Zero) }
    var playerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    // 计算 CENTER_CROP 的缩放和偏移
    val scale = if (videoSize.width > 0 && videoSize.height > 0 && viewSize.width > 0 && viewSize.height > 0) {
        val sx = viewSize.width.toFloat() / videoSize.width
        val sy = viewSize.height.toFloat() / videoSize.height
        if (sx > sy) sx else sy
    } else 1f
    val dx = if (videoSize.width > 0) (viewSize.width - videoSize.width * scale) / 2f else 0f
    val dy = if (videoSize.height > 0) (viewSize.height - videoSize.height * scale) / 2f else 0f

    // 兜底释放：composable 离开组合 / 视频切换时确保 MediaPlayer 被释放，防内存泄漏
    DisposableEffect(videoFile) {
        onDispose {
            playerRef?.let { player ->
                runCatching { player.stop() }
                runCatching { player.release() }
            }
            playerRef = null
        }
    }

    // key(videoPath)：视频切换时重建 TextureView，触发 onSurfaceTextureDestroyed 释放旧播放器
    key(videoPath) {
        AndroidView(
            modifier = modifier
                .clip(RectangleShape)
                .onSizeChanged { size ->
                    viewSize = size
                }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = dx
                    translationY = dy
                    transformOrigin = TransformOrigin(0f, 0f)
                },
            factory = { ctx ->
                TextureView(ctx).apply {
                    setOpaque(false)

                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            try {
                                val mp = MediaPlayer()
                                playerRef = mp
                                mp.setDataSource(ctx, Uri.fromFile(videoFile))
                                mp.isLooping = true
                                mp.setVolume(0f, 0f)
                                mp.setSurface(Surface(surface))
                                mp.setOnPreparedListener { player ->
                                    videoSize = IntSize(player.videoWidth, player.videoHeight)
                                    player.start()
                                }
                                mp.setOnErrorListener { player, _, _ ->
                                    runCatching {
                                        player.reset()
                                        player.setDataSource(ctx, Uri.fromFile(videoFile))
                                        player.prepareAsync()
                                    }
                                    true
                                }
                                mp.prepareAsync()
                                tag = mp
                            } catch (_: Exception) {}
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            (tag as? MediaPlayer)?.let { player ->
                                runCatching { player.stop() }
                                runCatching { player.release() }
                            }
                            playerRef = null
                            tag = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                    }
                }
            }
        )
    }
}
