package io.github.genowhirl.catchup.core

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

data class ReminderSettings(
    /** Default lead time for timed calls and meetings. */
    val callMinutes: Int = 15,
    val meetMinutes: Int = 60,
    /** Plans without a time are announced at this time on the day. */
    val dayOfTime: LocalTime = LocalTime.of(9, 0),
    /** "How was it?" this long after a timed plan starts… */
    val followUpAfterMinutes: Int = 120,
    /** …but never later than this; it moves to the next morning instead. */
    val latestFollowUp: LocalTime = LocalTime.of(21, 30),
    val morningFollowUp: LocalTime = LocalTime.of(9, 30),
    /** All-day plans are followed up in the evening. */
    val allDayFollowUp: LocalTime = LocalTime.of(20, 0),
)

data class Alarm(
    /** Stable across reschedules, so alarms can be replaced and cancelled. */
    val id: String,
    val at: LocalDateTime,
    val type: Type,
    val personId: String,
    val planKey: String,
    val title: String,
    val text: String,
) {
    enum class Type { REMINDER, FOLLOW_UP }
}

private fun isCall(kind: String?): Boolean =
    kind != null && listOf("call", "video", "phone").any { it in kind.lowercase() }

fun planTitle(p: Person, plan: Plan): String =
    plan.kind?.let { "$it with ${p.shortName}" } ?: "Catch up with ${p.shortName}"

/** Reminders before each plan and a "How was it?" afterwards. Only future times are returned. */
fun alarms(people: List<Person>, now: LocalDateTime, settings: ReminderSettings = ReminderSettings()): List<Alarm> {
    val out = mutableListOf<Alarm>()
    for (p in people) for (plan in p.plans) {
        val title = planTitle(p, plan)
        val place = plan.place?.let { " · $it" } ?: ""
        val ask = p.openFollowUps.take(2).takeIf { it.isNotEmpty() }?.joinToString(" · ", prefix = "\nAsk about: ") ?: ""

        // Reminder.
        val lead = plan.remindMinutes ?: if (plan.at.time == null) null else if (isCall(plan.kind)) settings.callMinutes else settings.meetMinutes
        val remindAt = when {
            lead == 0 -> null
            plan.at.time == null -> plan.at.date.minusDays(((lead ?: 0) / (24 * 60)).toLong()).atTime(settings.dayOfTime)
            else -> plan.at.dateTime.minusMinutes(lead!!.toLong())
        }
        if (remindAt != null && remindAt.isAfter(now)) {
            val whenText = plan.at.time?.let { "at ${it.format(When.HH_MM)}" } ?: "today"
            out += Alarm("r:${p.id}:${plan.key}", remindAt, Alarm.Type.REMINDER, p.id, plan.key, title, "$whenText$place$ask".trim())
        }

        // Follow-up.
        val followAt = if (plan.at.time == null) {
            plan.at.date.atTime(settings.allDayFollowUp)
        } else {
            val t = plan.at.dateTime.plusMinutes(settings.followUpAfterMinutes.toLong())
            if (t.toLocalTime().isAfter(settings.latestFollowUp) || t.toLocalDate().isAfter(plan.at.date)) {
                plan.at.date.plusDays(1).atTime(settings.morningFollowUp)
            } else t
        }
        if (followAt.isAfter(now)) {
            out += Alarm(
                "f:${p.id}:${plan.key}", followAt, Alarm.Type.FOLLOW_UP, p.id, plan.key,
                "How was it with ${p.shortName}?", "Note anything worth remembering — it takes a few seconds.",
            )
        }
    }
    return out.sortedBy { it.at }
}

/** The next weekly planning prompt, e.g. Sunday 18:00. */
fun nextWeeklyPrompt(now: LocalDateTime, day: DayOfWeek, time: LocalTime): LocalDateTime {
    val candidate = now.toLocalDate().with(TemporalAdjusters.nextOrSame(day)).atTime(time)
    return if (candidate.isAfter(now)) candidate else candidate.plusWeeks(1)
}
