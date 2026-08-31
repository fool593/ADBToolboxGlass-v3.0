package com.example.adbtoolbox

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.activity.ComponentActivity

class SplashActivity : ComponentActivity() {

    private lateinit var videoView: VideoView
    private var videoWidth = 0
    private var videoHeight = 0
    private val startTime = System.currentTimeMillis()
    private val minDisplayTime = 2000L // 最小显示时间 2 秒，防止开屏动画跳过
    private var hasNavigated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = FrameLayout(this)
        videoView = VideoView(this)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        videoView.layoutParams = params
        rootLayout.addView(videoView)
        setContentView(rootLayout)

        // 优先使用用户自定义的开屏视频（已持久化到 filesDir/splash/splash_video.mp4）
        var videoUri: Uri? = null
        try {
            val customPath = com.example.adbtoolbox.common.AppCache.splashVideoPath.value
            val splashFile = if (customPath != null && java.io.File(customPath).exists())
                java.io.File(customPath)
            else {
                // 从持久化恢复（若 AppCache 尚未加载）
                val saved = java.io.File(filesDir, "splash/splash_video.mp4")
                if (saved.exists()) saved else null
            }
            if (splashFile != null && splashFile.length() > 0) {
                videoUri = Uri.fromFile(splashFile)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (videoUri != null) {
            videoView.setVideoURI(videoUri)
        } else {
            val builtinPath = "android.resource://$packageName/raw/splash_video"
            videoView.setVideoURI(Uri.parse(builtinPath))
        }

        videoView.setOnCompletionListener {
            navigateToMain()
        }

        videoView.setOnPreparedListener { mp ->
            mp.isLooping = false
            videoWidth = mp.videoWidth
            videoHeight = mp.videoHeight
            adjustVideoSize()
            videoView.start()
        }

        videoView.setOnErrorListener { _, _, _ ->
            // 视频播放出错，等待最小显示时间后再跳转
            android.os.Handler(mainLooper).postDelayed({
                navigateToMain()
            }, minDisplayTime)
            true
        }

        videoView.setOnClickListener {
            // 用户点击跳过，也要等待最小显示时间
            navigateToMain()
        }
    }

    private fun adjustVideoSize() {
        if (videoWidth == 0 || videoHeight == 0) return

        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        val screenWidth = metrics.widthPixels
        val screenHeight = metrics.heightPixels

        val videoRatio = videoWidth.toFloat() / videoHeight
        val screenRatio = screenWidth.toFloat() / screenHeight

        val layoutParams = videoView.layoutParams as FrameLayout.LayoutParams

        if (videoRatio > screenRatio) {
            // 视频比屏幕宽，按高度适配，宽度超出裁剪
            layoutParams.height = screenHeight
            layoutParams.width = (screenHeight * videoRatio).toInt()
            layoutParams.leftMargin = -(layoutParams.width - screenWidth) / 2
        } else {
            // 视频比屏幕高，按宽度适配，高度超出裁剪
            layoutParams.width = screenWidth
            layoutParams.height = (screenWidth / videoRatio).toInt()
            layoutParams.topMargin = -(layoutParams.height - screenHeight) / 2
        }

        videoView.layoutParams = layoutParams
    }

    private fun navigateToMain() {
        if (hasNavigated) return
        val elapsed = System.currentTimeMillis() - startTime
        if (elapsed < minDisplayTime) {
            // 等待剩余时间后再跳转
            android.os.Handler(mainLooper).postDelayed({
                doNavigate()
            }, minDisplayTime - elapsed)
        } else {
            doNavigate()
        }
    }

    private fun doNavigate() {
        if (hasNavigated) return
        hasNavigated = true
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            videoView.stopPlayback()
        } catch (e: Exception) {
        }
    }
}
