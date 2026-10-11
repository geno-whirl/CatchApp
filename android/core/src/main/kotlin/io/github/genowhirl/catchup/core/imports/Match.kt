package io.github.genowhirl.catchup.core.imports

import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.PersonPatch
import io.github.genowhirl.catchup.core.applyOp
import io.github.genowhirl.catchup.core.contactKey
import io.github.genowhirl.catchup.core.newPersonFile
import io.github.genowhirl.catchup.core.personPath
import io.github.genowhirl.catchup.core.slugify
import io.github.genowhirl.catchup.core.toJson

data class ImportItem(
    val key: String,
    val candidate: Candidate,
    val match: Person?,
    /** What would change, e.g. ["birthday", "phone", "last contact"]; ["new"] for new people. */
    val changes: List<String>,
    val op: Op?,
)

private fun later(a: String?, b: String?): String? = when {
    a == null -> b
    b == null -> a
    else -> maxOf(a, b)
}

/** Combines candidates for the same person (e.g. a friend you also message). */
fun mergeCandidates(candidates: List<Candidate>): List<Candidate> {
    val byName = linkedMapOf<String, Candidate>()
    for (c in candidates) {
        val key = normalizeName(c.name)
        val e = byName[key]
        if (e == null) {
            byName[key] = c.copy(contact = LinkedHashMap(c.contact), family = c.family.toMutableList(), tags = c.tags.toMutableList())
            continue
        }
        e.nickname = e.nickname ?: c.nickname
        e.birthday = e.birthday ?: c.birthday
        e.location = e.location ?: c.location
        e.howWeMet = e.howWeMet ?: c.howWeMet
        e.notes = e.notes ?: c.notes
        e.lastContacted = later(e.lastContacted, c.lastContacted)
        e.detail = listOfNotNull(e.detail, c.detail).joinToString(" · ")
        for ((k, v) in c.contact) if (e.contact.values.none { contactKey(it) == contactKey(v) }) VCard.addContact(e.contact, k, v)
        c.tags.filter { it !in e.tags }.forEach { e.tags += it }
        c.family.filter { f -> e.family.none { normalizeName(it.first) == normalizeName(f.first) } }.forEach { e.family += it }
    }
    return byName.values.toList()
}

fun findMatch(c: Candidate, people: List<Person>): Person? {
    val name = normalizeName(c.name)
    people.firstOrNull { normalizeName(it.name) == name || it.nickname?.let(::normalizeName) == name }?.let { return it }
    val keys = c.contact.values.map(::contactKey).toSet()
    if (keys.isEmpty()) return null
    return people.firstOrNull { p -> p.contact.values.any { contactKey(it) in keys } }
}

/** What an import would do: new files, or additive updates to people you already have. */
fun planImport(candidates: List<Candidate>, people: List<Person>, files: Map<String, String>): List<ImportItem> =
    mergeCandidates(candidates).mapIndexed { i, c ->
        val match = findMatch(c, people)
        val key = "$i:${normalizeName(c.name)}"
        if (match == null) {
            val content = newPersonFile(
                linkedMapOf(
                    "name" to c.name,
                    "nickname" to c.nickname,
                    "tags" to c.tags,
                    "birthday" to c.birthday,
                    "location" to c.location,
                    "contact" to c.contact,
                    "family" to c.family.map { (n, r) -> linkedMapOf("name" to n, "relation" to r) },
                    "how_we_met" to c.howWeMet,
                    "last_contacted" to c.lastContacted,
                ),
                c.notes,
            )
            return@mapIndexed ImportItem(key, c, null, listOf("new"), Op.Create(personPath(slugify(c.name)), content))
        }

        val patch = PersonPatch(
            fill = buildMap {
                c.nickname?.let { put("nickname", it.toJson()) }
                c.birthday?.let { put("birthday", it.toJson()) }
                c.location?.let { put("location", it.toJson()) }
                c.howWeMet?.let { put("how_we_met", it.toJson()) }
                if (c.family.isNotEmpty()) put("family", c.family.map { (n, r) -> linkedMapOf("name" to n, "relation" to r) }.toJson())
            },
            contact = c.contact,
            tags = c.tags,
            lastContacted = c.lastContacted,
        )
        val op = Op.Patch(personPath(match.id), patch)
        val changes = buildList {
            if (c.nickname != null && match.nickname == null) add("nickname")
            if (c.birthday != null && match.birthday == null) add("birthday")
            if (c.location != null && match.location == null) add("location")
            if (c.howWeMet != null && match.howWeMet == null) add("how you met")
            if (c.family.isNotEmpty() && match.family.isEmpty()) add("family")
            val have = match.contact.values.map(::contactKey).toSet()
            c.contact.filter { contactKey(it.value) !in have }.keys.forEach { add(it.replace(Regex("_\\d+$"), "")) }
            if (c.tags.any { it !in match.tags }) add("tags")
            if (c.lastContacted != null && (match.lastContacted == null || c.lastContacted!! > match.lastContacted.toString())) add("last contact")
        }.distinct()
        val before = files[op.path]
        val unchanged = changes.isEmpty() || (before != null && applyOp(files, op).first[op.path] == before)
        ImportItem(key, c, match, if (unchanged) emptyList() else changes, if (unchanged) null else op)
    }
