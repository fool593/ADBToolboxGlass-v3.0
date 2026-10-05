package com.example.adbtoolbox.common.theme

import androidx.compose.ui.graphics.Color
import com.example.adbtoolbox.common.AppSettings
import com.example.adbtoolbox.common.GlassEffectConfig

/**
 * 应用主题（配色方案）。
 *
 * 设计原则：
 * 1. 主题不是"换一层贴图"，而是直接写进 [GlassEffectConfig]——液态玻璃的叠加色、长按发光色、
 *    折射强度、圆角、导航指示器颜色全部跟着主题走。这样**不需要改任何已有页面**就能整体换色，
 *    是成本最低、最不容易出错的做法。
 * 2. [id] 直接读取 [AppSettings.themeId]（mutableStateOf），因此主题切换会自动触发重组，
 *    不需要额外的状态同步，也不会出现"颜色改了但界面不刷新"的假生效。
 * 3. 每个主题只覆盖"视觉参数"，不改用户的语言/深浅模式/动态壁纸等无关设置。
 *
 * 当前内置：
 * - [CLASSIC]       经典：程序原有配色，默认值，不改变任何东西。
 * - [NATIONAL_DAY]  国庆：中国红 + 金属金，深红玻璃、金色长按辉光、方正一些的圆角（庄重）。
 * - [HUAWEI]        华为：华为红 + 石墨黑，冷调石墨玻璃、红色长按辉光，偏高刷的玻璃强度。
 */
object AppTheme {

    const val CLASSIC = "classic"
    const val NATIONAL_DAY = "national_day"
    const val HUAWEI = "huawei"

    // ------------------------------------------------------------------ 调色板

    /**
     * 一套完整主题配色。
     *
     * [glassTint] / [glowColor] / [glowIntensity] / [glowSize] / [refraction] / [corner] / [intensity]
     * 会在 [apply] 时写入 [GlassEffectConfig]；其余字段供界面（横幅、按钮、色块预览）直接取用。
     */
    data class ThemePalette(
        val id: String,
        /** 主题名文案 key（AppStrings） */
        val nameKey: String,
        /** 主题说明文案 key */
        val descKey: String,
        /** 主色：按钮、强调文字、选中态 */
        val accent: Color,
        /** 次色：渐变末端、描边、点缀 */
        val accentAlt: Color,
        /** 深色底：横幅、标题条 */
        val deep: Color,
        /** 主色之上的文字色 */
        val onAccent: Color,
        /** 玻璃叠加色 */
        val glassTint: Color,
        /** 长按边缘发光色 */
        val glowColor: Color,
        val glowIntensity: Float,
        val glowSize: Float,
        val refraction: Float,
        /** 卡片圆角比例 0~1 */
        val corner: Float,
        /** 全局玻璃强度 */
        val intensity: Float,
        /** 预览用的三段渐变色 */
        val preview: List<Color>
    )

    val nationalDay = ThemePalette(
        id = NATIONAL_DAY,
        nameKey = "theme_national_day",
        descKey = "theme_national_day_desc",
        accent = Color(0xFFC8102E),
        accentAlt = Color(0xFFD4AF37),
        deep = Color(0xFF6E0A14),
        onAccent = Color(0xFFFFF6E5),
        glassTint = Color(0xFFB0101E).copy(alpha = 0.20f),
        glowColor = Color(0xFFF5C542),
        glowIntensity = 0.85f,
        glowSize = 0.55f,
        refraction = 0.30f,
        corner = 0.34f,
        intensity = 1f,
        preview = listOf(Color(0xFF8C0F1B), Color(0xFFC8102E), Color(0xFFD4AF37))
    )

    val huawei = ThemePalette(
        id = HUAWEI,
        nameKey = "theme_huawei",
        descKey = "theme_huawei_desc",
        accent = Color(0xFFCF0A2C),
        accentAlt = Color(0xFF3A3A3C),
        deep = Color(0xFF17171A),
        onAccent = Color(0xFFFFFFFF),
        glassTint = Color(0xFF1C1C1E).copy(alpha = 0.30f),
        glowColor = Color(0xFFFF2D55),
        glowIntensity = 0.70f,
        glowSize = 0.45f,
        refraction = 0.22f,
        corner = 0.5f,
        intensity = 1.05f,
        preview = listOf(Color(0xFF17171A), Color(0xFFCF0A2C), Color(0xFF8C93A8))
    )

