package com.example.snipshot

import android.app.Activity
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.RelativeSizeSpan
import android.graphics.Typeface
import android.widget.TextView
import android.view.Gravity
import android.widget.LinearLayout
import android.view.ViewGroup

class HelpActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val fullText = buildString {
            append("Getting Started\n\n")
            append("Click the \"SnipShot\" bubble to start using the application.\n\n")
            append("You will be presented with three sub-bubbles: the \"Snip\" (cut icon), \"Settings\" (settings icon), \"Help\" (question mark icon).\n\n")
            append("After clicking \"Snip\", drag to create a rectangle around the text you want to capture and translate.\n\n")
            append("Translation\n\n")
            append("Once you've snipped, the extracted texts from the image will automatically be translated. You can change the target language in the settings.\n\n")
            append("Settings\n\n")
            append("Configure your target language (English, Japanese, Korean, and Chinese).")
        }

        val spannableText = SpannableStringBuilder(fullText)

        // Apply styles only to standalone section headers
        val sections = listOf(
            Pair("Getting Started\n\n", "Getting Started"),
            Pair("Translation\n\n", "Translation"),
            Pair("Settings\n\n", "Settings")
        )

        sections.forEach { (fullMatch, section) ->
            val start = fullText.indexOf(fullMatch)
            if (start >= 0) {
                val end = start + section.length
                spannableText.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                spannableText.setSpan(RelativeSizeSpan(1.25f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // Create a parent layout to center the text view
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val textView = TextView(this).apply {
            text = spannableText
            textSize = 16f
            gravity = Gravity.CENTER // Center the text within the TextView
            setPadding(16, 16, 16, 16)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 16, 16, 16)
            }
        }

        layout.addView(textView)
        setContentView(layout)

        // Optional: Finish the activity after a short delay or on user interaction
        textView.setOnClickListener { finish() }
    }
}