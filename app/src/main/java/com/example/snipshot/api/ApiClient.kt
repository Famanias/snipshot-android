package com.example.snipshot.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.snipshot.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private lateinit var prefs: SharedPreferences

    var accessToken: String? = null
        private set
    var refreshToken: String? = null
        private set
    var user: JSONObject? = null
        private set

    fun init(context: Context) {
        try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            prefs = EncryptedSharedPreferences.create(
                context,
                "secret_shared_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )

            accessToken = prefs.getString("access_token", null)
            refreshToken = prefs.getString("refresh_token", null)
            val userStr = prefs.getString("user_json", null)
            if (userStr != null) {
                user = JSONObject(userStr)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback for issues with EncryptedSharedPreferences in some devices/emulators
            prefs = context.getSharedPreferences("fallback_prefs", Context.MODE_PRIVATE)
            accessToken = prefs.getString("access_token", null)
            refreshToken = prefs.getString("refresh_token", null)
            val userStr = prefs.getString("user_json", null)
            if (userStr != null) {
                user = JSONObject(userStr)
            }
        }
    }

    fun isLoggedIn() = accessToken != null

    private fun getAuthHeaders(): Headers {
        val builder = Headers.Builder()
        accessToken?.let {
            builder.add("Authorization", "Bearer $it")
        }
        return builder.build()
    }

    private fun saveAuth(data: JSONObject) {
        accessToken = data.optString("access_token", null)
        refreshToken = data.optString("refresh_token", null)
        user = data.optJSONObject("user")

        prefs.edit().apply {
            putString("access_token", accessToken)
            putString("refresh_token", refreshToken)
            putString("user_json", user?.toString())
            apply()
        }
    }

    fun logout() {
        accessToken = null
        refreshToken = null
        user = null
        prefs.edit().clear().apply()
    }

    // --- Auth ---

    suspend fun login(email: String, password: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("email", email).put("password", password).toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("${BuildConfig.DATABASE_API_URL}/users/login")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    val data = JSONObject(responseStr)
                    saveAuth(data)
                    Result.success(data)
                } else {
                    val error = try { JSONObject(responseStr).getString("detail") } catch (e: Exception) { "Login failed" }
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun register(email: String, password: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("email", email).put("password", password).toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("${BuildConfig.DATABASE_API_URL}/users/register")
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.code == 201 || response.isSuccessful) {
                    val data = JSONObject(responseStr)
                    if (data.has("access_token")) {
                        saveAuth(data)
                    }
                    Result.success(data)
                } else {
                    val error = try { JSONObject(responseStr).getString("detail") } catch (e: Exception) { "Registration failed" }
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Images ---

    suspend fun getImages(folderId: Int? = null, page: Int = 1, perPage: Int = 50): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = "${BuildConfig.DATABASE_API_URL}/images".toHttpUrlOrNull()?.newBuilder()
                ?: throw Exception("Invalid URL")
            
            urlBuilder.addQueryParameter("page", page.toString())
            urlBuilder.addQueryParameter("per_page", perPage.toString())
            if (folderId != null) {
                urlBuilder.addQueryParameter("folder_id", folderId.toString())
            }

            val request = Request.Builder()
                .url(urlBuilder.build())
                .headers(getAuthHeaders())
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(JSONObject(response.body?.string() ?: "{}"))
                } else {
                    Result.failure(Exception("Failed to get images: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun uploadImage(
        imageBytes: ByteArray,
        filename: String,
        folderId: Int? = null,
        sourceLang: String? = null,
        targetLang: String? = null
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("image", filename, imageBytes.toRequestBody("image/png".toMediaType()))

            if (folderId != null) {
                builder.addFormDataPart("folder_id", folderId.toString())
            }
            if (sourceLang != null) {
                builder.addFormDataPart("source_language", sourceLang)
            }
            if (targetLang != null) {
                builder.addFormDataPart("target_language", targetLang)
            }

            val request = Request.Builder()
                .url("${BuildConfig.DATABASE_API_URL}/images/upload")
                .headers(getAuthHeaders())
                .post(builder.build())
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful || response.code == 201) {
                    Result.success(JSONObject(responseStr))
                } else {
                    val error = try { JSONObject(responseStr).getString("detail") } catch (e: Exception) { "Upload failed" }
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun moveImage(imageId: Int, folderId: Int?): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                if (folderId != null) put("folder_id", folderId)
                else put("folder_id", JSONObject.NULL)
            }.toString().toRequestBody(JSON)

            val request = Request.Builder()
                .url("${BuildConfig.DATABASE_API_URL}/images/$imageId")
                .headers(getAuthHeaders())
                .put(body)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(JSONObject(response.body?.string() ?: "{}"))
                } else {
                    Result.failure(Exception("Failed to move image"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteImage(imageId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${BuildConfig.DATABASE_API_URL}/images/$imageId")
                .headers(getAuthHeaders())
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to delete image"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Folders ---

    suspend fun getFolders(): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${BuildConfig.DATABASE_API_URL}/folders")
                .headers(getAuthHeaders())
                .get()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(JSONObject(response.body?.string() ?: "{}"))
                } else {
                    Result.failure(Exception("Failed to get folders: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFolder(name: String, description: String = ""): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("name", name).put("description", description).toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("${BuildConfig.DATABASE_API_URL}/folders")
                .headers(getAuthHeaders())
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful || response.code == 201) {
                    Result.success(JSONObject(responseStr))
                } else {
                    val error = try { JSONObject(responseStr).getString("detail") } catch (e: Exception) { "Failed to create folder" }
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFolder(folderId: Int, deleteImages: Boolean = false): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${BuildConfig.DATABASE_API_URL}/folders/$folderId?delete_images=$deleteImages")
                .headers(getAuthHeaders())
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(true)
                } else {
                    Result.failure(Exception("Failed to delete folder"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun renameFolder(folderId: Int, newName: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("name", newName).toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("${BuildConfig.DATABASE_API_URL}/folders/$folderId")
                .headers(getAuthHeaders())
                .put(body)
                .build()

            client.newCall(request).execute().use { response ->
                val responseStr = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    Result.success(JSONObject(responseStr))
                } else {
                    val error = try { JSONObject(responseStr).getString("detail") } catch (e: Exception) { "Failed to rename folder" }
                    Result.failure(Exception(error))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
