package io.github.genowhirl.catchup.core

import java.time.LocalDate

/** A problem found while reading a file. Errors make a field unusable; warnings are hints. */
data class Issue(val level: Level, val message: String) {
    enum class Level { ERROR, WARNING }
}

data class ParseResult(val person: Person?, val issues: List<Issue>)

/** A person file split into editable frontmatter and the Markdown body. */
class PersonDocument(
    val front: MutableMap<String, Any?>,
    var body: String,
    /** The original `---` block, reused verbatim when the fields haven't changed. */
    private val rawHeader: String? = null,
) {
    private val original: Map<String, Any?> = deepCopy(front)

    fun render(): String {
        val header = if (rawHeader != null && front == original) rawHeader else "---\n" + YamlWriter.write(front) + "---\n"
        return header + body
    }

    companion object {
        private val FRONTMATTER = Regex("^\uFEFF?---[ \t]*\r?\n(?:([\\s\\S]*?)\r?\n)?---[ \t]*(?:\r?\n|$)")

        /** Null if there's no frontmatter or it isn't valid YAML. */
        fun parse(text: String): PersonDocument? = parseOrError(text).getOrNull()

        fun parseOrError(text: String): Result<PersonDocument> {
            val m = FRONTMATTER.find(text)
            if (m == null || m.range.first != 0) {
                return Result.failure(IllegalArgumentException("Missing frontmatter (the file must start with a `---` block)."))
            }
            val yaml = m.groupValues[1]
            val data = try {
                if (yaml.isBlank()) emptyMap<String, Any?>() else loadYaml(yaml)
            } catch (e: Exception) {
                val first = e.message?.lineSequence()?.firstOrNull { it.isNotBlank() } ?: "invalid YAML"
                return Result.failure(IllegalArgumentException("Frontmatter is not valid YAML: $first"))
            }
            if (data != null && data !is Map<*, *>) {
                return Result.failure(IllegalArgumentException("Frontmatter must be a set of `key: value` fields."))
            }
            val front = LinkedHashMap<String, Any?>()
            (data as Map<*, *>?)?.forEach { (k, v) -> front[k.toString()] = v }
            val header = m.value.removePrefix("\uFEFF").let { if (it.endsWith("\n")) it else "$it\n" }
            return Result.success(PersonDocument(front, text.substring(m.range.last + 1), header))
        }
    }
}

@Suppress("UNCHECKED_CAST")
private fun <T> deepCopy(v: T): T = when (v) {
    is Map<*, *> -> LinkedHashMap(v.entries.associate { (k, x) -> k to deepCopy(x) }) as T
    is List<*> -> v.map { deepCopy(it) } as T
    else -> v
}

internal val KNOWN_KEYS = YamlWriter.ORDER.toSet() + "next_catchup"

private val LOG_HEADING = Regex("""^##\s+(log|interactions)\s*$""", RegexOption.IGNORE_CASE)
private val H2 = Regex("""^##\s""")
internal val ENTRY_HEADING = Regex("""^###\s+(\d{4}-\d{2}-\d{2})(?:[ T](\d{1,2}:\d{2}))?\b\s*(?:[-–—·:|]\s*)?(.*)$""")
internal val TASK = Regex("""^\s*[-*+]\s+\[([ xX])]\s+(.+?)\s*$""")
private val BULLET = Regex("""^\s*[-*+]\s+(.*)$""")
private val REMIND = Regex("""^(\d+)\s*([mhd])$""")

fun parseRemind(value: String?): Int? {
    val v = value?.trim()?.lowercase() ?: return null
    if (v == "none" || v == "no" || v == "0") return 0
    val m = REMIND.matchEntire(v) ?: return null
    val n = m.groupValues[1].toInt()
    return when (m.groupValues[2]) {
        "m" -> n
        "h" -> n * 60
        else -> n * 24 * 60
    }
}

fun formatRemind(minutes: Int): String = when {
    minutes == 0 -> "none"
    minutes % (24 * 60) == 0 -> "${minutes / (24 * 60)}d"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes}m"
}

