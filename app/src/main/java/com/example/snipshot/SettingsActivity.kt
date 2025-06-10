// SettingsActivity.kt
package com.example.snipshot

import android.app.Activity
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.app.AlertDialog
import androidx.core.content.edit

class SettingsActivity : Activity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val languageOptions = mapOf(
        "en" to "English",
        "ja" to "Japanese",
        "ko" to "Korean",
        "zh_cn" to "Simplified Chinese",
        "zh_tw" to "Traditional Chinese"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences("SnipShotPrefs", MODE_PRIVATE)

        val languageSpinner = findViewById<Spinner>(R.id.language_spinner)
        val saveButton = findViewById<Button>(R.id.save_button)
        val backButton = findViewById<Button>(R.id.back_button)

        // Setup spinner
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            languageOptions.values.toList()
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        languageSpinner.adapter = adapter

        // Set current selection
        val currentLang = sharedPreferences.getString("target_language", "en") ?: "en"
        val currentLangName = languageOptions[currentLang] ?: "English"
        languageSpinner.setSelection(adapter.getPosition(currentLangName))

        saveButton.setOnClickListener {
            val selectedPosition = languageSpinner.selectedItemPosition
            val selectedLang = languageOptions.keys.elementAt(selectedPosition)

            sharedPreferences.edit {
                putString("target_language", selectedLang)
            }

            showSuccessDialog()
        }

        backButton.setOnClickListener {
            finish()
        }
    }

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Settings Saved")
            .setMessage("Your preferences have been saved successfully.")
            .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
            .show()
    }
}