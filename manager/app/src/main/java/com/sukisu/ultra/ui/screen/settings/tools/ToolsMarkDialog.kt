package com.sukisu.ultra.ui.screen.settings.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.material.ExpressiveDialog
import com.sukisu.ultra.ui.util.getProcessMarkStatus
import com.sukisu.ultra.ui.util.markProcess
import com.sukisu.ultra.ui.util.refreshProcessMarks
import com.sukisu.ultra.ui.util.unmarkProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

private class MarkDialogStateHolder(private val scope: CoroutineScope) {
    var pid by mutableStateOf("")
    var busy by mutableStateOf(false)
    var result by mutableStateOf("")
    val parsedPid: Int? get() = pid.trim().toIntOrNull()

    fun exec(operation: suspend () -> String) {
        if (busy) return
        busy = true
        scope.launch(Dispatchers.IO) {
            val output = runCatching { operation() }.getOrElse { it.message ?: "error" }
            withContext(Dispatchers.Main) {
                busy = false
                result = output
            }
        }
    }
}

@Composable
private fun rememberMarkDialogState(): MarkDialogStateHolder {
    val scope = rememberCoroutineScope()
    return remember { MarkDialogStateHolder(scope) }
}

@Composable
fun MarkDialogMiuix(
    onDismiss: () -> Unit
) {
    val state = rememberMarkDialogState()
    val queryResultFallback = stringResource(R.string.tools_mark_result_empty)
    val doneText = stringResource(R.string.tools_mark_done)
    val failedText = stringResource(R.string.operation_failed)

    fun queryOp(pid: Int) = state.exec { getProcessMarkStatus(pid).ifBlank { queryResultFallback } }
    fun markOp(pid: Int) = state.exec { if (markProcess(pid)) doneText else failedText }
    fun unmarkOp(pid: Int) = state.exec { if (unmarkProcess(pid)) doneText else failedText }

    OverlayDialog(
        show = true,
        title = stringResource(R.string.tools_mark_title),
        onDismissRequest = onDismiss,
        content = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TextField(
                    value = state.pid,
                    onValueChange = { state.pid = it.filter { c -> c.isDigit() } },
                    label = stringResource(R.string.tools_mark_pid_label),
                    useLabelAsPlaceholder = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { state.parsedPid?.let(::queryOp) },
                        enabled = !state.busy && state.parsedPid != null,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        cornerRadius = 8.dp
                    ) {
                        Text(text = stringResource(R.string.tools_mark_query))
                    }
                    Button(
                        onClick = { state.parsedPid?.let(::markOp) },
                        enabled = !state.busy && state.parsedPid != null,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        cornerRadius = 8.dp
                    ) {
                        Text(text = stringResource(R.string.tools_mark_mark))
                    }
                    Button(
                        onClick = { state.parsedPid?.let(::unmarkOp) },
                        enabled = !state.busy && state.parsedPid != null,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp),
                        cornerRadius = 8.dp
                    ) {
                        Text(text = stringResource(R.string.tools_mark_unmark))
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        text = stringResource(R.string.tools_mark_all),
                        onClick = { state.exec { if (markProcess(0)) doneText else failedText } },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(R.string.tools_mark_refresh),
                        onClick = { state.exec { if (refreshProcessMarks()) doneText else failedText } },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (state.result.isNotBlank()) {
                    Text(
                        text = state.result,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        }
    )
}

@Composable
fun MarkDialogMaterial(
    onDismiss: () -> Unit
) {
    val state = rememberMarkDialogState()
    val queryResultFallback = stringResource(R.string.tools_mark_result_empty)
    val doneText = stringResource(R.string.tools_mark_done)
    val failedText = stringResource(R.string.operation_failed)

    fun queryOp(pid: Int) = state.exec { getProcessMarkStatus(pid).ifBlank { queryResultFallback } }
    fun markOp(pid: Int) = state.exec { if (markProcess(pid)) doneText else failedText }
    fun unmarkOp(pid: Int) = state.exec { if (unmarkProcess(pid)) doneText else failedText }

    ExpressiveDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.tools_mark_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                androidx.compose.material3.OutlinedTextField(
                    value = state.pid,
                    onValueChange = { state.pid = it.filter { c -> c.isDigit() } },
                    label = { Text(stringResource(R.string.tools_mark_pid_label)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.OutlinedButton(
                        onClick = { state.parsedPid?.let(::queryOp) },
                        enabled = !state.busy && state.parsedPid != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.tools_mark_query))
                    }
                    androidx.compose.material3.Button(
                        onClick = { state.parsedPid?.let(::markOp) },
                        enabled = !state.busy && state.parsedPid != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.tools_mark_mark))
                    }
                    androidx.compose.material3.OutlinedButton(
                        onClick = { state.parsedPid?.let(::unmarkOp) },
                        enabled = !state.busy && state.parsedPid != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.tools_mark_unmark))
                    }
                }

                if (state.result.isNotBlank()) {
                    androidx.compose.material3.Text(
                        text = state.result,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { state.exec { if (markProcess(0)) doneText else failedText } },
                enabled = !state.busy
            ) {
                Text(stringResource(R.string.tools_mark_all))
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(
                onClick = { state.exec { if (refreshProcessMarks()) doneText else failedText } },
                enabled = !state.busy
            ) {
                Text(stringResource(R.string.tools_mark_refresh))
            }
        }
    )
}
