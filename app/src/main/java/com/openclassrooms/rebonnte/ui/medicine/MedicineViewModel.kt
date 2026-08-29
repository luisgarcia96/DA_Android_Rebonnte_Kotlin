package com.openclassrooms.rebonnte.ui.medicine

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.openclassrooms.rebonnte.ui.aisle.Aisle
import com.openclassrooms.rebonnte.ui.history.History
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.Locale
import java.util.Random
import androidx.core.content.edit

class MedicineViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private var allMedicines: List<Medicine> = emptyList()
    private val _medicines = MutableStateFlow(loadMedicines().also { allMedicines = it })
    val medicines: StateFlow<List<Medicine>> = _medicines.asStateFlow()
    private var searchQuery = ""
    private var sortOrder = SortOrder.NONE

    fun addRandomMedicine(aisles: List<Aisle>) {
        if (aisles.isEmpty()) {
            showError("Ajoutez un rayon avant d'ajouter un médicament.")
            return
        }

        val updatedMedicines = ArrayList(allMedicines)
        updatedMedicines.add(
            Medicine(
                "Medicine " + (updatedMedicines.size + 1),
                Random().nextInt(100),
                aisles[Random().nextInt(aisles.size)].name,
                emptyList()
            )
        )
        allMedicines = updatedMedicines
        publishVisibleMedicines()
        persistMedicines()
    }

    suspend fun addMedicine(medicine: Medicine) {
        allMedicines = allMedicines + medicine
        publishVisibleMedicines()
        persistMedicinesAsync(allMedicines)
    }

    fun addTestMedicines(aisleNames: List<String>) {
        if (aisleNames.isEmpty()) {
            showError("Ajoutez un rayon avant de créer les données de test.")
            return
        }

        val firstTestMedicineNumber = allMedicines.size + 1
        val testMedicines = List(TEST_MEDICINE_COUNT) { index ->
            val number = firstTestMedicineNumber + index
            Medicine(
                name = "Test Medicine $number",
                stock = number % 100,
                nameAisle = aisleNames[index % aisleNames.size],
                histories = emptyList()
            )
        }
        allMedicines = allMedicines + testMedicines
        publishVisibleMedicines()
        persistMedicines()
    }

    fun clearAllMedicines() {
        allMedicines = emptyList()
        publishVisibleMedicines()
        persistMedicines()
    }

    suspend fun updateMedicine(originalName: String, updatedMedicine: Medicine) {
        val updatedMedicines = allMedicines.toMutableList()
        val medicineIndex = updatedMedicines.indexOfFirst { it.name == originalName }

        if (medicineIndex == -1) return

        updatedMedicines[medicineIndex] = updatedMedicine
        allMedicines = updatedMedicines
        publishVisibleMedicines()
        persistMedicinesAsync(allMedicines)
    }

    suspend fun deleteMedicine(medicineName: String) {
        val updatedMedicines = allMedicines.filterNot { it.name == medicineName }

        if (updatedMedicines.size == allMedicines.size) return

        allMedicines = updatedMedicines
        publishVisibleMedicines()
        persistMedicinesAsync(allMedicines)
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun filterByName(name: String) {
        searchQuery = name
        publishVisibleMedicines()
    }

    fun sortByNone() {
        sortOrder = SortOrder.NONE
        publishVisibleMedicines()
    }

    fun sortByName() {
        sortOrder = SortOrder.NAME
        publishVisibleMedicines()
    }

    fun sortByStock() {
        sortOrder = SortOrder.STOCK
        publishVisibleMedicines()
    }

    fun updateStock(medicineName: String, delta: Int) {
        val updatedMedicines = allMedicines.toMutableList()
        val medicineIndex = updatedMedicines.indexOfFirst { it.name == medicineName }

        if (medicineIndex == -1) return

        val medicine = updatedMedicines[medicineIndex]
        val updatedStock = (medicine.stock + delta).coerceAtLeast(0)

        if (updatedStock == medicine.stock) return

        val direction = if (delta > 0) "increased" else "decreased"
        val history = History(
            medicineName = medicine.name,
            userId = LOCAL_USER_ID,
            date = Date().toString(),
            details = "Stock $direction from ${medicine.stock} to $updatedStock"
        )
        updatedMedicines[medicineIndex] = medicine.copy(
            stock = updatedStock,
            histories = medicine.histories + history
        )
        allMedicines = updatedMedicines
        publishVisibleMedicines()
        persistMedicines()
    }

    fun reload() {
        allMedicines = loadMedicines()
        publishVisibleMedicines()
    }

    private fun publishVisibleMedicines() {
        val normalizedQuery = searchQuery.lowercase(Locale.ROOT)
        val filteredMedicines = allMedicines.filter { medicine ->
            medicine.name.lowercase(Locale.ROOT).contains(normalizedQuery)
        }
        _medicines.value = when (sortOrder) {
            SortOrder.NONE -> filteredMedicines
            SortOrder.NAME -> filteredMedicines.sortedBy { it.name.lowercase(Locale.ROOT) }
            SortOrder.STOCK -> filteredMedicines.sortedBy { it.stock }
        }
    }

    private fun loadMedicines(): List<Medicine> {
        val storedMedicines = preferences.getString(MEDICINES_KEY, null) ?: return emptyList()
        return runCatching {
            val medicinesArray = JSONArray(storedMedicines)
            List(medicinesArray.length()) { index ->
                val medicineObject = medicinesArray.getJSONObject(index)
                val historiesArray = medicineObject.optJSONArray(HISTORIES_KEY)
                val histories = if (historiesArray == null) {
                    emptyList()
                } else {
                    List(historiesArray.length()) { historyIndex ->
                        val historyObject = historiesArray.getJSONObject(historyIndex)
                        History(
                            medicineName = historyObject.getString("medicineName"),
                            userId = historyObject.getString("userId"),
                            date = historyObject.getString("date"),
                            details = historyObject.getString("details")
                        )
                    }
                }
                Medicine(
                    name = medicineObject.getString("name"),
                    stock = medicineObject.getInt("stock"),
                    nameAisle = medicineObject.getString("nameAisle"),
                    histories = histories
                )
            }
        }.getOrElse {
            showError("Impossible de lire les médicaments enregistrés.")
            emptyList()
        }
    }

    private suspend fun persistMedicinesAsync(medicines: List<Medicine>) {
        withContext(Dispatchers.IO) {
            persistMedicines(medicines)
        }
    }

    private fun persistMedicines(medicines: List<Medicine> = allMedicines) {
        val medicinesArray = JSONArray()
        medicines.forEach { medicine ->
            val medicineObject = JSONObject()
                .put("name", medicine.name)
                .put("stock", medicine.stock)
                .put("nameAisle", medicine.nameAisle)

            val historiesArray = JSONArray()
            medicine.histories.forEach { history ->
                historiesArray.put(
                    JSONObject()
                        .put("medicineName", history.medicineName)
                        .put("userId", history.userId)
                        .put("date", history.date)
                        .put("details", history.details)
                )
            }
            medicineObject.put(HISTORIES_KEY, historiesArray)
            medicinesArray.put(medicineObject)
        }

        runCatching {
            preferences.edit {
                putString(MEDICINES_KEY, medicinesArray.toString())
            }
        }.onFailure {
            showError("Impossible d'enregistrer les médicaments.")
        }
    }

    private fun showError(message: String) {
        _errorMessage.value = message
    }

    private companion object {
        const val PREFERENCES_NAME = "rebonnte_preferences"
        const val MEDICINES_KEY = "medicines"
        const val HISTORIES_KEY = "histories"
        const val LOCAL_USER_ID = "local-user"
        const val TEST_MEDICINE_COUNT = 25
    }

    private enum class SortOrder {
        NONE,
        NAME,
        STOCK
    }
}
