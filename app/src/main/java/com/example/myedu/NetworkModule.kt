package com.example.myedu

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import okhttp3.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

// --- LOGGING SYSTEM ---
object DebugLog {
    private val sb = StringBuilder()
    var onUpdate: ((String) -> Unit)? = null

    fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        val entry = "[$time] $msg\n"
        sb.append(entry)
        Log.d("MyEDU_Debug", msg)
        onUpdate?.invoke(sb.toString())
    }
    
    fun clear() { sb.setLength(0); onUpdate?.invoke("") }
    fun getFullLog(): String = sb.toString()
}

// --- API ---
data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val status: String?, val authorisation: AuthData?)
data class AuthData(val token: String?, val is_student: Boolean?)

interface OshSuApi {
    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("public/api/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("public/api/user")
    suspend fun getUser(): ResponseBody
}

// --- STATE MANAGEMENT ---
object SessionConfig {
    var useManualCookie: String? = null
    var useCookieJar: Boolean = false
    val cookieJar = MemoryCookieJar()
}

class MemoryCookieJar : CookieJar {
    private val cache = HashMap<String, List<Cookie>>()
    
    fun clear() { cache.clear() }
    
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (SessionConfig.useCookieJar) {
            cache[url.host] = cookies
            DebugLog.log("🍪 CookieJar saved ${cookies.size} cookies.")
            cookies.forEach { DebugLog.log("   -> ${it.name}=${it.value.take(10)}...") }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return if (SessionConfig.useCookieJar) {
            val cookies = cache[url.host] ?: ArrayList()
            if (cookies.isNotEmpty()) DebugLog.log("🍪 CookieJar loading ${cookies.size} cookies")
            cookies
        } else {
            ArrayList()
        }
    }
}

class DiagnosticInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        // 1. BASE HEADERS (Chrome Mimic)
        builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36")
        builder.header("Referer", "https://myedu.oshsu.kg/#/main")
        builder.header("Accept", "application/json, text/plain, */*")

        // 2. MANUAL INJECTION (Method B)
        SessionConfig.useManualCookie?.let { cookieStr ->
            builder.header("Cookie", cookieStr)
            // Extract Bearer
            if (cookieStr.contains("myedu-jwt-token=")) {
                val token = cookieStr.split("myedu-jwt-token=")[1].split(";")[0]
                builder.header("Authorization", "Bearer $token")
                DebugLog.log("💉 Injecting Header + Cookie manually")
            }
        }

        val request = builder.build()
        DebugLog.log("🚀 SENDING ${request.method} ${request.url}")
        DebugLog.log("   Headers: ${request.headers.names()}")
        
        try {
            val response = chain.proceed(request)
            DebugLog.log("⬅️ RECEIVED ${response.code}")
            return response
        } catch (e: Exception) {
            DebugLog.log("💥 NETWORK ERROR: ${e.message}")
            throw e
        }
    }
}

object NetworkClient {
    private val client = OkHttpClient.Builder()
        .cookieJar(SessionConfig.cookieJar)
        .connectTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(DiagnosticInterceptor())
        .build()

    val api: OshSuApi = Retrofit.Builder()
        .baseUrl("https://api.myedu.oshsu.kg/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OshSuApi::class.java)
}
