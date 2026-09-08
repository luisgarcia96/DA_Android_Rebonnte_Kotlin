package com.openclassrooms.rebonnte.ui.medicine

import java.util.Locale

enum class MedicineSortOrder {
    NONE,
    NAME,
    STOCK
}

object MedicineCatalog {
    fun visibleMedicines(
        medicines: List<Medicine>,
        query: String,
        sortOrder: MedicineSortOrder
    ): List<Medicine> {
        val normalizedQuery = MedicineRules.normalizeName(query)
        val filteredMedicines = medicines.filter { medicine ->
            normalizedQuery.isBlank() ||
                MedicineRules.normalizeName(medicine.name).contains(normalizedQuery)
        }
        return when (sortOrder) {
            MedicineSortOrder.NONE -> filteredMedicines
            MedicineSortOrder.NAME -> filteredMedicines.sortedBy { it.name.lowercase(Locale.ROOT) }
            MedicineSortOrder.STOCK -> filteredMedicines.sortedBy { it.stock }
        }
    }
}
