package com.example.adbtoolbox.common.ui

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
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
import com.example.adbtoolbox.common.AppStrings
import com.example.adbtoolbox.common.CommandResult
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class TerminalLine(
    val text: String,
    val type: LineType
)

enum class LineType {
    INPUT, OUTPUT, ERROR, INFO
}

@Composable
fun TerminalScreen(
    backdrop: Backdrop,
    contentColor: Color
) {
    var lines by remember { mutableStateOf(listOf(TerminalLine(AppStrings.get("adb_terminal_hint"), LineType.INFO))) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1)
    }

    fun executeCommand(cmd: String) {
        if (cmd.isBlank()) return
        lines = lines + TerminalLine("$ $cmd", LineType.INPUT)
        input = ""
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                ADBTools.execCommand(cmd)
            }
            if (result.output.isNotBlank()) {
                lines = lines + TerminalLine(result.output.trim(), LineType.OUTPUT)
            }
            if (result.error.isNotBlank()) {
                lines = lines + TerminalLine(result.error.trim(), LineType.ERROR)
            }
            if (result.exitCode != 0 && result.output.isBlank() && result.error.isBlank()) {
                lines = lines + TerminalLine("${AppStrings.get("success")}: ${result.exitCode}", LineType.ERROR)
            }
        }
    }

    // 读取并执行初始命令（由其他页面跳转时设置）
    LaunchedEffect(Unit) {
        val initialCmd = AppCache.terminalInitialCommand.value
        if (!initialCmd.isNullOrBlank()) {
            // 清除初始命令，避免重复执行
            AppCache.terminalInitialCommand.value = null
            // 逐行执行初始命令
            initialCmd.split("\n").forEach { line ->
                if (line.isNotBlank()) {
                    executeCommand(line)
                    // 短暂延迟，避免命令执行过快
                    kotlinx.coroutines.delay(150)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16f.dp)) {
        Spacer(Modifier.height(24f.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText(AppStrings.get("terminal_title"), style = TextStyle(contentColor, 24f.sp, androidx.compose.ui.text.font.FontWeight.Bold), modifier = Modifier.weight(1f))
            LiquidButton(
                onClick = {
                    lines = lines + TerminalLine("$ diag", LineType.INPUT)
                    scope.launch {
                        val diag = withContext(Dispatchers.Default) {
                            ADBTools.getShizukuDiagnostics()
                        }
                        lines = lines + TerminalLine(diag, LineType.OUTPUT)
                    }
                },
                backdrop = backdrop,
                modifier = Modifier.height(36f.dp),
                tint = Color(0xFFFF9500)
            ) {
                BasicText(AppStrings.get("diagnose"), Modifier.padding(horizontal = 12f.dp), style = TextStyle(Color.White, 12f.sp))
            }
        }
        Spacer(Modifier.height(12f.dp))

        // 终端输出区域
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedRectangle(20f.dp))
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { RoundedRectangle(20f.dp) },
                    effects = {
                        vibrancy()
                        blur(20f.dp.toPx())
                        lens(8f.dp.toPx(), 16f.dp.toPx())
                    }
                )
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(16f.dp),
                verticalArrangement = Arrangement.spacedBy(2f.dp)
            ) {
                items(lines) { line ->
                    val color = when (line.type) {
                        LineType.INPUT -> Color(0xFF0088FF)
                        LineType.OUTPUT -> contentColor
                        LineType.ERROR -> Color(0xFFFF3B30)
                        LineType.INFO -> contentColor.copy(alpha = 0.5f)
                    }
                    BasicText(
                        line.text,
                        style = TextStyle(color, 12f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    )
                }
            }
        }

        Spacer(Modifier.height(12f.dp))

        // 输入区域
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .weight(1f)
                    .height(48f.dp)
                    .clip(RoundedRectangle(16f.dp))
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { RoundedRectangle(16f.dp) },
                        effects = {
                            vibrancy()
                            blur(20f.dp.toPx())
                            lens(8f.dp.toPx(), 16f.dp.toPx())
                        }
                    )
                    .padding(horizontal = 16f.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(contentColor, 14f.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        if (input.isEmpty()) {
                            BasicText(AppStrings.get("enter_command"), style = TextStyle(contentColor.copy(alpha = 0.4f), 14f.sp))
                        }
                        innerTextField()
                    }
                )
            }
            Spacer(Modifier.width(8f.dp))
            LiquidButton(
                onClick = { executeCommand(input) },
                backdrop = backdrop,
                modifier = Modifier.height(48f.dp),
                tint = Color(0xFF0088FF)
            ) {
                BasicText(AppStrings.get("execute_btn"), Modifier.padding(horizontal = 12f.dp), style = TextStyle(Color.White, 14f.sp))
            }
        }

        // 快捷命令
        Spacer(Modifier.height(8f.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8f.dp)) {
            QuickCmdButton(backdrop, "ls", contentColor) { executeCommand("ls") }
            QuickCmdButton(backdrop, "pwd", contentColor) { executeCommand("pwd") }
            QuickCmdButton(backdrop, "ps", contentColor) { executeCommand("ps") }
            QuickCmdButton(backdrop, AppStrings.get("clear_screen"), contentColor) { lines = emptyList() }
        }
        Spacer(Modifier.height(16f.dp))
    }
}

@Composable
fun QuickCmdButton(backdrop: Backdrop, label: String, contentColor: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedRectangle(12f.dp))
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedRectangle(12f.dp) },
                effects = { blur(10f.dp.toPx()) }
            )
            .clickableNoRipple(onClick)
            .padding(horizontal = 14f.dp, vertical = 8f.dp)
    ) {
        BasicText(label, style = TextStyle(contentColor, 12f.sp))
    }
}

@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.then(Modifier.clickable(interactionSource = interactionSource, indication = null, onClick = onClick))
}
