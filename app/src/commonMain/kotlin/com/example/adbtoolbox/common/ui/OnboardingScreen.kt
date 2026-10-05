package com.example.adbtoolbox.common.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.adbtoolbox.common.ADBTools
import com.example.adbtoolbox.common.AppCache
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.theme.AppLayout
import com.example.adbtoolbox.common.theme.AppMotion
import com.example.adbtoolbox.common.theme.AppTheme
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 首次启动的设置向导（四步：欢迎 → 主题 → 权限 → 完成）。
 *
 * 关于"像 ColorOS 的初设动画"这件事，必须说清楚：ColorOS 是闭源系统，它的动画参数
 * （曲线、时长、插值）无法获取，**不可能也不宣称 1:1 复刻**。这里做的是按那类首次设置
 * 向导**公开可观察的风格**实现的本工程向导：每步整屏、居中大标题 + 说明、一步只做一件事、
 * 步骤之间淡入 + 轻微缩放 + 轻微位移、底部大而圆的按钮、顶部有进度指示、按下有干脆的回弹。
 * 所有时长/曲线/缩放比例都取自本工程的 [AppMotion]，间距与字号取自 [AppLayout]，
 * 颜色取自 [AppTheme]，没有一处是"照着系统抄的参数"。
 *
 * 权限这一步只做**真实读取 + 引导**：显示 Shizuku / 修改系统设置 / Root 的当前真实状态，
 * 点按钮只是把用户的选择交回调用方（触发已有的授权流程或跳到对应系统页面），
 * **不在向导里申请、授予或改写任何权限**，也明确告诉用户"没有权限也能用，只是部分功能受限"。
 * 状态读法：Shizuku 走全局三态 [AppCache.shizukuState]（MainContent 是唯一检测点），
 * 另两条每次进入本步时在后台线程现查（isRooted 内部会做文件探测并可能执行 which su，不能放主线程）。
 *
 * 文案全部走 [AppStrings] 的 ob_ 前缀 key（与 AppSettings 中已合并的 35 条 zh/en/hi 一致）。
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
    // 步骤号存进 saveable：转屏/进程被杀重进时不会把用户丢回第一步
    var step by rememberSaveable { mutableIntStateOf(StepWelcome) }
    val lastStep = StepDone

    // 权限真实状态：null = 还没查到（界面显示"未检测"）。
    // 不能用 false 兜底：那会让"还没查"先闪一下"未授予"，是假信息。
    var writeGranted by remember { mutableStateOf<Boolean?>(null) }
    var rooted by remember { mutableStateOf<Boolean?>(null) }

    // Shizuku 三态直接读全局 state：谁刷新谁改值，界面自动重组
    val shizukuState = AppCache.shizukuState.value

    // 进入权限步之后才现查；shizukuRefreshTick 变化时（用户在别处触发了重新检测）一起刷新，
    // 保证三行显示的是同一时刻的真实状态。本效果只读不写全局状态，不会和 MainContent 形成回环。
    LaunchedEffect(step, AppCache.shizukuRefreshTick.value) {
        if (step < StepPermissions) return@LaunchedEffect
        writeGranted = onboardingQueryOrNull { ADBTools.isWriteSettingsGranted() }
        rooted = onboardingQueryOrNull { ADBTools.isRooted() }
    }

    // 当前主题：初值取真实生效的 AppTheme.id；点击时本地先置位再回调，
    // 这样即使调用方不立刻应用主题，卡片高亮也是即时的。AppTheme.id 被别处改动时同步回来。
    var pickedThemeId by remember { mutableStateOf(AppTheme.id) }
    LaunchedEffect(AppTheme.id) { pickedThemeId = AppTheme.id }

    // 步骤间过渡的位移量：直接用列表项进场 token（8dp），不另写字面量
    val slidePx = with(LocalDensity.current) { AppMotion.listItemEnterOffsetY.roundToPx() }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = AppLayout.screenH)
    ) {
        // 顶部：进度点 + 跳过（最后一步的主按钮就是"开始使用"，不再重复放跳过）
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = AppLayout.screenTop),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(DotGap),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (index in 0 until OnboardingStepCount) {
                    StepDot(
                        isActive = index == step,
                        isPassed = index <= step,
                        activeColor = AppTheme.accent,
                        inactiveColor = contentColor
                    )
                }
            }
            if (step < lastStep) {
                LiquidButton(
                    onClick = onFinish,
                    backdrop = backdrop,
                    modifier = Modifier.height(RowButtonHeight)
                ) {
                    BasicText(
                        AppStrings.get("ob_skip"),
                        style = TextStyle(contentColor.copy(alpha = 0.75f), AppLayout.captionSize)
                    )
                }
            }
        }

        Spacer(Modifier.height(AppLayout.sectionGap))

        // 步骤内容：淡入 + 轻微缩放 + 轻微位移（曲线与幅度全部来自 AppMotion）
        AnimatedContent(
            targetState = step,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            transitionSpec = {
                // 往前进：新内容自下而上 8dp 落位；往回退：自上而下。位移刻意很小。
                val forward = targetState > initialState
                val enter = fadeIn(
                    animationSpec = AppMotion.screenEnterSpec,
                    initialAlpha = AppMotion.screenEnterAlphaFrom
                ) + scaleIn(
                    animationSpec = AppMotion.screenEnterSpec,
                    initialScale = AppMotion.screenEnterScaleFrom
                ) + slideInVertically(
                    animationSpec = AppMotion.moveIn,
                    initialOffsetY = { if (forward) slidePx else -slidePx }
                )
                // 退场只做淡出 + 轻微放大（比进场更快更干脆）：位移类 spec 在本版只接受
                // IntOffset 的 token，与其硬凑一条曲线，不如让"离开"这件事更安静。
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
                    // 进场内容画在退场内容之上：新步骤淡入时不被旧步骤的轻微放大盖住
                    targetContentZIndex = 1f,
                    // clip = false：各步内容高度不同（主题步最高）时不硬裁切
                    sizeTransform = SizeTransform(clip = false)
                )
            },
            label = "onboardingStep"
        ) { current ->
            when (current) {
                StepWelcome -> WelcomeStep(backdrop = backdrop, contentColor = contentColor)
                StepTheme -> ThemeStep(
                    backdrop = backdrop,
                    contentColor = contentColor,
                    selectedThemeId = pickedThemeId,
                    onPickTheme = { id ->
                        pickedThemeId = id
                        onPickTheme(id)
                    }
                )
                StepPermissions -> PermissionStep(
                    backdrop = backdrop,
                    contentColor = contentColor,
                    shizukuState = shizukuState,
                    writeGranted = writeGranted,
                    rooted = rooted,
                    onRequestShizuku = onRequestShizuku,
                    onOpenWriteSettings = onOpenWriteSettings
                )
                else -> DoneStep(backdrop = backdrop, contentColor = contentColor)
            }
        }

        // 底部：上一步 / 下一步（最后一步是"开始使用"）。高度用 LiquidButton 自带的 48dp 默认值。
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = AppLayout.sectionGap, bottom = AppLayout.screenTop),
            horizontalArrangement = Arrangement.spacedBy(AppLayout.innerGap),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (step > StepWelcome) {
                LiquidButton(
                    onClick = { step -= 1 },
                    backdrop = backdrop,
                    modifier = Modifier.weight(1f),
                    tint = SecondaryTint
                ) {
                    BasicText(
                        AppStrings.get("ob_back"),
                        style = TextStyle(Color.White, AppLayout.bodySize, FontWeight.Medium)
                    )
                }
            }
            LiquidButton(
                onClick = { if (step >= lastStep) onFinish() else step += 1 },
                backdrop = backdrop,
                modifier = Modifier.weight(1f),
                tint = AppTheme.accent
            ) {
                BasicText(
                    if (step >= lastStep) AppStrings.get("ob_start") else AppStrings.get("ob_next"),
                    style = TextStyle(AppTheme.onAccent, AppLayout.bodySize, FontWeight.Medium)
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 步骤 1：欢迎

@Composable
private fun WelcomeStep(backdrop: Backdrop, contentColor: Color) {
    StepScaffold {
        StepTitle(AppStrings.get("ob_welcome_title"), contentColor)
        Spacer(Modifier.height(AppLayout.innerGap))
        StepBody(AppStrings.get("ob_welcome_body"), contentColor)
        Spacer(Modifier.height(AppLayout.sectionGap))
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(AppLayout.cardPad)
            ) {
                OnboardingBullet(AppStrings.get("ob_welcome_b1"), checklist = false, contentColor = contentColor)
                Spacer(Modifier.height(AppLayout.innerGap))
                OnboardingBullet(AppStrings.get("ob_welcome_b2"), checklist = false, contentColor = contentColor)
                Spacer(Modifier.height(AppLayout.innerGap))
                OnboardingBullet(AppStrings.get("ob_welcome_b3"), checklist = false, contentColor = contentColor)
            }
        }
    }
}

