package com.example.classexercise

import android.util.Patterns
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

fun emailError(email: String): String? = when {
    email.isBlank() -> "Email is required"
    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address"
    else -> null
}

fun passwordError(password: String): String? = when {
    password.isBlank() -> "Password is required"
    password.length < 6 -> "Password must be at least 6 characters"
    else -> null
}

fun confirmError(password: String, confirm: String): String? =
    if (confirm != password) "Passwords do not match" else null

/** Turns Firebase exceptions into messages a user can understand. */
fun authMessage(e: Exception): String = when (e) {
    is FirebaseAuthWeakPasswordException -> "Password is too weak. Use at least 6 characters"
    is FirebaseAuthUserCollisionException -> "An account with this email already exists"
    is FirebaseAuthInvalidCredentialsException,
    is FirebaseAuthInvalidUserException -> "Incorrect email or password"
    is FirebaseNetworkException -> "No internet connection"
    else -> e.message ?: "Something went wrong"
}