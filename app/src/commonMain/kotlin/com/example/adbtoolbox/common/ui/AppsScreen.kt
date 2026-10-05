package com.example.adbtoolbox.common.ui

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
import com.example.adbtoolbox.common.theme.AppLayout
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppsScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onAppClick: (String) -> Unit
) {
    var showSystemApps by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var loadFailed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    // 直接观察全局缓存：在 AppDetailScreen 里做完操作后会写回缓存，本页立刻显示最新状态，
    // 不会出现“点了冻结/卸载后回到列表还是旧状态”的问题。
    val cachedApps = AppCache.installedApps.value
    val apps = remember(cachedApps, showSystemApps) {
        if (showSystemApps) cachedApps else cachedApps.filter { !it.isSystem }
    }

    LaunchedEffect(reloadTick, showSystemApps) {
        // 缓存有效（非空）直接用；Preloader 失败时会把空列表也标记为 appsLoaded=true，
        // 这里不信任空缓存，否则界面会永远空白且不再重试。
        if (AppCache.installedApps.value.isNotEmpty()) {
            isLoading = false
            loadFailed = false
            return@LaunchedEffect
        }
        isLoading = true
        loadFailed = false
        // 耗时操作移到后台线程，避免主线程阻塞
        val loaded = try {
            withContext(Dispatchers.Default) { ADBTools.getInstalledApps() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        if (loaded.isNullOrEmpty()) {
            // 关键修复：加载失败/为空时不要把 appsLoaded 置位，否则缓存永远为空且永不重试
            AppCache.appsLoaded.value = false
            loadFailed = true
        } else {
            AppCache.installedApps.value = loaded
            AppCache.appsLoaded.value = true
        }
        isLoading = false
    }

    Column(Modifier.fillMaxSize().padding(horizontal = AppLayout.screenH)) {
        Spacer(Modifier.height(AppLayout.screenTop))
        BasicText(AppStrings.get("app_manager"), style = TextStyle(contentColor, AppLayout.titleSize, androidx.compose.ui.text.font.FontWeight.Bold))
        Spacer(Modifier.height(AppLayout.sectionGap))

        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(AppStrings.get("show_system_apps"), style = TextStyle(contentColor, AppLayout.bodySize))
            Spacer(Modifier.width(AppLayout.innerGap))
            LiquidToggle(
                selected = { showSystemApps },
                onSelect = { showSystemApps = it },
                backdrop = backdrop,
                modifier = Modifier.size(51f.dp, 31f.dp)
            )
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BasicText(AppStrings.get("loading"), style = TextStyle(contentColor.copy(alpha = 0.5f), 16f.sp))
            }
        } else if (apps.isEmpty()) {
            // 明确的失败/空态提示 + 可点击重试，不再是一片空白
            Column(
                Modifier.fillMaxWidth().padding(top = 60f.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (loadFailed) {
                    BasicText(AppStrings.get("operation_failed"), style = TextStyle(Color(0xFFFF3B30), AppLayout.bodySize))
                    Spacer(Modifier.height(6f.dp))
                }
                BasicText("${AppStrings.get("app_manager")} (0)", style = TextStyle(contentColor.copy(alpha = 0.5f), 13f.sp))
                Spacer(Modifier.height(AppLayout.innerGap))
                LiquidButton(
                    onClick = { reloadTick++ },
                    backdrop = backdrop,
                    modifier = Modifier.height(38f.dp),
                    tint = Color(0xFF0088FF)
                ) {
                    BasicText(AppStrings.get("redetect"), Modifier.padding(horizontal = 14f.dp), style = TextStyle(Color.White, 12f.sp))
                }
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
        // 真实应用图标（数据来自 ADBTools.getAppIconBase64，按需解码 + 缓存）；
        // 取不到时 AppIconView 内部回退到首字母占位。
        AppIconView(
            packageName = app.packageName,
            appName = app.appName,
            backdrop = backdrop,
            isSystem = app.isSystem,
            tint = if (app.isSystem) Color(0xFFFF9500).copy(alpha = 0.3f) else AppTheme.accent.copy(alpha = 0.3f)
        )
        Spacer(Modifier.width(AppLayout.innerGap))
        Column(Modifier.weight(1f)) {
            BasicText(app.appName, style = TextStyle(contentColor, 15f.sp, androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 1)
            BasicText(
                app.packageName,
                style = TextStyle(contentColor.copy(alpha = 0.5f), AppLayout.captionSize),
                maxLines = 1
            )
        }
        if (app.isFrozen) {
            BasicText(AppStrings.get("frozen"), style = TextStyle(Color(0xFFFF3B30), AppLayout.captionSize))
        }
    }
}
