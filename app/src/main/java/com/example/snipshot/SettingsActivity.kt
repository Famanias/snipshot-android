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
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences("SnipShotPrefs", MODE_PRIVATE)

        val appBarLayout = findViewById<com.google.android.material.appbar.AppBarLayout>(R.id.appBarLayout)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val bottomNav = findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottom_navigation)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        ViewCompat.setOnApplyWindowInsetsListener(appBarLayout) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(0, statusBarInsets.top, 0, 0)
            insets
        }

        bottomNav.selectedItemId = R.id.nav_settings
        val baseFooterPadding = bottomNav.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { view, insets ->
            val navBarInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            view.setPadding(
                view.paddingLeft,
                view.paddingTop,
                view.paddingRight,
                baseFooterPadding + navBarInsets.bottom
            )
            insets
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_my_files -> {
                    val intent = Intent(this, DashboardActivity::class.java).apply {
                        putExtra("select_tab", R.id.nav_my_files)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_recent -> {
                    val intent = Intent(this, DashboardActivity::class.java).apply {
                        putExtra("select_tab", R.id.nav_recent)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.nav_settings -> {
                    true
                }
                else -> false
            }
        }

        val modeRadioGroup = findViewById<android.widget.RadioGroup>(R.id.mode_radio_group)
        val advancedLayout = findViewById<android.widget.LinearLayout>(R.id.advanced_settings_layout)
        val tvDetectorSizeValue = findViewById<TextView>(R.id.tv_detector_size_value)
        val tvBoxThresholdValue = findViewById<TextView>(R.id.tv_box_threshold_value)
        val tvTextThresholdValue = findViewById<TextView>(R.id.tv_text_threshold_value)

        val sbDetectorSize = findViewById<android.widget.SeekBar>(R.id.et_detector_size)
        val sbBoxThreshold = findViewById<android.widget.SeekBar>(R.id.et_box_threshold)
        val sbTextThreshold = findViewById<android.widget.SeekBar>(R.id.et_text_threshold)
        val languageSpinner = findViewById<Spinner>(R.id.language_spinner)
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
        val savedDetectorSize = sharedPreferences.getInt("detector_size", 1536)
        sbDetectorSize.progress = savedDetectorSize
        tvDetectorSizeValue.text = savedDetectorSize.toString()

        val savedBoxThreshold = sharedPreferences.getFloat("box_threshold", 0.7f)
        sbBoxThreshold.progress = (savedBoxThreshold * 100).toInt()
        tvBoxThresholdValue.text = String.format("%.2f", savedBoxThreshold)

        val savedTextThreshold = sharedPreferences.getFloat("text_threshold", 0.5f)
        sbTextThreshold.progress = (savedTextThreshold * 100).toInt()
        tvTextThresholdValue.text = String.format("%.2f", savedTextThreshold)

        sbDetectorSize.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                tvDetectorSizeValue.text = progress.toString()
                if (fromUser && !initializing) {
                    saveInt("detector_size", progress)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        sbBoxThreshold.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val floatVal = progress / 100.0f
                tvBoxThresholdValue.text = String.format("%.2f", floatVal)
                if (fromUser && !initializing) {
                    saveFloat("box_threshold", floatVal)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        sbTextThreshold.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                val floatVal = progress / 100.0f
                tvTextThresholdValue.text = String.format("%.2f", floatVal)
                if (fromUser && !initializing) {
                    saveFloat("text_threshold", floatVal)
                }
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        // ── Done initialising ─────────────────────────────────────────────────
        initializing = false
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun saveString(key: String, value: String) = sharedPreferences.edit { putString(key, value) }
    private fun saveInt(key: String, value: Int) = sharedPreferences.edit { putInt(key, value) }
    private fun saveFloat(key: String, value: Float) = sharedPreferences.edit { putFloat(key, value) }
}