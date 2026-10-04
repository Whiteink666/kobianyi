package com.sukisu.ultra.ui.component.choosekmidialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.sukisu.ultra.R
import com.sukisu.ultra.ui.util.getCurrentKmi
import com.sukisu.ultra.ui.util.getSupportedKmis
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.CheckboxLocation
import top.yukonga.miuix.kmp.preference.CheckboxPreference

/**
 * Sentinel selection meaning "no manual KMI, let ksud detect it".
 * A real KMI never looks like this (android\d+-\d+\.\d+).
 */
internal const val KMI_AUTO_DETECT = "auto-detect"

@Composable
fun ChooseKmiDialogMiuix(
    show: Boolean,
    onDismissRequest: () -> Unit,
    onSelected: (String?) -> Unit
) {
    val supportedKMIs by produceState(initialValue = emptyList<String>()) {
        value = getSupportedKmis()
    }
    val currentKmi by produceState(initialValue = "") {
        value = getCurrentKmi()
    }
    val detected = currentKmi.isNotBlank()
    val initialSelection = if (currentKmi in supportedKMIs) currentKmi else KMI_AUTO_DETECT
    val currentSelection = rememberSaveable(currentKmi, supportedKMIs) {
        mutableStateOf(initialSelection)
    }
    OverlayDialog(
        show = show,
        title = stringResource(R.string.select_kmi),
        summary = stringResource(R.string.current_kmi, currentKmi.let { it.ifBlank { "Unknown" } }),
        onDismissRequest = {
            onDismissRequest()
            currentSelection.value = initialSelection
        },
        insideMargin = DpSize(0.dp, 24.dp),
        content = {
            Column(modifier = Modifier.heightIn(max = 500.dp)) {
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    items(supportedKMIs) { kmi ->
                        CheckboxPreference(
                            title = kmi,
                            summary = if (kmi == currentKmi) stringResource(R.string.current_device_kmi) else null,
                            insideMargin = PaddingValues(horizontal = 30.dp, vertical = 16.dp),
                            checkboxLocation = CheckboxLocation.End,
                            checked = currentSelection.value == kmi,
                            holdDownState = currentSelection.value == kmi,
                            onCheckedChange = { _ ->
                                currentSelection.value = kmi
                            }
                        )
                    }
                    item {
                        CheckboxPreference(
                            title = stringResource(R.string.select_kmi_auto),
                            summary = stringResource(R.string.select_kmi_auto_summary),
                            insideMargin = PaddingValues(horizontal = 30.dp, vertical = 16.dp),
                            checkboxLocation = CheckboxLocation.End,
                            checked = currentSelection.value == KMI_AUTO_DETECT,
                            holdDownState = currentSelection.value == KMI_AUTO_DETECT,
                            onCheckedChange = { _ ->
                                currentSelection.value = KMI_AUTO_DETECT
                            }
                        )
                    }
                }
                if (supportedKMIs.isEmpty()) {
                    Text(
                        text = stringResource(R.string.select_kmi_no_lkm),
                        modifier = Modifier.padding(horizontal = 30.dp)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            currentSelection.value = initialSelection
                        },
                        text = stringResource(android.R.string.cancel),
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(20.dp))
                    TextButton(
                        enabled = currentSelection.value == KMI_AUTO_DETECT ||
                            supportedKMIs.contains(currentSelection.value) ||
                            (detected && currentSelection.value == currentKmi),
                        onClick = {
                            val selection = currentSelection.value
                            onSelected(if (selection == KMI_AUTO_DETECT) null else selection)
                            onDismissRequest()
                        },
                        text = stringResource(R.string.confirm),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.textButtonColorsPrimary()
                    )
                }
            }
        }
    )
}
