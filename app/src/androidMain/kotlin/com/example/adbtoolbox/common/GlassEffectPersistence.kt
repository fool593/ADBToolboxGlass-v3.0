package com.example.adbtoolbox.common

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color

// 液态玻璃效果持久化 actual 实现（使用 SharedPreferences）
actual object GlassEffectPersistence {
    private const val PREFS_NAME = "glass_effect_config"
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun ensurePrefs() {
        if (!::prefs.isInitialized) {
            prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        }
    }

    actual fun saveAll() {
        ensurePrefs()
        val config = GlassEffectConfig
        val editor = prefs.edit()

        // 基础效果
        editor.putFloat("cornerRadius", config.cornerRadius.value)
        editor.putFloat("blurRadius", config.blurRadius.value)
        editor.putFloat("refractionHeight", config.refractionHeight.value)
        editor.putFloat("refractionAmount", config.refractionAmount.value)
        editor.putFloat("chromaticAberration", config.chromaticAberration.value)
        editor.putBoolean("enableVibrancy", config.enableVibrancy.value)
        editor.putFloat("globalIntensity", config.globalIntensity.value)

        // 导航栏效果
        editor.putFloat("navBlurRadius", config.navBlurRadius.value)
        editor.putFloat("navOpacity", config.navOpacity.value)
        editor.putFloat("navCornerRadius", config.navCornerRadius.value)
        editor.putFloat("navRefractionHeight", config.navRefractionHeight.value)
        editor.putFloat("navRefractionAmount", config.navRefractionAmount.value)
        editor.putFloat("navChromaticAberration", config.navChromaticAberration.value)
        editor.putBoolean("navEnableVibrancy", config.navEnableVibrancy.value)

        // 导航栏胶囊
        editor.putString("navIndicatorShape", config.navIndicatorShape.value)
        editor.putFloat("navIndicatorHeight", config.navIndicatorHeight.value)
        editor.putFloat("navIndicatorWidth", config.navIndicatorWidth.value)
        editor.putFloat("navIndicatorCorner", config.navIndicatorCorner.value)
        editor.putFloat("navIndicatorBlur", config.navIndicatorBlur.value)
        editor.putFloat("navIndicatorOpacity", config.navIndicatorOpacity.value)
        editor.putInt("navIndicatorColor", config.navIndicatorColor.value.value.toInt())

        // 卡片效果
        editor.putFloat("cardBlurRadius", config.cardBlurRadius.value)
        editor.putFloat("cardOpacity", config.cardOpacity.value)
        editor.putFloat("cardCornerRadius", config.cardCornerRadius.value)

        // 按钮效果
        editor.putFloat("buttonBlurRadius", config.buttonBlurRadius.value)
        editor.putFloat("buttonOpacity", config.buttonOpacity.value)

        // 主界面
        editor.putFloat("homeBlurRadius", config.homeBlurRadius.value)
        editor.putFloat("homeOpacity", config.homeOpacity.value)
        editor.putFloat("homeCornerRadius", config.homeCornerRadius.value)
        editor.putFloat("homeRefractionHeight", config.homeRefractionHeight.value)
        editor.putFloat("homeRefractionAmount", config.homeRefractionAmount.value)
        editor.putBoolean("homeEnableVibrancy", config.homeEnableVibrancy.value)

        // 终端
        editor.putFloat("terminalBlurRadius", config.terminalBlurRadius.value)
        editor.putFloat("terminalOpacity", config.terminalOpacity.value)
        editor.putFloat("terminalCornerRadius", config.terminalCornerRadius.value)
        editor.putFloat("terminalRefractionHeight", config.terminalRefractionHeight.value)
        editor.putFloat("terminalRefractionAmount", config.terminalRefractionAmount.value)
        editor.putBoolean("terminalEnableVibrancy", config.terminalEnableVibrancy.value)

        // 设置
        editor.putFloat("settingsBlurRadius", config.settingsBlurRadius.value)
        editor.putFloat("settingsOpacity", config.settingsOpacity.value)
        editor.putFloat("settingsCornerRadius", config.settingsCornerRadius.value)
        editor.putFloat("settingsRefractionHeight", config.settingsRefractionHeight.value)
        editor.putFloat("settingsRefractionAmount", config.settingsRefractionAmount.value)
        editor.putBoolean("settingsEnableVibrancy", config.settingsEnableVibrancy.value)

        // 应用管理
        editor.putFloat("appsBlurRadius", config.appsBlurRadius.value)
        editor.putFloat("appsOpacity", config.appsOpacity.value)
        editor.putFloat("appsCornerRadius", config.appsCornerRadius.value)
        editor.putFloat("appsRefractionHeight", config.appsRefractionHeight.value)
        editor.putFloat("appsRefractionAmount", config.appsRefractionAmount.value)
        editor.putBoolean("appsEnableVibrancy", config.appsEnableVibrancy.value)

        // 插件页面
        editor.putFloat("pluginsBlurRadius", config.pluginsBlurRadius.value)
        editor.putFloat("pluginsOpacity", config.pluginsOpacity.value)
        editor.putFloat("pluginsCornerRadius", config.pluginsCornerRadius.value)
        editor.putFloat("pluginsRefractionHeight", config.pluginsRefractionHeight.value)
        editor.putFloat("pluginsRefractionAmount", config.pluginsRefractionAmount.value)
        editor.putBoolean("pluginsEnableVibrancy", config.pluginsEnableVibrancy.value)

        // ADB模块页面
        editor.putFloat("adbmoduleBlurRadius", config.adbmoduleBlurRadius.value)
        editor.putFloat("adbmoduleOpacity", config.adbmoduleOpacity.value)
        editor.putFloat("adbmoduleCornerRadius", config.adbmoduleCornerRadius.value)
        editor.putFloat("adbmoduleRefractionHeight", config.adbmoduleRefractionHeight.value)
        editor.putFloat("adbmoduleRefractionAmount", config.adbmoduleRefractionAmount.value)
        editor.putBoolean("adbmoduleEnableVibrancy", config.adbmoduleEnableVibrancy.value)

        // 颜色（fontColor 用 -1 标记 Unspecified，避免加载时变成透明黑）
        editor.putInt("glassColor", config.glassColor.value.value.toInt())
        editor.putInt("fontColor", if (config.fontColor.value == Color.Unspecified) -1 else config.fontColor.value.value.toInt())

        // 动态壁纸
        editor.putBoolean("dynamicWallpaperEnabled", AppCache.dynamicWallpaperEnabled.value)
        val videoPath = AppCache.dynamicWallpaperVideoPath.value
        editor.putString("dynamicWallpaperVideoPath", if (videoPath != null && java.io.File(videoPath).exists()) videoPath else null)

        editor.apply()
    }

    actual fun loadAll() {
        ensurePrefs()
        val config = GlassEffectConfig

        // 基础效果
        if (prefs.contains("cornerRadius")) config.cornerRadius.value = prefs.getFloat("cornerRadius", 0.5f)
        if (prefs.contains("blurRadius")) config.blurRadius.value = prefs.getFloat("blurRadius", 16f)
        if (prefs.contains("refractionHeight")) config.refractionHeight.value = prefs.getFloat("refractionHeight", 0.2f)
        if (prefs.contains("refractionAmount")) config.refractionAmount.value = prefs.getFloat("refractionAmount", 0.2f)
        if (prefs.contains("chromaticAberration")) config.chromaticAberration.value = prefs.getFloat("chromaticAberration", 0f)
        if (prefs.contains("enableVibrancy")) config.enableVibrancy.value = prefs.getBoolean("enableVibrancy", true)
        if (prefs.contains("globalIntensity")) config.globalIntensity.value = prefs.getFloat("globalIntensity", 1f)

        // 导航栏效果
        if (prefs.contains("navBlurRadius")) config.navBlurRadius.value = prefs.getFloat("navBlurRadius", 8f)
        if (prefs.contains("navOpacity")) config.navOpacity.value = prefs.getFloat("navOpacity", 0.4f)
        if (prefs.contains("navCornerRadius")) config.navCornerRadius.value = prefs.getFloat("navCornerRadius", 0.6f)
        if (prefs.contains("navRefractionHeight")) config.navRefractionHeight.value = prefs.getFloat("navRefractionHeight", 0.15f)
        if (prefs.contains("navRefractionAmount")) config.navRefractionAmount.value = prefs.getFloat("navRefractionAmount", 0.15f)
        if (prefs.contains("navChromaticAberration")) config.navChromaticAberration.value = prefs.getFloat("navChromaticAberration", 0f)
        if (prefs.contains("navEnableVibrancy")) config.navEnableVibrancy.value = prefs.getBoolean("navEnableVibrancy", true)

        // 导航栏胶囊
        if (prefs.contains("navIndicatorShape")) config.navIndicatorShape.value = prefs.getString("navIndicatorShape", "capsule") ?: "capsule"
        if (prefs.contains("navIndicatorHeight")) config.navIndicatorHeight.value = prefs.getFloat("navIndicatorHeight", 50f)
        if (prefs.contains("navIndicatorWidth")) config.navIndicatorWidth.value = prefs.getFloat("navIndicatorWidth", 0f)
        if (prefs.contains("navIndicatorCorner")) config.navIndicatorCorner.value = prefs.getFloat("navIndicatorCorner", 0.5f)
        if (prefs.contains("navIndicatorBlur")) config.navIndicatorBlur.value = prefs.getFloat("navIndicatorBlur", 12f)
        if (prefs.contains("navIndicatorOpacity")) config.navIndicatorOpacity.value = prefs.getFloat("navIndicatorOpacity", 0.6f)
        if (prefs.contains("navIndicatorColor")) config.navIndicatorColor.value = Color(prefs.getInt("navIndicatorColor", Color(0xFF007AFF).copy(alpha = 0.5f).value.toInt()))

        // 卡片效果
        if (prefs.contains("cardBlurRadius")) config.cardBlurRadius.value = prefs.getFloat("cardBlurRadius", 20f)
        if (prefs.contains("cardOpacity")) config.cardOpacity.value = prefs.getFloat("cardOpacity", 0.3f)
        if (prefs.contains("cardCornerRadius")) config.cardCornerRadius.value = prefs.getFloat("cardCornerRadius", 0.5f)

        // 按钮效果
        if (prefs.contains("buttonBlurRadius")) config.buttonBlurRadius.value = prefs.getFloat("buttonBlurRadius", 12f)
        if (prefs.contains("buttonOpacity")) config.buttonOpacity.value = prefs.getFloat("buttonOpacity", 0.5f)

        // 主界面
        if (prefs.contains("homeBlurRadius")) config.homeBlurRadius.value = prefs.getFloat("homeBlurRadius", 20f)
        if (prefs.contains("homeOpacity")) config.homeOpacity.value = prefs.getFloat("homeOpacity", 0.3f)
        if (prefs.contains("homeCornerRadius")) config.homeCornerRadius.value = prefs.getFloat("homeCornerRadius", 0.5f)
        if (prefs.contains("homeRefractionHeight")) config.homeRefractionHeight.value = prefs.getFloat("homeRefractionHeight", 0.2f)
        if (prefs.contains("homeRefractionAmount")) config.homeRefractionAmount.value = prefs.getFloat("homeRefractionAmount", 0.2f)
        if (prefs.contains("homeEnableVibrancy")) config.homeEnableVibrancy.value = prefs.getBoolean("homeEnableVibrancy", true)

        // 终端
        if (prefs.contains("terminalBlurRadius")) config.terminalBlurRadius.value = prefs.getFloat("terminalBlurRadius", 16f)
        if (prefs.contains("terminalOpacity")) config.terminalOpacity.value = prefs.getFloat("terminalOpacity", 0.35f)
        if (prefs.contains("terminalCornerRadius")) config.terminalCornerRadius.value = prefs.getFloat("terminalCornerRadius", 0.4f)
        if (prefs.contains("terminalRefractionHeight")) config.terminalRefractionHeight.value = prefs.getFloat("terminalRefractionHeight", 0.15f)
        if (prefs.contains("terminalRefractionAmount")) config.terminalRefractionAmount.value = prefs.getFloat("terminalRefractionAmount", 0.15f)
        if (prefs.contains("terminalEnableVibrancy")) config.terminalEnableVibrancy.value = prefs.getBoolean("terminalEnableVibrancy", true)

        // 设置
        if (prefs.contains("settingsBlurRadius")) config.settingsBlurRadius.value = prefs.getFloat("settingsBlurRadius", 18f)
        if (prefs.contains("settingsOpacity")) config.settingsOpacity.value = prefs.getFloat("settingsOpacity", 0.3f)
        if (prefs.contains("settingsCornerRadius")) config.settingsCornerRadius.value = prefs.getFloat("settingsCornerRadius", 0.5f)
        if (prefs.contains("settingsRefractionHeight")) config.settingsRefractionHeight.value = prefs.getFloat("settingsRefractionHeight", 0.18f)
        if (prefs.contains("settingsRefractionAmount")) config.settingsRefractionAmount.value = prefs.getFloat("settingsRefractionAmount", 0.18f)
        if (prefs.contains("settingsEnableVibrancy")) config.settingsEnableVibrancy.value = prefs.getBoolean("settingsEnableVibrancy", true)

        // 应用管理
        if (prefs.contains("appsBlurRadius")) config.appsBlurRadius.value = prefs.getFloat("appsBlurRadius", 18f)
        if (prefs.contains("appsOpacity")) config.appsOpacity.value = prefs.getFloat("appsOpacity", 0.3f)
        if (prefs.contains("appsCornerRadius")) config.appsCornerRadius.value = prefs.getFloat("appsCornerRadius", 0.5f)
        if (prefs.contains("appsRefractionHeight")) config.appsRefractionHeight.value = prefs.getFloat("appsRefractionHeight", 0.18f)
        if (prefs.contains("appsRefractionAmount")) config.appsRefractionAmount.value = prefs.getFloat("appsRefractionAmount", 0.18f)
        if (prefs.contains("appsEnableVibrancy")) config.appsEnableVibrancy.value = prefs.getBoolean("appsEnableVibrancy", true)

        // 插件页面
        if (prefs.contains("pluginsBlurRadius")) config.pluginsBlurRadius.value = prefs.getFloat("pluginsBlurRadius", 18f)
        if (prefs.contains("pluginsOpacity")) config.pluginsOpacity.value = prefs.getFloat("pluginsOpacity", 0.3f)
        if (prefs.contains("pluginsCornerRadius")) config.pluginsCornerRadius.value = prefs.getFloat("pluginsCornerRadius", 0.5f)
        if (prefs.contains("pluginsRefractionHeight")) config.pluginsRefractionHeight.value = prefs.getFloat("pluginsRefractionHeight", 0.18f)
        if (prefs.contains("pluginsRefractionAmount")) config.pluginsRefractionAmount.value = prefs.getFloat("pluginsRefractionAmount", 0.18f)
        if (prefs.contains("pluginsEnableVibrancy")) config.pluginsEnableVibrancy.value = prefs.getBoolean("pluginsEnableVibrancy", true)

        // ADB模块页面
        if (prefs.contains("adbmoduleBlurRadius")) config.adbmoduleBlurRadius.value = prefs.getFloat("adbmoduleBlurRadius", 18f)
        if (prefs.contains("adbmoduleOpacity")) config.adbmoduleOpacity.value = prefs.getFloat("adbmoduleOpacity", 0.3f)
        if (prefs.contains("adbmoduleCornerRadius")) config.adbmoduleCornerRadius.value = prefs.getFloat("adbmoduleCornerRadius", 0.5f)
        if (prefs.contains("adbmoduleRefractionHeight")) config.adbmoduleRefractionHeight.value = prefs.getFloat("adbmoduleRefractionHeight", 0.18f)
        if (prefs.contains("adbmoduleRefractionAmount")) config.adbmoduleRefractionAmount.value = prefs.getFloat("adbmoduleRefractionAmount", 0.18f)
        if (prefs.contains("adbmoduleEnableVibrancy")) config.adbmoduleEnableVibrancy.value = prefs.getBoolean("adbmoduleEnableVibrancy", true)

        // 颜色
        if (prefs.contains("glassColor")) config.glassColor.value = Color(prefs.getInt("glassColor", Color.White.copy(alpha = 0.15f).value.toInt()))
        if (prefs.contains("fontColor")) {
            val fv = prefs.getInt("fontColor", -1)
            config.fontColor.value = if (fv == -1) Color.Unspecified else Color(fv)
        }

        // 动态壁纸
        if (prefs.contains("dynamicWallpaperEnabled")) {
            AppCache.dynamicWallpaperEnabled.value = prefs.getBoolean("dynamicWallpaperEnabled", false)
        }
        if (prefs.contains("dynamicWallpaperVideoPath")) {
            val savedPath = prefs.getString("dynamicWallpaperVideoPath", null)
            if (savedPath != null && java.io.File(savedPath).exists()) {
                AppCache.dynamicWallpaperVideoPath.value = savedPath
            } else {
                AppCache.dynamicWallpaperVideoPath.value = null
                AppCache.dynamicWallpaperEnabled.value = false
            }
        }
    }

    actual fun clear() {
        ensurePrefs()
        prefs.edit().clear().apply()
    }
}
