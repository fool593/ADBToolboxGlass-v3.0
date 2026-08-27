package com.example.adbtoolbox.common.ui

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.PluginData
import com.example.adbtoolbox.common.PluginManager
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
actual fun PluginDetailScreen(
    plugin: PluginData,
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    var currentPlugin by remember { mutableStateOf(plugin) }
    var actionOutput by remember { mutableStateOf("") }
    var showActionOutput by remember { mutableStateOf(false) }
    var webUIPath by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentPlugin.id) {
        webUIPath = withContext(Dispatchers.Default) { PluginManager.getWebUIPath(currentPlugin.id) }
    }

    fun refreshPlugin() {
        scope.launch {
            val plugins = withContext(Dispatchers.Default) { PluginManager.getInstalledPlugins() }
            val updated = plugins.find { it.id == currentPlugin.id }
            if (updated != null) currentPlugin = updated
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16f.dp)
    ) {
        Spacer(Modifier.height(24f.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            LiquidButton(
                onClick = onBack,
                backdrop = backdrop,
                modifier = Modifier.height(36f.dp),
                tint = Color(0xFF0088FF)
            ) {
                BasicText(
                    AppStrings.get("back"),
                    Modifier.padding(horizontal = 10f.dp),
                    style = TextStyle(Color.White, 12f.sp)
                )
            }
            Spacer(Modifier.width(12f.dp))
            BasicText(
                currentPlugin.name,
                style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium)
            )
        }
        Spacer(Modifier.height(16f.dp))

        GlassCard(backdrop = backdrop) {
            Column(Modifier.padding(20f.dp)) {
                InfoRow("ID", currentPlugin.id, contentColor)
                InfoRow(AppStrings.get("version"), "v${currentPlugin.version} (${currentPlugin.versionCode})", contentColor)
                InfoRow(AppStrings.get("author"), currentPlugin.author, contentColor)
                InfoRow(
                    AppStrings.get("status"),
                    if (currentPlugin.isEnabled) AppStrings.get("enabled") else AppStrings.get("disabled"),
                    contentColor
                )
                if (currentPlugin.description.isNotBlank()) {
                    Spacer(Modifier.height(8f.dp))
                    BasicText(
                        currentPlugin.description,
                        style = TextStyle(contentColor.copy(alpha = 0.7f), 12f.sp)
                    )
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        GlassCard(backdrop = backdrop) {
            Column(Modifier.padding(20f.dp)) {
                SectionTitle(AppStrings.get("actions"), contentColor)
                Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                    LiquidButton(
                        onClick = {
                            scope.launch {
                                if (currentPlugin.isEnabled) {
                                    withContext(Dispatchers.Default) { PluginManager.disablePlugin(currentPlugin.id) }
                                } else {
                                    withContext(Dispatchers.Default) { PluginManager.enablePlugin(currentPlugin.id) }
                                }
                                refreshPlugin()
                            }
                        },
                        backdrop = backdrop,
                        modifier = Modifier.height(40f.dp).weight(1f),
                        tint = if (currentPlugin.isEnabled) Color(0xFFFF9500) else Color(0xFF34C759)
                    ) {
                        BasicText(
                            if (currentPlugin.isEnabled) AppStrings.get("disable") else AppStrings.get("enable"),
                            Modifier.padding(horizontal = 4f.dp),
                            style = TextStyle(Color.White, 12f.sp)
                        )
                    }
                    if (currentPlugin.hasAction) {
                        LiquidButton(
                            onClick = {
                                scope.launch {
                                    actionOutput = withContext(Dispatchers.Default) { PluginManager.runAction(currentPlugin.id) }
                                    showActionOutput = true
                                }
                            },
                            backdrop = backdrop,
                            modifier = Modifier.height(40f.dp).weight(1f),
                            tint = Color(0xFF0088FF)
                        ) {
                            BasicText(
                                AppStrings.get("run_action"),
                                Modifier.padding(horizontal = 4f.dp),
                                style = TextStyle(Color.White, 12f.sp)
                            )
                        }
                    }
                    LiquidButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.Default) { PluginManager.uninstallPlugin(currentPlugin.id) }
                                onBack()
                            }
                        },
                        backdrop = backdrop,
                        modifier = Modifier.height(40f.dp).weight(1f),
                        tint = Color(0xFFFF3B30)
                    ) {
                        BasicText(
                            AppStrings.get("uninstall"),
                            Modifier.padding(horizontal = 4f.dp),
                            style = TextStyle(Color.White, 12f.sp)
                        )
                    }
                }
            }
        }

        if (showActionOutput && actionOutput.isNotEmpty()) {
            Spacer(Modifier.height(16f.dp))
            GlassCard(backdrop = backdrop) {
                Column(Modifier.padding(20f.dp)) {
                    SectionTitle(AppStrings.get("action_output"), contentColor)
                    BasicText(
                        actionOutput,
                        style = TextStyle(contentColor, 11f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                }
            }
        }

        if (currentPlugin.hasWebUI) {
            Spacer(Modifier.height(16f.dp))
            GlassCard(backdrop = backdrop) {
                Column(Modifier.padding(20f.dp)) {
                    SectionTitle("WebUI", contentColor)
                    Spacer(Modifier.height(8f.dp))
                    val webUIPathLocal = webUIPath
                    if (webUIPathLocal != null) {
                        AndroidView(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(400f.dp),
                            factory = { context ->
                                WebView(context).apply {
                                    webViewClient = WebViewClient()
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.allowFileAccess = true
                                    settings.allowContentAccess = true
                                    settings.allowFileAccessFromFileURLs = true
                                    settings.allowUniversalAccessFromFileURLs = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    loadUrl("file://$webUIPathLocal")
                                }
                            }
                        )
                    } else {
                        BasicText(
                            "WebUI not found",
                            style = TextStyle(contentColor.copy(alpha = 0.5f), 12f.sp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(80f.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String, contentColor: Color) {
    Row(Modifier.padding(vertical = 4f.dp)) {
        BasicText(
            "$label: ",
            style = TextStyle(contentColor.copy(alpha = 0.5f), 12f.sp),
            modifier = Modifier.width(80f.dp)
        )
        BasicText(
            value,
            style = TextStyle(contentColor, 12f.sp)
        )
    }
}
