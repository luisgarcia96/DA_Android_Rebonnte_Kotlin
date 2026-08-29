package com.openclassrooms.rebonnte.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AuthUiState(
    val isSubmitting: Boolean = false,
    val isAuthenticated: Boolean = false,
    val userEmail: String? = null,
    val errorMessage: String? = null
)

class AuthViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    init {
        _uiState.value = AuthUiState(
            isAuthenticated = auth.currentUser != null,
            userEmail = auth.currentUser?.email
        )
    }

    fun signIn(email: String, password: String) {
        authenticate(email, password) { normalizedEmail, normalizedPassword ->
            auth.signInWithEmailAndPassword(normalizedEmail, normalizedPassword)
        }
    }

    fun createAccount(email: String, password: String) {
        authenticate(email, password) { normalizedEmail, normalizedPassword ->
            auth.createUserWithEmailAndPassword(normalizedEmail, normalizedPassword)
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun signOut() {
        auth.signOut()
        _uiState.value = AuthUiState()
    }

    private fun authenticate(
        email: String,
        password: String,
        request: (String, String) -> Task<*>
    ) {
        val validationError = validateCredentials(email, password)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validationError)
            return
        }

        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
        request(email.trim(), password).addOnCompleteListener { task ->
            _uiState.value = if (task.isSuccessful) {
                AuthUiState(
                    isAuthenticated = true,
                    userEmail = auth.currentUser?.email
                )
            } else {
                AuthUiState(
                    errorMessage = "Impossible de vous authentifier. Verifiez vos identifiants ou reessayez."
                )
            }
        }
    }

    private fun validateCredentials(email: String, password: String): String? = when {
        email.trim().isEmpty() -> "Saisissez votre adresse e-mail."
        !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() ->
            "Saisissez une adresse e-mail valide."
        password.length < MINIMUM_PASSWORD_LENGTH ->
            "Le mot de passe doit contenir au moins $MINIMUM_PASSWORD_LENGTH caracteres."
        else -> null
    }

    private companion object {
        const val MINIMUM_PASSWORD_LENGTH = 6
    }
}
