package com.example.myedu

import com.google.gson.GsonBuilder
import java.util.ArrayList
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
import retrofit2.http.Query

// --- DATA MODELS ---

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val status: String?, val authorisation: AuthData?)
data class AuthData(val token: String?, val is_student: Boolean?)

data class UserResponse(val user: UserData?)
data class UserData(val id: Long, val name: String?, val last_name: String?, val email: String?)

data class StudentInfoResponse(
    val pdsstudentinfo: PdsInfo?,
    val studentMovement: MovementInfo?,
    val avatar: String?,
    val active_semester: Int?
)

data class PdsInfo(val passport_number: String?, val birthday: String?, val phone: String?, val address: String?, val father_full_name: String?, val mother_full_name: String?)
data class MovementInfo(val id_speciality: Int?, val id_edu_form: Int?, val avn_group_name: String?, val speciality: NameObj?, val faculty: NameObj?, val edu_form: NameObj?)
data class NameObj(val name_en: String?, val name_ru: String?, val name_kg: String?) {
    fun get(): String = name_en ?: name_ru ?: name_kg ?: "Unknown"
}

data class EduYear(val id: Int, val name_en: String?, val active: Boolean)
data class ScheduleWrapper(val schedule_items: List<ScheduleItem>?)
data class ScheduleItem(val day: Int, val id_lesson: Int, val subject: NameObj?, val teacher: TeacherObj?, val room: RoomObj?, val subject_type: NameObj?)
data class TeacherObj(val name: String?, val last_name: String?) { fun get(): String = "${last_name ?: ""} ${name ?: ""}".trim() }
data class RoomObj(val name_en: String?)

// --- API INTERFACE ---

interface OshSuApi {
    @POST("public/api/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("public/api/user")
    suspend fun getUser(): UserResponse

    @GET("public/api/studentinfo")
    suspend fun getProfile(): StudentInfoResponse

    @GET("public/api/control/regulations/eduyear")
    suspend fun getYears(): List<EduYear>

    @GET("public/api/studentscheduleitem")
    suspend fun getSchedule(
        @Query("id_speciality") specId: Int,
        @Query("id_edu_form") formId: Int,
        @Query("id_edu_year") yearId: Int,
        @Query("id_semester") semId: Int
    ): List<ScheduleWrapper>
}

// --- UNIVERSAL COOKIE ENGINE ---

class UniversalCookieJar : CookieJar {
    // A simple list that ignores domain matching rules to ensure cookies are always sent
    private val cookieStorage = ArrayList<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        // Remove old cookies with same name to update them
        val names = cookies.map { it.name }
        cookieStorage.removeAll { it.name in names }
        cookieStorage.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return ArrayList(cookieStorage)
    }
    
    fun clear() {
        cookieStorage.clear()
    }
}

class WindowsInterceptor : Interceptor {
    var authToken: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()

        // 1. WINDOWS HEADERS (Mimics Chrome on Windows)
        builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        builder.header("Accept", "application/json, text/plain, */*")
        builder.header("Referer", "https://myedu.oshsu.kg/")
        builder.header("Origin", "https://myedu.oshsu.kg")
        
        // 2. SECURITY HEADERS
        builder.header("sec-ch-ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
        builder.header("sec-ch-ua-mobile", "?0")
        builder.header("sec-ch-ua-platform", "\"Windows\"")
        builder.header("Sec-Fetch-Dest", "empty")
        builder.header("Sec-Fetch-Mode", "cors")
        builder.header("Sec-Fetch-Site", "same-site")
        builder.header("X-Requested-With", "XMLHttpRequest")

        // 3. AUTH TOKEN
        if (authToken != null) {
            builder.header("Authorization", "Bearer $authToken")
        }

        return chain.proceed(builder.build())
    }
}

object NetworkClient {
    val cookieJar = UniversalCookieJar()
    val interceptor = WindowsInterceptor()

    // Use Lenient Gson to prevent crashes on server errors
    private val gson = GsonBuilder().setLenient().create()

    val api: OshSuApi = Retrofit.Builder()
        .baseUrl("https://api.myedu.oshsu.kg/")
        .client(OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(interceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .build())
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(OshSuApi::class.java)
}