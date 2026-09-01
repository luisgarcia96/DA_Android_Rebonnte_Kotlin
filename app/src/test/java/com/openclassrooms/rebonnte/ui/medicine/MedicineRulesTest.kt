package com.openclassrooms.rebonnte.ui.medicine

import com.openclassrooms.rebonnte.ui.history.History
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicineRulesTest {
    @Test
    fun `validation rejects missing name aisle and invalid stock`() {
        assertEquals(
            "Le nom du médicament est obligatoire.",
            MedicineRules.validateFields("", "Cold storage", "10")
        )
        assertEquals(
            "Sélectionnez un rayon.",
            MedicineRules.validateFields("Aspirin", "", "10")
        )
        assertEquals(
            "Le stock doit être un nombre entier.",
            MedicineRules.validateFields("Aspirin", "Cold storage", "ten")
        )
        assertEquals(
            "Le stock ne peut pas être négatif.",
            MedicineRules.validateFields("Aspirin", "Cold storage", "-1")
        )
    }

    @Test
    fun `validation accepts a complete medicine`() {
        assertEquals(null, MedicineRules.validateFields("Aspirin", "Cold storage", "0"))
    }

    @Test
    fun `stock never becomes negative`() {
        assertEquals(7, MedicineRules.adjustedStock(5, 2))
        assertEquals(0, MedicineRules.adjustedStock(1, -5))
    }

    @Test
    fun `search tokens support case insensitive partial searches`() {
        val tokens = MedicineRules.searchTokens("  Test Medicine 13 ")

        assertTrue("icine" in tokens)
        assertTrue("13" in tokens)
        assertTrue("3" in tokens)
        assertTrue("test medicine" in tokens)
    }

    @Test
    fun `history details list every changed medicine field`() {
        val original = medicine(name = "Aspirin", aisle = "A1", stock = 10)
        val updated = medicine(name = "Ibuprofen", aisle = "B2", stock = 4)

        assertEquals(
            "Name: Aspirin -> Ibuprofen, Aisle: A1 -> B2, Stock: 10 -> 4",
            MedicineRules.updateDetails(original, updated)
        )
    }

    @Test
    fun `history details identifies an unchanged medicine`() {
        val medicine = medicine(name = "Aspirin", aisle = "A1", stock = 10)

        assertEquals("No value changed", MedicineRules.updateDetails(medicine, medicine.copy()))
    }

    private fun medicine(name: String, aisle: String, stock: Int) = Medicine(
        name = name,
        stock = stock,
        nameAisle = aisle,
        histories = listOf(History(name, "user@example.com", "2026-09-02", "Created", ""))
    )
}
