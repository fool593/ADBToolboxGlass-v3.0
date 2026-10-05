package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.example.adbtoolbox.common.GlassEffectConfig
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.highlight.HighlightStyle
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangle
import kotlin.math.abs
import kotlin.math.tanh

/**
 * 液态玻璃可点击项。
 * 保留玻璃原始颜色（tint）+ 模糊/折射/活力效果，
 * 按压高光绘制在玻璃之上并裁剪为圆角形状，拖动时缩放/平移/边缘拉伸。
 *
 * 长按（350ms）时会真正读取 [GlassEffectConfig] 的四个长按配置项：
 * longPressGlowIntensity / longPressGlowSize / longPressGlowColor（外发光 + 边缘描边）、
 * longPressRefraction（加强折射）。这些都写在**绘制阶段**求值的 lambda 里，
 * 因此设置页拖动滑块能实时看到变化，也不需要为此重建 RenderEffect。
 *
 * shape/effects/layerBlock/onDrawSurface 均 remember 缓存，避免重组时重建 RenderEffect 造成卡顿。
 */
fun Modifier.liquidGlassItem(
    backdrop: Backdrop,
    corner: Dp = 16.dp,
    blurPx: Float = 36f,
    lensIn: Float = 24f,
    lensOut: Float = 48f,
    tint: Color = Color.Unspecified,
    onClick: (() -> Unit)? = null
): Modifier = composed {
    val scope = rememberCoroutineScope()
    val shape: Shape = remember(corner) { RoundedRectangle(corner) }
    val rimPath = remember { Path() }
    val highlight = remember(scope, shape) {
        InteractiveHighlight(
            animationScope = scope,
            shape = shape,
            drawAboveContent = true,
            // 传 lambda 而不是取值：绘制阶段求值 → 设置页改颜色/强度立刻生效
            glowColor = { GlassEffectConfig.longPressGlowColor.value },
            glowIntensity = { 0.65f + 0.35f * GlassEffectConfig.longPressGlowIntensity.value },
            edgeBoost = 0.75f
        )
    }
    // 长按进度的量化快照（1/16 台阶）。
    // effects 里的读数发生在 observeReads 观察区内：直接读动画值，长按期间每一帧都会被判定
    // "参数变了"，于是每帧重建整条 RenderEffect 链（ColorFilterEffect + BlurEffect +
    // RuntimeShaderEffect + createChainEffect，全是原生对象）。改读这个 derivedState 后，
    // 只有跨过 1/16 台阶才算变化，整段动画只重建十几次；1/16 的折射增强台阶看不出来。
    val longPressStep = remember(highlight) {
        derivedStateOf { (highlight.longPressProgress * 16f).toInt() / 16f }
    }
    val effects: BackdropEffectScope.() -> Unit = remember(highlight, blurPx, lensIn, lensOut) {
        {
            // 全局渲染强度映射 0.5~1.5 倍；长按按 longPressRefraction 加强折射（带 clamp，防止渲染崩坏）
            val gEff = 0.5f + GlassEffectConfig.globalIntensity.value * 0.5f
            val lp = longPressStep.value
            val boost = 1f + 0.8f * lp * GlassEffectConfig.longPressRefraction.value
            vibrancy()
            blur((blurPx * gEff).coerceAtMost(40f.dp.toPx()))
            lens(
                (lensIn * gEff * boost).coerceAtMost(72f.dp.toPx()),
                (lensOut * gEff * boost).coerceAtMost(112f.dp.toPx())
            )
        }
    }
    val highlightEffect: () -> Highlight? = remember(highlight) {
        {
            val lp = highlight.longPressProgress
            val glowIntensity = GlassEffectConfig.longPressGlowIntensity.value
            val glowSize = GlassEffectConfig.longPressGlowSize.value
            if (lp <= 0.01f) {
                Highlight.Default
            } else {
                // 长按：描边换成配置的发光色，变粗并带模糊 → 边缘发光
                Highlight(
                    width = (0.5f + 1.5f * glowSize).dp,
                    blurRadius = (0.5f + 5f * glowSize).dp,
                    alpha = (0.35f + 0.65f * lp * glowIntensity).coerceIn(0f, 1f),
                    style = HighlightStyle.Plain(
                        color = GlassEffectConfig.longPressGlowColor.value.copy(alpha = 1f),
                        blendMode = BlendMode.Plus
                    )
                )
            }
        }
    }
    val shadowEffect: () -> Shadow? = remember(highlight) {
        {
            val lp = highlight.longPressProgress
            val glowIntensity = GlassEffectConfig.longPressGlowIntensity.value
            val glowSize = GlassEffectConfig.longPressGlowSize.value
            if (lp <= 0.01f) {
                // 静息态不画投影。Shadow.Default 是「纯黑 10% + 24dp 模糊 + 向下偏 4dp」，
                // 它糊在玻璃下面，让边缘看起来是一条暗边——用户明确要求这里应该是亮边，不是黑影。
                null
            } else {
                // 长按：只留一圈紧贴边缘的薄发光（半径 4~12dp、透明度上限 0.3），
                // 不再用之前 10~32dp 的大范围光晕往卡片外面飘
                Shadow(
                    radius = (4f + 8f * glowSize).dp,
                    offset = DpOffset.Zero,
                    color = GlassEffectConfig.longPressGlowColor.value,
                    alpha = (0.30f * lp * glowIntensity).coerceIn(0f, 1f)
                )
            }
        }
    }
    val layerBlock: GraphicsLayerScope.() -> Unit = remember(highlight) {
        {
            // graphicsLayer 的 block 在绘制阶段执行：这里读到的 pressProgress / offset 永远是最新值
            // （原来的现象是按下几乎看不到位移反馈 —— 因为位移系数只有 0.05，而不是 remember 的 key 问题）
            val width = size.width
            val height = size.height
            if (width > 0f && height > 0f) {
                val progress = highlight.pressProgress
                val maxOffset = size.minDimension
                val off = highlight.offset
                var sx = 1f + 0.045f * progress
                var sy = 1f + 0.045f * progress
                // 拖动位移收敛为 ≤4dp 的弹性应变。
                // 原来是 maxOffset * tanh(0.22 * off / maxOffset)：最大能平移整整一块玻璃
                // （maxOffset = 短边）。位移本身不是反馈的关键，但"把整块玻璃搬走"会让任何
                // 相对位置/图层缓存的偏差被放大成一整块方块的错位，所以这里压到几 dp；
                // 按下光斑、按压缩放、长按发光全部保留。
                val maxStrain = 4f.dp.toPx()
                translationX = maxStrain * tanh(off.x / maxOffset)
                translationY = maxStrain * tanh(off.y / maxOffset)
                val maxDragScale = 3f.dp.toPx() / height
                sx += maxDragScale * abs(off.x / size.maxDimension) * (width / height)
                sy += maxDragScale * abs(off.y / size.maxDimension) * (height / width)
                scaleX = sx
                scaleY = sy
            }
        }
    }
    val onDrawSurface: (DrawScope.() -> Unit)? = remember(tint) {
        if (tint != Color.Unspecified) {
            {
                // 只用普通 alpha 叠加着色：Hue 混合在 Plus 叠加的玻璃上会产生发紫/发脏的怪色
                drawRect(tint.copy(alpha = tint.alpha * 0.45f))
            }
        } else {
            null
        }
    }
    // 链序很关键：drawBackdrop 内部会插入 Modifier.graphicsLayer(layerBlock)
    // （见 backdrop/DrawBackdropModifier.kt 的 drawBackdrop 实现），只有排在它"之后"（更靠内）
    // 的绘制节点才会跟着 layerBlock 的平移/缩放一起动。
    // 原顺序是高光与边缘描边写在 drawBackdrop 之前 → 它们画在变换之外的坐标里：
    // 一拖动，玻璃与文字跟着动，描边/光斑却原地不动，留下的就是"和方块一样大的方框"。
    var m: Modifier = this
        .then(highlight.gestureModifier)
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = effects,
            highlight = highlightEffect,
            shadow = shadowEffect,
            layerBlock = layerBlock,
            onDrawSurface = onDrawSurface
        )
        // 按下光斑：drawAboveContent = true ⇒ 在玻璃内容之上，且位于变换之内
        .then(highlight.modifier)
        // 边缘描边：最上层，同样位于变换之内
        .drawWithContent {
            // 边缘高光描边：静息就有一条很淡的亮边（玻璃本该有边缘高光），按压/长按再加强。
            // 关键：整条描边裁在形状内部，只贴在玻璃内侧，不会溢出到轮廓外面。
            drawContent()
            val press = highlight.pressProgress
            val lp = highlight.longPressProgress
            val glowIntensity = GlassEffectConfig.longPressGlowIntensity.value
            val glowSize = GlassEffectConfig.longPressGlowSize.value
            val alpha = (0.10f + 0.16f * press + 0.74f * lp * glowIntensity).coerceIn(0f, 1f)
            if (size.width > 0f && size.height > 0f) {
                rimPath.reset()
                rimPath.addOutline(shape.createOutline(size, layoutDirection, this))
                val strokeWidth =
                    (0.9f.dp.toPx() + 1.6f.dp.toPx() * glowSize * (0.35f + 0.65f * lp))
                        .coerceAtMost(size.minDimension * 0.2f)
                clipPath(rimPath) {
                    drawPath(
                        rimPath,
                        color = GlassEffectConfig.longPressGlowColor.value.copy(alpha = alpha * 0.85f),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
        }
    if (onClick != null) {
        m = m.clickable(interactionSource = null, indication = null, onClick = onClick)
    }
    m
}
