package com.example.myedu

import java.util.concurrent.TimeUnit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

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

object TokenStore {
    var jwtToken: String? = null
}

// INTERCEPTOR: The "Mirror" Logic
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        // 1. STANDARD BROWSER FINGERPRINT
        builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36")
        builder.header("Referer", "https://myedu.oshsu.kg/#/main")
        builder.header("Accept", "application/json, text/plain, */*")
        builder.header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8") // New!
        builder.header("Connection", "keep-alive") // New!
        
        // 2. INJECT TOKEN & COOKIES
        TokenStore.jwtToken?.let { token ->
            // A. Header Token
            builder.header("Authorization", "Bearer $token")
            
            // B. Generate Timestamp for Cookie (e.g. 2025-11-03T17:41:25.000000Z)
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000000'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            val timestamp = sdf.format(Date())
            
            // C. Construct EXACT Cookie String
            val cookieString = "myedu-jwt-token=$token; my_edu_update=$timestamp"
            builder.header("Cookie", cookieString)
        }

        return chain.proceed(builder.build())
    }
}

object NetworkClient {
    private const val BASE_URL = "https://api.myedu.oshsu.kg/" 

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AuthInterceptor())
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.HEADERS }) // Log Headers!
        .build()

    val api: OshSuApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OshSuApi::class.java)
}