// ------------------------------------------------------------------ 步骤 2：选主题

@Composable
private fun ThemeStep(
    backdrop: Backdrop,
    contentColor: Color,
    selectedThemeId: String,
    onPickTheme: (String) -> Unit
) {
    StepScaffold {
        StepTitle(AppStrings.get("ob_theme_title"), contentColor)
        Spacer(Modifier.height(AppLayout.innerGap))
        StepBody(AppStrings.get("ob_theme_body"), contentColor)
        Spacer(Modifier.height(AppLayout.sectionGap))

        // 三张主题卡：顺序即 AppTheme.all 的顺序（经典 → 国庆 → 华为）
        AppTheme.all.forEachIndexed { index, palette ->
            if (index > 0) Spacer(Modifier.height(AppLayout.innerGap))
            ThemeOptionCard(
                palette = palette,
                selected = palette.id == selectedThemeId,
                backdrop = backdrop,
                contentColor = contentColor,
                onClick = { onPickTheme(palette.id) }
            )
        }

        Spacer(Modifier.height(AppLayout.innerGap))
        BasicText(
            AppStrings.get("ob_theme_note"),
            Modifier.fillMaxWidth(),
            style = TextStyle(
                contentColor.copy(alpha = 0.5f),
                AppLayout.captionSize,
                textAlign = TextAlign.Center
            )
        )
    }
}

