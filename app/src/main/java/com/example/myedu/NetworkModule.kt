package com.example.myedu

import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.TimeUnit
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
import retrofit2.http.GET
import retrofit2.http.POST

// --- JSON DATA MODELS ---

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val status: String?, val authorisation: AuthData?)
data class AuthData(val token: String?, val is_student: Boolean?)

// Response from /api/user
data class UserResponse(
    val user: UserData?
)
data class UserData(
    val id: Long,
    val name: String?,
    val last_name: String?,
    val email: String?
)

// Response from /api/studentinfo
data class StudentInfoResponse(
    val pdsstudentinfo: PdsInfo?,
    val studentMovement: MovementInfo?,
    val avatar: String?
)

data class PdsInfo(
    val passport_number: String?,
    val birthday: String?,
    val phone: String?,
    val address: String?,
    val father_full_name: String?,
    val mother_full_name: String?
)

data class MovementInfo(
    val avn_group_name: String?, // "ИНл-16-21"
    val speciality: NameObj?,
    val faculty: NameObj?,
    val edu_form: NameObj?
)

data class NameObj(
    val name_en: String?,
    val name_ru: String?,
    val name_kg: String?
) {
    // Helper to grab the best language available
    fun get(): String = name_en ?: name_ru ?: name_kg ?: "Unknown"
}

// --- API INTERFACE ---

interface OshSuApi {
    @POST("public/api/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("public/api/user")
    suspend fun getUser(): UserResponse

    @GET("public/api/studentinfo")
    suspend fun getProfile(): StudentInfoResponse
}

// --- COOKIES & HEADERS (Windows Engine) ---

class WindowsCookieJar : CookieJar {
    private val cookieStore = HashMap<String, List<Cookie>>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { cookieStore[url.host] = cookies }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore[url.host] ?: ArrayList()
}

class WindowsInterceptor : Interceptor {
    var authToken: String? = null
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        
        // Headers that worked for you
        builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        builder.header("Accept", "application/json, text/plain, */*")
        builder.header("Referer", "https://myedu.oshsu.kg/")
        builder.header("Origin", "https://myedu.oshsu.kg")
        
        if (authToken != null) builder.header("Authorization", "Bearer $authToken")
        
        return chain.proceed(builder.build())
    }
}

object NetworkClient {
    private val cookieJar = WindowsCookieJar()
    val interceptor = WindowsInterceptor()

    val api: OshSuApi = Retrofit.Builder()
        .baseUrl("https://api.myedu.oshsu.kg/")
        .client(OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(interceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(OshSuApi::class.java)
}
