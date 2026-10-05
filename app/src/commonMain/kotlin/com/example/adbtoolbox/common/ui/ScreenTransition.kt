package com.example.adbtoolbox.common.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.adbtoolbox.common.theme.AppMotion

/**
 * 页面切换容器：把整屏页面的切换从"硬切"统一成一套带空间感的过渡。
 *
 * 这套过渡是按 ColorOS 系**公开可观察的动效语言**归纳出来的（弹簧为主、缩放+淡入、
 * 位移幅度小、退出比进入更快），**不是**对某个闭源系统的逐帧复制，也不宣称与它一致：
 * 时长、阻尼、刚度全部是本工程自己定的值，见 AppMotion 的页面级 token。
 *
 * 具体行为：
 * - 进入：scale [AppMotion.screenEnterScaleFrom] -> 1.0，alpha 0 -> 1，走 [AppMotion.screenEnterSpec]；
 * - 退出：alpha 1 -> 0，同时轻微放大到 [AppMotion.screenExitScaleTo]，走 [AppMotion.screenExitSpec]；
 * - 不做大距离滑动：位移感全部由缩放承担，页面不会像抽屉一样横着推出去；
 * - [SizeTransform] 关闭裁剪：页面高度不同（例如从长列表切到空态页）时不做硬裁切；
 * - 进入内容的 zIndex 高于退出内容：新页面压在上层，旧页面在后景淡出，形成层次感。
 *
 * 本组件刻意**不读取任何全局可变状态**，只依赖参数 [target]：
 * 一旦在这里观察某个全局状态，页面切换会与状态写入互相触发，形成重组死循环。
 *
 * 用法（由 MainContent 调用，target 传 ADBDestination）：
 * ```
 * ScreenTransitionHost(target = currentDestination) { dest ->
 *     when (dest as ADBDestination) { ... }
 * }
 * ```
 *
 * 实现注记：这里用 [ContentTransform] 构造器而不是 `enter togetherWith exit using SizeTransform(...)`，
 * 因为本轮依赖的 compose-animation（1.11/1.12 一带）已经不再提供 `ContentTransform.using` 扩展
 * （已核对解析到的构件：`androidx.compose.animation.EnterExitTransitionKt` / `ContentTransform`
 * 中都不存在 `using` 符号）。
 *
 * @param target 页面标识。值不变则不触发过渡；值变化即切换。
 * @param modifier 作用在容器上的修饰符。
 * @param content 绘制当前页面，参数为传入的 [target]。
 */
@Composable
fun ScreenTransitionHost(
    target: Any,
    modifier: Modifier = Modifier,
    content: @Composable (Any) -> Unit
) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        transitionSpec = {
            val enter = fadeIn(
                animationSpec = AppMotion.screenEnterSpec,
                initialAlpha = AppMotion.screenEnterAlphaFrom
            ) + scaleIn(
                animationSpec = AppMotion.screenEnterSpec,
                initialScale = AppMotion.screenEnterScaleFrom
            )
            val exit = fadeOut(
                animationSpec = AppMotion.screenExitSpec,
                targetAlpha = AppMotion.screenExitAlphaTo
            ) + scaleOut(
                animationSpec = AppMotion.screenExitSpec,
                targetScale = AppMotion.screenExitScaleTo
            )
            ContentTransform(
                targetContentEnter = enter,
                initialContentExit = exit,
                // 进入中的页面画在退出中的页面之上：新页面淡入时不被后景的轻微放大盖住
                targetContentZIndex = 1f,
                // clip = false：高度变化时不裁切内容（长列表切到空态页会明显不同高）
                sizeTransform = SizeTransform(clip = false)
            )
        },
        label = "screenTransition"
    ) { state ->
        content(state)
    }
}
