package com.openclassrooms.rebonnte

import android.util.Log
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.compose.ui.semantics.SemanticsProperties
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StockUiIntegrationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val testId = UUID.randomUUID().toString()
    private val testEmail = "ui-integration-$testId@example.com"
    private val testPassword = "UiIntegration-$testId"
    private val medicineName = "UI Medicine $testId"
    private var addedAisleName: String? = null
    private var accountWasCreated = false

    @Before
    fun signOutExistingUser() {
        auth.signOut()
    }

    @After
    fun clearTestData() {
        runBlocking(Dispatchers.IO) {
            withTimeoutOrNull(CLEANUP_TIMEOUT_MS) {
                if (accountWasCreated) {
                    auth.signInWithEmailAndPassword(testEmail, testPassword).await()
                    addedAisleName?.let { deleteDocuments(AISLES_COLLECTION, it) }
                    deleteDocuments(MEDICINES_COLLECTION, medicineName)
                    auth.currentUser?.delete()?.await()
                }
            }
            auth.signOut()
        }
    }

    @Test
    fun createAccountAddAisleAddMedicineAndSignOut() {
        logStep("Waiting for the authentication screen")
        signOutFromInterfaceIfNeeded()

        logStep("Creating the test account")
        composeRule.onNodeWithText("Creer un compte").performClick()
        composeRule.onNodeWithTag("auth_email").performTextInput(testEmail)
        composeRule.onNodeWithTag("auth_password").performTextInput(testPassword)
        composeRule.onNodeWithTag("auth_submit").performClick()
        waitForText("Aisle")
        accountWasCreated = true

        logStep("Signing out and signing back in")
        composeRule.onNodeWithContentDescription("Se deconnecter").performClick()
        composeRule.onNodeWithText("Connexion").assertIsDisplayed()
        composeRule.onNodeWithTag("auth_email").performTextInput(testEmail)
        composeRule.onNodeWithTag("auth_password").performTextInput(testPassword)
        composeRule.onNodeWithTag("auth_submit").performClick()
        waitForText("Aisle")

        logStep("Adding an aisle")
        val initialAisleNames = displayedAisleNames()
        composeRule.onNodeWithContentDescription("Add aisle").performClick()
        composeRule.waitUntil(TEST_TIMEOUT_MS) {
            displayedAisleNames().size > initialAisleNames.size
        }
        addedAisleName = displayedAisleNames().first { it !in initialAisleNames }

        logStep("Adding a medicine")
        composeRule.onNodeWithText("Medicine").performClick()
        composeRule.onNodeWithContentDescription("Add medicine").performClick()
        waitForText("New medicine")
        waitForText(addedAisleName.orEmpty())

        composeRule.onNodeWithTag("new_medicine_name").performTextInput(medicineName)
        composeRule.onNodeWithTag("new_medicine_stock").performTextClearance()
        composeRule.onNodeWithTag("new_medicine_stock").performTextInput("12")
        composeRule.onNodeWithTag("new_medicine_save").performClick()
        waitForDeviceObject(By.desc("Se deconnecter"))
    }

    private fun signOutFromInterfaceIfNeeded() {
        composeRule.waitUntil(TEST_TIMEOUT_MS) {
            composeRule.onAllNodesWithText("Connexion").fetchSemanticsNodes().isNotEmpty() ||
                composeRule
                    .onAllNodesWithContentDescription("Se deconnecter")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
        }
        if (
            composeRule
                .onAllNodesWithContentDescription("Se deconnecter")
                .fetchSemanticsNodes()
                .isNotEmpty()
        ) {
            composeRule.onNodeWithContentDescription("Se deconnecter").performClick()
        }
        composeRule.onNodeWithText("Connexion").assertIsDisplayed()
    }

    private fun logStep(step: String) {
        Log.i(LOG_TAG, step)
    }

    private fun waitForText(text: String) {
        composeRule.waitUntil(TEST_TIMEOUT_MS) {
            composeRule.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForDeviceObject(selector: BySelector) {
        assertNotNull(device.wait(Until.findObject(selector), TEST_TIMEOUT_MS))
    }

    private fun displayedAisleNames(): List<String> = composeRule
        .onAllNodes(hasContentDescription("Open aisle", substring = true))
        .fetchSemanticsNodes()
        .map { node ->
            node.config[SemanticsProperties.ContentDescription]
                .first()
                .removePrefix("Open aisle ")
        }

    private suspend fun deleteDocuments(collection: String, name: String) {
        firestore.collection(collection)
            .whereEqualTo(NAME_FIELD, name)
            .get()
            .await()
            .documents
            .forEach { it.reference.delete().await() }
    }

    private companion object {
        const val AISLES_COLLECTION = "aisles"
        const val MEDICINES_COLLECTION = "medicines"
        const val NAME_FIELD = "name"
        const val TEST_TIMEOUT_MS = 15_000L
        const val CLEANUP_TIMEOUT_MS = 20_000L
        const val LOG_TAG = "StockUiIntegrationTest"
    }
}
