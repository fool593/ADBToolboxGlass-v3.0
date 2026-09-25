package com.example.adbtoolbox.common.ui

import com.example.adbtoolbox.common.GlassEffectConfig
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
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.example.adbtoolbox.common.PermissionInfoData
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@Composable
fun PermissionsScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    var apps by remember { mutableStateOf<List<AppInfoData>>(emptyList()) }
    var selectedApp by remember { mutableStateOf<AppInfoData?>(null) }
    var permissions by remember { mutableStateOf<List<PermissionInfoData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        isLoading = true
        // 优先从预加载缓存读取，秒开
        if (AppCache.appsLoaded.value) {
            apps = AppCache.installedApps.value.filter { !it.isSystem }.take(50)
        } else {
            val allApps = withContext(Dispatchers.Default) { ADBTools.getInstalledApps() }
            AppCache.installedApps.value = allApps
            AppCache.appsLoaded.value = true
            apps = allApps.filter { !it.isSystem }.take(50)
        }
        isLoading = false
    }

    LaunchedEffect(selectedApp) {
        selectedApp?.let { app ->
            // 优先从权限缓存读取
            val cached = AppCache.getPermissions(app.packageName)
            if (cached != null) {
                permissions = cached
            } else {
                val perms = withContext(Dispatchers.Default) { ADBTools.getAppPermissions(app.packageName) }
                AppCache.setPermissions(app.packageName, perms)
                permissions = perms
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16f.dp)) {
        Spacer(Modifier.height(24f.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
            Spacer(Modifier.height(12f.dp))
            BasicText(AppStrings.get("permission_manager"), style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold))
        }
        Spacer(Modifier.height(12f.dp))

        if (selectedApp == null) {
            BasicText("${AppStrings.get("app_manager")} - ${AppStrings.get("app_permissions")}", style = TextStyle(contentColor.copy(alpha = 0.5f), 14f.sp))
            Spacer(Modifier.height(8f.dp))
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    BasicText(AppStrings.get("loading"), style = TextStyle(contentColor.copy(alpha = 0.5f), 16f.sp))
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6f.dp)) {
                    items(apps, key = { it.packageName }) { app ->
                        AppListItem(app = app, backdrop = backdrop, contentColor = contentColor, onClick = { selectedApp = app })
                    }
                    item { Spacer(Modifier.height(80f.dp)) }
                }
            }
        } else {
            // 应用权限详情
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiquidButton(
                    onClick = { selectedApp = null },
                    backdrop = backdrop,
                    modifier = Modifier.height(36f.dp),
                    tint = Color(0xFF0088FF)
                ) {
                    BasicText(AppStrings.get("back"), Modifier.padding(horizontal = 10f.dp), style = TextStyle(Color.White, 12f.sp))
                }
                Spacer(Modifier.width(12f.dp))
                BasicText(selectedApp!!.appName, style = TextStyle(contentColor, 16f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
            }
            Spacer(Modifier.height(12f.dp))
            BasicText("${AppStrings.get("app_permissions")}: ${permissions.size}", style = TextStyle(contentColor.copy(alpha = 0.5f), 12f.sp))
            Spacer(Modifier.height(8f.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6f.dp)) {
                items(permissions, key = { it.permission }) { perm ->
                    PermissionItem(
                        perm = perm,
                        backdrop = backdrop,
                        contentColor = contentColor,
                        onToggle = {
                            scope.launch {
                                try {
                                    if (perm.isGranted) {
                                        withContext(Dispatchers.Default) { ADBTools.revokePermission(selectedApp!!.packageName, perm.permission) }
                                    } else {
                                        withContext(Dispatchers.Default) { ADBTools.grantPermission(selectedApp!!.packageName, perm.permission) }
                                    }
                                    val perms = withContext(Dispatchers.Default) { ADBTools.getAppPermissions(selectedApp!!.packageName) }
                                    AppCache.setPermissions(selectedApp!!.packageName, perms)
                                    permissions = perms
                                } catch (e: Exception) {
                                    // 忽略错误，防止闪退
                                }
                            }
                        }
                    )
                }
                item { Spacer(Modifier.height(80f.dp)) }
            }
        }
    }
}

@Composable
fun PermissionItem(
    perm: PermissionInfoData,
    backdrop: Backdrop,
    contentColor: Color,
    onToggle: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .liquidGlassItem(backdrop = backdrop, corner = 14.dp, onClick = onToggle)
            .padding(14f.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(perm.name, style = TextStyle(contentColor, 14f.sp, androidx.compose.ui.text.font.FontWeight.Medium), maxLines = 1)
            BasicText(perm.permission, style = TextStyle(contentColor.copy(alpha = 0.4f), 10f.sp), maxLines = 1)
        }
        Spacer(Modifier.width(8f.dp))
        BasicText(
            if (perm.isGranted) AppStrings.get("granted") else AppStrings.get("not_granted"),
            style = TextStyle(if (perm.isGranted) Color(0xFF34C759) else Color(0xFFFF3B30), 12f.sp)
        )
    }
}

@Composable
fun Modifier.clipGlass(backdrop: Backdrop, radius: Float): Modifier {
    return this.then(
        Modifier
            .clip(com.kyant.shapes.RoundedRectangle(radius.dp))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { com.kyant.shapes.RoundedRectangle(radius.dp) },
                effects = {
                    if (GlassEffectConfig.settingsEnableVibrancy.value) vibrancy()
                    blur(GlassEffectConfig.settingsBlurRadius.value.dp.toPx())
                    lens(
                        GlassEffectConfig.settingsRefractionHeight.value * 48f.dp.toPx(),
                        GlassEffectConfig.settingsRefractionAmount.value * 48f.dp.toPx(),
                        chromaticAberration = GlassEffectConfig.chromaticAberration.value > 0f
                    )
                }
            )
    )
}
