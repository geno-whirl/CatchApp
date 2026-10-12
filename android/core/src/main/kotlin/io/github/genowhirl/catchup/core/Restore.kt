package io.github.genowhirl.catchup.core

import java.util.zip.ZipInputStream

/** People found in a backup, compared with what's here now. Paths are `people/<id>.md`. */
data class RestorePlan(
    val new: Map<String, String>,
    val changed: Map<String, String>,
    val unchanged: Int,
    /** Files that looked like people but couldn't be read. */
    val unreadable: List<String>,
) {
    val total: Int get() = new.size + changed.size + unchanged

    /** Adds the people who aren't here; with `replaceChanged`, also puts back the backup's version of the others. */
    fun ops(replaceChanged: Boolean): List<Op> =
        new.map { (path, text) -> Op.Create(path, text, skipIfExists = true) } +
            if (replaceChanged) changed.map { (path, text) -> Op.Replace(path, text) } else emptyList()
}

/**
 * Reads a CatchUp backup: the app's .zip export, a zip of the data repository (as GitHub
 * downloads it), or loose `.md` files. Returns person files keyed by `people/<id>.md`.
 */
fun readBackup(files: List<Pair<String, ByteArray>>): Map<String, String> {
    val out = linkedMapOf<String, String>()
    fun add(name: String, text: String) {
        val path = if ("people/" in name) "people/" + name.substringAfterLast("people/") else "people/" + name.substringAfterLast('/')
        if (isPersonFile(path)) out[path] = text
    }
    for ((name, bytes) in files) {
        if (name.endsWith(".zip", true) || bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()) {
            ZipInputStream(bytes.inputStream()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name.endsWith(".md", true) && "__MACOSX" !in entry.name) {
                        add(entry.name, zip.readBytes().toString(Charsets.UTF_8))
                    }
                }
            }
        } else if (name.endsWith(".md", true)) {
            add(name, bytes.toString(Charsets.UTF_8))
        }
    }
    return out
}

fun planRestore(backup: Map<String, String>, current: Map<String, String>): RestorePlan {
    val new = linkedMapOf<String, String>()
    val changed = linkedMapOf<String, String>()
    val unreadable = mutableListOf<String>()
    var unchanged = 0
    for ((path, text) in backup) {
        if (parsePerson(idFromPath(path), text).person == null) {
            unreadable += path
            continue
        }
        val here = current[path]
        when {
            here == null -> new[path] = text
            here.trim() == text.trim() -> unchanged++
            else -> changed[path] = text
        }
    }
    return RestorePlan(new, changed, unchanged, unreadable)
}
