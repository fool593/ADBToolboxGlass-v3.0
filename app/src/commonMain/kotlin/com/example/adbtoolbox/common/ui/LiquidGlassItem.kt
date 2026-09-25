package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import kotlin.math.abs
import kotlin.math.tanh

/**
 * 液态玻璃可点击项。
 * 保留玻璃原始颜色（tint）+ 模糊/折射/活力效果，
 * 长按高光绘制在玻璃之上并裁剪为圆角形状，拖动时缩放/平移/边缘拉伸。
 * shape/effects/layerBlock 均 remember 缓存，避免重组时重建 RenderEffect 导致卡顿。
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
    val highlight = remember(scope, shape) {
        InteractiveHighlight(
            animationScope = scope,
            shape = shape,
            drawAboveContent = true
        )
    }
    val effects: BackdropEffectScope.() -> Unit = remember(blurPx, lensIn, lensOut) {
        {
            vibrancy()
            blur(blurPx)
            lens(lensIn, lensOut)
        }
    }
    val layerBlock: GraphicsLayerScope.() -> Unit = remember(highlight) {
        {
            val progress = highlight.pressProgress
            scaleX = 1f + 0.03f * progress
            scaleY = 1f + 0.03f * progress
            val maxOffset = size.minDimension
            val off = highlight.offset
            translationX = maxOffset * tanh(0.05f * off.x / maxOffset)
            translationY = maxOffset * tanh(0.05f * off.y / maxOffset)
            val maxDragScale = 3f.dp.toPx() / size.height
            scaleX = scaleX + maxDragScale * abs(off.x / size.maxDimension) * (size.width / size.height)
            scaleY = scaleY + maxDragScale * abs(off.y / size.maxDimension) * (size.height / size.width)
        }
    }
    val onDrawSurface: (DrawScope.() -> Unit)? = remember(tint) {
        if (tint != Color.Unspecified) {
            {
                drawRect(tint, blendMode = BlendMode.Hue)
                drawRect(tint.copy(alpha = 0.55f))
            }
        } else {
            null
        }
    }
    var m: Modifier = this
        .then(highlight.gestureModifier)
        .then(highlight.modifier)
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = effects,
            layerBlock = layerBlock,
            onDrawSurface = onDrawSurface
        )
    if (onClick != null) {
        m = m.clickable(interactionSource = null, indication = null, onClick = onClick)
    }
    m
}
