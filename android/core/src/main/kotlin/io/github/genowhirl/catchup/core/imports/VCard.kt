package io.github.genowhirl.catchup.core.imports

/** Reads .vcf files exported from iPhone Contacts, Google Contacts, Android and Outlook. */
object VCard {
    private class Property(val group: String?, val name: String, val params: Map<String, List<String>>, var value: String)

    private val HEX = Regex("[0-9A-Fa-f]{2}")

    private fun decodeQuotedPrintable(value: String, charset: String?): String {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            if (c == '=' && i + 2 < value.length && value.substring(i + 1, i + 3).matches(HEX)) {
                out.write(value.substring(i + 1, i + 3).toInt(16)); i += 3
            } else {
                out.write(c.toString().toByteArray()); i++
            }
        }
        return runCatching { String(out.toByteArray(), charset(charset ?: "UTF-8")) }.getOrElse { String(out.toByteArray()) }
    }

    private fun unescape(v: String) = v.replace(Regex("""\\([\\,;nN])""")) { if (it.groupValues[1] in listOf("n", "N")) "\n" else it.groupValues[1] }

    /** Splits on separators that aren't backslash-escaped. */
    private fun splitUnescaped(value: String, sep: Char): List<String> {
        val parts = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                c == '\\' && i + 1 < value.length -> { cur.append(c).append(value[i + 1]); i += 2; continue }
                c == sep -> { parts += cur.toString(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        parts += cur.toString()
        return parts.map(::unescape)
    }

    private fun parseLine(line: String): Property? {
        var colon = -1
        var quoted = false
        for ((i, c) in line.withIndex()) {
            if (c == '"') quoted = !quoted else if (c == ':' && !quoted) { colon = i; break }
        }
        if (colon == -1) return null
        val parts = line.substring(0, colon).split(";")
        val head = parts[0]
        val dot = head.lastIndexOf('.')
        val params = mutableMapOf<String, List<String>>()
        for (part in parts.drop(1)) {
            val eq = part.indexOf('=')
            // vCard 2.1 allows bare types like `TEL;CELL:`.
            val key = (if (eq == -1) "TYPE" else part.substring(0, eq)).uppercase()
            val values = (if (eq == -1) part else part.substring(eq + 1)).replace("\"", "").split(",").map { it.lowercase() }
            params[key] = (params[key] ?: emptyList()) + values
        }
        return Property(if (dot == -1) null else head.substring(0, dot).lowercase(), head.substring(dot + 1).uppercase(), params, line.substring(colon + 1))
    }

    private fun cards(text: String): List<List<Property>> {
        val lines = text.replace(Regex("\r\n?"), "\n").split("\n")
        val result = mutableListOf<List<Property>>()
        var props: MutableList<Property>? = null
        var i = 0
        while (i < lines.size) {
            var line = lines[i]
            // Folded continuation lines start with a space or tab.
            while (i + 1 < lines.size && (lines[i + 1].startsWith(" ") || lines[i + 1].startsWith("\t"))) line += lines[++i].substring(1)
            // Quoted-printable soft line breaks end with "=".
            if (line.substringBefore(':').contains("ENCODING=QUOTED-PRINTABLE", ignoreCase = true)) {
                while (line.endsWith("=") && i + 1 < lines.size) line = line.dropLast(1) + lines[++i]
            }
            i++
            val prop = parseLine(line) ?: continue
            when {
                prop.name == "BEGIN" && prop.value.equals("VCARD", true) -> props = mutableListOf()
                prop.name == "END" && prop.value.equals("VCARD", true) -> { props?.let { result += it }; props = null }
                props != null -> {
                    if (prop.params["ENCODING"]?.contains("quoted-printable") == true) {
                        prop.value = decodeQuotedPrintable(prop.value, prop.params["CHARSET"]?.firstOrNull())
                    }
                    props.add(prop)
                }
            }
        }
        return result
    }

    /** Accepts `1991-10-19`, `19911019`, `--10-19`, `--1019`; iOS uses year 1604 for "no year". */
    fun parseDate(value: String, omitYear: Boolean = false): String? {
        val v = value.trim().replace(Regex("T.*$"), "")
        Regex("^(\\d{4})-?(\\d{2})-?(\\d{2})$").matchEntire(v)?.let { m ->
            val (y, mo, d) = m.destructured
            return if (omitYear || y == "1604") "$mo-$d" else "$y-$mo-$d"
        }
        Regex("^--(\\d{2})-?(\\d{2})$").matchEntire(v)?.let { m -> return "${m.groupValues[1]}-${m.groupValues[2]}" }
        return null
    }

    /** iOS labels look like `_$!<Spouse>!$_`. */
    private fun cleanLabel(label: String) = label.trim().removePrefix("_\$!<").removeSuffix(">!\$_").trim().lowercase()

    private val IGNORED_CATEGORIES = setOf("mycontacts", "starred", "imported", "card")

    internal fun addContact(contact: MutableMap<String, String>, key: String, value: String) {
        if (value.isBlank() || value in contact.values) return
        var k = key
        var n = 2
        while (k in contact) k = "${key}_${n++}"
        contact[k] = value
    }

    private fun toCandidate(props: List<Property>): Candidate? {
        fun get(name: String) = props.firstOrNull { it.name == name }
        val labels = props.filter { it.name == "X-ABLABEL" && it.group != null }.associate { it.group!! to cleanLabel(unescape(it.value)) }
        val fn = get("FN")?.let { unescape(it.value).trim() }.orEmpty()
        val n = get("N")?.let { splitUnescaped(it.value, ';') } ?: emptyList()
        val name = fn.ifEmpty { listOf(3, 1, 2, 0, 4).mapNotNull { n.getOrNull(it)?.trim()?.ifEmpty { null } }.joinToString(" ") }
        if (name.isEmpty()) return null

        val c = Candidate(name, ImportSource.CONTACTS)
        get("NICKNAME")?.let { c.nickname = unescape(it.value).split(",")[0].trim().ifEmpty { null } }
        get("BDAY")?.let { c.birthday = parseDate(it.value, "X-APPLE-OMIT-YEAR" in it.params) }

        val notes = mutableListOf<String>()
        for (p in props) {
            val types = p.params["TYPE"] ?: emptyList()
            val label = p.group?.let { labels[it] }
            when (p.name) {
                "TEL" -> {
                    val value = unescape(p.value).replace(Regex("^tel:", RegexOption.IGNORE_CASE), "").trim()
                    val kind = label ?: types.firstOrNull { it == "home" || it == "work" }
                    addContact(c.contact, if (kind == "home" || kind == "work") "phone_$kind" else "phone", value)
                }
                "EMAIL" -> {
                    val kind = label ?: types.firstOrNull { it == "work" }
                    addContact(c.contact, if (kind == "work") "email_work" else "email", unescape(p.value).trim())
                }
                "ADR" -> if (c.location == null) {
                    val f = splitUnescaped(p.value, ';').map { it.trim() }
                    val place = listOfNotNull(f.getOrNull(3)?.ifEmpty { null }, (f.getOrNull(6)?.ifEmpty { null } ?: f.getOrNull(4)?.ifEmpty { null })).joinToString(", ")
                    if (place.isNotEmpty()) c.location = place
                }
                "X-SOCIALPROFILE" -> {
                    val service = types.firstOrNull { it != "pref" } ?: "social"
                    val handle = p.params["X-USER"]?.firstOrNull()
                    val value = unescape(p.value).replace(Regex("^x-apple:", RegexOption.IGNORE_CASE), "").trim()
                    addContact(c.contact, service, if (Regex("^https?://").containsMatchIn(value)) value else handle ?: value)
                }
                "URL" -> addContact(c.contact, "website", unescape(p.value).trim())
                "NOTE" -> notes += unescape(p.value).trim()
                "ORG" -> {
                    val org = splitUnescaped(p.value, ';').filter { it.isNotBlank() }.joinToString(", ")
                    if (org.isNotEmpty()) notes.add(0, "Works at ${org.trimEnd('.')}.")
                }
                "CATEGORIES" -> splitUnescaped(p.value, ',').forEach { cat ->
                    val tag = cat.trim().lowercase().replace(Regex("\\s+"), "-")
                    if (tag.isNotEmpty() && tag !in IGNORED_CATEGORIES && tag !in c.tags) c.tags += tag
                }
                "X-ABRELATEDNAMES", "RELATED" -> {
                    val rel = unescape(p.value).replace(Regex("^text:", RegexOption.IGNORE_CASE), "").trim()
                    if (rel.isNotEmpty() && !Regex("^(urn|https?):", RegexOption.IGNORE_CASE).containsMatchIn(rel)) c.family += rel to (label ?: types.firstOrNull())
                }
            }
        }
        notes.filter { it.isNotBlank() }.joinToString("\n\n").takeIf { it.isNotEmpty() }?.let { c.notes = it }
        return c
    }

    fun parse(text: String): ParseOutcome {
        val candidates = mutableListOf<Candidate>()
        var skipped = 0
        for (props in cards(text)) toCandidate(props)?.let { candidates += it } ?: skipped++
        return ParseOutcome(candidates, if (skipped > 0) listOf("Skipped $skipped contact${if (skipped > 1) "s" else ""} without a name.") else emptyList())
    }
}
