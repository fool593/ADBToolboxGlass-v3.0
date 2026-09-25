package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppInfoData
import com.example.adbtoolbox.common.AppStrings
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppsScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onAppClick: (String) -> Unit
) {
    var apps by remember { mutableStateOf<List<AppInfoData>>(emptyList()) }
    var showSystemApps by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(showSystemApps) {
        // 优先从预加载缓存读取，秒开
        if (AppCache.appsLoaded.value) {
            val allApps = AppCache.installedApps.value
            apps = if (showSystemApps) allApps else allApps.filter { !it.isSystem }
            isLoading = false
        } else {
            isLoading = true
            // 耗时操作移到后台线程，避免主线程阻塞
            val allApps = withContext(Dispatchers.Default) { ADBTools.getInstalledApps() }
            AppCache.installedApps.value = allApps
            AppCache.appsLoaded.value = true
            apps = if (showSystemApps) allApps else allApps.filter { !it.isSystem }
            isLoading = false
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16f.dp)) {
        Spacer(Modifier.height(24f.dp))
        BasicText(AppStrings.get("app_manager"), style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold))
        Spacer(Modifier.height(12f.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(AppStrings.get("show_system_apps"), style = TextStyle(contentColor, 14f.sp))
            Spacer(Modifier.width(12f.dp))
            LiquidToggle(
                selected = { showSystemApps },
                onSelect = { showSystemApps = it },
                backdrop = backdrop,
                modifier = Modifier.size(51f.dp, 31f.dp)
            )
        }

        Spacer(Modifier.height(12f.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(AppStrings.get("loading"), style = TextStyle(contentColor.copy(alpha = 0.5f), 16f.sp))
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8f.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppListItem(app = app, backdrop = backdrop, contentColor = contentColor, onClick = { onAppClick(app.packageName) })
                }
                item { Spacer(Modifier.height(80f.dp)) }
            }
        }
    }
}

@Composable
fun AppListItem(
    app: AppInfoData,
    backdrop: Backdrop,
    contentColor: Color,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .liquidGlassItem(backdrop = backdrop, corner = 16.dp, onClick = onClick)
            .padding(14f.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44f.dp)
                .clip(RoundedCornerShape(12f.dp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(12f.dp) },
                    effects = { blur(10f.dp.toPx()) },
                    onDrawSurface = { drawRect(if (app.isSystem) Color(0xFFFF9500).copy(0.3f) else Color(0xFF0088FF).copy(0.3f)) }
                ),
            contentAlignment = Alignment.Center
        ) {
            BasicText(
                app.appName.take(1),
                style = TextStyle(Color.White, 18f.sp, androidx.compose.ui.text.font.FontWeight.Bold)
            )
        }
        Spacer(Modifier.width(12f.dp))
        Column(Modifier.weight(1f)) {
            BasicText(app.appName, style = TextStyle(contentColor, 15f.sp, androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 1)
            BasicText(
                app.packageName,
                style = TextStyle(contentColor.copy(alpha = 0.5f), 11f.sp),
                maxLines = 1
            )
        }
        if (app.isFrozen) {
            BasicText(AppStrings.get("frozen"), style = TextStyle(Color(0xFFFF3B30), 11f.sp))
        }
    }
}
