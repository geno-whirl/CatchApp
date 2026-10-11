package io.github.genowhirl.catchup.core.imports

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.time.Instant
import java.time.ZoneId

/**
 * Reads Facebook and Instagram "Download your information" exports (JSON): friends, mutual
 * follows and 1:1 message threads. Only dates and counts are kept from messages.
 */
object Meta {
    /** Meta's JSON stores UTF-8 bytes as \u00XX escapes ("ZoÃ«" instead of "Zoë"). */
    fun fixMojibake(text: String): String {
        if (text.none { it.code in 0x80..0xff } || text.any { it.code > 0xff }) return text
        return runCatching {
            val decoder = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(ByteArray(text.length) { text[it].code.toByte() })).toString()
        }.getOrDefault(text)
    }

    private class Thread(val folder: String?, val participants: List<String>, val count: Int, val last: Long?)

    private fun JsonElement?.str(): String? = (this as? kotlinx.serialization.json.JsonPrimitive)?.content
    private fun JsonElement?.arr(): List<JsonElement> = (this as? JsonArray) ?: emptyList()

    private fun handle(item: JsonElement): Triple<String, String?, Long?>? {
        val o = item as? JsonObject ?: return null
        val data = o["string_list_data"].arr().firstOrNull() as? JsonObject
        val href = data?.get("href").str()
        val h = o["title"].str()?.ifEmpty { null } ?: data?.get("value").str()
            ?: href?.replace(Regex("^.*instagram\\.com/(_u/)?"), "")?.trimEnd('/')
        return h?.let { Triple(it, href, data?.get("timestamp")?.jsonPrimitive?.longOrNull) }
    }

    fun parse(files: List<ImportFile>, source: ImportSource): ParseOutcome {
        val notes = mutableListOf<String>()
        val friends = mutableListOf<Pair<String, Long?>>()
        val following = mutableListOf<Triple<String, String?, Long?>>()
        val followers = mutableSetOf<String>()
        var sawFollowers = false
        val threads = mutableListOf<Thread>()
        var selfName: String? = null
        val json = Json { ignoreUnknownKeys = true }

        for (file in files.filter { it.name.endsWith(".json", true) }) {
            val data = runCatching { json.parseToJsonElement(file.text) }.getOrElse {
                notes += "Couldn't read ${file.name.substringAfterLast('/')}."
                continue
            }
            val obj = data as? JsonObject ?: JsonObject(emptyMap())
            (obj["friends_v2"] ?: obj["friends"]).arr().forEach { f ->
                f.jsonObject["name"].str()?.let { friends += fixMojibake(it) to f.jsonObject["timestamp"]?.jsonPrimitive?.longOrNull }
            }
            obj["relationships_following"].arr().forEach { handle(it)?.let { h -> following += h } }
            val followerItems = if (data is JsonArray && file.name.contains("followers", true)) data else obj["relationships_followers"].arr()
            if (followerItems.isNotEmpty()) sawFollowers = true
            followerItems.forEach { handle(it)?.let { h -> followers += h.first.lowercase() } }

            val participants = obj["participants"]
            val messages = obj["messages"]
            if (participants is JsonArray && messages is JsonArray) {
                val parts = file.name.split("/")
                threads += Thread(
                    if (parts.size > 1) parts[parts.size - 2] else null,
                    participants.mapNotNull { (it as? JsonObject)?.get("name").str()?.let(::fixMojibake) },
                    messages.size,
                    messages.mapNotNull { (it as? JsonObject)?.get("timestamp_ms")?.jsonPrimitive?.longOrNull }.maxOrNull(),
                )
            }
            (obj["profile_v2"] as? JsonObject)?.get("name")?.let { (it as? JsonObject)?.get("full_name").str() }?.let { selfName = fixMojibake(it) }
            ((obj["profile_user"].arr().firstOrNull() as? JsonObject)?.get("string_map_data") as? JsonObject)
                ?.get("Name")?.let { (it as? JsonObject)?.get("value").str() }?.let { selfName = fixMojibake(it) }
        }

        val candidates = mutableListOf<Candidate>()
        for ((name, since) in friends) {
            val year = since?.let { Instant.ofEpochSecond(it).atZone(ZoneId.of("UTC")).year }
            candidates += Candidate(name, source, howWeMet = year?.let { "Facebook friends since $it." }, detail = "Facebook friend")
        }
        for ((h, href, _) in following) {
            // Prefer mutual follows: people you follow who follow you back.
            if (sawFollowers && h.lowercase() !in followers) continue
            candidates += Candidate(h, source, detail = if (sawFollowers) "Mutual follow" else "You follow them").also {
                it.contact["instagram"] = href?.takeIf { u -> u.startsWith("http") } ?: "https://www.instagram.com/$h"
            }
        }

        // Your name: from the profile, or the one person in most threads.
        var self = selfName?.let(::normalizeName)
        if (self == null && threads.size > 1) {
            val counts = threads.flatMap { t -> t.participants.map(::normalizeName).toSet() }.groupingBy { it }.eachCount()
            counts.maxByOrNull { it.value }?.takeIf { it.value >= threads.size * 0.6 }?.let { self = it.key }
        }
        var groups = 0
        for (t in threads) {
            if (t.participants.size != 2) {
                if (t.participants.size > 2) groups++
                continue
            }
            var others = t.participants.filter { normalizeName(it) != self }
            if (others.size == 2 && t.folder != null) {
                // Thread folders are named after the other person, e.g. "mayapatel_10158…".
                val hint = t.folder.replace(Regex("_\\d+$"), "").lowercase()
                val byFolder = others.filter { normalizeName(it).replace(" ", "") == hint }
                if (byFolder.size == 1) others = byFolder
            }
            for (name in others) {
                candidates += Candidate(
                    name, source,
                    lastContacted = t.last?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of("UTC")).toLocalDate().toString() },
                    detail = "${t.count} ${if (source == ImportSource.FACEBOOK) "Messenger" else "Instagram"} messages",
                )
            }
            if (others.size > 1) notes += "Couldn't tell which name is yours in some threads — untick yourself."
        }
        if (groups > 0) notes += "Skipped $groups group conversation${if (groups > 1) "s" else ""}."
        if (candidates.isEmpty()) {
            notes += if (source == ImportSource.FACEBOOK) "No friends or messages found. Make sure the export is in JSON format and includes Connections and Messages."
            else "No follows or messages found. Make sure the export is in JSON format and includes Followers and following and Messages."
        }
        return ParseOutcome(candidates, notes.distinct())
    }
}
