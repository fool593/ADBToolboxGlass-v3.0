package com.kyant.backdrop.catalog.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.utils.DampedDragAnimation
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.example.adbtoolbox.common.GlassEffectConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest

@Composable
fun LiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    visibilityThreshold: Float,
    backdrop: Backdrop,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor =
        if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier.fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDrag) {
                        onValueChange(targetValue)
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    onValueChange(
                        if (isLtr) (targetValue + delta).coerceIn(valueRange)
                        else (targetValue - delta).coerceIn(valueRange)
                    )
                }
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value() }
                .collectLatest { value ->
                    if (dampedDragAnimation.targetValue != value) {
                        dampedDragAnimation.updateValue(value)
                    }
                }
        }

        // 长按边缘发光检测：按住 350ms 触发
        var longPressActive by remember { mutableStateOf(false) }
        val longPressAnim = remember { Animatable(0f) }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { dampedDragAnimation.pressProgress }
                .collectLatest { progress ->
                    if (progress > 0.5f) {
                        delay(350)
                        longPressActive = true
                    } else {
                        longPressActive = false
                    }
                }
        }
        LaunchedEffect(longPressActive) {
            if (longPressActive) longPressAnim.animateTo(1f, tween(220))
            else longPressAnim.animateTo(0f, tween(320))
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .pointerInput(animationScope) {
                        detectTapGestures { position ->
                            val delta = (valueRange.endInclusive - valueRange.start) * (position.x / trackWidth)
                            val targetValue =
                                (if (isLtr) valueRange.start + delta
                                else valueRange.endInclusive - delta)
                                    .coerceIn(valueRange)
                            dampedDragAnimation.animateToValue(targetValue)
                            onValueChange(targetValue)
                        }
                    }
                    .height(6f.dp)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(Capsule())
                    .background(accentColor)
                    .height(6f.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) * if (isLtr) 1f else -1f
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        val lp = longPressAnim.value
                        val edgeRef = GlassEffectConfig.longPressRefraction.value
                        // 长按时大幅增强折射，边缘文字/背景会产生明显扭曲；全局渲染强度实时放大（映射0.5~1.5且clamp）
                        val gI = GlassEffectConfig.globalIntensity.value
                        val gEff = 0.5f + gI * 0.5f
                        blur((8f.dp.toPx() * (1f - progress) * gEff).coerceAtMost(36f.dp.toPx()))
                        lens(
                            ((10f.dp.toPx() * progress + 22f.dp.toPx() * lp * edgeRef) * gEff).coerceAtMost(80f.dp.toPx()),
                            ((14f.dp.toPx() * progress + 34f.dp.toPx() * lp * edgeRef) * gEff).coerceAtMost(100f.dp.toPx()),
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        val lp = longPressAnim.value
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = (progress + lp * 0.9f).coerceIn(0f, 1f)
                        )
                    },
                    shadow = {
                        // 边缘外发光：长按时产生光晕
                        val lp = longPressAnim.value
                        val glowIntensity = GlassEffectConfig.longPressGlowIntensity.value
                        val glowSize = GlassEffectConfig.longPressGlowSize.value
                        Shadow(
                            radius = 4f.dp + 22f.dp * lp * glowSize,
                            color = GlassEffectConfig.longPressGlowColor.value.copy(
                                alpha = (0.05f + 0.65f * lp * glowIntensity).coerceIn(0f, 1f)
                            )
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4f.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .drawWithContent {
                    // 长按时绘制胶囊边缘高光描边
                    drawContent()
                    val lp = longPressAnim.value
                    if (lp > 0.01f) {
                        val glowIntensity = GlassEffectConfig.longPressGlowIntensity.value
                        val glowColor = GlassEffectConfig.longPressGlowColor.value
                        val alpha = (lp * glowIntensity).coerceIn(0f, 1f)
                        val strokeW = (2.5f.dp.toPx() + 2.5f.dp.toPx() * lp * glowIntensity)
                        val radius = size.height / 2f
                        // 外圈高光
                        drawRoundRect(
                            color = glowColor.copy(alpha = alpha * 0.9f),
                            topLeft = Offset(strokeW / 2f, strokeW / 2f),
                            size = Size(size.width - strokeW, size.height - strokeW),
                            cornerRadius = CornerRadius(radius, radius),
                            style = Stroke(width = strokeW)
                        )
                        // 内圈白色高光
                        val innerW = strokeW * 0.5f
                        drawRoundRect(
                            color = Color.White.copy(alpha = alpha * 0.7f),
                            topLeft = Offset(strokeW, strokeW),
                            size = Size(size.width - strokeW * 2f, size.height - strokeW * 2f),
                            cornerRadius = CornerRadius(radius - strokeW, radius - strokeW),
                            style = Stroke(width = innerW)
                        )
                    }
                }
                .size(40f.dp, 24f.dp)
        )
    }
}
