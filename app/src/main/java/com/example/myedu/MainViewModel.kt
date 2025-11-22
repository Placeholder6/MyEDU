package com.example.myedu

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale

class MainViewModel : ViewModel() {
    var appState by mutableStateOf("STARTUP")
    var currentTab by mutableStateOf(0)
    var isLoading by mutableStateOf(false)
    var errorMsg by mutableStateOf<String?>(null)
    
    var userData by mutableStateOf<UserData?>(null)
    var profileData by mutableStateOf<StudentInfoResponse?>(null)
    var payStatus by mutableStateOf<PayStatusResponse?>(null)
    var newsList by mutableStateOf<List<NewsItem>>(emptyList())
    var fullSchedule by mutableStateOf<List<ScheduleItem>>(emptyList())
    var todayClasses by mutableStateOf<List<ScheduleItem>>(emptyList())
    var timeMap by mutableStateOf<Map<Int, String>>(emptyMap())
    var todayDayName by mutableStateOf("Today")
    
    var determinedStream by mutableStateOf<Int?>(null)
    var determinedGroup by mutableStateOf<Int?>(null)
    var selectedClass by mutableStateOf<ScheduleItem?>(null)
    var sessionData by mutableStateOf<List<SessionResponse>>(emptyList())
    var isGradesLoading by mutableStateOf(false)
    
    // --- DOCUMENT STATES ---
    var transcriptData by mutableStateOf<List<TranscriptYear>>(emptyList())
    var isTranscriptLoading by mutableStateOf(false)
    var showTranscriptScreen by mutableStateOf(false)
    var showReferenceScreen by mutableStateOf(false)
    var isPdfGenerating by mutableStateOf(false)
    var pdfStatusMessage by mutableStateOf<String?>(null)

    private var prefs: PrefsManager? = null

    fun initSession(context: Context) {
        if (prefs == null) {
            prefs = PrefsManager(context)
            val token = prefs?.getToken()
            if (token != null) {
                NetworkClient.interceptor.authToken = token
                NetworkClient.cookieJar.injectSessionCookies(token)
                loadOfflineData()
                appState = "APP"
                refreshAllData()
            } else appState = "LOGIN"
        }
    }

