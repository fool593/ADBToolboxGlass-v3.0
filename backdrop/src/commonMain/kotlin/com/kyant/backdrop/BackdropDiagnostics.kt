package com.kyant.backdrop

/**
 * 玻璃效果的轻量运行时计数（内存诊断用）。
 *
 * 为什么需要：`GraphicsLayer`、`RenderEffect` 这类对象背后是 HWUI 的原生缓冲，
 * 它们"看起来"已经被 Compose 回收，泄漏却只体现在原生内存与 GPU 内存上，
 * 普通堆快照（heap dump）看不到。四个创建 [androidx.compose.ui.graphics.layer.GraphicsLayer]
 * 的节点（backdrop / highlight / shadow / innerShadow）都在 onAttach 建、onDetach 释放，
 * 但"成对"这件事只能靠读代码确认。这里把 attach / detach 与 layer 的创建 / 释放各记一个计数，
 * 于是"层数是否收支平衡"变成可以直接读到的数字：在页面之间来回进出后，
 * `layers-in-use` 应当回到 0（或回到与当前可见玻璃节点数一致的稳定值），
 * 持续上涨就说明某条路径上的 onDetach 没跑。
 *
 * 线程模型：Compose 节点的 attach / detach 都发生在主线程（UI 线程），
 * 因此这里用普通 Int / Long 计数，不加锁、不用原子类 —— 与 Compose 自身的模型一致。
 * 只读快照 [snapshot] 可以在任意线程调用（最坏情况读到本帧正在写的一个数字）。
 */
object BackdropDiagnostics {

    /** 当前已 attach 的 backdrop（毛玻璃本体）节点数。 */
    var attachedBackdropNodes: Int = 0
        private set

    /** 当前已 attach 的高光节点数。 */
    var attachedHighlightNodes: Int = 0
        private set

    /** 累计创建的 [androidx.compose.ui.graphics.layer.GraphicsLayer] 个数。 */
    var createdGraphicsLayers: Long = 0
        private set

    /** 累计释放的 [androidx.compose.ui.graphics.layer.GraphicsLayer] 个数。 */
    var releasedGraphicsLayers: Long = 0
        private set

    /** 净占用的 layer 数：正常情况下应当与"当前可见的玻璃节点数"同量级，且回落到 0。 */
    val graphicsLayersInUse: Long
        get() = createdGraphicsLayers - releasedGraphicsLayers

    internal fun onBackdropNodeAttached() {
        attachedBackdropNodes++
    }

    internal fun onBackdropNodeDetached() {
        attachedBackdropNodes--
    }

    internal fun onHighlightNodeAttached() {
        attachedHighlightNodes++
    }

    internal fun onHighlightNodeDetached() {
        attachedHighlightNodes--
    }

    internal fun onGraphicsLayerCreated() {
        createdGraphicsLayers++
    }

    internal fun onGraphicsLayerReleased() {
        releasedGraphicsLayers++
    }

    /**
     * 一行诊断文本（不做本地化：这是贴在诊断输出里的技术数据，不是界面文案）。
     * 例：`glass=12 highlight=12 layersInUse=24 (created=118/released=94)`
     */
    fun snapshot(): String =
        "glass=$attachedBackdropNodes highlight=$attachedHighlightNodes " +
            "layersInUse=$graphicsLayersInUse " +
            "(created=$createdGraphicsLayers/released=$releasedGraphicsLayers)"
}
