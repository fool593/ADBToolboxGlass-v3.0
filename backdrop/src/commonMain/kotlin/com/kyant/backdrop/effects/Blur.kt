package com.kyant.backdrop.effects

import androidx.annotation.FloatRange
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.BackdropEffectStep
import com.kyant.backdrop.isRenderEffectSupported
import kotlin.math.roundToInt

fun BackdropEffectScope.blur(
    @FloatRange(from = 0.0) radius: Float,
    edgeTreatment: TileMode = TileMode.Clamp
) {
    if (!isRenderEffectSupported()) return
    if (radius <= 0f) return

    // 半径量化到 2px 网格：按住动画里半径是逐帧变化的，量化后效果链最多重建十几次而不是每帧一次
    // （每帧重建会让离屏图层尺寸与 HWUI 管线一起每帧重建）；2px 之内的模糊差异肉眼不可分辨。
    val blurRadius = if (radius >= 4f) (radius / 2f).roundToInt() * 2f else radius

    if (edgeTreatment != TileMode.Clamp || effectCount > 0) {
        if (blurRadius > padding) {
            padding = blurRadius
        }
    }

    addEffect(BlurStep(blurRadius, blurRadius, edgeTreatment))
}

private data class BlurStep(
    val radiusX: Float,
    val radiusY: Float,
    val edgeTreatment: TileMode
) : BackdropEffectStep {

    override fun build(inner: RenderEffect?): RenderEffect {
        return BlurEffect(inner, radiusX, radiusY, edgeTreatment)
    }
}
