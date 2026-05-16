package com.example.snipshot.utils

import android.content.Context
import android.graphics.Bitmap
import android.os.Environment
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

object StorageManager {
    fun saveLocally(context: Context, bitmap: Bitmap, prefix: String = "Snip"): File? {
        try {
            val dir = context.getExternalFilesDir("Translated") ?: return null
            if (!dir.exists()) {
                dir.mkdirs()
            }
            
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(dir, "${prefix}_${timeStamp}.png")
            
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            return file
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
    
    fun getLocalFiles(context: Context): List<File> {
        val dir = context.getExternalFilesDir("Translated") ?: return emptyList()
        return dir.listFiles()?.toList()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
