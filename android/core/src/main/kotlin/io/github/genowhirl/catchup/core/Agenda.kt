package io.github.genowhirl.catchup.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Why someone is suggested. */
sealed interface Reason {
    data class Birthday(val days: Int, val age: Int?) : Reason
    data class FamilyBirthday(val days: Int, val member: String, val relation: String?) : Reason
    data class Overdue(val days: Int) : Reason
    data class DueSoon(val days: Int) : Reason
    data object NeverContacted : Reason
}

data class Suggestion(
    val person: Person,
    val reasons: List<Reason>,
    val score: Double,
)

/**
 * The cadence actually used: the one on file, or — for people with an explicit priority —
 * a sensible default (high: monthly, medium: quarterly, low: yearly).
 */
fun Person.effectiveCadence(): Cadence? = cadence ?: if (!priorityIsSet) null else when (priority) {
    Priority.HIGH -> Cadence(30, "monthly")
    Priority.MEDIUM -> Cadence(91, "quarterly")
    Priority.LOW -> Cadence(365, "yearly")
}

fun Person.effectiveDueDate(today: LocalDate): LocalDate? =
    nextContact ?: effectiveCadence()?.let { c -> lastContacted?.plusDays(c.days.toLong()) ?: today }

enum class ContactStatus { NONE, OK, DUE_SOON, OVERDUE }

fun Person.contactStatus(today: LocalDate): ContactStatus {
    val due = effectiveDueDate(today) ?: return ContactStatus.NONE
    if (lastContacted == null && nextContact == null) return ContactStatus.OVERDUE
    val days = daysBetween(today, due)
    return when {
        days < 0 -> ContactStatus.OVERDUE
        days <= 7 -> ContactStatus.DUE_SOON
        else -> ContactStatus.OK
    }
}

private fun reasonScore(r: Reason, cadenceDays: Int): Double = when (r) {
    // Birthdays are now-or-never, so they outrank almost everything on the day.
    is Reason.Birthday -> 140.0 - r.days * 12
    is Reason.FamilyBirthday -> 45.0 - r.days * 4
    is Reason.Overdue -> 45.0 + min(r.days.toDouble() / max(cadenceDays, 7) * 30, 40.0)
    is Reason.DueSoon -> 30.0 - r.days * 2
    Reason.NeverContacted -> 50.0
}

/**
 * Who to get in touch with between today and `today + horizonDays`, best first. People with a
 * catch-up already planned in that window are left out.
 */
fun suggestions(people: List<Person>, today: LocalDate, horizonDays: Int = 7): List<Suggestion> {
    val end = today.plusDays(horizonDays.toLong())
    return people.mapNotNull { p ->
        if (p.plans.any { !it.at.date.isBefore(today) && !it.at.date.isAfter(end) }) return@mapNotNull null
        val reasons = mutableListOf<Reason>()
        p.birthday?.let { b ->
            val d = b.daysUntil(today)
            if (d <= horizonDays) reasons += Reason.Birthday(d, b.upcomingAge(today))
        }
        for (m in p.family) {
            val d = m.birthday?.daysUntil(today) ?: continue
            if (d <= horizonDays) reasons += Reason.FamilyBirthday(d, m.name, m.relation)
        }
        val cadence = p.effectiveCadence()
        val due = p.effectiveDueDate(today)
        if (due != null) {
            when {
                p.lastContacted == null && p.nextContact == null -> reasons += Reason.NeverContacted
                due.isBefore(today) -> reasons += Reason.Overdue(daysBetween(due, today))
                !due.isAfter(end) -> reasons += Reason.DueSoon(daysBetween(today, due))
            }
        }
        if (reasons.isEmpty()) return@mapNotNull null
        val base = reasons.maxOf { reasonScore(it, cadence?.days ?: 30) }
        val score = base * p.priority.weight + (if (p.openFollowUps.isNotEmpty()) 4.0 else 0.0)
        Suggestion(p, reasons, score)
    }.sortedWith(compareByDescending<Suggestion> { it.score }.thenBy { it.person.name.lowercase() })
}

data class Upcoming(val person: Person, val plan: Plan)

