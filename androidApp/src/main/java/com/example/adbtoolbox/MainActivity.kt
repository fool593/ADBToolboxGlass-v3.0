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
            // 授权结果出来后立刻刷新全局 Shizuku 状态，首页/设置页马上就能显示"已连接"
            com.example.adbtoolbox.common.AppCache.requestShizukuRefresh()
        } catch (e: Exception) {
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户很可能刚切到 Shizuku 里启动服务或授权，再切回本应用 —— 回到前台必须重查一次，
        // 否则界面会一直停在"未连接"（这正是用户反馈的"连上了却不显示"）
        try {
            com.example.adbtoolbox.common.AppCache.requestShizukuRefresh()
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

            // 临时 Root 提权包选择器
            val tempRootPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let { selectedUri ->
                    try {
                        val tempRootDir = java.io.File(filesDir, "temp_root")
                        if (!tempRootDir.exists()) tempRootDir.mkdirs()
                        val cacheFile = java.io.File(tempRootDir, "temproot_${System.currentTimeMillis()}.zip")
                        contentResolver.openInputStream(selectedUri)?.use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (cacheFile.exists() && cacheFile.length() > 0) {
                            AppCache.selectedTempRootPath.value = cacheFile.absolutePath
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 观察临时 Root 选择触发器
            val tempRootTrigger by androidx.compose.runtime.rememberUpdatedState(AppCache.pickTempRootFileTrigger.value)
            LaunchedEffect(tempRootTrigger) {
                if (tempRootTrigger > 0) {
                    AppCache.pickTempRootFileTrigger.value = 0
                    tempRootPicker.launch("application/zip")
                }
            }

            // Root 模块（Magisk / KernelSU）安装包选择器：与临时 Root 同一套路，
            // 先把选中的 zip 复制到应用私有目录，再由 RootModuleManager 安装
            val rootModulePicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let { selectedUri ->
                    try {
                        val moduleDir = java.io.File(filesDir, "root_module")
                        if (!moduleDir.exists()) moduleDir.mkdirs()
                        // 每次选择都会复制成一个新文件（路径改变才能重新触发安装），
                        // 所以这里先清掉上一次的缓存 zip，避免堆在私有目录里越积越多
                        moduleDir.listFiles()?.forEach { it.delete() }
                        val cacheFile = java.io.File(moduleDir, "module_${System.currentTimeMillis()}.zip")
                        contentResolver.openInputStream(selectedUri)?.use { input ->
                            cacheFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (cacheFile.exists() && cacheFile.length() > 0) {
                            AppCache.selectedRootModulePath.value = cacheFile.absolutePath
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 观察 Root 模块选择触发器
            val rootModuleTrigger by androidx.compose.runtime.rememberUpdatedState(AppCache.pickRootModuleFileTrigger.value)
            LaunchedEffect(rootModuleTrigger) {
                if (rootModuleTrigger > 0) {
                    AppCache.pickRootModuleFileTrigger.value = 0
                    rootModulePicker.launch("application/zip")
                }
            }

            // 自定义开屏动画视频选择器
            val splashPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri: Uri? ->
                uri?.let { selectedUri ->
                    try {
                        // 复制到 filesDir/splash，确保开屏视频稳定可用
                        val splashDir = java.io.File(filesDir, "splash")
                        if (!splashDir.exists()) splashDir.mkdirs()
                        // 删除旧开屏视频
                        splashDir.listFiles()?.forEach { it.delete() }
                        val splashFile = java.io.File(splashDir, "splash_video.mp4")
                        contentResolver.openInputStream(selectedUri)?.use { input ->
                            splashFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                        if (splashFile.exists() && splashFile.length() > 0) {
                            AppCache.splashVideoPath.value = splashFile.absolutePath
                            // 立即持久化
                            try { com.example.adbtoolbox.common.GlassEffectPersistence.saveAll() } catch (_: Exception) {}
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            // 观察开屏视频选择触发器
            val splashTrigger by androidx.compose.runtime.rememberUpdatedState(AppCache.pickSplashVideoTrigger.value)
            LaunchedEffect(splashTrigger) {
                if (splashTrigger > 0) {
                    AppCache.pickSplashVideoTrigger.value = 0
                    splashPicker.launch("video/*")
                }
            }

            // 观察清除开屏视频触发器
            val clearSplashTrigger by androidx.compose.runtime.rememberUpdatedState(AppCache.clearSplashVideoTrigger.value)
            LaunchedEffect(clearSplashTrigger) {
                if (clearSplashTrigger > 0) {
                    AppCache.clearSplashVideoTrigger.value = 0
                    try {
                        AppCache.splashVideoPath.value?.let { path ->
                            java.io.File(path).delete()
                        }
                        AppCache.splashVideoPath.value = null
                        // 清理整个 splash 目录
                        val splashDir = java.io.File(filesDir, "splash")
                        splashDir.listFiles()?.forEach { it.delete() }
                        try { com.example.adbtoolbox.common.GlassEffectPersistence.saveAll() } catch (_: Exception) {}
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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

    override fun onPause() {
        super.onPause()
        // 应用进入后台时自动保存所有个性化设置，避免滑后台后丢失
        try {
            com.example.adbtoolbox.common.GlassEffectPersistence.saveAll()
        } catch (e: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            Shizuku.removeRequestPermissionResultListener(shizukuListener)
        } catch (e: Exception) {
        }
    }
}
