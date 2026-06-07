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
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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
    var refreshToken: String? = null
        private set
    var user: JSONObject? = null
        private set

    data class SignedUrlCacheEntry(
        val signedUrl: String,
        val fetchedAt: Long
    )

    private val signedUrlCache = ConcurrentHashMap<String, SignedUrlCacheEntry>()
    private val signedUrlSemaphore = Semaphore(3)

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
        signedUrlCache.clear()
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

    /**
     * Refresh the session using the refresh token.
     * POST {supabaseUrl}/auth/v1/token?grant_type=refresh_token
     */
    suspend fun refreshSession(): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val token = refreshToken ?: return@withContext Result.failure(Exception("No refresh token available"))
            val body = JSONObject().put("refresh_token", token)
                .toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/token?grant_type=refresh_token")
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
                        json.optString("msg", "Session refresh failed"))
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Send a password reset recovery email.
     * POST {supabaseUrl}/auth/v1/recover
     */
    suspend fun resetPasswordForEmail(email: String, redirectTo: String = "https://snipshot.space/reset-password"): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("email", email)
                .put("redirect_to", redirectTo)
                .toString()
                .toRequestBody(JSON)
            val encodedRedirect = java.net.URLEncoder.encode(redirectTo, "UTF-8")
            val request = Request.Builder()
                .url("$supabaseUrl/auth/v1/recover?redirect_to=$encodedRedirect")
                .headers(authHeaders())
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val str = response.body?.string() ?: ""
                val json = if (str.trim().isNotEmpty()) JSONObject(str) else JSONObject()
                if (response.isSuccessful) {
                    Result.success(json)
                } else {
                    val msg = json.optString("msg", json.optString("message", "Password reset request failed"))
                    Result.failure(Exception(msg))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ─── Signed URL Caching & Loading ─────────────────────────────────────────

    suspend fun getSignedUrl(storagePath: String): String? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = signedUrlCache[storagePath]
        if (cached != null && (now - cached.fetchedAt) < 50 * 60 * 1000) {
            return@withContext cached.signedUrl
        }

        // Fetch new signed URL, throttled via Semaphore
        val url = signedUrlSemaphore.withPermit {
            fetchSignedUrl(storagePath).getOrNull()
        }
        if (url != null) {
            signedUrlCache[storagePath] = SignedUrlCacheEntry(url, now)
            return@withContext url
        }
        return@withContext null
    }

    private suspend fun fetchSignedUrl(storagePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = accessToken ?: return@withContext Result.failure(Exception("Not logged in"))
            val body = JSONObject().put("expiresIn", 3600)
                .toString().toRequestBody(JSON)
            
            val request = Request.Builder()
                .url("$supabaseUrl/storage/v1/object/sign/$storageBucket/$storagePath")
                .headers(Headers.Builder()
                    .add("apikey", anonKey)
                    .add("Authorization", "Bearer $token")
                    .add("Content-Type", "application/json")
                    .build())
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val str = response.body?.string() ?: "{}"
                if (response.isSuccessful) {
                    val json = JSONObject(str)
                    val url = json.optString("signedURL", json.optString("signedUrl", ""))
                    if (url.isNotEmpty()) {
                        val resolvedUrl = if (url.startsWith("/")) {
                            val parsed = supabaseUrl.toHttpUrlOrNull()
                            val path = if (url.startsWith("/object/")) "/storage/v1$url" else url
                            if (parsed != null) {
                                "${parsed.scheme}://${parsed.host}${if (parsed.port != 80 && parsed.port != 443) ":${parsed.port}" else ""}$path"
                            } else {
                                "$supabaseUrl$path"
                            }
                        } else {
                            url
                        }
                        Result.success(resolvedUrl)
                    } else {
                        Result.failure(Exception("Signed URL is empty in response: $str"))
                    }
                } else {
                    Result.failure(Exception("Failed to fetch signed URL: ${response.code} - $str"))
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
    suspend fun getImages(folderId: Int? = null, isFolderIdNull: Boolean = false, page: Int = 1, perPage: Int = 50): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val urlBuilder = "$supabaseUrl/rest/v1/images".toHttpUrlOrNull()!!.newBuilder()
                    .addQueryParameter("select", "*")
                    .addQueryParameter("order", "created_at.desc")
                    .addQueryParameter("limit", perPage.toString())
                    .addQueryParameter("offset", ((page - 1) * perPage).toString())
                if (folderId != null) {
                    urlBuilder.addQueryParameter("folder_id", "eq.$folderId")
                } else if (isFolderIdNull) {
                    urlBuilder.addQueryParameter("folder_id", "is.null")
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
                        _uploadFlow.emit(UploadEvent.Success(filename, id, url, storagePath))
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
     * Rename an image.
     * PATCH {supabaseUrl}/rest/v1/images?id=eq.{imageId}
     */
    suspend fun renameImage(imageId: Int, newName: String): Result<JSONObject> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject().put("filename", newName).toString().toRequestBody(JSON)
            val request = Request.Builder()
                .url("$supabaseUrl/rest/v1/images?id=eq.$imageId")
                .headers(apiHeaders(mapOf("Prefer" to "return=representation")))
                .patch(body)
                .build()
            client.newCall(request).execute().use { response ->
                val str = response.body?.string() ?: "[]"
                if (response.isSuccessful) {
                    val arr = JSONArray(str)
                    Result.success(if (arr.length() > 0) arr.getJSONObject(0) else JSONObject())
                } else {
                    Result.failure(Exception("Failed to rename image: $str"))
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
    suspend fun createFolder(name: String, description: String = "", parentFolderId: Int? = null): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val userId = user?.optString("id") ?: return@withContext Result.failure(Exception("Not logged in"))
                val body = JSONObject().apply {
                    put("user_id", userId)
                    put("name", name)
                    put("description", description)
                    if (parentFolderId != null) {
                        put("parent_folder_id", parentFolderId)
                    } else {
                        put("parent_folder_id", JSONObject.NULL)
                    }
                }.toString().toRequestBody(JSON)
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

    /**
     * Move a folder into another folder (updating parent_folder_id).
     * PATCH {supabaseUrl}/rest/v1/folders?id=eq.{folderId}
     */
    suspend fun moveFolder(folderId: Int, parentFolderId: Int?): Result<JSONObject> =
        withContext(Dispatchers.IO) {
            try {
                val body = JSONObject().apply {
                    if (parentFolderId != null && parentFolderId != 0) {
                        put("parent_folder_id", parentFolderId)
                    } else {
                        put("parent_folder_id", JSONObject.NULL)
                    }
                }.toString().toRequestBody(JSON)
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
                        Result.failure(Exception("Failed to move folder: $str"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Delete folder (Promote contents mode)
     */
    suspend fun deleteFolderPromote(folderId: Int, parentFolderId: Int?): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                // 1. Reassign child folders
                val folderBody = JSONObject().apply {
                    if (parentFolderId != null) {
                        put("parent_folder_id", parentFolderId)
                    } else {
                        put("parent_folder_id", JSONObject.NULL)
                    }
                }.toString().toRequestBody(JSON)
                val folderReq = Request.Builder()
                    .url("$supabaseUrl/rest/v1/folders?parent_folder_id=eq.$folderId")
                    .headers(apiHeaders())
                    .patch(folderBody)
                    .build()
                client.newCall(folderReq).execute().close()

                // 2. Reassign child images
                val imageBody = JSONObject().apply {
                    if (parentFolderId != null) {
                        put("folder_id", parentFolderId)
                    } else {
                        put("folder_id", JSONObject.NULL)
                    }
                }.toString().toRequestBody(JSON)
                val imageReq = Request.Builder()
                    .url("$supabaseUrl/rest/v1/images?folder_id=eq.$folderId")
                    .headers(apiHeaders())
                    .patch(imageBody)
                    .build()
                client.newCall(imageReq).execute().close()

                // 3. Delete the folder itself
                val deleteReq = Request.Builder()
                    .url("$supabaseUrl/rest/v1/folders?id=eq.$folderId")
                    .headers(apiHeaders())
                    .delete()
                    .build()
                client.newCall(deleteReq).execute().use { response ->
                    if (response.isSuccessful) Result.success(true)
                    else Result.failure(Exception("Failed to delete folder row"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private data class FolderDeleteHelper(val id: Int, val parentFolderId: Int?)

    private suspend fun getImagesInFolders(folderIds: List<Int>): Result<List<JSONObject>> =
        withContext(Dispatchers.IO) {
            try {
                if (folderIds.isEmpty()) return@withContext Result.success(emptyList())
                val idsStr = folderIds.joinToString(",")
                val request = Request.Builder()
                    .url("$supabaseUrl/rest/v1/images?folder_id=in.($idsStr)&select=id,storage_path")
                    .headers(apiHeaders())
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val array = JSONArray(response.body?.string() ?: "[]")
                        val list = mutableListOf<JSONObject>()
                        for (i in 0 until array.length()) {
                            list.add(array.getJSONObject(i))
                        }
                        Result.success(list)
                    } else {
                        Result.failure(Exception("Failed to get images in folders: ${response.code}"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Delete folder (Delete recursively mode)
     */
    suspend fun deleteFolderRecursive(folderId: Int): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                // 1. Fetch all folders to find children/descendants in memory
                val foldersResult = getFolders()
                val foldersList = mutableListOf<FolderDeleteHelper>()
                val foldersArray = foldersResult.getOrNull()?.optJSONArray("folders")
                if (foldersArray != null) {
                    for (i in 0 until foldersArray.length()) {
                        val obj = foldersArray.getJSONObject(i)
                        foldersList.add(
                            FolderDeleteHelper(
                                id = obj.getInt("id"),
                                parentFolderId = if (obj.isNull("parent_folder_id")) null else obj.getInt("parent_folder_id")
                            )
                        )
                    }
                }

                // Recursive bottom-up retrieval
                val descendants = mutableListOf<Int>()
                fun traverse(fid: Int) {
                    val children = foldersList.filter { it.parentFolderId == fid }
                    for (child in children) {
                        traverse(child.id)
                        descendants.add(child.id)
                    }
                }
                traverse(folderId)
                descendants.add(folderId) // bottom-up: deepest subfolders first, then parents, then folderId at the end

                val idsStr = descendants.joinToString(",")

                // 2. Fetch all images in these folders
                val imagesResult = getImagesInFolders(descendants)
                val imagesToDelete = imagesResult.getOrDefault(emptyList())

                // 3. Delete files from Storage
                val prefixes = JSONArray()
                for (img in imagesToDelete) {
                    val path = img.optString("storage_path")
                    if (!path.isNullOrEmpty()) {
                        prefixes.put(path)
                    }
                }
                if (prefixes.length() > 0) {
                    val delBody = JSONObject().put("prefixes", prefixes)
                        .toString().toRequestBody(JSON)
                    val storageDelRequest = Request.Builder()
                        .url("$supabaseUrl/storage/v1/object/$storageBucket")
                        .headers(apiHeaders())
                        .delete(delBody)
                        .build()
                    client.newCall(storageDelRequest).execute().close()
                }

                // 4. Delete images rows
                val imgDelReq = Request.Builder()
                    .url("$supabaseUrl/rest/v1/images?folder_id=in.($idsStr)")
                    .headers(apiHeaders())
                    .delete()
                    .build()
                client.newCall(imgDelReq).execute().close()

                // 5. Delete folders rows bottom-up
                for (fid in descendants) {
                    val folderDelReq = Request.Builder()
                        .url("$supabaseUrl/rest/v1/folders?id=eq.$fid")
                        .headers(apiHeaders())
                        .delete()
                        .build()
                    client.newCall(folderDelReq).execute().close()
                }

                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
}
