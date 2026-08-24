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
    private val _medicines = MutableStateFlow(loadMedicines())
    val medicines: StateFlow<List<Medicine>> = _medicines.asStateFlow()

    fun addRandomMedicine(aisles: List<Aisle>) {
        val currentMedicines = ArrayList(medicines.value)
        currentMedicines.add(
            Medicine(
                "Medicine " + (currentMedicines.size + 1),
                Random().nextInt(100),
                aisles[Random().nextInt(aisles.size)].name,
                emptyList()
            )
        )
        _medicines.value = currentMedicines
        persistMedicines()
    }

    fun filterByName(name: String) {
        val currentMedicines: List<Medicine> = medicines.value
        val filteredMedicines: MutableList<Medicine> = ArrayList()
        for (medicine in currentMedicines) {
            if (medicine.name.lowercase(Locale.getDefault())
                    .contains(name.lowercase(Locale.getDefault()))
            ) {
                filteredMedicines.add(medicine)
            }
        }
        _medicines.value = filteredMedicines
    }

    fun sortByNone() {
        _medicines.value = medicines.value.toMutableList() // Pas de tri
    }

    fun sortByName() {
        val currentMedicines = ArrayList(medicines.value)
        currentMedicines.sortWith(Comparator.comparing(Medicine::name))
        _medicines.value = currentMedicines
    }

    fun sortByStock() {
        val currentMedicines = ArrayList(medicines.value)
        currentMedicines.sortWith(Comparator.comparingInt(Medicine::stock))
        _medicines.value = currentMedicines
    }

    fun updateStock(medicineName: String, delta: Int) {
        val currentMedicines = medicines.value.toMutableList()
        val medicineIndex = currentMedicines.indexOfFirst { it.name == medicineName }

        if (medicineIndex == -1) return

        val medicine = currentMedicines[medicineIndex]
        val updatedStock = (medicine.stock + delta).coerceAtLeast(0)

        if (updatedStock == medicine.stock) return

        val direction = if (delta > 0) "increased" else "decreased"
        val history = History(
            medicineName = medicine.name,
            userId = LOCAL_USER_ID,
            date = Date().toString(),
            details = "Stock $direction from ${medicine.stock} to $updatedStock"
        )
        currentMedicines[medicineIndex] = medicine.copy(
            stock = updatedStock,
            histories = medicine.histories + history
        )
        _medicines.value = currentMedicines
        persistMedicines()
    }

    fun reload() {
        _medicines.value = loadMedicines()
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
        }.getOrElse { emptyList() }
    }

    private fun persistMedicines() {
        val medicinesArray = JSONArray()
        medicines.value.forEach { medicine ->
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

        preferences.edit {
          putString(MEDICINES_KEY, medicinesArray.toString())
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "rebonnte_preferences"
        const val MEDICINES_KEY = "medicines"
        const val HISTORIES_KEY = "histories"
        const val LOCAL_USER_ID = "local-user"
    }
}
