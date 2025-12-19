package kg.oshsu.myedu

import com.google.gson.GsonBuilder
import com.google.gson.annotations.SerializedName
import java.util.ArrayList
import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// --- AUTH & USER ---
data class LoginRequest(val email: String, val password: String)
data class LoginResponse(val status: String?, val authorisation: AuthData?)
data class AuthData(val token: String?, val is_student: Boolean?)
data class UserResponse(val user: UserData?)

data class UserData(
    val id: Long, 
    val name: String?, 
    val last_name: String?, 
    val email: String?,
    @SerializedName("father_name") val father_name: String?,
    @SerializedName("id_avn_student") val id_avn_student: Long?,
    @SerializedName("email2") val email2: String?,
    @SerializedName("created_at") val created_at: String?,
    @SerializedName("updated_at") val updated_at: String?,
    @SerializedName("is_pds_approval") val is_pds_approval: Boolean?,
    @SerializedName("id_university") val id_university: Int?,
    @SerializedName("id_user") val id_user: Long?,
    @SerializedName("email_verified_at") val email_verified_at: String?,
    @SerializedName("is_working") val is_working: Boolean?,
    @SerializedName("is_student") val is_student: Boolean?,
    @SerializedName("pin") val pin: String?,
    @SerializedName("id_avn") val id_avn: Long?,
    @SerializedName("id_aryz") val id_aryz: Long?,
    @SerializedName("check") val check: Int?,
    @SerializedName("old_fio") val old_fio: String?,
    @SerializedName("date_prikaz") val date_prikaz: String?,
    @SerializedName("birthday") val birthday: String?,
    @SerializedName("is_reset_password") val is_reset_password: Boolean?,
    @SerializedName("reset_password_updated_at") val reset_password_updated_at: String?,
    @SerializedName("avatar") val avatar: String?
)

// --- STUDENT PROFILE ---
data class StudentInfoResponse(
    @SerializedName("pdsstudentinfo") val pdsstudentinfo: PdsInfo?,
    @SerializedName("studentMovement") val studentMovement: MovementInfo?,
    @SerializedName("pdsstudentmilitary") val pdsstudentmilitary: PdsMilitary?,
    @SerializedName("avatar") val avatar: String?,
    @SerializedName("active_semester") val active_semester: Int?,
    @SerializedName("is_library_debt") val is_library_debt: Boolean?,
    @SerializedName("access_debt_credit_count") val access_debt_credit_count: Double?,
    @SerializedName("is_have_image") val is_have_image: Boolean?,
    @SerializedName("studentlibrary") val studentlibrary: List<Any>?,
    @SerializedName("student_debt_transcript") val student_debt_transcript: List<Any>?,
    @SerializedName("total_price") val total_price: List<Any>?,
    @SerializedName("active_semesters") val active_semesters: List<ActiveSemester>?
)

data class ActiveSemester(
    val id: Int,
    val name_kg: String?,
    val name_ru: String?,
    val name_en: String?,
    val number_name: Int?,
    val id_university: Int?,
    val id_user: Int?,
    val created_at: String?,
    val updated_at: String?
) {
    fun getName(lang: String): String = when (lang) {
        "ky", "kg" -> name_kg ?: name_ru ?: name_en ?: ""
        "en" -> name_en ?: name_ru ?: name_kg ?: ""
        else -> name_ru ?: name_kg ?: name_en ?: ""
    }
}