    private fun loadOfflineData() {
        prefs?.let { p ->
            userData = p.loadData("user_data", UserData::class.java)
            profileData = p.loadData("profile_data", StudentInfoResponse::class.java)
            payStatus = p.loadData("pay_status", PayStatusResponse::class.java)
            newsList = p.loadList("news_list")
            fullSchedule = p.loadList("schedule_list")
            sessionData = p.loadList("session_list")
            transcriptData = p.loadList<TranscriptYear>("transcript_list")
            processScheduleLocally()
        }
    }

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            isLoading = true; errorMsg = null; NetworkClient.cookieJar.clear(); NetworkClient.interceptor.authToken = null
            try {
                val resp = withContext(Dispatchers.IO) { NetworkClient.api.login(LoginRequest(email.trim(), pass.trim())) }
                val token = resp.authorisation?.token
                if (token != null) {
                    prefs?.saveToken(token)
                    NetworkClient.interceptor.authToken = token
                    NetworkClient.cookieJar.injectSessionCookies(token)
                    refreshAllData()
                    appState = "APP"
                } else errorMsg = "Incorrect credentials"
            } catch (e: Exception) { errorMsg = "Login Failed: ${e.message}" }
            isLoading = false
        }
    }

    fun logout() {
        appState = "LOGIN"; currentTab = 0; userData = null; profileData = null; payStatus = null; newsList = emptyList(); fullSchedule = emptyList(); sessionData = emptyList(); transcriptData = emptyList()
        prefs?.clearAll(); NetworkClient.cookieJar.clear(); NetworkClient.interceptor.authToken = null
    }

    private fun refreshAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = NetworkClient.api.getUser().user
                val profile = NetworkClient.api.getProfile()
                withContext(Dispatchers.Main) {
                    userData = user; profileData = profile
                    prefs?.saveData("user_data", user); prefs?.saveData("profile_data", profile)
                }
                if (profile != null) {
                    try { val news = NetworkClient.api.getNews(); withContext(Dispatchers.Main) { newsList = news; prefs?.saveList("news_list", news) } } catch (_: Exception) {}
                    try { val pay = NetworkClient.api.getPayStatus(); withContext(Dispatchers.Main) { payStatus = pay; prefs?.saveData("pay_status", pay) } } catch (_: Exception) {}
                    loadScheduleNetwork(profile)
                    fetchSession(profile)
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun loadScheduleNetwork(profile: StudentInfoResponse) {
        val mov = profile.studentMovement ?: return
        try {
            val years = NetworkClient.api.getYears()
            val activeYearId = years.find { it.active }?.id ?: 25
            val times = try { NetworkClient.api.getLessonTimes(mov.id_speciality!!, mov.id_edu_form!!, activeYearId) } catch (e: Exception) { emptyList() }
            val wrappers = NetworkClient.api.getSchedule(mov.id_speciality!!, mov.id_edu_form!!, activeYearId, profile.active_semester ?: 1)
            withContext(Dispatchers.Main) {
                timeMap = times.associate { (it.lesson?.num ?: 0) to "${it.begin_time ?: ""} - ${it.end_time ?: ""}" }
                fullSchedule = wrappers.flatMap { it.schedule_items ?: emptyList() }.sortedBy { it.id_lesson }
                prefs?.saveList("schedule_list", fullSchedule)
                processScheduleLocally()
            }
        } catch (_: Exception) {}
    }

    private fun processScheduleLocally() {
        if (fullSchedule.isEmpty()) return
        determinedStream = fullSchedule.asSequence().filter { it.subject_type?.get() == "Lecture" }.mapNotNull { it.stream?.numeric }.firstOrNull()
        determinedGroup = fullSchedule.asSequence().filter { it.subject_type?.get() == "Practical Class" }.mapNotNull { it.stream?.numeric }.firstOrNull()
        val cal = Calendar.getInstance()
        todayDayName = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: "Today"
        val apiDay = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 6 else cal.get(Calendar.DAY_OF_WEEK) - 2
        todayClasses = fullSchedule.filter { it.day == apiDay }
    }

    private fun fetchSession(profile: StudentInfoResponse) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { isGradesLoading = true }
                val session = NetworkClient.api.getSession(profile.active_semester ?: 1)
                withContext(Dispatchers.Main) { sessionData = session; prefs?.saveList("session_list", session) }
            } catch (_: Exception) {} finally { withContext(Dispatchers.Main) { isGradesLoading = false } }
        }
    }

    fun fetchTranscript() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { 
                    isTranscriptLoading = true
                    showTranscriptScreen = true
                    // Explicit type call here for safety
                    transcriptData = prefs?.loadList<TranscriptYear>("transcript_list") ?: emptyList() 
                }
                val uid = userData?.id ?: return@launch
                val movId = profileData?.studentMovement?.id ?: return@launch 
                val transcript = NetworkClient.api.getTranscript(uid, movId)
                withContext(Dispatchers.Main) { transcriptData = transcript; prefs?.saveList("transcript_list", transcript) }
            } catch (_: Exception) {} finally { withContext(Dispatchers.Main) { isTranscriptLoading = false } }
        }
    }
    
    fun getTimeString(lessonId: Int) = timeMap[lessonId] ?: "Pair $lessonId"

    // --- PDF GENERATION ---
    fun generateAndOpenTranscript(context: Context) {
        if (isPdfGenerating || transcriptData.isEmpty()) return
        val studentId = userData?.id ?: return
        val fullName = "${userData?.last_name ?: ""} ${userData?.name ?: ""}"
        viewModelScope.launch {
            isPdfGenerating = true; pdfStatusMessage = "Preparing Transcript..."
            try {
                val keyRaw = withContext(Dispatchers.IO) { NetworkClient.api.getTranscriptLink(DocIdRequest(studentId)).string() }
                val keyObj = JSONObject(keyRaw); val key = keyObj.optString("key"); val linkId = keyObj.optLong("id")
                val transcriptJson = Gson().toJson(transcriptData)
                val pds = profileData?.pdsstudentinfo; val mov = profileData?.studentMovement
                val infoMap = mapOf("birthday" to (pds?.birthday?:""), "speciality" to (mov?.speciality?.name_ru?:""), "direction" to (mov?.speciality?.name_ru?:""), "edu_form" to (mov?.edu_form?.name_ru?:""))
                val file = withContext(Dispatchers.Default) { PdfGenerator(context).generateTranscriptPdf(transcriptJson, fullName, studentId.toString(), Gson().toJson(infoMap)) }
                if (file != null) { pdfStatusMessage = "Uploading..."; uploadAndOpen(context, linkId, studentId, file, "transcript.pdf", key, true) } else pdfStatusMessage = "Generation Failed"
            } catch (e: Exception) { pdfStatusMessage = "Error: ${e.message}" } finally { isPdfGenerating = false; delay(3000); pdfStatusMessage = null }
        }
    }

    fun generateAndOpenReference(context: Context) {
        if (isPdfGenerating) return
        val studentId = userData?.id ?: return
        val fullName = "${userData?.last_name ?: ""} ${userData?.name ?: ""}"
        viewModelScope.launch {
            isPdfGenerating = true; pdfStatusMessage = "Preparing Reference..."
            try {
                val keyRaw = withContext(Dispatchers.IO) { NetworkClient.api.getReferenceLink(DocIdRequest(studentId)).string() }
                val keyObj = JSONObject(keyRaw); val key = keyObj.optString("key"); val linkId = keyObj.optLong("id")
                val mov = profileData?.studentMovement
                val infoMap = mapOf("speciality_ru" to (mov?.speciality?.name_ru?:""), "spec_code" to (mov?.speciality?.code?:""), "edu_form_ru" to (mov?.edu_form?.name_ru?:""), "payment_form_ru" to (if (mov?.id_payment_form == 2) "Контракт" else "Бюджет"), "active_semester" to (profileData?.active_semester ?: 1), "second_id" to "1713914806")
                val file = withContext(Dispatchers.Default) { PdfGenerator(context).generateReferencePdf(fullName, studentId.toString(), Gson().toJson(infoMap)) }
                if (file != null) { pdfStatusMessage = "Uploading..."; uploadAndOpen(context, linkId, studentId, file, "reference.pdf", key, false) } else pdfStatusMessage = "Generation Failed"
            } catch (e: Exception) { pdfStatusMessage = "Error: ${e.message}" } finally { isPdfGenerating = false; delay(3000); pdfStatusMessage = null }
        }
    }

    private suspend fun uploadAndOpen(context: Context, linkId: Long, studentId: Long, file: java.io.File, filename: String, key: String, isTranscript: Boolean) {
        val plain = "text/plain".toMediaTypeOrNull(); val pdfType = "application/pdf".toMediaTypeOrNull()
        val bodyId = linkId.toString().toRequestBody(plain); val bodyStudent = studentId.toString().toRequestBody(plain)
        val filePart = MultipartBody.Part.createFormData("pdf", filename, file.asRequestBody(pdfType))
        withContext(Dispatchers.IO) { if (isTranscript) NetworkClient.api.uploadPdf(bodyId, bodyStudent, filePart).string() else NetworkClient.api.uploadReferencePdf(bodyId, bodyStudent, filePart).string() }
        delay(1000)
        val resRaw = withContext(Dispatchers.IO) { NetworkClient.api.resolveDocLink(DocKeyRequest(key)).string() }
        val url = JSONObject(resRaw).optString("url")
        if (url.isNotEmpty()) { val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }; context.startActivity(i); pdfStatusMessage = null } else pdfStatusMessage = "URL not found"
    }
}
