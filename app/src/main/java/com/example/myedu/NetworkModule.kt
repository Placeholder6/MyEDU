package com.example.myedu

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST

// --- DATA MODELS (Matched to your JSON) ---

data class LoginRequest(
    val email: String,
    val password: String
)

data class LoginResponse(
    val status: String,         // "success"
    val authorisation: AuthData // The nested object
)

data class AuthData(
    val token: String,          // The actual key we need
    val type: String,           // "bearer"
    val is_student: Boolean
)

// --- API DEFINITION ---

interface OshSuApi {
    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("public/api/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse
}

// --- CLIENT ---

object NetworkClient {
    private const val BASE_URL = "https://api.myedu.oshsu.kg/" 

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { 
            level = HttpLoggingInterceptor.Level.BODY 
        })
        .build()

    val api: OshSuApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OshSuApi::class.java)
}
