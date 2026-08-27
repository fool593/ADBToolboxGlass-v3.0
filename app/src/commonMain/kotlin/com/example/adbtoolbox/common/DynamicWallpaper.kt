package com.example.adbtoolbox.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// 视频动态壁纸背景 - expect 声明
@Composable
expect fun DynamicWallpaperBackground(
    videoPath: String?,
    modifier: Modifier = Modifier
)
