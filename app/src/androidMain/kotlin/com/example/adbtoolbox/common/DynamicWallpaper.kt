package com.example.adbtoolbox.common

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

// 视频动态壁纸背景 - Android 实现
// 使用 TextureView + MediaPlayer：居中裁剪铺满全屏（CENTER_CROP）、循环播放、静音
@Composable
actual fun DynamicWallpaperBackground(
    videoPath: String?,
    modifier: Modifier
) {
    if (videoPath == null) return
    val videoFile = File(videoPath)
    if (!videoFile.exists()) return

    AndroidView(
        modifier = modifier,
        factory = { context ->
            TextureView(context).apply {
                setOpaque(false)
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        attachVideo(this@apply, videoFile.absolutePath, surface, width, height)
                    }

                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                        (this@apply.tag as? VideoHolder)?.player?.let { fitVideo(this@apply, it, width, height) }
                    }

                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                        (this@apply.tag as? VideoHolder)?.player?.let { runCatching { it.release() } }
                        this@apply.tag = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        },
        update = { view ->
            val holder = view.tag as? VideoHolder
            if (holder == null || holder.path != videoFile.absolutePath) {
                holder?.player?.let { runCatching { it.release() } }
                val surfaceTexture = view.surfaceTexture
                if (surfaceTexture != null) {
                    attachVideo(view, videoFile.absolutePath, surfaceTexture, view.width, view.height)
                } else {
                    view.tag = VideoHolder(videoFile.absolutePath, null)
                }
            }
        }
    )
}

private class VideoHolder(val path: String, var player: MediaPlayer?)

private fun attachVideo(view: TextureView, path: String, surface: SurfaceTexture, width: Int, height: Int) {
    try {
        val mp = MediaPlayer()
        mp.setDataSource(view.context, Uri.fromFile(File(path)))
        mp.isLooping = true
        mp.setVolume(0f, 0f)
        mp.setSurface(Surface(surface))
        mp.setOnPreparedListener { player ->
            fitVideo(view, player, width, height)
            player.start()
        }
        mp.setOnErrorListener { player, _, _ ->
            // 出错时重置并重试，保证循环稳定
            runCatching {
                player.reset()
                player.setDataSource(view.context, Uri.fromFile(File(path)))
                player.prepareAsync()
            }
            true
        }
        mp.prepareAsync()
        view.tag = VideoHolder(path, mp)
    } catch (_: Exception) {}
}

// 居中裁剪（CENTER_CROP）：视频保持比例铺满整个屏幕，超出部分裁掉，不留黑边
private fun fitVideo(view: TextureView, player: MediaPlayer, viewW: Int, viewH: Int) {
    try {
        val vw = player.videoWidth
        val vh = player.videoHeight
        if (vw <= 0 || vh <= 0 || viewW <= 0 || viewH <= 0) return
        val scaleX = viewW.toFloat() / vw
        val scaleY = viewH.toFloat() / vh
        val scale = if (scaleX > scaleY) scaleX else scaleY
        val matrix = Matrix()
        matrix.setScale(scale, scale)
        val dx = (viewW - vw * scale) / 2f
        val dy = (viewH - vh * scale) / 2f
        matrix.postTranslate(dx, dy)
        view.setTransform(matrix)
    } catch (_: Exception) {}
}
