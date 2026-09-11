package com.openclassrooms.rebonnte.ui.auth

data class AuthCredentials(val email: String, val password: String)

object AuthCredentialsValidator {
    private const val MINIMUM_PASSWORD_LENGTH = 6
    private val emailPattern = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun validate(email: String, password: String): String? = when {
        email.trim().isEmpty() -> "Saisissez votre adresse e-mail."
        !emailPattern.matches(email.trim()) ->
            "Saisissez une adresse e-mail valide."
        password.length < MINIMUM_PASSWORD_LENGTH ->
            "Le mot de passe doit contenir au moins $MINIMUM_PASSWORD_LENGTH caracteres."
        else -> null
    }

    fun normalized(email: String, password: String) = AuthCredentials(
        email = email.trim(),
        password = password
    )
}
