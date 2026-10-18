package com.sukisu.ultra.ui.screen.install

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.component.material.ExpressiveDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

/**
 * 临时 root（DirtyFrag Root / GhostLock）的运行日志弹窗。
 *
 * 和页面里其它弹窗一样按 UI 模式分发：Miuix 走 WindowDialog、
 * Material 走 ExpressiveDialog，避免混用另一套对话框观感。
 */
@Composable
internal fun TempRootLogDialog(
    title: String,
    log: String,
    running: Boolean,
    onDismiss: () -> Unit,
) {
    val placeholder = stringResource(R.string.dirtyfrag_running)
    when (LocalUiMode.current) {
        UiMode.Miuix -> {
            WindowDialog(
                show = true,
                title = title,
                onDismissRequest = { if (!running) onDismiss() },
            ) {
                Column {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = log.ifBlank { placeholder },
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    TextButton(
                        text = stringResource(android.R.string.ok),
                        onClick = onDismiss,
                        enabled = !running,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        colors = ButtonDefaults.textButtonColorsPrimary(),
                    )
                }
            }
        }

        UiMode.Material -> {
            ExpressiveDialog(
                onDismissRequest = { if (!running) onDismiss() },
                title = { androidx.compose.material3.Text(title) },
                text = {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        androidx.compose.material3.Text(
                            text = log.ifBlank { placeholder },
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !running) {
                        androidx.compose.material3.Text(stringResource(android.R.string.ok))
                    }
                }
            )
        }
    }
}
