package com.openclassrooms.rebonnte.ui.medicine

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import com.openclassrooms.rebonnte.ui.aisle.Aisle
import com.openclassrooms.rebonnte.ui.history.History
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
        _medicines.value = allMedicines
        persistMedicines()
    }

    fun addMedicine(medicine: Medicine) {
        allMedicines = allMedicines + medicine
        _medicines.value = allMedicines
        persistMedicines()
    }

    fun updateMedicine(originalName: String, updatedMedicine: Medicine) {
        val updatedMedicines = allMedicines.toMutableList()
        val medicineIndex = updatedMedicines.indexOfFirst { it.name == originalName }

        if (medicineIndex == -1) return

        updatedMedicines[medicineIndex] = updatedMedicine
        allMedicines = updatedMedicines
        _medicines.value = allMedicines
        persistMedicines()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun filterByName(name: String) {
        val normalizedName = name.lowercase(Locale.getDefault())
        _medicines.value = allMedicines.filter { medicine ->
            medicine.name.lowercase(Locale.getDefault()).contains(normalizedName)
        }
    }

    fun sortByNone() {
        _medicines.value = allMedicines
    }

    fun sortByName() {
        allMedicines = allMedicines.sortedBy { it.name }
        _medicines.value = allMedicines
    }

    fun sortByStock() {
        allMedicines = allMedicines.sortedBy { it.stock }
        _medicines.value = allMedicines
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
        _medicines.value = medicines.value.map { currentMedicine ->
            allMedicines.first { it.name == currentMedicine.name }
        }
        persistMedicines()
    }

    fun reload() {
        allMedicines = loadMedicines()
        _medicines.value = allMedicines
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

    private fun persistMedicines() {
        val medicinesArray = JSONArray()
        allMedicines.forEach { medicine ->
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
    }
}
