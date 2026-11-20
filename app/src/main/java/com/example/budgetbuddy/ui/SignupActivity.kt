package com.example.budgetbuddy.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.budgetbuddy.R
import com.example.budgetbuddy.data.AppDatabase
import com.example.budgetbuddy.data.ExpenseRepository
import com.example.budgetbuddy.utils.PrefsManager
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class SignupActivity : AppCompatActivity() {

    private var selectedPhotoUri: String = ""
    private lateinit var ivAvatar: ImageView

    private val pickGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                val savedUri = copyUriToInternalStorage(uri)
                if (savedUri != null) {
                    selectedPhotoUri = savedUri
                    showCircularAvatar(Uri.parse(savedUri))
                }
            }
        }

    private val takeCameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                val savedUri = saveBitmapToInternalStorage(bitmap)
                selectedPhotoUri = savedUri
                showCircularAvatar(Uri.parse(savedUri))
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        val prefs = PrefsManager(this)
        val repository = ExpenseRepository(AppDatabase.getInstance(this))

        ivAvatar = findViewById(R.id.ivSignupAvatar)
        val etName = findViewById<EditText>(R.id.etSignupName)
        val etPassword = findViewById<EditText>(R.id.etSignupPassword)
        val etPhone = findViewById<EditText>(R.id.etSignupPhone)
        val btnSignup = findViewById<Button>(R.id.btnSignup)
        val tvGoToLogin = findViewById<TextView>(R.id.tvGoToLogin)

        ivAvatar.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Profile Photo")
                .setItems(arrayOf("Choose from Gallery", "Take a Photo")) { _, which ->
                    if (which == 0) pickGalleryLauncher.launch("image/*")
                    else takeCameraLauncher.launch(null)
                }
                .show()
        }

        btnSignup.setOnClickListener {
            val name = etName.text.toString().trim()
            val password = etPassword.text.toString()
            val rawPhone = etPhone.text.toString().trim()
            val fullPhone = if (rawPhone.startsWith("+20")) rawPhone else "+20$rawPhone"

            btnSignup.isEnabled = false
            btnSignup.text = "Creating Account..."

            lifecycleScope.launch {
                val result = repository.registerUser(name, password, fullPhone)
                result.onSuccess { account ->
                    prefs.saveUserSession(account.id, account.fullName, account.phone)
                    if (selectedPhotoUri.isNotEmpty()) {
                        prefs.saveProfileImage(selectedPhotoUri)
                    }
                    Toast.makeText(this@SignupActivity, "Account created!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this@SignupActivity, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }
                    startActivity(intent)
                    finish()
                }.onFailure { error ->
                    btnSignup.isEnabled = true
                    btnSignup.text = "Sign Up"
                    Toast.makeText(this@SignupActivity, error.message ?: "Sign up failed", Toast.LENGTH_LONG).show()
                }
            }
        }

        tvGoToLogin.setOnClickListener {
            finish()
        }
    }

    private fun showCircularAvatar(uri: Uri) {
        ivAvatar.setPadding(0, 0, 0, 0)
        ivAvatar.setBackgroundResource(R.drawable.bg_circle_badge)
        ivAvatar.clipToOutline = true
        ivAvatar.setImageURI(null)
        ivAvatar.setImageURI(uri)
    }

    private fun saveBitmapToInternalStorage(bitmap: Bitmap): String {
        val file = File(filesDir, "user_avatar.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        return Uri.fromFile(file).toString()
    }

    private fun copyUriToInternalStorage(uri: Uri): String? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()
            if (bitmap != null) saveBitmapToInternalStorage(bitmap) else null
        } catch (_: Exception) {
            null
        }
    }
}
