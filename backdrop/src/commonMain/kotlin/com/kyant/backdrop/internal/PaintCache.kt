package com.kyant.backdrop.internal

import androidx.compose.ui.graphics.Paint
import com.kyant.backdrop.RuntimeShader
import kotlin.math.abs
import kotlin.math.max

internal expect fun Paint.blur(radius: Float)

internal expect fun Paint.setRuntimeShader(runtimeShader: RuntimeShader?)

/**
 * 判断模糊半径是否需要真的重新写进 [Paint]。
 *
 * 原因：[Paint.blur] 在 Android 上每次都 new 一个 `BlurMaskFilter`（原生对象，只能等 GC 释放）。
 * 它原来被无条件写在 draw() 的 prepare 逻辑里，等于每帧每个节点一个原生分配；
 * 长按动画期间半径逐帧变化时更是一直分配 —— 这是高端机（API 31+ 才真的走
 * RenderEffect/RuntimeShader 路径）卡顿与原生内存波动的来源之一。
 *
 * 判定规则：
 * - 第一次（previous 为 NaN）必须写；
 * - 完全相等不写；
 * - 从 0 变非 0（或反过来）必须写，保证"有模糊/无模糊"这类可见开关立即生效；
 * - 其余情况只在变化超过 max(4px, 10%) 时才写：模糊半径 10% 以内的台阶在屏幕上看不出来，
 *   但重建次数从"每帧一次"降到"整段动画几次"。
 *
 * 文件为什么叫 PaintCache 而不是 Paint：`androidMain` 已有一个 `internal/Paint.kt`
 * （里面是 `Paint.blur` / `setRuntimeShader` 的 actual 实现）。若 commonMain 再放一个同名
 * `Paint.kt`，两者会生成同一个 JVM 类名 `com/kyant/backdrop/internal/PaintKt`，编译直接报
 * "Duplicate JVM class name"。改文件名即可，函数本身仍在同一包内。
 */
internal fun blurNeedsUpdate(previous: Float, next: Float): Boolean {
    if (previous.isNaN()) return true
    if (previous == next) return false
    if (previous <= 0f || next <= 0f) return true
    return abs(next - previous) > max(4f, previous * 0.1f)
}
