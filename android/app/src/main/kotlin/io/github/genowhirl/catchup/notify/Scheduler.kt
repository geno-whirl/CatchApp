package io.github.genowhirl.catchup.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import io.github.genowhirl.catchup.core.Alarm
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.alarms
import io.github.genowhirl.catchup.core.nextWeeklyPrompt
import io.github.genowhirl.catchup.data.AppSettings
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Turns plans and settings into system alarms: reminders before plans, "How was it?"
 * afterwards, the weekly planning prompt and the morning summary. Rescheduled whenever
 * the data or settings change, and after a reboot.
 */
object Scheduler {
    const val TYPE_REMINDER = "reminder"
    const val TYPE_FOLLOW_UP = "follow_up"
    const val TYPE_WEEKLY = "weekly"
    const val TYPE_DIGEST = "digest"

    const val EXTRA_TYPE = "type"
    const val EXTRA_PERSON = "person"
    const val EXTRA_PLAN = "plan"
    const val EXTRA_TITLE = "title"
    const val EXTRA_TEXT = "text"

    private const val PREFS = "scheduled_alarms"
    private const val MAX_ALARMS = 120

    private data class Entry(val id: String, val at: LocalDateTime, val extras: Map<String, String>)

    fun canScheduleExact(context: Context): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
    }

    fun reschedule(context: Context, people: List<Person>, settings: AppSettings) {
        val now = LocalDateTime.now()
        val horizon = now.plusDays(60)
        val entries = mutableListOf<Entry>()
        for (a in alarms(people, now, settings.reminderSettings())) {
            if (a.at.isAfter(horizon)) continue
            if (a.type == Alarm.Type.FOLLOW_UP && !settings.followUps) continue
            entries += Entry(
                a.id, a.at,
                mapOf(
                    EXTRA_TYPE to if (a.type == Alarm.Type.REMINDER) TYPE_REMINDER else TYPE_FOLLOW_UP,
                    EXTRA_PERSON to a.personId, EXTRA_PLAN to a.planKey, EXTRA_TITLE to a.title, EXTRA_TEXT to a.text,
                ),
            )
        }
        if (settings.weeklyPrompt) {
            entries += Entry("weekly", nextWeeklyPrompt(now, settings.planningDay, settings.planningTime), mapOf(EXTRA_TYPE to TYPE_WEEKLY))
        }
        if (settings.morningDigest) {
            val today = now.toLocalDate().atTime(settings.digestTime)
            entries += Entry("digest", if (today.isAfter(now)) today else today.plusDays(1), mapOf(EXTRA_TYPE to TYPE_DIGEST))
        }
        val keep = entries.sortedBy { it.at }.take(MAX_ALARMS)

        val am = context.getSystemService(AlarmManager::class.java)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = prefs.getStringSet("ids", emptySet()).orEmpty()
        val ids = keep.map { it.id }.toSet()
        for (old in previous - ids) am.cancel(pendingIntent(context, old, emptyMap()))

        val exact = canScheduleExact(context)
        for (e in keep) {
            val millis = e.at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val pi = pendingIntent(context, e.id, e.extras)
            try {
                if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } catch (_: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            }
        }
        prefs.edit().putStringSet("ids", ids).apply()
    }

    private fun pendingIntent(context: Context, id: String, extras: Map<String, String>): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction("io.github.genowhirl.catchup.ALARM")
            // A distinct URI per alarm, so each one can be replaced or cancelled on its own.
            .setData(Uri.parse("catchup://alarm/" + Uri.encode(id)))
        extras.forEach { (k, v) -> intent.putExtra(k, v) }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun notificationId(personId: String, planKey: String) = "$personId:$planKey".hashCode()
}
