package io.github.genowhirl.catchup.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate

/*
 * Every change is an "op": plain data that can be queued while offline and re-applied on top
 * of the latest version of a file when syncing. So edits made in the app, by an AI assistant
 * or on another device all survive.
 */

@Serializable
data class InteractionInput(
    val date: String,
    val time: String? = null,
    val kind: String,
    val place: String? = null,
    val notes: List<String> = emptyList(),
    val followUps: List<String> = emptyList(),
)

@Serializable
data class PlanInput(
    val at: String,
    val kind: String? = null,
    val place: String? = null,
    /** e.g. `30m`, `2h`, `1d`, `none`; null = app default. */
    val remind: String? = null,
    val notes: String? = null,
)

/** Additive changes used by imports: never overwrites what's already there. */
@Serializable
data class PersonPatch(
    val fill: Map<String, JsonElement> = emptyMap(),
    val contact: Map<String, String> = emptyMap(),
    val tags: List<String> = emptyList(),
    /** YYYY-MM-DD; applied only if newer. */
    val lastContacted: String? = null,
)

@Serializable
sealed interface Op {
    val path: String

    @Serializable @SerialName("create")
    data class Create(override val path: String, val content: String, val skipIfExists: Boolean = false) : Op

    @Serializable @SerialName("replace")
    data class Replace(override val path: String, val content: String) : Op

    @Serializable @SerialName("delete")
    data class Delete(override val path: String) : Op

    /** Logs an interaction; `planKey` marks the plan it fulfils (removed from `plans`). */
    @Serializable @SerialName("log")
    data class Log(override val path: String, val entry: InteractionInput, val planKey: String? = null) : Op

    @Serializable @SerialName("followUp")
    data class SetFollowUp(override val path: String, val text: String, val done: Boolean) : Op

    /** Sets frontmatter fields (JSON null removes a field). */
    @Serializable @SerialName("fields")
    data class SetFields(override val path: String, val fields: Map<String, JsonElement>) : Op

    /** Adds a plan, or replaces the one at `replaceKey`. */
    @Serializable @SerialName("plan")
    data class SavePlan(override val path: String, val plan: PlanInput, val replaceKey: String? = null) : Op

    @Serializable @SerialName("removePlan")
    data class RemovePlan(override val path: String, val key: String) : Op

    @Serializable @SerialName("patch")
    data class Patch(override val path: String, val patch: PersonPatch) : Op

    /** Adds a `- [ ]` item to the notes: something to bring up next time. */
    @Serializable @SerialName("addFollowUp")
    data class AddFollowUp(override val path: String, val text: String) : Op

    /** Replaces the free-form notes, keeping the `## Log` section as it is. */
    @Serializable @SerialName("notes")
    data class SetNotes(override val path: String, val notes: String) : Op
}

/** JSON (op payloads) → the plain values the YAML writer understands. */
fun JsonElement.toPlain(): Any? = when (this) {
    is JsonNull -> null
    is JsonPrimitive -> if (isString) content else booleanOrNull ?: longOrNull ?: content
    is JsonArray -> map { it.toPlain() }
    is JsonObject -> entries.associate { (k, v) -> k to v.toPlain() }
}

fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is Map<*, *> -> JsonObject(entries.associate { (k, v) -> k.toString() to v.toJson() })
    is Iterable<*> -> JsonArray(map { it.toJson() })
    else -> JsonPrimitive(toString())
}

fun uniquePath(files: Map<String, String>, path: String): String {
    if (path !in files) return path
    val stem = path.removeSuffix(".md")
    return generateSequence(2) { it + 1 }.map { "$stem-$it.md" }.first { it !in files }
}

private fun planMap(p: PlanInput): Map<String, Any?> = linkedMapOf(
    "when" to p.at, "what" to p.kind, "where" to p.place, "remind" to p.remind, "notes" to p.notes,
)

