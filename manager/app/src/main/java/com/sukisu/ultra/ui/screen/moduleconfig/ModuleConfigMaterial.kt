package com.sukisu.ultra.ui.screen.moduleconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.material.ExpressiveDialog
import com.sukisu.ultra.ui.component.material.SegmentedColumn
import com.sukisu.ultra.ui.component.material.SegmentedListItem
import com.sukisu.ultra.ui.component.material.TopBarBackButton
import com.sukisu.ultra.ui.component.material.expressiveTopAppBarColors

@Composable
fun ModuleConfigMaterial(
    state: ModuleConfigUiState,
    actions: ModuleConfigActions,
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.module_config_title)) },
                navigationIcon = {
                    TopBarBackButton(onClick = actions.onBack)
                },
                actions = {
                    IconButton(onClick = actions.onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = expressiveTopAppBarColors(),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = actions.onOpenAdd) {
                Icon(Icons.Rounded.Add, contentDescription = null)
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    text = state.moduleId,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (state.entries.isEmpty()) {
                            item {
                                Text(
                                    text = stringResource(R.string.module_config_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }

                        items(state.entries, key = { it.key }) { entry ->
                            SegmentedColumn(
                                content = listOf({
                                    SegmentedListItem(
                                        onClick = { actions.onOpenEdit(entry) },
                                        headlineContent = { Text(entry.key) },
                                        supportingContent = {
                                            Text(
                                                entry.value,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    )
                                })
                            )
                        }

                        if (state.entries.isNotEmpty()) {
                            item {
                                Button(
                                    onClick = actions.onClearAll,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(stringResource(R.string.module_config_clear))
                                }
                            }
                        }
                    }
                }
            }
        }

        if (state.showEditDialog) {
            ModuleConfigEditDialogMaterial(state = state, actions = actions)
        }
    }
}

@Composable
private fun ModuleConfigEditDialogMaterial(
    state: ModuleConfigUiState,
    actions: ModuleConfigActions,
) {
    ExpressiveDialog(
        onDismissRequest = actions.onDismissEditDialog,
        title = {
            Text(
                stringResource(
                    if (state.editEntryNew) R.string.module_config_add
                    else R.string.module_config_edit
                )
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.editEntryKey ?: "",
                    onValueChange = actions.onEditKeyChange,
                    label = { Text(stringResource(R.string.module_config_key)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.editEntryValue,
                    onValueChange = actions.onEditValueChange,
                    label = { Text(stringResource(R.string.module_config_value)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = actions.onSave,
                enabled = !(state.editEntryKey ?: "").isBlank()
            ) {
                Text(stringResource(android.R.string.ok))
            }
        },
        dismissButton = {
            if (!state.editEntryNew) {
                TextButton(onClick = actions.onDelete) {
                    Text(stringResource(R.string.module_config_delete))
                }
            } else {
                TextButton(onClick = actions.onDismissEditDialog) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        }
    )
}
