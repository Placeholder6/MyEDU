package kg.oshsu.myedu

import android.app.Application
import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent // <--- IMPORT FIXED HERE
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

// NAVIGATION ENUM
enum class AppScreen {
    HOME, SCHEDULE, GRADES, PROFILE, TRANSCRIPT, REFERENCE, EDIT_PROFILE, PERSONAL_INFO
}

class MainViewModel(application: Application) : AndroidViewModel(application) {
    
    private fun getString(resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    // --- STATE: APP STATUS ---
    var appState by mutableStateOf("STARTUP")
    
    // Updated Navigation State
    var currentScreen by mutableStateOf(AppScreen.HOME)
    
    // Helper to sync BottomBar tab index with Screen enum
    var currentTab: Int
        get() = when(currentScreen) {
            AppScreen.HOME -> 0
            AppScreen.SCHEDULE -> 1
            AppScreen.GRADES -> 2
            AppScreen.PROFILE -> 3
            else -> 3 // Default to Profile for nested screens
        }
        set(value) {
            currentScreen = when(value) {
                0 -> AppScreen.HOME
                1 -> AppScreen.SCHEDULE
                2 -> AppScreen.GRADES
                3 -> AppScreen.PROFILE
                else -> AppScreen.HOME
            }
        }

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

    // --- STATE: UPDATES & NETWORK ---
    var updateAvailableRelease by mutableStateOf<GitHubRelease?>(null)
    var downloadId by mutableStateOf<Long?>(null)
    var lastRefreshTime by mutableStateOf(System.currentTimeMillis())
        private set

    val uiName: String
        get() = customName ?: userData?.let { "${it.last_name ?: ""} ${it.name ?: ""}".trim() } ?: getString(R.string.student_default)
    
    val uiPhoto: Any?
        get() = customPhotoUri ?: profileData?.avatar

    var avatarRefreshTrigger by mutableStateOf(0)

    // --- STATE: SCHEDULE DATA ---
    var fullSchedule by mutableStateOf<List<ScheduleItem>>(emptyList())
    var todayClasses by mutableStateOf<List<ScheduleItem>>(emptyList())
    var timeMap by mutableStateOf<Map<Int, String>>(emptyMap())
    var determinedStream by mutableStateOf<Int?>(null)
    var determinedGroup by mutableStateOf<Int?>(null)
    var todayDayName by mutableStateOf("") 
    var selectedClass by mutableStateOf<ScheduleItem?>(null)

    // --- STATE: GRADES DATA ---
    var sessionData by mutableStateOf<List<SessionResponse>>(emptyList())
    var isGradesLoading by mutableStateOf(false)

    // --- STATE: DOCUMENTS & PDF ---
    var transcriptData by mutableStateOf<List<TranscriptYear>>(emptyList())
    var isTranscriptLoading by mutableStateOf(false)
    
    var isPdfGenerating by mutableStateOf(false)
    var pdfStatusMessage by mutableStateOf<String?>(null)
    var pdfProgress by mutableStateOf(0f)

    var savedPdfUri by mutableStateOf<Uri?>(null)
    var savedPdfName by mutableStateOf<String?>(null)

    private var generationJob: Job? = null
    private var transcriptJob: Job? = null

    // --- STATE: DICTIONARY ---
    var dictionaryUrl by mutableStateOf("https://gist.githubusercontent.com/Placeholder6/71c6a6638faf26c7858d55a1e73b7aef/raw/myedudictionary.json")
    
    private var remoteDictionary: Map<String, String> = emptyMap()
    private var customDictionary: MutableMap<String, String> = mutableMapOf()
    var combinedDictionary by mutableStateOf<Map<String, String>>(emptyMap())

    private var prefs: PrefsManager? = null

    private val jsFetcher = JsResourceFetcher()
    private val refFetcher = ReferenceJsFetcher()
    private val dictUtils = DictionaryUtils()

    private var cachedResourcesRu: PdfResources? = null
    private var cachedResourcesEn: PdfResources? = null
    private var cachedRefResourcesRu: ReferenceResources? = null
    private var cachedRefResourcesEn: ReferenceResources? = null

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

    fun isOnboardingComplete(): Boolean {
        return prefs?.isOnboardingComplete() == true
    }

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
                    } catch (e: Exception) {
                        if (e.toString().contains("401") || e.message?.contains("401") == true) {
                             withContext(Dispatchers.Main) { handleTokenExpiration() }
                        }
                    }
                }
                checkForUpdates(context)
                
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

    fun onNetworkAvailable() { }

    fun checkForUpdates(context: Context) {
        viewModelScope.launch {
            try {
                val apiUrl = context.getString(R.string.update_repo_path) 
                val release = withContext(Dispatchers.IO) { NetworkClient.githubApi.getLatestRelease(apiUrl) }
                val currentVer = BuildConfig.VERSION_NAME
                
                val remoteVer = release.tagName.replace("v", "")
                val localVer = currentVer.replace("v", "")
                
                if (remoteVer != localVer && isNewerVersion(remoteVer, localVer)) {
                    updateAvailableRelease = release
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        return try {
            val rParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val lParts = local.split(".").map { it.toIntOrNull() ?: 0 }
            val length = max(rParts.size, lParts.size)
            for (i in 0 until length) {
                val r = rParts.getOrElse(i) { 0 }
                val l = lParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
            false
        } catch (e: Exception) { false }
    }

    fun downloadUpdate(context: Context) {
        val release = updateAvailableRelease ?: return
        val apkAsset = release.assets.find { it.name.endsWith(".apk") } ?: return
        try {
            val request = DownloadManager.Request(Uri.parse(apkAsset.downloadUrl))
                .setTitle(getString(R.string.update_notif_title))
                .setDescription(getString(R.string.update_notif_desc, release.tagName))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, apkAsset.name)
                .setMimeType("application/vnd.android.package-archive")

            val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadId = manager.enqueue(request)
            updateAvailableRelease = null
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun login(email: String, pass: String) {
        viewModelScope.launch {
            isLoading = true
            isLoginSuccess = false
            errorMsg = null
            NetworkClient.cookieJar.clear()
            NetworkClient.interceptor.authToken = null

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

                    try { withContext(Dispatchers.IO) { fetchAllDataSuspend() } } catch (e: Exception) { e.printStackTrace() }

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
        val wasRemember = rememberMe
        val savedE = loginEmail
        val savedP = loginPass

        currentScreen = AppScreen.HOME
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
                withContext(Dispatchers.Main) { lastRefreshTime = System.currentTimeMillis() }
            } catch (e: Exception) {
                if (e.message?.contains("401") == true || e.toString().contains("401")) { 
                    withContext(Dispatchers.Main) { handleTokenExpiration() } 
                }
            } finally {
                withContext(Dispatchers.Main) { isRefreshing = false }
            }
        }
    }

    fun refreshProfile() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val profile = NetworkClient.api.getProfile()
                withContext(Dispatchers.Main) {
                    profileData = profile
                    prefs?.saveData("profile_data", profile)
                    validateAndSyncPhoto(profile)
                    avatarRefreshTrigger++ 
                }
            } catch (e: Exception) {
                if (e.message?.contains("401") == true || e.toString().contains("401")) {
                    withContext(Dispatchers.Main) { handleTokenExpiration() }
                }
            }
        }
    }

    fun debugForceTokenExpiry() {
        handleTokenExpiration()
    }

    private fun handleTokenExpiration() {
        if (rememberMe && loginEmail.isNotBlank() && loginPass.isNotBlank()) {
            appState = "LOGIN"
            login(loginEmail, loginPass) 
        } else {
            logout()
            errorMsg = getString(R.string.error_credentials)
        }
    }

    private fun validateAndSyncPhoto(profile: StudentInfoResponse?) {
        val remote = profile?.avatar ?: return
        val local = customPhotoUri
        if (local != null && local.startsWith("http") && local != remote) {
            customPhotoUri = remote
            prefs?.saveSettings(customName ?: "", remote, appTheme, notificationsEnabled)
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
            validateAndSyncPhoto(profile)
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

    // --- TRANSCRIPT & DOCS LOGIC ---

    fun fetchTranscript(forceRefresh: Boolean = false) {
        transcriptJob?.cancel()
        isTranscriptLoading = true
        currentScreen = AppScreen.TRANSCRIPT
        
        if (forceRefresh) {
            transcriptData = emptyList()
        } else {
            transcriptData = prefs?.loadList<TranscriptYear>("transcript_list") ?: emptyList()
        }

        transcriptJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val uid = userData?.id ?: return@launch
                val movId = profileData?.studentMovement?.id ?: return@launch 
                val transcript = NetworkClient.api.getTranscript(uid, movId)
                withContext(Dispatchers.Main) { 
                    transcriptData = transcript
                    prefs?.saveList("transcript_list", transcript)
                }
            } catch (e: Exception) {
                if (e.message?.contains("401") == true || e.toString().contains("401")) {
                     withContext(Dispatchers.Main) { handleTokenExpiration() }
                }
            } finally { 
                withContext(Dispatchers.Main) { isTranscriptLoading = false } 
            }
        }
    }
    
    fun getTimeString(lessonId: Int) = timeMap[lessonId] ?: "${getString(R.string.pair)} $lessonId"

    fun resetDocumentState() {
        generationJob?.cancel()
        generationJob = null
        transcriptJob?.cancel()
        transcriptJob = null
        isPdfGenerating = false
        pdfProgress = 0f
        pdfStatusMessage = null
        savedPdfUri = null
        savedPdfName = null
    }

    fun openPdf(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/pdf")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun generateTranscriptPdf(context: Context, language: String) {
        if (isPdfGenerating) return
        resetDocumentState()
        val studentId = userData?.id ?: return
        
        generationJob = viewModelScope.launch {
            isPdfGenerating = true
            pdfProgress = 0.1f
            pdfStatusMessage = getString(R.string.status_preparing_transcript)
            try {
                withContext(Dispatchers.IO) { fetchDictionaryIfNeeded() }
                pdfProgress = 0.2f
                val dictToUse = combinedDictionary
                
                var resources = if (language == "en") cachedResourcesEn else cachedResourcesRu
                if (resources == null) {
                    pdfStatusMessage = getString(R.string.status_fetching_scripts)
                    resources = jsFetcher.fetchResources({ println(it) }, language, dictToUse)
                    if (language == "en") cachedResourcesEn = resources else cachedResourcesRu = resources
                }
                pdfProgress = 0.4f

                val (infoJsonString, transcriptRaw, linkId, qrUrl) = withContext(Dispatchers.IO) {
                    val infoRaw = NetworkClient.api.getStudentInfoRaw(studentId).string()
                    val infoJson = JSONObject(infoRaw)
                    val fullName = "${infoJson.optString("last_name")} ${infoJson.optString("name")} ${infoJson.optString("father_name")}".replace("null", "").trim()
                    infoJson.put("fullName", fullName)

                    val movId = profileData?.studentMovement?.id ?: 0L
                    val transRaw = NetworkClient.api.getTranscriptDataRaw(studentId, movId).string()

                    val keyRaw = NetworkClient.api.getTranscriptLink(DocIdRequest(studentId)).string()
                    val keyObj = JSONObject(keyRaw)
                    
                    Quadruple(infoJson.toString(), transRaw, keyObj.optLong("id"), keyObj.optString("url"))
                }
                
                pdfStatusMessage = getString(R.string.generating_pdf)
                pdfProgress = 0.7f
                
                val generator = WebPdfGenerator(context)
                val bytes = generator.generatePdf(
                    infoJsonString, transcriptRaw, linkId, qrUrl, resources!!, language, dictToUse
                ) { println(it) }

                pdfProgress = 0.8f
                pdfStatusMessage = getString(R.string.status_uploading)
                withContext(Dispatchers.IO) {
                    try {
                        uploadPdfOnly(linkId, studentId, bytes, "transcript_$language.pdf", true)
                    } catch (e: Exception) { e.printStackTrace() }
                }

                pdfStatusMessage = "Saving to Downloads..."
                pdfProgress = 0.9f
                
                val lName = userData?.last_name ?: "Student"
                val fName = userData?.name ?: "Name"
                val baseName = "${lName}_${fName}_Transcript_$language"
                
                val uri = saveToDownloads(context, bytes, baseName)
                
                pdfProgress = 1.0f
                if (uri != null) {
                    savedPdfUri = uri
                    savedPdfName = getFileNameFromUri(context, uri) ?: "$baseName.pdf"
                    pdfStatusMessage = null
                } else {
                    pdfStatusMessage = "Failed to save to Downloads"
                }

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e.message?.contains("401") == true || e.toString().contains("401")) {
                     withContext(Dispatchers.Main) { handleTokenExpiration() }
                } else {
                    var msg = getString(R.string.error_generic, e.message ?: "Unknown")
                    if (e.message?.contains("400") == true || (e is retrofit2.HttpException && e.code() == 400)) {
                        msg += "\nPlease wait a few minutes and try again."
                    }
                    pdfStatusMessage = msg
                    e.printStackTrace()
                }
            } finally {
                isPdfGenerating = false
            }
        }
    }

    fun generateReferencePdf(context: Context, language: String) {
        if (isPdfGenerating) return
        resetDocumentState()
        val studentId = userData?.id ?: return

        generationJob = viewModelScope.launch {
            isPdfGenerating = true
            pdfProgress = 0.1f
            pdfStatusMessage = getString(R.string.status_preparing_reference)
            try {
                withContext(Dispatchers.IO) { fetchDictionaryIfNeeded() }
                pdfProgress = 0.2f
                val dictToUse = combinedDictionary

                var resources = if (language == "en") cachedRefResourcesEn else cachedRefResourcesRu
                if (resources == null) {
                    pdfStatusMessage = getString(R.string.status_fetching_scripts)
                    resources = refFetcher.fetchResources({ println(it) }, language, dictToUse)
                    if (language == "en") cachedRefResourcesEn = resources else cachedRefResourcesRu = resources
                }
                pdfProgress = 0.4f
                
                val (infoJsonString, licenseRaw, univRaw, linkId, qrUrl, key) = withContext(Dispatchers.IO) {
                    val infoRaw = NetworkClient.api.getStudentInfoRaw(studentId).string()
                    val infoJson = JSONObject(infoRaw)
                    val fullName = "${infoJson.optString("last_name")} ${infoJson.optString("name")} ${infoJson.optString("father_name")}".replace("null", "").trim()
                    infoJson.put("fullName", fullName)

                    var specId = infoJson.optJSONObject("speciality")?.optInt("id") ?: 0
                    if (specId == 0) specId = infoJson.optJSONObject("lastStudentMovement")?.optJSONObject("speciality")?.optInt("id") ?: 0
                    var eduFormId = infoJson.optJSONObject("lastStudentMovement")?.optJSONObject("edu_form")?.optInt("id") ?: 0
                    if (eduFormId == 0) eduFormId = infoJson.optJSONObject("edu_form")?.optInt("id") ?: 0

                    val licRaw = NetworkClient.api.getSpecialityLicense(specId, eduFormId).string()
                    val uRaw = NetworkClient.api.getUniversityInfo().string()
                    val linkRaw = NetworkClient.api.getReferenceLink(DocIdRequest(studentId)).string()
                    val linkObj = JSONObject(linkRaw)
                    
                    QuintuplePlusOne(infoJson.toString(), licRaw, uRaw, linkObj.optLong("id"), linkObj.optString("url"), linkObj.optString("key"))
                }

                val token = prefs?.getToken() ?: ""

                pdfStatusMessage = getString(R.string.generating_pdf)
                pdfProgress = 0.7f
                
                val generator = ReferencePdfGenerator(context)
                val bytes = generator.generatePdf(
                    infoJsonString, licenseRaw, univRaw, linkId, qrUrl, resources!!, token, language, dictToUse
                ) { println(it) }

                pdfProgress = 0.8f
                pdfStatusMessage = getString(R.string.status_uploading)
                withContext(Dispatchers.IO) {
                    try {
                        uploadPdfOnly(linkId, studentId, bytes, "reference_$language.pdf", false)
                    } catch (e: Exception) { e.printStackTrace() }
                }

                pdfStatusMessage = "Saving to Downloads..."
                pdfProgress = 0.9f
                
                val lName = userData?.last_name ?: "Student"
                val fName = userData?.name ?: "Name"
                val baseName = "${lName}_${fName}_Reference_$language"
                
                val uri = saveToDownloads(context, bytes, baseName)
                
                pdfProgress = 1.0f
                if (uri != null) {
                    savedPdfUri = uri
                    savedPdfName = getFileNameFromUri(context, uri) ?: "$baseName.pdf"
                    pdfStatusMessage = null
                } else {
                    pdfStatusMessage = "Failed to save to Downloads"
                }

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (e.message?.contains("401") == true || e.toString().contains("401")) {
                     withContext(Dispatchers.Main) { handleTokenExpiration() }
                } else {
                    var msg = getString(R.string.error_generic, e.message ?: "Unknown")
                    if (e.message?.contains("400") == true || (e is retrofit2.HttpException && e.code() == 400)) {
                        msg += "\nPlease wait a few minutes and try again."
                    }
                    pdfStatusMessage = msg
                    e.printStackTrace()
                }
            } finally {
                isPdfGenerating = false
            }
        }
    }

    private suspend fun uploadPdfOnly(linkId: Long, studentId: Long, bytes: ByteArray, filename: String, isTranscript: Boolean) {
        val plain = "text/plain".toMediaTypeOrNull()
        val pdfType = "application/pdf".toMediaTypeOrNull()
        val bodyId = linkId.toString().toRequestBody(plain)
        val bodyStudent = studentId.toString().toRequestBody(plain)
        val filePart = MultipartBody.Part.createFormData("pdf", filename, bytes.toRequestBody(pdfType))
        
        withContext(Dispatchers.IO) { 
            if (isTranscript) NetworkClient.api.uploadPdf(bodyId, bodyStudent, filePart).string() 
            else NetworkClient.api.uploadReferencePdf(bodyId, bodyStudent, filePart).string() 
        }
    }

    private suspend fun saveToDownloads(context: Context, bytes: ByteArray, baseName: String): Uri? {
        return withContext(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }
                
                var finalName = "$baseName.pdf"
                var count = 0
                while (checkFileExists(context, finalName)) {
                    count++
                    finalName = "${baseName}_($count).pdf"
                }
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, finalName)

                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                uri?.let {
                    resolver.openOutputStream(it)?.use { os ->
                        os.write(bytes)
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(it, contentValues, null, null)
                    }
                }
                uri
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    private fun checkFileExists(context: Context, fileName: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val projection = arrayOf(MediaStore.MediaColumns.DISPLAY_NAME)
            val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
            val args = arrayOf(fileName)
            val queryUri = MediaStore.Downloads.EXTERNAL_CONTENT_URI
            context.contentResolver.query(queryUri, projection, selection, args, null)?.use {
                return it.count > 0
            }
        }
        return false
    }

    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (index != -1) name = it.getString(index)
            }
        }
        return name
    }

    data class Quadruple(val info: String, val transcript: String, val linkId: Long, val qr: String)
    data class QuintuplePlusOne(val info: String, val license: String, val univ: String, val linkId: Long, val qr: String, val key: String)
}