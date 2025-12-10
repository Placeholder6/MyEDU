package kg.oshsu.myedu

import android.app.Application
import android.content.Context
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.Locale

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    // Helper to get strings in ViewModel
    private fun getString(resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    // --- STATE: APP STATUS ---
    var appState by mutableStateOf("STARTUP")
    var currentTab by mutableStateOf(0)
    var isLoading by mutableStateOf(false)
    var isRefreshing by mutableStateOf(false)
    var isLoginSuccess by mutableStateOf(false) 
    var errorMsg by mutableStateOf<String?>(null)

    // --- STATE: LOGIN CREDENTIALS ---
    var loginEmail by mutableStateOf("")
    var loginPass by mutableStateOf("")
    var rememberMe by mutableStateOf(false)

    // --- STATE: USER DATA ---
    var userData by mutableStateOf<UserData?>(null)
    var profileData by mutableStateOf<StudentInfoResponse?>(null)
    var payStatus by mutableStateOf<PayStatusResponse?>(null)
    var newsList by mutableStateOf<List<NewsItem>>(emptyList())

    // --- STATE: CUSTOM UI DATA ---
    var customName by mutableStateOf<String?>(null)
    var customPhotoUri by mutableStateOf<String?>(null)
    var appTheme by mutableStateOf("system") 
    var notificationsEnabled by mutableStateOf(true)

    // --- STATE: SETTINGS ---
    var showSettingsScreen by mutableStateOf(false)
    var downloadMode by mutableStateOf("IN_APP")
    var language by mutableStateOf("en")
    var showDictionaryScreen by mutableStateOf(false)

    val uiName: String
        get() = customName ?: userData?.let { "${it.last_name ?: ""} ${it.name ?: ""}".trim() } ?: getString(R.string.student_default)
    
    val uiPhoto: Any?
        get() = customPhotoUri ?: profileData?.avatar

    // --- STATE: SCHEDULE DATA ---
    var fullSchedule by mutableStateOf<List<ScheduleItem>>(emptyList())
    var todayClasses by mutableStateOf<List<ScheduleItem>>(emptyList())
    var timeMap by mutableStateOf<Map<Int, String>>(emptyMap())
    var todayDayName by mutableStateOf("") 
    var determinedStream by mutableStateOf<Int?>(null)
    var determinedGroup by mutableStateOf<Int?>(null)
    var selectedClass by mutableStateOf<ScheduleItem?>(null)

    // --- STATE: GRADES DATA ---
    var sessionData by mutableStateOf<List<SessionResponse>>(emptyList())
    var isGradesLoading by mutableStateOf(false)

    // --- STATE: DOCUMENTS & PDF ---
    var transcriptData by mutableStateOf<List<TranscriptYear>>(emptyList())
    var isTranscriptLoading by mutableStateOf(false)
    var showTranscriptScreen by mutableStateOf(false)
    var showReferenceScreen by mutableStateOf(false)
    var isPdfGenerating by mutableStateOf(false)
    var pdfStatusMessage by mutableStateOf<String?>(null)

    // --- STATE: DICTIONARY ---
    var dictionaryUrl by mutableStateOf("https://gist.githubusercontent.com/Placeholder6/71c6a6638faf26c7858d55a1e73b7aef/raw/myedudictionary.json")
    
    private var remoteDictionary: Map<String, String> = emptyMap()
    private var customDictionary: MutableMap<String, String> = mutableMapOf()
    
    // Exposed merged dictionary for UI and Logic
    var combinedDictionary by mutableStateOf<Map<String, String>>(emptyMap())

    private var prefs: PrefsManager? = null

    // --- RESOURCE CACHE ---
    private val jsFetcher = JsResourceFetcher()
    private val refFetcher = ReferenceJsFetcher()
    private val dictUtils = DictionaryUtils()

    private var cachedResourcesRu: PdfResources? = null
    private var cachedResourcesEn: PdfResources? = null
    private var cachedRefResourcesRu: ReferenceResources? = null
    private var cachedRefResourcesEn: ReferenceResources? = null

    // --- SETTINGS ACTIONS ---
    fun setTheme(theme: String) {
        appTheme = theme
        prefs?.saveSettings(customName ?: "", customPhotoUri, theme, notificationsEnabled)
    }

    fun setDocMode(mode: String) {
        downloadMode = mode
    }

    fun setAppLanguage(lang: String) {
        language = lang
        prefs?.saveAppLanguage(lang)
    }

    // --- DICTIONARY ACTIONS ---
    fun addOrUpdateDictionaryEntry(original: String, translation: String) {
        customDictionary[original] = translation
        updateCombinedDictionary()
        prefs?.saveCustomDictionary(customDictionary)
    }

    fun removeDictionaryEntry(original: String) {
        customDictionary.remove(original)
        updateCombinedDictionary()
        prefs?.saveCustomDictionary(customDictionary)
    }

    fun resetDictionaryToDefault() {
        customDictionary.clear()
        updateCombinedDictionary()
        prefs?.saveCustomDictionary(customDictionary)
    }

    private fun updateCombinedDictionary() {
        combinedDictionary = remoteDictionary + customDictionary
    }

    private suspend fun fetchDictionaryIfNeeded() {
        if (remoteDictionary.isEmpty() && dictionaryUrl.isNotBlank()) {
            remoteDictionary = dictUtils.fetchDictionary(dictionaryUrl)
        }
        if (customDictionary.isEmpty()) {
            customDictionary = prefs?.getCustomDictionary()?.toMutableMap() ?: mutableMapOf()
        }
        withContext(Dispatchers.Main) {
            updateCombinedDictionary()
        }
    }

    // --- INIT: CHECK SESSION ---
    fun initSession(context: Context) {
        viewModelScope.launch {
            if (prefs == null) {
                prefs = PrefsManager(context)
            }
            prefs?.let { p ->
                customName = p.getCustomName()
                customPhotoUri = p.getCustomPhoto()
                appTheme = p.getAppTheme()
                notificationsEnabled = p.areNotificationsEnabled()
                language = p.getAppLanguage()
                customDictionary = p.getCustomDictionary().toMutableMap()
                
                // Load Saved Credentials
                val isRemember = p.loadData("pref_remember_me", Boolean::class.java) ?: false
                rememberMe = isRemember
                if (isRemember) {
                    loginEmail = p.loadData("pref_saved_email", String::class.java) ?: ""
                    loginPass = p.loadData("pref_saved_pass", String::class.java) ?: ""
                }
            }

            val token = prefs?.getToken()
            
            if (token != null) {
                NetworkClient.interceptor.authToken = token
                NetworkClient.cookieJar.injectSessionCookies(token)
                loadOfflineData()
                
                if (prefs?.isOnboardingComplete() == true) {
                    appState = "APP"
                } else {
                    appState = "ONBOARDING"
                }

                launch(Dispatchers.IO) {
                    try { 
                        fetchAllDataSuspend()
                        fetchDictionaryIfNeeded()
                    } catch (_: Exception) {}
                }
            } else {
                appState = "LOGIN"
            }
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

    // --- AUTH: LOGIN LOGIC ---
    fun login(email: String, pass: String) {
        viewModelScope.launch {
            isLoading = true
            isLoginSuccess = false
            errorMsg = null
            NetworkClient.cookieJar.clear()
            NetworkClient.interceptor.authToken = null

            // Save credentials if Remember Me is active
            if (rememberMe) {
                prefs?.saveData("pref_remember_me", true)
                prefs?.saveData("pref_saved_email", email)
                prefs?.saveData("pref_saved_pass", pass)
            } else {
                prefs?.saveData("pref_remember_me", false)
                prefs?.saveData("pref_saved_email", "")
                prefs?.saveData("pref_saved_pass", "")
            }

            try {
                val resp = withContext(Dispatchers.IO) { NetworkClient.api.login(LoginRequest(email.trim(), pass.trim())) }
                val token = resp.authorisation?.token
                if (token != null) {
                    prefs?.saveToken(token)
                    NetworkClient.interceptor.authToken = token
                    NetworkClient.cookieJar.injectSessionCookies(token)

                    try {
                        withContext(Dispatchers.IO) { fetchAllDataSuspend() }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    isLoginSuccess = true
                    delay(200) 

                    if (prefs?.isOnboardingComplete() == true) {
                        appState = "APP"
                    } else {
                        if (customName == null) {
                            customName = "${userData?.last_name ?: ""} ${userData?.name ?: ""}".trim()
                        }
                        appState = "ONBOARDING"
                    }
                } else {
                    errorMsg = getString(R.string.error_credentials)
                    isLoading = false
                }
            } catch (e: Exception) {
                errorMsg = getString(R.string.error_login_failed, e.message ?: "Unknown")
                isLoading = false
            }
        }
    }

    // --- ONBOARDING ACTIONS ---
    fun saveOnboardingSettings(name: String, photo: String?, theme: String, notifications: Boolean) {
        customName = name
        customPhotoUri = photo
        appTheme = theme
        notificationsEnabled = notifications
        
        prefs?.saveSettings(name, photo, theme, notifications)
        prefs?.setOnboardingComplete(true)
        
        appState = "APP"
    }

    fun logout() {
        appState = "LOGIN"
        
        // Preserve credentials if Remember Me was active
        val wasRemember = rememberMe
        val savedE = loginEmail
        val savedP = loginPass

        currentTab = 0
        userData = null
        profileData = null
        payStatus = null
        newsList = emptyList()
        fullSchedule = emptyList()
        sessionData = emptyList()
        transcriptData = emptyList()
        prefs?.clearAll()
        NetworkClient.cookieJar.clear()
        NetworkClient.interceptor.authToken = null
        
        isLoading = false
        isLoginSuccess = false
        customName = null
        customPhotoUri = null
        appTheme = "system"
        notificationsEnabled = true
        showSettingsScreen = false
        showDictionaryScreen = false

        if (wasRemember) {
            prefs?.saveData("pref_remember_me", true)
            prefs?.saveData("pref_saved_email", savedE)
            prefs?.saveData("pref_saved_pass", savedP)
            loginEmail = savedE
            loginPass = savedP
            rememberMe = true
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isRefreshing = true }
            try {
                fetchAllDataSuspend()
            } catch (e: Exception) {
                if (e.message?.contains("401") == true) { withContext(Dispatchers.Main) { logout() } }
            } finally {
                withContext(Dispatchers.Main) { isRefreshing = false }
            }
        }
    }

    private suspend fun fetchAllDataSuspend() {
        val user = NetworkClient.api.getUser().user
        val profile = NetworkClient.api.getProfile()

        withContext(Dispatchers.Main) {
            userData = user
            profileData = profile
            prefs?.saveData("user_data", user)
            prefs?.saveData("profile_data", profile)
        }

        try { val news = NetworkClient.api.getNews(); withContext(Dispatchers.Main) { newsList = news; prefs?.saveList("news_list", news) } } catch (_: Exception) {}
        try { val pay = NetworkClient.api.getPayStatus(); withContext(Dispatchers.Main) { payStatus = pay; prefs?.saveData("pay_status", pay) } } catch (_: Exception) {}

        if (profile != null) {
            loadScheduleNetwork(profile)
            fetchSessionSuspend(profile)
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
        todayDayName = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()) ?: getString(R.string.today)
        val apiDay = if (cal.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY) 6 else cal.get(Calendar.DAY_OF_WEEK) - 2
        todayClasses = fullSchedule.filter { it.day == apiDay }
    }

    private suspend fun fetchSessionSuspend(profile: StudentInfoResponse) {
        try {
            withContext(Dispatchers.Main) { if(!isRefreshing) isGradesLoading = true }
            val session = NetworkClient.api.getSession(profile.active_semester ?: 1)
            withContext(Dispatchers.Main) { sessionData = session; prefs?.saveList("session_list", session) }
        } catch (_: Exception) {} finally { withContext(Dispatchers.Main) { isGradesLoading = false } }
    }

    fun fetchTranscript() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) { 
                    isTranscriptLoading = true
                    showTranscriptScreen = true
                    transcriptData = prefs?.loadList<TranscriptYear>("transcript_list") ?: emptyList() 
                }
                val uid = userData?.id ?: return@launch
                val movId = profileData?.studentMovement?.id ?: return@launch 
                val transcript = NetworkClient.api.getTranscript(uid, movId)
                withContext(Dispatchers.Main) { transcriptData = transcript; prefs?.saveList("transcript_list", transcript) }
            } catch (_: Exception) {} finally { withContext(Dispatchers.Main) { isTranscriptLoading = false } }
        }
    }
    
    fun getTimeString(lessonId: Int) = timeMap[lessonId] ?: "${getString(R.string.pair)} $lessonId"

    fun generateTranscriptPdf(context: Context, language: String) {
        if (isPdfGenerating) return
        val studentId = userData?.id ?: return
        
        viewModelScope.launch {
            isPdfGenerating = true
            pdfStatusMessage = getString(R.string.status_preparing_transcript)
            try {
                fetchDictionaryIfNeeded()
                val dictToUse = combinedDictionary
                
                var resources = if (language == "en") cachedResourcesEn else cachedResourcesRu
                if (resources == null) {
                    pdfStatusMessage = getString(R.string.status_fetching_scripts)
                    resources = jsFetcher.fetchResources({ println(it) }, language, dictToUse)
                    if (language == "en") cachedResourcesEn = resources else cachedResourcesRu = resources
                }

                val infoRaw = withContext(Dispatchers.IO) { NetworkClient.api.getStudentInfoRaw(studentId).string() }
                val infoJson = JSONObject(infoRaw)
                val fullName = "${infoJson.optString("last_name")} ${infoJson.optString("name")} ${infoJson.optString("father_name")}".replace("null", "").trim()
                infoJson.put("fullName", fullName)

                val movId = profileData?.studentMovement?.id ?: 0L
                val transcriptRaw = withContext(Dispatchers.IO) { NetworkClient.api.getTranscriptDataRaw(studentId, movId).string() }

                val keyRaw = withContext(Dispatchers.IO) { NetworkClient.api.getTranscriptLink(DocIdRequest(studentId)).string() }
                val keyObj = JSONObject(keyRaw)
                val linkId = keyObj.optLong("id")
                val qrUrl = keyObj.optString("url")
                
                pdfStatusMessage = getString(R.string.generating_pdf)
                val generator = WebPdfGenerator(context)
                val bytes = generator.generatePdf(
                    infoJson.toString(), transcriptRaw, linkId, qrUrl, resources!!, language, dictToUse
                ) { println(it) }

                val file = File(context.getExternalFilesDir(null), "transcript_$language.pdf")
                withContext(Dispatchers.IO) { FileOutputStream(file).use { it.write(bytes) } }

                pdfStatusMessage = getString(R.string.status_uploading)
                uploadAndOpen(context, linkId, studentId, file, "transcript_$language.pdf", keyObj.optString("key"), true)

            } catch (e: Exception) {
                pdfStatusMessage = getString(R.string.error_generic, e.message ?: "Unknown")
                e.printStackTrace()
                delay(3000)
                pdfStatusMessage = null
            } finally {
                isPdfGenerating = false
            }
        }
    }

    fun generateReferencePdf(context: Context, language: String) {
        if (isPdfGenerating) return
        val studentId = userData?.id ?: return

        viewModelScope.launch {
            isPdfGenerating = true
            pdfStatusMessage = getString(R.string.status_preparing_reference)
            try {
                fetchDictionaryIfNeeded()
                val dictToUse = combinedDictionary

                var resources = if (language == "en") cachedRefResourcesEn else cachedRefResourcesRu
                if (resources == null) {
                    pdfStatusMessage = getString(R.string.status_fetching_scripts)
                    resources = refFetcher.fetchResources({ println(it) }, language, dictToUse)
                    if (language == "en") cachedRefResourcesEn = resources else cachedRefResourcesRu = resources
                }
                
                val infoRaw = withContext(Dispatchers.IO) { NetworkClient.api.getStudentInfoRaw(studentId).string() }
                val infoJson = JSONObject(infoRaw)
                val fullName = "${infoJson.optString("last_name")} ${infoJson.optString("name")} ${infoJson.optString("father_name")}".replace("null", "").trim()
                infoJson.put("fullName", fullName)

                var specId = infoJson.optJSONObject("speciality")?.optInt("id") ?: 0
                if (specId == 0) specId = infoJson.optJSONObject("lastStudentMovement")?.optJSONObject("speciality")?.optInt("id") ?: 0
                var eduFormId = infoJson.optJSONObject("lastStudentMovement")?.optJSONObject("edu_form")?.optInt("id") ?: 0
                if (eduFormId == 0) eduFormId = infoJson.optJSONObject("edu_form")?.optInt("id") ?: 0

                val licenseRaw = withContext(Dispatchers.IO) { NetworkClient.api.getSpecialityLicense(specId, eduFormId).string() }
                val univRaw = withContext(Dispatchers.IO) { NetworkClient.api.getUniversityInfo().string() }
                
                val linkRaw = withContext(Dispatchers.IO) { NetworkClient.api.getReferenceLink(DocIdRequest(studentId)).string() }
                val linkObj = JSONObject(linkRaw)
                val linkId = linkObj.optLong("id")
                val qrUrl = linkObj.optString("url")
                val key = linkObj.optString("key")

                val token = prefs?.getToken() ?: ""

                pdfStatusMessage = getString(R.string.generating_pdf)
                val generator = ReferencePdfGenerator(context)
                val bytes = generator.generatePdf(
                    infoJson.toString(), licenseRaw, univRaw, linkId, qrUrl, resources!!, token, language, dictToUse
                ) { println(it) }

                val file = File(context.getExternalFilesDir(null), "reference_$language.pdf")
                withContext(Dispatchers.IO) { FileOutputStream(file).use { it.write(bytes) } }

                pdfStatusMessage = getString(R.string.status_uploading)
                uploadAndOpen(context, linkId, studentId, file, "reference_$language.pdf", key, false)

            } catch (e: Exception) {
                pdfStatusMessage = getString(R.string.error_generic, e.message ?: "Unknown")
                e.printStackTrace()
                delay(3000)
                pdfStatusMessage = null
            } finally {
                isPdfGenerating = false
            }
        }
    }

    private suspend fun uploadAndOpen(context: Context, linkId: Long, studentId: Long, file: java.io.File, filename: String, key: String, isTranscript: Boolean) {
        try {
            val plain = "text/plain".toMediaTypeOrNull()
            val pdfType = "application/pdf".toMediaTypeOrNull()
            val bodyId = linkId.toString().toRequestBody(plain)
            val bodyStudent = studentId.toString().toRequestBody(plain)
            val filePart = MultipartBody.Part.createFormData("pdf", filename, file.asRequestBody(pdfType))
            
            withContext(Dispatchers.IO) { 
                if (isTranscript) NetworkClient.api.uploadPdf(bodyId, bodyStudent, filePart).string() 
                else NetworkClient.api.uploadReferencePdf(bodyId, bodyStudent, filePart).string() 
            }
            delay(1000)
            val resRaw = withContext(Dispatchers.IO) { NetworkClient.api.resolveDocLink(DocKeyRequest(key)).string() }
            val url = JSONObject(resRaw).optString("url")
            if (url.isNotEmpty()) { 
                val i = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                context.startActivity(i) 
                pdfStatusMessage = null 
            } else {
                pdfStatusMessage = getString(R.string.error_url_not_found)
            }
        } catch (e: Exception) {
            pdfStatusMessage = getString(R.string.error_upload_failed, e.message ?: "Unknown")
        }
    }
}
