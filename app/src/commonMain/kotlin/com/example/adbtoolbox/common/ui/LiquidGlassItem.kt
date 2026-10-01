package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.clickable
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
    val effects: BackdropEffectScope.() -> Unit = remember(highlight, blurPx, lensIn, lensOut) {
        {
            // 全局渲染强度映射 0.5~1.5 倍；长按按 longPressRefraction 加强折射（带 clamp，防止渲染崩坏）
            val gEff = 0.5f + GlassEffectConfig.globalIntensity.value * 0.5f
            val lp = highlight.longPressProgress
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
                Shadow.Default
            } else {
                // 四周外发光光晕：半径来自 longPressGlowSize（按住期间半径固定，避免每帧重建大图层）
                Shadow(
                    radius = (10f + 22f * glowSize).dp,
                    offset = DpOffset.Zero,
                    color = GlassEffectConfig.longPressGlowColor.value,
                    alpha = (0.6f * lp * glowIntensity).coerceIn(0f, 1f)
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
                translationX = maxOffset * tanh(0.22f * off.x / maxOffset)
                translationY = maxOffset * tanh(0.22f * off.y / maxOffset)
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
    var m: Modifier = this
        .then(highlight.gestureModifier)
        .drawWithContent {
            // 边缘高光描边：按压时轻微出现，长按按配置发光（用形状 outline 描边，圆角精确贴合）
            drawContent()
            val press = highlight.pressProgress
            val lp = highlight.longPressProgress
            val glowIntensity = GlassEffectConfig.longPressGlowIntensity.value
            val glowSize = GlassEffectConfig.longPressGlowSize.value
            val alpha = (0.12f * press + 0.88f * lp * glowIntensity).coerceIn(0f, 1f)
            if (alpha > 0.01f && size.width > 0f && size.height > 0f) {
                rimPath.reset()
                rimPath.addOutline(shape.createOutline(size, layoutDirection, this))
                val strokeWidth =
                    (0.8f.dp.toPx() + 1.8f.dp.toPx() * glowSize * (0.35f + 0.65f * lp))
                        .coerceAtMost(size.minDimension * 0.2f)
                drawPath(
                    rimPath,
                    color = GlassEffectConfig.longPressGlowColor.value.copy(alpha = alpha * 0.85f),
                    style = Stroke(width = strokeWidth)
                )
            }
        }
        .then(highlight.modifier)
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = effects,
            highlight = highlightEffect,
            shadow = shadowEffect,
            layerBlock = layerBlock,
            onDrawSurface = onDrawSurface
        )
    if (onClick != null) {
        m = m.clickable(interactionSource = null, indication = null, onClick = onClick)
    }
    m
}
