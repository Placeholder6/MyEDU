package kg.oshsu.myedu

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class DictionaryUtils { 
    private val client = OkHttpClient()

    suspend fun fetchDictionary(url: String): Map<String, String> = withContext(Dispatchers.IO) {
        val map = mutableMapOf<String, String>()
        if (url.isBlank()) return@withContext map

        try {
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val jsonStr = response.body?.string() ?: "{}"
                    val json = JSONObject(jsonStr)
                    val keys = json.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = json.optString(key)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext map
    }
}