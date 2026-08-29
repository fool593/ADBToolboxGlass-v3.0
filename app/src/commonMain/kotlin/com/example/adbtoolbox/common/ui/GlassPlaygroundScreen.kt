package com.example.adbtoolbox.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.GlassEffectConfig
import com.example.adbtoolbox.common.GlassEffectPersistence
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidSlider
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.shapes.RoundedRectangle

@Composable
fun GlassPlaygroundScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    // 基础效果参数 - 直接绑定全局配置，实时生效
    val cornerRadius = GlassEffectConfig.cornerRadius
    val blurRadius = GlassEffectConfig.blurRadius
    val refractionHeight = GlassEffectConfig.refractionHeight
    val refractionAmount = GlassEffectConfig.refractionAmount
    val chromaticAberration = GlassEffectConfig.chromaticAberration
    val enableVibrancy = GlassEffectConfig.enableVibrancy
    var showSaved by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16f.dp)
    ) {
        Spacer(Modifier.height(24f.dp))

        // 标题
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassBackButton(backdrop = backdrop, contentColor = contentColor, onBack = onBack)
            Spacer(Modifier.height(12f.dp))
            BasicText(
                AppStrings.get("glass_playground"),
                style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold)
            )
        }
        Spacer(Modifier.height(8f.dp))
        BasicText(
            AppStrings.get("glass_playground_hint"),
            style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
        )
        Spacer(Modifier.height(20f.dp))

        // 实时预览区域
        Box(
            Modifier
                .fillMaxWidth()
                .height(200f.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(160f.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(160f.dp / 2f * cornerRadius.value) },
                        effects = {
                            val minDimension = size.minDimension
                            if (enableVibrancy.value) vibrancy()
                            blur(blurRadius.value.dp.toPx())
                            lens(
                                refractionHeight = refractionHeight.value * minDimension * 0.5f,
                                refractionAmount = refractionAmount.value * minDimension,
                                depthEffect = true,
                                chromaticAberration = chromaticAberration.value > 0f
                            )
                        },
                        highlight = { Highlight.Plain }
                    )
            )
        }

        Spacer(Modifier.height(24f.dp))

        // 调节滑块区域
        GlassCard(backdrop = backdrop) {
            Column(Modifier.padding(20f.dp), verticalArrangement = Arrangement.spacedBy(20f.dp)) {
                // 圆角
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(
                            AppStrings.get("corner_radius"),
                            style = TextStyle(contentColor, 14f.sp, androidx.compose.ui.text.font.FontWeight.Medium),
                            modifier = Modifier.weight(1f)
                        )
                        BasicText(
                            "${(cornerRadius.value * 100).toInt()}%",
                            style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
                        )
                    }
                    LiquidSlider(
                        value = { cornerRadius.value },
                        onValueChange = { cornerRadius.value = it },
                        valueRange = 0f..1f,
                        visibilityThreshold = 0.001f,
                        backdrop = backdrop
                    )
                }

                // 模糊半径
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(
                            AppStrings.get("blur_radius"),
                            style = TextStyle(contentColor, 14f.sp, androidx.compose.ui.text.font.FontWeight.Medium),
                            modifier = Modifier.weight(1f)
                        )
                        BasicText(
                            "${blurRadius.value.toInt()}dp",
                            style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
                        )
                    }
                    LiquidSlider(
                        value = { blurRadius.value },
                        onValueChange = { blurRadius.value = it },
                        valueRange = 0f..32f,
                        visibilityThreshold = 0.01f,
                        backdrop = backdrop
                    )
                }

                // 折射高度
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(
                            AppStrings.get("refraction_height"),
                            style = TextStyle(contentColor, 14f.sp, androidx.compose.ui.text.font.FontWeight.Medium),
                            modifier = Modifier.weight(1f)
                        )
                        BasicText(
                            "${(refractionHeight.value * 100).toInt()}%",
                            style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
                        )
                    }
                    LiquidSlider(
                        value = { refractionHeight.value },
                        onValueChange = { refractionHeight.value = it },
                        valueRange = 0f..1f,
                        visibilityThreshold = 0.001f,
                        backdrop = backdrop
                    )
                }

                // 折射量
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(
                            AppStrings.get("refraction_amount"),
                            style = TextStyle(contentColor, 14f.sp, androidx.compose.ui.text.font.FontWeight.Medium),
                            modifier = Modifier.weight(1f)
                        )
                        BasicText(
                            "${(refractionAmount.value * 100).toInt()}%",
                            style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
                        )
                    }
                    LiquidSlider(
                        value = { refractionAmount.value },
                        onValueChange = { refractionAmount.value = it },
                        valueRange = 0f..1f,
                        visibilityThreshold = 0.001f,
                        backdrop = backdrop
                    )
                }

                // 色差
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(
                            AppStrings.get("chromatic_aberration"),
                            style = TextStyle(contentColor, 14f.sp, androidx.compose.ui.text.font.FontWeight.Medium),
                            modifier = Modifier.weight(1f)
                        )
                        BasicText(
                            if (chromaticAberration.value > 0f) AppStrings.get("enabled") else AppStrings.get("disabled"),
                            style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)
                        )
                    }
                    LiquidSlider(
                        value = { chromaticAberration.value },
                        onValueChange = { chromaticAberration.value = it },
                        valueRange = 0f..1f,
                        visibilityThreshold = 0.001f,
                        backdrop = backdrop
                    )
                }

                // 光域开关
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(
                            AppStrings.get("vibrancy"),
                            style = TextStyle(contentColor, 14f.sp, androidx.compose.ui.text.font.FontWeight.Medium),
                            modifier = Modifier.weight(1f)
                        )
                        BasicText(
                            if (enableVibrancy.value) AppStrings.get("enabled") else AppStrings.get("disabled"),
                            style = TextStyle(
                                if (enableVibrancy.value) Color(0xFF34C759) else Color(0xFF8E8E93),
                                12f.sp
                            )
                        )
                    }
                    LiquidSlider(
                        value = { if (enableVibrancy.value) 1f else 0f },
                        onValueChange = { enableVibrancy.value = it > 0.5f },
                        valueRange = 0f..1f,
                        visibilityThreshold = 0.01f,
                        backdrop = backdrop
                    )
                }

                // 玻璃颜色选择
                Spacer(Modifier.height(8f.dp))
                BasicText(
                    AppStrings.get("glass_color"),
                    style = TextStyle(contentColor, 14f.sp, androidx.compose.ui.text.font.FontWeight.Medium)
                )
                Spacer(Modifier.height(8f.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8f.dp)
                ) {
                    GlassEffectConfig.presetColors.forEach { (name, color) ->
                        val isSelected = GlassEffectConfig.glassColor.value == color
                        Box(
                            modifier = Modifier
                                .size(36f.dp)
                                .clip(RoundedRectangle(18f.dp))
                                .clickable {
                                    GlassEffectConfig.setColor(color)
                                }
                                .drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { RoundedRectangle(18f.dp) },
                                    effects = {
                                        vibrancy()
                                        blur(4f.dp.toPx())
                                        lens(2f.dp.toPx(), 4f.dp.toPx())
                                    },
                                    highlight = { Highlight.Plain }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24f.dp)
                                    .clip(RoundedRectangle(12f.dp))
                                    .then(
                                        if (color == Color.Transparent) {
                                            Modifier
                                        } else {
                                            Modifier.drawBackdrop(
                                                backdrop = backdrop,
                                                shape = { RoundedRectangle(12f.dp) },
                                                effects = {
                                                    vibrancy()
                                                    blur(2f.dp.toPx())
                                                },
                                                highlight = { Highlight.Plain },
                                                onDrawSurface = {
                                                    drawRect(color)
                                                }
                                            )
                                        }
                                    )
                            )
                            if (isSelected) {
                                BasicText(
                                    "✓",
                                    style = TextStyle(Color.White, 14f.sp, androidx.compose.ui.text.font.FontWeight.Bold)
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 字体颜色选择（全局文字颜色，赤橙黄绿青蓝紫 + 默认/白/黑）
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp), verticalArrangement = Arrangement.spacedBy(12f.dp)) {
                BasicText(AppStrings.get("font_color"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8f.dp)
                ) {
                    GlassEffectConfig.presetFontColors.forEach { (key, color) ->
                        val isSelected = GlassEffectConfig.fontColor.value == color
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36f.dp)
                                    .clip(RoundedRectangle(18f.dp))
                                    .background(
                                        if (color == Color.Unspecified) contentColor.copy(alpha = 0.25f) else color,
                                        RoundedRectangle(18f.dp)
                                    )
                                    .clickable { GlassEffectConfig.fontColor.value = color },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    BasicText(
                                        "✓",
                                        style = TextStyle(Color.White, 14f.sp, androidx.compose.ui.text.font.FontWeight.Bold)
                                    )
                                }
                            }
                            Spacer(Modifier.height(4f.dp))
                            BasicText(
                                AppStrings.get(key),
                                style = TextStyle(contentColor.copy(alpha = 0.6f), 10f.sp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 导航栏胶囊调节
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp), verticalArrangement = Arrangement.spacedBy(12f.dp)) {
                BasicText(AppStrings.get("nav_capsule_settings"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))

                // 胶囊形状
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(AppStrings.get("capsule_shape"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                        BasicText(when(GlassEffectConfig.navIndicatorShape.value) {
                            "round" -> AppStrings.get("round")
                            "square" -> AppStrings.get("square")
                            else -> AppStrings.get("capsule")
                        }, style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
                        listOf("capsule" to AppStrings.get("capsule"), "round" to AppStrings.get("round"), "square" to AppStrings.get("square")).forEach { (shape, name) ->
                            val selected = GlassEffectConfig.navIndicatorShape.value == shape
                            LiquidButton(
                                onClick = { GlassEffectConfig.navIndicatorShape.value = shape },
                                backdrop = backdrop,
                                modifier = Modifier.height(32f.dp),
                                tint = if (selected) Color(0xFF007AFF) else Color.Unspecified
                            ) {
                                BasicText(name, Modifier.padding(horizontal = 10f.dp), style = TextStyle(if (selected) Color.White else contentColor, 11f.sp))
                            }
                        }
                    }
                }

                // 胶囊高度
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(AppStrings.get("capsule_height"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                        BasicText("${GlassEffectConfig.navIndicatorHeight.value.toInt()}dp", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                    }
                    LiquidSlider(value = { GlassEffectConfig.navIndicatorHeight.value / 80f }, onValueChange = { GlassEffectConfig.navIndicatorHeight.value = it * 80f }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }

                // 胶囊宽度（0=自适应）
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(AppStrings.get("capsule_width"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                        BasicText(if (GlassEffectConfig.navIndicatorWidth.value == 0f) AppStrings.get("auto") else "${GlassEffectConfig.navIndicatorWidth.value.toInt()}dp", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                    }
                    LiquidSlider(value = { GlassEffectConfig.navIndicatorWidth.value / 120f }, onValueChange = { GlassEffectConfig.navIndicatorWidth.value = if (it < 0.05f) 0f else it * 120f }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }

                // 胶囊圆角
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(AppStrings.get("capsule_corner"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                        BasicText("${(GlassEffectConfig.navIndicatorCorner.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                    }
                    LiquidSlider(value = { GlassEffectConfig.navIndicatorCorner.value }, onValueChange = { GlassEffectConfig.navIndicatorCorner.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }

                // 胶囊模糊
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(AppStrings.get("capsule_blur"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                        BasicText("${GlassEffectConfig.navIndicatorBlur.value.toInt()}dp", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                    }
                    LiquidSlider(value = { GlassEffectConfig.navIndicatorBlur.value / 32f }, onValueChange = { GlassEffectConfig.navIndicatorBlur.value = it * 32f }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }

                // 胶囊透明度
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(AppStrings.get("capsule_opacity"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                        BasicText("${(GlassEffectConfig.navIndicatorOpacity.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                    }
                    LiquidSlider(value = { GlassEffectConfig.navIndicatorOpacity.value }, onValueChange = { GlassEffectConfig.navIndicatorOpacity.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 导航栏液态玻璃调节
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp), verticalArrangement = Arrangement.spacedBy(12f.dp)) {
                BasicText(AppStrings.get("nav_glass"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))

                // 导航栏模糊
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(AppStrings.get("blur_radius"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                        BasicText("${GlassEffectConfig.navBlurRadius.value.toInt()}dp", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                    }
                    LiquidSlider(value = { GlassEffectConfig.navBlurRadius.value / 32f }, onValueChange = { GlassEffectConfig.navBlurRadius.value = it * 32f }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }

                // 导航栏透明度
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(AppStrings.get("opacity"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                        BasicText("${(GlassEffectConfig.navOpacity.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                    }
                    LiquidSlider(value = { GlassEffectConfig.navOpacity.value }, onValueChange = { GlassEffectConfig.navOpacity.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }

                // 导航栏折射高度
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(AppStrings.get("refraction_height"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                        BasicText("${(GlassEffectConfig.navRefractionHeight.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                    }
                    LiquidSlider(value = { GlassEffectConfig.navRefractionHeight.value }, onValueChange = { GlassEffectConfig.navRefractionHeight.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }

                // 导航栏折射量
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(AppStrings.get("refraction_amount"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                        BasicText("${(GlassEffectConfig.navRefractionAmount.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                    }
                    LiquidSlider(value = { GlassEffectConfig.navRefractionAmount.value }, onValueChange = { GlassEffectConfig.navRefractionAmount.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }

                // 导航栏圆角
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(AppStrings.get("corner_radius"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                        BasicText("${(GlassEffectConfig.navCornerRadius.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                    }
                    LiquidSlider(value = { GlassEffectConfig.navCornerRadius.value }, onValueChange = { GlassEffectConfig.navCornerRadius.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }

                // 导航栏色差
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(AppStrings.get("chromatic_aberration"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                        BasicText("${(GlassEffectConfig.navChromaticAberration.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                    }
                    LiquidSlider(value = { GlassEffectConfig.navChromaticAberration.value }, onValueChange = { GlassEffectConfig.navChromaticAberration.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }

                // 导航栏光域
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row {
                        BasicText(AppStrings.get("vibrancy"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f))
                        BasicText(if (GlassEffectConfig.navEnableVibrancy.value) AppStrings.get("enabled") else AppStrings.get("disabled"), style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp))
                    }
                    LiquidSlider(value = { if (GlassEffectConfig.navEnableVibrancy.value) 1f else 0f }, onValueChange = { GlassEffectConfig.navEnableVibrancy.value = it > 0.5f }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 主页液态玻璃调节
        GlassCard(backdrop = backdrop, pageType = "home") {
            Column(Modifier.padding(20f.dp), verticalArrangement = Arrangement.spacedBy(12f.dp)) {
                BasicText(AppStrings.get("home_glass"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                // 模糊
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("blur_radius"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${GlassEffectConfig.homeBlurRadius.value.toInt()}dp", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.homeBlurRadius.value / 32f }, onValueChange = { GlassEffectConfig.homeBlurRadius.value = it * 32f }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                // 透明度
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("opacity"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.homeOpacity.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.homeOpacity.value }, onValueChange = { GlassEffectConfig.homeOpacity.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                // 圆角
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("corner_radius"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.homeCornerRadius.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.homeCornerRadius.value }, onValueChange = { GlassEffectConfig.homeCornerRadius.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                // 折射高度
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("refraction_height"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.homeRefractionHeight.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.homeRefractionHeight.value }, onValueChange = { GlassEffectConfig.homeRefractionHeight.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                // 折射量
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("refraction_amount"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.homeRefractionAmount.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.homeRefractionAmount.value }, onValueChange = { GlassEffectConfig.homeRefractionAmount.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 终端液态玻璃调节
        GlassCard(backdrop = backdrop, pageType = "terminal") {
            Column(Modifier.padding(20f.dp), verticalArrangement = Arrangement.spacedBy(12f.dp)) {
                BasicText(AppStrings.get("terminal_glass"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("blur_radius"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${GlassEffectConfig.terminalBlurRadius.value.toInt()}dp", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.terminalBlurRadius.value / 32f }, onValueChange = { GlassEffectConfig.terminalBlurRadius.value = it * 32f }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("opacity"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.terminalOpacity.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.terminalOpacity.value }, onValueChange = { GlassEffectConfig.terminalOpacity.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("corner_radius"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.terminalCornerRadius.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.terminalCornerRadius.value }, onValueChange = { GlassEffectConfig.terminalCornerRadius.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("refraction_height"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.terminalRefractionHeight.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.terminalRefractionHeight.value }, onValueChange = { GlassEffectConfig.terminalRefractionHeight.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("refraction_amount"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.terminalRefractionAmount.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.terminalRefractionAmount.value }, onValueChange = { GlassEffectConfig.terminalRefractionAmount.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 设置页面液态玻璃调节
        GlassCard(backdrop = backdrop, pageType = "settings") {
            Column(Modifier.padding(20f.dp), verticalArrangement = Arrangement.spacedBy(12f.dp)) {
                BasicText(AppStrings.get("settings_glass"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("blur_radius"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${GlassEffectConfig.settingsBlurRadius.value.toInt()}dp", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.settingsBlurRadius.value / 32f }, onValueChange = { GlassEffectConfig.settingsBlurRadius.value = it * 32f }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("opacity"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.settingsOpacity.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.settingsOpacity.value }, onValueChange = { GlassEffectConfig.settingsOpacity.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("corner_radius"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.settingsCornerRadius.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.settingsCornerRadius.value }, onValueChange = { GlassEffectConfig.settingsCornerRadius.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("refraction_height"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.settingsRefractionHeight.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.settingsRefractionHeight.value }, onValueChange = { GlassEffectConfig.settingsRefractionHeight.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("refraction_amount"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.settingsRefractionAmount.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.settingsRefractionAmount.value }, onValueChange = { GlassEffectConfig.settingsRefractionAmount.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 应用管理液态玻璃调节
        GlassCard(backdrop = backdrop, pageType = "apps") {
            Column(Modifier.padding(20f.dp), verticalArrangement = Arrangement.spacedBy(12f.dp)) {
                BasicText(AppStrings.get("apps_glass"), style = TextStyle(contentColor, 18f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("blur_radius"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${GlassEffectConfig.appsBlurRadius.value.toInt()}dp", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.appsBlurRadius.value / 32f }, onValueChange = { GlassEffectConfig.appsBlurRadius.value = it * 32f }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("opacity"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.appsOpacity.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.appsOpacity.value }, onValueChange = { GlassEffectConfig.appsOpacity.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("corner_radius"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.appsCornerRadius.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.appsCornerRadius.value }, onValueChange = { GlassEffectConfig.appsCornerRadius.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("refraction_height"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.appsRefractionHeight.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.appsRefractionHeight.value }, onValueChange = { GlassEffectConfig.appsRefractionHeight.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8f.dp)) {
                    Row { BasicText(AppStrings.get("refraction_amount"), style = TextStyle(contentColor, 14f.sp), modifier = Modifier.weight(1f)); BasicText("${(GlassEffectConfig.appsRefractionAmount.value * 100).toInt()}%", style = TextStyle(contentColor.copy(alpha = 0.6f), 12f.sp)) }
                    LiquidSlider(value = { GlassEffectConfig.appsRefractionAmount.value }, onValueChange = { GlassEffectConfig.appsRefractionAmount.value = it }, valueRange = 0f..1f, visibilityThreshold = 0.001f, backdrop = backdrop)
                }
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 保存按钮
        LiquidButton(
            onClick = {
                // 所有参数已直接绑定全局配置，实时生效，只需持久化保存
                GlassEffectPersistence.saveAll()
                showSaved = true
            },
            backdrop = backdrop,
            modifier = Modifier
                .height(48f.dp)
                .fillMaxWidth(),
            tint = Color(0xFF34C759)
        ) {
            BasicText(
                if (showSaved) AppStrings.get("saved") else AppStrings.get("save"),
                Modifier.padding(horizontal = 16f.dp),
                style = TextStyle(Color.White, 16f.sp, androidx.compose.ui.text.font.FontWeight.Medium)
            )
        }

        Spacer(Modifier.height(80f.dp))
    }
}
