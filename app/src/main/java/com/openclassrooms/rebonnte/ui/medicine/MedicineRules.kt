package com.openclassrooms.rebonnte.ui.medicine

import java.util.Locale

object MedicineRules {
    fun validateFields(name: String, aisle: String, stock: String): String? {
        if (name.isBlank()) return "Le nom du médicament est obligatoire."
        if (aisle.isBlank()) return "Sélectionnez un rayon."

        val stockValue = stock.toIntOrNull()
            ?: return "Le stock doit être un nombre entier."
        return if (stockValue < 0) "Le stock ne peut pas être négatif." else null
    }

    fun adjustedStock(currentStock: Int, delta: Int): Int =
        (currentStock + delta).coerceAtLeast(0)

    fun normalizeName(name: String): String = name.trim().lowercase(Locale.ROOT)

    fun searchTokens(name: String): List<String> {
        val normalizedName = normalizeName(name)
        return buildSet {
            normalizedName.indices.forEach { startIndex ->
                (startIndex + 1..normalizedName.length).forEach { endIndex ->
                    add(normalizedName.substring(startIndex, endIndex))
                }
            }
        }.toList()
    }

    fun updateDetails(original: Medicine, updated: Medicine): String {
        val changes = buildList {
            if (original.name != updated.name) add("Name: ${original.name} -> ${updated.name}")
            if (original.nameAisle != updated.nameAisle) {
                add("Aisle: ${original.nameAisle} -> ${updated.nameAisle}")
            }
            if (original.stock != updated.stock) {
                add("Stock: ${original.stock} -> ${updated.stock}")
            }
        }
        return if (changes.isEmpty()) "No value changed" else changes.joinToString()
    }
}
