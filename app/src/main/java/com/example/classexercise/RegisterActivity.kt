package com.example.classexercise

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.classexercise.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class RegisterActivity : AppCompatActivity() {

    private lateinit var b: ActivityRegisterBinding
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(b.root)

        b.btnRegister.setOnClickListener { register() }
        b.btnGoLogin.setOnClickListener { finish() }   // back to the login screen
    }

    private fun register() {
        b.tvError.visibility = View.GONE
        val email = b.etEmail.text.toString().trim()
        val password = b.etPassword.text.toString()
        val confirm = b.etConfirm.text.toString()

        b.tilEmail.error = emailError(email)
        b.tilPassword.error = passwordError(password)
        b.tilConfirm.error = confirmError(password, confirm)
        if (b.tilEmail.error != null || b.tilPassword.error != null || b.tilConfirm.error != null) return

        b.btnRegister.isEnabled = false
        b.btnRegister.text = "Creating account..."

        lifecycleScope.launch {
            try {
                // Creates the account and signs the user in
                auth.createUserWithEmailAndPassword(email, password).await()
                startActivity(
                    Intent(this@RegisterActivity, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                b.tvError.text = authMessage(e)
                b.tvError.visibility = View.VISIBLE
            } finally {
                b.btnRegister.isEnabled = true
                b.btnRegister.text = "Register"
            }
        }
    }
}