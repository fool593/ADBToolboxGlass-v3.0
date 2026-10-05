package com.kyant.backdrop

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/**
 * 效果链里的一步。
 *
 * 存在的理由：效果 lambda（`vibrancy()` / `blur()` / `lens()` …）里有一部分参数是**逐帧变化**的
 * （例如长按折射强度），但这些变化绝大多数只是 **shader 的 uniform**，并不改变效果链的“结构”。
 * 把每一步记成一个可比较的对象后，结构不变时就能直接复用上一帧建好的 [RenderEffect]，
 * 不必每帧重新走 `BlurEffect` / `ColorFilterEffect` / `RuntimeShaderEffect` / `createChainEffect`。
 *
 * [build] 接收链上已经建好的内层效果（`chain` 语义：本步在外层）。
 */
/**
 * 效果链上的一步。
 *
 * 可见性说明：`BackdropEffectScope.addEffect` 是公开接口成员，而 Kotlin 的接口成员不能标 `internal`，
 * 所以这个标记接口必须是 public；具体实现类仍然都是 internal，外部拿不到实现，实际不会泄漏内部细节。
 */
interface BackdropEffectStep {

    fun build(inner: RenderEffect?): RenderEffect
}

sealed interface BackdropEffectScope : Density, RuntimeShaderCache {

    val size: Size

    val layoutDirection: LayoutDirection

    val shape: Shape

    var padding: Float

    var renderEffect: RenderEffect?

    /**
     * 本帧已经追加的效果步数。等于 0 表示当前这一步是效果链的第一层
     * （`blur()` 用它判断“后面还有没有别的效果”，从而决定是否需要预留模糊溢出的内边距）。
     *
     * 注意：接口成员不能带 `internal`（Kotlin 不允许），本接口本身是 public 的，
     * 实现类 [BackdropEffectScopeImpl] 是 internal，实际可见性由实现类决定。
     */
    val effectCount: Int

    /**
     * 追加一步效果（见 [BackdropEffectStep]）。实现必须记录该步以便判断效果链结构是否变化。
     */
    fun addEffect(step: BackdropEffectStep)
}

internal abstract class BackdropEffectScopeImpl : BackdropEffectScope, RuntimeShaderCache {

    override var density: Float = 1f
    override var fontScale: Float = 1f
    override var size: Size = Size.Unspecified
    override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
    override var padding: Float = 0f
    override var renderEffect: RenderEffect? = null

    private val runtimeShaderCache = RuntimeShaderCacheImpl()

    // 本帧效果链的结构；与上一帧比较决定能否复用 RenderEffect 对象
    private val effectSteps = ArrayList<BackdropEffectStep>()

    // 上一帧真正建出来的效果链（结构相同则整条复用）
    private var previousSteps: List<BackdropEffectStep>? = null
    private var previousRenderEffect: RenderEffect? = null

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaderCache.obtainRuntimeShader(key, string)
    }

    override val effectCount: Int
        get() = effectSteps.size

    override fun addEffect(step: BackdropEffectStep) {
        effectSteps += step
    }

    fun update(scope: DrawScope): Boolean {
        val newDensity = scope.density
        val newFontScale = scope.fontScale
        val newSize = scope.size
        val newLayoutDirection = scope.layoutDirection

        val changed = newDensity != density ||
                newFontScale != fontScale ||
                newSize != size ||
                newLayoutDirection != layoutDirection

        if (changed) {
            density = newDensity
            fontScale = newFontScale
            size = newSize
            layoutDirection = newLayoutDirection
        }

        return changed
    }

    fun apply(effects: BackdropEffectScope.() -> Unit) {
        padding = 0f
        renderEffect = null
        effectSteps.clear()

        effects()

        // 调用方直接给 renderEffect 赋值（旧的公开用法）：不做结构复用，原样使用并让缓存失效
        val directly = renderEffect
        if (directly != null) {
            previousSteps = null
            previousRenderEffect = null
            return
        }

        val previous = previousSteps
        var sameStructure = previous != null && previous.size == effectSteps.size
        if (sameStructure) {
            for (index in effectSteps.indices) {
                if (previous!![index] != effectSteps[index]) {
                    sameStructure = false
                    break
                }
            }
        }

        if (sameStructure) {
            // 结构没变：uniform 已经在本帧重新写进同一个 RuntimeShader，画面依旧逐帧变化，
            // 但不会再新建任何 RenderEffect / 原生滤镜对象。
            renderEffect = previousRenderEffect
        } else {
            var chained: RenderEffect? = null
            for (step in effectSteps) {
                chained = step.build(chained)
            }
            renderEffect = chained
            previousSteps = ArrayList(effectSteps)
            previousRenderEffect = chained
        }
    }

    fun reset() {
        density = 1f
        fontScale = 1f
        size = Size.Unspecified
        layoutDirection = LayoutDirection.Ltr
        padding = 0f
        renderEffect = null
        runtimeShaderCache.clear()
        // shader 缓存被清空后，旧效果链里引用的 shader 已经不再是缓存里那一个：
        // 若继续复用，新 uniform 会写在新 shader 上、画面却仍旧 shader，折射会“冻住”。
        effectSteps.clear()
        previousSteps = null
        previousRenderEffect = null
    }
}
