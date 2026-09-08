package com.openclassrooms.rebonnte.ui.history

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryRulesTest {
    @Test
    fun `infers known and fallback history actions`() {
        assertEquals("Stock variation", HistoryRules.inferredAction("Stock: 1 -> 2"))
        assertEquals("Medicine created", HistoryRules.inferredAction("Medicine created"))
        assertEquals("Medicine updated", HistoryRules.inferredAction("Medicine updated"))
        assertEquals("Medicine deleted", HistoryRules.inferredAction("Medicine deleted"))
        assertEquals("Recorded action", HistoryRules.inferredAction("Imported"))
    }
}
