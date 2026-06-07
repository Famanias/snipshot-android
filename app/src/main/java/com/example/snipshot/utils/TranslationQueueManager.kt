package com.example.snipshot.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class TranslationTask(
    val id: String,
    val tempImagePath: String,
    val mode: String,
    val targetLanguage: String,
    var status: Status,
    var errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    var completedImageId: Int? = null,
    var completedUrl: String? = null,
    var completedStoragePath: String? = null
) {
    enum class Status {
        QUEUED,
        PREPARING,
        TRANSLATING,
        UPLOADING,
        FAILED,
        COMPLETED
    }
    
    val queuePosition: Int
        get() = TranslationQueueManager.getQueuePosition(id)
}

object TranslationQueueManager {
    private const val FILE_NAME = "translation_queue.json"
    
    private val _tasks = MutableStateFlow<List<TranslationTask>>(emptyList())
    val tasks: StateFlow<List<TranslationTask>> = _tasks.asStateFlow()
    
    private val lock = Any()

    fun init(context: Context) {
        synchronized(lock) {
            try {
                val file = File(context.filesDir, FILE_NAME)
                if (!file.exists()) {
                    _tasks.value = emptyList()
                    return
                }
                val jsonStr = file.readText()
                if (jsonStr.trim().isEmpty()) {
                    _tasks.value = emptyList()
                    return
                }
                val jsonArray = JSONArray(jsonStr)
                val list = mutableListOf<TranslationTask>()
                for (i in 0 until jsonArray.length()) {
                    try {
                        val obj = jsonArray.getJSONObject(i)
                        list.add(obj.toTranslationTask())
                    } catch (e: Exception) {
                        Log.e("TranslationQueueManager", "Failed to parse task json", e)
                    }
                }
                _tasks.value = list
            } catch (e: Exception) {
                Log.e("TranslationQueueManager", "Failed to load queue", e)
                _tasks.value = emptyList()
            }
        }
    }

    private fun saveToFile(context: Context) {
        synchronized(lock) {
            try {
                val file = File(context.filesDir, FILE_NAME)
                val jsonArray = JSONArray()
                for (task in _tasks.value) {
                    jsonArray.put(task.toJSONObject())
                }
                file.writeText(jsonArray.toString())
            } catch (e: Exception) {
                Log.e("TranslationQueueManager", "Failed to save queue to file", e)
            }
        }
    }

    fun getQueuePosition(taskId: String): Int {
        val queuedTasks = _tasks.value
            .filter { it.status == TranslationTask.Status.QUEUED }
            .sortedBy { it.timestamp }
        val index = queuedTasks.indexOfFirst { it.id == taskId }
        return if (index != -1) index + 1 else 0
    }

    fun addTask(context: Context, task: TranslationTask) {
        synchronized(lock) {
            val currentList = _tasks.value.toMutableList()
            currentList.add(task)
            _tasks.value = currentList
            saveToFile(context)
        }
    }

    fun getNextQueuedTask(context: Context): TranslationTask? {
        synchronized(lock) {
            val queued = _tasks.value
                .filter { it.status == TranslationTask.Status.QUEUED }
                .minByOrNull { it.timestamp } ?: return null
                
            // Update status to PREPARING
            updateTaskStatus(context, queued.id, TranslationTask.Status.PREPARING)
            return _tasks.value.find { it.id == queued.id }
        }
    }

    fun updateTaskStatus(context: Context, taskId: String, status: TranslationTask.Status, errorMessage: String? = null) {
        synchronized(lock) {
            val currentList = _tasks.value.map {
                if (it.id == taskId) {
                    it.copy(status = status, errorMessage = errorMessage)
                } else {
                    it
                }
            }
            _tasks.value = currentList
            saveToFile(context)
        }
    }

    fun markTaskCompleted(context: Context, taskId: String, imageId: Int?, url: String?, storagePath: String?) {
        synchronized(lock) {
            val currentList = _tasks.value.map {
                if (it.id == taskId) {
                    it.copy(
                        status = TranslationTask.Status.COMPLETED,
                        completedImageId = imageId,
                        completedUrl = url,
                        completedStoragePath = storagePath
                    )
                } else {
                    it
                }
            }
            _tasks.value = currentList
            saveToFile(context)
        }
    }

    fun removeTask(context: Context, taskId: String) {
        synchronized(lock) {
            val task = _tasks.value.find { it.id == taskId }
            if (task != null) {
                try {
                    val file = File(task.tempImagePath)
                    if (file.exists()) {
                        file.delete()
                    }
                } catch (e: Exception) {
                    Log.e("TranslationQueueManager", "Failed to delete temp file ${task.tempImagePath}", e)
                }
            }
            val currentList = _tasks.value.filter { it.id != taskId }
            _tasks.value = currentList
            saveToFile(context)
        }
    }

    private fun JSONObject.toTranslationTask(): TranslationTask {
        return TranslationTask(
            id = getString("id"),
            tempImagePath = getString("tempImagePath"),
            mode = getString("mode"),
            targetLanguage = getString("targetLanguage"),
            status = TranslationTask.Status.valueOf(getString("status")),
            errorMessage = if (isNull("errorMessage")) null else getString("errorMessage"),
            timestamp = optLong("timestamp", System.currentTimeMillis()),
            completedImageId = if (isNull("completedImageId")) null else getInt("completedImageId"),
            completedUrl = if (isNull("completedUrl")) null else getString("completedUrl"),
            completedStoragePath = if (isNull("completedStoragePath")) null else getString("completedStoragePath")
        )
    }

    private fun TranslationTask.toJSONObject(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("tempImagePath", tempImagePath)
            put("mode", mode)
            put("targetLanguage", targetLanguage)
            put("status", status.name)
            put("errorMessage", errorMessage ?: JSONObject.NULL)
            put("timestamp", timestamp)
            put("completedImageId", completedImageId ?: JSONObject.NULL)
            put("completedUrl", completedUrl ?: JSONObject.NULL)
            put("completedStoragePath", completedStoragePath ?: JSONObject.NULL)
        }
    }
}