private fun text(v: Any?): String? = when (v) {
    null -> null
    is String -> v.trim().ifEmpty { null }
    is Number, is Boolean -> v.toString()
    else -> null
}

/** Splits the body into the free-form notes and the `## Log` section's lines. */
internal fun splitLog(body: String): Pair<String, List<String>> {
    val notes = mutableListOf<String>()
    val log = mutableListOf<String>()
    var inLog = false
    for (line in body.lines()) {
        if (H2.containsMatchIn(line)) inLog = LOG_HEADING.matches(line.trimEnd())
        if (inLog) log += line else notes += line
    }
    return notes.joinToString("\n").trim() to log
}

internal fun parseInteractions(logLines: List<String>): List<Interaction> {
    val out = mutableListOf<Interaction>()
    var current: Triple<When, String?, String?>? = null
    val notes = mutableListOf<String>()
    val tasks = mutableListOf<FollowUp>()
    fun flush() {
        current?.let { (at, kind, place) -> out += Interaction(at, kind, place, notes.toList(), tasks.toList()) }
        notes.clear()
        tasks.clear()
    }
    for (line in logLines) {
        val m = ENTRY_HEADING.matchEntire(line.trimEnd())
        if (m != null) {
            flush()
            val at = When.parse(listOf(m.groupValues[1], m.groupValues[2]).filter { it.isNotEmpty() }.joinToString(" "))
            val parts = m.groupValues[3].split(" · ").map { it.trim() }.filter { it.isNotEmpty() }
            current = at?.let { Triple(it, parts.getOrNull(0), parts.drop(1).joinToString(" · ").ifEmpty { null }) }
            continue
        }
        if (line.startsWith("### ") || H2.containsMatchIn(line)) {
            flush()
            current = null
            continue
        }
        if (current == null) continue
        val task = TASK.matchEntire(line)
        if (task != null) {
            tasks += FollowUp(task.groupValues[2], task.groupValues[1] != " ")
            continue
        }
        val bullet = BULLET.matchEntire(line)
        if (bullet != null) notes += bullet.groupValues[1].trim()
        else if (line.isNotBlank()) notes += line.trim()
    }
    flush()
    return out.sortedByDescending { it.at }
}

fun parseFollowUps(body: String): List<FollowUp> =
    body.lines().mapNotNull { TASK.matchEntire(it) }.map { FollowUp(it.groupValues[2], it.groupValues[1] != " ") }

