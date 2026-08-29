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
    private var deletedMedicineHistories = loadDeletedMedicineHistories()
    private var searchQuery = ""
    private var sortOrder = SortOrder.NONE

    fun addRandomMedicine(aisles: List<Aisle>) {
        if (aisles.isEmpty()) {
            showError("Ajoutez un rayon avant d'ajouter un médicament.")
            return
        }

        val updatedMedicines = ArrayList(allMedicines)
        val medicineName = "Medicine " + (updatedMedicines.size + 1)
        updatedMedicines.add(
            Medicine(
                medicineName,
                Random().nextInt(100),
                aisles[Random().nextInt(aisles.size)].name,
                listOf(createHistory(medicineName, "Medicine created"))
            )
        )
        allMedicines = updatedMedicines
        publishVisibleMedicines()
        persistMedicines()
    }

    suspend fun addMedicine(medicine: Medicine) {
        val medicineWithHistory = medicine.copy(
            histories = medicine.histories + createHistory(medicine.name, "Medicine created")
        )
        allMedicines = allMedicines + medicineWithHistory
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
                histories = listOf(createHistory("Test Medicine $number", "Medicine created"))
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

        val originalMedicine = updatedMedicines[medicineIndex]
        updatedMedicines[medicineIndex] = updatedMedicine.copy(
            histories = originalMedicine.histories + createHistory(
                updatedMedicine.name,
                getUpdateDetails(originalMedicine, updatedMedicine)
            )
        )
        allMedicines = updatedMedicines
        publishVisibleMedicines()
        persistMedicinesAsync(allMedicines)
    }

    suspend fun deleteMedicine(medicineName: String) {
        val deletedMedicine = allMedicines.find { it.name == medicineName } ?: return
        val updatedMedicines = allMedicines.filterNot { it.name == medicineName }

        allMedicines = updatedMedicines
        deletedMedicineHistories = deletedMedicineHistories + createHistory(
            deletedMedicine.name,
            "Medicine deleted from ${deletedMedicine.nameAisle} with stock ${deletedMedicine.stock}"
        )
        publishVisibleMedicines()
        persistMedicinesAsync(allMedicines)
        persistDeletedMedicineHistoriesAsync()
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
        val history = createHistory(
            medicine.name,
            "Stock $direction from ${medicine.stock} to $updatedStock"
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
                val histories = historiesArray?.toHistories().orEmpty()
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

    private suspend fun persistDeletedMedicineHistoriesAsync() {
        val histories = deletedMedicineHistories
        withContext(Dispatchers.IO) {
            persistDeletedMedicineHistories(histories)
        }
    }

    private fun persistMedicines(medicines: List<Medicine> = allMedicines) {
        val medicinesArray = JSONArray()
        medicines.forEach { medicine ->
            val medicineObject = JSONObject()
                .put("name", medicine.name)
                .put("stock", medicine.stock)
                .put("nameAisle", medicine.nameAisle)

            medicineObject.put(HISTORIES_KEY, medicine.histories.toJsonArray())
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

    private fun loadDeletedMedicineHistories(): List<History> {
        val storedHistories = preferences.getString(DELETED_MEDICINE_HISTORIES_KEY, null)
            ?: return emptyList()
        return runCatching {
            JSONArray(storedHistories).toHistories()
        }.getOrElse {
            showError("Impossible de lire l'historique des suppressions.")
            emptyList()
        }
    }

    private fun persistDeletedMedicineHistories(histories: List<History>) {
        runCatching {
            preferences.edit {
                putString(DELETED_MEDICINE_HISTORIES_KEY, histories.toJsonArray().toString())
            }
        }.onFailure {
            showError("Impossible d'enregistrer l'historique des suppressions.")
        }
    }

    private fun getUpdateDetails(original: Medicine, updated: Medicine): String {
        val changes = buildList {
            if (original.name != updated.name) add("name changed from ${original.name} to ${updated.name}")
            if (original.nameAisle != updated.nameAisle) {
                add("aisle changed from ${original.nameAisle} to ${updated.nameAisle}")
            }
            if (original.stock != updated.stock) {
                add("stock changed from ${original.stock} to ${updated.stock}")
            }
        }
        return if (changes.isEmpty()) "Medicine updated" else "Medicine updated: ${changes.joinToString()}"
    }

    private fun createHistory(medicineName: String, details: String) = History(
        medicineName = medicineName,
        userId = LOCAL_USER_ID,
        date = Date().toString(),
        details = details
    )

    private fun JSONArray.toHistories(): List<History> = List(length()) { index ->
        val historyObject = getJSONObject(index)
        History(
            medicineName = historyObject.getString("medicineName"),
            userId = historyObject.getString("userId"),
            date = historyObject.getString("date"),
            details = historyObject.getString("details")
        )
    }

    private fun List<History>.toJsonArray() = JSONArray().also { historiesArray ->
        forEach { history ->
            historiesArray.put(
                JSONObject()
                    .put("medicineName", history.medicineName)
                    .put("userId", history.userId)
                    .put("date", history.date)
                    .put("details", history.details)
            )
        }
    }

    private fun showError(message: String) {
        _errorMessage.value = message
    }

    private companion object {
        const val PREFERENCES_NAME = "rebonnte_preferences"
        const val MEDICINES_KEY = "medicines"
        const val HISTORIES_KEY = "histories"
        const val DELETED_MEDICINE_HISTORIES_KEY = "deleted_medicine_histories"
        const val LOCAL_USER_ID = "local-user"
        const val TEST_MEDICINE_COUNT = 25
    }

    private enum class SortOrder {
        NONE,
        NAME,
        STOCK
    }
}
