package com.example.snipshot

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.core.net.toUri

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request SYSTEM_ALERT_WINDOW permission if needed
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:$packageName".toUri()
            )
            startActivityForResult(intent, 1234)
        } else {
            startService(Intent(this, BubbleService::class.java))
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1234) {
            if (Settings.canDrawOverlays(this)) {
                startService(Intent(this, BubbleService::class.java))
                finish()
            } else {
                Toast.makeText(this, "Overlay permission required", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
