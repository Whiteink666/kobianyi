package com.sukisu.ultra.ui.screen.moduleconfig

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

@Composable
fun ModuleConfigMiuix(
    state: ModuleConfigUiState,
    actions: ModuleConfigActions,
) {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.module_config_title),
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        val layoutDirection = LocalLayoutDirection.current
                        Icon(
                            modifier = Modifier.graphicsLayer {
                                if (layoutDirection == LayoutDirection.Rtl) scaleX = -1f
                            },
                            imageVector = MiuixIcons.Back,
                            contentDescription = null,
                            tint = colorScheme.onBackground
                        )
                    }
                },
                actions = {
                    IconButton(onClick = actions.onRefresh) {
                        Icon(
                            imageVector = MiuixIcons.Refresh,
                            contentDescription = null,
                            tint = colorScheme.onBackground
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = actions.onOpenAdd) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .scrollEndHaptic()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
        ) {
            Text(
                text = state.moduleId,
                color = colorScheme.onSurfaceVariantSummary,
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
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(R.string.module_config_empty),
                                    color = colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }

                    items(state.entries, key = { it.key }) { entry ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { actions.onOpenEdit(entry) }
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = entry.key,
                                    color = colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = entry.value,
                                    color = colorScheme.onSurfaceVariantSummary
                                )
                            }
                        }
                    }

                    if (state.entries.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = actions.onClearAll,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(text = stringResource(R.string.module_config_clear))
                            }
                        }
                    }
                }
            }
        }

        if (state.showEditDialog) {
            ModuleConfigEditDialogMiuix(state = state, actions = actions)
        }
    }
}

@Composable
private fun ModuleConfigEditDialogMiuix(
    state: ModuleConfigUiState,
    actions: ModuleConfigActions,
) {
    val showDialog = remember { mutableStateOf(true) }

    OverlayDialog(
        show = showDialog.value,
        title = stringResource(
            if (state.editEntryNew) R.string.module_config_add
            else R.string.module_config_edit
        ),
        onDismissRequest = {
            showDialog.value = false
            actions.onDismissEditDialog()
        },
        content = {
            Column {
                TextField(
                    value = state.editEntryKey ?: "",
                    onValueChange = actions.onEditKeyChange,
                    label = stringResource(R.string.module_config_key),
                    modifier = Modifier.fillMaxWidth(),
                    useLabelAsPlaceholder = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                TextField(
                    value = state.editEntryValue,
                    onValueChange = actions.onEditValueChange,
                    label = stringResource(R.string.module_config_value),
                    modifier = Modifier.fillMaxWidth(),
                    useLabelAsPlaceholder = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (!state.editEntryNew) {
                        TextButton(
                            text = stringResource(R.string.module_config_delete),
                            onClick = actions.onDelete,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    TextButton(
                        text = stringResource(android.R.string.cancel),
                        onClick = {
                            showDialog.value = false
                            actions.onDismissEditDialog()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        text = stringResource(android.R.string.ok),
                        onClick = actions.onSave,
                        enabled = !(state.editEntryKey ?: "").isBlank(),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    )
}
