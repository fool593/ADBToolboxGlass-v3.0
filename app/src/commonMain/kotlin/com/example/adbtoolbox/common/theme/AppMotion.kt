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
import androidx.compose.ui.unit.dp

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

    // ================================================================== 页面级 token
    //
    // 下面的 token 是把"ColorOS 风格动效语言"里**公开可观察**的几条特征
    // （缩放+淡入的页面切换、小幅位移、列表错峰、干脆的按压、退出比进入快）
    // 收敛成一套本工程自己统一的规范。这是风格对齐，不是对任何闭源系统逐帧复制：
    // 时长/阻尼/刚度都是在本工程里试出来的值，不是从系统里提取的参数。
    //
    // 对文件开头约定的一处补充：页面级进场用弹簧而不是 tween。
    // 原因是"下级页面推入、上层页面轻微后退"这类切换需要收尾有一点惯性，
    // 纯 tween 会显得像淡入淡出而不是空间运动。按压/拖动/回弹以外的局部状态
    // 变化仍然按原约定走 tween。

    // ------------------------------------------------------------------ 按压基线

    /**
     * 按压时缩到的比例。
     * 0.96 是"有反馈但不晃"的甜点区：低于 0.94 会觉得软，高于 0.98 则几乎看不见。
     */
    const val pressScale = 0.96f

    // ------------------------------------------------------------------ 页面（整屏）切换

    /**
     * 页面进入的起始缩放：0.92 -> 1.0。
     * 0.92 的意义是"有明确的推出感"，同时不产生缩小窗口的失重感；
     * 低于 0.9 会像缩放动画而不是页面切换。
     */
    const val screenEnterScaleFrom = 0.92f

    /** 页面退出的终止缩放：1.0 -> 1.02。轻微放大表示"被压到下一层"，幅度刻意小于进入。 */
    const val screenExitScaleTo = 1.02f

    /** 页面进入起始透明度（0 -> 1 淡入）。 */
    const val screenEnterAlphaFrom = 0f

    /** 页面退出终止透明度（1 -> 0 淡出）。 */
    const val screenExitAlphaTo = 0f

    /**
     * 页面进入：弹簧、阻尼接近临界（0.86）、刚度偏软（520）。
     * 收尾有极短的过冲，形成"落位"的物理感；刚度再高会变成硬切，再低会拖沓。
     */
    val screenEnterSpec: SpringSpec<Float> = spring(dampingRatio = 0.86f, stiffness = 520f)

    /**
     * 页面退出：不用弹簧，用加速曲线并把时长压到比进入更短。
     * 直觉是"离开要干脆"——用户已经决定走了，不该再等它演完。
     */
    val screenExitSpec: FiniteAnimationSpec<Float> = tween(durationMillis = 200, easing = exit)

    // ------------------------------------------------------------------ 列表错峰进场

    /** 相邻列表项的进场延迟差（毫秒）。40ms 能看出层次，再大会明显变慢。 */
    const val staggerStepMs = 40

    /**
     * 错峰延迟上限（毫秒）。
     * 没有上限的话，第 50 项要等 2 秒，长列表会越滚越慢。
     * 240ms 之后所有项同时进场，视觉上仍是"一批一批出现"。
     */
    const val staggerMaxMs = 240

    /** 列表项进场的垂直位移起点（向上抬 8dp 落位）。只走几 dp，不做大距离滑动。 */
    val listItemEnterOffsetY = 8.dp

    /** 列表项进场缩放起点：0.98 -> 1.0，几乎只用于"不硬切"的边界感。 */
    const val listItemEnterScaleFrom = 0.98f

    /** 列表项进场：比页面进场更快更干脆，避免整屏项一起晃。 */
    val listItemEnterSpec: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 600f)

    // ------------------------------------------------------------------ 对话框 / 浮层

    /** 对话框进场：比页面略快，阻尼 0.9（几乎不过冲），保证文字不抖。 */
    val dialogEnterSpec: SpringSpec<Float> = spring(dampingRatio = 0.9f, stiffness = 700f)

    /** 对话框退出：比进场更短，关闭动作不应该有等待感。 */
    val dialogExitSpec: FiniteAnimationSpec<Float> = tween(durationMillis = 160, easing = exit)
}
