package com.openclassrooms.rebonnte.ui.auth

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
        val validationError = AuthCredentialsValidator.validate(email, password)
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validationError)
            return
        }

        _uiState.value = _uiState.value.copy(isSubmitting = true, errorMessage = null)
        val credentials = AuthCredentialsValidator.normalized(email, password)
        request(credentials.email, credentials.password).addOnCompleteListener { task ->
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

}