fun upcomingPlans(people: List<Person>, today: LocalDate, days: Int = 60): List<Upcoming> {
    val end = today.plusDays(days.toLong())
    return people.flatMap { p -> p.plans.filter { !it.at.date.isBefore(today) && !it.at.date.isAfter(end) }.map { Upcoming(p, it) } }
        .sortedBy { it.plan.at }
}

/** Plans that have happened but haven't been logged: "How was it?" */
fun awaitingNotes(people: List<Person>, now: LocalDateTime): List<Upcoming> =
    people.flatMap { p -> p.pastPlans(now).map { Upcoming(p, it) } }.sortedByDescending { it.plan.at }

data class BirthdayItem(val person: Person, val label: String, val birthday: Birthday, val days: Int, val isFamily: Boolean)

fun upcomingBirthdays(people: List<Person>, today: LocalDate, days: Int = 30): List<BirthdayItem> =
    people.flatMap { p ->
        listOfNotNull(p.birthday?.let { BirthdayItem(p, p.name, it, it.daysUntil(today), false) }) +
            p.family.mapNotNull { m ->
                m.birthday?.let {
                    val rel = m.relation?.let { r -> "${p.shortName}'s $r" } ?: "with ${p.shortName}"
                    BirthdayItem(p, "${m.name} ($rel)", it, it.daysUntil(today), true)
                }
            }
    }.filter { it.days <= days }.sortedBy { it.days }

// ---------------------------------------------------------------------------------------------
// Weekly planning: "When are you free this week?" → who to see or call, and when.

enum class DayPart(val label: String, val time: LocalTime) {
    MORNING("Morning", LocalTime.of(10, 0)),
    LUNCH("Lunch", LocalTime.of(12, 30)),
    AFTERNOON("Afternoon", LocalTime.of(15, 0)),
    EVENING("Evening", LocalTime.of(19, 0)),
}

/** A free time in the week: a day and part of the day, at the part's usual time unless changed. */
data class Slot(val date: LocalDate, val part: DayPart, val time: LocalTime = part.time) {
    val at: When get() = When(date, time)
}

fun partOf(time: LocalTime?): DayPart = when {
    time == null -> DayPart.EVENING
    time.hour < 12 -> DayPart.MORNING
    time.hour < 14 -> DayPart.LUNCH
    time.hour < 17 -> DayPart.AFTERNOON
    else -> DayPart.EVENING
}

data class Proposal(val person: Person, val slot: Slot, val kind: String, val reasons: List<Reason>)

data class WeekPlan(val proposals: List<Proposal>, val more: List<Suggestion>)

const val KIND_CALL = "Call"
const val KIND_MEET = "Meet"

/** Call or meet: whatever you did last time, else meet the people closest to you. */
fun suggestedKind(p: Person): String {
    val last = p.interactions.firstOrNull()?.kind?.lowercase()
    return when {
        last == null -> if (p.priority == Priority.HIGH) KIND_MEET else KIND_CALL
        listOf("call", "video", "phone", "message", "text", "whatsapp").any { it in last } -> KIND_CALL
        else -> KIND_MEET
    }
}

/** Suggestions for the 7 days from `weekStart`, leaving out birthdays that fall before it. */
fun weekSuggestions(people: List<Person>, today: LocalDate, weekStart: LocalDate): List<Suggestion> {
    val horizon = daysBetween(today, weekStart.plusDays(6))
    return suggestions(people, today, horizon).mapNotNull { s ->
        val kept = s.reasons.filterNot { r ->
            (r is Reason.Birthday && today.plusDays(r.days.toLong()).isBefore(weekStart)) ||
                (r is Reason.FamilyBirthday && today.plusDays(r.days.toLong()).isBefore(weekStart))
        }
        if (kept.isEmpty()) null else s.copy(reasons = kept)
    }
}

/**
 * Picks up to `goal` people and spreads them over the free slots: birthdays land on (or
 * near) the day, everyone else goes to the earliest free day, at most one per day while
 * there are days to spare.
 */
