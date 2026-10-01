package com.example.adbtoolbox.common.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset

/**
 * 全局动效规范。
 *
 * 为什么要有这个文件：界面"看起来像 AI 拼的"，最常见的原因不是配色，而是**动效不统一**——
 * 有的地方 300ms 线性、有的地方 400ms 缓出，按下去有的弹、有的不弹，页面切换又是硬切。
 * 把所有时长与曲线收敛到这一处，各处只引用常量，观感立刻就"像一个团队做的"。
 *
 * 取值原则：
 * - 短。按压反馈 90~140ms，状态切换 180~220ms。超过 300ms 的位移会显得迟钝。
 * - 进场用 decelerate（快起慢停），退场用 accelerate（慢起快收）。
 * - 弹簧只用于"手指跟着走"的物理反馈（按压/拖动/回弹），其余一律用 tween，避免到处都是果冻感。
 */
object AppMotion {

    // ------------------------------------------------------------------ 时长（毫秒）

    /** 极快：按压亮度、勾选、徽标出现 */
    const val instant = 90
    /** 快：按钮缩放回弹、开关切换 */
    const val fast = 140
    /** 常规：卡片/列表项状态变化、折叠展开 */
    const val normal = 220
    /** 慢：页面切换、较大的展开 */
    const val slow = 320

    // ------------------------------------------------------------------ 缓动曲线

    /** 进场：快起慢停。用于出现、展开、淡入。 */
    val enter: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** 退场：慢起快收。用于消失、收起、淡出。 */
    val exit: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** 强调：首尾都缓，用于需要被注意到的变化（如进度条冲到 100%）。 */
    val emphasized: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /** 线性：只用于持续进度这类不需要缓动的地方。 */
    val linear: Easing = CubicBezierEasing(0f, 0f, 1f, 1f)

    // ------------------------------------------------------------------ 现成 spec

    /** 淡入 */
    val fadeIn: FiniteAnimationSpec<Float> = tween(durationMillis = normal, easing = enter)

    /** 淡出（比淡入快，符合"消失要干脆"的直觉） */
    val fadeOut: FiniteAnimationSpec<Float> = tween(durationMillis = fast, easing = exit)

    /** 尺寸/位移进场 */
    val moveIn: FiniteAnimationSpec<IntOffset> = tween(durationMillis = slow, easing = enter)

    /**
     * 按压：阻尼 0.86、刚度 1100 —— 有轻微回弹但不至于乱晃。
     * 阻尼低于 0.75 会明显"果冻"，高于 0.95 又会显得僵硬。
     */
    val pressSpring: SpringSpec<Float> = spring(dampingRatio = 0.86f, stiffness = 1100f)

    /** 抬起回弹：比按下更软一点，收尾自然 */
    val releaseSpring: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 700f)

    /** 光斑跟随手指：必须够快，否则手指走了光还在后面追 */
    val followSpring: SpringSpec<Offset> =
        spring(dampingRatio = 0.9f, stiffness = 1400f, visibilityThreshold = Offset.VisibilityThreshold)

    /** 长按反馈淡入淡出 */
    val longPressIn: FiniteAnimationSpec<Float> = tween(durationMillis = 200, easing = enter)
    val longPressOut: FiniteAnimationSpec<Float> = tween(durationMillis = 260, easing = exit)

    /** 长按判定时间（毫秒）。低于 300 容易误触，高于 400 又觉得没反应。 */
    const val longPressDelayMs = 340L
}
