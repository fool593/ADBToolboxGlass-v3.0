package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.AppInfoData
import com.example.adbtoolbox.common.PermissionInfoData
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AppDetailScreen(
    backdrop: Backdrop,
    contentColor: Color,
    packageName: String,
    onBack: () -> Unit
) {
    var appInfo by remember { mutableStateOf<AppInfoData?>(null) }
    var permissions by remember { mutableStateOf<List<PermissionInfoData>>(emptyList()) }
    var actionResult by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(packageName) {
        val apps = withContext(Dispatchers.Default) { ADBTools.getInstalledApps() }
        appInfo = apps.find { it.packageName == packageName }
        permissions = withContext(Dispatchers.Default) { ADBTools.getAppPermissions(packageName) }
    }

    fun runAction(label: String, action: () -> Boolean) {
        actionResult = "${AppStrings.get("executing")}: $label..."
        scope.launch {
            val ok = try {
                withContext(Dispatchers.Default) { action() }
            } catch (e: Exception) {
                false
            }
            actionResult = if (ok) "$label ${AppStrings.get("success")}" else "$label ${AppStrings.get("failed")} (${AppStrings.get("need_root_or_shizuku")})"
            // 刷新应用状态
            val apps = withContext(Dispatchers.Default) { ADBTools.getInstalledApps() }
            appInfo = apps.find { it.packageName == packageName }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16f.dp)
    ) {
        Spacer(Modifier.height(24f.dp))

        // 顶部返回和标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
        }

        Spacer(Modifier.height(16f.dp))

        // 应用信息卡片
        appInfo?.let { app ->
            GlassCard(backdrop = backdrop, pageType = "apps") {
                Column(Modifier.padding(20f.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(56f.dp)
                                .clip(RoundedCornerShape(16f.dp))
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { RoundedRectangle(16f.dp) },
                                    effects = { blur(10f.dp.toPx()) },
                                    onDrawSurface = { drawRect(Color(0xFF0088FF).copy(0.3f)) }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            BasicText(app.appName.take(1), style = TextStyle(Color.White, 22f.sp, androidx.compose.ui.text.font.FontWeight.Bold))
                        }
                        Spacer(Modifier.width(14f.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(app.appName, style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Bold))
                            BasicText(app.packageName, style = TextStyle(contentColor.copy(alpha = 0.5f), 11f.sp))
                            BasicText("${AppStrings.get("version")} ${app.versionName}", style = TextStyle(contentColor.copy(alpha = 0.5f), 11f.sp))
                        }
                    }
                    Spacer(Modifier.height(12f.dp))
                    InfoRow(AppStrings.get("type"), if (app.isSystem) AppStrings.get("system_app") else AppStrings.get("user_app"), contentColor)
                    InfoRow(AppStrings.get("status"), if (app.isFrozen) AppStrings.get("frozen") else AppStrings.get("normal"), contentColor)
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 操作按钮
        GlassCard(backdrop = backdrop, pageType = "apps") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("app_actions"), contentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    if (appInfo?.isFrozen == true) {
                        ActionBtn(backdrop, AppStrings.get("unfreeze"), Color(0xFF34C759)) { runAction(AppStrings.get("unfreeze")) { ADBTools.unfreezeApp(packageName) } }
                    } else {
                        ActionBtn(backdrop, AppStrings.get("freeze"), Color(0xFFFF9500)) { runAction(AppStrings.get("freeze")) { ADBTools.freezeApp(packageName) } }
                    }
                    ActionBtn(backdrop, AppStrings.get("force_stop"), Color(0xFFFF3B30)) { runAction(AppStrings.get("force_stop")) { ADBTools.forceStop(packageName) } }
                    ActionBtn(backdrop, AppStrings.get("clear_cache"), Color(0xFF0088FF)) { runAction(AppStrings.get("clear_cache")) { ADBTools.clearCache(packageName) } }
                }
                Spacer(Modifier.height(8f.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    ActionBtn(backdrop, AppStrings.get("uninstall"), Color(0xFFFF3B30)) { runAction(AppStrings.get("uninstall")) { ADBTools.uninstallApp(packageName) } }
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 权限列表
        if (permissions.isNotEmpty()) {
            GlassCard(backdrop = backdrop, pageType = "apps") {
                Column(Modifier.padding(20f.dp)) {
                    SectionTitle("${AppStrings.get("permissions")} (${permissions.size})", contentColor)
                    permissions.take(30).forEach { perm ->
                        Spacer(Modifier.height(6f.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                BasicText(perm.name, style = TextStyle(contentColor, 12f.sp), maxLines = 1)
                                BasicText(perm.permission, style = TextStyle(contentColor.copy(alpha = 0.4f), 9f.sp), maxLines = 1)
                            }
                            BasicText(
                                if (perm.isGranted) AppStrings.get("allowed") else AppStrings.get("denied"),
                                style = TextStyle(if (perm.isGranted) Color(0xFF34C759) else Color(0xFFFF3B30), 10f.sp)
                            )
                        }
                    }
                    if (permissions.size > 30) {
                        Spacer(Modifier.height(8f.dp))
                        BasicText("...${AppStrings.get("more_items")} ${permissions.size - 30}", style = TextStyle(contentColor.copy(alpha = 0.4f), 11f.sp))
                    }
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 操作结果
        if (actionResult.isNotEmpty()) {
            GlassCard(backdrop = backdrop, pageType = "apps") {
                Column(Modifier.padding(20f.dp)) {
                    BasicText(actionResult, style = TextStyle(contentColor, 13f.sp))
                }
            }
        }

        Spacer(Modifier.height(80f.dp))
    }
}

@Composable
fun RowScope.ActionBtn(backdrop: Backdrop, label: String, tint: Color, onClick: () -> Unit) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = Modifier.height(40f.dp).weight(1f),
        tint = tint
    ) {
        BasicText(label, Modifier.padding(horizontal = 4f.dp), style = TextStyle(Color.White, 12f.sp))
    }
}