/** 单张主题卡：玻璃卡片 + 真实调色板色条 + 名称/说明 + 选中高亮 + 按压回弹。 */
@Composable
private fun ThemeOptionCard(
    palette: AppTheme.ThemePalette,
    selected: Boolean,
    backdrop: Backdrop,
    contentColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // 按压缩放：与全局按压反馈同一套 token（AppMotion.pressScale / pressSpring / releaseSpring）
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) AppMotion.pressScale else 1f,
        animationSpec = if (isPressed) AppMotion.pressSpring else AppMotion.releaseSpring,
        label = "onboardingThemeCardPress"
    )

    Box(
        Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
    ) {
        GlassCard(backdrop = backdrop, pageType = "home") {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(AppLayout.cardPadCompact),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 预览色条用设置页同一个组件，两处观感完全一致
                ThemePreviewSwatch(
                    colors = palette.preview,
                    modifier = Modifier
                        .size(SwatchWidth, SwatchHeight)
                        .clip(RoundedCornerShape(SwatchHeight / 2))
                )
                Spacer(Modifier.width(AppLayout.innerGap))
                Column(Modifier.weight(1f)) {
                    BasicText(
                        AppStrings.get(palette.nameKey),
                        style = TextStyle(
                            if (selected) palette.accent else contentColor,
                            AppLayout.bodySize,
                            FontWeight.Medium
                        )
                    )
                    Spacer(Modifier.height(TightGap))
                    BasicText(
                        AppStrings.get(palette.descKey),
                        style = TextStyle(contentColor.copy(alpha = 0.6f), AppLayout.captionSize)
                    )
                }
                if (selected) {
                    Spacer(Modifier.width(AppLayout.innerGap))
                    PerfBadge(AppStrings.get("ob_selected"), palette.accent)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 步骤 3：权限

@Composable
private fun PermissionStep(
    backdrop: Backdrop,
    contentColor: Color,
    shizukuState: String,
    writeGranted: Boolean?,
    rooted: Boolean?,
    onRequestShizuku: () -> Unit,
    onOpenWriteSettings: () -> Unit
) {
    // 三条通道各自算状态：ok / 未授予 / 未检测，颜色只用主题色与两个语义色
    val shizukuOk = shizukuState == "granted"
    val shizukuText = when (shizukuState) {
        "granted" -> AppStrings.get("ob_perm_ok")
        "no_permission" -> AppStrings.get("ob_perm_shizuku_no_perm")
        "not_running" -> AppStrings.get("ob_perm_shizuku_not_running")
        else -> AppStrings.get("ob_perm_unknown")
    }

    StepScaffold {
        StepTitle(AppStrings.get("ob_perm_title"), contentColor)
        Spacer(Modifier.height(AppLayout.innerGap))
        StepBody(AppStrings.get("ob_perm_body"), contentColor)
        Spacer(Modifier.height(AppLayout.sectionGap))

        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(AppLayout.cardPad)
            ) {
                // 通道 1：Shizuku —— 三态如实显示（granted / no_permission / not_running / 未检测）
                OnboardingPermissionRow(
                    title = AppStrings.get("ob_perm_shizuku"),
                    desc = AppStrings.get("ob_perm_shizuku_desc"),
                    ready = shizukuOk,
                    statusText = shizukuText,
                    statusColor = if (shizukuOk) ReadyColor else shizukuStatusColor(shizukuState),
                    actionText = AppStrings.get("ob_perm_shizuku_action"),
                    onAction = onRequestShizuku,
                    backdrop = backdrop,
                    contentColor = contentColor
                )
                Spacer(Modifier.height(AppLayout.innerGap))

                // 通道 2：修改系统设置（非华为机型写刷新率/游戏帧率用）
                OnboardingPermissionRow(
                    title = AppStrings.get("ob_perm_write"),
                    desc = AppStrings.get("ob_perm_write_desc"),
                    ready = writeGranted == true,
                    statusText = when (writeGranted) {
                        true -> AppStrings.get("ob_perm_ok")
                        false -> AppStrings.get("ob_perm_not_granted")
                        null -> AppStrings.get("ob_perm_unknown")
                    },
                    statusColor = when (writeGranted) {
                        true -> ReadyColor
                        false -> PendingColor
                        null -> UnknownColor
                    },
                    actionText = AppStrings.get("ob_perm_write_action"),
                    onAction = onOpenWriteSettings,
                    backdrop = backdrop,
                    contentColor = contentColor
                )
                Spacer(Modifier.height(AppLayout.innerGap))

                // 通道 3：Root —— 可选，不给按钮，只如实显示状态
                OnboardingPermissionRow(
                    title = AppStrings.get("ob_perm_root"),
                    desc = AppStrings.get("ob_perm_root_desc"),
                    ready = rooted == true,
                    statusText = when (rooted) {
                        true -> AppStrings.get("ob_perm_ok")
                        false -> AppStrings.get("ob_perm_root_optional")
                        null -> AppStrings.get("ob_perm_unknown")
                    },
                    statusColor = if (rooted == true) ReadyColor else UnknownColor,
                    actionText = null,
                    onAction = {},
                    backdrop = backdrop,
                    contentColor = contentColor
                )
            }
        }

        Spacer(Modifier.height(AppLayout.innerGap))
        // 明确写清楚：没有权限也能用，只是部分功能受限
        BasicText(
            AppStrings.get("ob_perm_note"),
            Modifier.fillMaxWidth(),
            style = TextStyle(
                AppTheme.accent,
                AppLayout.captionSize,
                FontWeight.Medium,
                textAlign = TextAlign.Center
            )
        )
    }
}

/** 单条权限通道：名称 + 说明 + 真实状态徽标 +（未就绪时）对应的操作按钮。 */
@Composable
private fun OnboardingPermissionRow(
    title: String,
    desc: String,
    ready: Boolean,
    statusText: String,
    statusColor: Color,
    actionText: String?,
    onAction: () -> Unit,
    backdrop: Backdrop,
    contentColor: Color
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(title, style = TextStyle(contentColor, AppLayout.bodySize, FontWeight.Medium))
                Spacer(Modifier.height(TightGap))
                BasicText(desc, style = TextStyle(contentColor.copy(alpha = 0.55f), AppLayout.captionSize))
            }
            Spacer(Modifier.width(AppLayout.innerGap))
            PerfBadge(statusText, statusColor)
        }
        // 已就绪就不再摆一个没有意义的按钮；Root 一类的"可选"通道 actionText 传 null
        if (!ready && actionText != null) {
            Spacer(Modifier.height(TightGap))
            LiquidButton(
                onClick = onAction,
                backdrop = backdrop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(RowButtonHeight),
                tint = AppTheme.accent
            ) {
                BasicText(
                    actionText,
                    style = TextStyle(AppTheme.onAccent, AppLayout.captionSize, FontWeight.Medium)
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 步骤 4：完成

@Composable
private fun DoneStep(backdrop: Backdrop, contentColor: Color) {
    StepScaffold {
        StepTitle(AppStrings.get("ob_done_title"), contentColor)
        Spacer(Modifier.height(AppLayout.innerGap))
        StepBody(AppStrings.get("ob_done_body"), contentColor)
        Spacer(Modifier.height(AppLayout.sectionGap))
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(AppLayout.cardPad)
            ) {
                OnboardingBullet(AppStrings.get("ob_done_b1"), checklist = true, contentColor = contentColor)
                Spacer(Modifier.height(AppLayout.innerGap))
                OnboardingBullet(AppStrings.get("ob_done_b2"), checklist = true, contentColor = contentColor)
                Spacer(Modifier.height(AppLayout.innerGap))
                OnboardingBullet(AppStrings.get("ob_done_b3"), checklist = true, contentColor = contentColor)
            }
        }
    }
}

// ------------------------------------------------------------------ 公共小件

/**
 * 步骤容器：内容短时整屏居中，内容长时可滚动。
 *
 * 为什么不用"fillMaxSize + verticalScroll + Arrangement.Center"：滚动容器给子项的
 * maxHeight 是无限，Column 的高度会等于内容高度，居中排列等于没生效；外面套一层
 * 居中的 Box 才是"能居中、也能滚"的正确写法（英文/印地语文案明显更长，必须能滚）。
 */
@Composable
private fun StepScaffold(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/** 步骤大标题：整屏居中，用工程里最大的标题 token。 */
@Composable
private fun StepTitle(text: String, contentColor: Color) {
    BasicText(
        text,
        Modifier.fillMaxWidth(),
        style = TextStyle(
            contentColor,
            AppLayout.titleSize,
            FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    )
}

/** 步骤说明：居中，次要亮度。 */
@Composable
private fun StepBody(text: String, contentColor: Color) {
    BasicText(
        text,
        Modifier.fillMaxWidth(),
        style = TextStyle(
            contentColor.copy(alpha = 0.75f),
            AppLayout.bodySize,
            textAlign = TextAlign.Center
        )
    )
}

/** 要点行：欢迎/完成两步共用（· 为说明，✓ 为已具备）。 */
@Composable
private fun OnboardingBullet(text: String, checklist: Boolean, contentColor: Color) {
    Row(verticalAlignment = Alignment.Top) {
        BasicText(
            if (checklist) "✓" else "·",
            style = TextStyle(
                if (checklist) ReadyColor else contentColor.copy(alpha = 0.6f),
                AppLayout.bodySize,
                FontWeight.Bold
            )
        )
        Spacer(Modifier.width(AppLayout.innerGap))
        BasicText(text, style = TextStyle(contentColor.copy(alpha = 0.85f), AppLayout.bodySize))
    }
}

/** 进度圆点：当前点最大最亮（主题主色），已走过的点半亮，未到的点最淡。 */
@Composable
private fun StepDot(isActive: Boolean, isPassed: Boolean, activeColor: Color, inactiveColor: Color) {
    val progress by animateFloatAsState(
        targetValue = if (isActive) 1f else if (isPassed) 0.5f else 0f,
        animationSpec = if (isActive) AppMotion.fadeIn else AppMotion.fadeOut,
        label = "onboardingStepDot"
    )
    val dotSize = DotInactiveSize + (DotActiveSize - DotInactiveSize) * progress
    val dotColor = lerp(inactiveColor.copy(alpha = 0.25f), activeColor, progress)
    Box(
        Modifier
            .size(dotSize)
            .clip(CircleShape)
            .background(dotColor)
    )
}

// ------------------------------------------------------------------ 文件内私有常量

/** 向导总步数：欢迎 / 主题 / 权限 / 完成。 */
private const val OnboardingStepCount = 4

private const val StepWelcome = 0
private const val StepTheme = 1
private const val StepPermissions = 2
private const val StepDone = 3

// 组件层没有对应 token 的几个尺寸，集中在此并注明来源，避免散落的字面量：
// - 行内按钮高度（顶部"跳过"、权限行里的操作按钮）沿用导航/权限页既有小按钮的 38dp；
//   底部主按钮不写高度，直接用 LiquidButton 自带的 48dp 默认值。
// - 主题预览色条与设置页主题行一致（宽 46dp、高 26dp，圆角取高度的一半）。
// - 进度圆点：当前点 9dp，其余 7dp，点间距 6dp。
// - 卡片里标题与说明之间的 8dp 紧凑间距。
private val RowButtonHeight = 38.dp
private val SwatchWidth = 46.dp
private val SwatchHeight = 26.dp
private val DotActiveSize = 9.dp
private val DotInactiveSize = 7.dp
private val DotGap = 6.dp
private val TightGap = 8.dp

/** 已就绪：与权限页"已授权"的绿色同一取值。 */
private val ReadyColor = Color(0xFF34C759)

/** 未授予：提示色，不用红色——权限缺失不是错误。 */
private val PendingColor = Color(0xFFFF9500)

/** 未检测：中性灰，明确区别于"未授予"。 */
private val UnknownColor = Color(0xFF8E8E93)

/** 次级按钮底色：与权限页/应用详情页的次级按钮同一取值。 */
private val SecondaryTint = Color(0xFF8E8E93)

/** Shizuku 未检测时用中性灰，其余沿用提示色。 */
private fun shizukuStatusColor(state: String): Color =
    if (state == "granted") ReadyColor else if (state == "unknown") UnknownColor else PendingColor

/**
 * 后台线程执行 + 异常转成 null（CancellationException 正常抛出，不吞掉协程取消）。
 * 本文件私有，避免与其它页面重名。
 */
private suspend fun <T> onboardingQueryOrNull(block: () -> T): T? = try {
    withContext(Dispatchers.Default) { block() }
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