/** Moves the old single `next_catchup` field into `plans`. */
private fun PersonDocument.migrate() {
    val legacy = front.remove("next_catchup")?.toString()?.trim()
    if (!legacy.isNullOrEmpty()) {
        val plans = plansList()
        if (plans.none { (it as? Map<*, *>)?.get("when")?.toString() == legacy }) plans += mapOf("when" to legacy)
        front["plans"] = plans
    }
}

private fun PersonDocument.plansList(): MutableList<Any?> =
    ((front["plans"] as? List<*>)?.toMutableList() ?: mutableListOf())

private fun PersonDocument.setPlans(plans: List<Any?>) {
    val sorted = plans.sortedBy { (it as? Map<*, *>)?.get("when")?.toString() ?: "" }
    if (sorted.isEmpty()) front.remove("plans") else front["plans"] = sorted
}

private fun planKeyOf(entry: Any?): String? =
    ((entry as? Map<*, *>)?.get("when"))?.toString()?.let { When.parse(it)?.format() ?: it }

fun formatInteractionHeading(e: InteractionInput): String {
    val at = listOfNotNull(e.date, e.time?.takeIf { it.isNotBlank() }).joinToString(" ")
    val rest = listOfNotNull(e.kind.trim().ifEmpty { null }, e.place?.trim()?.ifEmpty { null })
    return "### $at" + if (rest.isEmpty()) "" else " · " + rest.joinToString(" · ")
}

fun formatInteraction(e: InteractionInput): String = buildString {
    append(formatInteractionHeading(e))
    e.notes.map { it.trim() }.filter { it.isNotEmpty() }.forEach { append("\n- ").append(it) }
    e.followUps.map { it.trim() }.filter { it.isNotEmpty() }.forEach { append("\n- [ ] ").append(it) }
}

private val LOG_SECTION = Regex("""^##\s+(log|interactions)\s*$""", RegexOption.IGNORE_CASE)

/** Inserts an entry under `## Log`, keeping entries newest first. */
internal fun insertInteraction(body: String, entry: InteractionInput): String {
    val text = formatInteraction(entry)
    val sortKey = listOfNotNull(entry.date, entry.time).joinToString(" ")
    val lines = body.split("\n").toMutableList()
    val start = lines.indexOfFirst { LOG_SECTION.matches(it.trimEnd()) }
    if (start == -1) {
        val trimmed = body.trimEnd()
        return (if (trimmed.isEmpty()) "\n" else "$trimmed\n\n") + "## Log\n\n$text\n"
    }
    var end = lines.size
    for (i in start + 1 until lines.size) if (lines[i].startsWith("## ")) { end = i; break }

    for (i in start + 1 until end) {
        val m = ENTRY_HEADING.matchEntire(lines[i].trimEnd()) ?: continue
        val key = listOf(m.groupValues[1], m.groupValues[2]).filter { it.isNotEmpty() }.joinToString(" ")
        if (key <= sortKey) {
            lines.addAll(i, text.split("\n") + "")
            return lines.joinToString("\n")
        }
    }
    // Oldest (or the section is empty): append at the end of the section.
    var at = end
    while (at > start + 1 && lines[at - 1].isBlank()) at--
    val block = (listOf("") + text.split("\n")).toMutableList()
    if (end < lines.size) block += ""
    repeat(end - at) { lines.removeAt(at) }
    lines.addAll(at, block)
    val result = lines.joinToString("\n")
    return if (result.endsWith("\n")) result else "$result\n"
}

/** Adds `- [ ] text` at the end of the notes, i.e. just before `## Log` (or at the end). */
internal fun addTask(body: String, text: String): String {
    val lines = body.split("\n").toMutableList()
    val log = lines.indexOfFirst { LOG_SECTION.matches(it.trimEnd()) }
    var at = if (log == -1) lines.size else log
    while (at > 0 && lines[at - 1].isBlank()) at--
    val afterTask = at > 0 && TASK.matches(lines[at - 1])
    val block = mutableListOf<String>()
    if (at > 0 && !afterTask) block += ""
    block += "- [ ] $text"
    if (log != -1 && lines.getOrNull(at)?.isBlank() != true) block += ""
    lines.addAll(at, block)
    var result = lines.joinToString("\n")
    if (!result.startsWith("\n")) result = "\n" + result
    return if (result.endsWith("\n")) result else "$result\n"
}

