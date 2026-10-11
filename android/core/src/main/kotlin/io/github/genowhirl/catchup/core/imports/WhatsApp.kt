package io.github.genowhirl.catchup.core.imports

import java.time.LocalDateTime

/**
 * Reads WhatsApp "Export chat" files (Without media). Only the date of the last message and
 * the message count are kept — message text never leaves this parser.
 */
object WhatsApp {
    private val LINE = Regex(
        """^\[?(\d{1,4})[./-](\d{1,2})[./-](\d{1,4}),?\s+(\d{1,2})[:.](\d{2})(?:[:.]\d{2})?(?:\s*([AaPp])\.?\s?[Mm]\.?)?]?\s*(?:[-–]\s+)?(.*)$""",
    )
    private val INVISIBLE = Regex("[‎‏‪-‮⁦-⁩]")
    private val TITLE = Regex("""whats\s*app[\s-]*(?:chat)?\s*(?:with|con|mit|com|avec|met|-|–|:)\s*(.+)$""", RegexOption.IGNORE_CASE)

    private class Chat(val title: String?) {
        val participants = linkedMapOf<String, Pair<String, Int>>()
        var messages = 0
        var last: LocalDateTime? = null
    }

    /** "WhatsApp Chat with Maya Patel.txt", "WhatsApp Chat - Maya.zip/_chat.txt", "WhatsApp-Chat mit Jonas.txt"… */
    fun chatTitle(fileName: String): String? {
        val parts = fileName.split("/")
        val base = if (parts.last() == "_chat.txt" && parts.size > 1) parts[parts.size - 2] else parts.last()
        val stem = base.replace(Regex("\\.(txt|zip)$", RegexOption.IGNORE_CASE), "").trim()
        return TITLE.find(stem)?.groupValues?.get(1)?.trim()
    }

    private data class Row(val a: Int, val b: Int, val c: String, val h: Int, val min: Int, val ampm: Char?, val sender: String)

    private fun parseChat(text: String, title: String?): Chat {
        val rows = text.lines().mapNotNull { raw ->
            val line = raw.replace(INVISIBLE, "").replace(' ', ' ').replace(' ', ' ')
            val m = LINE.matchEntire(line) ?: return@mapNotNull null
            val rest = m.groupValues[7]
            val colon = rest.indexOf(": ")
            if (colon <= 0) return@mapNotNull null // System message ("Messages are end-to-end encrypted").
            Row(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3], m.groupValues[4].toInt(), m.groupValues[5].toInt(),
                m.groupValues[6].lowercase().firstOrNull(), rest.substring(0, colon).trim())
        }
        // Date order: Y-M-D, D/M/Y (most of the world) or M/D/Y (US, with AM/PM).
        val yearFirst = rows.any { it.a > 31 }
        val dayFirst = rows.any { it.a > 12 } || (rows.none { it.b > 12 } && rows.none { it.ampm != null })
        val chat = Chat(title)
        for (r in rows) {
            val (year, month, day) = if (yearFirst) Triple(r.a, r.b, r.c.toInt()) else {
                val y = r.c.toInt().let { if (it < 100) 2000 + it else it }
                if (dayFirst) Triple(y, r.b, r.a) else Triple(y, r.a, r.b)
            }
            var hours = r.h % (if (r.ampm != null) 12 else 24)
            if (r.ampm == 'p') hours += 12
            val date = runCatching { LocalDateTime.of(year, month, day, hours, r.min) }.getOrNull() ?: continue
            val key = normalizeName(r.sender)
            val p = chat.participants[key]
            chat.participants[key] = (p?.first ?: r.sender) to ((p?.second ?: 0) + 1)
            chat.messages++
            if (chat.last == null || date.isAfter(chat.last)) chat.last = date
        }
        return chat
    }

    private val PHONE = Regex("""^\+?[\d\s()-]{7,}$""")

    fun parse(files: List<ImportFile>): ParseOutcome {
        val chats = files.filter { it.name.endsWith(".txt", true) }.map { parseChat(it.text, chatTitle(it.name)) }.filter { it.messages > 0 }
        val notes = mutableListOf<String>()
        // You appear in every chat, so with several chats we can tell which name is yours.
        val appearances = chats.flatMap { it.participants.keys }.groupingBy { it }.eachCount()
        val self = if (chats.size > 1) appearances.entries.firstOrNull { it.value == chats.size }?.key else null

        val candidates = mutableListOf<Candidate>()
        for (chat in chats) {
            val titleKey = chat.title?.let(::normalizeName)
            var others = chat.participants.entries.filter { it.key != self }
            val byTitle = others.firstOrNull { it.key == titleKey }
            if (byTitle != null) others = listOf(byTitle)
            else if (others.size > 2 || (self != null && others.size > 1)) {
                notes += "Skipped group chat${chat.title?.let { " \"$it\"" } ?: ""}."
                continue
            }
            for ((_, v) in others) {
                val c = Candidate(v.first, ImportSource.WHATSAPP, lastContacted = chat.last?.toLocalDate()?.toString(), detail = "${chat.messages} WhatsApp messages")
                if (PHONE.matches(v.first)) c.contact["whatsapp"] = v.first
                candidates += c
            }
            if (others.size > 1) notes += "Couldn't tell which name in \"${chat.title ?: "a chat"}\" is yours — untick it."
        }
        if (files.isNotEmpty() && chats.isEmpty()) notes += "No WhatsApp messages found in these files."
        return ParseOutcome(candidates, notes)
    }
}