    val classic = ThemePalette(
        id = CLASSIC,
        nameKey = "theme_classic",
        descKey = "theme_classic_desc",
        accent = Color(0xFF0088FF),
        accentAlt = Color(0xFF34C759),
        deep = Color(0xFF10131A),
        onAccent = Color(0xFFFFFFFF),
        glassTint = Color.White.copy(alpha = 0.15f),
        glowColor = Color.White,
        glowIntensity = 0.6f,
        glowSize = 0.5f,
        refraction = 0.2f,
        corner = 0.5f,
        intensity = 1f,
        preview = listOf(Color(0xFF10131A), Color(0xFF0088FF), Color(0xFF5AC8FA))
    )

    /** 顺序即设置页的展示顺序：经典 → 国庆 → 华为。 */
    val all: List<ThemePalette> = listOf(classic, nationalDay, huawei)

    fun byId(id: String): ThemePalette = all.firstOrNull { it.id == id } ?: classic

    // ------------------------------------------------------------------ 当前主题

    /** 当前主题 id，直接绑定 [AppSettings.themeId]，切换即重组。 */
    val id: String get() = AppSettings.themeId

    val palette: ThemePalette get() = byId(id)

    /** 主色。界面里需要强调色时用它，不要再硬编码 `Color(0xFF0088FF)`。 */
    val accent: Color get() = palette.accent

    /** 次色。 */
    val accentAlt: Color get() = palette.accentAlt

    /** 深色底。 */
    val deep: Color get() = palette.deep

    /** 主色上的文字色。 */
    val onAccent: Color get() = palette.onAccent

    /** 是否为国庆主题（首页横幅、界面装饰用）。 */
    val isNationalDay: Boolean get() = id == NATIONAL_DAY

    /** 是否为华为主题。 */
    val isHuawei: Boolean get() = id == HUAWEI

    // ------------------------------------------------------------------ 应用主题

    /**
     * 应用主题：写入 [AppSettings.themeId]，并把视觉参数落到 [GlassEffectConfig]。
     *
     * [persist] 为 true 时会调用 [com.example.adbtoolbox.common.GlassEffectPersistence.saveAll]
     * 立即落盘；[loadAll] 恢复设置时传 false，避免重复写盘。
     */
    fun apply(themeId: String, persist: Boolean = true) {
        val p = byId(themeId)
        AppSettings.themeId = p.id
        // 选了主题 = 从现在起以主题这套参数为准，之前的手动调整作废。
        // 这保证了"选完主题后无论怎么重启，主题效果都在"。
        AppSettings.themeUserOverrode = false

        val c = GlassEffectConfig
        c.glassColor.value = p.glassTint
        c.longPressGlowColor.value = p.glowColor
        c.longPressGlowIntensity.value = p.glowIntensity
        c.longPressGlowSize.value = p.glowSize
        c.longPressRefraction.value = p.refraction
        c.cardCornerRadius.value = p.corner
        c.cornerRadius.value = p.corner
        c.globalIntensity.value = p.intensity
        // 字体颜色跟随深浅模式（Unspecified），避免红金主题下浅色模式字看不清
        c.fontColor.value = Color.Unspecified
        // 导航胶囊指示器跟着主色走，否则蓝胶囊配红主题会非常突兀
        c.navIndicatorColor.value = p.accent.copy(alpha = 0.3f)

        if (persist) {
            try {
                com.example.adbtoolbox.common.GlassEffectPersistence.saveAll()
            } catch (_: Exception) {
            }
        }
    }

    /** 恢复经典主题（等价于把玻璃参数恢复成程序默认值）。 */
    fun reset(persist: Boolean = true) {
        apply(CLASSIC, persist)
        if (persist) {
            try {
                com.example.adbtoolbox.common.GlassEffectPersistence.saveAll()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * 国庆档期：10 月 1 日 — 10 月 7 日。
     *
     * 用于"首次启动且用户从未选过主题"时自动套用国庆主题——只在没有历史选择时生效，
     * 用户一旦自己选过（哪怕选的还是国庆），就完全尊重用户的选择。
     */
    fun isNationalDaySeason(month: Int, dayOfMonth: Int): Boolean =
        month == 10 && dayOfMonth in 1..7
}

/** 顶层别名：`ThemePalette` 与 `AppTheme.ThemePalette` 两种写法都能解析，调用方少踩一个坑。 */
typealias ThemePalette = AppTheme.ThemePalette
