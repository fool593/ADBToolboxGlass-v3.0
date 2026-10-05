package com.example.adbtoolbox.common.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.example.adbtoolbox.common.theme.AppMotion
import kotlinx.coroutines.delay

/**
 * 列表项错峰进场：每一项比上一项晚 [AppMotion.staggerStepMs] 毫秒淡入。
 *
 * 这条"逐项浮现"的节奏是 ColorOS 系动效里可观察到的特征之一，
 * 这里按本工程自己的参数实现（错峰步长、上限、进场 spec 都在 AppMotion 里），
 * 属于风格对齐，不是对闭源系统的逐帧复制。
 *
 * 两个刻意的设计：
 * - **延迟有上限**（[AppMotion.staggerMaxMs]）：没有上限时第 50 项要等 2 秒，
 *   列表越长越像卡顿；到达上限后所有项同时进场，观感仍是"一批一批出现"。
 * - **只播一次**：进场状态用 rememberSaveable 记录，滚动导致条目被回收重建时
 *   直接落在终态而不是重播，否则来回滚动会一直闪。
 *
 * 只做 alpha + 2% 缩放 + 8dp 上抬，全部走 graphicsLayer，不参与布局测量，
 * 因此不会改变原有列表的排版（间距、行高、weight 都与不用这个包装时一致）。
 *
 * @param index 该项在列表中的序号，从 0 开始。
 * @param modifier 作用在包装容器上的修饰符（weight / fillMaxWidth 等照常传进来）。
 * @param content 列表项内容。
 */
@Composable
fun StaggeredFadeIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val progress = remember { Animatable(0f) }
    var entered by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(index) {
        if (entered) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        // 用 Long 计算再夹取，避免极端序号（超长列表）下 Int 乘法溢出成负数。
        val delayMs = (index.coerceAtLeast(0).toLong() * AppMotion.staggerStepMs)
            .coerceAtMost(AppMotion.staggerMaxMs.toLong())
        if (delayMs > 0L) delay(delayMs)
        progress.animateTo(1f, AppMotion.listItemEnterSpec)
        entered = true
    }

    Box(
        modifier.graphicsLayer {
            val visible = progress.value
            alpha = visible
            val scale = AppMotion.listItemEnterScaleFrom +
                (1f - AppMotion.listItemEnterScaleFrom) * visible
            scaleX = scale
            scaleY = scale
            translationY = AppMotion.listItemEnterOffsetY.toPx() * (1f - visible)
        }
    ) {
        content()
    }
}
