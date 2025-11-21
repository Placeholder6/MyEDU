package com.example.myedu

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// --- DATA MODELS ---

data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val status: String?, val authorisation: AuthData?)
data class AuthData(val token: String?, val is_student: Boolean?)

data class UserResponse(val user: UserData?)
data class UserData(val id: Long, val name: String?, val last_name: String?, val email: String?)

data class StudentInfoResponse(
    @SerializedName("pdsstudentinfo") val pdsstudentinfo: PdsInfo?,
    @SerializedName("studentMovement") val studentMovement: MovementInfo?,
    @SerializedName("avatar") val avatar: String?,
    @SerializedName("active_semester") val active_semester: Int?
)

data class PdsInfo(
    @SerializedName("passport_number") val passport_number: String?, 
    @SerializedName("serial") val serial: String?,
    @SerializedName("birthday") val birthday: String?, 
    @SerializedName("phone") val phone: String?, 
    @SerializedName("address") val address: String?, 
    @SerializedName("father_full_name") val father_full_name: String?, 
    @SerializedName("mother_full_name") val mother_full_name: String?
) {
    fun getFullPassport(): String {
        val s = (serial ?: "").trim()
        val n = (passport_number ?: "").trim()
        if (s.isEmpty()) return n
        if (n.isEmpty()) return s
        if (s.endsWith(n)) return s
        return "$s$n"
    }
}

data class MovementInfo(
    @SerializedName("id_speciality") val id_speciality: Int?, 
    @SerializedName("id_edu_form") val id_edu_form: Int?, 
    @SerializedName("avn_group_name") val avn_group_name: String?, 
    @SerializedName("speciality") val speciality: NameObj?, 
    @SerializedName("faculty") val faculty: NameObj?, 
    @SerializedName("edu_form") val edu_form: NameObj?
)

data class NameObj(
    @SerializedName("name_en") val name_en: String?, 
    @SerializedName("name_ru") val name_ru: String?, 
    @SerializedName("name_kg") val name_kg: String?,
    @SerializedName("short_name_en") val short_name_en: String?,
    @SerializedName("short_name_ru") val short_name_ru: String?,
    @SerializedName("short_name_kg") val short_name_kg: String?
) {
    fun get(): String {
        val text = name_en ?: name_ru ?: name_kg ?: "Unknown"
        return when (text) {
            "Lection" -> "Lecture"
            "Practical lessons" -> "Practical Class"
            else -> text
        }
    }

    fun format(): String {
        val main = get()
        val short = short_name_en ?: short_name_ru ?: short_name_kg
        return if (!short.isNullOrEmpty() && short != main) "$main ($short)" else main
    }
}

data class EduYear(val id: Int, val name_en: String?, val active: Boolean)
data class ScheduleWrapper(val schedule_items: List<ScheduleItem>?)

data class ScheduleItem(
    val day: Int,
    val id_lesson: Int,
    val subject: NameObj?,
    val teacher: TeacherObj?,
    val room: RoomObj?,
    val subject_type: NameObj?,
    val classroom: ClassroomObj?,
    val stream: StreamObj? 
)

data class StreamObj(val id: Int, val numeric: Int?)

data class ClassroomObj(val building: BuildingObj?)
data class BuildingObj(val name_en: String?, val name_ru: String?, val info_en: String?, val info_ru: String?) {
    fun getName(): String = name_en ?: name_ru ?: "Campus"
    fun getAddress(): String = info_en ?: info_ru ?: ""
}

data class TeacherObj(val name: String?, val last_name: String?) { 
    fun get(): String = "${last_name ?: ""} ${name ?: ""}".trim() 
}
data class RoomObj(val name_en: String?)

// --- FEATURE MODELS ---

data class PayStatusResponse(
    val paid_summa: Double?,
    val need_summa: Double?,
    val access_message: List<String>?
) {
    fun getDebt(): Double = (need_summa ?: 0.0) - (paid_summa ?: 0.0)
}

data class NewsItem(val id: Int, val title: String?, val message: String?, val created_at: String?)

data class LessonTimeResponse(val id_lesson: Int, val begin_time: String?, val end_time: String?, val lesson: LessonNum?)
data class LessonNum(val num: Int)

