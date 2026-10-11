package io.github.genowhirl.catchup.ui.components

import io.github.genowhirl.catchup.core.Birthday
import io.github.genowhirl.catchup.core.Plan
import io.github.genowhirl.catchup.core.When
import io.github.genowhirl.catchup.core.daysBetween
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale

private val locale get() = Locale.getDefault()

fun shortDate(d: LocalDate): String = d.format(DateTimeFormatter.ofPattern("d MMM", locale))

fun longDate(d: LocalDate): String = d.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale))

fun weekday(d: LocalDate, style: TextStyle = TextStyle.SHORT): String = d.dayOfWeek.getDisplayName(style, locale)

/** "Today", "Tomorrow", "Thu", "Thu 23 Oct". */
fun relativeDay(d: LocalDate, today: LocalDate): String = when (daysBetween(today, d)) {
    0 -> "Today"
    1 -> "Tomorrow"
    -1 -> "Yesterday"
    in 2..6 -> weekday(d, TextStyle.FULL)
    else -> "${weekday(d)} ${shortDate(d)}" + if (d.year != today.year) " ${d.year}" else ""
}

fun whenText(w: When, today: LocalDate): String =
    relativeDay(w.date, today) + (w.time?.let { " · ${it.format(When.HH_MM)}" } ?: "")

fun planText(p: Plan, today: LocalDate): String =
    listOfNotNull(whenText(p.at, today), p.place).joinToString(" · ")

fun birthdayText(b: Birthday): String {
    val d = LocalDate.of(b.year ?: 2000, b.month, b.day)
    val md = d.format(DateTimeFormatter.ofPattern("d MMMM", locale))
    return if (b.year != null) "$md ${b.year}" else md
}

fun inDays(n: Int): String = when {
    n == 0 -> "today"
    n == 1 -> "tomorrow"
    n < 14 -> "in $n days"
    n < 60 -> "in ${n / 7} weeks"
    else -> "in ${n / 30} months"
}

fun minutesLabel(m: Int): String = when {
    m == 0 -> "No reminder"
    m < 60 -> "$m min before"
    m % 1440 == 0 -> if (m == 1440) "1 day before" else "${m / 1440} days before"
    else -> if (m == 60) "1 hour before" else "${m / 60} hours before"
}
