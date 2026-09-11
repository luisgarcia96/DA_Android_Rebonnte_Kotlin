package com.openclassrooms.rebonnte

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class   FirebaseStockIntegrationTest {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val testId = UUID.randomUUID().toString()
    private val testEmail = "integration-$testId@example.com"
    private val testPassword = "Integration-$testId"
    private val aisleName = "Integration Aisle $testId"
    private val medicineName = "Integration Medicine $testId"
    private val updatedMedicineName = "Updated Medicine $testId"
    private val aisleDocument = firestore.collection(AISLES_COLLECTION).document("integration-$testId")
    private val medicineDocument = firestore.collection(MEDICINES_COLLECTION).document("integration-$testId")
    private val deletedHistoryDocument = firestore
        .collection(DELETED_HISTORIES_COLLECTION)
        .document("integration-$testId")

    @Before
    fun createTestAccount() {
        runBlocking {
            auth.signOut()
            auth.createUserWithEmailAndPassword(testEmail, testPassword).await()
        }
    }

    @After
    fun clearTestData() {
        runBlocking {
            runCatching { aisleDocument.delete().await() }
            runCatching { medicineDocument.delete().await() }
            runCatching { deletedHistoryDocument.delete().await() }
            runCatching { auth.currentUser?.delete()?.await() }
            auth.signOut()
        }
    }

    @Test
    fun createUpdateAndDeleteMedicinePersistsChanges() = runBlocking {
        aisleDocument.set(mapOf(NAME_FIELD to aisleName)).await()
        medicineDocument.set(
            mapOf(
                NAME_FIELD to medicineName,
                NORMALIZED_NAME_FIELD to medicineName.lowercase(),
                SEARCH_TOKENS_FIELD to listOf("integration", "medicine"),
                STOCK_FIELD to INITIAL_STOCK,
                AISLE_FIELD to aisleName,
                HISTORIES_FIELD to listOf(
                    mapOf(
                        HISTORY_ACTION_FIELD to "Medicine created",
                        HISTORY_DETAILS_FIELD to "Initial stock: $INITIAL_STOCK"
                    )
                )
            )
        ).await()

        val createdMedicine = medicineDocument.get().await()
        assertEquals(medicineName, createdMedicine.getString(NAME_FIELD))
        assertEquals(INITIAL_STOCK.toLong(), createdMedicine.getLong(STOCK_FIELD))
        assertEquals(aisleName, aisleDocument.get().await().getString(NAME_FIELD))

        medicineDocument.update(
            mapOf(
                NAME_FIELD to updatedMedicineName,
                NORMALIZED_NAME_FIELD to updatedMedicineName.lowercase(),
                STOCK_FIELD to UPDATED_STOCK,
                HISTORIES_FIELD to listOf(
                    mapOf(
                        HISTORY_ACTION_FIELD to "Medicine updated",
                        HISTORY_DETAILS_FIELD to "Stock: $INITIAL_STOCK -> $UPDATED_STOCK"
                    )
                )
            )
        ).await()

        val updatedMedicine = medicineDocument.get().await()
        assertEquals(updatedMedicineName, updatedMedicine.getString(NAME_FIELD))
        assertEquals(UPDATED_STOCK.toLong(), updatedMedicine.getLong(STOCK_FIELD))
        assertNotNull(updatedMedicine.get(HISTORIES_FIELD))

        firestore.runBatch { batch ->
            batch.delete(medicineDocument)
            batch.set(
                deletedHistoryDocument,
                mapOf(
                    HISTORY_ACTION_FIELD to "Medicine deleted",
                    HISTORY_DETAILS_FIELD to "Final stock: $UPDATED_STOCK"
                )
            )
        }.await()

        assertFalse(medicineDocument.get().await().exists())
        assertEquals(
            "Medicine deleted",
            deletedHistoryDocument.get().await().getString(HISTORY_ACTION_FIELD)
        )
    }

    private companion object {
        const val AISLES_COLLECTION = "aisles"
        const val AISLE_FIELD = "nameAisle"
        const val DELETED_HISTORIES_COLLECTION = "deletedMedicineHistories"
        const val HISTORIES_FIELD = "histories"
        const val HISTORY_ACTION_FIELD = "action"
        const val HISTORY_DETAILS_FIELD = "details"
        const val INITIAL_STOCK = 8
        const val MEDICINES_COLLECTION = "medicines"
        const val NAME_FIELD = "name"
        const val NORMALIZED_NAME_FIELD = "normalizedName"
        const val SEARCH_TOKENS_FIELD = "searchTokens"
        const val STOCK_FIELD = "stock"
        const val UPDATED_STOCK = 14
    }
}
