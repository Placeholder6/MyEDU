package com.example.myedu

import java.util.concurrent.TimeUnit
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
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
    var jwtToken: String? = null
}

class MixerInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val builder = original.newBuilder()

        // Mimic Chrome
        builder.header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Mobile Safari/537.36")
        builder.header("Referer", "https://myedu.oshsu.kg/#/main")
        builder.header("Accept", "application/json, text/plain, */*")

        // Inject Token into BOTH Header and Cookie
        TokenStore.jwtToken?.let { token ->
            builder.header("Authorization", "Bearer $token")
            builder.header("Cookie", "myedu-jwt-token=$token")
        }

        return chain.proceed(builder.build())
    }
}

object NetworkClient {
    private const val BASE_URL = "https://api.myedu.oshsu.kg/" 

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(MixerInterceptor())
        .build()

    val api: OshSuApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OshSuApi::class.java)
}
