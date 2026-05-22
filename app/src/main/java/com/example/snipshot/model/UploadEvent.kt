package com.example.snipshot.model

sealed class UploadEvent {
    data class Success(val filename: String, val imageId: Int, val publicUrl: String) : UploadEvent()
    data class Failure(val filename: String, val error: String) : UploadEvent()
}
