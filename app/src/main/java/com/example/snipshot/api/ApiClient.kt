package com.example.snipshot.api

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.snipshot.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.example.snipshot.model.UploadEvent
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import java.util.concurrent.TimeUnit

/**
 * ApiClient — Direct Supabase Integration
 *
 * Architecture:
 *   Auth    → {SUPABASE_URL}/auth/v1/           (signup, login, session)
 *   Tables  → {SUPABASE_URL}/rest/v1/           (folders, images via PostgREST)
 *   Storage → {SUPABASE_URL}/storage/v1/        (binary image upload/delete)
 *
 * Security:
 *   - Uses ANON key only (never the service role key)
 *   - Row-Level Security on Supabase filters data per auth.uid() automatically
 *   - All traffic is HTTPS — no cleartext exceptions needed
 */
object ApiClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val supabaseUrl = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anonKey = BuildConfig.SUPABASE_ANON_KEY
    private val storageBucket = BuildConfig.SUPABASE_STORAGE_BUCKET

    private val _uploadFlow = MutableSharedFlow<UploadEvent>(extraBufferCapacity = 10)
    val uploadFlow: SharedFlow<UploadEvent> = _uploadFlow.asSharedFlow()

    private lateinit var prefs: SharedPreferences

    var accessToken: String? = null
        private set
    var refreshToken: String? = null
        private set
    var user: JSONObject? = null
        private set

    // ─── Initialization ───────────────────────────────────────────────────────

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
        } catch (e: Exception) {
            e.printStackTrace()
            prefs = context.getSharedPreferences("fallback_prefs", Context.MODE_PRIVATE)
        }
        accessToken = prefs.getString("access_token", null)
        refreshToken = prefs.getString("refresh_token", null)
        val userStr = prefs.getString("user_json", null)
        if (userStr != null) user = JSONObject(userStr)
    }

    fun isLoggedIn() = accessToken != null

    fun logout() {
        accessToken = null
        refreshToken = null
        user = null
        prefs.edit().clear().apply()
    }

    // ─── Shared header builders ───────────────────────────────────────────────

    /** Headers for Supabase Auth endpoints */
    private fun authHeaders(): Headers = Headers.Builder()
        .add("apikey", anonKey)
        .add("Content-Type", "application/json")
        .build()

    /** Headers for PostgREST and Storage endpoints (requires login) */
    private fun apiHeaders(extraHeaders: Map<String, String> = emptyMap()): Headers {
        val builder = Headers.Builder()
            .add("apikey", anonKey)
            .add("Content-Type", "application/json")
        accessToken?.let { builder.add("Authorization", "Bearer $it") }
        extraHeaders.forEach { (k, v) -> builder.add(k, v) }
        return builder.build()
    }

    private fun saveSession(data: JSONObject) {
        accessToken = data.optString("access_token").ifEmpty { null }
        refreshToken = data.optString("refresh_token").ifEmpty { null }
        user = data.optJSONObject("user")
        prefs.edit()
            .putString("access_token", accessToken)
            .putString("refresh_token", refreshToken)
            .putString("user_json", user?.toString())
            .apply()
    }

    // ─── Auth ─────────────────────────────────────────────────────────────────

    /**
     * Register via Supabase Auth.
     * POST {supabaseUrl}/auth/v1/signup
     */
    suspend fun register(email: String, password: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("email", email).put("password", password)
                .toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/signup")
                .headers(authHeaders())
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val str = response.body?.string() ?: "{}"
                val json = JSONObject(str)
                if (response.isSuccessful) {
                    // Supabase returns session immediately if email confirm is off
                    if (json.has("access_token")) saveSession(json)
                    Result.success(json)
                } else {
                    val msg = json.optString("msg", json.optString("message", "Registration failed"))
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Login via Supabase Auth (password grant).
     * POST {supabaseUrl}/auth/v1/token?grant_type=password
     */
    suspend fun login(email: String, password: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("email", email).put("password", password)
                .toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=password")
                .headers(authHeaders())
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val str = response.body?.string() ?: "{}"
                val json = JSONObject(str)
                if (response.isSuccessful) {
                    saveSession(json)
                    Result.success(json)
                } else {
                    val msg = json.optString("error_description",
                        json.optString("msg", "Invalid email or password"))
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Images (PostgREST) ───────────────────────────────────────────────────

    /**
     * List images for the current user.
     * GET {supabaseUrl}/rest/v1/images?select=*&order=created_at.desc
     * RLS on Supabase filters by auth.uid() automatically.
     */
    suspend fun getImages(folderId: Int? = null, page: Int = 1, perPage: Int = 50): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val urlBuilder = "$supabaseUrl/rest/v1/images".toHttpUrlOrNull()!!.newBuilder()
                    .addQueryParameter("select", "*")
                    .addQueryParameter("order", "created_at.desc")
                    .addQueryParameter("limit", perPage.toString())
                    .addQueryParameter("offset", ((page - 1) * perPage).toString())
                if (folderId != null) {
                    urlBuilder.addQueryParameter("folder_id", "eq.$folderId")
                }
                val request = Request.Builder()
                    .url(urlBuilder.build())
                    .headers(apiHeaders(mapOf("Prefer" to "count=exact")))
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val array = JSONArray(response.body?.string() ?: "[]")
                        // Wrap in the same shape the UI expects: {"images": [...]}
                        val wrapper = JSONObject().put("images", array)
                        Result.success(wrapper)
                    } else {
                        Result.failure(Exception("Failed to get images: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Upload image bytes to Supabase Storage, then insert metadata into the images table.
     * PUT  {supabaseUrl}/storage/v1/object/{bucket}/{path}
     * POST {supabaseUrl}/rest/v1/images
     */
    suspend fun uploadImage(
        imageBytes: ByteArray,
        filename: String,
        folderId: Int? = null,
        sourceLang: String? = null,
        targetLang: String? = null,
        timestamp: Long = System.currentTimeMillis()
    ): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val userId = user?.optString("id") ?: return@withContext Result.failure(Exception("Not logged in"))
            val storagePath = "$userId/${timestamp}_$filename"

            // 1. Upload binary to Supabase Storage
            val storageUrl = "$supabaseUrl/storage/v1/object/$storageBucket/$storagePath"
            val storageHeaders = Headers.Builder()
                .add("apikey", anonKey)
                .add("Authorization", "Bearer $accessToken")
                .add("Content-Type", "image/png")
                .build()
            val uploadRequest = Request.Builder()
                .url(storageUrl)
                .headers(storageHeaders)
                .put(imageBytes.toRequestBody("image/png".toMediaType()))
                .build()
            val uploadResponse = client.newCall(uploadRequest).execute()
            if (!uploadResponse.isSuccessful) {
                val err = uploadResponse.body?.string() ?: "Storage upload failed"
                _uploadFlow.emit(UploadEvent.Failure(filename, err))
                return@withContext Result.failure(Exception(err))
            }
            uploadResponse.body?.close()

            // 2. Build public URL
            val publicUrl = "$supabaseUrl/storage/v1/object/public/$storageBucket/$storagePath"

            // 3. Insert metadata row via PostgREST
            val meta = JSONObject().apply {
                put("user_id", userId)
                put("storage_path", storagePath)
                put("public_url", publicUrl)
                put("filename", filename)
                put("original_filename", filename)
                put("file_size", imageBytes.size)
                if (folderId != null) put("folder_id", folderId)
                if (sourceLang != null) put("source_language", sourceLang)
                if (targetLang != null) put("target_language", targetLang)
            }
            val metaRequest = Request.Builder()
                .url("$supabaseUrl/rest/v1/images")
                .headers(apiHeaders(mapOf("Prefer" to "return=representation")))
                .post(meta.toString().toRequestBody(JSON))
                .build()
            client.newCall(metaRequest).execute().use { response ->
                val str = response.body?.string() ?: "[]"
                if (response.isSuccessful || response.code == 201) {
                    val arr = JSONArray(str)
                    val resultObj = if (arr.length() > 0) arr.getJSONObject(0) else JSONObject()
                    if (resultObj.has("id")) {
                        val id = resultObj.optInt("id", -1)
                        val url = resultObj.optString("public_url", "")
                        _uploadFlow.emit(UploadEvent.Success(filename, id, url))
                    }
                    Result.success(resultObj)
                } else {
                    val errMsg = "Metadata insert failed: $str"
                    _uploadFlow.emit(UploadEvent.Failure(filename, errMsg))
                    Result.failure(Exception(errMsg))
                }
            }
        } catch (e: Exception) {
            _uploadFlow.emit(UploadEvent.Failure(filename, e.message ?: "Unknown error"))
            Result.failure(e)
        }
    }

    /**
     * Move an image to a different folder.
     * PATCH {supabaseUrl}/rest/v1/images?id=eq.{imageId}
     */
    suspend fun moveImage(imageId: Int, folderId: Int?): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().apply {
                if (folderId != null && folderId != 0) put("folder_id", folderId)
                else put("folder_id", JSONObject.NULL)
            }.toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/images?id=eq.$imageId")
                .headers(apiHeaders(mapOf("Prefer" to "return=representation")))
                .patch(body)
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val arr = JSONArray(response.body?.string() ?: "[]")
                    Result.success(if (arr.length() > 0) arr.getJSONObject(0) else JSONObject())
                } else {
                    Result.failure(Exception("Failed to move image"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete an image: remove from Storage then delete metadata row.
     * DELETE {supabaseUrl}/storage/v1/object/{bucket}
     * DELETE {supabaseUrl}/rest/v1/images?id=eq.{imageId}
     */
    suspend fun deleteImage(imageId: Int): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // 1. Fetch storage_path first
            val fetchRequest = Request.Builder()
                .url("$supabaseUrl/rest/v1/images?id=eq.$imageId&select=storage_path")
                .headers(apiHeaders())
                .get()
                .build()
            val storagePath = client.newCall(fetchRequest).execute().use { resp ->
                val arr = JSONArray(resp.body?.string() ?: "[]")
                if (arr.length() > 0) arr.getJSONObject(0).optString("storage_path") else null
            }

            // 2. Delete from Storage
            if (!storagePath.isNullOrEmpty()) {
                val delBody = JSONObject().put("prefixes", org.json.JSONArray().put(storagePath))
                    .toString().toRequestBody(JSON)
                val storageDelRequest = Request.Builder()
                    .url("$supabaseUrl/storage/v1/object/$storageBucket")
                    .headers(apiHeaders())
                    .delete(delBody)
                    .build()
                client.newCall(storageDelRequest).execute().close()
            }

            // 3. Delete metadata row
            val delRequest = Request.Builder()
                .url("$supabaseUrl/rest/v1/images?id=eq.$imageId")
                .headers(apiHeaders())
                .delete()
                .build()
            client.newCall(delRequest).execute().use { response ->
                if (response.isSuccessful) Result.success(true)
                else Result.failure(Exception("Failed to delete image"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Folders (PostgREST) ──────────────────────────────────────────────────

    /**
     * List folders for the current user.
     * GET {supabaseUrl}/rest/v1/folders?select=*&order=name.asc
     */
    suspend fun getFolders(): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/folders?select=*&order=name.asc")
                .headers(apiHeaders())
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val array = JSONArray(response.body?.string() ?: "[]")
                    // Wrap in the same shape the UI expects: {"folders": [...]}
                    val wrapper = JSONObject().put("folders", array)
                    Result.success(wrapper)
                } else {
                    Result.failure(Exception("Failed to get folders: ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new folder.
     * POST {supabaseUrl}/rest/v1/folders
     */
    suspend fun createFolder(name: String, description: String = ""): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val userId = user?.optString("id") ?: return@withContext Result.failure(Exception("Not logged in"))
                val body = JSONObject()
                    .put("user_id", userId)
                    .put("name", name)
                    .put("description", description)
                    .toString().toRequestBody(JSON)
                val request = Request.Builder()
                    .url("$supabaseUrl/rest/v1/folders")
                    .headers(apiHeaders(mapOf("Prefer" to "return=representation")))
                    .post(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    val str = response.body?.string() ?: "[]"
                    if (response.isSuccessful || response.code == 201) {
                        val arr = JSONArray(str)
                        Result.success(if (arr.length() > 0) arr.getJSONObject(0) else JSONObject())
                    } else {
                        Result.failure(Exception("Failed to create folder: $str"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Delete a folder.
     * DELETE {supabaseUrl}/rest/v1/folders?id=eq.{folderId}
     */
    suspend fun deleteFolder(folderId: Int, deleteImages: Boolean = false): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                if (deleteImages) {
                    // Unfile or delete images in this folder first
                    val unfileBody = JSONObject().put("folder_id", JSONObject.NULL)
                        .toString().toRequestBody(JSON)
                    val unfileRequest = Request.Builder()
                        .url("$supabaseUrl/rest/v1/images?folder_id=eq.$folderId")
                        .headers(apiHeaders())
                        .patch(unfileBody)
                        .build()
                    client.newCall(unfileRequest).execute().close()
                }
                val request = Request.Builder()
                    .url("$supabaseUrl/rest/v1/folders?id=eq.$folderId")
                    .headers(apiHeaders())
                    .delete()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) Result.success(true)
                    else Result.failure(Exception("Failed to delete folder"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Rename a folder.
     * PATCH {supabaseUrl}/rest/v1/folders?id=eq.{folderId}
     */
    suspend fun renameFolder(folderId: Int, newName: String): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().put("name", newName).toString().toRequestBody(JSON)
                val request = Request.Builder()
                    .url("$supabaseUrl/rest/v1/folders?id=eq.$folderId")
                    .headers(apiHeaders(mapOf("Prefer" to "return=representation")))
                    .patch(body)
                    .build()
                client.newCall(request).execute().use { response ->
                    val str = response.body?.string() ?: "[]"
                    if (response.isSuccessful) {
                        val arr = JSONArray(str)
                        Result.success(if (arr.length() > 0) arr.getJSONObject(0) else JSONObject())
                    } else {
                        Result.failure(Exception("Failed to rename folder: $str"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
