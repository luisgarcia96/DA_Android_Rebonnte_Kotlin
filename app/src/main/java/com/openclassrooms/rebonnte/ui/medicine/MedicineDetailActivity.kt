package com.openclassrooms.rebonnte.ui.medicine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openclassrooms.rebonnte.ui.history.History
import com.openclassrooms.rebonnte.ui.aisle.AisleViewModel
import com.openclassrooms.rebonnte.ui.components.AisleSelector
import com.openclassrooms.rebonnte.ui.theme.RebonnteTheme
import kotlinx.coroutines.launch

private fun validateMedicineFields(name: String, aisle: String, stock: String): String? {
    if (name.isBlank()) return "Le nom du médicament est obligatoire."
    if (aisle.isBlank()) return "Sélectionnez un rayon."

    val stockValue = stock.toIntOrNull()
        ?: return "Le stock doit être un nombre entier."
    if (stockValue < 0) return "Le stock ne peut pas être négatif."

    return null
}

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
                    MedicineDetailScreen(name, aisleNames, viewModel, onDeleted = ::finish)
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
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var isSaving by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

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
                onValueChange = {
                    name = it
                    validationError = null
                },
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            AisleSelector(
                aisleNames = aisleNames,
                selectedAisle = selectedAisle,
                onAisleSelected = { aisleName ->
                    selectedAisle = aisleName
                    validationError = null
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
            TextField(
                value = stock,
                onValueChange = {
                    stock = it
                    validationError = null
                },
                label = { Text("Stock") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    validationError = validateMedicineFields(name, selectedAisle, stock)
                    if (validationError == null) {
                        isSaving = true
                        coroutineScope.launch {
                            viewModel.addMedicine(
                                Medicine(
                                    name = name.trim(),
                                    stock = stock.toInt(),
                                    nameAisle = selectedAisle,
                                    histories = emptyList()
                                )
                            )
                            onSaved()
                        }
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                LoadingButtonContent(label = "Save", isLoading = isSaving)
            }
            validationError?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
fun MedicineDetailScreen(
    name: String,
    aisleNames: List<String>,
    viewModel: MedicineViewModel,
    onDeleted: () -> Unit
) {
    val medicines by viewModel.medicines.collectAsState(initial = emptyList())
    var currentMedicineName by rememberSaveable { mutableStateOf(name) }
    var isEditing by rememberSaveable { mutableStateOf(false) }
    val medicine = medicines.find { it.name == currentMedicineName } ?: return
    var editedName by rememberSaveable { mutableStateOf(medicine.name) }
    var editedAisle by rememberSaveable { mutableStateOf(medicine.nameAisle) }
    var editedStock by rememberSaveable { mutableStateOf(medicine.stock.toString()) }
    var isDeleteDialogVisible by rememberSaveable { mutableStateOf(false) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var isSaving by rememberSaveable { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            TextField(
                value = if (isEditing) editedName else medicine.name,
                onValueChange = {
                    editedName = it
                    validationError = null
                },
                label = { Text("Name") },
                enabled = isEditing,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (isEditing) {
                AisleSelector(
                    aisleNames = aisleNames,
                    selectedAisle = editedAisle,
                    onAisleSelected = { aisleName ->
                        editedAisle = aisleName
                        validationError = null
                    }
                )
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
                    onValueChange = {
                        editedStock = it
                        validationError = null
                    },
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
                            contentDescription = "Decrease stock by one"
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
                            contentDescription = "Increase stock by one"
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    if (isEditing) {
                        validationError = validateMedicineFields(
                            editedName,
                            editedAisle,
                            editedStock
                        )
                        if (validationError == null) {
                            isSaving = true
                            coroutineScope.launch {
                                viewModel.updateMedicine(
                                    medicine.name,
                                    medicine.copy(
                                        name = editedName.trim(),
                                        nameAisle = editedAisle,
                                        stock = editedStock.toInt()
                                    )
                                )
                                currentMedicineName = editedName.trim()
                                isEditing = false
                                isSaving = false
                            }
                        }
                    } else {
                        editedName = medicine.name
                        editedAisle = medicine.nameAisle
                        editedStock = medicine.stock.toString()
                        validationError = null
                        isEditing = true
                    }
                },
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                LoadingButtonContent(
                    label = if (isEditing) "Save" else "Edit",
                    isLoading = isSaving
                )
            }
            validationError?.let { message ->
                Text(text = message, color = MaterialTheme.colorScheme.error)
            }
            TextButton(
                onClick = { isDeleteDialogVisible = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Delete")
            }
            if (isDeleteDialogVisible) {
                AlertDialog(
                    onDismissRequest = {
                        if (!isSaving) isDeleteDialogVisible = false
                    },
                    title = { Text("Delete medicine") },
                    text = { Text("Are you sure you want to delete ${medicine.name}?") },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                isSaving = true
                                coroutineScope.launch {
                                    viewModel.deleteMedicine(medicine.name)
                                    onDeleted()
                                }
                            },
                            enabled = !isSaving
                        ) {
                            LoadingButtonContent(label = "Delete", isLoading = isSaving)
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { isDeleteDialogVisible = false },
                            enabled = !isSaving
                        ) {
                            Text("Cancel")
                        }
                    }
                )
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
private fun LoadingButtonContent(label: String, isLoading: Boolean) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.height(20.dp),
            strokeWidth = 2.dp
        )
    } else {
        Text(label)
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
            Text(text = "Action: ${history.action}")
            Text(text = "User: ${history.userId}")
            Text(text = "Date: ${history.date}")
            Text(text = "Details: ${history.details}")
        }
    }
}
