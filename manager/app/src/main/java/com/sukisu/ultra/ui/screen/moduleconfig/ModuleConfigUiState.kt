package com.sukisu.ultra.ui.screen.moduleconfig

import androidx.compose.runtime.Immutable

@Immutable
data class ModuleConfigEntry(
    val key: String,
    val value: String,
)

@Immutable
data class ModuleConfigUiState(
    val moduleId: String = "",
    val entries: List<ModuleConfigEntry> = emptyList(),
    val isLoading: Boolean = true,
    val showEditDialog: Boolean = false,
    val editEntryKey: String? = null,
    val editEntryValue: String = "",
    val editEntryNew: Boolean = false,
)

@Immutable
data class ModuleConfigActions(
    val onBack: () -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpenAdd: () -> Unit = {},
    val onOpenEdit: (ModuleConfigEntry) -> Unit = {},
    val onDismissEditDialog: () -> Unit = {},
    val onEditKeyChange: (String) -> Unit = {},
    val onEditValueChange: (String) -> Unit = {},
    val onSave: () -> Unit = {},
    val onDelete: () -> Unit = {},
    val onClearAll: () -> Unit = {},
)
