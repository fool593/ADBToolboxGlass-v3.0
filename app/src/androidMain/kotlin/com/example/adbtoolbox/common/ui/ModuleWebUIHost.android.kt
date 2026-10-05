package com.example.adbtoolbox.common.ui

import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
 * 模块 WebUI 宿主的 Android 实现：一个 WebView + 顶部关闭栏 + 可见的加载/错误信息。
 *
 * 为什么单独一层宿主：详情页要随时把界面让给 WebUI，而 WebView 的生命周期必须可控
 * （[AndroidView] 的 onRelease 里销毁），单独一层既保证关闭后定时器与 js 桥一起释放，
 * 又不污染 commonMain（commonMain 没有 android API）。
 *
 * 关于"打开后到底能不能用"（这是用户明确要求检查的点），这里做了四件事，让失败**可见**：
 * 1. debug 构建下开启 `WebView.setWebContentsDebuggingEnabled(true)` —— 可以用
 *    Chrome 打开 `chrome://inspect` 直接调试模块页面（看 DOM、console、网络、断点）。
 *    只在可调试构建里开，release 不开（避免把模块页面暴露给任意 adb 调试）。
 * 2. 主框架加载失败 / HTTP 错误 / 渲染进程崩溃都会把**真实原因**显示在顶部条里，而不是白屏。
 * 3. 页面 console 的最后一条消息也会显示出来，便于排查"页面在报错但你看不到"。
 * 4. 加载进度可见；入口文件不存在时提前给出明确提示。
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

    // 本地入口文件可能在这一刻已经不在了（模块被卸载、文件被删、缓存被清）。
    // 直接 loadUrl 只会得到一片空白，所以先做一次真实存在性检查，不存在就说明具体原因。
    val localPath = remember(url) {
        when {
            url.startsWith("file://") -> try {
                Uri.parse(url).path
            } catch (e: Exception) {
                null
            }
            url.contains("://") -> null // http/https/content 等交给 WebView 自己处理
            else -> url
        }
    }
    val entryMissing = remember(localPath) {
        val path = localPath
        path != null && !File(path).exists()
    }

    var progress by remember(url) { mutableIntStateOf(0) }
    var finished by remember(url) { mutableStateOf(false) }
    var pageError by remember(url) { mutableStateOf<String?>(null) }
    var consoleTail by remember(url) { mutableStateOf("") }

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

        // 加载进度：不引入 Material，用一条细条自己画
        if (!finished && !entryMissing) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((progress.coerceIn(0, 100)) / 100f)
                        .height(2.dp)
                        .background(AppTheme.accent)
                )
            }
        }

        // 真实的失败原因（主框架错误 / HTTP 错误 / 渲染进程崩溃 / console 报错）都显示在这里
        pageError?.let { message ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xE6FF3B30))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                BasicText(
                    message,
                    style = TextStyle(Color.White, 11.sp)
                )
            }
        }
        if (consoleTail.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x66000000))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                BasicText(
                    "console: $consoleTail",
                    style = TextStyle(Color.White.copy(alpha = 0.85f), 10.sp)
                )
            }
        }

        if (entryMissing) {
            // 文件确实不在了：明确说明，而不是渲染一张空白页
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicText(
                    hwFormat(AppStrings.get("plugin_webui_gone"), localPath ?: url),
                    style = TextStyle(AppTheme.onAccent.copy(alpha = 0.8f), 12.sp)
                )
            }
        } else {
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        // 只有可调试构建才开 WebView 远程调试：release 不开，
                        // 避免任意 adb 连接都能调试模块页面。debug 下用 chrome://inspect 即可。
                        val debuggable = (context.applicationInfo.flags and
                                ApplicationInfo.FLAG_DEBUGGABLE) != 0
                        if (debuggable) {
                            try {
                                WebView.setWebContentsDebuggingEnabled(true)
                            } catch (e: Exception) {
                                // 个别 ROM 关掉了该接口，忽略即可，不影响加载
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                finished = true
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                // 只有主框架失败才是"页面打不开"；子资源失败不刷屏
                                if (request == null || request.isForMainFrame) {
                                    val code = error?.errorCode ?: -1
                                    val desc = error?.description?.toString().orEmpty()
                                    pageError = "加载失败($code): ${desc.ifBlank { "未知原因" }}"
                                    finished = true
                                }
                            }

                            override fun onReceivedHttpError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                errorResponse: WebResourceResponse?
                            ) {
                                if (request != null && request.isForMainFrame) {
                                    pageError = "HTTP 错误: ${errorResponse?.statusCode ?: -1} " +
                                            "${errorResponse?.reasonPhrase.orEmpty()}"
                                }
                            }

                            override fun onRenderProcessGone(
                                view: WebView?,
                                detail: android.webkit.RenderProcessGoneDetail?
                            ): Boolean {
                                pageError = "WebView 渲染进程已退出，请关闭后重试"
                                return true
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                                if (newProgress >= 100) finished = true
                            }

                            override fun onConsoleMessage(message: ConsoleMessage?): Boolean {
                                // 记下最后一条 console 消息：模块页面报错时用户能直接看到
                                consoleTail = message?.message()?.take(200).orEmpty()
                                return true
                            }
                        }
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
                    // 先停止加载 → 清空页面 → 从父容器摘掉 → 销毁。
                    // 不摘父容器而直接 destroy，在部分 ROM 上 WebView 仍被 ViewGroup 引用着，
                    // 属于常见的 WebView 泄漏写法，所以这里显式移除。
                    try {
                        webView.stopLoading()
                        webView.loadUrl("about:blank")
                        (webView.parent as? android.view.ViewGroup)?.removeView(webView)
                        webView.removeAllViews()
                        webView.destroy()
                    } catch (e: Exception) {
                        // 已销毁的情况下重复调用会抛异常，忽略
                    }
                }
            )
        }
    }
}
