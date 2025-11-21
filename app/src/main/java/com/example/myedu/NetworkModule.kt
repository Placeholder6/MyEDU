package com.example.myedu

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

// --- SIMPLE DATA MODELS ---
data class DocIdRequest(val id: Long)
data class DocKeyRequest(val key: String)

// --- API INTERFACE (Raw Responses for Debugging) ---
interface OshSuApi {
    // Step 1: Get the Key
    @POST("public/api/student/doc/form13link")
    suspend fun getTranscriptLink(@Body req: DocIdRequest): ResponseBody

    // Step 2: Trigger Generation
    @POST("public/api/student/doc/form13")
    suspend fun generateTranscript(@Body req: DocIdRequest): ResponseBody

    // Step 3: Resolve Key to URL
    @POST("public/api/open/doc/showlink")
    suspend fun resolveDocLink(@Body req: DocKeyRequest): ResponseBody
}

// --- MANUAL COOKIE INJECTOR ---
class DebugCookieJar : CookieJar {
    private val cookieStore = ArrayList<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {}

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return ArrayList(cookieStore)
    }
    
    fun setDebugCookies(token: String) {
        cookieStore.clear()
        
        // 1. JWT Token
        cookieStore.add(Cookie.Builder()
            .domain("myedu.oshsu.kg")
            .path("/")
            .name("myedu-jwt-token")
            .value(token)
            .build())

        // 2. Timestamp (Mimicking Browser)
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000000'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = sdf.format(Date())
        
        cookieStore.add(Cookie.Builder()
            .domain("myedu.oshsu.kg")
            .path("/")
            .name("my_edu_update")
            .value(timestamp)
            .build())
    }
}

class DebugInterceptor : Interceptor {
    var authToken: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()

        // EXACT HEADERS FROM YOUR LOGS
        builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36")
        builder.header("Accept", "application/json, text/plain, */*")
        builder.header("Referer", "https://myedu.oshsu.kg/#/main")
        builder.header("Origin", "https://myedu.oshsu.kg")
        
        if (authToken != null) {
            builder.header("Authorization", "Bearer $authToken")
        }

        return chain.proceed(builder.build())
    }
}

object NetworkClient {
    val cookieJar = DebugCookieJar()
    val interceptor = DebugInterceptor()

    val api: OshSuApi = Retrofit.Builder()
        .baseUrl("https://api.myedu.oshsu.kg/")
        .client(OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(interceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build())
        .addConverterFactory(GsonConverterFactory.create()) 
        .build()
        .create(OshSuApi::class.java)
}