internal fun setFollowUp(body: String, text: String, done: Boolean): String {
    val lines = body.split("\n").toMutableList()
    val target = text.trim()
    val i = lines.indexOfFirst { line ->
        val m = TASK.matchEntire(line)
        m != null && m.groupValues[2] == target && (m.groupValues[1] != " ") != done
    }
    if (i == -1) return body
    lines[i] = lines[i].replaceFirst(Regex("""\[[ xX]]"""), if (done) "[x]" else "[ ]")
    return lines.joinToString("\n")
}

private fun normalizeContact(value: String): String {
    val v = value.trim().lowercase()
    val digits = v.filter { it.isDigit() }
    if (digits.length >= 7 && v.all { it.isDigit() || it in " +().-" }) return "tel:" + digits.takeLast(9)
    return v.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')
}

internal fun contactKey(value: String) = normalizeContact(value)

private fun isBlankValue(v: Any?) = v == null || (v is String && v.isBlank()) ||
    (v is Collection<*> && v.isEmpty()) || (v is Map<*, *> && v.isEmpty())

private fun PersonDocument.applyPatch(patch: PersonPatch) {
    for ((k, v) in patch.fill) {
        val value = v.toPlain()
        if (!isBlankValue(value) && isBlankValue(front[k])) front[k] = value
    }
    if (patch.contact.isNotEmpty()) {
        val contact = LinkedHashMap<String, Any?>()
        (front["contact"] as? Map<*, *>)?.forEach { (k, v) -> contact[k.toString()] = v }
        val have = contact.values.mapNotNull { it?.toString() }.map(::normalizeContact).toMutableSet()
        for ((k, v) in patch.contact) {
            if (v.isBlank() || normalizeContact(v) in have) continue
            var key = k
            var n = 2
            while (key in contact) key = "${k}_${n++}"
            contact[key] = v
            have += normalizeContact(v)
        }
        front["contact"] = contact
    }
    if (patch.tags.isNotEmpty()) {
        val tags = (front["tags"] as? List<*>)?.map { it.toString() }?.toMutableList() ?: mutableListOf()
        patch.tags.filter { it !in tags }.forEach { tags += it }
        front["tags"] = tags
    }
    patch.lastContacted?.let { raw ->
        val next = parseDate(raw) ?: return@let
        val current = front["last_contacted"]?.toString()?.let(::parseDate)
        if (current == null || next.isAfter(current)) front["last_contacted"] = raw
    }
}

/**
 * After logging, an explicit `next_contact` that this interaction satisfies is cleared, so the
 * cadence takes over again. A far-off date the user picked on purpose is kept.
 */
private fun PersonDocument.clearSatisfiedNextContact(date: LocalDate) {
    val next = front["next_contact"]?.toString()?.let(::parseDate) ?: return
    val cadenceDays = front["cadence"]?.toString()?.let { Cadence.parse(it)?.days } ?: 30
    val window = maxOf(7, cadenceDays / 2).toLong()
    if (!next.isAfter(date.plusDays(window))) front.remove("next_contact")
}

