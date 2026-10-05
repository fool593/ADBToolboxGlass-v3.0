package com.example.adbtoolbox.common.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.theme.AppLayout
import com.example.adbtoolbox.common.theme.AppMotion
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 首次启动的设置向导（四步：欢迎 → 主题 → 权限 → 完成）。
 *
 * 关于"像 ColorOS 的初设动画"这件事，必须说清楚：ColorOS 是闭源系统，
 * 它的动画参数（曲线、时长、插值）无法获取，**不可能也不宣称 1:1 复刻**。
 * 这里做的是按那类首次设置向导**公开可观察的风格**实现的本工程向导：
 * 每步整屏、居中大标题 + 说明、步骤间淡入 + 轻微缩放过渡、底部大而圆的按钮、
 * 顶部有进度指示。所有时长/曲线都用本工程的 [AppMotion] token，没有魔法值。
 *
 * 权限这一步只做**真实读取 + 引导**：显示 Shizuku / 修改系统设置 / Root 的当前真实状态，
 * 点按钮只是跳到对应的系统页面或触发已有的授权流程，**不替用户做任何决定**，
 * 也明确告诉用户"没有权限也能用，只是部分功能受限"。
 */
@Composable
fun OnboardingScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onPickTheme: (String) -> Unit,
    onRequestShizuku: () -> Unit,
    onOpenWriteSettings: () -> Unit,
    onFinish: () -> Unit
) {
    var step by remember { mutableIntStateOf(0) }
    val lastStep = 3

    // 权限状态：只读全局，真实反映当前情况（离开再回来也会刷新）
    var writeGranted by remember { mutableStateOf(false) }
    var rooted by remember { mutableStateOf(false) }
    LaunchedEffect(step) {
        if (step == 2) {
            writeGranted = withContext(Dispatchers.Default) { ADBTools.isWriteSettingsGranted() }
            rooted = withContext(Dispatchers.Default) { ADBTools.isRooted() }
        }
    }
    val shizukuState = AppCache.shizukuState.value
    val selectedTheme = AppSettingsThemeId()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = AppLayout.screenH)
    ) {
        Spacer(Modifier.height(AppLayout.screenTop))

        // 顶部：进度点 + 跳过
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(lastStep + 1) { i ->
                    Box(
                        Modifier
                            .size(if (i == step) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                if (i <= step) AppTheme.accent
                                else contentColor.copy(alpha = 0.25f)
                            )
                    )
                }
            }
            if (step < lastStep) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onFinish() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    BasicText(
                        AppStrings.get("ob_skip"),
                        style = TextStyle(contentColor.copy(alpha = 0.7f), 12.sp)
                    )
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 步骤内容：淡入 + 轻微缩放（与页面切换同一套 token，观感一致）
        AnimatedContent(
            targetState = step,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            transitionSpec = {
                val enter = fadeIn(
                    animationSpec = AppMotion.screenEnterSpec,
                    initialAlpha = AppMotion.screenEnterAlphaFrom
                ) + scaleIn(
                    animationSpec = AppMotion.screenEnterSpec,
                    initialScale = AppMotion.screenEnterScaleFrom
                )
                val exit = fadeOut(
                    animationSpec = AppMotion.screenExitSpec,
                    targetAlpha = AppMotion.screenExitAlphaTo
                ) + scaleOut(
                    animationSpec = AppMotion.screenExitSpec,
                    targetScale = AppMotion.screenExitScaleTo
                )
                ContentTransform(
                    targetContentEnter = enter,
                    initialContentExit = exit,
                    targetContentZIndex = 1f,
                    sizeTransform = SizeTransform(clip = false)
                )
            },
            label = "onboardingStep"
        ) { current ->
            when (current) {
                // ---------------- 1. 欢迎 ----------------
                0 -> Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(24.dp))
                    BasicText(
                        AppStrings.get("ob_welcome_title"),
                        style = TextStyle(contentColor, 30.sp, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(12.dp))
                    BasicText(
                        AppStrings.get("ob_welcome_body"),
                        style = TextStyle(contentColor.copy(alpha = 0.75f), AppLayout.bodySize)
                    )
                    Spacer(Modifier.height(AppLayout.sectionGap))
                    GlassCard(backdrop = backdrop, pageType = "home") {
                        Column(Modifier.padding(AppLayout.cardPad)) {
                            OnboardingBullet(AppStrings.get("ob_welcome_b1"), checklist = false, contentColor = contentColor)
                            Spacer(Modifier.height(8.dp))
                            OnboardingBullet(AppStrings.get("ob_welcome_b2"), checklist = false, contentColor = contentColor)
                            Spacer(Modifier.height(8.dp))
                            OnboardingBullet(AppStrings.get("ob_welcome_b3"), checklist = false, contentColor = contentColor)
                        }
                    }
                }

                // ---------------- 2. 选主题 ----------------
                1 -> Column(Modifier.fillMaxSize()) {
                    BasicText(
                        AppStrings.get("ob_theme_title"),
                        style = TextStyle(contentColor, 26.sp, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        AppStrings.get("ob_theme_body"),
                        style = TextStyle(contentColor.copy(alpha = 0.7f), AppLayout.captionSize)
                    )
                    Spacer(Modifier.height(AppLayout.innerGap))
                    AppTheme.all.forEach { palette ->
                        val isSelected = palette.id == selectedTheme
                        GlassCard(
                            backdrop = backdrop,
                            pageType = "home",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clickable { onPickTheme(palette.id) }
                        ) {
                            Row(
                                Modifier.padding(AppLayout.cardPadCompact),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // 预览色条：直接取该主题真实调色板里的颜色
                                Row(
                                    Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .size(width = 46.dp, height = 26.dp)
                                ) {
                                    palette.preview.take(4).forEach { c ->
                                        Box(Modifier.weight(1f).fillMaxSize().background(c))
                                    }
                                }
                                Spacer(Modifier.width(AppLayout.innerGap))
                                Column(Modifier.weight(1f)) {
                                    BasicText(
                                        AppStrings.get(palette.nameKey),
                                        style = TextStyle(contentColor, AppLayout.bodySize, FontWeight.Medium)
                                    )
                                    BasicText(
                                        AppStrings.get(palette.descKey),
                                        style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                                    )
                                }
                                if (isSelected) {
                                    PerfBadge(AppStrings.get("ob_selected"), AppTheme.accent)
                                }
                            }
                        }
                    }
                    BasicText(
                        AppStrings.get("ob_theme_note"),
                        style = TextStyle(contentColor.copy(alpha = 0.55f), AppLayout.captionSize)
                    )
                }

                // ---------------- 3. 权限 ----------------
                2 -> Column(Modifier.fillMaxSize()) {
                    BasicText(
                        AppStrings.get("ob_perm_title"),
                        style = TextStyle(contentColor, 26.sp, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))
                    BasicText(
                        AppStrings.get("ob_perm_body"),
                        style = TextStyle(contentColor.copy(alpha = 0.7f), AppLayout.captionSize)
                    )
                    Spacer(Modifier.height(AppLayout.innerGap))
                    GlassCard(backdrop = backdrop, pageType = "home") {
                        Column(Modifier.padding(AppLayout.cardPad)) {
                            // Shizuku：三态如实显示
                            OnboardingPermissionRow(
                                title = AppStrings.get("ob_perm_shizuku"),
                                desc = AppStrings.get("ob_perm_shizuku_desc"),
                                ok = shizukuState == "granted",
                                okText = AppStrings.get("ob_perm_ok"),
                                noText = when (shizukuState) {
                                    "no_permission" -> AppStrings.get("ob_perm_shizuku_no_perm")
                                    "not_running" -> AppStrings.get("ob_perm_shizuku_not_running")
                                    else -> AppStrings.get("ob_perm_unknown")
                                },
                                contentColor = contentColor,
                                actionText = AppStrings.get("ob_perm_shizuku_action"),
                                onAction = onRequestShizuku,
                                backdrop = backdrop
                            )
                            Spacer(Modifier.height(14.dp))
                            // 修改系统设置：非华为机型写刷新率要用
                            OnboardingPermissionRow(
                                title = AppStrings.get("ob_perm_write"),
                                desc = AppStrings.get("ob_perm_write_desc"),
                                ok = writeGranted,
                                okText = AppStrings.get("ob_perm_ok"),
                                noText = AppStrings.get("ob_perm_not_granted"),
                                contentColor = contentColor,
                                actionText = AppStrings.get("ob_perm_write_action"),
                                onAction = onOpenWriteSettings,
                                backdrop = backdrop
                            )
                            Spacer(Modifier.height(14.dp))
                            // Root：可选
                            OnboardingPermissionRow(
                                title = AppStrings.get("ob_perm_root"),
                                desc = AppStrings.get("ob_perm_root_desc"),
                                ok = rooted,
                                okText = AppStrings.get("ob_perm_ok"),
                                noText = AppStrings.get("ob_perm_root_optional"),
                                contentColor = contentColor,
                                actionText = null,
                                onAction = {},
                                backdrop = backdrop
                            )
                        }
                    }
                    Spacer(Modifier.height(AppLayout.innerGap))
                    BasicText(
                        AppStrings.get("ob_perm_note"),
                        style = TextStyle(Color(0xFF34C759), AppLayout.captionSize)
                    )
                }

                // ---------------- 4. 完成 ----------------
                else -> Column(Modifier.fillMaxSize()) {
                    Spacer(Modifier.height(24.dp))
                    BasicText(
                        AppStrings.get("ob_done_title"),
                        style = TextStyle(contentColor, 30.sp, FontWeight.Bold)
                    )
                    Spacer(Modifier.height(12.dp))
                    BasicText(
                        AppStrings.get("ob_done_body"),
                        style = TextStyle(contentColor.copy(alpha = 0.75f), AppLayout.bodySize)
                    )
                    Spacer(Modifier.height(AppLayout.sectionGap))
                    GlassCard(backdrop = backdrop, pageType = "home") {
                        Column(Modifier.padding(AppLayout.cardPad)) {
                            OnboardingBullet(AppStrings.get("ob_done_b1"), checklist = true, contentColor = contentColor)
                            Spacer(Modifier.height(8.dp))
                            OnboardingBullet(AppStrings.get("ob_done_b2"), checklist = true, contentColor = contentColor)
                            Spacer(Modifier.height(8.dp))
                            OnboardingBullet(AppStrings.get("ob_done_b3"), checklist = true, contentColor = contentColor)
                        }
                    }
                }
            }
        }

        // 底部按钮：下一步 / 上一步 / 开始使用
        Row(
            Modifier.fillMaxWidth().padding(bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(AppLayout.innerGap)
        ) {
            if (step > 0) {
                LiquidButton(
                    onClick = { step -= 1 },
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f).height(50.dp),
                    tint = Color(0xFF8E8E93)
                ) {
                    BasicText(AppStrings.get("ob_back"), style = TextStyle(Color.White, 15.sp))
                }
            }
            LiquidButton(
                onClick = { if (step >= lastStep) onFinish() else step += 1 },
                backdrop = backdrop,
                modifier = Modifier.weight(if (step > 0) 1.6f else 1f).height(50.dp),
                tint = AppTheme.accent
            ) {
                BasicText(
                    if (step >= lastStep) AppStrings.get("ob_start") else AppStrings.get("ob_next"),
                    style = TextStyle(AppTheme.onAccent, 15.sp, FontWeight.Medium)
                )
            }
        }
    }
}

