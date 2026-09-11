package com.openclassrooms.rebonnte.ui.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthCredentialsValidatorTest {
    @Test
    fun `validation explains every invalid credential`() {
        assertEquals(
            "Saisissez votre adresse e-mail.",
            AuthCredentialsValidator.validate("  ", "password")
        )
        assertEquals(
            "Saisissez une adresse e-mail valide.",
            AuthCredentialsValidator.validate("invalid", "password")
        )
        assertEquals(
            "Le mot de passe doit contenir au moins 6 caracteres.",
            AuthCredentialsValidator.validate("user@example.com", "short")
        )
    }

    @Test
    fun `validation accepts valid credentials and normalizes only the email`() {
        assertNull(AuthCredentialsValidator.validate(" user@example.com ", " secret "))

        assertEquals(
            AuthCredentials("user@example.com", " secret "),
            AuthCredentialsValidator.normalized(" user@example.com ", " secret ")
        )
    }
}
