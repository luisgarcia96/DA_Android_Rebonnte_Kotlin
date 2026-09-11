package com.openclassrooms.rebonnte.ui.history

object HistoryRules {
    fun inferredAction(details: String): String = when {
        details.startsWith("Stock") -> "Stock variation"
        details.startsWith("Medicine created") -> "Medicine created"
        details.startsWith("Medicine updated") -> "Medicine updated"
        details.startsWith("Medicine deleted") -> "Medicine deleted"
        else -> "Recorded action"
    }
}
