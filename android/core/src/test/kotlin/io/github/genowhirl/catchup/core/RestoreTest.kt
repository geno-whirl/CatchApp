package io.github.genowhirl.catchup.core

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class RestoreTest {
    private fun zip(vararg entries: Pair<String, String>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, text) ->
                z.putNextEntry(ZipEntry(name))
                z.write(text.toByteArray())
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private val maya = "---\nname: Maya Patel\n---\n"
    private val joe = "---\nname: Joe Moreno\n---\n"
    private val ana = "---\nname: Ana Lima\n---\n"

    @Test fun `reads app backups, repo downloads and loose files`() {
        val backup = readBackup(
            listOf(
                "catchup-backup.zip" to zip("people/maya-patel.md" to maya, "people/_template.md" to "---\nname: x\n---\n"),
                "catchup-data-main.zip" to zip("catchup-data-main/people/joe-moreno.md" to joe, "catchup-data-main/README.md" to "# hi"),
                "ana-lima.md" to ana.toByteArray(),
            ),
        )
        assertEquals(listOf("people/maya-patel.md", "people/joe-moreno.md", "people/ana-lima.md"), backup.keys.toList())
    }

    @Test fun `adds who's missing and only replaces when asked`() {
        val current = mapOf("people/maya-patel.md" to maya, "people/joe-moreno.md" to "---\nname: Joe Moreno\nnickname: Jo\n---\n")
        val plan = planRestore(
            mapOf("people/maya-patel.md" to maya, "people/joe-moreno.md" to joe, "people/ana-lima.md" to ana, "people/broken.md" to "no frontmatter"),
            current,
        )
        assertEquals(listOf("people/ana-lima.md"), plan.new.keys.toList())
        assertEquals(listOf("people/joe-moreno.md"), plan.changed.keys.toList())
        assertEquals(1, plan.unchanged)
        assertEquals(listOf("people/broken.md"), plan.unreadable)

        val added = applyOps(current, plan.ops(replaceChanged = false))
        assertEquals(current["people/joe-moreno.md"], added["people/joe-moreno.md"])
        assertEquals(ana, added["people/ana-lima.md"])
        assertEquals(joe, applyOps(current, plan.ops(replaceChanged = true))["people/joe-moreno.md"])
    }
}
