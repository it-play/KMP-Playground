package com.amond.kmpbook.ui.screens.debug

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amond.kmpbook.debug.console.DebugConsoleLine
import com.amond.kmpbook.debug.console.DebugConsoleLineTone
import com.amond.kmpbook.debug.console.DebugConsoleSession
import com.amond.kmpbook.ui.theme.MarketColors
import com.amond.kmpbook.ui.theme.MarketMotion
import com.amond.kmpbook.ui.theme.MarketRadii
import com.amond.kmpbook.ui.theme.MarketSpacing
import com.amond.kmpbook.ui.theme.MarketType

@Composable
fun DebugConsoleOverlay(
    visible: Boolean,
    session: DebugConsoleSession,
    onExecute: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(MarketMotion.quick)),
            exit = fadeOut(tween(MarketMotion.standard)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MarketColors.Scrim)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .semantics { contentDescription = "디버그 콘솔 닫기" },
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 24.dp)
                .fillMaxWidth(0.94f)
                .widthIn(max = 1_480.dp)
                .fillMaxHeight(0.68f),
            enter = slideInVertically(
                animationSpec = tween(MarketMotion.emphasized),
                initialOffsetY = { height -> -height },
            ) + fadeIn(tween(MarketMotion.standard)),
            exit = slideOutVertically(
                animationSpec = tween(MarketMotion.standard),
                targetOffsetY = { height -> -height },
            ) + fadeOut(tween(MarketMotion.quick)),
        ) {
            DebugConsoleSurface(
                visible = visible,
                session = session,
                onExecute = onExecute,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun DebugConsoleSurface(
    visible: Boolean,
    session: DebugConsoleSession,
    onExecute: () -> Unit,
    onDismiss: () -> Unit,
) {
    val inputFocusRequester = remember { FocusRequester() }
    val outputListState = rememberLazyListState()

    LaunchedEffect(visible, session.isExecuting) {
        if (visible && !session.isExecuting) inputFocusRequester.requestFocus()
    }
    LaunchedEffect(visible, session.lines.size) {
        if (visible && session.lines.isNotEmpty()) {
            outputListState.scrollToItem(session.lines.lastIndex)
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MarketColors.NavyRaised,
        shape = RoundedCornerShape(MarketRadii.large),
        border = BorderStroke(1.dp, MarketColors.SignalLine.copy(alpha = 0.58f)),
        shadowElevation = 18.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            DebugConsoleHeader(
                lineCount = session.lines.size,
                isExecuting = session.isExecuting,
                onClear = session::clearOutput,
                onDismiss = onDismiss,
            )
            ConsoleDivider()
            SelectionContainer(
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                LazyColumn(
                    state = outputListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MarketColors.Navy),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    items(items = session.lines, key = DebugConsoleLine::sequence) { line ->
                        DebugConsoleOutputLine(line)
                    }
                }
            }
            ConsoleDivider()
            DebugConsolePrompt(
                value = session.input,
                isExecuting = session.isExecuting,
                focusRequester = inputFocusRequester,
                onValueChange = session::updateInput,
                onPreviousCommand = session::previousCommand,
                onNextCommand = session::nextCommand,
                onExecute = onExecute,
            )
        }
    }
}

@Composable
private fun ConsoleDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(MarketColors.Grey700))
}

@Composable
private fun DebugConsoleHeader(
    lineCount: Int,
    isExecuting: Boolean,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .background(
                    if (isExecuting) MarketColors.Amber else MarketColors.Positive,
                    RoundedCornerShape(MarketRadii.pill),
                ),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = "RUNTIME / DEBUG CONSOLE",
                style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
            )
            Text(
                text = if (isExecuting) "명령을 적용하는 중" else "개발 모드 연결됨 · 로그 ${lineCount}줄",
                style = MarketType.caption,
                color = if (isExecuting) MarketColors.Amber else MarketColors.SignalLine,
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onClear, enabled = lineCount > 0 && !isExecuting) {
            Text(
                "기록 지우기",
                style = MarketType.label,
                color = if (lineCount > 0 && !isExecuting) MarketColors.Grey200 else MarketColors.Grey600,
            )
        }
        TextButton(onClick = onDismiss) {
            Text("닫기  ×", style = MarketType.label, color = MarketColors.Grey200)
        }
    }
}

@Composable
private fun DebugConsoleOutputLine(line: DebugConsoleLine) {
    val color = when (line.tone) {
        DebugConsoleLineTone.SYSTEM -> MarketColors.SignalLine
        DebugConsoleLineTone.COMMAND -> Color.White
        DebugConsoleLineTone.OUTPUT -> MarketColors.Grey200
        DebugConsoleLineTone.WARNING -> MarketColors.Amber
        DebugConsoleLineTone.ERROR -> MarketColors.Rise
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(
            text = line.sequence.toString().padStart(4, '0'),
            modifier = Modifier.width(48.dp),
            style = MarketType.caption.copy(fontFamily = FontFamily.Monospace),
            color = MarketColors.Grey600,
        )
        Text(
            text = line.text,
            modifier = Modifier.weight(1f),
            style = MarketType.body.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = if (line.tone == DebugConsoleLineTone.COMMAND) {
                    FontWeight.SemiBold
                } else {
                    FontWeight.Normal
                },
            ),
            color = color,
        )
    }
}

@Composable
private fun DebugConsolePrompt(
    value: String,
    isExecuting: Boolean,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit,
    onPreviousCommand: () -> Unit,
    onNextCommand: () -> Unit,
    onExecute: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "debug›",
                modifier = Modifier.padding(end = 10.dp),
                style = MarketType.body.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold),
                color = MarketColors.SignalLine,
            )
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter,
                            Key.NumPadEnter,
                            -> {
                                if (value.isNotBlank() && !isExecuting) onExecute()
                                true
                            }
                            Key.DirectionUp -> {
                                onPreviousCommand()
                                true
                            }
                            Key.DirectionDown -> {
                                onNextCommand()
                                true
                            }
                            else -> false
                        }
                    }
                    .semantics { contentDescription = "디버그 콘솔 명령 입력" },
                enabled = !isExecuting,
                singleLine = true,
                placeholder = {
                    Text(
                        text = "명령을 입력하세요. 예: help",
                        style = MarketType.body.copy(fontFamily = FontFamily.Monospace),
                        color = MarketColors.Grey600,
                    )
                },
                textStyle = MarketType.body.copy(fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    disabledTextColor = MarketColors.Grey400,
                    cursorColor = MarketColors.SignalLine,
                    focusedBorderColor = MarketColors.SignalLine,
                    unfocusedBorderColor = MarketColors.Grey700,
                    disabledBorderColor = MarketColors.Grey700,
                    focusedContainerColor = MarketColors.Navy,
                    unfocusedContainerColor = MarketColors.Navy,
                    disabledContainerColor = MarketColors.Navy,
                ),
            )
            Spacer(Modifier.width(10.dp))
            TextButton(
                onClick = onExecute,
                enabled = value.isNotBlank() && !isExecuting,
            ) {
                if (isExecuting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MarketColors.SignalLine,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text = "실행  ↵",
                        style = MarketType.label.copy(fontWeight = FontWeight.SemiBold),
                        color = MarketColors.SignalLine,
                    )
                }
            }
        }
        Spacer(Modifier.height(MarketSpacing.xs))
        Text(
            text = "↑↓ 이전 명령   ·   Enter 실행   ·   clear 기록 삭제   ·   ` 또는 Esc 닫기",
            style = MarketType.caption.copy(fontFamily = FontFamily.Monospace),
            color = MarketColors.Grey400,
        )
    }
}
