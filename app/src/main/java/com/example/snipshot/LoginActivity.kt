package com.example.snipshot

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.snipshot.api.ApiClient
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailInput = findViewById<EditText>(R.id.email_input)
        val passwordInput = findViewById<EditText>(R.id.password_input)
        val loginButton = findViewById<Button>(R.id.btn_login)
        val registerLink = findViewById<TextView>(R.id.tv_register_link)
        val forgotPassword = findViewById<TextView>(R.id.tv_forgot_password)
        val progressBar = findViewById<ProgressBar>(R.id.progress_bar)

        loginButton.setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            progressBar.visibility = View.VISIBLE
            loginButton.isEnabled = false

            lifecycleScope.launch {
                val result = ApiClient.login(email, password)
                progressBar.visibility = View.GONE
                loginButton.isEnabled = true

                if (result.isSuccess) {
                    Toast.makeText(this@LoginActivity, "Login successful", Toast.LENGTH_SHORT).show()
                    finish() // Return to dashboard
                } else {
                    Toast.makeText(this@LoginActivity, result.exceptionOrNull()?.message ?: "Login failed", Toast.LENGTH_LONG).show()
                }
            }
        }

        forgotPassword.setOnClickListener {
            val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_forgot_password, null)
            val resetEmailInput = dialogView.findViewById<TextInputEditText>(R.id.reset_email_input)
            val resetEmailInputLayout = dialogView.findViewById<TextInputLayout>(R.id.reset_email_input_layout)

            // Pre-populate if the email input already has a valid value
            val currentEmail = emailInput.text.toString().trim()
            if (currentEmail.isNotEmpty() && android.util.Patterns.EMAIL_ADDRESS.matcher(currentEmail).matches()) {
                resetEmailInput.setText(currentEmail)
            }

            val dialog = AlertDialog.Builder(this)
                .setTitle("Reset Password")
                .setView(dialogView)
                .setPositiveButton("Send Reset Link", null)
                .setNegativeButton("Cancel", null)
                .create()

            dialog.show()

            val sendButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            sendButton.setOnClickListener {
                val email = resetEmailInput.text.toString().trim()
                if (email.isEmpty()) {
                    resetEmailInputLayout.error = "Email cannot be empty"
                    return@setOnClickListener
                }
                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    resetEmailInputLayout.error = "Invalid email format"
                    return@setOnClickListener
                }
                resetEmailInputLayout.error = null

                sendButton.isEnabled = false
                resetEmailInput.isEnabled = false
                dialog.setCancelable(false)

                lifecycleScope.launch {
                    val result = ApiClient.resetPasswordForEmail(email)
                    if (result.isSuccess) {
                        Toast.makeText(this@LoginActivity, "Password reset email sent. Check your inbox.", Toast.LENGTH_LONG).show()
                        dialog.dismiss()
                    } else {
                        val errorMsg = result.exceptionOrNull()?.message ?: "Failed to send reset email"
                        Toast.makeText(this@LoginActivity, errorMsg, Toast.LENGTH_LONG).show()
                        sendButton.isEnabled = true
                        resetEmailInput.isEnabled = true
                        dialog.setCancelable(true)
                    }
                }
            }
        }

        registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }
}
