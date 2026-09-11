package com.openclassrooms.rebonnte.ui.aisle

import org.junit.Assert.assertEquals
import org.junit.Test

class AisleCatalogTest {
    private val aisles = listOf(Aisle("Zinc"), Aisle("aspirin"), Aisle("Ibuprofen"))

    @Test
    fun `filters case insensitively and sorts visible aisles`() {
        assertEquals(
            listOf("aspirin", "Ibuprofen", "Zinc"),
            AisleCatalog.visibleAisles(aisles, "").map { it.name }
        )
        assertEquals(
            listOf("aspirin"),
            AisleCatalog.visibleAisles(aisles, "SPIR").map { it.name }
        )
    }
}
