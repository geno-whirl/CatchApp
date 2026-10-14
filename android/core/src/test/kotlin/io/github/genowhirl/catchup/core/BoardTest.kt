package io.github.genowhirl.catchup.core

import java.time.LocalDate
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BoardTest {
    private val today = LocalDate.of(2026, 10, 11) // Sunday
    private val monday = today.plusDays(1)
    private fun person(id: String, fm: String = "", body: String = "") =
        parsePerson(id, "---\nname: ${id.replaceFirstChar { it.uppercase() }} Test\n$fm\n---\n$body").person!!

    private val maya = person("maya", "language: Spanish\npriority: high\ncadence: monthly\nlast_contacted: 2026-08-01")
    private val joe = person("joe", "cadence: weekly\nlast_contacted: 2026-10-06")
    private val ana = person("ana", "priority: low\nlast_contacted: 2026-01-01")
    private val leo = person("leo", "cadence: monthly\nlast_contacted: 2026-10-01\nplans:\n  - when: 2026-10-14 19:00")
    private val zed = person("zed")
    private val people = listOf(maya, joe, ana, leo, zed)

    private val tue = Slot(today.plusDays(2), DayPart.EVENING)
    private val thu = Slot(today.plusDays(4), DayPart.LUNCH)

    @Test fun `several people can share a time and moving someone takes them off the old one`() {
        var b = WeekBoard(listOf(tue, thu))
        b = b.place(maya, tue).place(joe, tue)
        assertEquals(listOf("maya", "joe"), b.at(tue).map { it.person.id })
        assertEquals(KIND_MEET, b.at(tue)[0].kind) // high priority, never logged → meet

        b = b.setKind("maya", KIND_CALL).place(maya, thu)
        assertEquals(listOf("joe"), b.at(tue).map { it.person.id })
        assertEquals(KIND_CALL, b.at(thu)[0].kind) // the kind travels with them
        assertEquals(thu, b.slotOf("maya"))

        b = b.remove("joe")
        assertEquals(emptyList(), b.at(tue))
        assertEquals(1, b.count)
        assertEquals(listOf(thu.at to "maya"), b.plans().map { it.second.at to it.first.id })
    }

    @Test fun `times can be changed, added and removed`() {
        var b = WeekBoard(listOf(tue, thu)).place(maya, tue)
        b = b.retime(tue, LocalTime.of(20, 30))
        val moved = Slot(tue.date, DayPart.EVENING, LocalTime.of(20, 30))
        assertEquals(listOf(moved, thu), b.slots)
        assertEquals("2026-10-13 20:30", b.plans().single().second.at.format())

        // Moving a time onto another free time that day merges them.
        val tueLunch = Slot(tue.date, DayPart.LUNCH)
        b = b.place(joe, tueLunch).retime(tueLunch, LocalTime.of(20, 30))
        assertEquals(listOf("maya", "joe"), b.at(moved).map { it.person.id })
        assertEquals(listOf(moved, thu), b.slots)
        val sat = Slot(today.plusDays(6), DayPart.MORNING)
        b = b.addSlot(sat).place(ana, sat)
        assertEquals(3, b.slots.size)
        b = b.removeSlot(sat)
        assertNull(b.slotOf("ana"))
    }

    @Test fun `auto-fill tops the board up to the goal without moving anyone`() {
        val start = WeekBoard(listOf(tue, thu)).place(zed, tue)
        val filled = start.autoFill(people, today, monday, 2)
        assertEquals(2, filled.count)
        assertEquals(listOf("zed"), filled.at(tue).map { it.person.id })
        assertEquals(1, filled.at(thu).size)
        assertEquals(filled, filled.autoFill(people, today, monday, 2))
    }

    @Test fun `the bench can be ordered several ways, already planned last`() {
        fun ids(o: PeopleOrder) = bench(people, today, monday, o).map { it.person.id }
        assertEquals("leo", ids(PeopleOrder.SUGGESTED).last())
        assertEquals(listOf("maya", "joe", "ana", "zed", "leo"), ids(PeopleOrder.DUE))
        assertEquals(listOf("maya", "joe", "zed", "ana", "leo"), ids(PeopleOrder.PRIORITY))
        assertEquals(listOf("joe", "maya", "ana", "zed", "leo"), ids(PeopleOrder.CADENCE))
        assertEquals("2026-10-14 19:00", bench(people, today, monday, PeopleOrder.DUE).last().planned?.at?.format())
    }

    @Test fun `free moments skip the past and times already planned`() {
        val usual = setOf(
            java.time.DayOfWeek.SUNDAY to DayPart.LUNCH, java.time.DayOfWeek.SUNDAY to DayPart.EVENING,
            java.time.DayOfWeek.WEDNESDAY to DayPart.EVENING, java.time.DayOfWeek.FRIDAY to DayPart.AFTERNOON,
        )
        val free = freeMoments(people, usual, today.atTime(15, 0))
        // Sunday lunch has passed; Leo's dinner fills Wednesday evening.
        assertEquals(listOf("2026-10-11 19:00", "2026-10-16 15:00"), free.map { it.at.format() })
    }
}
