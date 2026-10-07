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

        val prefs = getSharedPreferences("splash_state", MODE_PRIVATE)

        // 崩溃标记：上一轮如果没走到 MainActivity（开屏期间就被杀/崩溃），splash_ok 会是 false。
        // 这种情况直接跳过视频——高安卓版（Android 15/16，尤其部分厂商 ROM）上 MediaPlayer 的
        // codec/surface 硬崩溃无法被 try/catch 捕获，用户会"每次进应用看一小段动画就闪退"。
        // 跳过视频后仍然正常进入主界面，避免崩溃循环；下次正常到达主界面后标记恢复。
        if (!prefs.getBoolean("splash_ok", true)) {
            navigateToMain()
            return
        }
        // 此刻开始"播放中"：主界面安全到达（MainActivity.onResume）后会写回 true
        prefs.edit().putBoolean("splash_ok", false).apply()

        val rootLayout = FrameLayout(this)
        videoView = VideoView(this)
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        videoView.layoutParams = params
        rootLayout.addView(videoView)
        try {
            setContentView(rootLayout)
        } catch (e: Exception) {
            // 极少数 ROM 上 VideoView 初始化失败，直接进主界面
            e.printStackTrace()
            navigateToMain()
            return
        }

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

        try {
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
                try {
                    videoView.start()
                } catch (e: Exception) {
                    e.printStackTrace()
                    navigateToMain()
                }
            }

            videoView.setOnErrorListener { _, _, _ ->
                // 视频解码/播放出错（高安卓版常见），等待最小显示时间后再跳转
                android.os.Handler(mainLooper).postDelayed({
                    navigateToMain()
                }, minDisplayTime)
                true
            }

            videoView.setOnClickListener {
                // 用户点击跳过，也要等待最小显示时间
                navigateToMain()
            }
        } catch (e: Exception) {
            // 设置视频的任何一步失败都直接进主界面，绝不卡死在开屏
            e.printStackTrace()
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
