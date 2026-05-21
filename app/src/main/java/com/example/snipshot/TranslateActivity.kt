package com.example.snipshot

import android.content.ClipboardManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import java.util.Locale

class TranslateActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate)

        // Get data from intent
        val detectedLanguage = intent.getStringExtra("detected_language")
        val extractedText = intent.getStringExtra("extracted_text") ?: ""
        val translatedText = intent.getStringExtra("translated_text") ?: ""
        val summary = intent.getStringExtra("summary")

        // Set up Toolbar
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener {
            finish()
        }

        // Set up UI elements
        val detectedLangTextView = findViewById<TextView>(R.id.detected_language)
        val extractedTextView = findViewById<TextView>(R.id.extracted_text)
        val translatedTextView = findViewById<TextView>(R.id.translated_text)
        val summaryCard = findViewById<MaterialCardView>(R.id.summary_card)
        val summaryTextView = findViewById<TextView>(R.id.summary_text)

        val copyExtractedButton = findViewById<ImageButton>(R.id.copy_extracted)
        val copyTranslatedButton = findViewById<ImageButton>(R.id.copy_translated)

        // Populate Detected Language Badge
        if (!detectedLanguage.isNullOrBlank()) {
            val capitalizedLang = detectedLanguage.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
            detectedLangTextView.text = getString(R.string.detected_language, capitalizedLang)
        } else {
            detectedLangTextView.text = getString(R.string.detected_language_unknown)
        }

        // Populate OCR and Translation
        extractedTextView.text = extractedText
        translatedTextView.text = translatedText

        // Populate Summary (conditionally display card)
        if (!summary.isNullOrBlank()) {
            summaryTextView.text = summary
            summaryCard.visibility = View.VISIBLE
        } else {
            summaryCard.visibility = View.GONE
        }

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