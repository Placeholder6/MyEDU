package kg.oshsu.myedu

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

class BackgroundSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Attempt to run the sync logic, allowing for one retry on auth failure
        val success = runSyncTask(retryAuth = true)
        if (success) Result.success() else Result.retry()
    }

    private suspend fun runSyncTask(retryAuth: Boolean): Boolean {
        try {
            val context = applicationContext
            val prefs = PrefsManager(context)
            var token = prefs.getToken()

            if (token == null && retryAuth) {
                // No token found initially, try to login if credentials exist
                if (attemptBgLogin(prefs)) {
                     token = prefs.getToken()
                } else {
                     return false
                }
            } else if (token == null) {
                return false
            }

            NetworkClient.interceptor.authToken = token
            NetworkClient.cookieJar.injectSessionCookies(token!!)

            // 1. Fetch User Data
            val userResponse = try { 
                NetworkClient.api.getUser() 
            } catch (e: Exception) {
                // If 401 and we haven't retried yet, attempt login and recursion
                if (retryAuth && (e.message?.contains("401") == true || e.message?.contains("HTTP 401") == true)) {
                    if (attemptBgLogin(prefs)) {
                        return runSyncTask(retryAuth = false)
                    }
                }
                return false // Other error or auth retry failed
            }

            // 2. Fetch Profile
            val profile = try { NetworkClient.api.getProfile() } catch (e: Exception) { return false }

            prefs.saveData("user_data", userResponse.user)
            prefs.saveData("profile_data", profile)

            // 3. Other Data (Best Effort)
            try { val news = NetworkClient.api.getNews(); prefs.saveList("news_list", news) } catch (_: Exception) { }
            try { val pay = NetworkClient.api.getPayStatus(); prefs.saveData("pay_status", pay) } catch (_: Exception) { }

            // 4. Session & Grades Notification
            try {
                // Note: Ensure PrefsManager can handle the new SessionResponse class structure
                val oldSession = prefs.loadList<SessionResponse>("session_list")
                val activeSemester = profile.active_semester ?: 1
                val newSession = NetworkClient.api.getSession(activeSemester)

                if (oldSession.isNotEmpty() && newSession.isNotEmpty()) {
                    val localizedContext = getLocalizedContext(context, prefs)
                    val updates = checkForUpdates(oldSession, newSession, localizedContext)
                    if (updates.isNotEmpty()) sendNotification(localizedContext, updates)
                }
                prefs.saveList("session_list", newSession)
            } catch (e: Exception) { e.printStackTrace() }

            // 5. Schedule & Alarms
            val mov = profile.studentMovement
            if (mov != null) {
                try {
                    val years = NetworkClient.api.getYears()
                    val activeYearId = years.find { it.active }?.id ?: 25
                    val times = try { NetworkClient.api.getLessonTimes(mov.id_speciality!!, mov.id_edu_form!!, activeYearId) } catch (e: Exception) { emptyList() }
                    val wrappers = NetworkClient.api.getSchedule(mov.id_speciality!!, mov.id_edu_form!!, activeYearId, profile.active_semester ?: 1)
                    val fullSchedule = wrappers.flatMap { it.schedule_items ?: emptyList() }.sortedBy { it.id_lesson }

                    if (fullSchedule.isNotEmpty()) {
                        prefs.saveList("schedule_list", fullSchedule)
                        if (times.isNotEmpty()) {
                            val timeMap = times.associate { (it.lesson?.num ?: 0) to "${it.begin_time ?: ""} - ${it.end_time ?: ""}" }
                            val localizedContext = getLocalizedContext(context, prefs)
                            val alarmManager = ScheduleAlarmManager(localizedContext)
                            val lang = prefs.getAppLanguage()
                            alarmManager.scheduleNotifications(fullSchedule, timeMap)
                        }
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            return true
        } catch (e: Exception) {
            return false
        }
    }

    private suspend fun attemptBgLogin(prefs: PrefsManager): Boolean {
        val isRemember = prefs.loadData("pref_remember_me", Boolean::class.java) ?: false
        if (!isRemember) return false
        
        val email = prefs.loadData("pref_saved_email", String::class.java) ?: ""
        val pass = prefs.loadData("pref_saved_pass", String::class.java) ?: ""
        
        if (email.isBlank() || pass.isBlank()) return false

        return try {
            val resp = NetworkClient.api.login(LoginRequest(email.trim(), pass.trim()))
            val token = resp.authorisation?.token
            if (token != null) {
                prefs.saveToken(token)
                NetworkClient.interceptor.authToken = token
                NetworkClient.cookieJar.injectSessionCookies(token)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun getLocalizedContext(context: Context, prefs: PrefsManager): Context {
        val lang = prefs.getAppLanguage()
        val locale = Locale.forLanguageTag(lang) 
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    private fun checkForUpdates(oldList: List<SessionResponse>, newList: List<SessionResponse>, context: Context): List<String> {
        val updates = ArrayList<String>()
        // Change: associateBy to keep the whole object so we can access .graphic later
        val oldMap = oldList.flatMap { it.subjects ?: emptyList() }
            .associateBy { it.subject?.get() ?: context.getString(R.string.unknown) }

        newList.flatMap { it.subjects ?: emptyList() }.forEach { newSub ->
            val name = newSub.subject?.get() ?: return@forEach
            val oldSub = oldMap[name]
            
            // 1. Check Grades
            val oldMarks = oldSub?.marklist
            val newMarks = newSub.marklist
            
            fun check(label: String, oldVal: Double?, newVal: Double?) {
                val v2 = newVal ?: 0.0
                if (v2 > (oldVal ?: 0.0) && v2 > 0) updates.add(context.getString(R.string.notif_grades_msg_single, name, label, v2.toInt()))
            }
            if (oldMarks != null && newMarks != null) {
                check(context.getString(R.string.m1), oldMarks.point1, newMarks.point1)
                check(context.getString(R.string.m2), oldMarks.point2, newMarks.point2)
                check(context.getString(R.string.exam_short), oldMarks.finalScore, newMarks.finalScore)
            }

            // 2. Check Portal (Graphic)
            val oldGraphic = oldSub?.graphic
            val newGraphic = newSub.graphic

            if (oldGraphic == null && newGraphic != null) {
                updates.add(context.getString(R.string.notif_portal_opened, name))
            }
        }
        return updates
    }

    private fun sendNotification(context: Context, updates: List<String>) {
        val title = context.getString(R.string.notif_new_grades_title)
        val message = if (updates.size > 4) context.getString(R.string.notif_grades_msg_multiple, updates.size) else updates.joinToString("\n")
        val intent = Intent(context, NotificationReceiver::class.java).apply {
            putExtra("TITLE", title); putExtra("MESSAGE", message); putExtra("ID", 777)
        }
        context.sendBroadcast(intent)
    }
}
