package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.theme.AppLayout
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 国庆主题横幅。
 *
 * 视觉上只做一件事：把五星红旗的**真实几何比例**画准，其余留白。
 * 旗帜采用 30×20 标准坐标（大星中心 (5,5) 半径 3；四颗小星中心 (10,2)/(12,4)/(12,7)/(10,9) 半径 1，
 * 每颗小星均有一个角尖指向大星中心），因此这里按 size.width/30、size.height/20 换算，
 * 不会因为控件尺寸变化而画歪。
 *
 * 只有当前主题是国庆主题时才显示——主题没选中就不该出现任何节日装饰。
 */
@Composable
fun NationalDayBanner(backdrop: Backdrop, contentColor: Color) {
    if (!AppTheme.isNationalDay) return

    val gold = AppTheme.accentAlt
    val red = AppTheme.accent

    GlassCard(backdrop = backdrop, pageType = "home") {
        Row(
            // 卡片内边距统一走 AppLayout：横幅与其它卡片保持同一内边距，不再是 18/16 的个例
            Modifier.fillMaxWidth().padding(horizontal = AppLayout.cardPad, vertical = AppLayout.cardPad),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FlagStars(
                modifier = Modifier.size(60f.dp, 40f.dp).clip(RoundedCornerShape(4f.dp)),
                red = red,
                gold = gold
            )
            Spacer(Modifier.width(14f.dp))
            Box(Modifier.width(1f.dp).height(32f.dp).background(gold.copy(alpha = 0.55f)))
            Spacer(Modifier.width(14f.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4f.dp)) {
                BasicText(
                    AppStrings.get("national_day_banner_title"),
                    style = TextStyle(gold, 17f.sp, FontWeight.Bold)
                )
                BasicText(
                    AppStrings.get("national_day_banner_sub"),
                    style = TextStyle(contentColor.copy(alpha = 0.65f), 12f.sp)
                )
            }
        }
    }
}

/** 标准五星红旗图案（30×20 坐标）。 */
@Composable
fun FlagStars(modifier: Modifier = Modifier, red: Color = Color(0xFFDE2910), gold: Color = Color(0xFFFFDE00)) {
    Canvas(modifier) {
        drawRect(red)
        drawFlagStars(gold)
    }
}

/**
 * 在 [DrawScope] 上按国旗标准比例绘制五颗星。
 * 供横幅与设置页主题预览共用，保证两处图案完全一致。
 */
fun DrawScope.drawFlagStars(gold: Color, inset: Float = 0f) {
    val w = size.width - inset * 2f
    val h = size.height - inset * 2f
    if (w <= 0f || h <= 0f) return
    val ux = w / 30f
    val uy = h / 20f
    val ox = inset
    val oy = inset

    fun u(x: Float, y: Float) = Offset(ox + x * ux, oy + y * uy)

    // 大星：中心 (5,5)，外接圆半径 3
    val bigCenter = u(5f, 5f)
    val bigR = 3f * ux
    drawPath(starPath(bigCenter.x, bigCenter.y, bigR, 0f), gold)

    // 四颗小星：中心 (10,2) (12,4) (12,7) (10,9)，半径 1，角尖指向大星中心
    val smallCenters = listOf(10f to 2f, 12f to 4f, 12f to 7f, 10f to 9f)
    val smallR = 1f * ux
    smallCenters.forEach { (sx, sy) ->
        val c = u(sx, sy)
        val dx = bigCenter.x - c.x
        val dy = bigCenter.y - c.y
        // 星形默认有一个角尖朝正上方（-90°），要让角尖指向大星中心需补偿 +90°
        val rotation = (atan2(dy.toDouble(), dx.toDouble()) * 180.0 / PI).toFloat() + 90f
        drawPath(starPath(c.x, c.y, smallR, rotation), gold)
    }
}

/** 五角星路径：外接圆 [outer]，内接圆 0.382×外接圆（正五角星的精确比值）。 */
private fun starPath(cx: Float, cy: Float, outer: Float, rotationDeg: Float): Path {
    val inner = outer * 0.38197f
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val angle = (-90.0 + rotationDeg + i * 36.0) * PI / 180.0
        val x = cx + r * cos(angle).toFloat()
        val y = cy + r * sin(angle).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/**
 * 主题预览色条：三段渐变 + 描边，用在设置页的主题按钮里。
 * 纯绘制，不依赖任何图片资源。
 */
@Composable
fun ThemePreviewSwatch(colors: List<Color>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val n = colors.size.coerceAtLeast(1)
        val bandW = size.width / n
        colors.forEachIndexed { i, c ->
            drawRect(c, topLeft = Offset(i * bandW, 0f), size = Size(bandW, size.height))
        }
        drawRoundRect(
            color = Color.White.copy(alpha = 0.25f),
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
            style = Stroke(width = 1f.dp.toPx())
        )
    }
}
