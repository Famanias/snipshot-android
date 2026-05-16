package com.example.snipshot

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.snipshot.api.ApiClient
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailInput = findViewById<EditText>(R.id.email_input)
        val passwordInput = findViewById<EditText>(R.id.password_input)
        val loginButton = findViewById<Button>(R.id.btn_login)
        val registerLink = findViewById<TextView>(R.id.tv_register_link)
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

        registerLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
            finish()
        }
    }
}
