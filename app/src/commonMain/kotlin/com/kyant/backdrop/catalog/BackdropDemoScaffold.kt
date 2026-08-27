package com.kyant.backdrop.catalog

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.LayerBackdrop

@Composable
expect fun BackdropDemoScaffold(
    modifier: Modifier = Modifier,
    pickWallpaperTrigger: Int = 0,
    clearWallpaperTrigger: Int = 0,
    content: @Composable BoxScope.(backdrop: LayerBackdrop) -> Unit
)