data class PdsInfo(
    @SerializedName("id") val id: Long,
    @SerializedName("id_student") val id_student: Long?,
    @SerializedName("passport_number") val passport_number: String?, 
    @SerializedName("serial") val serial: String?,
    @SerializedName("birthday") val birthday: String?, 
    @SerializedName("phone") val phone: String?,
    @SerializedName("address") val address: String?,
    @SerializedName("id_male") val id_male: Int?,
    @SerializedName("pin") val pin: String?,
    @SerializedName("release_organ") val release_organ: String?,
    @SerializedName("release_date") val release_date: String?,
    @SerializedName("id_citizenship") val id_citizenship: Int?,
    @SerializedName("id_nationality") val id_nationality: Int?,
    @SerializedName("marital_status") val marital_status: Int?,
    @SerializedName("birth_address") val birth_address: String?,
    @SerializedName("residence_address") val residence_address: String?,
    @SerializedName("residence_phone") val residence_phone: String?,
    @SerializedName("is_ethnic") val is_ethnic: Boolean?,
    @SerializedName("is_have_document") val is_have_document: Boolean?,
    @SerializedName("id_country") val id_country: Int?,
    @SerializedName("id_oblast") val id_oblast: Int?,
    @SerializedName("id_region") val id_region: Int?,
    @SerializedName("id_birth_country") val id_birth_country: Int?,
    @SerializedName("id_birth_oblast") val id_birth_oblast: Int?,
    @SerializedName("id_birth_region") val id_birth_region: Int?,
    @SerializedName("id_residence_country") val id_residence_country: Int?,
    @SerializedName("id_residence_oblast") val id_residence_oblast: Int?,
    @SerializedName("id_residence_region") val id_residence_region: Int?,
    @SerializedName("info") val info: String?,
    @SerializedName("id_round") val id_round: Int?,
    @SerializedName("id_exam_type") val id_exam_type: Int?,
    @SerializedName("father_full_name") val father_full_name: String?,
    @SerializedName("father_phone") val father_phone: String?,
    @SerializedName("father_info") val father_info: String?,
    @SerializedName("mother_full_name") val mother_full_name: String?,
    @SerializedName("mother_phone") val mother_phone: String?,
    @SerializedName("mother_info") val mother_info: String?,
    @SerializedName("id_user") val id_user: Long?,
    @SerializedName("created_at") val created_at: String?,
    @SerializedName("updated_at") val updated_at: String?
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

data class PdsMilitary(
    @SerializedName("id") val id: Long,
    @SerializedName("id_student") val id_student: Long?,
    @SerializedName("id_user") val id_user: Long?,
    @SerializedName("name") val name: String?,
    @SerializedName("name_military") val name_military: String?,
    @SerializedName("serial_number") val serial_number: String?,
    @SerializedName("date") val date: String?,
    @SerializedName("created_at") val created_at: String?,
    @SerializedName("updated_at") val updated_at: String?
)

// --- EXTENDED MOVEMENT MODELS ---
data class MovementInfo(
    @SerializedName("id") val id: Long?,
    @SerializedName("id_movement") val id_movement: Int?,
    @SerializedName("id_student") val id_student: Long?,
    @SerializedName("id_edu_year") val id_edu_year: Int?,
    @SerializedName("id_speciality") val id_speciality: Int?, 
    @SerializedName("id_edu_form") val id_edu_form: Int?, 
    @SerializedName("id_payment_form") val id_payment_form: Int?,
    @SerializedName("info") val info: String?, 
    @SerializedName("date_movement") val date_movement: String?,
    @SerializedName("id_university") val id_university: Int?,
    @SerializedName("id_user") val id_user: Long?,
    @SerializedName("created_at") val created_at: String?,
    @SerializedName("updated_at") val updated_at: String?,
    @SerializedName("id_period") val id_period: Int?,
    @SerializedName("id_tariff_type") val id_tariff_type: Int?,
    @SerializedName("id_avn_group") val id_avn_group: Int?,
    @SerializedName("itngyrg") val itngyrg: Int?,
    @SerializedName("citizenship") val citizenship: Int?,
    @SerializedName("avn_group_name") val avn_group_name: String?, 
    @SerializedName("id_language") val id_language: Int?,
    @SerializedName("id_state_language_level") val id_state_language_level: Int?,
    @SerializedName("id_movement_info") val id_movement_info: Int?,
    @SerializedName("id_import_archive_user") val id_import_archive_user: Long?,
    @SerializedName("info_description") val info_description: String?,
    @SerializedName("id_oo1") val id_oo1: Int?,
    @SerializedName("id_zo1") val id_zo1: Int?,
    
    // Nested Objects
    @SerializedName("speciality") val speciality: SpecialityObj?, 
    @SerializedName("faculty") val faculty: FacultyObj?, 
    @SerializedName("edu_form") val edu_form: EduFormObj?,
    @SerializedName("period") val period: PeriodObj?,
    @SerializedName("payment_form") val payment_form: PaymentFormObj?,
    @SerializedName("movement_info") val movement_info: MovementTypeObj?,
    @SerializedName("language") val language: LanguageObj?
)

data class SpecialityObj(
    val id: Int,
    val name_kg: String?, val name_ru: String?, val name_en: String?,
    val info_kg: String?, val info_ru: String?, val info_en: String?,
    val short_name_kg: String?, val short_name_ru: String?, val short_name_en: String?,
    val code: String?,
    val id_faculty: Int?,
    val id_direction: Int?,
    val id_university: Int?,
    val id_user: Int?,
    val created_at: String?,
    val updated_at: String?,
    val id_oo1: Int?,
    val id_zo1: Int?,
    @SerializedName("faculty") val faculty: FacultyObj?
) {
    fun getName(lang: String): String = when (lang) {
        "ky", "kg" -> name_kg ?: name_ru ?: name_en ?: ""
        "en" -> name_en ?: name_ru ?: name_kg ?: ""
        else -> name_ru ?: name_kg ?: name_en ?: ""
    }
    
    fun getShortName(lang: String): String = when (lang) {
        "ky", "kg" -> short_name_kg ?: short_name_ru ?: short_name_en ?: ""
        "en" -> short_name_en ?: short_name_ru ?: short_name_kg ?: ""
        else -> short_name_ru ?: short_name_kg ?: short_name_en ?: ""
    }
    
    fun getInfo(lang: String): String = when (lang) {
        "ky", "kg" -> info_kg ?: info_ru ?: info_en ?: ""
        "en" -> info_en ?: info_ru ?: info_kg ?: ""
        else -> info_ru ?: info_kg ?: info_en ?: ""
    }
}

data class FacultyObj(
    val id: Int,
    val name_kg: String?, val name_ru: String?, val name_en: String?,
    val info_kg: String?, val info_ru: String?, val info_en: String?,
    val short_name_kg: String?, val short_name_ru: String?, val short_name_en: String?,
    val id_university: Int?,
    val id_user: Int?,
    val id_faculty_type: Int?,
    val created_at: String?,
    val updated_at: String?
) {
    fun getName(lang: String): String = when (lang) {
        "ky", "kg" -> name_kg ?: name_ru ?: name_en ?: ""
        "en" -> name_en ?: name_ru ?: name_kg ?: ""
        else -> name_ru ?: name_kg ?: name_en ?: ""
    }

    fun getShortName(lang: String): String = when (lang) {
        "ky", "kg" -> short_name_kg ?: short_name_ru ?: short_name_en ?: ""
        "en" -> short_name_en ?: short_name_ru ?: short_name_kg ?: ""
        else -> short_name_ru ?: short_name_kg ?: short_name_en ?: ""
    }

    fun getInfo(lang: String): String = when (lang) {
        "ky", "kg" -> info_kg ?: info_ru ?: info_en ?: ""
        "en" -> info_en ?: info_ru ?: info_kg ?: ""
        else -> info_ru ?: info_kg ?: info_en ?: ""
    }
}

data class EduFormObj(
    val id: Int,
    val name_kg: String?, val name_ru: String?, val name_en: String?,
    val short_name_kg: String?, val short_name_ru: String?, val short_name_en: String?,
    val id_university: Int?,
    val id_user: Int?,
    val id_group_edu_form: Int?,
    val status: Boolean?,
    val created_at: String?,
    val updated_at: String?
) {
    fun getName(lang: String): String = when (lang) {
        "ky", "kg" -> name_kg ?: name_ru ?: name_en ?: ""
        "en" -> name_en ?: name_ru ?: name_kg ?: ""
        else -> name_ru ?: name_kg ?: name_en ?: ""
    }

    fun getShortName(lang: String): String = when (lang) {
        "ky", "kg" -> short_name_kg ?: short_name_ru ?: short_name_en ?: ""
        "en" -> short_name_en ?: short_name_ru ?: short_name_kg ?: ""
        else -> short_name_ru ?: short_name_kg ?: short_name_en ?: ""
    }
}

data class PaymentFormObj(
    val id: Int,
    val name_kg: String?, val name_ru: String?, val name_en: String?,
    val id_university: Int?,
    val id_user: Int?,
    val created_at: String?,
    val updated_at: String?
) {
    fun getName(lang: String): String = when (lang) {
        "ky", "kg" -> name_kg ?: name_ru ?: name_en ?: ""
        "en" -> name_en ?: name_ru ?: name_kg ?: ""
        else -> name_ru ?: name_kg ?: name_en ?: ""
    }
}

data class MovementTypeObj(
    val id: Int,
    val name_kg: String?, val name_ru: String?, val name_en: String?,
    val id_university: Int?,
    val id_user: Int?,
    val is_student: Boolean?,
    val created_at: String?,
    val updated_at: String?
) {
    fun getName(lang: String): String = when (lang) {
        "ky", "kg" -> name_kg ?: name_ru ?: name_en ?: ""
        "en" -> name_en ?: name_ru ?: name_kg ?: ""
        else -> name_ru ?: name_kg ?: name_en ?: ""
    }
}

data class PeriodObj(
    val id: Int,
    val name_kg: String?, val name_ru: String?, val name_en: String?,
    val start: String?,
    val id_manual: Int?,
    val id_university: Int?,
    val id_user: Int?,
    val active: String?, 
    val created_at: String?,
    val updated_at: String?
) {
    fun getName(lang: String): String = when (lang) {
        "ky", "kg" -> name_kg ?: name_ru ?: name_en ?: ""
        "en" -> name_en ?: name_ru ?: name_kg ?: ""
        else -> name_ru ?: name_kg ?: name_en ?: ""
    }
}

data class LanguageObj(
    @SerializedName("id") val id: Int,
    @SerializedName("name") val name: String?,
    @SerializedName("short_name") val short_name: String?,
    @SerializedName("id_user") val id_user: Int?,
    @SerializedName("id_university") val id_university: Int?,
    @SerializedName("created_at") val created_at: String?,
    @SerializedName("updated_at") val updated_at: String?
)

data class NameObj(
    @SerializedName("name_en") val name_en: String?, 
    @SerializedName("name_ru") val name_ru: String?, 
    @SerializedName("name_kg") val name_kg: String?,
    @SerializedName("short_name_en") val short_name_en: String?,
    @SerializedName("short_name_ru") val short_name_ru: String?,
    @SerializedName("short_name_kg") val short_name_kg: String?,
    @SerializedName("code") val code: String?,
    @SerializedName("faculty") val faculty: NameObj? 
) {
    fun getName(lang: String): String {
        return when (lang) {
            "ky", "kg" -> name_kg ?: name_ru ?: name_en ?: ""
            "en" -> name_en ?: name_ru ?: name_kg ?: ""
            else -> name_ru ?: name_kg ?: name_en ?: ""
        }
    }
    
    // Legacy fallback for other usages
    fun get(): String = name_en ?: name_ru ?: name_kg ?: "Unknown"
}

// --- SCHEDULE ---
data class EduYear(val id: Int, val name_en: String?, val active: Boolean)
data class ScheduleWrapper(val schedule_items: List<ScheduleItem>?)
data class ScheduleItem(val day: Int, val id_lesson: Int, val subject: NameObj?, val teacher: TeacherObj?, val room: RoomObj?, val subject_type: NameObj?, val classroom: ClassroomObj?, val stream: StreamObj?)
data class StreamObj(val id: Int, val numeric: Int?)
data class ClassroomObj(val building: BuildingObj?)
data class BuildingObj(val name_en: String?, val name_ru: String?, val info_en: String?, val info_ru: String?) {
    fun getName(): String = name_en ?: name_ru ?: "Campus"
    fun getAddress(): String = info_en ?: info_ru ?: ""
}
data class TeacherObj(val name: String?, val last_name: String?) { fun get(): String = "${last_name ?: ""} ${name ?: ""}".trim() }
data class RoomObj(val name_en: String?)
data class LessonTimeResponse(val id_lesson: Int, val begin_time: String?, val end_time: String?, val lesson: LessonNum?)
data class LessonNum(val num: Int)

// --- PAYMENTS & NEWS ---
data class PayStatusResponse(val paid_summa: Double?, val need_summa: Double?, val access_message: List<String>?) { fun getDebt(): Double = (need_summa ?: 0.0) - (paid_summa ?: 0.0) }
data class NewsItem(val id: Int, val title: String?, val message: String?, val created_at: String?)

// --- SESSION / GRADES (UPDATED) ---
data class SessionResponse(
    val semester: SemesterInfo?,
    val subjects: List<SubjectWrapper>?
)

data class SemesterInfo(
    val id: Int,
    val name_en: String?,
    val name_ru: String?,
    val name_kg: String?,
    val number_name: Int?
)

data class SubjectWrapper(
    val subject: SubjectInfo?,
    val marklist: MarkList?,
    val exam: ExamInfo?,
    val graphic: GraphicInfo?
)

data class SubjectInfo(
    val id: Int,
    val name_en: String?,
    val name_ru: String?,
    val name_kg: String?,
    val code: String?
) {
    fun get(lang: String = "en"): String {
        return when (lang) {
            "ky", "kg" -> name_kg ?: name_ru ?: name_en ?: ""
            "en" -> name_en ?: name_ru ?: name_kg ?: ""
            else -> name_ru ?: name_kg ?: name_en ?: ""
        } ?: name_ru ?: "Unknown"
    }
}

data class MarkList(
    val point1: Double?,
    val point2: Double?,
    val point3: Double?,
    @SerializedName("finally") val finalScore: Double?,
    val total: Double?,
    @SerializedName("updated_at") val updated_at: String? // Added for last updated time
)

data class ExamInfo(
    val active: Boolean?
)

data class GraphicInfo(
    val begin: String?,
    val end: String?
)

// --- DOCUMENT MODELS ---
data class DocIdRequest(val id: Long)
data class DocKeyRequest(val key: String)
data class TranscriptYear(@SerializedName("edu_year") val eduYear: String?, @SerializedName("semesters") val semesters: List<TranscriptSemester>?)
data class TranscriptSemester(@SerializedName("semester") val semesterName: String?, @SerializedName("subjects") val subjects: List<TranscriptSubject>?)
data class TranscriptSubject(@SerializedName("subject") val subjectName: String?, @SerializedName("code") val code: String?, @SerializedName("credit") val credit: Double?, @SerializedName("mark_list") val markList: MarkList?, @SerializedName("exam_rule") val examRule: ExamRule?)
data class ExamRule(@SerializedName("alphabetic") val alphabetic: String?, @SerializedName("digital") val digital: Double?, @SerializedName("control_form") val controlForm: String?, @SerializedName("word_ru") val wordRu: String?)

// --- GITHUB UPDATE MODELS ---
data class GitHubRelease(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("body") val body: String,
    @SerializedName("assets") val assets: List<GitHubAsset>
)
data class GitHubAsset(
    @SerializedName("browser_download_url") val downloadUrl: String,
    @SerializedName("name") val name: String,
    @SerializedName("content_type") val contentType: String
)

// --- DICTIONARY MODELS ---
data class DictionaryItem(
    val id: Int,
    val name_kg: String?,
    val name_ru: String?,
    val name_en: String?,
    @SerializedName("id_integration") val idIntegration: Int? = null,
    @SerializedName("id_integration_citizenship") val idIntegrationCitizenship: Int? = null
) {
    fun getName(lang: String): String = when (lang) {
        "ky", "kg" -> name_kg ?: name_ru ?: name_en ?: ""
        "en" -> name_en ?: name_ru ?: name_kg ?: ""
        else -> name_ru ?: name_kg ?: name_en ?: ""
    }
}

data class PeriodItem(
    val id: Int,
    val name_kg: String?,
    val name_ru: String?,
    val name_en: String?,
    val start: String?,
    val active: Int?
)

// --- API INTERFACE ---
interface OshSuApi {
    @POST("public/api/login") suspend fun login(@Body request: LoginRequest): LoginResponse
    @GET("public/api/user") suspend fun getUser(): UserResponse
    @GET("public/api/studentinfo") suspend fun getProfile(): StudentInfoResponse
    @GET("public/api/control/regulations/eduyear") suspend fun getYears(): List<EduYear>
    @GET("public/api/studentPayStatus") suspend fun getPayStatus(): PayStatusResponse
    @GET("public/api/appupdate") suspend fun getNews(): List<NewsItem>
    @GET("public/api/ep/schedule/schedulelessontime") suspend fun getLessonTimes(@Query("id_speciality") specId: Int, @Query("id_edu_form") formId: Int, @Query("id_edu_year") yearId: Int): List<LessonTimeResponse>
    @GET("public/api/studentscheduleitem") suspend fun getSchedule(@Query("id_speciality") specId: Int, @Query("id_edu_form") formId: Int, @Query("id_edu_year") yearId: Int, @Query("id_semester") semId: Int): List<ScheduleWrapper>
    @GET("public/api/studentsession") suspend fun getSession(@Query("id_semester") semesterId: Int): List<SessionResponse>
    @GET("public/api/studenttranscript") suspend fun getTranscript(@Query("id_student") studentId: Long, @Query("id_movement") movementId: Long): List<TranscriptYear>

    // --- DOCS: RAW ENDPOINTS ---
    @GET("public/api/searchstudentinfo") 
    suspend fun getStudentInfoRaw(@Query("id_student") studentId: Long): ResponseBody

    @GET("public/api/studenttranscript")
    suspend fun getTranscriptDataRaw(@Query("id_student") sId: Long, @Query("id_movement") mId: Long): ResponseBody

    @GET("public/api/control/structure/specialitylicense")
    suspend fun getSpecialityLicense(@Query("id_speciality") sId: Int, @Query("id_edu_form") eId: Int): ResponseBody

    @GET("public/api/control/structure/university")
    suspend fun getUniversityInfo(): ResponseBody
    
    // DOCS: Form 13 (Transcript)
    @POST("public/api/student/doc/form13link") suspend fun getTranscriptLink(@Body req: DocIdRequest): ResponseBody
    @Multipart @POST("public/api/student/doc/form13") suspend fun uploadPdf(@Part("id") id: RequestBody, @Part("id_student") idStudent: RequestBody, @Part pdf: MultipartBody.Part): ResponseBody

    // DOCS: Form 8 (Reference)
    @POST("public/api/student/doc/form8link") suspend fun getReferenceLink(@Body req: DocIdRequest): ResponseBody
    @Multipart @POST("public/api/student/doc/form8") suspend fun uploadReferencePdf(@Part("id") id: RequestBody, @Part("id_student") idStudent: RequestBody, @Part pdf: MultipartBody.Part): ResponseBody

    // DOCS: Shared
    @POST("public/api/open/doc/showlink") suspend fun resolveDocLink(@Body req: DocKeyRequest): ResponseBody

    // --- DICTIONARY ENDPOINTS ---
    @GET("public/api/open/pdscountry")
    suspend fun getCountries(): List<DictionaryItem>
    @GET("public/api/open/pdsoblast")
    suspend fun getOblasts(): List<DictionaryItem>
    @GET("public/api/open/pdsregion")
    suspend fun getRegions(): List<DictionaryItem>
    @GET("public/api/open/pdsnational")
    suspend fun getNationalities(): List<DictionaryItem>
    @GET("public/api/open/pdsschool")
    suspend fun getSchools(): List<DictionaryItem>
    @GET("public/api/open/pdsmale")
    suspend fun getGenders(): List<DictionaryItem>
    @GET("public/api/control/regulations/period")
    suspend fun getPeriods(): List<PeriodItem>
}

// --- GITHUB API INTERFACE ---
interface GitHubApi {
    @GET
    suspend fun getLatestRelease(@Url url: String): GitHubRelease
}

// --- NETWORK CLIENT SETUP ---
class UniversalCookieJar : CookieJar {
    private val cookieStore = ArrayList<Cookie>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) { val names = cookies.map { it.name }; cookieStore.removeAll { it.name in names }; cookieStore.addAll(cookies) }
    override fun loadForRequest(url: HttpUrl): List<Cookie> = ArrayList(cookieStore)
    fun injectSessionCookies(token: String) {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.000000'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        cookieStore.removeAll { it.name == "myedu-jwt-token" || it.name == "my_edu_update" }
        cookieStore.add(Cookie.Builder().domain("myedu.oshsu.kg").path("/").name("myedu-jwt-token").value(token).build())
        cookieStore.add(Cookie.Builder().domain("myedu.oshsu.kg").path("/").name("my_edu_update").value(sdf.format(Date())).build())
    }
    fun clear() { cookieStore.clear() }
}

class WindowsInterceptor : Interceptor {
    var authToken: String? = null
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("Accept", "application/json, text/plain, */*")
            .header("Referer", "https://myedu.oshsu.kg/")
            .header("Origin", "https://myedu.oshsu.kg")
        
        if (authToken != null) builder.header("Authorization", "Bearer $authToken")
        return chain.proceed(builder.build())
    }
}

object NetworkClient {
    val cookieJar = UniversalCookieJar()
    val interceptor = WindowsInterceptor()
    
    val api: OshSuApi = Retrofit.Builder().baseUrl("https://api.myedu.oshsu.kg/")
        .client(OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor(interceptor)
            .connectTimeout(60, TimeUnit.SECONDS) // Increased timeout for heavy PDF ops
            .readTimeout(60, TimeUnit.SECONDS)
            .build())
        .addConverterFactory(GsonConverterFactory.create(GsonBuilder().setLenient().create()))
        .build().create(OshSuApi::class.java)

    // Separate Client for GitHub
    val githubApi: GitHubApi = Retrofit.Builder().baseUrl("https://api.github.com/")
        .client(OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build())
        .addConverterFactory(GsonConverterFactory.create())
        .build().create(GitHubApi::class.java)
}