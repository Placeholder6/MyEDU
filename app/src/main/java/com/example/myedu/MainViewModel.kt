package com.example.myedu

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class MainViewModel : ViewModel() {
    var appState by mutableStateOf("LOGIN")
    var currentTab by mutableStateOf(0)
    var isLoading by mutableStateOf(false)
    var errorMsg by mutableStateOf<String?>(null)
    
    var userData by mutableStateOf<UserData?>(null)
    var profileData by mutableStateOf<StudentInfoResponse?>(null)
    
    var payStatus by mutableStateOf<PayStatusResponse?>(null)
    var newsList by mutableStateOf<List<NewsItem>>(emptyList())
    var timeMap by mutableStateOf<Map<Int, String>>(emptyMap()) 
    
    var fullSchedule by mutableStateOf<List<ScheduleItem>>(emptyList())
    var todayClasses by mutableStateOf<List<ScheduleItem>>(emptyList())
    var todayDayName by mutableStateOf("Today")
    
    var determinedStream by mutableStateOf<Int?>(null)
    var determinedGroup by mutableStateOf<Int?>(null)
    
    var sessionData by mutableStateOf<List<SessionResponse>>(emptyList())
    var isGradesLoading by mutableStateOf(false)
    
    var docUrl by mutableStateOf<String?>(null)
    var docLoading by mutableStateOf(false)
    var docError by mutableStateOf<String?>(null)
    
    var selectedClass by mutableStateOf<ScheduleItem?>(null)

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            isLoading = true
            errorMsg = null
            NetworkClient.cookieJar.clear()
            NetworkClient.interceptor.authToken = null
            try {
                val cleanEmail = email.trim()
                val cleanPass = pass.trim()
                val resp = withContext(Dispatchers.IO) { NetworkClient.api.login(LoginRequest(cleanEmail, cleanPass)) }
                val token = resp.authorisation?.token
                
                if (token != null) {
                    NetworkClient.interceptor.authToken = token
                    NetworkClient.cookieJar.injectSessionCookies(token)
                    
                    val user = withContext(Dispatchers.IO) { NetworkClient.api.getUser().user }
                    val profile = withContext(Dispatchers.IO) { NetworkClient.api.getProfile() }
                    userData = user
                    profileData = profile
                    
                    if (profile != null) {
                        loadDashboardData(profile)
                        fetchSession(profile)
                    }
                    appState = "APP"
                } else {
                    errorMsg = "Incorrect credentials"
                }
            } catch (e: Exception) {
                errorMsg = "Login Failed: ${e.message}"
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    private fun loadDashboardData(profile: StudentInfoResponse) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                try { newsList = NetworkClient.api.getNews() } catch(e:Exception) {}
                try { payStatus = NetworkClient.api.getPayStatus() } catch(e:Exception) {}

                val mov = profile.studentMovement
                if (mov?.id_speciality != null && mov.id_edu_form != null && profile.active_semester != null) {
                    val years = NetworkClient.api.getYears()
                    val activeYearId = years.find { it.active }?.id ?: 25
                    
                    val times = try {
                        NetworkClient.api.getLessonTimes(mov.id_speciality, mov.id_edu_form, activeYearId)
                    } catch (e:Exception) { emptyList() }
                    
                    timeMap = times.associate { 
                        (it.lesson?.num ?: 0) to "${it.begin_time ?: ""} - ${it.end_time ?: ""}" 
                    }

                    val wrappers = NetworkClient.api.getSchedule(mov.id_speciality, mov.id_edu_form, activeYearId, profile.active_semester)
                    val allItems = wrappers.flatMap { it.schedule_items ?: emptyList() }
                    fullSchedule = allItems.sortedBy { it.id_lesson }
                    
                    determinedStream = fullSchedule.asSequence().filter { it.subject_type?.get() == "Lecture" }.mapNotNull { it.stream?.numeric }.firstOrNull()
                    determinedGroup = fullSchedule.asSequence().filter { it.subject_type?.get() == "Practical Class" }.mapNotNull { it.stream?.numeric }.firstOrNull()
                    
                    val cal = Calendar.getInstance()
                    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) 
                    todayDayName = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: "Today"
                    val apiDay = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - 2
                    todayClasses = fullSchedule.filter { it.day == apiDay }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    private fun fetchSession(profile: StudentInfoResponse) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isGradesLoading = true
                val semId = profile.active_semester ?: return@launch
                sessionData = NetworkClient.api.getSession(semId)
            } catch (e: Exception) { e.printStackTrace() } 
            finally { isGradesLoading = false }
        }
    }
    
    fun downloadDocument(type: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                docLoading = true
                docUrl = null
                docError = null
                val uid = userData?.id ?: return@launch
                
                // 1. Get Key
                val keyResp = if (type == "reference") {
                    NetworkClient.api.getReferenceLink(DocIdRequest(uid))
                } else {
                    NetworkClient.api.getTranscriptLink(DocIdRequest(uid))
                }
                
                if (keyResp.key != null) {
                    // 2. Trigger Generation (Using POST now, consuming response)
                    try {
                        if (type == "reference") {
                            NetworkClient.api.generateReference(DocIdRequest(uid)).close() 
                        } else {
                            NetworkClient.api.generateTranscript(DocIdRequest(uid)).close()
                        }
                    } catch (e: Exception) {
                        // Ignore non-JSON parsing issues if any, though ResponseBody should handle it
                    }

                    // 3. Resolve URL
                    val urlResp = NetworkClient.api.resolveDocLink(DocKeyRequest(keyResp.key))
                    if (urlResp.url != null) {
                        docUrl = urlResp.url
                    } else {
                        docError = "Document URL not found"
                    }
                } else {
                    docError = "Failed to generate document key"
                }
            } catch (e: Exception) { 
                docError = "Network error: ${e.message}"
                e.printStackTrace() 
            } finally { 
                docLoading = false 
            }
        }
    }
    
    fun getTimeString(lessonId: Int): String {
        return timeMap[lessonId] ?: "Pair $lessonId"
    }

    fun logout() {
        appState = "LOGIN"
        currentTab = 0
        userData = null
        profileData = null
        payStatus = null
        newsList = emptyList()
        fullSchedule = emptyList()
        timeMap = emptyMap()
        sessionData = emptyList()
        selectedClass = null
        determinedStream = null
        determinedGroup = null
        docUrl = null
        NetworkClient.cookieJar.clear()
        NetworkClient.interceptor.authToken = null
    }
}
