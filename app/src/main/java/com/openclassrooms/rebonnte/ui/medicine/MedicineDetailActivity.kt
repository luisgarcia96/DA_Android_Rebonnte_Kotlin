package com.openclassrooms.rebonnte.ui.medicine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openclassrooms.rebonnte.ui.history.History
import com.openclassrooms.rebonnte.ui.aisle.AisleViewModel
import com.openclassrooms.rebonnte.ui.theme.RebonnteTheme

class MedicineDetailActivity : ComponentActivity() {
    private val viewModel: MedicineViewModel by viewModels()
    private val aisleViewModel: AisleViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val name = intent.getStringExtra("nameMedicine") ?: "Unknown"
        val isNewMedicine = intent.getBooleanExtra("isNewMedicine", false)
        val aisleNames = aisleViewModel.aisles.value.map { it.name }

        setContent {
            RebonnteTheme {
                if (isNewMedicine) {
                    NewMedicineScreen(aisleNames, viewModel, onSaved = ::finish)
                } else {
                    MedicineDetailScreen(name, aisleNames, viewModel)
                }
            }
        }
    }
}

@Composable
fun NewMedicineScreen(
    aisleNames: List<String>,
    viewModel: MedicineViewModel,
    onSaved: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    var selectedAisle by rememberSaveable { mutableStateOf(aisleNames.firstOrNull().orEmpty()) }
    var stock by rememberSaveable { mutableStateOf("0") }
    var isAisleMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(text = "New medicine", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))
            TextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box {
                OutlinedButton(
                    onClick = { isAisleMenuExpanded = true },
                    enabled = aisleNames.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = selectedAisle.ifEmpty { "Select an aisle" })
                }
                DropdownMenu(
                    expanded = isAisleMenuExpanded,
                    onDismissRequest = { isAisleMenuExpanded = false }
                ) {
                    aisleNames.forEach { aisleName ->
                        DropdownMenuItem(
                            text = { Text(aisleName) },
                            onClick = {
                                selectedAisle = aisleName
                                isAisleMenuExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = stock,
                onValueChange = { stock = it },
                label = { Text("Stock") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.addMedicine(
                        Medicine(
                            name = name,
                            stock = stock.toIntOrNull() ?: 0,
                            nameAisle = selectedAisle,
                            histories = emptyList()
                        )
                    )
                    onSaved()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save")
            }
        }
    }
}

@Composable
fun MedicineDetailScreen(
    name: String,
    aisleNames: List<String>,
    viewModel: MedicineViewModel
) {
    val medicines by viewModel.medicines.collectAsState(initial = emptyList())
    var currentMedicineName by rememberSaveable { mutableStateOf(name) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    val medicine = medicines.find { it.name == currentMedicineName } ?: return
    var editedName by rememberSaveable { mutableStateOf(medicine.name) }
    var editedAisle by rememberSaveable { mutableStateOf(medicine.nameAisle) }
    var editedStock by rememberSaveable { mutableStateOf(medicine.stock.toString()) }
    var isAisleMenuExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            TextField(
                value = if (isEditing) editedName else medicine.name,
                onValueChange = { editedName = it },
                label = { Text("Name") },
                enabled = isEditing,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isEditing) {
                Box {
                    OutlinedButton(
                        onClick = { isAisleMenuExpanded = true },
                        enabled = aisleNames.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = editedAisle.ifEmpty { "Select an aisle" })
                    }
                    DropdownMenu(
                        expanded = isAisleMenuExpanded,
                        onDismissRequest = { isAisleMenuExpanded = false }
                    ) {
                        aisleNames.forEach { aisleName ->
                            DropdownMenuItem(
                                text = { Text(aisleName) },
                                onClick = {
                                    editedAisle = aisleName
                                    isAisleMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            } else {
                TextField(
                    value = medicine.nameAisle,
                    onValueChange = {},
                    label = { Text("Aisle") },
                    enabled = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (isEditing) {
                TextField(
                    value = editedStock,
                    onValueChange = { editedStock = it },
                    label = { Text("Stock") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(onClick = {
                        viewModel.updateStock(medicine.name, -1)
                    }) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Minus One"
                        )
                    }
                    TextField(
                        value = medicine.stock.toString(),
                        onValueChange = {},
                        label = { Text("Stock") },
                        enabled = false,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        viewModel.updateStock(medicine.name, 1)
                    }) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Plus One"
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (isEditing) {
                        viewModel.updateMedicine(
                            medicine.name,
                            medicine.copy(
                                name = editedName,
                                nameAisle = editedAisle,
                                stock = editedStock.toIntOrNull() ?: medicine.stock
                            )
                        )
                        currentMedicineName = editedName
                    } else {
                        editedName = medicine.name
                        editedAisle = medicine.nameAisle
                        editedStock = medicine.stock.toString()
                    }
                    isEditing = !isEditing
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isEditing) "Save" else "Edit")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "History", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(medicine.histories) { history ->
                    HistoryItem(history = history)
                }
            }
        }
    }
}

@Composable
fun HistoryItem(history: History) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = history.medicineName, fontWeight = FontWeight.Bold)
            Text(text = "User: ${history.userId}")
            Text(text = "Date: ${history.date}")
            Text(text = "Details: ${history.details}")
        }
    }
}
