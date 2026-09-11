package com.openclassrooms.rebonnte.ui.aisle

import java.util.Locale

object AisleCatalog {
    fun visibleAisles(aisles: List<Aisle>, query: String): List<Aisle> {
        val normalizedQuery = query.lowercase(Locale.ROOT)
        return aisles
            .filter { aisle -> aisle.name.lowercase(Locale.ROOT).contains(normalizedQuery) }
            .sortedBy { it.name.lowercase(Locale.ROOT) }
    }
}
