package kg.oshsu.myedu

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

class ScheduleAlarmManager(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // --- ALARM: MAIN SCHEDULER ---
    @SuppressLint("ScheduleExactAlarm")
    fun scheduleNotifications(schedule: List<ScheduleItem>, timeMap: Map<Int, String>) {
        // 1. Evening Summary (Next Day)
        scheduleEveningSummary(schedule)

        // 2. Class Reminders (1 Hour Before)
        scheduleClassReminders(schedule, timeMap)
    }

    // --- ALARM: EVENING SUMMARY ---
    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleEveningSummary(schedule: List<ScheduleItem>) {
        val calendar = Calendar.getInstance()
        
        // Target: Tonight at 8:00 PM
        calendar.set(Calendar.HOUR_OF_DAY, 20)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)

        // If it's already past 8 PM, skip summary for today
        if (calendar.timeInMillis < System.currentTimeMillis()) return

        // Calculate "Tomorrow's" API Day (Mon=0 ... Sun=6)
        val tomorrowCal = Calendar.getInstance()
        tomorrowCal.add(Calendar.DAY_OF_YEAR, 1)
        val javaDay = tomorrowCal.get(Calendar.DAY_OF_WEEK)
        // Convert Java (Sun=1...Sat=7) to API (Mon=0...Sun=6)
        val apiDay = if (javaDay == Calendar.SUNDAY) 6 else javaDay - 2

        val nextDayClasses = schedule.filter { it.day == apiDay }
        
        if (nextDayClasses.isNotEmpty()) {
            val count = nextDayClasses.size
            val firstClass = nextDayClasses.first().subject?.get() ?: "Class"
            
            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("TITLE", "Tomorrow's Schedule")
                putExtra("MESSAGE", "You have $count classes tomorrow. First up: $firstClass.")
                putExtra("ID", 9999) // Unique ID for summary
            }

            setAlarm(calendar.timeInMillis, 9999, intent)
        }
    }

    // --- ALARM: CLASS REMINDERS ---
    @SuppressLint("ScheduleExactAlarm")
    private fun scheduleClassReminders(schedule: List<ScheduleItem>, timeMap: Map<Int, String>) {
        val now = System.currentTimeMillis()

        schedule.forEach { item ->
            val timeString = timeMap[item.id_lesson] ?: return@forEach
            val startTime = timeString.split("-").firstOrNull()?.trim() ?: return@forEach
            val parts = startTime.split(":")
            if (parts.size < 2) return@forEach
            
            val hour = parts[0].toInt()
            val minute = parts[1].toInt()

            // Calculate the specific date for this class
            val classCal = Calendar.getInstance()
            val currentJavaDay = classCal.get(Calendar.DAY_OF_WEEK) // Sun=1, Mon=2
            val currentApiDay = if (currentJavaDay == Calendar.SUNDAY) 6 else currentJavaDay - 2
            
            val dayDiff = item.day - currentApiDay
            classCal.add(Calendar.DAY_OF_YEAR, dayDiff)
            classCal.set(Calendar.HOUR_OF_DAY, hour)
            classCal.set(Calendar.MINUTE, minute)
            classCal.set(Calendar.SECOND, 0)

            // Subtract 1 Hour for the notification
            classCal.add(Calendar.HOUR_OF_DAY, -1)

            // Logic: 
            // If the calculated time is in the past, it means the class already happened this week 
            // OR the alarm time passed. We add 7 days to schedule it for next week.
            if (classCal.timeInMillis < now) {
                classCal.add(Calendar.DAY_OF_YEAR, 7)
            }

            // Subject and Room info
            val subjectName = item.subject?.get() ?: "Class"
            val roomName = item.room?.name_en ?: "Unknown Room"
            val msg = "$subjectName\nTime: $startTime • Room: $roomName"

            val intent = Intent(context, NotificationReceiver::class.java).apply {
                putExtra("TITLE", "Upcoming Class in 1 hr")
                putExtra("MESSAGE", msg)
                // Unique ID based on Day (0-6) and LessonID (1-10) -> e.g. 502
                putExtra("ID", item.day * 100 + item.id_lesson)
            }

            setAlarm(classCal.timeInMillis, item.day * 100 + item.id_lesson, intent)
        }
    }

    private fun setAlarm(timeInMillis: Long, reqCode: Int, intent: Intent) {
        val pendingIntent = PendingIntent.getBroadcast(
            context, reqCode, intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pendingIntent)
        }
    }
}