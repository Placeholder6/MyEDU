package com.example.myedu

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

// --- DATA MODELS ---

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val status: String, val authorisation: AuthData)
data class AuthData(val token: String, val type: String, val is_student: Boolean)

// Profile Data Structure (Based on your logs)
data class StudentInfoResponse(
    val avatar: String?,
    val studentMovement: StudentMovement?
)

data class StudentMovement(
    val avn_group_name: String?, // e.g. "ИНл-16-21"
    val speciality: NameData?,
    val faculty: NameData?
)

data class NameData(
    val name_en: String? // e.g. "General Medicine"
)

// --- API DEFINITION ---

interface OshSuApi {
    @Headers("Content-Type: application/json", "Accept: application/json")
    @POST("public/api/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @Headers("Accept: application/json")
    @GET("public/api/studentinfo")
    suspend fun getStudentInfo(@Header("Authorization") token: String): StudentInfoResponse
}

// --- CLIENT ---

object NetworkClient {
    private const val BASE_URL = "https://api.myedu.oshsu.kg/" 

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    val api: OshSuApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OshSuApi::class.java)
}
