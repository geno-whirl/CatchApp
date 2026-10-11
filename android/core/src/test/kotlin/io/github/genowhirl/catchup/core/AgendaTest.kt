package io.github.genowhirl.catchup.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class AgendaTest {
    private val today = LocalDate.of(2026, 10, 11) // Sunday
    private fun person(id: String, fm: String, body: String = "") =
        parsePerson(id, "---\nname: $id\n$fm\n---\n$body").person!!

    private val people = listOf(
        person("birthday-today", "birthday: 1990-10-11"),
        person("overdue", "cadence: monthly\nlast_contacted: 2026-08-01", "- [ ] Ask about the move"),
        person("never", "cadence: weekly"),
        person("due-soon", "cadence: monthly\nlast_contacted: 2026-09-15"),
        person("fine", "cadence: monthly\nlast_contacted: 2026-10-05"),
        person("kid-birthday", "family:\n  - name: Leela\n    relation: daughter\n    birthday: 2022-10-15"),
        person("planned", "cadence: weekly\nlast_contacted: 2026-01-01\nplans:\n  - when: 2026-10-14 18:30"),
        person("far-birthday", "birthday: 12-25"),
        person("vip", "priority: high\nlast_contacted: 2026-08-20"),
        person("unprioritised", "last_contacted: 2020-01-01"),
    )

    @Test fun `ranks who to reach out to and explains why`() {
        val s = suggestions(people, today)
        assertEquals(listOf("birthday-today", "vip", "overdue", "never", "kid-birthday", "due-soon"), s.map { it.person.id })
        assertEquals(
            listOf("Turns 36 today", "Overdue by 3 weeks", "Overdue by 6 weeks", "No catch-up logged yet", "Leela (daughter)'s birthday on Thursday", "Due in 4 days"),
            s.map { describe(it.reasons.first(), today) },
        )
    }

    @Test fun `priority raises the rank and implies a cadence`() {
        val low = person("low", "priority: low\ncadence: monthly\nlast_contacted: 2026-08-01")
        val high = person("high", "priority: high\ncadence: monthly\nlast_contacted: 2026-08-01")
        assertEquals(listOf("high", "low"), suggestions(listOf(low, high), today).map { it.person.id })
        assertEquals(30, person("h", "priority: high").effectiveCadence()!!.days)
        assertEquals(null, person("m", "").effectiveCadence())
    }

    @Test fun `plans the week over free evenings, birthdays on the day`() {
        val start = LocalDate.of(2026, 10, 12) // Monday
        val free = listOf(
            Slot(LocalDate.of(2026, 10, 13), DayPart.EVENING),
            Slot(LocalDate.of(2026, 10, 15), DayPart.LUNCH),
            Slot(LocalDate.of(2026, 10, 15), DayPart.EVENING),
            Slot(LocalDate.of(2026, 10, 17), DayPart.AFTERNOON),
        )
        val plan = planWeek(people, today, start, free, goal = 4)
        assertEquals(
            listOf(
                "vip" to "2026-10-13 19:00", "overdue" to "2026-10-15 12:30",
                "kid-birthday" to "2026-10-15 19:00", "never" to "2026-10-17 15:00",
            ),
            plan.proposals.map { it.person.id to it.slot.at.format() },
        )
        assertEquals(listOf(KIND_MEET, KIND_CALL, KIND_CALL, KIND_CALL), plan.proposals.map { it.kind })
        // Today's birthday is before the planned week: it's for today, not the plan.
        assertEquals(listOf("due-soon"), plan.more.map { it.person.id })
    }

    @Test fun `awaiting notes and upcoming`() {
        val now = LocalDateTime.of(2026, 10, 14, 22, 0)
        val p = person("p", "plans:\n  - when: 2026-10-14 18:30\n  - when: 2026-10-20")
        assertEquals(listOf("2026-10-14 18:30"), awaitingNotes(listOf(p), now).map { it.plan.key })
        assertEquals(listOf("2026-10-20"), upcomingPlans(listOf(p), now.toLocalDate().plusDays(1)).map { it.plan.key })
    }

    @Test fun `reminders before and follow-ups after`() {
        val p = person(
            "maya", "nickname: Maya\nplans:\n  - when: 2026-10-14 18:30\n    what: Dinner\n    where: Luigi's\n  - when: 2026-10-15 20:30\n    what: Call\n  - when: 2026-10-16\n    remind: 1d\n  - when: 2026-10-17 10:00\n    remind: none",
            "- [ ] Ask about the house",
        )
        val a = alarms(listOf(p), LocalDateTime.of(2026, 10, 11, 12, 0))
        assertEquals(
            listOf(
                "REMINDER 2026-10-14T17:30 Dinner with Maya | at 18:30 · Luigi's\nAsk about: Ask about the house",
                "FOLLOW_UP 2026-10-14T20:30 How was it with Maya?",
                "REMINDER 2026-10-15T09:00 Catch up with Maya | today\nAsk about: Ask about the house",
                "REMINDER 2026-10-15T20:15 Call with Maya | at 20:30\nAsk about: Ask about the house",
                "FOLLOW_UP 2026-10-16T09:30 How was it with Maya?",
                "FOLLOW_UP 2026-10-16T20:00 How was it with Maya?",
                "FOLLOW_UP 2026-10-17T12:00 How was it with Maya?",
            ),
            a.map { "${it.type} ${it.at} ${it.title}" + if (it.type == Alarm.Type.REMINDER) " | ${it.text}" else "" },
        )
        // Nothing in the past.
        assertEquals(1, alarms(listOf(p), LocalDateTime.of(2026, 10, 17, 11, 0)).size)
    }

    @Test fun `weekly prompt`() {
        val now = LocalDateTime.of(2026, 10, 11, 18, 0)
        assertEquals(LocalDateTime.of(2026, 10, 18, 18, 0), nextWeeklyPrompt(now, DayOfWeek.SUNDAY, LocalTime.of(18, 0)))
        assertEquals(LocalDateTime.of(2026, 10, 11, 19, 0), nextWeeklyPrompt(now, DayOfWeek.SUNDAY, LocalTime.of(19, 0)))
        assertEquals(LocalDate.of(2026, 10, 12), planningWeekStart(now))
        assertEquals(
            "birthday-today, vip and 4 others would love to hear from you. When are you free this week?",
            weeklyPromptText(people, today),
        )
    }
}
