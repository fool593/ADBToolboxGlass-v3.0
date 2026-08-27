package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

/**
 * 液态玻璃返回按钮
 */
@Composable
fun GlassBackButton(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40f.dp)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(20f.dp) },
                effects = {
                    vibrancy()
                    blur(8f.dp.toPx())
                    lens(4f.dp.toPx(), 8f.dp.toPx())
                },
                highlight = { Highlight.Plain }
            )
            .clickable { onBack() },
        contentAlignment = Alignment.Center
    ) {
        BasicText(
            text = "‹",
            style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold)
        )
    }
}
