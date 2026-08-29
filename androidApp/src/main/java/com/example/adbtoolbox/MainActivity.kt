package com.example.adbtoolbox

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.MainContent
import rikka.shizuku.Shizuku

class MainActivity : ComponentActivity() {

    private val shizukuListener = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        try {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "ADB permission granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "ADB permission denied", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.adbtoolbox.common.appContext = applicationContext
        WindowCompat.setDecorFitsSystemWindows(window, false)

        try {
            Shizuku.addRequestPermissionResultListener(shizukuListener)
        } catch (e: Exception) {
        }

        // 启动预加载：后台加载应用列表和设备信息，页面切换秒开
        try {
            com.example.adbtoolbox.common.Preloader.start()
        } catch (e: Exception) {
        }

        // 加载持久化的液态玻璃效果配置（退出应用后重新进入不丢失）
        try {
            com.example.adbtoolbox.common.GlassEffectPersistence.loadAll()
        } catch (e: Exception) {
        }

        setContent {
            // 文件选择器：选择zip模块文件
            val filePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let { selectedUri ->
                    try {
                        // 从Uri获取真实文件路径
                        val filePath = getFilePathFromUri(selectedUri)
                        if (filePath != null) {
                            AppCache.selectedModulePath.value = filePath
                        } else {
                            // 如果无法获取真实路径，复制到缓存目录
                            val cacheFile = java.io.File(cacheDir, "module_${System.currentTimeMillis()}.zip")
                            contentResolver.openInputStream(selectedUri)?.use { input ->
                                cacheFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            AppCache.selectedModulePath.value = cacheFile.absolutePath
                        }
                    } catch (e: Exception) {
                        Toast.makeText(this, "File selection failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // 观察文件选择触发器
            val trigger by androidx.compose.runtime.rememberUpdatedState(AppCache.pickModuleFileTrigger.value)
            LaunchedEffect(trigger) {
                if (trigger > 0) {
                    AppCache.pickModuleFileTrigger.value = 0 // 立即重置，防止重复触发
                    filePicker.launch("application/zip")
                }
            }

            // 插件文件选择器
            val pluginPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let { selectedUri ->
                    // 立即显示安装中状态，避免UI卡顿
                    AppCache.pluginInstalling.value = true
                    // 在后台线程执行文件复制和插件安装，避免主线程卡顿
                    Thread {
                        try {
                            // 从Uri获取真实文件路径
                            var filePath = getFilePathFromUri(selectedUri)
                            if (filePath == null) {
                                // 如果无法获取真实路径，复制到缓存目录
                                val cacheFile = java.io.File(cacheDir, "plugin_${System.currentTimeMillis()}.zip")
                                contentResolver.openInputStream(selectedUri)?.use { input ->
                                    cacheFile.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                filePath = cacheFile.absolutePath
                            }
                            // 调用插件安装
                            val result = com.example.adbtoolbox.common.PluginManager.installPlugin(filePath)
                            AppCache.pluginInstallResult.value = result.message
                        } catch (e: Exception) {
                            AppCache.pluginInstallResult.value = "Plugin installation failed: ${e.message}"
                        } finally {
                            AppCache.pluginInstalling.value = false
                        }
                    }.start()
                }
            }

            // 观察插件文件选择触发器
            val pluginTrigger by androidx.compose.runtime.rememberUpdatedState(AppCache.pickPluginFileTrigger.value)
            LaunchedEffect(pluginTrigger) {
                if (pluginTrigger > 0) {
                    AppCache.pickPluginFileTrigger.value = 0 // 立即重置，防止重复触发
                    pluginPicker.launch("application/zip")
                }
            }

            // 视频动态壁纸选择器
            val videoPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let { selectedUri ->
                    try {
                        // 始终复制到 filesDir，确保视频文件不会被系统缓存清理
                        val wallpaperDir = java.io.File(filesDir, "wallpapers")
                        if (!wallpaperDir.exists()) wallpaperDir.mkdirs()
                        val cacheFile = java.io.File(wallpaperDir, "wallpaper_${System.currentTimeMillis()}.mp4")
                        contentResolver.openInputStream(selectedUri)?.use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (cacheFile.exists() && cacheFile.length() > 0) {
                            AppCache.dynamicWallpaperVideoPath.value = cacheFile.absolutePath
                            AppCache.dynamicWallpaperEnabled.value = true
                            // 立即持久化，避免退出应用后丢失
                            try { com.example.adbtoolbox.common.GlassEffectPersistence.saveAll() } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 观察视频选择触发器
            val videoTrigger by androidx.compose.runtime.rememberUpdatedState(AppCache.pickDynamicVideoTrigger.value)
            LaunchedEffect(videoTrigger) {
                if (videoTrigger > 0) {
                    AppCache.pickDynamicVideoTrigger.value = 0
                    videoPicker.launch("video/*")
                }
            }

            MainContent()
        }
    }

    private fun getFilePathFromUri(uri: Uri): String? {
        return try {
            if ("file".equals(uri.scheme, ignoreCase = true)) {
                uri.path
            } else {
                // 尝试从MediaStore查询
                val projection = arrayOf(android.provider.MediaStore.Images.Media.DATA)
                val cursor = contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val columnIndex = it.getColumnIndexOrThrow(projection[0])
                        it.getString(columnIndex)
                    } else null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuListener)
        } catch (e: Exception) {
        }
    }
}
