package com.openclassrooms.rebonnte.ui.aisle

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

class AisleViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private val _aisles = MutableStateFlow<List<Aisle>>(emptyList())
    val aisles: StateFlow<List<Aisle>> = _aisles.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private var allAisles: List<Aisle> = emptyList()
    private var searchQuery = ""
    private var hasLoaded = false
    private var isLoading = false

    init {
        reload()
    }

    fun addRandomAisle() {
        viewModelScope.launch {
            val aisle = Aisle("Aisle ${allAisles.size + 1}")
            runCatching {
                saveAisle(aisle)
            }.onSuccess {
                allAisles = allAisles + aisle
                publishVisibleAisles()
            }.onFailure {
                showError("Impossible d'enregistrer le rayon.")
            }
        }
    }

    fun addTestAisles(count: Int, onSuccess: (List<String>) -> Unit) {
        viewModelScope.launch {
            val firstAisleNumber = allAisles.size + 1
            val newAisles = List(count) { index ->
                Aisle("Aisle ${firstAisleNumber + index}")
            }

            runCatching {
                val batch = firestore.batch()
                newAisles.forEach { aisle ->
                    batch.set(aislesCollection.document(aisle.id), aisle.toDocument())
                }
                batch.commit().await()
            }.onSuccess {
                allAisles = allAisles + newAisles
                publishVisibleAisles()
                onSuccess(allAisles.map { it.name })
            }.onFailure {
                showError("Impossible de créer les rayons de test.")
            }
        }
    }

    fun clearAllAisles() {
        viewModelScope.launch {
            val defaultAisle = defaultAisles().single()
            runCatching {
                val batch = firestore.batch()
                aislesCollection.get().await().documents.forEach { document ->
                    if (document.id != defaultAisle.id) {
                        batch.delete(document.reference)
                    }
                }
                batch.set(aislesCollection.document(defaultAisle.id), defaultAisle.toDocument())
                batch.commit().await()
            }.onSuccess {
                allAisles = listOf(defaultAisle)
                publishVisibleAisles()
                preferences.edit().putBoolean(MIGRATION_COMPLETE_KEY, true).apply()
            }.onFailure {
                showError("Impossible de supprimer les rayons de test.")
            }
        }
    }

    fun reload(force: Boolean = false) {
        if (auth.currentUser == null || isLoading || (hasLoaded && !force)) return

        isLoading = true
        viewModelScope.launch {
            try {
                runCatching {
                    val remoteAisles = aislesCollection.get().await().documents.mapNotNull { document ->
                        document.getString(NAME_FIELD)?.let { name -> Aisle(name, document.id) }
                    }
                    when {
                        remoteAisles.isNotEmpty() -> {
                            preferences.edit().putBoolean(MIGRATION_COMPLETE_KEY, true).apply()
                            remoteAisles
                        }
                        preferences.getBoolean(MIGRATION_COMPLETE_KEY, false) -> emptyList()
                        else -> migrateLegacyAisles()
                    }
                }.onSuccess { loadedAisles ->
                    hasLoaded = true
                    allAisles = loadedAisles
                    publishVisibleAisles()
                }.onFailure {
                    showError("Impossible de charger les rayons depuis Firestore.")
                }
            } finally {
                isLoading = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun filterByName(name: String) {
        searchQuery = name
        publishVisibleAisles()
    }

    private fun publishVisibleAisles() {
        _aisles.value = AisleCatalog.visibleAisles(allAisles, searchQuery)
    }

    private suspend fun migrateLegacyAisles(): List<Aisle> {
        val legacyAisles = loadLegacyAisles()
        val batch = firestore.batch()
        legacyAisles.forEach { aisle ->
            batch.set(aislesCollection.document(aisle.id), aisle.toDocument())
        }
        batch.commit().await()
        preferences.edit().putBoolean(MIGRATION_COMPLETE_KEY, true).apply()
        return legacyAisles
    }

    private suspend fun saveAisle(aisle: Aisle) {
        aislesCollection.document(aisle.id).set(aisle.toDocument()).await()
    }

    private fun loadLegacyAisles(): List<Aisle> {
        val storedAisles = preferences.getString(AISLES_KEY, null) ?: return defaultAisles()
        return runCatching {
            val aislesArray = JSONArray(storedAisles)
            List(aislesArray.length()) { index -> Aisle(aislesArray.getString(index)) }
        }.getOrElse {
            defaultAisles()
        }
    }

    private fun defaultAisles(): List<Aisle> = listOf(Aisle("Main Aisle", DEFAULT_AISLE_ID))

    private fun Aisle.toDocument() = mapOf(NAME_FIELD to name)

    private fun showError(message: String) {
        _errorMessage.value = message
    }

    private val aislesCollection
        get() = firestore.collection(AISLES_COLLECTION)

    private companion object {
        const val AISLES_COLLECTION = "aisles"
        const val AISLES_KEY = "aisles"
        const val DEFAULT_AISLE_ID = "main-aisle"
        const val MIGRATION_COMPLETE_KEY = "firestore_aisles_migration_complete"
        const val NAME_FIELD = "name"
        const val PREFERENCES_NAME = "rebonnte_preferences"
    }
}
