package com.sukisu.ultra.ui.screen.moduleconfig

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.LocalUiMode
import com.sukisu.ultra.ui.UiMode
import com.sukisu.ultra.ui.component.dialog.ConfirmResult
import com.sukisu.ultra.ui.component.dialog.rememberConfirmDialog
import com.sukisu.ultra.ui.navigation3.LocalNavigator
import com.sukisu.ultra.ui.util.clearModuleConfig
import com.sukisu.ultra.ui.util.deleteModuleConfigKey
import com.sukisu.ultra.ui.util.listModuleConfig
import com.sukisu.ultra.ui.util.setModuleConfigValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CONFIG_KEY_REGEX = Regex("^[a-zA-Z][a-zA-Z0-9._-]+$")

@Composable
fun ModuleConfigScreen(moduleId: String) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val confirmDialog = rememberConfirmDialog()

    var entries by remember { mutableStateOf<List<ModuleConfigEntry>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editKey by remember { mutableStateOf("") }
    var editValue by remember { mutableStateOf("") }
    var editIsNew by remember { mutableStateOf(true) }

    val operationFailedText = context.getString(R.string.operation_failed)
    val savedText = context.getString(R.string.module_config_saved)
    val deletedText = context.getString(R.string.module_config_deleted)
    val clearedText = context.getString(R.string.module_config_cleared)
    val invalidKeyText = context.getString(R.string.module_config_invalid_key)
    val confirmDeleteText = context.getString(R.string.module_config_confirm_delete)
    val confirmClearText = context.getString(R.string.module_config_confirm_clear)

    fun loadEntries() {
        scope.launch(Dispatchers.IO) {
            val map = runCatching { listModuleConfig(moduleId) }.getOrElse { emptyMap() }
            withContext(Dispatchers.Main) {
                entries = map.map { ModuleConfigEntry(it.key, it.value) }
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadEntries()
    }

    val actions = ModuleConfigActions(
        onBack = { navigator.pop() },
        onRefresh = {
            isLoading = true
            loadEntries()
        },
        onOpenAdd = {
            editKey = ""
            editValue = ""
            editIsNew = true
            showEditDialog = true
        },
        onOpenEdit = { entry ->
            editKey = entry.key
            editValue = entry.value
            editIsNew = false
            showEditDialog = true
        },
        onDismissEditDialog = {
            showEditDialog = false
        },
        onEditKeyChange = { editKey = it },
        onEditValueChange = { editValue = it },
        onSave = {
            val key = editKey.trim()
            // ksud requires keys to match ^[a-zA-Z][a-zA-Z0-9._-]+$ (min 2 chars)
            if (!CONFIG_KEY_REGEX.matches(key)) {
                Toast.makeText(context, invalidKeyText, Toast.LENGTH_SHORT).show()
                return@ModuleConfigActions
            }
            showEditDialog = false
            scope.launch(Dispatchers.IO) {
                val success = setModuleConfigValue(moduleId, key, editValue)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (success) savedText else operationFailedText,
                        Toast.LENGTH_SHORT
                    ).show()
                    if (success) loadEntries()
                }
            }
        },
        onDelete = {
            val key = editKey.trim()
            showEditDialog = false
            scope.launch {
                if (confirmDialog.awaitConfirm(
                        title = confirmDeleteText,
                        content = key
                    ) == ConfirmResult.Confirmed
                ) {
                    scope.launch(Dispatchers.IO) {
                        val success = deleteModuleConfigKey(moduleId, key)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                if (success) deletedText else operationFailedText,
                                Toast.LENGTH_SHORT
                            ).show()
                            if (success) loadEntries()
                        }
                    }
                }
            }
        },
        onClearAll = {
            scope.launch {
                if (confirmDialog.awaitConfirm(
                        title = confirmClearText,
                        content = confirmClearText
                    ) == ConfirmResult.Confirmed
                ) {
                    scope.launch(Dispatchers.IO) {
                        val success = clearModuleConfig(moduleId)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(
                                context,
                                if (success) clearedText else operationFailedText,
                                Toast.LENGTH_SHORT
                            ).show()
                            if (success) loadEntries()
                        }
                    }
                }
            }
        }
    )

    val state = ModuleConfigUiState(
        moduleId = moduleId,
        entries = entries,
        isLoading = isLoading,
        showEditDialog = showEditDialog,
        editEntryKey = editKey,
        editEntryValue = editValue,
        editEntryNew = editIsNew,
    )

    when (LocalUiMode.current) {
        UiMode.Miuix -> ModuleConfigMiuix(state = state, actions = actions)
        UiMode.Material -> ModuleConfigMaterial(state = state, actions = actions)
    }
}
