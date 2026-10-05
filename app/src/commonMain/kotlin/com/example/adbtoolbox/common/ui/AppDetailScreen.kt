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
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppInfoData
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.PermissionInfoData
import com.example.adbtoolbox.common.theme.AppLayout
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 后台线程执行 + 异常转成 null（CancellationException 正常抛出，不吞掉协程取消）。
 * 本文件私有，避免与其它页面重名。
 */
private suspend fun <T> appDetailBackgroundOrNull(block: () -> T): T? = try {
    withContext(Dispatchers.Default) { block() }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}

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
    var actionOk by remember { mutableStateOf(true) }
    var isBusy by remember { mutableStateOf(false) }
    // null = 检测中, true = 有 Shizuku/Dhizuku 或 Root, false = 都不可用
    var privileged by remember { mutableStateOf<Boolean?>(null) }
    var pendingConfirmLabel by remember { mutableStateOf<String?>(null) }
    var pendingConfirmHint by remember { mutableStateOf("") }
    var pendingConfirmRun by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingRevokePermission by remember { mutableStateOf<String?>(null) }
    var uninstalled by remember { mutableStateOf(false) }
    var loadFinished by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(packageName) {
        loadFinished = false
        if (packageName.isEmpty()) {
            appInfo = null
            permissions = emptyList()
            privileged = false
            loadFinished = true
            return@LaunchedEffect
        }
        // 读取应用信息（后台线程），同时把最新列表写回全局缓存，返回列表页时状态一致
        val loaded = appDetailBackgroundOrNull {
            val apps = ADBTools.getInstalledApps()
            if (apps.isNotEmpty()) {
                AppCache.installedApps.value = apps
                AppCache.appsLoaded.value = true
            }
            apps.find { it.packageName == packageName }
        }
        appInfo = loaded
        permissions = appDetailBackgroundOrNull { ADBTools.getAppPermissions(packageName) } ?: emptyList()
        // 高危操作前置检测：Shizuku/Dhizuku 或 Root 至少一个可用
        privileged = appDetailBackgroundOrNull { ADBTools.isShizukuAvailable() || ADBTools.isRooted() } ?: false
        loadFinished = true
    }

    // 卸载成功后自动返回列表页（列表缓存已刷新，不会显示已卸载的应用）
    LaunchedEffect(uninstalled) {
        if (uninstalled) {
            delay(900)
            onBack()
        }
    }

    /** 操作完成后重新读取应用状态与权限，保证界面不是旧状态 */
    suspend fun refreshState() {
        val apps = appDetailBackgroundOrNull { ADBTools.getInstalledApps() }
        if (!apps.isNullOrEmpty()) {
            AppCache.installedApps.value = apps
            AppCache.appsLoaded.value = true
            apps.find { it.packageName == packageName }?.let { appInfo = it }
        }
        val perms = appDetailBackgroundOrNull { ADBTools.getAppPermissions(packageName) }
        if (perms != null) {
            permissions = perms
            AppCache.setPermissions(packageName, perms)
        }
    }

    /** 真正执行操作；失败原因会显示出来，不再静默吞掉 */
    fun startAction(
        label: String,
        action: () -> Boolean,
        onSuccess: () -> Unit = {},
        refreshAfter: Boolean = true
    ) {
        if (isBusy) return
        isBusy = true
        actionOk = true
        actionResult = "${AppStrings.get("executing")}: $label..."
        scope.launch {
            var errorText: String? = null
            val ok = try {
                withContext(Dispatchers.Default) { action() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorText = e.message ?: AppStrings.get("unknown")
                false
            }
            if (ok) {
                actionOk = true
                actionResult = "$label ${AppStrings.get("success")}"
                onSuccess()
            } else {
                actionOk = false
                actionResult = if (errorText != null) {
                    "$label ${AppStrings.get("failed")} ($errorText)"
                } else {
                    "$label ${AppStrings.get("failed")} (${AppStrings.get("operation_failed")})"
                }
            }
            // 成功/失败都重新读取，避免界面停留在旧状态（用户会误认为“点了没反应”）
            if (refreshAfter) refreshState()
            isBusy = false
        }
    }

    /** 入口：先做权限检测，破坏性操作走二次确认 */
    fun requestAction(
        label: String,
        destructive: Boolean = false,
        confirmHint: String = "",
        onSuccess: () -> Unit = {},
        refreshAfter: Boolean = true,
        action: () -> Boolean
    ) {
        if (isBusy) return
        if (privileged != true) {
            // 不可用时给出可操作提示，而不是静默失败
            pendingConfirmLabel = null
            pendingConfirmRun = null
            actionOk = false
            actionResult = "${AppStrings.get("operation_failed")} · ${AppStrings.get("need_root_or_shizuku")}"
            return
        }
        if (destructive) {
            actionResult = ""
            pendingConfirmLabel = label
            pendingConfirmHint = confirmHint
            pendingConfirmRun = { startAction(label, action, onSuccess, refreshAfter) }
            return
        }
        pendingConfirmLabel = null
        pendingConfirmRun = null
        startAction(label, action, onSuccess, refreshAfter)
    }

    fun confirmPending() {
        val run = pendingConfirmRun
        pendingConfirmLabel = null
        pendingConfirmHint = ""
        pendingConfirmRun = null
        run?.invoke()
    }

    fun cancelPending() {
        pendingConfirmLabel = null
        pendingConfirmHint = ""
        pendingConfirmRun = null
    }

    /** 点击一条权限：授权直接执行，撤销属于破坏性操作，先二次确认 */
    fun togglePermission(perm: PermissionInfoData) {
        if (isBusy) return
        if (privileged != true) {
            actionOk = false
            actionResult = "${AppStrings.get("operation_failed")} · ${AppStrings.get("need_root_or_shizuku")}"
            return
        }
        if (perm.isGranted) {
            pendingRevokePermission = perm.permission
            return
        }
        startAction(
            label = "${AppStrings.get("grant")} ${perm.name}",
            action = { ADBTools.grantPermission(packageName, perm.permission) }
        )
    }

    fun confirmRevokePermission(perm: PermissionInfoData) {
        pendingRevokePermission = null
        startAction(
            label = "${AppStrings.get("revoke")} ${perm.name}",
            action = { ADBTools.revokePermission(packageName, perm.permission) }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppLayout.screenH)
    ) {
        Spacer(Modifier.height(AppLayout.screenTop))

        // 顶部返回和标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 读取失败/应用不存在时给出明确提示，避免出现空白页面
        if (loadFinished && appInfo == null) {
            GlassCard(backdrop = backdrop, pageType = "apps") {
                Column(Modifier.padding(AppLayout.cardPad)) {
                    BasicText(packageName.ifEmpty { AppStrings.get("unknown") }, style = TextStyle(contentColor, AppLayout.bodySize))
                    Spacer(Modifier.height(6f.dp))
                    BasicText(AppStrings.get("operation_failed"), style = TextStyle(Color(0xFFFF3B30), 12f.sp))
                }
            }
            Spacer(Modifier.height(AppLayout.sectionGap))
        }

        // 应用信息卡片
        appInfo?.let { app ->
            GlassCard(backdrop = backdrop, pageType = "apps") {
                Column(Modifier.padding(AppLayout.cardPad)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 真实应用图标，取不到时内部回退首字母占位
                        AppIconView(
                            packageName = app.packageName,
                            appName = app.appName,
                            backdrop = backdrop,
                            size = 56f.dp,
                            corner = 16f.dp,
                            isSystem = app.isSystem,
                            tint = AppTheme.accent.copy(alpha = 0.3f)
                        )
                        Spacer(Modifier.width(14f.dp))
                        Column(Modifier.weight(1f)) {
                            BasicText(app.appName, style = TextStyle(contentColor, AppLayout.sectionTitleSize, androidx.compose.ui.text.font.FontWeight.Bold))
                            BasicText(app.packageName, style = TextStyle(contentColor.copy(alpha = 0.5f), AppLayout.captionSize))
                            BasicText("${AppStrings.get("version")} ${app.versionName}", style = TextStyle(contentColor.copy(alpha = 0.5f), AppLayout.captionSize))
                        }
                    }
                    Spacer(Modifier.height(AppLayout.innerGap))
                    InfoRow(AppStrings.get("type"), if (app.isSystem) AppStrings.get("system_app") else AppStrings.get("user_app"), contentColor)
                    InfoRow(AppStrings.get("status"), if (app.isFrozen) AppStrings.get("frozen") else AppStrings.get("normal"), contentColor)
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 权限不可用提示 + 一键请求 Shizuku
        if (privileged == false) {
            GlassCard(backdrop = backdrop, pageType = "apps") {
                Column(Modifier.padding(AppLayout.cardPad)) {
                    BasicText(
                        "${AppStrings.get("need_root_or_shizuku")} · ${AppStrings.get("authorization_required")}",
                        style = TextStyle(Color(0xFFFF9500), 12f.sp)
                    )
                    Spacer(Modifier.height(10f.dp))
                    LiquidButton(
                        onClick = {
                            // 与 SettingsScreen 一致：主线程请求 Shizuku 权限
                            ADBTools.requestShizukuPermission()
                            scope.launch {
                                delay(1200) // Shizuku 状态有 1 秒缓存，稍等再复查
                                privileged = appDetailBackgroundOrNull {
                                    ADBTools.isShizukuAvailable() || ADBTools.isRooted()
                                } ?: false
                                if (privileged != true) {
                                    actionOk = false
                                    actionResult = "${AppStrings.get("operation_failed")} · ${AppStrings.get("shizuku_not_connected")}"
                                }
                            }
                        },
                        backdrop = backdrop,
                        modifier = Modifier.height(36.dp),
                        tint = Color(0xFF0088FF)
                    ) {
                        BasicText(AppStrings.get("request_shizuku"), Modifier.padding(horizontal = 10f.dp), style = TextStyle(Color.White, 12f.sp))
                    }
                }
            }
            Spacer(Modifier.height(AppLayout.sectionGap))
        }

        // 操作按钮
        GlassCard(backdrop = backdrop, pageType = "apps") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("app_actions"), contentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    if (appInfo?.isFrozen == true) {
                        ActionBtn(backdrop, AppStrings.get("unfreeze"), Color(0xFF34C759)) {
                            requestAction(AppStrings.get("unfreeze")) { ADBTools.unfreezeApp(packageName) }
                        }
                    } else {
                        ActionBtn(backdrop, AppStrings.get("freeze"), Color(0xFFFF9500)) {
                            requestAction(AppStrings.get("freeze")) { ADBTools.freezeApp(packageName) }
                        }
                    }
                    ActionBtn(backdrop, AppStrings.get("force_stop"), Color(0xFFFF3B30)) {
                        requestAction(AppStrings.get("force_stop")) { ADBTools.forceStop(packageName) }
                    }
                    ActionBtn(backdrop, AppStrings.get("clear_cache"), AppTheme.accent) {
                        requestAction(
                            label = AppStrings.get("clear_cache"),
                            destructive = true,
                            // 这里只清缓存（ADBTools.clearCache 已改成 cache-only），语义必须说准，
                            // 否则用户会以为点一下就把应用数据清了
                            confirmHint = AppStrings.get("clear_cache_hint")
                        ) { ADBTools.clearCache(packageName) }
                    }
                }
                Spacer(Modifier.height(8f.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    // 清除数据是另一件事：不可逆，必须单独确认，绝不作为清缓存的兜底
                    ActionBtn(backdrop, AppStrings.get("clear_data"), Color(0xFFFF2D55)) {
                        requestAction(
                            label = AppStrings.get("clear_data"),
                            destructive = true,
                            confirmHint = AppStrings.get("clear_data_warn_hint")
                        ) { ADBTools.clearAppData(packageName) }
                    }
                    ActionBtn(backdrop, AppStrings.get("uninstall"), Color(0xFFFF3B30)) {
                        requestAction(
                            label = AppStrings.get("uninstall"),
                            destructive = true,
                            onSuccess = {
                                // 立即从全局缓存移除，返回列表页时不会再显示已卸载的应用
                                AppCache.installedApps.value =
                                    AppCache.installedApps.value.filterNot { it.packageName == packageName }
                                AppCache.clearPermissions(packageName)
                                uninstalled = true
                            },
                            refreshAfter = false,
                            action = { ADBTools.uninstallApp(packageName) }
                        )
                    }
                }

                // 破坏性操作二次确认
                pendingConfirmLabel?.let { confirmLabel ->
                    Spacer(Modifier.height(10f.dp))
                    BasicText(
                        "${pendingConfirmHint.ifEmpty { confirmLabel }} · ${appInfo?.appName ?: packageName}?",
                        style = TextStyle(Color(0xFFFF9500), 12f.sp)
                    )
                    Spacer(Modifier.height(8f.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                        ActionBtn(backdrop, AppStrings.get("execute"), Color(0xFFFF3B30)) { confirmPending() }
                        ActionBtn(backdrop, AppStrings.get("back"), Color(0xFF8E8E93)) { cancelPending() }
                    }
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 权限列表（点击一次切换授权状态；撤销需二次确认）
        GlassCard(backdrop = backdrop, pageType = "apps") {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle("${AppStrings.get("permissions")} (${permissions.size})", contentColor)
                permissions.take(30).forEach { perm ->
                    Spacer(Modifier.height(6f.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .liquidGlassItem(
                                    backdrop = backdrop,
                                    corner = 12.dp,
                                    onClick = { togglePermission(perm) }
                                )
                                .padding(horizontal = 10f.dp, vertical = 8f.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                BasicText(perm.name, style = TextStyle(contentColor, 12f.sp), maxLines = 1)
                                BasicText(perm.permission, style = TextStyle(contentColor.copy(alpha = 0.4f), 9f.sp), maxLines = 1)
                            }
                            BasicText(
                                "${if (perm.isGranted) AppStrings.get("allowed") else AppStrings.get("denied")} ›",
                                style = TextStyle(if (perm.isGranted) Color(0xFF34C759) else Color(0xFFFF3B30), 10f.sp)
                            )
                        }
                        if (pendingRevokePermission == perm.permission) {
                            Row(
                                Modifier.fillMaxWidth().padding(top = 4f.dp, start = 10f.dp, end = 10f.dp),
                                horizontalArrangement = Arrangement.spacedBy(8f.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicText(
                                    "${AppStrings.get("revoke")} ${perm.name}?",
                                    Modifier.weight(1f),
                                    style = TextStyle(Color(0xFFFF9500), AppLayout.captionSize)
                                )
                                AppDetailSmallButton(backdrop, AppStrings.get("execute"), Color(0xFFFF3B30)) { confirmRevokePermission(perm) }
                                AppDetailSmallButton(backdrop, AppStrings.get("back"), Color(0xFF8E8E93)) { pendingRevokePermission = null }
                            }
                        }
                    }
                }
                if (permissions.size > 30) {
                    Spacer(Modifier.height(8f.dp))
                    BasicText("...${AppStrings.get("more_items")} ${permissions.size - 30}", style = TextStyle(contentColor.copy(alpha = 0.4f), AppLayout.captionSize))
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 操作结果
        if (actionResult.isNotEmpty()) {
            GlassCard(backdrop = backdrop, pageType = "apps") {
                Column(Modifier.padding(AppLayout.cardPad)) {
                    BasicText(
                        actionResult,
                        style = TextStyle(if (actionOk) contentColor else Color(0xFFFF3B30), 13f.sp)
                    )
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

@Composable
fun AppDetailSmallButton(backdrop: Backdrop, label: String, tint: Color, onClick: () -> Unit) {
    LiquidButton(
        onClick = onClick,
        backdrop = backdrop,
        modifier = Modifier.height(30f.dp),
        tint = tint
    ) {
        BasicText(label, Modifier.padding(horizontal = 10f.dp), style = TextStyle(Color.White, AppLayout.captionSize))
    }
}