// --- SESSION / GRADES MODELS ---
data class SessionResponse(
    val semester: SemesterObj?,
    val subjects: List<SessionSubjectWrapper>?
)

data class SemesterObj(val id: Int, val name_en: String?)

data class SessionSubjectWrapper(
    val subject: NameObj?,
    val marklist: MarkList?
)

data class MarkList(
    val point1: Double?, 
    val point2: Double?, 
    val point3: Double?, 
    val finally: Double?, 
    val total: Double?   
)

// Documents
data class DocIdRequest(val id: Long)
data class DocKeyResponse(val key: String?)
data class DocKeyRequest(val key: String)
data class DocUrlResponse(val url: String?)

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

    @GET("public/api/studentPayStatus")
    suspend fun getPayStatus(): PayStatusResponse

    @GET("public/api/appupdate")
    suspend fun getNews(): List<NewsItem>

    @GET("public/api/ep/schedule/schedulelessontime")
    suspend fun getLessonTimes(
        @Query("id_speciality") specId: Int,
        @Query("id_edu_form") formId: Int,
        @Query("id_edu_year") yearId: Int
    ): List<LessonTimeResponse>

    @GET("public/api/studentscheduleitem")
    suspend fun getSchedule(
        @Query("id_speciality") specId: Int,
        @Query("id_edu_form") formId: Int,
        @Query("id_edu_year") yearId: Int,
        @Query("id_semester") semId: Int
    ): List<ScheduleWrapper>

    @GET("public/api/studentsession")
    suspend fun getSession(
        @Query("id_semester") semesterId: Int
    ): List<SessionResponse>

    // --- DOCUMENT ENDPOINTS ---

    // Step 1: Get Key
    @POST("public/api/student/doc/form8link")
    suspend fun getReferenceLink(@Body req: DocIdRequest): DocKeyResponse

    @POST("public/api/student/doc/form13link")
    suspend fun getTranscriptLink(@Body req: DocIdRequest): DocKeyResponse

    // Step 2: Trigger Generation (UPDATED TO RETURN RAW BODY because server sends text "Ok :)")
    @POST("public/api/student/doc/form8")
    suspend fun generateReference(@Body req: DocIdRequest): ResponseBody

    @POST("public/api/student/doc/form13")
    suspend fun generateTranscript(@Body req: DocIdRequest): ResponseBody

    // Step 3: Resolve Key to URL
    @POST("public/api/open/doc/showlink")
    suspend fun resolveDocLink(@Body req: DocKeyRequest): DocUrlResponse
}

// --- NETWORK CLIENT ---

class UniversalCookieJar : CookieJar {
    private val cookieStore = ArrayList<Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val names = cookies.map { it.name }
        cookieStore.removeAll { it.name in names }
        cookieStore.addAll(cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return ArrayList(cookieStore)
    }
    
    fun injectSessionCookies(token: String) {
        val targetUrl = "https://api.myedu.oshsu.kg".toHttpUrlOrNull() ?: return
        
        val jwtCookie = Cookie.Builder()
            .domain("myedu.oshsu.kg")
            .path("/")
            .name("myedu-jwt-token")
            .value(token)
            .build()

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000000'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        val timestamp = sdf.format(Date())
        
        val timeCookie = Cookie.Builder()
            .domain("myedu.oshsu.kg")
            .path("/")
            .name("my_edu_update")
            .value(timestamp)
            .build()
        
        cookieStore.removeAll { it.name == "myedu-jwt-token" || it.name == "my_edu_update" }
        cookieStore.add(jwtCookie)
        cookieStore.add(timeCookie)
    }
    
    fun clear() {
        cookieStore.clear()
    }
}

class WindowsInterceptor : Interceptor {
    var authToken: String? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
        builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        builder.header("Accept", "application/json, text/plain, */*")
        builder.header("Referer", "https://myedu.oshsu.kg/")
        builder.header("Origin", "https://myedu.oshsu.kg")
        
        if (authToken != null) {
            builder.header("Authorization", "Bearer $authToken")
        }
        return chain.proceed(builder.build())
    }
}

object NetworkClient {
    val cookieJar = UniversalCookieJar()
    val interceptor = WindowsInterceptor()
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