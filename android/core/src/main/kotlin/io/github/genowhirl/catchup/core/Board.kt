package io.github.genowhirl.catchup.core

import java.time.LocalDate
import java.time.LocalTime

// ---------------------------------------------------------------------------------------------
// The week board: free times you can drop people into (several per time if you like), and
// everyone else on a bench, ordered the way you choose.

/** Someone placed at a time on the board. */
data class Placement(val person: Person, val kind: String, val reasons: List<Reason> = emptyList())

data class WeekBoard(
    /** Free times, in order. */
    val slots: List<Slot> = emptyList(),
    val placed: Map<Slot, List<Placement>> = emptyMap(),
) {
    val count: Int get() = placed.values.sumOf { it.size }

    fun at(slot: Slot): List<Placement> = placed[slot].orEmpty()

    fun slotOf(personId: String): Slot? = placed.entries.firstOrNull { (_, l) -> l.any { it.person.id == personId } }?.key

    /** Puts someone at a time, moving them if they were somewhere else. Adds the time if new. */
    fun place(person: Person, slot: Slot, kind: String? = null, reasons: List<Reason> = emptyList()): WeekBoard {
        val before = placed.values.flatten().firstOrNull { it.person.id == person.id }
        val placement = Placement(person, kind ?: before?.kind ?: suggestedKind(person), reasons.ifEmpty { before?.reasons.orEmpty() })
        val rest = remove(person.id).addSlot(slot)
        return rest.copy(placed = rest.placed + (slot to rest.at(slot) + placement))
    }

    fun remove(personId: String): WeekBoard =
        copy(placed = placed.mapValues { (_, l) -> l.filter { it.person.id != personId } }.filterValues { it.isNotEmpty() })

    fun setKind(personId: String, kind: String): WeekBoard =
        copy(placed = placed.mapValues { (_, l) -> l.map { if (it.person.id == personId) it.copy(kind = kind) else it } })

    fun addSlot(slot: Slot): WeekBoard = if (slot in slots) this else copy(slots = (slots + slot).sortedBy { it.at })

    fun removeSlot(slot: Slot): WeekBoard = copy(slots = slots - slot, placed = placed - slot)

    /** Changes a time; if another free time already has that time, the two are merged. */
    fun retime(slot: Slot, time: LocalTime): WeekBoard {
        val moved = Slot(slot.date, partOf(time), time)
        if (moved == slot || slot !in slots) return this
        val people = at(moved) + at(slot).filter { m -> at(moved).none { it.person.id == m.person.id } }
        val rest = removeSlot(slot).removeSlot(moved).addSlot(moved)
        return if (people.isEmpty()) rest else rest.copy(placed = rest.placed + (moved to people))
    }

    fun clear(): WeekBoard = copy(placed = emptyMap())

    /** Fills empty times with the best suggestions until `goal` people are on the board. */
    fun autoFill(people: List<Person>, today: LocalDate, weekStart: LocalDate, goal: Int): WeekBoard {
        val onBoard = placed.values.flatten().map { it.person.id }.toSet()
        val empty = slots.filter { at(it).isEmpty() }
        val plan = planWeek(people.filter { it.id !in onBoard }, today, weekStart, empty, (goal - count).coerceAtLeast(0))
        return plan.proposals.fold(this) { b, pr -> b.place(pr.person, pr.slot, pr.kind, pr.reasons) }
    }

    /** What to save: one plan per person placed. */
    fun plans(): List<Pair<Person, Plan>> = slots.flatMap { s -> at(s).map { it.person to Plan(s.at, it.kind) } }

    companion object {
        fun of(slots: List<Slot>, plan: WeekPlan): WeekBoard =
            plan.proposals.fold(WeekBoard(slots.sortedBy { it.at })) { b, pr -> b.place(pr.person, pr.slot, pr.kind, pr.reasons) }
    }
}

/** How to order the bench of people to choose from. */
enum class PeopleOrder(val label: String) {
    SUGGESTED("Suggested"),
    DUE("Keep in touch"),
    PRIORITY("Priority"),
    CADENCE("Cadence"),
}

/** Someone on the bench, with why they're suggested (if they are) and when they're due. */
data class BenchItem(
    val person: Person,
    val reasons: List<Reason>,
    val score: Double,
    val due: LocalDate?,
    /** A catch-up already planned that week. */
    val planned: Plan?,
)

/** Everyone, in the chosen order. People already planned for that week come last. */
fun bench(people: List<Person>, today: LocalDate, weekStart: LocalDate, order: PeopleOrder): List<BenchItem> {
    val weekEnd = weekStart.plusDays(6)
    val suggested = weekSuggestions(people, today, weekStart).associateBy { it.person.id }
    val items = people.map { p ->
        val s = suggested[p.id]
        BenchItem(
            p, s?.reasons.orEmpty(), s?.score ?: 0.0, p.effectiveDueDate(today),
            p.plans.firstOrNull { !it.at.date.isBefore(weekStart) && !it.at.date.isAfter(weekEnd) },
        )
    }
    val byDue = compareBy<BenchItem, LocalDate?>(nullsLast()) { it.due }
    val byName = compareBy<BenchItem> { it.person.name.lowercase() }
    val comparator: Comparator<BenchItem> = when (order) {
        PeopleOrder.SUGGESTED -> compareByDescending<BenchItem> { it.score }.then(byDue).then(byName)
        PeopleOrder.DUE -> byDue.thenBy { it.person.priority.ordinal }.then(byName)
        PeopleOrder.PRIORITY -> compareBy<BenchItem> { it.person.priority.ordinal }.then(byDue).then(byName)
        PeopleOrder.CADENCE -> compareBy<BenchItem, Int?>(nullsLast()) { it.person.effectiveCadence()?.days }.then(byDue).then(byName)
    }
    return items.sortedWith(compareBy<BenchItem> { it.planned != null }.then(comparator))
}
