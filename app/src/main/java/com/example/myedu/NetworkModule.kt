package com.example.myedu

import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers

// API DEFINITION (Only Data Endpoints)
interface OshSuApi {
    @GET("public/api/user")
    suspend fun getUser(): ResponseBody

    // --- SCANNER ENDPOINTS ---
    @GET("public/api/studentSession")
    suspend fun scanSession(): ResponseBody

    @GET("public/api/studentCurricula")
    suspend fun scanCurricula(): ResponseBody

    @GET("public/api/student_mark_list")
    suspend fun scanMarkList(): ResponseBody

    @GET("public/api/student/transcript")
    suspend fun scanTranscript(): ResponseBody
}

object TokenStore {
    var cookies: String? = null
    var userAgent: String? = null
}

class GhostInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        // Use the credentials stolen by the Ghost Browser
        TokenStore.userAgent?.let { builder.header("User-Agent", it) }
        TokenStore.cookies?.let { builder.header("Cookie", it) }
        
        // Extract Bearer Token from cookies for Double-Lock
        TokenStore.cookies?.let {
            val jwt = it.split(";").find { c -> c.trim().startsWith("myedu-jwt-token=") }
            if (jwt != null) {
                val token = jwt.substringAfter("=").trim()
                builder.header("Authorization", "Bearer $token")
            }
        }

        builder.header("Referer", "https://myedu.oshsu.kg/")
        builder.header("Accept", "application/json, text/plain, */*")

        return chain.proceed(builder.build())
    }
}

object NetworkClient {
    private const val BASE_URL = "https://api.myedu.oshsu.kg/" 

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(GhostInterceptor())
        .build()

    val api: OshSuApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OshSuApi::class.java)
}
