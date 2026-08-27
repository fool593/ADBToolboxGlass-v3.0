package com.example.adbtoolbox.common

import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import java.io.File

// 视频动态壁纸背景 - Android 实现
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
            VideoView(context).apply {
                // 设置视频URI，比setVideoPath更可靠
                setVideoURI(Uri.fromFile(videoFile))
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    mp.setVolume(0f, 0f) // 静音
                    mp.start()
                }
                setOnCompletionListener { mp ->
                    // 双重保险，确保循环播放
                    try {
                        mp.seekTo(0)
                        mp.start()
                    } catch (_: Exception) {}
                }
                setOnErrorListener { mp, what, extra ->
                    try {
                        mp.reset()
                        mp.setDataSource(context, Uri.fromFile(videoFile))
                        mp.prepareAsync()
                    } catch (_: Exception) {}
                    true
                }
                // 开始加载视频
                start()
            }
        },
        update = { view ->
            if (view.tag != videoPath) {
                view.tag = videoPath
                view.setVideoURI(Uri.fromFile(videoFile))
                view.start()
            }
        }
    )
}
