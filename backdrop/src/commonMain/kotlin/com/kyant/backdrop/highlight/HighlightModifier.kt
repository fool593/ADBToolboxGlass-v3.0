package com.kyant.backdrop.highlight

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceAtMost
import com.kyant.backdrop.BackdropDiagnostics
import com.kyant.backdrop.RuntimeShaderCacheImpl
import com.kyant.backdrop.internal.ShapeProvider
import com.kyant.backdrop.internal.blur
import com.kyant.backdrop.internal.blurNeedsUpdate
import com.kyant.backdrop.internal.clipOutline
import com.kyant.backdrop.internal.setRuntimeShader
import com.kyant.backdrop.isRuntimeShaderSupported
import kotlin.math.ceil

internal class HighlightElement(
    val shapeProvider: ShapeProvider,
    val highlight: () -> Highlight?
) : ModifierNodeElement<HighlightNode>() {

    override fun create(): HighlightNode {
        return HighlightNode(shapeProvider, highlight)
    }

    override fun update(node: HighlightNode) {
        node.shapeProvider = shapeProvider
        node.highlight = highlight
        node.invalidateDraw()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "highlight"
        properties["shapeProvider"] = shapeProvider
        properties["highlight"] = highlight
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HighlightElement) return false

        if (shapeProvider != other.shapeProvider) return false
        if (highlight != other.highlight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = shapeProvider.hashCode()
        result = 31 * result + highlight.hashCode()
        return result
    }
}

internal class HighlightNode(
    var shapeProvider: ShapeProvider,
    var highlight: () -> Highlight?
) : DrawModifierNode, Modifier.Node() {

    override val shouldAutoInvalidate: Boolean = false

    private var highlightLayer: GraphicsLayer? = null

    private val paint =
        Paint().apply {
            style = PaintingStyle.Stroke
        }
    private var clipPath: Path? = null

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    /**
     * 上一次设置过的模糊半径。
     *
     * [Paint.blur] 在 Android 上每次调用都会 new 一个 `BlurMaskFilter`（原生对象，
     * 只在 GC 时释放）。它原来写在 [configurePaint] 里，而 [configurePaint] 在 draw() 里调用，
     * 等于每帧每个节点分配一个原生对象。半径只在配置/长按尺寸变化时才变，
     * 所以这里记下上次的值，相同就跳过 —— 分配次数从"每帧一次"降到"每次变化一次"。
     */
    private var prevBlurRadius = Float.NaN

    override fun ContentDrawScope.draw() {
        val highlight = highlight()
        if (highlight == null || highlight.width.value <= 0f) {
            return drawContent()
        }
        // alpha = 0 的高光是完全不可见的，但原来仍会走完整条离屏图层流程：
        // 录一张"和控件一样大"的图层（safeSize = 控件尺寸 + 2）再按 blendMode 合上去。
        // LiquidToggle / LiquidSlider / LiquidBottomTabs 在静息态传的就是 alpha = 0，
        // 所以这条路径每帧都在白白录制 + 合成一张整块大小的图层。
        // 直接跳过：既省掉每帧一次的整块图层，也避免了"全透明 + 非 SrcOver 混合模式"
        // 这种退化合成（部分驱动上会把它合成为一块不透明黑，正好是控件大小）。
        if (highlight.alpha <= 0.001f) {
            return drawContent()
        }

        drawContent()

        val highlightLayer = highlightLayer
        if (highlightLayer != null) {
            val size = size
            val density: Density = this
            val layoutDirection = layoutDirection

            val safeSize =
                IntSize(
                    ceil(size.width).toInt() + 2,
                    ceil(size.height).toInt() + 2
                )

            val outline = shapeProvider.shape.createOutline(size, layoutDirection, density)
            val clipPath =
                if (outline is Outline.Rounded) {
                    clipPath ?: Path().also { clipPath = it }
                } else {
                    null
                }

            configurePaint(highlight)

            highlightLayer.alpha = highlight.alpha
            highlightLayer.blendMode = highlight.style.blendMode
            highlightLayer.record(safeSize) {
                translate(1f, 1f) {
                    val canvas = drawContext.canvas
                    canvas.save()
                    canvas.clipOutline(outline, clipPath)
                    canvas.drawOutline(outline, paint)
                    canvas.restore()
                }
            }

            translate(-1f, -1f) {
                drawLayer(highlightLayer)
            }
        }
    }

    override fun onAttach() {
        val graphicsContext = requireGraphicsContext()
        // 显式声明为 Offscreen：这张图层是用 BlendMode.Plus 之类的非 SrcOver 模式合上去的，
        // 走 Auto 时是否真的分配独立缓冲由 Compose/驱动自行决定，一旦这条分支不被支持，
        // 整块图层（正好是控件大小）就可能被合成为一块黑。
        // 这里与 ShadowNode / InnerShadowNode 保持同一写法：先隔离成独立缓冲，再按混合模式合成。
        highlightLayer =
            graphicsContext.createGraphicsLayer().apply {
                compositingStrategy = CompositingStrategy.Offscreen
            }
        // 诊断计数：见 BackdropDiagnostics，用来确认 layer 的创建/释放真的成对
        BackdropDiagnostics.onHighlightNodeAttached()
        BackdropDiagnostics.onGraphicsLayerCreated()
    }

    override fun onDetach() {
        val graphicsContext = requireGraphicsContext()
        highlightLayer?.let { layer ->
            graphicsContext.releaseGraphicsLayer(layer)
            highlightLayer = null
            BackdropDiagnostics.onGraphicsLayerReleased()
        }
        BackdropDiagnostics.onHighlightNodeDetached()
        clipPath = null
        runtimeShaderCache.clear()
        prevBlurRadius = Float.NaN
    }

    private fun DrawScope.configurePaint(highlight: Highlight) {
        paint.color = highlight.style.color
        paint.strokeWidth = ceil(highlight.width.toPx().fastCoerceAtMost(size.minDimension / 2f)) * 2f
        // 只在半径真的变了（且变化可见）才重建 BlurMaskFilter，详见 blurNeedsUpdate 的注释
        val blurRadius = highlight.blurRadius.toPx()
        if (blurNeedsUpdate(prevBlurRadius, blurRadius)) {
            paint.blur(blurRadius)
            prevBlurRadius = blurRadius
        }
        if (isRuntimeShaderSupported()) {
            val shader =
                with(highlight.style) {
                    createShader(
                        shape = shapeProvider.shape,
                        runtimeShaderCache = runtimeShaderCache
                    )
                }
            paint.setRuntimeShader(shader)
        }
    }
}
