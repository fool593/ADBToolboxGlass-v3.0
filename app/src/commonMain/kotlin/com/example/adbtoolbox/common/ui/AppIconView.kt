package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.decodeBase64ToImageBitmap
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 应用图标。
 *
 * 真实图标来自 [ADBTools.getAppIconBase64]（Android 侧已降采样为 96px WebP，并按包名缓存），
 * 这里按需在后台线程解码，解码结果再存一份到 [AppCache]，避免列表来回滚动时重复解码。
 * 取不到图标就退回首字母占位——以前的问题正是"数据源永远返回 null"，导致这一块的图标其实是个空壳。
 *
 * 为什么不做成一次性批量加载：一次解码 300+ 个图标会让列表首次进入明显卡顿，
 * 按 item 惰性触发才是列表该有的行为（LazyColumn 只会组合可见项）。
 */
@Composable
fun AppIconView(
    packageName: String,
    appName: String,
    backdrop: Backdrop,
    size: Dp = 44.dp,
    corner: Dp = 12.dp,
    isSystem: Boolean = false,
    tint: Color? = null
) {
    var icon by remember(packageName) { mutableStateOf(AppCache.getAppIcon(packageName)) }

    LaunchedEffect(packageName) {
        if (icon != null) return@LaunchedEffect
        val decoded = try {
            withContext(Dispatchers.Default) {
                val base64 = ADBTools.getAppIconBase64(packageName) ?: return@withContext null
                decodeBase64ToImageBitmap(base64)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (decoded != null) {
            AppCache.putAppIcon(packageName, decoded)
            icon = decoded
        }
    }

    val surface = tint ?: if (isSystem) Color(0xFFFF9500).copy(alpha = 0.3f) else Color(0xFF0088FF).copy(alpha = 0.3f)

    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(corner) },
                effects = { blur(10f.dp.toPx()) },
                onDrawSurface = { drawRect(surface) }
            ),
        contentAlignment = Alignment.Center
    ) {
        val bitmap: ImageBitmap? = icon
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = appName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            BasicText(
                appName.take(1),
                style = TextStyle(Color.White, (size.value * 0.4f).sp, FontWeight.Bold)
            )
        }
    }
}