fun planWeek(people: List<Person>, today: LocalDate, weekStart: LocalDate, free: List<Slot>, goal: Int): WeekPlan {
    val weekEnd = weekStart.plusDays(6)
    val ranked = weekSuggestions(people, today, weekStart)
    val slots = free.filter { !it.date.isBefore(weekStart) && !it.date.isAfter(weekEnd) }
        .sortedBy { it.at }
        .toMutableList()
    val usedDays = mutableMapOf<LocalDate, Int>()
    val proposals = mutableListOf<Proposal>()
    val chosen = mutableSetOf<String>()

    for (s in ranked) {
        if (proposals.size >= goal || slots.isEmpty()) break
        val birthdayDate = s.reasons.filterIsInstance<Reason.Birthday>().firstOrNull()
            ?.let { today.plusDays(it.days.toLong()) }
            ?: s.reasons.filterIsInstance<Reason.FamilyBirthday>().firstOrNull()?.let { today.plusDays(it.days.toLong()) }
        val slot = if (birthdayDate != null) {
            slots.minByOrNull { kotlin.math.abs(daysBetween(birthdayDate, it.date)) * 10 + (3 - it.part.ordinal) }
        } else {
            val minUse = slots.minOf { usedDays[it.date] ?: 0 }
            slots.first { (usedDays[it.date] ?: 0) == minUse }
        } ?: break
        slots.remove(slot)
        usedDays[slot.date] = (usedDays[slot.date] ?: 0) + 1
        proposals += Proposal(s.person, slot, suggestedKind(s.person), s.reasons)
        chosen += s.person.id
    }
    return WeekPlan(proposals.sortedBy { it.slot.at }, ranked.filter { it.person.id !in chosen })
}

/** The 7 days to plan: starting tomorrow if it's late in the day, else today. */
fun planningWeekStart(now: LocalDateTime): LocalDate =
    if (now.toLocalTime().isAfter(LocalTime.of(16, 0))) now.toLocalDate().plusDays(1) else now.toLocalDate()

// ---------------------------------------------------------------------------------------------
// Wording shared by the app and notifications.

fun dayName(date: LocalDate, today: LocalDate): String = when (daysBetween(today, date)) {
    0 -> "today"
    1 -> "tomorrow"
    -1 -> "yesterday"
    in 2..6 -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    else -> "${date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
}

private fun span(days: Int): String = when {
    days < 14 -> "$days day${if (days == 1) "" else "s"}"
    days < 60 -> "${(days / 7.0).roundToInt()} weeks"
    else -> "${(days / 30.0).roundToInt()} months"
}

fun describe(r: Reason, today: LocalDate): String = when (r) {
    is Reason.Birthday -> (r.age?.let { "Turns $it" } ?: "Birthday") + " " + dayName(today.plusDays(r.days.toLong()), today).let { if (r.days in 2..6) "on $it" else it }
    is Reason.FamilyBirthday -> "${r.member}${r.relation?.let { " ($it)" } ?: ""}'s birthday " + dayName(today.plusDays(r.days.toLong()), today).let { if (r.days in 2..6) "on $it" else it }
    is Reason.Overdue -> "Overdue by ${span(r.days)}"
    is Reason.DueSoon -> if (r.days == 0) "Due today" else "Due in ${span(r.days)}"
    Reason.NeverContacted -> "No catch-up logged yet"
}

fun formatDaysAgo(days: Int): String = when {
    days <= 0 -> "today"
    days == 1 -> "yesterday"
    days < 14 -> "$days days ago"
    days < 60 -> "${days / 7} weeks ago"
    days < 365 -> "${days / 30} months ago"
    days < 730 -> "a year ago"
    else -> "${days / 365} years ago"
}

/** The Sunday-evening nudge. */
fun weeklyPromptText(people: List<Person>, today: LocalDate): String {
    val s = suggestions(people, today, 7)
    if (s.isEmpty()) return "Take a minute to plan who you'd like to see or call this week."
    val names = s.take(3).map { it.person.shortName }
    val who = when (names.size) {
        1 -> names[0]
        2 -> "${names[0]} and ${names[1]}"
        else -> "${names[0]}, ${names[1]} and ${if (s.size > 3) "${s.size - 2} others" else names[2]}"
    }
    return "$who would love to hear from you. When are you free this week?"
}

val WEEKDAYS: List<DayOfWeek> = DayOfWeek.entries
