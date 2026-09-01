package com.openclassrooms.rebonnte.ui.medicine

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.openclassrooms.rebonnte.ui.aisle.Aisle
import com.openclassrooms.rebonnte.ui.history.History
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

sealed interface MedicineOperationEvent {
    data object Created : MedicineOperationEvent
    data object Updated : MedicineOperationEvent
    data object Deleted : MedicineOperationEvent
    data object Failed : MedicineOperationEvent
}

class MedicineViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private var allMedicines: List<Medicine> = emptyList()
    private val _medicines = MutableStateFlow<List<Medicine>>(emptyList())
    val medicines: StateFlow<List<Medicine>> = _medicines.asStateFlow()
    private val _operationEvents = MutableSharedFlow<MedicineOperationEvent>()
    val operationEvents: SharedFlow<MedicineOperationEvent> = _operationEvents.asSharedFlow()
    private var searchQuery = ""
    private var sortOrder = SortOrder.NONE
    private var hasLoaded = false
    private var isLoading = false

    init {
        reload()
    }

    fun addRandomMedicine(aisles: List<Aisle>) {
        if (aisles.isEmpty()) {
            showError("Ajoutez un rayon avant d'ajouter un médicament.")
            return
        }

        viewModelScope.launch {
            val medicineName = "Medicine ${allMedicines.size + 1}"
            val medicine = Medicine(
                name = medicineName,
                stock = Random().nextInt(100),
                nameAisle = aisles.random().name,
                histories = listOf(createHistory(medicineName, "Medicine created", "Name: $medicineName"))
            )
            saveNewMedicine(medicine)
        }
    }

    fun addMedicine(medicine: Medicine) {
        viewModelScope.launch {
            val medicineWithHistory = medicine.copy(
                histories = medicine.histories + createHistory(
                    medicine.name,
                    "Medicine created",
                    "Aisle: ${medicine.nameAisle}; initial stock: ${medicine.stock}"
                )
            )
            _operationEvents.emit(
                if (saveNewMedicine(medicineWithHistory)) {
                    MedicineOperationEvent.Created
                } else {
                    MedicineOperationEvent.Failed
                }
            )
        }
    }

    fun addTestMedicines(aisleNames: List<String>) {
        if (aisleNames.isEmpty()) {
            showError("Ajoutez un rayon avant de créer les données de test.")
            return
        }

        viewModelScope.launch {
            val firstTestMedicineNumber = allMedicines.size + 1
            val testMedicines = List(TEST_MEDICINE_COUNT) { index ->
                val number = firstTestMedicineNumber + index
                Medicine(
                    name = "Test Medicine $number",
                    stock = number % 100,
                    nameAisle = aisleNames[index % aisleNames.size],
                    histories = listOf(
                        createHistory(
                            "Test Medicine $number",
                            "Medicine created",
                            "Aisle: ${aisleNames[index % aisleNames.size]}; initial stock: ${number % 100}"
                        )
                    )
                )
            }

            runCatching {
                withContext(Dispatchers.IO) {
                    val batch = firestore.batch()
                    testMedicines.forEach { medicine ->
                        batch.set(medicinesCollection.document(medicine.id), medicine.toDocument())
                    }
                    batch.commit().await()
                }
            }.onSuccess {
                allMedicines = allMedicines + testMedicines
                publishVisibleMedicines()
            }.onFailure {
                showError("Impossible de créer les médicaments de test.")
            }
        }
    }

    fun clearAllMedicines() {
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val batch = firestore.batch()
                    medicinesCollection.get().await().documents.forEach { document ->
                        batch.delete(document.reference)
                    }
                    batch.commit().await()
                }
            }.onSuccess {
                allMedicines = emptyList()
                publishVisibleMedicines()
                preferences.edit().putBoolean(MIGRATION_COMPLETE_KEY, true).apply()
            }.onFailure {
                showError("Impossible de supprimer les médicaments de test.")
            }
        }
    }

    fun updateMedicine(originalName: String, updatedMedicine: Medicine) {
        viewModelScope.launch {
            val medicineIndex = allMedicines.indexOfFirst { it.name == originalName }
            if (medicineIndex == -1) {
                _operationEvents.emit(MedicineOperationEvent.Failed)
                return@launch
            }

            val originalMedicine = allMedicines[medicineIndex]
            val medicineWithHistory = updatedMedicine.copy(
                id = originalMedicine.id,
                histories = originalMedicine.histories + createHistory(
                    updatedMedicine.name,
                    "Medicine updated",
                    getUpdateDetails(originalMedicine, updatedMedicine)
                )
            )

            val operationEvent = runCatching {
                saveMedicine(medicineWithHistory)
            }.fold(
                onSuccess = {
                    allMedicines = allMedicines.toMutableList().apply {
                        set(medicineIndex, medicineWithHistory)
                    }
                    publishVisibleMedicines()
                    MedicineOperationEvent.Updated
                },
                onFailure = {
                    showError("Impossible de modifier le médicament.")
                    MedicineOperationEvent.Failed
                }
            )
            _operationEvents.emit(operationEvent)
        }
    }

    fun deleteMedicine(medicineName: String) {
        viewModelScope.launch {
            val deletedMedicine = allMedicines.find { it.name == medicineName }
            if (deletedMedicine == null) {
                _operationEvents.emit(MedicineOperationEvent.Failed)
                return@launch
            }

            val deletionHistory = createHistory(
                deletedMedicine.name,
                "Medicine deleted",
                "Aisle: ${deletedMedicine.nameAisle}; final stock: ${deletedMedicine.stock}"
            )

            val operationEvent = runCatching {
                withContext(Dispatchers.IO) {
                    val batch = firestore.batch()
                    batch.delete(medicinesCollection.document(deletedMedicine.id))
                    batch.set(deletedHistoriesCollection.document(), deletionHistory.toDocument())
                    batch.commit().await()
                }
            }.fold(
                onSuccess = {
                    allMedicines = allMedicines.filterNot { it.id == deletedMedicine.id }
                    publishVisibleMedicines()
                    MedicineOperationEvent.Deleted
                },
                onFailure = {
                    showError("Impossible de supprimer le médicament.")
                    MedicineOperationEvent.Failed
                }
            )
            _operationEvents.emit(operationEvent)
        }
    }

    fun validateMedicineFields(name: String, aisle: String, stock: String): String? {
        return MedicineRules.validateFields(name, aisle, stock)
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
        viewModelScope.launch {
            val medicineIndex = allMedicines.indexOfFirst { it.name == medicineName }
            if (medicineIndex == -1) return@launch

            val medicine = allMedicines[medicineIndex]
            val updatedStock = MedicineRules.adjustedStock(medicine.stock, delta)
            if (updatedStock == medicine.stock) return@launch

            val direction = if (delta > 0) "increased" else "decreased"
            val updatedMedicine = medicine.copy(
                stock = updatedStock,
                histories = medicine.histories + createHistory(
                    medicine.name,
                    "Stock $direction",
                    "Stock: ${medicine.stock} -> $updatedStock"
                )
            )

            runCatching {
                saveMedicine(updatedMedicine)
            }.onSuccess {
                allMedicines = allMedicines.toMutableList().apply {
                    set(medicineIndex, updatedMedicine)
                }
                publishVisibleMedicines()
            }.onFailure {
                showError("Impossible de mettre à jour le stock.")
            }
        }
    }

    fun reload(force: Boolean = false) {
        if (auth.currentUser == null || isLoading || (hasLoaded && !force)) return

        isLoading = true
        viewModelScope.launch {
            try {
                runCatching {
                    withContext(Dispatchers.IO) {
                        ensureSearchFields()
                        val remoteMedicines = medicineQuery().get().await().documents.mapNotNull { document ->
                            document.toMedicine()
                        }
                        when {
                            remoteMedicines.isNotEmpty() -> {
                                preferences.edit().putBoolean(MIGRATION_COMPLETE_KEY, true).apply()
                                remoteMedicines
                            }
                            preferences.getBoolean(MIGRATION_COMPLETE_KEY, false) -> emptyList()
                            else -> migrateLegacyMedicines()
                        }
                    }
                }.onSuccess { loadedMedicines ->
                    hasLoaded = true
                    allMedicines = loadedMedicines
                    publishVisibleMedicines()
                }.onFailure {
                    showError("Impossible de charger les médicaments depuis Firestore.")
                }
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun saveNewMedicine(medicine: Medicine): Boolean = runCatching {
            saveMedicine(medicine)
        }.onSuccess {
            allMedicines = allMedicines + medicine
            publishVisibleMedicines()
        }.onFailure {
            showError("Impossible d'enregistrer le médicament.")
        }.isSuccess

    private suspend fun saveMedicine(medicine: Medicine) {
        withContext(Dispatchers.IO) {
            medicinesCollection.document(medicine.id).set(medicine.toDocument()).await()
        }
    }

    private suspend fun migrateLegacyMedicines(): List<Medicine> {
        val legacyMedicines = loadLegacyMedicines()
        if (legacyMedicines.isNotEmpty()) {
            val batch = firestore.batch()
            legacyMedicines.forEach { medicine ->
                batch.set(medicinesCollection.document(medicine.id), medicine.toDocument())
            }
            batch.commit().await()
        }
        preferences.edit().putBoolean(MIGRATION_COMPLETE_KEY, true).apply()
        return legacyMedicines
    }

    private fun publishVisibleMedicines() {
        val normalizedQuery = searchQuery.normalizeForSearch()
        val filteredMedicines = allMedicines.filter { medicine ->
            normalizedQuery.isBlank() || medicine.name.normalizeForSearch().contains(normalizedQuery)
        }
        _medicines.value = when (sortOrder) {
            SortOrder.NONE -> filteredMedicines
            SortOrder.NAME -> filteredMedicines.sortedBy { it.name.lowercase(Locale.ROOT) }
            SortOrder.STOCK -> filteredMedicines.sortedBy { it.stock }
        }
    }

    private suspend fun ensureSearchFields() {
        val normalizedNamesComplete = preferences.getBoolean(QUERY_MIGRATION_COMPLETE_KEY, false)
        val searchTokensComplete = preferences.getBoolean(SEARCH_TOKENS_MIGRATION_COMPLETE_KEY, false)
        val searchSubstringsComplete = preferences.getBoolean(
            SEARCH_SUBSTRINGS_MIGRATION_COMPLETE_KEY,
            false
        )
        if (normalizedNamesComplete && searchTokensComplete && searchSubstringsComplete) return

        val documents = medicinesCollection.get().await().documents
        val batch = firestore.batch()
        documents.forEach { document ->
            document.getString(NAME_FIELD)?.let { name ->
                if (!normalizedNamesComplete && document.getString(NORMALIZED_NAME_FIELD) == null) {
                    batch.update(document.reference, NORMALIZED_NAME_FIELD, name.normalizeForSearch())
                }
                if (
                    !searchSubstringsComplete ||
                    (!searchTokensComplete && document.get(SEARCH_TOKENS_FIELD) == null)
                ) {
                    batch.update(document.reference, SEARCH_TOKENS_FIELD, MedicineRules.searchTokens(name))
                }
            }
        }
        batch.commit().await()
        preferences.edit()
            .putBoolean(QUERY_MIGRATION_COMPLETE_KEY, true)
            .putBoolean(SEARCH_TOKENS_MIGRATION_COMPLETE_KEY, true)
            .putBoolean(SEARCH_SUBSTRINGS_MIGRATION_COMPLETE_KEY, true)
            .apply()
    }

    private fun medicineQuery(): Query {
        return medicinesCollection.orderBy(NORMALIZED_NAME_FIELD)
    }

    private fun loadLegacyMedicines(): List<Medicine> {
        val storedMedicines = preferences.getString(MEDICINES_KEY, null) ?: return emptyList()
        return runCatching {
            val medicinesArray = JSONArray(storedMedicines)
            List(medicinesArray.length()) { index ->
                val medicineObject = medicinesArray.getJSONObject(index)
                Medicine(
                    name = medicineObject.getString(NAME_FIELD),
                    stock = medicineObject.getInt(STOCK_FIELD),
                    nameAisle = medicineObject.getString(AISLE_FIELD),
                    histories = medicineObject.optJSONArray(HISTORIES_FIELD)?.toHistories().orEmpty()
                )
            }
        }.getOrElse {
            showError("Impossible de lire les médicaments enregistrés localement.")
            emptyList()
        }
    }

    private fun DocumentSnapshot.toMedicine(): Medicine? {
        val name = getString(NAME_FIELD) ?: return null
        val stock = getLong(STOCK_FIELD)?.toInt() ?: return null
        val aisle = getString(AISLE_FIELD) ?: return null
        val histories = (get(HISTORIES_FIELD) as? List<*>)?.mapNotNull { value ->
            (value as? Map<*, *>)?.toHistory(name)
        }.orEmpty()
        return Medicine(name, stock, aisle, histories, id)
    }

    private fun Medicine.toDocument() = mapOf(
        NAME_FIELD to name,
        NORMALIZED_NAME_FIELD to name.normalizeForSearch(),
        SEARCH_TOKENS_FIELD to MedicineRules.searchTokens(name),
        STOCK_FIELD to stock,
        AISLE_FIELD to nameAisle,
        HISTORIES_FIELD to histories.map { it.toDocument() }
    )

    private fun History.toDocument() = mapOf(
        HISTORY_MEDICINE_NAME_FIELD to medicineName,
        HISTORY_USER_ID_FIELD to userId,
        HISTORY_DATE_FIELD to date,
        HISTORY_ACTION_FIELD to action,
        HISTORY_DETAILS_FIELD to details
    )

    private fun Map<*, *>.toHistory(defaultMedicineName: String) = History(
        medicineName = this[HISTORY_MEDICINE_NAME_FIELD] as? String ?: defaultMedicineName,
        userId = this[HISTORY_USER_ID_FIELD] as? String ?: "unknown-user",
        date = this[HISTORY_DATE_FIELD] as? String ?: "",
        action = this[HISTORY_ACTION_FIELD] as? String ?: "Recorded action",
        details = this[HISTORY_DETAILS_FIELD] as? String ?: ""
    )

    private fun getUpdateDetails(original: Medicine, updated: Medicine) =
        MedicineRules.updateDetails(original, updated)

    private fun createHistory(medicineName: String, action: String, details: String) = History(
        medicineName = medicineName,
        userId = auth.currentUser?.email ?: "unknown-user",
        date = HISTORY_DATE_FORMAT.format(Date()),
        action = action,
        details = details
    )

    private fun JSONArray.toHistories(): List<History> = List(length()) { index ->
        val historyObject = getJSONObject(index)
        History(
            medicineName = historyObject.getString(HISTORY_MEDICINE_NAME_FIELD),
            userId = historyObject.getString(HISTORY_USER_ID_FIELD),
            date = historyObject.getString(HISTORY_DATE_FIELD),
            action = historyObject.optString(HISTORY_ACTION_FIELD).ifBlank {
                inferAction(historyObject.getString(HISTORY_DETAILS_FIELD))
            },
            details = historyObject.getString(HISTORY_DETAILS_FIELD)
        )
    }

    private fun inferAction(details: String): String = when {
        details.startsWith("Stock") -> "Stock variation"
        details.startsWith("Medicine created") -> "Medicine created"
        details.startsWith("Medicine updated") -> "Medicine updated"
        details.startsWith("Medicine deleted") -> "Medicine deleted"
        else -> "Recorded action"
    }

    private fun showError(message: String) {
        _errorMessage.value = message
    }

    private fun String.normalizeForSearch() = MedicineRules.normalizeName(this)

    private val medicinesCollection
        get() = firestore.collection(MEDICINES_COLLECTION)

    private val deletedHistoriesCollection
        get() = firestore.collection(DELETED_HISTORIES_COLLECTION)

    private companion object {
        const val AISLE_FIELD = "nameAisle"
        const val DELETED_HISTORIES_COLLECTION = "deletedMedicineHistories"
        const val HISTORIES_FIELD = "histories"
        const val HISTORY_ACTION_FIELD = "action"
        const val HISTORY_DATE_FIELD = "date"
        const val HISTORY_DETAILS_FIELD = "details"
        const val HISTORY_MEDICINE_NAME_FIELD = "medicineName"
        const val HISTORY_USER_ID_FIELD = "userId"
        val HISTORY_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        const val MEDICINES_COLLECTION = "medicines"
        const val MEDICINES_KEY = "medicines"
        const val MIGRATION_COMPLETE_KEY = "firestore_medicines_migration_complete"
        const val NAME_FIELD = "name"
        const val NORMALIZED_NAME_FIELD = "normalizedName"
        const val PREFERENCES_NAME = "rebonnte_preferences"
        const val STOCK_FIELD = "stock"
        const val TEST_MEDICINE_COUNT = 25
        const val QUERY_MIGRATION_COMPLETE_KEY = "firestore_medicines_query_migration_complete"
        const val SEARCH_SUBSTRINGS_MIGRATION_COMPLETE_KEY =
            "firestore_medicines_search_substrings_migration_complete"
        const val SEARCH_TOKENS_FIELD = "searchTokens"
        const val SEARCH_TOKENS_MIGRATION_COMPLETE_KEY = "firestore_medicines_search_tokens_migration_complete"
    }

    private enum class SortOrder {
        NONE,
        NAME,
        STOCK
    }
}
