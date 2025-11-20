package com.example.myedu

import java.util.concurrent.TimeUnit
import java.util.ArrayList
import android.util.Log
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
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
    suspend fun getUser(@Header("Authorization") token: String): ResponseBody
}

// COOKIE JAR (The missing piece)
class SessionCookieJar : CookieJar {
    private val cookieStore = ArrayList<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore.clear()
        cookieStore.addAll(cookies)
        Log.d("COOKIES", "Saved ${cookies.size} cookies")
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        Log.d("COOKIES", "Loading ${cookieStore.size} cookies")
        return cookieStore
    }
}

object NetworkClient {
    private const val BASE_URL = "https://api.myedu.oshsu.kg/" 
    private val cookieJar = SessionCookieJar()

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar) // <--- CRITICAL ADDITION
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val original = chain.request()
            val request = original.newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36")
                .header("Referer", "https://myedu.oshsu.kg/#/main")
                .header("Accept", "application/json, text/plain, */*")
                .method(original.method, original.body)
                .build()
            chain.proceed(request)
        }
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    val api: OshSuApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OshSuApi::class.java)
}
