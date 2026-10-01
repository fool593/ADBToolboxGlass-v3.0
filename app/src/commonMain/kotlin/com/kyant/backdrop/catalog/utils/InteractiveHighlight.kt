package com.kyant.backdrop.catalog.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastCoerceIn
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.asComposeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 按压 / 长按高光。
 *
 * - [pressProgress]：按下瞬间开始的高光进度（spring），驱动光斑淡入与卡片轻微缩放。
 * - [longPressProgress]：按住 350ms 后开始的长按进度（tween），驱动边缘发光 / 折射增强。
 * - [offset]：按下后的拖动位移，用于“果冻”跟随。
 *
 * [glowColor] / [glowIntensity] 是**函数**而非取值：它们在绘制阶段求值，
 * 因此直接读全局配置时，设置页改动会实时生效，不需要重建实例。
 */
class InteractiveHighlight(
    val animationScope: CoroutineScope,
    val position: (size: Size, offset: Offset) -> Offset = { _, offset -> offset },
    val shape: Shape? = null,
    val drawAboveContent: Boolean = false,
    /** 高光颜色，绘制时求值（默认白色） */
    val glowColor: () -> Color = { Color.White },
    /** 高光强度倍率 0~1+，绘制时求值（默认 1） */
    val glowIntensity: () -> Float = { 1f },
    /** 边缘菲涅尔加权：越大越靠近玻璃边缘越亮 */
    val edgeBoost: Float = 0.75f
) {

    private val pressProgressAnimationSpec =
        spring(0.5f, 300f, 0.001f)
    private val positionAnimationSpec =
        spring(0.5f, 300f, Offset.VisibilityThreshold)

    private val pressProgressAnimation =
        Animatable(0f, 0.001f)
    private val longPressProgressAnimation =
        Animatable(0f, 0.001f)
    private val positionAnimation =
        Animatable(Offset.Zero, Offset.VectorConverter, Offset.VisibilityThreshold)

    private var startPosition = Offset.Zero
    private var longPressJob: Job? = null

    /** 按压进度 0~1 */
    val pressProgress: Float get() = pressProgressAnimation.value

    /** 长按进度 0~1（按住 350ms 后淡入） */
    val longPressProgress: Float get() = longPressProgressAnimation.value

    val offset: Offset get() = positionAnimation.value - startPosition

    private val shader =
        if (isRuntimeShaderSupported()) {
            RuntimeShader(
                """
uniform float2 size;
layout(color) uniform half4 color;
uniform float radius;
uniform float2 position;
uniform float edgeBoost;

half4 main(float2 coord) {
    // 中心亮、边缘衰减。smoothstep 要求 edge0 < edge1：
    // 之前写成 smoothstep(radius, radius * 0.5, dist) 是反的，高光会整片反相/不连续。
    float dist = distance(coord, position);
    float falloff = 1.0 - smoothstep(radius * 0.5, radius, dist);
    // 菲涅尔式边缘加权：越靠近玻璃边缘，高光越亮（模拟玻璃边缘聚光）
    float2 toEdge = min(coord, size - coord);
    float edge = min(toEdge.x, toEdge.y);
    float fresnel = 1.0 - clamp(edge / max(radius, 1.0), 0.0, 1.0);
    fresnel = fresnel * fresnel;
    float intensity = clamp(falloff * (1.0 + edgeBoost * fresnel), 0.0, 1.5);
    return half4(color.rgb * half(intensity), color.a * half(intensity));
}"""
            )
        } else {
            null
        }

    private fun DrawScope.clipShape(shape: Shape, block: DrawScope.() -> Unit) {
        when (val outline = shape.createOutline(size, LayoutDirection.Ltr, this)) {
            is Outline.Rectangle -> clipRect(block = block)
            is Outline.Rounded -> {
                val path = Path().apply { addRoundRect(outline.roundRect) }
                clipPath(path = path, block = block)
            }
            is Outline.Generic -> clipPath(path = outline.path, block = block)
        }
    }

    /** 按下：高光淡入 + 启动长按计时（350ms 后长按发光淡入） */
    fun press() {
        animationScope.launch {
            pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec)
        }
        longPressJob?.cancel()
        longPressJob = animationScope.launch {
            delay(350)
            longPressProgressAnimation.animateTo(1f, tween(220))
        }
    }

    /** 抬起 / 取消：高光与长按发光淡出 */
    fun release() {
        longPressJob?.cancel()
        longPressJob = null
        animationScope.launch {
            pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec)
        }
        animationScope.launch {
            longPressProgressAnimation.animateTo(0f, tween(320))
        }
    }

    val modifier: Modifier =
        Modifier.drawWithContent {
            val drawHighlight: DrawScope.() -> Unit = {
                val progress = pressProgressAnimation.value
                val glow = glowIntensity()
                if (progress > 0.001f && glow > 0.001f) {
                    val color = glowColor()
                    val alpha = (progress * glow).fastCoerceIn(0f, 1f)
                    if (shader != null) {
                        // 整片极淡的底色高光 + 跟随手指的径向光斑（Plus 叠加，只加亮不盖内容）
                        drawRect(
                            Color.White.copy(0.06f * alpha),
                            blendMode = BlendMode.Plus
                        )
                        shader.apply {
                            val position = position(size, positionAnimation.value)
                            setFloatUniform("size", size.width, size.height)
                            setColorUniform("color", color.copy(alpha = 0.16f * alpha))
                            setFloatUniform("radius", size.minDimension * 1.5f)
                            setFloatUniform("edgeBoost", edgeBoost)
                            setFloatUniform(
                                "position",
                                position.x.fastCoerceIn(0f, size.width),
                                position.y.fastCoerceIn(0f, size.height)
                            )
                        }
                        drawRect(
                            ShaderBrush(shader.asComposeShader()),
                            blendMode = BlendMode.Plus
                        )
                    } else {
                        drawRect(
                            color.copy(alpha = 0.2f * alpha),
                            blendMode = BlendMode.Plus
                        )
                    }
                }
            }

            val clippedHighlight: DrawScope.() -> Unit = if (shape != null) {
                { clipShape(shape) { drawHighlight() } }
            } else {
                drawHighlight
            }

            if (drawAboveContent) {
                drawContent()
                clippedHighlight()
            } else {
                clippedHighlight()
                drawContent()
            }
        }

    val gestureModifier: Modifier =
        Modifier.pointerInput(animationScope) {
            inspectDragGestures(
                onDragStart = { down ->
                    startPosition = down.position
                    animationScope.launch {
                        launch { positionAnimation.snapTo(startPosition) }
                    }
                    press()
                },
                onDragEnd = {
                    animationScope.launch {
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                    release()
                },
                onDragCancel = {
                    animationScope.launch {
                        launch { positionAnimation.animateTo(startPosition, positionAnimationSpec) }
                    }
                    release()
                }
            ) { change, _ ->
                animationScope.launch { positionAnimation.snapTo(change.position) }
            }
        }
}
