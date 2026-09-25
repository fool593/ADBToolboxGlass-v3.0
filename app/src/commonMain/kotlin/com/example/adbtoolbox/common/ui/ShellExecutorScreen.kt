package com.example.adbtoolbox.common.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
import com.example.adbtoolbox.common.GlassEffectConfig
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ShellExecutorScreen(
    backdrop: Backdrop,
    contentColor: Color,
    onBack: () -> Unit
) {
    var command by remember { mutableStateOf("") }
    var output by remember { mutableStateOf(AppStrings.get("waiting_cmd")) }
    var useRoot by remember { mutableStateOf(false) }
    var isExecuting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun execute() {
        if (command.isBlank() || isExecuting) return
        isExecuting = true
        output = AppStrings.get("executing")
        val cmd = if (useRoot) "su -c '$command'" else command
        scope.launch {
            val result = withContext(Dispatchers.Default) { ADBTools.execCommand(cmd) }
            output = buildString {
                appendLine("$ $command")
                appendLine()
                if (result.output.isNotBlank()) appendLine(result.output.trim())
                if (result.error.isNotBlank()) {
                    appendLine()
                    appendLine(AppStrings.get("err_bracket"))
                    appendLine(result.error.trim())
                }
                appendLine()
                appendLine("${AppStrings.get("exit_code_bracket")}${result.exitCode}]")
            }
            isExecuting = false
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16f.dp)
    ) {
        Spacer(Modifier.height(24f.dp))
        BasicText(AppStrings.get("shell_executor"), style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold))
        Spacer(Modifier.height(16f.dp))

        // Root 切换
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(AppStrings.get("use_root"), style = TextStyle(contentColor, 14f.sp))
            Spacer(Modifier.width(12f.dp))
            LiquidToggle(
                selected = { useRoot },
                onSelect = { useRoot = it },
                backdrop = backdrop,
                modifier = Modifier.size(51f.dp, 31f.dp)
            )
        }

        Spacer(Modifier.height(12f.dp))

        // 命令输入
        Box(
            Modifier
                .fillMaxWidth()
                .height(120f.dp)
                .clipGlassSmall(backdrop)
                .padding(16f.dp)
        ) {
            BasicTextField(
                value = command,
                onValueChange = { command = it },
                textStyle = TextStyle(contentColor, 13f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                modifier = Modifier.fillMaxSize(),
                decorationBox = { innerTextField ->
                    if (command.isEmpty()) {
                        BasicText(AppStrings.get("shell_input_hint"), style = TextStyle(contentColor.copy(alpha = 0.4f), 13f.sp))
                    }
                    innerTextField()
                }
            )
        }

        Spacer(Modifier.height(12f.dp))

        // 执行按钮
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
            LiquidButton(
                onClick = { execute() },
                backdrop = backdrop,
                modifier = Modifier.height(48f.dp).weight(1f),
                tint = Color(0xFF0088FF)
            ) {
                BasicText(if (isExecuting) AppStrings.get("executing") else AppStrings.get("execute_btn"), Modifier.padding(horizontal = 8f.dp), style = TextStyle(Color.White, 14f.sp))
            }
            LiquidButton(
                onClick = { command = ""; output = "" },
                backdrop = backdrop,
                modifier = Modifier.height(48f.dp),
                tint = Color(0xFFFF9500)
            ) {
                BasicText(AppStrings.get("clear"), Modifier.padding(horizontal = 12f.dp), style = TextStyle(Color.White, 14f.sp))
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 快捷命令
        BasicText(AppStrings.get("quick_commands"), style = TextStyle(contentColor, 14f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
        Spacer(Modifier.height(8f.dp))
        val quickCmds = listOf("ls /sdcard", "df -h", "cat /proc/cpuinfo", "getprop", "ip addr", "dumpsys battery")
        quickCmds.forEach { cmd ->
            Spacer(Modifier.height(4f.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .liquidGlassItem(backdrop = backdrop, corner = 20.dp, onClick = { command = cmd })
                    .padding(horizontal = 16f.dp, vertical = 10f.dp)
            ) {
                BasicText(cmd, style = TextStyle(contentColor, 12f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
            }
        }

        Spacer(Modifier.height(16f.dp))

        // 输出区域
        BasicText(AppStrings.get("output"), style = TextStyle(contentColor, 14f.sp, androidx.compose.ui.text.font.FontWeight.Medium))
        Spacer(Modifier.height(8f.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clipGlassSmall(backdrop)
                .padding(16f.dp)
        ) {
            BasicText(
                output,
                style = TextStyle(contentColor, 11f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            )
        }

        Spacer(Modifier.height(80f.dp))
    }
}

@Composable
fun Modifier.clipGlassSmall(backdrop: Backdrop): Modifier {
    return this.then(
        Modifier
            .clip(com.kyant.shapes.RoundedRectangle(32f.dp * GlassEffectConfig.settingsCornerRadius.value))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { com.kyant.shapes.RoundedRectangle(32f.dp * GlassEffectConfig.settingsCornerRadius.value) },
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
