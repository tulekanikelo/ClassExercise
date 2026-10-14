package com.example.classexercise

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.classexercise.databinding.ActivityLoginBinding
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : AppCompatActivity() {

    private lateinit var b: ActivityLoginBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnLogin.setOnClickListener { login() }
        b.btnForgot.setOnClickListener { resetPassword() }
        b.btnGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    override fun onStart() {
        super.onStart()
        // Already signed in? Skip straight to the app.
        if (auth.currentUser != null) goToMain()
    }

    private fun login() {
        b.tvError.visibility = View.GONE
        val email = b.etEmail.text.toString().trim()
        val password = b.etPassword.text.toString()

        b.tilEmail.error = emailError(email)
        b.tilPassword.error = passwordError(password)
        if (b.tilEmail.error != null || b.tilPassword.error != null) return

        setLoading(true)
        lifecycleScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                goToMain()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                b.tvError.text = authMessage(e)
                b.tvError.visibility = View.VISIBLE
            } finally {
                setLoading(false)
            }
        }
    }

    private fun resetPassword() {
        val email = b.etEmail.text.toString().trim()
        b.tilEmail.error = emailError(email)
        if (b.tilEmail.error != null) return

        lifecycleScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                Snackbar.make(b.root, "If that account exists, a reset email was sent.", Snackbar.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                b.tvError.text = authMessage(e)
                b.tvError.visibility = View.VISIBLE
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        b.btnLogin.isEnabled = !loading
        b.btnLogin.text = if (loading) "Logging in..." else "Log in"
    }

    private fun goToMain() {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
    }
}