package com.example.snipshot

import android.app.Activity
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast

class TranslateActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate)

        // Get data from intent
        val extractedText = intent.getStringExtra("extracted_text") ?: ""
//        val detectedLanguage = intent.getStringExtra("detected_language") ?: "Unknown"
        val translatedText = intent.getStringExtra("translated_text") ?: ""

        // Set up UI elements
        val extractedTextView = findViewById<TextView>(R.id.extracted_text)
//        val detectedLanguageView = findViewById<TextView>(R.id.detected_language)
        val translatedTextView = findViewById<TextView>(R.id.translated_text)
        val copyExtractedButton = findViewById<ImageButton>(R.id.copy_extracted)
        val copyTranslatedButton = findViewById<ImageButton>(R.id.copy_translated)
//        val newSnipButton = findViewById<Button>(R.id.new_snip_button)
//        val settingsButton = findViewById<ImageButton>(R.id.settings_button)
//        val helpButton = findViewById<ImageButton>(R.id.help_button)

        // Populate UI
        extractedTextView.text = extractedText
//        val text = getString(R.string.detected_language, detectedLanguage)
//        detectedLanguageView.text = text
        translatedTextView.text = translatedText

        // Copy buttons
        copyExtractedButton.setOnClickListener {
            copyToClipboard(extractedText)
            Toast.makeText(this, "Extracted text copied", Toast.LENGTH_SHORT).show()
        }
        copyTranslatedButton.setOnClickListener {
            copyToClipboard(translatedText)
            Toast.makeText(this, "Translated text copied", Toast.LENGTH_SHORT).show()
        }

        // New snip button
//        newSnipButton.setOnClickListener {
//            // TODO: Implement logic to start a new snip
//            // This could relaunch the snipping activity or service that captures the screenshot
//            Toast.makeText(this, "New snip not implemented yet", Toast.LENGTH_SHORT).show()
//        }
//
//        // Settings button
//        settingsButton.setOnClickListener {
//            // TODO: Implement settings activity
//            Toast.makeText(this, "Settings not implemented yet", Toast.LENGTH_SHORT).show()
//        }
//
//        // Help button
//        helpButton.setOnClickListener {
//            // TODO: Implement help activity or dialog
//            Toast.makeText(this, "Help not implemented yet", Toast.LENGTH_SHORT).show()
//        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText("SnipShot Text", text)
        clipboard.setPrimaryClip(clip)
    }
}