package com.openclassrooms.rebonnte.ui.aisle

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

class AisleViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _aisles = MutableStateFlow(loadAisles())
    val aisles: StateFlow<List<Aisle>> = _aisles.asStateFlow()

    fun addRandomAisle() {
        val currentAisles: MutableList<Aisle> = ArrayList(aisles.value)
        currentAisles.add(Aisle("Aisle " + (currentAisles.size + 1)))
        _aisles.value = currentAisles
        persistAisles()
    }

    fun clearAllAisles() {
        _aisles.value = defaultAisles()
        persistAisles()
    }

    private fun loadAisles(): List<Aisle> {
        val storedAisles = preferences.getString(AISLES_KEY, null) ?: return defaultAisles()
        return runCatching {
            val aislesArray = JSONArray(storedAisles)
            List(aislesArray.length()) { index ->
                Aisle(aislesArray.getString(index))
            }
        }.getOrElse { defaultAisles() }
    }

    private fun persistAisles() {
        val aislesArray = JSONArray()
        aisles.value.forEach { aisle -> aislesArray.put(aisle.name) }
        preferences.edit { putString(AISLES_KEY, aislesArray.toString()) }
    }

    private fun defaultAisles(): List<Aisle> = listOf(Aisle("Main Aisle"))

    private companion object {
        const val PREFERENCES_NAME = "rebonnte_preferences"
        const val AISLES_KEY = "aisles"
    }
}
