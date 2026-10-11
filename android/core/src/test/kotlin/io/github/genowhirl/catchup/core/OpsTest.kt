package io.github.genowhirl.catchup.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpsTest {
    private val path = "people/maya-patel.md"
    private val maya = """
        ---
        # Maya's file
        name: Maya Patel
        tags: [college]
        cadence: monthly
        next_contact: 2026-10-20
        plans:
          - when: 2026-10-20 18:30
            what: Dinner
        ---

        Loves matcha.

        ## Log

        ### 2026-08-30 · Video call
        - Leela started preschool.

        ### 2026-06-14 · Coffee
        - [ ] Ask about Portland
        - [x] Send photos

        ## Gift ideas
        - Chalk bag
    """.trimIndent() + "\n"
    private val files = mapOf(path to maya)
    private fun run(op: Op) = applyOp(files, op).first.getValue(path)

    @Test fun `logging inserts newest first and inside the Log section`() {
        val out = run(Op.Log(path, InteractionInput("2026-10-11", "19:00", "Call", null, listOf("New job!"), listOf("Ask about team"))))
        assertTrue(out.contains("## Log\n\n### 2026-10-11 19:00 · Call\n- New job!\n- [ ] Ask about team\n\n### 2026-08-30 · Video call"), out)
        val p = parsePerson("maya-patel", out).person!!
        assertEquals("Call", p.interactions[0].kind)
        assertEquals(listOf("Ask about team", "Ask about Portland"), p.openFollowUps)

        val middle = run(Op.Log(path, InteractionInput("2026-07-01", kind = "Text")))
        assertTrue(middle.indexOf("### 2026-07-01") in middle.indexOf("### 2026-08-30")..middle.indexOf("### 2026-06-14"))
        val oldest = run(Op.Log(path, InteractionInput("2025-01-01", kind = "Text", place = "Lisbon")))
        assertTrue(oldest.contains("- [x] Send photos\n\n### 2025-01-01 · Text · Lisbon\n\n## Gift ideas"), oldest)
    }

    @Test fun `logging creates the section, fulfils the plan and clears a satisfied next contact`() {
        val out = applyOp(mapOf("people/x.md" to "---\nname: X\n---\n\nNotes.\n"), Op.Log("people/x.md", InteractionInput("2026-10-11", kind = "Coffee", notes = listOf("Hi")))).first["people/x.md"]
        assertEquals("---\nname: X\n---\n\nNotes.\n\n## Log\n\n### 2026-10-11 · Coffee\n- Hi\n", out)

        val logged = run(Op.Log(path, InteractionInput("2026-10-20", "21:00", "Dinner"), planKey = "2026-10-20 18:30"))
        val p = parsePerson("maya-patel", logged).person!!
        assertEquals(emptyList(), p.plans)
        assertEquals(null, p.nextContact)
        assertFalse(logged.contains("plans:"))
    }

    @Test fun `a far-off next contact the user chose is kept`() {
        val f = mapOf(path to "---\nname: M\ncadence: monthly\nnext_contact: 2027-06-01\n---\n")
        val out = applyOp(f, Op.Log(path, InteractionInput("2026-10-11", kind = "Call"))).first.getValue(path)
        assertTrue(out.contains("next_contact: 2027-06-01"))
    }

    @Test fun `body-only edits keep the frontmatter exactly as written`() {
        val out = run(Op.SetFollowUp(path, "Ask about Portland", true))
        assertTrue(out.startsWith("---\n# Maya's file\nname: Maya Patel\ntags: [college]\n"))
        assertTrue(out.contains("- [x] Ask about Portland"))
        val reopened = applyOp(mapOf(path to out), Op.SetFollowUp(path, "Send photos", false)).first.getValue(path)
        assertTrue(reopened.contains("- [ ] Send photos"))
    }

    @Test fun `field edits write a tidy canonical frontmatter`() {
        val out = run(Op.SetFields(path, mapOf("priority" to JsonPrimitive("high"), "next_contact" to JsonNull, "location" to JsonPrimitive("Seattle, USA"))))
        assertEquals(
            "---\nname: Maya Patel\npriority: high\ntags: [college]\nlocation: Seattle, USA\ncadence: monthly\nplans:\n  - when: 2026-10-20 18:30\n    what: Dinner\n---\n",
            out.substringBefore("\nLoves") ,
        )
        assertTrue(out.endsWith("- Chalk bag\n"))
    }

    @Test fun `plans can be added, moved and removed`() {
        val added = run(Op.SavePlan(path, PlanInput("2026-10-15", "Call", remind = "30m")))
        var p = parsePerson("m", added).person!!
        assertEquals(listOf("2026-10-15", "2026-10-20 18:30"), p.plans.map { it.key })
        assertEquals(30, p.plans[0].remindMinutes)

        val moved = applyOp(mapOf(path to added), Op.SavePlan(path, PlanInput("2026-10-22 19:00", "Dinner", "Luigi's"), replaceKey = "2026-10-20 18:30")).first.getValue(path)
        p = parsePerson("m", moved).person!!
        assertEquals(listOf("2026-10-15", "2026-10-22 19:00"), p.plans.map { it.key })
        assertEquals("Luigi's", p.plans[1].place)

        val removed = applyOp(mapOf(path to moved), Op.RemovePlan(path, "2026-10-15")).first.getValue(path)
        assertEquals(listOf("2026-10-22 19:00"), parsePerson("m", removed).person!!.plans.map { it.key })
    }

    @Test fun `old next_catchup is migrated on first edit`() {
        val f = mapOf(path to "---\nname: M\nnext_catchup: 2026-10-14\n---\n")
        val out = applyOp(f, Op.SetFields(path, mapOf("cadence" to JsonPrimitive("monthly")))).first.getValue(path)
        assertEquals("---\nname: M\ncadence: monthly\nplans:\n  - when: 2026-10-14\n---\n", out)
    }

    @Test fun `imports only add`() {
        val out = run(
            Op.Patch(
                path,
                PersonPatch(
                    fill = mapOf("name" to JsonPrimitive("Someone"), "birthday" to JsonPrimitive("1991-10-19")),
                    contact = mapOf("phone" to "+1 206 555 0142", "email" to "maya@example.com"),
                    tags = listOf("college", "instagram"),
                    lastContacted = "2026-09-01",
                ),
            ),
        )
        val p = parsePerson("m", out).person!!
        assertEquals("Maya Patel", p.name)
        assertEquals(Birthday(10, 19, 1991), p.birthday)
        assertEquals(listOf("college", "instagram"), p.tags)
        assertTrue(out.contains("  phone: \"+1 206 555 0142\""))
        val again = applyOp(mapOf(path to out), Op.Patch(path, PersonPatch(contact = mapOf("phone" to "206-555-0142"), lastContacted = "2026-01-01"))).first.getValue(path)
        assertEquals(out, again)
    }

    @Test fun `create never overwrites and edits to deleted files are skipped`() {
        val (out, created) = applyOp(files, Op.Create(path, "---\nname: Maya P\n---\n"))
        assertEquals("people/maya-patel-2.md", created)
        assertEquals(maya, out[path])
        assertEquals(files, applyOp(files, Op.Create(path, "x", skipIfExists = true)).first)
        assertEquals(emptyMap(), applyOps(files, listOf(Op.Delete(path), Op.SetFollowUp(path, "Send photos", false))))
    }

    @Test fun `unreadable files are never rewritten`() {
        val broken = mapOf(path to "---\nname: [oops\n---\n")
        assertEquals(broken, applyOp(broken, Op.SetFields(path, mapOf("a" to JsonPrimitive("b")))).first)
    }

    @Test fun `new files are valid and round-trip`() {
        val text = newPersonFile(
            linkedMapOf(
                "name" to "Zoë O'Brien",
                "priority" to "high",
                "tags" to listOf("work", "running"),
                "birthday" to "03-04",
                "contact" to mapOf("phone" to "+15551234567", "email" to ""),
                "family" to listOf(mapOf("name" to "Sam", "relation" to "son", "birthday" to "2015-05-02")),
                "how_we_met" to "At the marathon: mile 20",
            ),
            notes = "Met at the marathon.",
        )
        assertEquals(
            "---\nname: Zoë O'Brien\npriority: high\ntags: [work, running]\nbirthday: 03-04\ncontact:\n  phone: \"+15551234567\"\nfamily:\n  - name: Sam\n    relation: son\n    birthday: 2015-05-02\nhow_we_met: \"At the marathon: mile 20\"\n---\n\nMet at the marathon.\n",
            text,
        )
        val (p, issues) = parsePerson("zoe", text)
        assertEquals(emptyList(), issues)
        assertEquals(LocalDate.of(2015, 5, 2), p!!.family[0].birthday!!.inYear(2015))
        assertEquals("zoe-o-brien", slugify("Zoë O'Brien"))
    }

    @Test fun `ops survive a JSON round trip (the offline queue)`() {
        val ops: List<Op> = listOf(
            Op.Log(path, InteractionInput("2026-10-11", kind = "Call"), "k"),
            Op.SetFields(path, mapOf("tags" to listOf("a").toJson(), "x" to JsonNull)),
            Op.SavePlan(path, PlanInput("2026-10-15")),
        )
        val json = Json.encodeToString(kotlinx.serialization.builtins.ListSerializer(Op.serializer()), ops)
        assertEquals(ops, Json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Op.serializer()), json))
    }
}
