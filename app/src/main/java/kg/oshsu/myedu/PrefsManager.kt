package kg.oshsu.myedu

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PrefsManager(context: Context) {
    // @PublishedApi makes these accessible to the inline function below
    @PublishedApi
    internal val prefs: SharedPreferences = context.getSharedPreferences("myedu_offline_cache", Context.MODE_PRIVATE)
    
    @PublishedApi
    internal val gson = Gson()

    // --- AUTH TOKEN MANAGEMENT ---
    fun saveToken(token: String) {
        prefs.edit().putString("auth_token", token).apply()
    }

    fun getToken(): String? {
        return prefs.getString("auth_token", null)
    }

    // --- ONBOARDING & SETTINGS ---
    fun setOnboardingComplete(done: Boolean) {
        prefs.edit().putBoolean("onboarding_complete", done).apply()
    }

    fun isOnboardingComplete(): Boolean {
        return prefs.getBoolean("onboarding_complete", false)
    }

    fun saveSettings(name: String?, photoUri: String?, theme: String, notifications: Boolean) {
        prefs.edit()
            .putString("custom_name", name)
            .putString("custom_photo", photoUri)
            .putString("app_theme", theme)
            .putBoolean("notifications_enabled", notifications)
            .apply()
    }

    fun getCustomName(): String? = prefs.getString("custom_name", null)
    fun getCustomPhoto(): String? = prefs.getString("custom_photo", null)
    fun getAppTheme(): String = prefs.getString("app_theme", "system") ?: "system"
    fun areNotificationsEnabled(): Boolean = prefs.getBoolean("notifications_enabled", true)

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    // --- DATA SAVING (GENERIC) ---
    fun <T> saveData(key: String, data: T) {
        val json = gson.toJson(data)
        prefs.edit().putString(key, json).apply()
    }

    // --- DATA LOADING (GENERIC) ---
    fun <T> loadData(key: String, type: Class<T>): T? {
        val json = prefs.getString(key, null) ?: return null
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            null
        }
    }

    // --- LIST SAVING ---
    fun <T> saveList(key: String, list: List<T>) {
        val json = gson.toJson(list)
        prefs.edit().putString(key, json).apply()
    }

    // --- LIST LOADING ---
    inline fun <reified T> loadList(key: String): List<T> {
        val json = prefs.getString(key, null) ?: return emptyList()
        val type = object : TypeToken<List<T>>() {}.type
        return try {
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }
}