package com.openclassrooms.rebonnte.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AisleSelector(
    aisleNames: List<String>,
    selectedAisle: String,
    onAisleSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Box(modifier = modifier) {
        ExposedDropdownMenuBox(
            expanded = isMenuExpanded,
            onExpandedChange = { isMenuExpanded = it && aisleNames.isNotEmpty() }
        ) {
            OutlinedTextField(
                value = selectedAisle,
                onValueChange = {},
                readOnly = true,
                enabled = aisleNames.isNotEmpty(),
                label = { Text("Aisle") },
                placeholder = { Text("Select an aisle") },
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = isMenuExpanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = isMenuExpanded,
                onDismissRequest = { isMenuExpanded = false }
            ) {
                aisleNames.forEach { aisleName ->
                    DropdownMenuItem(
                        text = { Text(aisleName) },
                        onClick = {
                            onAisleSelected(aisleName)
                            isMenuExpanded = false
                        }
                    )
                }
            }
        }
    }
}
