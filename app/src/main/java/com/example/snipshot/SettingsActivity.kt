// SettingsActivity.kt
package com.example.snipshot

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import com.example.snipshot.api.ApiClient

class SettingsActivity : AppCompatActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val languageOptions = mapOf(
        "en" to "English",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh_cn" to "Simplified Chinese",
        "zh_tw" to "Traditional Chinese"
    )

    // Prevent saving while we are programmatically initialising the UI
    private var initializing = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences("SnipShotPrefs", MODE_PRIVATE)

        val modeRadioGroup = findViewById<android.widget.RadioGroup>(R.id.mode_radio_group)
        val advancedLayout = findViewById<android.widget.LinearLayout>(R.id.advanced_settings_layout)
        val etDetectorSize = findViewById<android.widget.EditText>(R.id.et_detector_size)
        val etBoxThreshold = findViewById<android.widget.EditText>(R.id.et_box_threshold)
        val etTextThreshold = findViewById<android.widget.EditText>(R.id.et_text_threshold)
        val languageSpinner = findViewById<Spinner>(R.id.language_spinner)
        val backButton = findViewById<Button>(R.id.back_button)
        val btnLogout = findViewById<Button>(R.id.btn_logout)
        val tvAccountEmail = findViewById<TextView>(R.id.tv_account_email)

        // ── Account section ───────────────────────────────────────────────────
        if (ApiClient.isLoggedIn()) {
            val email = ApiClient.user?.optString("email", "") ?: ""
            tvAccountEmail.text = if (email.isNotEmpty()) "Signed in as $email" else "Signed in"
            btnLogout.visibility = android.view.View.VISIBLE
        } else {
            tvAccountEmail.text = "Not signed in (Offline Mode)"
            btnLogout.visibility = android.view.View.GONE
        }

        btnLogout.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure you want to log out?")
                .setPositiveButton("Log Out") { _, _ ->
                    ApiClient.logout()
                    // Return to dashboard so it refreshes
                    val intent = Intent(this, DashboardActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        // ── Translation Mode ──────────────────────────────────────────────────
        val savedMode = sharedPreferences.getString("translation_mode", TranslationMode.MODE_2_SIMPLE_OCR.name)
        if (savedMode == TranslationMode.MODE_1_MANGA.name) {
            modeRadioGroup.check(R.id.rb_mode_1)
            advancedLayout.visibility = android.view.View.VISIBLE
        } else {
            modeRadioGroup.check(R.id.rb_mode_2)
            advancedLayout.visibility = android.view.View.GONE
        }

        modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = if (checkedId == R.id.rb_mode_1) {
                advancedLayout.visibility = android.view.View.VISIBLE
                TranslationMode.MODE_1_MANGA.name
            } else {
                advancedLayout.visibility = android.view.View.GONE
                TranslationMode.MODE_2_SIMPLE_OCR.name
            }
            if (!initializing) saveString("translation_mode", mode)
        }

        // ── Language Spinner ──────────────────────────────────────────────────
        val spinnerAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languageOptions.values.toList()
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = spinnerAdapter

        val currentLang = sharedPreferences.getString("target_language", "en") ?: "en"
        languageSpinner.setSelection(spinnerAdapter.getPosition(languageOptions[currentLang] ?: "English"))

        languageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (!initializing) {
                    val lang = languageOptions.keys.elementAt(position)
                    saveString("target_language", lang)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // ── Advanced parameters ───────────────────────────────────────────────
        etDetectorSize.setText(sharedPreferences.getInt("detector_size", 1536).toString())
        etBoxThreshold.setText(sharedPreferences.getFloat("box_threshold", 0.7f).toString())
        etTextThreshold.setText(sharedPreferences.getFloat("text_threshold", 0.5f).toString())

        etDetectorSize.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!initializing) saveInt("detector_size", s.toString().toIntOrNull() ?: 1536)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        etBoxThreshold.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!initializing) saveFloat("box_threshold", s.toString().toFloatOrNull() ?: 0.7f)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })
        etTextThreshold.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (!initializing) saveFloat("text_threshold", s.toString().toFloatOrNull() ?: 0.5f)
            }
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // ── Done initialising ─────────────────────────────────────────────────
        initializing = false

        backButton.setOnClickListener { finish() }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun saveString(key: String, value: String) = sharedPreferences.edit { putString(key, value) }
    private fun saveInt(key: String, value: Int) = sharedPreferences.edit { putInt(key, value) }
    private fun saveFloat(key: String, value: Float) = sharedPreferences.edit { putFloat(key, value) }
}