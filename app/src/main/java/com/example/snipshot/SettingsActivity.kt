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

        val modeRadioGroup = findViewById<android.widget.RadioGroup>(R.id.mode_radio_group)
        val advancedLayout = findViewById<android.widget.LinearLayout>(R.id.advanced_settings_layout)
        val etDetectorSize = findViewById<android.widget.EditText>(R.id.et_detector_size)
        val etBoxThreshold = findViewById<android.widget.EditText>(R.id.et_box_threshold)
        val etTextThreshold = findViewById<android.widget.EditText>(R.id.et_text_threshold)
        val languageSpinner = findViewById<Spinner>(R.id.language_spinner)
        val saveButton = findViewById<Button>(R.id.save_button)
        val backButton = findViewById<Button>(R.id.back_button)

        // Setup Mode Selection
        val savedMode = sharedPreferences.getString("translation_mode", TranslationMode.MODE_2_SIMPLE_OCR.name)
        if (savedMode == TranslationMode.MODE_1_MANGA.name) {
            modeRadioGroup.check(R.id.rb_mode_1)
            advancedLayout.visibility = android.view.View.VISIBLE
        } else {
            modeRadioGroup.check(R.id.rb_mode_2)
            advancedLayout.visibility = android.view.View.GONE
        }

        modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.rb_mode_1) {
                advancedLayout.visibility = android.view.View.VISIBLE
            } else {
                advancedLayout.visibility = android.view.View.GONE
            }
        }

        etDetectorSize.setText(sharedPreferences.getInt("detector_size", 1536).toString())
        etBoxThreshold.setText(sharedPreferences.getFloat("box_threshold", 0.7f).toString())
        etTextThreshold.setText(sharedPreferences.getFloat("text_threshold", 0.5f).toString())

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

            val selectedMode = if (modeRadioGroup.checkedRadioButtonId == R.id.rb_mode_1) {
                TranslationMode.MODE_1_MANGA.name
            } else {
                TranslationMode.MODE_2_SIMPLE_OCR.name
            }

            val detectorSize = etDetectorSize.text.toString().toIntOrNull() ?: 1536
            val boxThreshold = etBoxThreshold.text.toString().toFloatOrNull() ?: 0.7f
            val textThreshold = etTextThreshold.text.toString().toFloatOrNull() ?: 0.5f

            sharedPreferences.edit {
                putString("target_language", selectedLang)
                putString("translation_mode", selectedMode)
                putInt("detector_size", detectorSize)
                putFloat("box_threshold", boxThreshold)
                putFloat("text_threshold", textThreshold)
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