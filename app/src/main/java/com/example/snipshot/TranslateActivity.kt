package com.example.snipshot

import android.app.Activity
import android.content.ClipboardManager
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast

class TranslateActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate)

        // Get data from intent
        val extractedText = intent.getStringExtra("extracted_text") ?: ""
        val translatedText = intent.getStringExtra("translated_text") ?: ""

        // Set up UI elements
        val extractedTextView = findViewById<TextView>(R.id.extracted_text)
        val translatedTextView = findViewById<TextView>(R.id.translated_text)
        val copyExtractedButton = findViewById<ImageButton>(R.id.copy_extracted)
        val copyTranslatedButton = findViewById<ImageButton>(R.id.copy_translated)

        // Populate UI
        extractedTextView.text = extractedText
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

    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = android.content.ClipData.newPlainText("SnipShot Text", text)
        clipboard.setPrimaryClip(clip)
    }

}