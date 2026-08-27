package com.openclassrooms.rebonnte.ui.medicine

import android.content.Context
import androidx.compose.runtime.Composable

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlin.math.min
import androidx.compose.ui.platform.LocalContext
import com.openclassrooms.rebonnte.ui.components.MedicineListItem

@Composable
fun MedicineScreen(
    viewModel: MedicineViewModel,
    onAddTestData: () -> Unit,
    onClearAllData: () -> Unit
) {
    val medicines by viewModel.medicines.collectAsState(initial = emptyList())
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var visibleCount by rememberSaveable { mutableStateOf(PAGE_SIZE) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var isLoadingNextPage by remember { mutableStateOf(false) }

    LaunchedEffect(medicines) {
        visibleCount = min(PAGE_SIZE, medicines.size)
    }

    LaunchedEffect(listState, medicines.size, visibleCount) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .filter { lastVisibleIndex ->
                lastVisibleIndex >= visibleCount - 2 &&
                    visibleCount < medicines.size &&
                    !isLoadingNextPage
            }
            .collectLatest {
                isLoadingNextPage = true
                visibleCount = min(visibleCount + PAGE_SIZE, medicines.size)
                isLoadingNextPage = false
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row {
            TextButton(onClick = onAddTestData) {
                Text("Add temporary test data")
            }
            TextButton(onClick = { showClearDialog = true }) {
                Text("Clear all test data")
            }
        }
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                title = { Text("Clear all data") },
                text = { Text("This removes all medicines and extra aisles.") },
                confirmButton = {
                    TextButton(onClick = {
                        onClearAllData()
                        showClearDialog = false
                    }) {
                        Text("Clear")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            items(medicines.take(visibleCount)) { medicine ->
                MedicineListItem(medicine = medicine, onClick = {
                    startDetailActivity(context, medicine.name)
                })
            }
            if (visibleCount < medicines.size) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

private const val PAGE_SIZE = 20

private fun startDetailActivity(context: Context, name: String) {
    val intent = Intent(context, MedicineDetailActivity::class.java).apply {
        putExtra("nameMedicine", name)
    }
    context.startActivity(intent)
}
