package io.github.genowhirl.catchup.core

import java.time.LocalDate

enum class Priority(val key: String, val label: String, val weight: Double) {
    HIGH("high", "High", 1.6),
    MEDIUM("medium", "Medium", 1.0),
    LOW("low", "Low", 0.6);

    companion object {
        fun parse(value: String?): Priority? = when (value?.trim()?.lowercase()) {
            "high", "h", "3", "inner", "inner-circle" -> HIGH
            "medium", "normal", "m", "2" -> MEDIUM
            "low", "l", "1" -> LOW
            else -> null
        }
    }
}

data class FamilyMember(
    val name: String,
    val relation: String? = null,
    val birthday: Birthday? = null,
    val notes: String? = null,
)

/** A `- [ ] …` item: something to bring up next time. */
data class FollowUp(val text: String, val done: Boolean)

/** One logged conversation or meeting (`### YYYY-MM-DD HH:MM · Kind · Place` under `## Log`). */
data class Interaction(
    val at: When,
    val kind: String?,
    val place: String?,
    val notes: List<String>,
    val followUps: List<FollowUp>,
) {
    val date: LocalDate get() = at.date
}

/** A scheduled catch-up, kept in the `plans:` list until it's logged. */
data class Plan(
    val at: When,
    val kind: String? = null,
    val place: String? = null,
    /** Minutes before `at` to remind; null = app default, 0 = no reminder. */
    val remindMinutes: Int? = null,
    val notes: String? = null,
) {
    /** Identifies the plan inside a file (used by edit and remove ops). */
    val key: String get() = at.format()
}

data class Person(
    /** File name without `.md`, e.g. `maya-patel`. */
    val id: String,
    val name: String,
    val nickname: String? = null,
    val relationship: String? = null,
    val priority: Priority = Priority.MEDIUM,
    val priorityIsSet: Boolean = false,
    val tags: List<String> = emptyList(),
    val birthday: Birthday? = null,
    val location: String? = null,
    /** Language tag for invitations, e.g. `es` or `pt-BR`. */
    val language: String? = null,
    val contact: Map<String, String> = emptyMap(),
    val family: List<FamilyMember> = emptyList(),
    val howWeMet: String? = null,
    val cadence: Cadence? = null,
    /** An explicit "next time to get in touch", overriding the cadence. */
    val nextContact: LocalDate? = null,
    val lastContactedField: LocalDate? = null,
    val plans: List<Plan> = emptyList(),
    /** Newest first. */
    val interactions: List<Interaction> = emptyList(),
    val followUps: List<FollowUp> = emptyList(),
    /** Free-form notes: the body without the `## Log` section. */
    val notes: String = "",
) {
    val path: String get() = personPath(id)
    val shortName: String get() = nickname ?: name.substringBefore(' ')
    val openFollowUps: List<String> get() = followUps.filter { !it.done }.map { it.text }

    /** The most recent of `last_contacted` and the newest logged interaction. */
    val lastContacted: LocalDate?
        get() = listOfNotNull(lastContactedField, interactions.firstOrNull()?.date).maxOrNull()

    /** When to next get in touch: the explicit date, else last contact + cadence. */
    fun dueDate(today: LocalDate): LocalDate? =
        nextContact ?: cadence?.let { c -> lastContacted?.plusDays(c.days.toLong()) ?: today }

    fun upcomingPlans(today: LocalDate): List<Plan> = plans.filter { !it.at.date.isBefore(today) }.sortedBy { it.at }

    /** Plans whose time has passed but that haven't been logged yet. */
    fun pastPlans(now: java.time.LocalDateTime): List<Plan> =
        plans.filter { it.at.dateTime.isBefore(now) && !(it.at.time == null && it.at.date == now.toLocalDate()) && !loggedSince(it) }

    /** Whether something was logged at or after the plan (it happened, or was overtaken). */
    fun loggedSince(plan: Plan): Boolean = interactions.any { settles(it.at, plan.at) }
}

const val PEOPLE_DIR = "people"

fun personPath(id: String) = "$PEOPLE_DIR/$id.md"

fun idFromPath(path: String) = path.substringAfterLast('/').removeSuffix(".md")

/** Markdown files directly in `people/`, except `_template.md`-style files and READMEs. */
fun isPersonFile(path: String): Boolean {
    if (!path.startsWith("$PEOPLE_DIR/")) return false
    val name = path.removePrefix("$PEOPLE_DIR/")
    return !name.contains('/') && name.endsWith(".md") && !name.startsWith("_") && !name.equals("readme.md", true)
}

/** Lowercase, hyphenated, ASCII file-name stem for a name. */
fun slugify(name: String): String {
    val ascii = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "")
    return ascii.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifEmpty { "person" }
}

/**
 * Whether a catch-up logged at `logged` covers a plan for `planned`: it's on a later day, or
 * the same day no more than an hour before the planned time (or either has no time).
 */
fun settles(logged: When, planned: When): Boolean = when {
    logged.date.isAfter(planned.date) -> true
    logged.date.isBefore(planned.date) -> false
    logged.time == null || planned.time == null -> true
    else -> !logged.time!!.isBefore(planned.time!!.minusHours(1))
}
