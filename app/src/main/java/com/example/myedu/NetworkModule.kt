package com.example.myedu

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface OshSuApi {
    // Verify URL in Chrome Network Tab!
    @POST("index.php?do=login") 
    suspend fun login(@Body body: okhttp3.RequestBody): String

    @GET("index.php?do=grades")
    suspend fun getGradesPage(): String
}

class SessionCookieJar : CookieJar {
    private val cookieStore = mutableListOf<Cookie>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore.clear()
        cookieStore.addAll(cookies)
    }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore
}

object NetworkClient {
    private const val BASE_URL = "https://myedu.oshsu.kg/" 
    private val cookieJar = SessionCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    val api: OshSuApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()
        .create(OshSuApi::class.java)
}