/** 读取当前主题 id（放在这里是为了让向导文件自包含，避免再引入 AppSettings 全局引用）。 */
@Composable
private fun AppSettingsThemeId(): String = com.example.adbtoolbox.common.AppSettings.themeId

@Composable
private fun OnboardingBullet(text: String, checklist: Boolean, contentColor: Color) {
    Row(verticalAlignment = Alignment.Top) {
        BasicText(
            if (checklist) "✓" else "·",
            style = TextStyle(
                if (checklist) Color(0xFF34C759) else contentColor.copy(alpha = 0.6f),
                14.sp,
                FontWeight.Bold
            )
        )
        Spacer(Modifier.width(8.dp))
        BasicText(text, style = TextStyle(contentColor.copy(alpha = 0.85f), AppLayout.bodySize))
    }
}

@Composable
private fun OnboardingPermissionRow(
    title: String,
    desc: String,
    ok: Boolean,
    okText: String,
    noText: String,
    contentColor: Color,
    actionText: String?,
    onAction: () -> Unit,
    backdrop: Backdrop
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                BasicText(title, style = TextStyle(contentColor, AppLayout.bodySize, FontWeight.Medium))
                BasicText(desc, style = TextStyle(contentColor.copy(alpha = 0.55f), AppLayout.captionSize))
            }
            PerfBadge(
                if (ok) okText else noText,
                if (ok) Color(0xFF34C759) else Color(0xFFFF9500)
            )
        }
        if (!ok && actionText != null) {
            Spacer(Modifier.height(8.dp))
            LiquidButton(
                onClick = onAction,
                backdrop = backdrop,
                modifier = Modifier.height(38.dp).fillMaxWidth(),
                tint = AppTheme.accent
            ) {
                BasicText(actionText, style = TextStyle(AppTheme.onAccent, 12.sp))
            }
        }
    }
}
