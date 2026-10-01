package com.example.adbtoolbox.common.ui

import androidx.compose.runtime.Composable

/**
 * 模块自带 WebUI 的宿主。
 *
 * AxManager / AXM 风格插件把界面放在 `webroot/` 或 `webui/` 目录下（入口 index.html）。
 * 宿主只负责"把本地页面显示出来"：不注入脚本、不修改插件目录里的任何文件，
 * 关闭时销毁承载页面，避免 WebView 与页面里的定时器常驻。
 *
 * 平台实现：Android 端用 WebView 承载（androidMain 的 actual）。
 * 本项目只构建 Android，其他目标平台不提供 actual —— 与
 * [com.example.adbtoolbox.common.GlassEffectPersistence] 的策略一致。
 *
 * @param url [com.example.adbtoolbox.common.PluginManager.getWebUIPath] 返回的入口文件路径，
 *            绝对路径或 `file://` 开头的 URL 都可以。
 * @param onClose 用户点击关闭按钮时回调；宿主自身不持有导航状态，由调用方决定关闭后的去向。
 */
@Composable
expect fun ModuleWebUIHost(
    url: String,
    onClose: () -> Unit
)
