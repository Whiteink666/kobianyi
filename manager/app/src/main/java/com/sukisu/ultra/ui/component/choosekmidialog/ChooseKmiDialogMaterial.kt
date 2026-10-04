package com.sukisu.ultra.ui.component.choosekmidialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.component.material.SegmentedColumn
import com.sukisu.ultra.ui.component.material.SegmentedRadioItem
import com.sukisu.ultra.ui.util.getCurrentKmi
import com.sukisu.ultra.ui.util.getSupportedKmis
import kotlin.collections.map

@Composable
fun ChooseKmiDialogMaterial(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onSelected: (String?) -> Unit
) {
    if (!show) return

    val supportedKMIs by produceState(initialValue = emptyList<String>()) {
        value = getSupportedKmis()
    }

    val currentKmi by produceState(initialValue = "") {
        value = getCurrentKmi()
    }

    val detected = currentKmi.isNotBlank()
    val initialSelection = if (currentKmi in supportedKMIs) currentKmi else KMI_AUTO_DETECT
    val selectedKmi = remember(currentKmi, supportedKMIs) {
        mutableStateOf(initialSelection)
    }

    AlertDialog(
        onDismissRequest = {
            onDismissRequest()
            selectedKmi.value = initialSelection
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val selection = selectedKmi.value
                    onSelected(if (selection == KMI_AUTO_DETECT) null else selection)
                    onDismissRequest()
                },
                enabled = selectedKmi.value == KMI_AUTO_DETECT ||
                    supportedKMIs.contains(selectedKmi.value) ||
                    (detected && selectedKmi.value == currentKmi)
            ) {
                Text(stringResource(id = R.string.confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismissRequest()
                selectedKmi.value = initialSelection
            }) {
                Text(stringResource(id = android.R.string.cancel))
            }
        },
        title = {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                text = stringResource(R.string.select_kmi),
                textAlign = TextAlign.Center
            )
        },
        text = {
            SegmentedColumn(
                content = buildList {
                    add {
                        SegmentedRadioItem(
                            title = stringResource(R.string.select_kmi_auto),
                            summary = stringResource(R.string.select_kmi_auto_summary),
                            selected = selectedKmi.value == KMI_AUTO_DETECT,
                            onClick = { selectedKmi.value = KMI_AUTO_DETECT }
                        )
                    }
                    if (supportedKMIs.isEmpty()) {
                        add {
                            Text(
                                text = stringResource(R.string.select_kmi_no_lkm),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                            )
                        }
                    }
                    supportedKMIs.map { kmi ->
                        add {
                            SegmentedRadioItem(
                                title = kmi,
                                summary = if (kmi == currentKmi) stringResource(R.string.current_device_kmi) else null,
                                selected = selectedKmi.value == kmi,
                                onClick = { selectedKmi.value = kmi }
                            )
                        }
                    }
                }
            )
        }
    )
}
