package com.example.snipshot

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class HelpActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val textView = TextView(this).apply {
            text = buildString {
                append("Help Screen\n\nThis is a placeholder for the help content.")
            }
            textSize = 20f
            setPadding(16, 16, 16, 16)
        }
        setContentView(textView)

        // Optional: Finish the activity after a short delay or on user interaction
        textView.setOnClickListener { finish() }
    }

}