package com.kyant.backdrop.catalog.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.utils.DampedDragAnimation
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.example.adbtoolbox.common.GlassEffectConfig
import com.kyant.shapes.Capsule
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    indicatorHeight: Float = 56f, // 指示器高度，56=默认圆形，40=胶囊状
    accentColor: Color? = null, // 外部传入的胶囊指示器颜色，null时用默认蓝色或navIndicatorColor
    containerColor: Color? = null, // 外部传入的容器颜色，null时用默认
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    val externalAccentColor = accentColor
    val accentColor = accentColor ?: if (isLightTheme) Color(0xFF0088FF)
        else Color(0xFF0091FF)
    val containerColor = containerColor ?: if (isLightTheme) Color(0xFFFAFAFA).copy(0.4f)
        else Color(0xFF121212).copy(0.4f)

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember(selectedTabIndex) {
            mutableIntStateOf(selectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(selectedTabIndex) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }

        // 长按导航栏胶囊边缘发光检测：按住 350ms 触发
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

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(32f.dp * GlassEffectConfig.navCornerRadius.value) },
                    effects = {
                        val gI = GlassEffectConfig.globalIntensity.value
                        val gEff = 0.5f + gI * 0.5f
                        if (GlassEffectConfig.navEnableVibrancy.value) vibrancy()
                        blur((GlassEffectConfig.navBlurRadius.value.dp.toPx() * gEff).coerceAtMost(40f.dp.toPx()))
                        lens(
                            (GlassEffectConfig.navRefractionHeight.value * 48f.dp.toPx() * gEff).coerceAtMost(72f.dp.toPx()),
                            (GlassEffectConfig.navRefractionAmount.value * 48f.dp.toPx() * gEff).coerceAtMost(90f.dp.toPx()),
                            chromaticAberration = GlassEffectConfig.navChromaticAberration.value > 0f
                        )
                    },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = {
                        drawRect(containerColor.copy(alpha = GlassEffectConfig.navOpacity.value))
                    }
                )
                .then(interactiveHighlight.modifier)
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(28f.dp * GlassEffectConfig.navCornerRadius.value) },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            if (GlassEffectConfig.navEnableVibrancy.value) vibrancy()
                            blur(GlassEffectConfig.navBlurRadius.value.dp.toPx() * 1.5f)
                            lens(
                                GlassEffectConfig.navRefractionHeight.value * 72f.dp.toPx() * (1f + progress),
                                GlassEffectConfig.navRefractionAmount.value * 72f.dp.toPx() * (1f + progress),
                                chromaticAberration = GlassEffectConfig.navChromaticAberration.value > 0f
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = progress)
                        },
                        onDrawSurface = {
                            drawRect(containerColor.copy(alpha = GlassEffectConfig.navOpacity.value))
                        }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4f.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = {
                        // 所有形状都用 RoundedRectangle，通过圆角值区分：round=height/2(胶囊), square=0(正方形), 中间值自由调节
                        val cornerFactor = when (GlassEffectConfig.navIndicatorShape.value) {
                            "round" -> 1f
                            "square" -> 0f
                            else -> GlassEffectConfig.navIndicatorCorner.value
                        }
                        RoundedRectangle(
                            (GlassEffectConfig.navIndicatorHeight.value / 2f).dp * cornerFactor
                        )
                    },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        val lp = longPressAnim.value
                        val edgeRef = GlassEffectConfig.longPressRefraction.value
                        blur(GlassEffectConfig.navIndicatorBlur.value.dp.toPx() * 2f)
                        lens(
                            10f.dp.toPx() * progress + 12f.dp.toPx() * lp * edgeRef,
                            14f.dp.toPx() * progress + 18f.dp.toPx() * lp * edgeRef,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        val lp = longPressAnim.value
                        Highlight.Default.copy(alpha = (progress + lp * 0.9f).coerceIn(0f, 1f))
                    },
                    shadow = {
                        // 边缘外发光：长按时产生光晕
                        val progress = dampedDragAnimation.pressProgress
                        val lp = longPressAnim.value
                        val glowIntensity = GlassEffectConfig.longPressGlowIntensity.value
                        val glowSize = GlassEffectConfig.longPressGlowSize.value
                        Shadow(
                            alpha = (progress + lp * 0.85f * glowIntensity).coerceIn(0f, 1f),
                            radius = 22f.dp * lp * glowSize
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 8f.dp * progress,
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
                        val base = externalAccentColor ?: GlassEffectConfig.navIndicatorColor.value
                        drawRect(
                            base.copy(alpha = GlassEffectConfig.navIndicatorOpacity.value * (1f - progress))
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(GlassEffectConfig.navIndicatorHeight.value.dp)
                .then(
                    if (GlassEffectConfig.navIndicatorWidth.value > 0f) {
                        Modifier.width(GlassEffectConfig.navIndicatorWidth.value.dp)
                    } else {
                        Modifier.fillMaxWidth(1f / tabsCount)
                    }
                )
        )
    }
}
