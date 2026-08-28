package com.kyant.backdrop.catalog

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import androidx.compose.ui.graphics.painter.ColorPainter

@Composable
actual fun BackdropDemoScaffold(
    modifier: Modifier,
    pickWallpaperTrigger: Int,
    clearWallpaperTrigger: Int,
    dynamicWallpaper: (@Composable () -> Unit)?,
    content: @Composable BoxScope.(backdrop: LayerBackdrop) -> Unit
) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        var painter: Painter? by remember { mutableStateOf(null) }

        val backdrop = rememberLayerBackdrop()

        if (dynamicWallpaper != null) {
            Box(
                Modifier
                    .layerBackdrop(backdrop)
                    .then(modifier)
                    .fillMaxSize()
            ) {
                dynamicWallpaper()
            }
        } else {
            Image(
                painter ?: ColorPainter(androidx.compose.ui.graphics.Color(0xFF1C1C1E)),
                null,
                Modifier
                    .layerBackdrop(backdrop)
                    .then(modifier)
                    .fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        content(backdrop)
    }
}
