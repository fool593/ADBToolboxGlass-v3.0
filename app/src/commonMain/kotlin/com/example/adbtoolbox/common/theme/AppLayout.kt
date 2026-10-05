package com.example.adbtoolbox.common.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 全局布局规范：间距、卡片内边距、字号。
 *
 * 存在的理由和 [AppMotion] 一样——"看起来不像一个团队做的"，一半来自动效不统一，
 * 另一半来自间距和字号各写各的。这个文件把各页面**已经在用的主流取值**固化成 token，
 * 目的是把少数跑偏的值收敛回来，而不是把所有页面推倒重排。
 *
 * 取值依据（对 app/src/commonMain/.../common/ui 下各页面的现状统计）：
 * - 页面左右内边距：17 个页面用 16dp（Apps/ADBPanel/ADBModule/AppDetail/DeviceInfo/
 *   GlassPlayground/Home/Permissions/Plugins/RootManager/RootModule/Settings/
 *   ShellExecutor/Terminal 等），无 20dp 或 24dp 的页面级用法 -> [screenH] = 16dp。
 * - 卡片内容内边距：20dp 是主流（Home/ADBPanel/ADBModule/AppDetail/DeviceInfo/
 *   GlassPlayground/Settings/TempRoot/RootManager/PerfWidgets/PerfWidgets 等约 30 处）；
 *   16dp 出现在 Permissions、Plugins、RootTool、RootModule、ShellExecutor、HuaweiBoost；
 *   18dp 出现在 PerformanceBoost、PhoneInspector -> [cardPad] = 20dp，
 *   16dp 保留为 [cardPadCompact] 供信息密度高的列表卡使用，18dp 视为应被收敛的中间值。
 * - 顶部标题：24sp + Bold 是绝对主流（Apps/ADBModule/ADBPanel/DeviceInfo/GlassPlayground/
 *   Permissions/Settings 等），仅 Home 用 28sp、HuaweiBoost 用 22sp -> [titleSize] = 24sp。
 * - 分区标题：18sp + Medium 是主流（Home/AppDetail/GlassPlayground 全部小节标题等），
 *   16sp（DeviceInfo/ADBModule/Permissions/RootTool）与 17sp（PerfWidgets/
 *   PerformanceBoost/HuaweiBoost/ThemeBanner）是少数派 -> [sectionTitleSize] = 18sp。
 * - 正文：14sp 最常用；次要说明：12sp 与 11sp 混用，其中 11sp 更接近"注释"层级
 *   -> [bodySize] = 14sp、[captionSize] = 11sp。
 * - 顶部返回按钮到标题的间距：主流是 12dp（Permissions/HuaweiBoost/RootModule），
 *   故 [headerGap] = 12dp。
 * - 返回按钮：组件内固定 40dp，字形 24sp -> [backButtonSize] / [backButtonGlyphSize]。
 *
 * 用法约定：只替换**数值**，不改布局结构、不改信息层级、不删功能入口、不动配色。
 * 拿不准的地方就不动——token 的存在是为了收敛，不是为了制造大面积 diff。
 */
object AppLayout {

    // ------------------------------------------------------------------ 页面骨架

    /** 页面左右安全内边距。所有整屏可滚动页面的水平 padding。 */
    val screenH: Dp = 16.dp

    /** 页面顶部留白（第一个元素之前的空白）。 */
    val screenTop: Dp = 24.dp

    /** 返回按钮与页面标题之间的水平间距。 */
    val headerGap: Dp = 12.dp

    // ------------------------------------------------------------------ 纵向节奏

    /** 卡片与卡片之间（分区之间）的间距。 */
    val sectionGap: Dp = 16.dp

    /** 卡片内部元素之间的间距，对应现状里最常见的 Arrangement.spacedBy(12dp)。 */
    val innerGap: Dp = 12.dp

    // ------------------------------------------------------------------ 卡片

    /** 卡片内容内边距（四边统一）。 */
    val cardPad: Dp = 20.dp

    /**
     * 紧凑卡片内边距。用于行高小、条目多的列表卡，
     * 与 [cardPad] 的差别是刻意的信息密度取舍，不是随手写的值。
     */
    val cardPadCompact: Dp = 16.dp

    // ------------------------------------------------------------------ 返回按钮

    /** 玻璃返回按钮的边长（正方形）。 */
    val backButtonSize: Dp = 40.dp

    /** 返回按钮里 "‹" 字形的字号。 */
    val backButtonGlyphSize: TextUnit = 24.sp

    // ------------------------------------------------------------------ 字号

    /** 页面主标题（配合 FontWeight.Bold 使用）。 */
    val titleSize: TextUnit = 24.sp

    /** 卡片内分区标题（配合 FontWeight.Medium 或 Bold 使用）。 */
    val sectionTitleSize: TextUnit = 18.sp

    /** 正文与主要说明文字。 */
    val bodySize: TextUnit = 14.sp

    /** 次要说明、单位、状态等注释级文字。 */
    val captionSize: TextUnit = 11.sp
}
