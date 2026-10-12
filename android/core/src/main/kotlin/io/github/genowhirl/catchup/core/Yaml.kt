package io.github.genowhirl.catchup.core

import org.snakeyaml.engine.v2.api.Load
import org.snakeyaml.engine.v2.api.LoadSettings
import org.snakeyaml.engine.v2.schema.CoreSchema

/** Reads frontmatter with YAML 1.2's core schema (so `2026-10-11` stays a string). */
internal fun loadYaml(text: String): Any? =
    Load(LoadSettings.builder().setSchema(CoreSchema()).setAllowDuplicateKeys(false).build()).loadFromString(text)

/**
 * Writes frontmatter in one consistent, readable style. Known fields come first in a fixed
 * order; anything else (fields added by hand or by agents) is kept, after them.
 */
internal object YamlWriter {
    val ORDER = listOf(
        "name", "nickname", "relationship", "priority", "tags", "birthday", "location", "language", "contact", "family",
        "how_we_met", "cadence", "next_contact", "last_contacted", "plans",
    )
    private val FLOW_LISTS = setOf("tags")
    private val NUMERIC = Regex("""^[-+]?(\d+|0o[0-7]+|0x[0-9a-fA-F]+|(\.\d+|\d+(\.\d*)?)([eE][-+]?\d+)?|\.(inf|Inf|INF)|\.(nan|NaN|NAN))$""")
    private val RESERVED = setOf("~", "null", "Null", "NULL", "true", "True", "TRUE", "false", "False", "FALSE")
    private const val INDICATORS = "-?:,[]{}#&*!|>'\"%@`"

    fun write(map: Map<String, Any?>): String {
        val keys = ORDER.filter { it in map } + map.keys.filter { it !in ORDER }
        val sb = StringBuilder()
        for (key in keys) {
            val value = map[key] ?: continue
            if (isEmptyValue(value)) continue
            writeEntry(sb, key, value, 0, forceQuote = false)
        }
        return sb.toString()
    }

    private fun isEmptyValue(v: Any?): Boolean = when (v) {
        null -> true
        is String -> v.isEmpty()
        is Collection<*> -> v.isEmpty()
        is Map<*, *> -> v.isEmpty()
        else -> false
    }

    private fun writeEntry(sb: StringBuilder, key: String, value: Any, indent: Int, forceQuote: Boolean) {
        val pad = " ".repeat(indent)
        val k = scalar(key, false)
        when (value) {
            is Map<*, *> -> {
                sb.append(pad).append(k).append(":\n")
                writeMap(sb, value, indent + 2, quoteValues = key == "contact")
            }
            is List<*> -> {
                if (key in FLOW_LISTS && value.all { it !is Map<*, *> && it !is List<*> }) {
                    sb.append(pad).append(k).append(": [")
                        .append(value.filterNotNull().joinToString(", ") { flowScalar(it.toString()) }).append("]\n")
                } else {
                    sb.append(pad).append(k).append(":\n")
                    writeList(sb, value, indent + 2)
                }
            }
            else -> sb.append(pad).append(k).append(": ").append(scalar(value, forceQuote)).append('\n')
        }
    }

    private fun writeMap(sb: StringBuilder, map: Map<*, *>, indent: Int, quoteValues: Boolean = false) {
        for ((k, v) in map) {
            if (v == null || isEmptyValue(v)) continue
            val force = quoteValues && v is String && v.firstOrNull()?.let { it == '+' || it.isDigit() } == true
            writeEntry(sb, k.toString(), v, indent, force)
        }
    }

    private fun writeList(sb: StringBuilder, list: List<*>, indent: Int) {
        val pad = " ".repeat(indent)
        for (item in list) {
            when (item) {
                null -> continue
                is Map<*, *> -> {
                    val entries = item.entries.filter { it.value != null && !isEmptyValue(it.value) }
                    if (entries.isEmpty()) continue
                    // "- name: Leela" then the rest aligned under it.
                    val inner = StringBuilder()
                    writeMap(inner, entries.associate { it.key to it.value }, indent + 2)
                    val text = inner.toString()
                    sb.append(pad).append("- ").append(text.substring(indent + 2))
                }
                is List<*> -> {
                    sb.append(pad).append("-\n")
                    writeList(sb, item, indent + 2)
                }
                else -> sb.append(pad).append("- ").append(scalar(item, false)).append('\n')
            }
        }
    }

    private fun flowScalar(s: String): String =
        if (s.any { it in ",[]{}" } || needsQuotes(s)) quote(s) else s

    fun scalar(value: Any, forceQuote: Boolean): String = when (value) {
        is Boolean, is Number -> value.toString()
        else -> {
            val s = value.toString()
            if (forceQuote || needsQuotes(s)) quote(s) else s
        }
    }

    fun needsQuotes(s: String): Boolean {
        if (s.isEmpty() || s != s.trim()) return true
        if (s in RESERVED || NUMERIC.matches(s)) return true
        if (s.first() in INDICATORS) return true
        if (s.contains(": ") || s.contains(" #") || s.endsWith(":")) return true
        return s.any { it == '\n' || it == '\t' || it == '\r' || it.code < 0x20 }
    }

    private fun quote(s: String): String = buildString {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
        append('"')
    }
}
