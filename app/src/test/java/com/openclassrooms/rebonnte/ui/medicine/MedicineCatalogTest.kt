package com.openclassrooms.rebonnte.ui.medicine

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicineCatalogTest {
    private val medicines = listOf(
        medicine("Zinc", 3),
        medicine("aspirin", 12),
        medicine("Ibuprofen", 1)
    )

    @Test
    fun `filters names without case or surrounding-space sensitivity`() {
        assertEquals(
            listOf("aspirin"),
            MedicineCatalog.visibleMedicines(medicines, "  SPIR ", MedicineSortOrder.NONE)
                .map { it.name }
        )
    }

    @Test
    fun `keeps input order when no sort is requested`() {
        assertEquals(
            listOf("Zinc", "aspirin", "Ibuprofen"),
            MedicineCatalog.visibleMedicines(medicines, "", MedicineSortOrder.NONE).map { it.name }
        )
    }

    @Test
    fun `sorts by normalized name or stock`() {
        assertEquals(
            listOf("aspirin", "Ibuprofen", "Zinc"),
            MedicineCatalog.visibleMedicines(medicines, "", MedicineSortOrder.NAME).map { it.name }
        )
        assertEquals(
            listOf("Ibuprofen", "Zinc", "aspirin"),
            MedicineCatalog.visibleMedicines(medicines, "", MedicineSortOrder.STOCK).map { it.name }
        )
    }

    private fun medicine(name: String, stock: Int) = Medicine(name, stock, "A1", emptyList())
}
