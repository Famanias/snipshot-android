package com.example.snipshot

import android.content.SharedPreferences
import com.example.snipshot.api.ApiClient
import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class ApiClientTest {

    // Simple manual mock of SharedPreferences.Editor
    class MockEditor : SharedPreferences.Editor {
        val storage = mutableMapOf<String, Any?>()
        override fun putString(key: String?, value: String?): SharedPreferences.Editor {
            if (key != null) storage[key] = value
            return this
        }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
            if (key != null) storage[key] = values
            return this
        }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
            if (key != null) storage[key] = value
            return this
        }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
            if (key != null) storage[key] = value
            return this
        }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
            if (key != null) storage[key] = value
            return this
        }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
            if (key != null) storage[key] = value
            return this
        }
        override fun remove(key: String?): SharedPreferences.Editor {
            if (key != null) storage.remove(key)
            return this
        }
        override fun clear(): SharedPreferences.Editor {
            storage.clear()
            return this
        }
        override fun commit(): Boolean = true
        override fun apply() {}
    }

    // Simple manual mock of SharedPreferences
    class MockSharedPreferences : SharedPreferences {
        val storage = mutableMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = storage
        override fun getString(key: String?, defValue: String?): String? = (storage[key] as? String) ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = (storage[key] as? MutableSet<String>) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = (storage[key] as? Int) ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = (storage[key] as? Long) ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = (storage[key] as? Float) ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = (storage[key] as? Boolean) ?: defValue
        override fun contains(key: String?): Boolean = storage.containsKey(key)
        override fun edit(): SharedPreferences.Editor = MockEditor()
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    }

    @Test
    fun testLogoutClearsAccessTokenAndRefreshToken() {
        // Set mock SharedPreferences using reflection
        val field = ApiClient::class.java.getDeclaredField("prefs")
        field.isAccessible = true
        val mockPrefs = MockSharedPreferences()
        field.set(ApiClient, mockPrefs)

        // Set access token and refresh token via reflection
        val tokenField = ApiClient::class.java.getDeclaredField("accessToken")
        tokenField.isAccessible = true
        tokenField.set(ApiClient, "fake-access-token")

        val refreshField = ApiClient::class.java.getDeclaredField("refreshToken")
        refreshField.isAccessible = true
        refreshField.set(ApiClient, "fake-refresh-token")

        // Assert tokens are set
        assertEquals("fake-access-token", ApiClient.accessToken)
        assertEquals("fake-refresh-token", ApiClient.refreshToken)

        // Perform logout
        ApiClient.logout()

        // Verify tokens are now null
        assertNull(ApiClient.accessToken)
        assertNull(ApiClient.refreshToken)
        
        println("Android ApiClient.logout() verification passed successfully!")
    }
}
