package com.example.myedu

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

// --- DATA MODELS ---
data class DocIdRequest(val id: Long)
data class DocKeyRequest(val key: String)

// --- API INTERFACE ---
interface OshSuApi {
    // 1. Get Student Info (for Movement ID)
    @GET("public/api/searchstudentinfo")
    suspend fun getStudentInfo(@Query("id_student") studentId: Long): ResponseBody

    // 2. Get Transcript Data (The Marksheet JSON)
    @GET("public/api/studenttranscript")
    suspend fun getTranscriptData(
        @Query("id_student") studentId: Long,
        @Query("id_movement") movementId: Long
    ): ResponseBody

    // 3. Get Document Key (Link ID)
    @POST("public/api/student/doc/form13link")
    suspend fun getTranscriptLink(@Body req: DocIdRequest): ResponseBody

    // 4. Generate PDF (Sends JSON data + Dummy PDF)
    @Multipart
    @POST("public/api/student/doc/form13")
    suspend fun generateTranscript(
        @Part("id") id: RequestBody,
        @Part("id_student") idStudent: RequestBody,
        @Part("id_movement") idMovement: RequestBody,
        @Part("contents") contents: RequestBody, // <--- The JSON string goes here as text
        @Part pdf: MultipartBody.Part
    ): ResponseBody

    // 5. Resolve Final URL
    @POST("public/api/open/doc/showlink")
    suspend fun resolveDocLink(@Body req: DocKeyRequest): ResponseBody
}

// --- COOKIE INJECTOR ---
class DebugCookieJar : CookieJar {
    private val cookieStore = ArrayList<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { newCookie ->
            val iterator = cookieStore.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().name == newCookie.name) iterator.remove()
            }
            cookieStore.add(newCookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return ArrayList(cookieStore)
    }
    
    fun setDebugCookies(token: String) {
        cookieStore.clear()
        val cleanToken = token.removePrefix("Bearer ").trim()
        
        cookieStore.add(Cookie.Builder()
            .domain("myedu.oshsu.kg")
            .path("/")
            .name("myedu-jwt-token")
            .value(cleanToken)
            .build())

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

// --- INTERCEPTOR WITH DYNAMIC REFERER ---
class DebugInterceptor : Interceptor {
    var authToken: String? = null
    // Mutable Referer allows us to update it per-step
    var currentReferer: String = "https://myedu.oshsu.kg/" 

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        builder.header("Accept", "application/json, text/plain, */*")
        builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36")
        builder.header("Origin", "https://myedu.oshsu.kg")
        builder.header("Referer", currentReferer)
        
        if (authToken != null) {
            val cleanToken = authToken!!.removePrefix("Bearer ").trim()
            builder.header("Authorization", "Bearer $cleanToken")
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