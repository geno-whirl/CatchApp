package io.github.genowhirl.catchup.core.imports

import java.text.Normalizer
import java.util.zip.ZipInputStream

enum class ImportSource(val label: String) { CONTACTS("Contacts"), WHATSAPP("WhatsApp"), FACEBOOK("Facebook"), INSTAGRAM("Instagram") }

/** Someone found in an import, before matching against existing people. */
data class Candidate(
    val name: String,
    val source: ImportSource,
    var nickname: String? = null,
    /** YYYY-MM-DD or MM-DD */
    var birthday: String? = null,
    var location: String? = null,
    val contact: MutableMap<String, String> = linkedMapOf(),
    val family: MutableList<Pair<String, String?>> = mutableListOf(),
    var howWeMet: String? = null,
    /** YYYY-MM-DD of the most recent message. */
    var lastContacted: String? = null,
    var notes: String? = null,
    val tags: MutableList<String> = mutableListOf(),
    /** Short context for the preview, e.g. "312 WhatsApp messages". */
    var detail: String? = null,
)

data class ImportFile(val name: String, val text: String)

data class ParseOutcome(val candidates: List<Candidate>, val notes: List<String>)

fun normalizeName(name: String): String =
    Normalizer.normalize(name, Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "")
        .lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

private val RELEVANT = listOf(
    Regex("\\.vcf$", RegexOption.IGNORE_CASE),
    Regex("\\.txt$", RegexOption.IGNORE_CASE),
    Regex("(^|/)(your_)?friends(_\\d+)?\\.json$", RegexOption.IGNORE_CASE),
    Regex("(^|/)following(_\\d+)?\\.json$", RegexOption.IGNORE_CASE),
    Regex("(^|/)followers(_\\d+)?\\.json$", RegexOption.IGNORE_CASE),
    Regex("(^|/)message_\\d+\\.json$", RegexOption.IGNORE_CASE),
    Regex("(^|/)profile_information\\.json$", RegexOption.IGNORE_CASE),
    Regex("(^|/)personal_information\\.json$", RegexOption.IGNORE_CASE),
)

fun isRelevant(name: String): Boolean =
    !Regex("(^|/)(__MACOSX|\\.)").containsMatchIn(name) && RELEVANT.any { it.containsMatchIn(name) }

/**
 * Turns picked files (zips included) into text files the parsers understand. Zip entries are
 * filtered by name, so photos and videos in exports are skipped without being decompressed.
 */
fun readImportFile(name: String, bytes: ByteArray): List<ImportFile> {
    if (!name.endsWith(".zip", ignoreCase = true)) return listOf(ImportFile(name, bytes.toString(Charsets.UTF_8)))
    val out = mutableListOf<ImportFile>()
    ZipInputStream(bytes.inputStream()).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && isRelevant(entry.name)) out += ImportFile("$name/${entry.name}", zip.readBytes().toString(Charsets.UTF_8))
        }
    }
    return out
}
