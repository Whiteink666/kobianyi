package com.sukisu.ultra.ui.component.uninstalldialog

import android.widget.Toast
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.component.material.SegmentedColumn
import com.sukisu.ultra.ui.component.material.SegmentedListItem
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.navigation3.Route
import com.sukisu.ultra.ui.screen.flash.FlashIt
import com.sukisu.ultra.ui.screen.flash.UninstallType
import com.sukisu.ultra.ui.screen.flash.UninstallType.PERMANENT
import com.sukisu.ultra.ui.screen.flash.UninstallType.RESTORE_STOCK_IMAGE
import com.sukisu.ultra.ui.screen.flash.UninstallType.TEMPORARY
import com.sukisu.ultra.ui.util.uninstallTemporary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UninstallDialogMaterial(
    show: Boolean,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val options = listOf(
        TEMPORARY,
        PERMANENT,
        RESTORE_STOCK_IMAGE
    )
    val showConfirmDialog = remember { mutableStateOf(false) }
    val runType = remember { mutableStateOf<UninstallType?>(null) }

    val run = { type: UninstallType ->
        when (type) {
            PERMANENT -> navigator.push(Route.Flash(FlashIt.FlashUninstall))
            RESTORE_STOCK_IMAGE -> navigator.push(Route.Flash(FlashIt.FlashRestore))
            TEMPORARY -> {
                Toast.makeText(context, R.string.settings_uninstall_temporary_started, Toast.LENGTH_SHORT).show()
                scope.launch(Dispatchers.IO) {
                    val success = uninstallTemporary()
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            context,
                            if (success) R.string.settings_uninstall_temporary_success
                            else R.string.settings_uninstall_temporary_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
            else -> Unit
        }
    }

    if (show) {
        AlertDialog(
            onDismissRequest = onDismissRequest,
            title = { Text(stringResource(R.string.settings_uninstall)) },
            text = {
                SegmentedColumn(
                    modifier = Modifier,
                    content = options.map { type ->
                        {
                            SegmentedListItem(
                                onClick = {
                                    showConfirmDialog.value = true
                                    runType.value = type
                                },
                                headlineContent = { Text(stringResource(type.title)) },
                                supportingContent = { Text(stringResource(type.message)) },
                                leadingContent = {
                                    Icon(
                                        imageVector = type.icon,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = onDismissRequest) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    val confirmDialog = rememberConfirmDialog(
        onConfirm = {
            showConfirmDialog.value = false
            onDismissRequest()
            runType.value?.let { type ->
                run(type)
            }
        },
        onDismiss = {
            showConfirmDialog.value = false
        }
    )

    val dialogTitle = runType.value?.let { type ->
        options.find { it == type }?.let { stringResource(it.title) }
    } ?: ""
    val dialogContent = runType.value?.let { type ->
        options.find { it == type }?.let { stringResource(it.message) }
    }

    if (showConfirmDialog.value) {
        confirmDialog.showConfirm(title = dialogTitle, content = dialogContent)
    }
}
