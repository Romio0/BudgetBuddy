package com.example.budgetbuddy.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.budgetbuddy.R
import com.example.budgetbuddy.data.AppDatabase
import com.example.budgetbuddy.data.ExpenseRepository
import com.example.budgetbuddy.utils.PrefsManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = PrefsManager(this)
        if (prefs.isLoggedIn()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        val repository = ExpenseRepository(AppDatabase.getInstance(this))
        val etName = findViewById<EditText>(R.id.etLoginName)
        val etPassword = findViewById<EditText>(R.id.etLoginPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvGoToSignup = findViewById<TextView>(R.id.tvGoToSignup)

        btnLogin.setOnClickListener {
            val name = etName.text.toString().trim()
            val password = etPassword.text.toString()

            btnLogin.isEnabled = false
            btnLogin.text = "Logging in..."

            lifecycleScope.launch {
                val result = repository.loginUser(name, password)
                result.onSuccess { account ->
                    prefs.saveUserSession(account.id, account.fullName, account.phone)
                    Toast.makeText(this@LoginActivity, "Welcome back, ${account.fullName}!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }.onFailure { error ->
                    btnLogin.isEnabled = true
                    btnLogin.text = "Log In"
                    Toast.makeText(this@LoginActivity, error.message ?: "Login failed", Toast.LENGTH_LONG).show()
                }
            }
        }

        tvGoToSignup.setOnClickListener {
            startActivity(Intent(this, SignupActivity::class.java))
        }
    }
}
