package com.example.myedu

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

// --- VIEWMODEL ---
class MainViewModel : ViewModel() {
    var appState by mutableStateOf("LOGIN")
    var currentTab by mutableStateOf(0)
    var isLoading by mutableStateOf(false)
    var errorMsg by mutableStateOf<String?>(null)
    
    var userData by mutableStateOf<UserData?>(null)
    var profileData by mutableStateOf<StudentInfoResponse?>(null)
    var fullSchedule by mutableStateOf<List<ScheduleItem>>(emptyList())
    var todayClasses by mutableStateOf<List<ScheduleItem>>(emptyList())
    var todayDayName by mutableStateOf("Today")
    
    // State for Class Details Navigation
    var selectedClass by mutableStateOf<ScheduleItem?>(null)

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            isLoading = true
            errorMsg = null
            
            // Clear any previous session data to ensure a clean login
            NetworkClient.cookieJar.clear()
            NetworkClient.interceptor.authToken = null
            
            try {
                // 1. Clean input
                val cleanEmail = email.trim()
                val cleanPass = pass.trim()
                
                // 2. Perform Login API Call
                val resp = withContext(Dispatchers.IO) { 
                    NetworkClient.api.login(LoginRequest(cleanEmail, cleanPass)) 
                }
                
                // 3. Check for Token
                val token = resp.authorisation?.token
                
                if (token != null) {
                    // 4. Set up Session (Token + Cookies)
                    NetworkClient.interceptor.authToken = token
                    NetworkClient.cookieJar.injectSessionCookies(token)
                    
                    // 5. Fetch User & Profile Data
                    val user = withContext(Dispatchers.IO) { NetworkClient.api.getUser().user }
                    val profile = withContext(Dispatchers.IO) { NetworkClient.api.getProfile() }
                    userData = user
                    profileData = profile
                    
                    // 6. Fetch Schedule if profile is valid
                    if (profile != null) fetchSchedule(profile)
                    
                    // 7. Navigate to App
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

    private suspend fun fetchSchedule(profile: StudentInfoResponse) {
        try {
            val mov = profile.studentMovement
            // Ensure we have the necessary IDs to query the schedule
            if (mov?.id_speciality != null && mov.id_edu_form != null && profile.active_semester != null) {
                
                // Get Active Academic Year
                val years = withContext(Dispatchers.IO) { NetworkClient.api.getYears() }
                val activeYearId = years.find { it.active }?.id ?: 25 
                
                // Fetch Schedule Wrapper
                val wrappers = withContext(Dispatchers.IO) {
                    NetworkClient.api.getSchedule(
                        specId = mov.id_speciality,
                        formId = mov.id_edu_form,
                        yearId = activeYearId,
                        semId = profile.active_semester
                    )
                }
                
                // Flatten the list of items
                val allItems = wrappers.flatMap { it.schedule_items ?: emptyList() }
                fullSchedule = allItems.sortedBy { it.id_lesson }
                
                // Determine "Today"
                val cal = Calendar.getInstance()
                val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) 
                todayDayName = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: "Today"
                
                // Convert Java Calendar Day (Sun=1) to API Day (Mon=1)
                // If Sunday (1), set to 0 (or handle as "No Class")
                val apiDay = if(dayOfWeek == Calendar.SUNDAY) 0 else dayOfWeek - 1 
                
                // Filter classes for today
                todayClasses = fullSchedule.filter { it.day == apiDay }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun logout() {
        appState = "LOGIN"
        currentTab = 0
        userData = null
        profileData = null
        fullSchedule = emptyList()
        todayClasses = emptyList()
        selectedClass = null
        
        // Clear networking session
        NetworkClient.cookieJar.clear()
        NetworkClient.interceptor.authToken = null
    }
}