/** Reads one person file. Never throws: problems are reported as issues. */
fun parsePerson(id: String, text: String): ParseResult {
    val issues = mutableListOf<Issue>()
    fun error(m: String) = issues.add(Issue(Issue.Level.ERROR, m))
    fun warn(m: String) = issues.add(Issue(Issue.Level.WARNING, m))

    val doc = PersonDocument.parseOrError(text).getOrElse {
        error(it.message ?: "Unreadable file.")
        return ParseResult(null, issues)
    }
    val d = doc.front
    d.keys.filter { it !in KNOWN_KEYS }.forEach { warn("Unknown field `$it` (kept, but not used).") }

    val name = text(d["name"]) ?: run {
        error("`name` is required.")
        return ParseResult(null, issues)
    }

    val tags = when (val t = d["tags"]) {
        null -> emptyList()
        is List<*> -> t.mapNotNull { text(it) }
        else -> { warn("`tags` should be a list, e.g. `tags: [college, hiking]`."); emptyList() }
    }

    val priority = text(d["priority"])?.let { raw ->
        Priority.parse(raw) ?: run { error("`priority` \"$raw\" should be high, medium or low."); null }
    }

    val birthday = text(d["birthday"])?.let { raw ->
        Birthday.parse(raw) ?: run { error("`birthday` \"$raw\" must be YYYY-MM-DD or MM-DD."); null }
    }

    val contact = linkedMapOf<String, String>()
    when (val c = d["contact"]) {
        null -> {}
        is Map<*, *> -> c.forEach { (k, v) ->
            if (v is Number) warn("`contact.$k` was read as a number; wrap it in quotes to keep a leading + or 0.")
            text(v)?.let { contact[k.toString()] = it }
        }
        else -> warn("`contact` should be a set of fields, e.g. `phone`, `email`.")
    }

    val family = mutableListOf<FamilyMember>()
    when (val f = d["family"]) {
        null -> {}
        is List<*> -> f.forEachIndexed { i, entry ->
            when (entry) {
                is String -> family += FamilyMember(entry)
                is Map<*, *> -> {
                    val memberName = text(entry["name"])
                    if (memberName == null) warn("`family[$i]` needs a `name`.")
                    else {
                        val bdRaw = text(entry["birthday"])
                        val bd = bdRaw?.let { Birthday.parse(it) }
                        if (bdRaw != null && bd == null) error("`family[$i].birthday` \"$bdRaw\" must be YYYY-MM-DD or MM-DD.")
                        family += FamilyMember(memberName, text(entry["relation"]), bd, text(entry["notes"]))
                    }
                }
                else -> warn("`family[$i]` should be a name or a set of fields.")
            }
        }
        else -> warn("`family` should be a list of people.")
    }

    val cadence = text(d["cadence"])?.let { raw ->
        Cadence.parse(raw) ?: run { error("`cadence` \"$raw\" is not recognised (try monthly, quarterly, or 6w)."); null }
    }

    fun date(key: String): LocalDate? = text(d[key])?.let { raw ->
        parseDate(raw) ?: run { error("`$key` \"$raw\" must be YYYY-MM-DD."); null }
    }
    val nextContact = date("next_contact")
    val lastContacted = date("last_contacted")

    val plans = mutableListOf<Plan>()
    when (val p = d["plans"]) {
        null -> {}
        is List<*> -> p.forEachIndexed { i, entry ->
            val m = entry as? Map<*, *>
            val raw = m?.let { text(it["when"]) }
            val at = raw?.let { When.parse(it) }
            if (at == null) error("`plans[$i].when` must be YYYY-MM-DD or YYYY-MM-DD HH:MM.")
            else {
                val remindRaw = text(m["remind"])
                val remind = remindRaw?.let { parseRemind(it) }
                if (remindRaw != null && remind == null) warn("`plans[$i].remind` \"$remindRaw\" should look like 30m, 2h, 1d or none.")
                plans += Plan(at, text(m["what"]), text(m["where"]), remind, text(m["notes"]))
            }
        }
        else -> warn("`plans` should be a list.")
    }
    // Older files used a single `next_catchup`.
    text(d["next_catchup"])?.let { raw ->
        When.parse(raw)?.let { at -> if (plans.none { it.at == at }) plans += Plan(at) }
            ?: error("`next_catchup` \"$raw\" must be YYYY-MM-DD or YYYY-MM-DD HH:MM.")
    }

    val (notes, logLines) = splitLog(doc.body)
    return ParseResult(
        Person(
            id = id,
            name = name,
            nickname = text(d["nickname"]),
            relationship = text(d["relationship"]),
            priority = priority ?: Priority.MEDIUM,
            priorityIsSet = priority != null,
            tags = tags,
            birthday = birthday,
            location = text(d["location"]),
            contact = contact,
            family = family,
            howWeMet = text(d["how_we_met"]),
            cadence = cadence,
            nextContact = nextContact,
            lastContactedField = lastContacted,
            plans = plans.sortedBy { it.at },
            interactions = parseInteractions(logLines),
            followUps = parseFollowUps(doc.body),
            notes = notes,
        ),
        issues,
    )
}

/** Parses every person file in a set of files. */
fun parseAll(files: Map<String, String>): Pair<List<Person>, Map<String, List<Issue>>> {
    val people = mutableListOf<Person>()
    val problems = sortedMapOf<String, List<Issue>>()
    for ((path, text) in files) {
        if (!isPersonFile(path)) continue
        val (person, issues) = parsePerson(idFromPath(path), text)
        person?.let { people += it }
        if (issues.isNotEmpty()) problems[path] = issues
    }
    return people.sortedBy { it.name.lowercase() } to problems
}
