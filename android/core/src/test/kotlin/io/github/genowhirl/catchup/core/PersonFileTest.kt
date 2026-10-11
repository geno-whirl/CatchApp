package io.github.genowhirl.catchup.core

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonFileTest {
    private val maya = """
        ---
        name: Maya Patel
        nickname: May
        relationship: friend
        priority: high
        tags: [college, climbing]
        birthday: 1991-10-19
        contact:
          phone: "+1 206 555 0142"
        family:
          - name: Arjun
            relation: partner
          - Leela
        cadence: monthly
        next_contact: 2026-11-01
        last_contacted: 2026-08-01
        plans:
          - when: 2026-10-20 18:30
            what: Dinner
            where: Luigi's
            remind: 2h
        ---

        Loves matcha.

        - [ ] Send the photos

        ## Log

        ### 2026-06-14 · Coffee
        - old

        ### 2026-08-30 18:00 · Video call · from the train
        - newest
        - [ ] Ask about the new team
        - [x] Congratulate her
    """.trimIndent() + "\n"

    @Test fun `parses every field`() {
        val (person, issues) = parsePerson("maya-patel", maya)
        assertEquals(emptyList(), issues)
        person!!
        assertEquals("Maya Patel", person.name)
        assertEquals(Priority.HIGH, person.priority)
        assertEquals(listOf("college", "climbing"), person.tags)
        assertEquals(Birthday(10, 19, 1991), person.birthday)
        assertEquals(mapOf("phone" to "+1 206 555 0142"), person.contact)
        assertEquals(listOf(FamilyMember("Arjun", "partner"), FamilyMember("Leela")), person.family)
        assertEquals(30, person.cadence!!.days)
        assertEquals(LocalDate.of(2026, 11, 1), person.nextContact)
        assertEquals(LocalDate.of(2026, 11, 1), person.dueDate(LocalDate.of(2026, 10, 11)))
        assertEquals(listOf(Plan(When(LocalDate.of(2026, 10, 20), LocalTime.of(18, 30)), "Dinner", "Luigi's", 120)), person.plans)
        assertEquals("Loves matcha.\n\n- [ ] Send the photos", person.notes)
    }

    @Test fun `reads interactions newest first with time, kind, place and follow-ups`() {
        val person = parsePerson("maya-patel", maya).person!!
        val first = person.interactions[0]
        assertEquals(When(LocalDate.of(2026, 8, 30), LocalTime.of(18, 0)), first.at)
        assertEquals("Video call", first.kind)
        assertEquals("from the train", first.place)
        assertEquals(listOf("newest"), first.notes)
        assertEquals(listOf(FollowUp("Ask about the new team", false), FollowUp("Congratulate her", true)), first.followUps)
        assertEquals("Coffee", person.interactions[1].kind)
        assertNull(person.interactions[1].place)
        // The log date is newer than last_contacted, so it wins.
        assertEquals(LocalDate.of(2026, 8, 30), person.lastContacted)
        assertEquals(listOf("Send the photos", "Ask about the new team"), person.openFollowUps)
    }

    @Test fun `due date falls back to last contact plus cadence, or today if never contacted`() {
        val today = LocalDate.of(2026, 10, 11)
        val a = parsePerson("a", "---\nname: A\ncadence: 2w\nlast_contacted: 2026-10-01\n---\n").person!!
        assertEquals(LocalDate.of(2026, 10, 15), a.dueDate(today))
        val b = parsePerson("b", "---\nname: B\ncadence: weekly\n---\n").person!!
        assertEquals(today, b.dueDate(today))
        val c = parsePerson("c", "---\nname: C\n---\n").person!!
        assertNull(c.dueDate(today))
    }

    @Test fun `old next_catchup becomes a plan`() {
        val p = parsePerson("x", "---\nname: X\nnext_catchup: 2026-10-14 18:30\n---\n").person!!
        assertEquals(listOf(Plan(When(LocalDate.of(2026, 10, 14), LocalTime.of(18, 30)))), p.plans)
    }

    @Test fun `reports problems without crashing`() {
        assertEquals(Issue.Level.ERROR, parsePerson("x", "# no frontmatter").issues.single().level)
        assertNull(parsePerson("x", "---\nnickname: X\n---\n").person)
        assertTrue(parsePerson("x", "---\nname: [oops\n---\n").issues.single().message.contains("YAML"))
        val (p, issues) = parsePerson(
            "x",
            "---\nname: X\nbirthday: 19/10/1991\npriority: urgent\ncadence: often\nfavourite: blue\ncontact:\n  phone: 0123\nplans:\n  - when: soon\n---\n",
        )
        assertNotNull(p)
        assertEquals(
            listOf(
                "Unknown field `favourite` (kept, but not used).",
                "`priority` \"urgent\" should be high, medium or low.",
                "`birthday` \"19/10/1991\" must be YYYY-MM-DD or MM-DD.",
                "`contact.phone` was read as a number; wrap it in quotes to keep a leading + or 0.",
                "`cadence` \"often\" is not recognised (try monthly, quarterly, or 6w).",
                "`plans[0].when` must be YYYY-MM-DD or YYYY-MM-DD HH:MM.",
            ),
            issues.map { it.message },
        )
    }

    @Test fun `birthdays and cadences`() {
        val today = LocalDate.of(2026, 10, 11)
        assertEquals(0, Birthday(10, 11).daysUntil(today))
        assertEquals(364, Birthday(10, 10).daysUntil(today))
        assertEquals(35, Birthday(10, 19, 1991).upcomingAge(today))
        assertEquals(1, Birthday(2, 29).daysUntil(LocalDate.of(2027, 2, 27)))
        assertEquals(Birthday(2, 29), Birthday.parse("--02-29"))
        assertNull(Birthday.parse("02-30"))
        assertEquals(42, Cadence.parse("6w")!!.days)
        assertNull(Cadence.parse("0d"))
        assertEquals(120, parseRemind("2h"))
        assertEquals("1d", formatRemind(1440))
    }

    @Test fun `the example people all parse cleanly`() {
        val dir = File("../../examples/people")
        val files = dir.listFiles { f -> f.name.endsWith(".md") }!!
        assertTrue(files.size >= 5)
        for (f in files) {
            val (person, issues) = parsePerson(f.nameWithoutExtension, f.readText())
            assertNotNull(person, f.name)
            assertEquals(emptyList(), issues.filter { it.level == Issue.Level.ERROR }, f.name)
        }
    }
}
