package com.example.adbtoolbox.common.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.theme.AppTheme
import java.io.File

/**
 * 模块 WebUI 宿主的 Android 实现：一个 WebView + 顶部关闭栏。
 *
 * 为什么不是把 WebView 直接塞进详情页：详情页需要随时把界面让给 WebUI，
 * 而 WebView 的生命周期必须可控（[AndroidView] 的 onRelease 里销毁），
 * 单独一层宿主既不污染 commonMain（commonMain 没有 android API），
 * 也保证关闭后页面里的定时器与 js 桥一起释放。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun ModuleWebUIHost(
    url: String,
    onClose: () -> Unit
) {
    // 入口路径里可能带空格或中文（插件 id 来自 module.prop），
    // 用 Uri.fromFile 生成百分号编码后的 file:// URL，避免 WebView 解析失败。
    val fileUrl = remember(url) {
        if (url.startsWith("file://")) {
            url
        } else {
            try {
                Uri.fromFile(File(url)).toString()
            } catch (e: Exception) {
                "file://$url"
            }
        }
    }

    // 系统返回键也关闭 WebUI：宿主打开期间不让返回键一路退到 Activity（那样会直接退出整个应用）
    BackHandler { onClose() }

    Column(
        Modifier
            .fillMaxSize()
            .background(AppTheme.deep)
            // 宿主是自己盖在列表/详情页上的一层，必须吞掉落在空白区域（顶部栏空白处）的点击，
            // 否则会穿透到底下的插件条目上。WebView 与关闭按钮在自己的区域内先拿到事件，不受影响。
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        // 顶部栏：WebView 没有系统标题栏，必须自带一个明确的出口
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicText(
                AppStrings.get("plugin_webui_title"),
                Modifier.weight(1f),
                style = TextStyle(AppTheme.onAccent, 14.sp, FontWeight.Medium)
            )
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppTheme.accent)
                    .clickable { onClose() }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                BasicText(
                    AppStrings.get("plugin_webui_close"),
                    style = TextStyle(AppTheme.onAccent, 12.sp, FontWeight.Medium)
                )
            }
        }

        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            factory = { context ->
                WebView(context).apply {
                    // 页面内的跳转全部留在 WebView 里，不外抛给系统浏览器
                    webViewClient = WebViewClient()
                    // 默认 WebChromeClient：console 消息走系统日志，不做额外处理
                    webChromeClient = WebChromeClient()
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowFileAccessFromFileURLs = true
                        allowContentAccess = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                    }
                    loadUrl(fileUrl)
                }
            },
            onRelease = { webView ->
                // 必须先停止加载再 destroy，否则正在进行的加载会抛 "WebView destroyed" 警告
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.destroy()
            }
        )
    }
}
