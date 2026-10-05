package com.kyant.backdrop.effects

import androidx.compose.ui.graphics.RenderEffect
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.BackdropEffectStep
import com.kyant.backdrop.RuntimeShader
import com.kyant.backdrop.internal.RuntimeShaderEffect
import com.kyant.backdrop.internal.chain
import com.kyant.backdrop.isRenderEffectSupported
import com.kyant.backdrop.isRuntimeShaderSupported
import org.intellij.lang.annotations.Language
import kotlin.contracts.ExperimentalContracts

fun BackdropEffectScope.effect(effect: RenderEffect) {
    if (!isRenderEffectSupported()) return

    addEffect(PassThroughStep(effect))
}

@OptIn(ExperimentalContracts::class)
fun BackdropEffectScope.runtimeShaderEffect(
    key: String,
    @Language("AGSL") shaderString: String,
    uniformShaderName: String,
    block: RuntimeShader.() -> Unit
) {
    if (!isRuntimeShaderSupported()) return

    // block 每帧都执行（写 uniform），但它不改变效果链结构：结构没变时复用上一帧的 RenderEffect。
    val shader = obtainRuntimeShader(key, shaderString).apply(block)
    addEffect(RuntimeShaderStep(shader, uniformShaderName))
}

/**
 * 把 RuntimeShader 作为渲染效果挂到链上。
 *
 * 只保存 shader 与输入名（结构），uniform 由调用方在每帧写进同一个 shader，
 * 因此长按折射这类逐帧变化的参数不会造成效果链重建。
 */
internal class RuntimeShaderStep(
    private val shader: RuntimeShader,
    private val uniformShaderName: String
) : BackdropEffectStep {

    override fun build(inner: RenderEffect?): RenderEffect {
        return inner.chain(RuntimeShaderEffect(shader, uniformShaderName))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RuntimeShaderStep) return false
        return shader === other.shader && uniformShaderName == other.uniformShaderName
    }

    override fun hashCode(): Int = 31 * shader.hashCode() + uniformShaderName.hashCode()
}

private class PassThroughStep(
    private val effect: RenderEffect
) : BackdropEffectStep {

    override fun build(inner: RenderEffect?): RenderEffect {
        return inner.chain(effect)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PassThroughStep) return false
        return effect === other.effect
    }

    override fun hashCode(): Int = effect.hashCode()
}
