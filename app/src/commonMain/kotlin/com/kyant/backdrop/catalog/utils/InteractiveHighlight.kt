package com.kyant.backdrop.catalog.utils

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
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
import com.example.adbtoolbox.common.theme.AppMotion
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

    // 动效统一走 AppMotion：按下/回弹/光斑跟随用弹簧（手感一致），长按淡入淡出用 tween
    private val pressProgressAnimationSpec = AppMotion.pressSpring
    private val releaseProgressAnimationSpec = AppMotion.releaseSpring
    private val positionAnimationSpec = AppMotion.followSpring

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

    /**
     * 高光着色器与画刷：**懒创建 + 实例内复用**。
     *
     * `android.graphics.RuntimeShader` 的构造函数里就会编译 AGSL 程序（原生 SkRuntimeEffect），
     * 而列表里绝大多数条目从来不会被按下。原来是 `private val shader = RuntimeShader(...)`：
     * 每个实例一构造就编译一次 —— 一屏十几个条目、滚动时不断建/丢，
     * 这是"每次进入列表/每次重组都新建 RuntimeShader"最集中的地方。
     * 现在改成第一次真的要画光斑时才建，之后实例内一直复用；
     * 没被按过的条目一个字节都不花。
     *
     * 没有改成进程级共享同一个实例：uniform（size / position / radius / color）是逐次绘制
     * 写在 shader 对象上的可变状态，同一帧里多个控件顺序绘制时共用一份 uniform，
     * 谁最后写谁说了算。HWUI 把录制与光栅化分开（且可能换线程），无法保证每个控件的
     * uniform 在各自绘制时被快照，会出现"光斑串到别的控件上"，
     * 风险大于"少编译几次"的收益，因此保持每实例一份。
     */
    private var shader: RuntimeShader? = null
    private var shaderBrush: ShaderBrush? = null

    /** 取得（必要时创建）高光画刷；API < 33 没有 AGSL 时返回 null，调用方走纯色回退。 */
    private fun obtainShaderBrush(): ShaderBrush? {
        shaderBrush?.let { return it }
        if (!isRuntimeShaderSupported()) return null
        val runtimeShader =
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
        shader = runtimeShader
        return ShaderBrush(runtimeShader.asComposeShader()).also { shaderBrush = it }
    }

    /**
     * 圆角裁剪用的路径：复用同一个 [Path]。
     *
     * 原实现每次绘制都 `Path()` + `addRoundRect`，即每帧每个按下中的控件
     * 分配一个 Compose Path（内含原生 Skia 路径）。这里改成复用 + rewind()，
     * 分配次数从"每帧一次"降到"每个实例一次"。
     */
    private val clipPathCache = Path()

    private fun DrawScope.clipShape(shape: Shape, block: DrawScope.() -> Unit) {
        when (val outline = shape.createOutline(size, LayoutDirection.Ltr, this)) {
            is Outline.Rectangle -> clipRect(block = block)
            is Outline.Rounded -> {
                clipPathCache.rewind()
                clipPathCache.addRoundRect(outline.roundRect)
                clipPath(path = clipPathCache, block = block)
            }
            is Outline.Generic -> clipPath(path = outline.path, block = block)
        }
    }

    /** 按下：高光淡入 + 启动长按计时（超过 longPressDelayMs 后长按发光淡入） */
    fun press() {
        animationScope.launch {
            pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec)
        }
        longPressJob?.cancel()
        longPressJob = animationScope.launch {
            delay(AppMotion.longPressDelayMs)
            longPressProgressAnimation.animateTo(1f, AppMotion.longPressIn)
        }
    }

    /** 抬起 / 取消：高光与长按发光淡出 */
    fun release() {
        longPressJob?.cancel()
        longPressJob = null
        animationScope.launch {
            pressProgressAnimation.animateTo(0f, releaseProgressAnimationSpec)
        }
        animationScope.launch {
            longPressProgressAnimation.animateTo(0f, AppMotion.longPressOut)
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
                    val brush = obtainShaderBrush()
                    val runtimeShader = shader
                    if (brush != null && runtimeShader != null) {
                        // 整片极淡的底色高光 + 跟随手指的径向光斑（Plus 叠加，只加亮不盖内容）
                        drawRect(
                            Color.White.copy(0.06f * alpha),
                            blendMode = BlendMode.Plus
                        )
                        runtimeShader.apply {
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
                            brush,
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