/** Applies one op. Returns the new files and the path it touched. */
fun applyOp(files: Map<String, String>, op: Op): Pair<Map<String, String>, String> {
    when (op) {
        is Op.Create -> {
            if (op.skipIfExists && op.path in files) return files to op.path
            val path = uniquePath(files, op.path)
            return files + (path to op.content) to path
        }
        is Op.Replace -> return files + (op.path to op.content) to op.path
        is Op.Delete -> return files - op.path to op.path
        else -> {}
    }
    // Everything else edits an existing file; if it was deleted elsewhere, skip.
    val current = files[op.path] ?: return files to op.path
    // Never rewrite a file whose frontmatter we can't read.
    val doc = PersonDocument.parse(current) ?: return files to op.path
    doc.migrate()

    when (op) {
        is Op.Log -> {
            doc.body = insertInteraction(doc.body, op.entry)
            val date = parseDate(op.entry.date)
            val plans = doc.plansList()
            if (op.planKey != null) plans.removeAll { planKeyOf(it) == op.planKey }
            // Logged on its own: plans this catch-up covers are done too.
            else When.parse(listOfNotNull(op.entry.date, op.entry.time).joinToString(" "))?.let { logged ->
                plans.removeAll { m -> planKeyOf(m)?.let { When.parse(it) }?.let { settles(logged, it) } == true }
            }
            doc.setPlans(plans)
            date?.let { doc.clearSatisfiedNextContact(it) }
        }
        is Op.SetFollowUp -> doc.body = setFollowUp(doc.body, op.text, op.done)
        is Op.SetFields -> for ((k, v) in op.fields) {
            val value = v.toPlain()
            if (isBlankValue(value)) doc.front.remove(k) else doc.front[k] = value
        }
        is Op.SavePlan -> {
            val plans = doc.plansList()
            val key = When.parse(op.plan.at)?.format() ?: op.plan.at
            val replaced = op.replaceKey ?: key
            val i = plans.indexOfFirst { planKeyOf(it) == replaced }
            if (i >= 0) plans[i] = planMap(op.plan) else plans += planMap(op.plan)
            doc.setPlans(plans)
        }
        is Op.RemovePlan -> {
            val plans = doc.plansList()
            plans.removeAll { planKeyOf(it) == op.key }
            doc.setPlans(plans)
        }
        is Op.Patch -> doc.applyPatch(op.patch)
        is Op.AddFollowUp -> doc.body = addTask(doc.body, op.text.trim())
        is Op.SetNotes -> {
            val (_, log) = splitLog(doc.body)
            val notes = op.notes.trim()
            val logText = log.joinToString("\n").trim()
            doc.body = listOf(notes, logText).filter { it.isNotEmpty() }.joinToString("\n\n", prefix = "\n", postfix = "\n")
                .let { if (it.isBlank()) "" else it }
        }
        is Op.Create, is Op.Replace, is Op.Delete -> error("handled above")
    }
    val rendered = doc.render()
    return (if (rendered == current) files else files + (op.path to rendered)) to op.path
}

fun applyOps(files: Map<String, String>, ops: List<Op>): Map<String, String> =
    ops.fold(files) { acc, op -> applyOp(acc, op).first }

fun describeOp(op: Op): String {
    val who = idFromPath(op.path)
    return when (op) {
        is Op.Create -> "Add $who"
        is Op.Replace -> "Edit $who"
        is Op.Delete -> "Remove $who"
        is Op.Log -> "Log ${op.entry.kind.lowercase().ifEmpty { "catch-up" }} with $who (${op.entry.date})"
        is Op.SetFollowUp -> "${if (op.done) "Complete" else "Reopen"} follow-up for $who"
        is Op.SetFields -> "Update ${op.fields.keys.joinToString(", ")} for $who"
        is Op.SavePlan -> "Plan ${op.plan.kind?.lowercase() ?: "catch-up"} with $who (${op.plan.at})"
        is Op.RemovePlan -> "Cancel plan with $who (${op.key})"
        is Op.Patch -> "Import details for $who"
        is Op.SetNotes -> "Edit notes for $who"
        is Op.AddFollowUp -> "Add follow-up for $who"
    }
}

fun commitMessage(ops: List<Op>): String =
    if (ops.size == 1) describeOp(ops[0])
    else "CatchUp: ${ops.size} changes\n\n" + ops.joinToString("\n") { "- ${describeOp(it)}" }

/** A new person file in the documented format, omitting empty fields. */
fun newPersonFile(fields: Map<String, Any?>, notes: String? = null): String {
    val doc = PersonDocument(LinkedHashMap(fields.filterValues { !isBlankValue(it) }), "")
    doc.body = notes?.trim()?.takeIf { it.isNotEmpty() }?.let { "\n$it\n" } ?: ""
    return doc.render()
}
