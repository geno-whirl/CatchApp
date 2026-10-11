package io.github.genowhirl.catchup.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.MonthDay
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** A birthday; the year is optional because people often don't know it. */
data class Birthday(val month: Int, val day: Int, val year: Int? = null) {
    /** The next occurrence, today included. Feb 29 falls on Feb 28 in other years. */
    fun next(today: LocalDate): LocalDate {
        val thisYear = inYear(today.year)
        return if (!thisYear.isBefore(today)) thisYear else inYear(today.year + 1)
    }

    fun inYear(year: Int): LocalDate {
        val md = MonthDay.of(month, day)
        return if (md.isValidYear(year)) md.atYear(year) else LocalDate.of(year, month, day - 1)
    }

    fun daysUntil(today: LocalDate): Int = ChronoUnit.DAYS.between(today, next(today)).toInt()

    /** The age they turn on their next birthday, if the year is known. */
    fun upcomingAge(today: LocalDate): Int? = year?.let { next(today).year - it }

    /** `YYYY-MM-DD` or `MM-DD`. */
    fun format(): String = if (year != null) "%04d-%02d-%02d".format(year, month, day) else "%02d-%02d".format(month, day)

    companion object {
        private val FULL = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")
        private val PARTIAL = Regex("""^(?:--)?(\d{2})-(\d{2})$""")

        fun parse(value: String): Birthday? {
            val v = value.trim()
            parseDate(v)?.let { return Birthday(it.monthValue, it.dayOfMonth, it.year) }
            val m = PARTIAL.matchEntire(v) ?: return null
            val (month, day) = m.destructured
            return runCatching { MonthDay.of(month.toInt(), day.toInt()) }
                .getOrNull()
                ?.let { Birthday(it.monthValue, it.dayOfMonth) }
        }
    }
}

private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
private val DATE_TIME = Regex("""^(\d{4}-\d{2}-\d{2})(?:[ T](\d{1,2}):(\d{2}))?$""")

/** Parses `YYYY-MM-DD`; null if malformed or impossible (e.g. Feb 30). */
fun parseDate(value: String): LocalDate? {
    val v = value.trim()
    if (!ISO_DATE.matches(v)) return null
    return try {
        LocalDate.parse(v)
    } catch (_: DateTimeParseException) {
        null
    }
}

/** A day with an optional time of day. */
data class When(val date: LocalDate, val time: LocalTime? = null) : Comparable<When> {
    val dateTime: LocalDateTime get() = date.atTime(time ?: LocalTime.MIDNIGHT)

    override fun compareTo(other: When): Int =
        compareValuesBy(this, other, { it.date }, { it.time ?: LocalTime.MIN })

    /** `YYYY-MM-DD` or `YYYY-MM-DD HH:MM`. */
    fun format(): String = if (time == null) date.toString() else "$date ${time.format(HH_MM)}"

    companion object {
        val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

        fun parse(value: String): When? {
            val m = DATE_TIME.matchEntire(value.trim()) ?: return null
            val date = parseDate(m.groupValues[1]) ?: return null
            if (m.groupValues[2].isEmpty()) return When(date)
            val h = m.groupValues[2].toInt()
            val min = m.groupValues[3].toInt()
            if (h > 23 || min > 59) return null
            return When(date, LocalTime.of(h, min))
        }
    }
}

/** How often to keep in touch, e.g. `monthly` or `6w`. */
data class Cadence(val days: Int, val label: String) {
    companion object {
        val PRESETS = listOf(
            Cadence(7, "weekly"),
            Cadence(14, "biweekly"),
            Cadence(30, "monthly"),
            Cadence(61, "bimonthly"),
            Cadence(91, "quarterly"),
            Cadence(182, "biannually"),
            Cadence(365, "yearly"),
        )
        private val ALIASES = PRESETS.associateBy { it.label } + mapOf(
            "fortnightly" to Cadence(14, "fortnightly"),
            "annually" to Cadence(365, "annually"),
            "twice a year" to Cadence(182, "twice a year"),
        )
        private val UNITS = mapOf('d' to 1, 'w' to 7, 'm' to 30, 'y' to 365)
        private val SHORT = Regex("""^(\d+)\s*([dwmy])$""")

        fun parse(value: String): Cadence? {
            val v = value.trim().lowercase()
            ALIASES[v]?.let { return it.copy(label = v) }
            val m = SHORT.matchEntire(v) ?: return null
            val n = m.groupValues[1].toInt()
            if (n == 0) return null
            return Cadence(n * UNITS.getValue(m.groupValues[2][0]), v)
        }
    }
}

fun daysBetween(from: LocalDate, to: LocalDate): Int = ChronoUnit.DAYS.between(from, to).toInt()
