package com.example.adbtoolbox.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.adbtoolbox.common.PluginData
import com.kyant.backdrop.Backdrop

@Composable
expect fun PluginDetailScreen(
    plugin: PluginData,
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
)
