package io.github.genowhirl.catchup.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.genowhirl.catchup.core.BenchItem
import io.github.genowhirl.catchup.core.KIND_CALL
import io.github.genowhirl.catchup.core.KIND_MEET
import io.github.genowhirl.catchup.core.PeopleOrder
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.Placement
import io.github.genowhirl.catchup.core.Reason
import io.github.genowhirl.catchup.core.Slot
import io.github.genowhirl.catchup.core.WeekBoard
import io.github.genowhirl.catchup.core.When
import io.github.genowhirl.catchup.core.bench
import io.github.genowhirl.catchup.core.daysBetween
import io.github.genowhirl.catchup.core.describe
import io.github.genowhirl.catchup.core.effectiveCadence
import io.github.genowhirl.catchup.core.formatDaysAgo
import io.github.genowhirl.catchup.core.partOf
import io.github.genowhirl.catchup.ui.components.Avatar
import io.github.genowhirl.catchup.ui.components.DateTile
import io.github.genowhirl.catchup.ui.components.DragDropState
import io.github.genowhirl.catchup.ui.components.ReasonPill
import io.github.genowhirl.catchup.ui.components.TimeChip
import io.github.genowhirl.catchup.ui.components.dragSource
import io.github.genowhirl.catchup.ui.components.dropTarget
import io.github.genowhirl.catchup.ui.components.rememberDragDropState
import io.github.genowhirl.catchup.ui.components.shortDate
import io.github.genowhirl.catchup.ui.components.weekday
import io.github.genowhirl.catchup.ui.sheets.cadenceLabel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle
import kotlin.math.roundToInt

private const val BENCH = "bench"

/**
 * Step two of planning the week: free times on top, everyone else on a bench below. Drag
 * people onto a time (or tap someone, then a time); several people can share a time.
 */
@Composable
internal fun WeekBoardStep(
    people: List<Person>,
    today: LocalDate,
    weekStart: LocalDate,
    goal: Int,
    board: WeekBoard,
    onBoard: (WeekBoard) -> Unit,
    onSave: () -> Unit,
) {
    var order by remember { mutableStateOf(PeopleOrder.SUGGESTED) }
    var selected by remember { mutableStateOf<String?>(null) }
    var addingTime by remember { mutableStateOf(false) }
    val benchItems = remember(people, order, weekStart) { bench(people, today, weekStart, order) }
    fun person(id: String) = people.firstOrNull { it.id == id }
    fun reasons(id: String): List<Reason> = benchItems.firstOrNull { it.person.id == id }?.reasons.orEmpty()

    val drag = rememberDragDropState<String> { id, target ->
        val p = person(id) ?: return@rememberDragDropState
        when (target) {
            is Slot -> onBoard(board.place(p, target, reasons = reasons(id)))
            BENCH -> onBoard(board.remove(id))
        }
        selected = null
    }
    fun tapSlot(slot: Slot) {
        val p = selected?.let(::person) ?: return
        onBoard(board.place(p, slot, reasons = reasons(p.id)))
        selected = null
    }

    var origin by remember { mutableStateOf(Offset.Zero) }
    var slotsArea by remember { mutableStateOf(Rect.Zero) }
    val slotsList = rememberLazyListState()
    val edge = with(LocalDensity.current) { 56.dp.toPx() }

    // Scroll the times while dragging near the top or bottom of them.
    LaunchedEffect(drag.item != null) {
        while (drag.item != null) {
            val y = drag.pointer.y
            val inside = drag.pointer.x in slotsArea.left..slotsArea.right
            when {
                inside && y in slotsArea.top..(slotsArea.top + edge) -> slotsList.scrollBy(-14f)
                inside && y in (slotsArea.bottom - edge)..slotsArea.bottom -> slotsList.scrollBy(14f)
            }
            delay(16)
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize()) {
            // Header.
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(
                    if (selected != null) "Now tap a time for ${person(selected!!)?.shortName}."
                    else "Hold and drag people onto a time, or tap someone and then a time. Several people can share one.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(Modifier.padding(top = 8.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { onBoard(board.autoFill(people, today, weekStart, maxOf(goal, board.count + 1))) },
                        label = { Text("Suggest") },
                        leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(AssistChipDefaults.IconSize)) },
                        enabled = board.slots.any { board.at(it).isEmpty() },
                    )
                    AssistChip(
                        onClick = { addingTime = true },
                        label = { Text("Add a time") },
                        leadingIcon = { Icon(Icons.Rounded.Add, null, Modifier.size(AssistChipDefaults.IconSize)) },
                    )
                    if (board.count > 0) {
                        AssistChip(
                            onClick = { onBoard(board.clear()) },
                            label = { Text("Clear") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null, Modifier.size(AssistChipDefaults.IconSize)) },
                        )
                    }
                }
            }

            // Free times.
            LazyColumn(
                state = slotsList,
                modifier = Modifier.weight(1f).fillMaxWidth().onGloballyPositioned { slotsArea = it.boundsInRoot() },
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (board.slots.isEmpty()) {
                    item {
                        Text(
                            "No free times yet — go back and tap when you're free, or add a time.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(board.slots, key = { it.at.format() + it.part }) { slot ->
                    SlotCard(
                        slot, today, board.at(slot), drag,
                        hovered = drag.hovered == slot,
                        pickingFor = selected?.let(::person),
                        onTap = { tapSlot(slot) },
                        onTime = { t -> onBoard(board.retime(slot, t)) },
                        onRemoveSlot = { onBoard(board.removeSlot(slot)) },
                        onKind = { id, k -> onBoard(board.setKind(id, k)) },
                        onRemove = { id -> onBoard(board.remove(id)) },
                    )
                }
            }

            // The bench.
            Bench(
                benchItems, today, order, { order = it }, board, selected,
                onSelect = { id -> selected = if (selected == id) null else id },
                drag = drag,
                modifier = Modifier.weight(1f),
            )

            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = onSave,
                    enabled = board.count > 0,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
                ) {
                    Icon(Icons.Rounded.EventAvailable, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (board.count == 0) "Add someone to save" else "Save ${board.count} plan${if (board.count == 1) "" else "s"}")
                }
            }
        }

        // What's being dragged, under the finger.
        drag.item?.let(::person)?.let { p ->
            val density = LocalDensity.current
            val lift = with(density) { Offset(24.dp.toPx(), 56.dp.toPx()) }
            Row(
                Modifier
                    .offset { (drag.pointer - origin - lift).let { IntOffset(it.x.roundToInt(), it.y.roundToInt()) } }
                    .shadow(10.dp, RoundedCornerShape(50))
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(start = 4.dp, end = 14.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(p.name, 36.dp)
                Spacer(Modifier.width(8.dp))
                Text(p.shortName, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }

    if (addingTime) {
        AddTimeSheet(weekStart, today, { addingTime = false }) { slot ->
            onBoard(board.addSlot(slot))
            addingTime = false
        }
    }
}

@Composable
private fun SlotCard(
    slot: Slot,
    today: LocalDate,
    placed: List<Placement>,
    drag: DragDropState<String>,
    hovered: Boolean,
    pickingFor: Person?,
    onTap: () -> Unit,
    onTime: (LocalTime) -> Unit,
    onRemoveSlot: () -> Unit,
    onKind: (String, String) -> Unit,
    onRemove: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (hovered) scheme.primaryContainer else scheme.surfaceContainerLow, label = "slot")
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth()
            .dropTarget(drag, slot)
            .clip(shape)
            .background(bg)
            .border(if (hovered || pickingFor != null) 2.dp else 0.dp, if (hovered || pickingFor != null) scheme.primary else Color.Transparent, shape)
            .clickable(enabled = pickingFor != null, onClick = onTap)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DateTile(slot.date, highlight = slot.date == today, modifier = Modifier.size(46.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (slot.date == today) "Today" else slot.date.dayOfWeek.getDisplayName(TextStyle.FULL, java.util.Locale.getDefault()),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(slot.part.label, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            TimeChip(slot.time, { it?.let(onTime) }, clearable = false, defaultTime = slot.time)
            if (placed.isEmpty()) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(50)).clickable(onClick = onRemoveSlot), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, "Remove this time", tint = scheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (placed.isEmpty()) {
            Box(
                Modifier.fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp))
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    pickingFor?.let { "Tap to add ${it.shortName}" } ?: if (hovered) "Drop here" else "Drag someone here",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (pickingFor != null || hovered) scheme.primary else scheme.onSurfaceVariant,
                )
            }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                placed.forEach { pl -> PlacedChip(pl, drag, onKind, onRemove) }
                if (pickingFor != null) {
                    Box(
                        Modifier.height(40.dp).clip(RoundedCornerShape(50)).border(1.dp, scheme.primary, RoundedCornerShape(50)).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("+ ${pickingFor.shortName}", style = MaterialTheme.typography.labelLarge, color = scheme.primary) }
                }
            }
        }
    }
}

@Composable
private fun PlacedChip(pl: Placement, drag: DragDropState<String>, onKind: (String, String) -> Unit, onRemove: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val call = pl.kind == KIND_CALL
    Row(
        Modifier.height(40.dp)
            .alpha(if (drag.item == pl.person.id) 0.35f else 1f)
            .clip(RoundedCornerShape(50))
            .background(scheme.secondaryContainer)
            .dragSource(drag, pl.person.id)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(pl.person.name, 32.dp)
        Spacer(Modifier.width(8.dp))
        Text(pl.person.shortName, style = MaterialTheme.typography.labelLarge, color = scheme.onSecondaryContainer, maxLines = 1)
        // Call or meet: tap to switch.
        Box(
            Modifier.padding(start = 6.dp).size(32.dp).clip(RoundedCornerShape(50)).background(scheme.surface.copy(alpha = 0.6f))
                .clickable { onKind(pl.person.id, if (call) KIND_MEET else KIND_CALL) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (call) Icons.Rounded.Call else Icons.Rounded.Groups, if (call) "Call — tap to meet instead" else "Meet up — tap to call instead", Modifier.size(16.dp), tint = scheme.onSecondaryContainer)
        }
        Box(Modifier.size(36.dp).clickable { onRemove(pl.person.id) }, contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Close, "Remove ${pl.person.shortName}", Modifier.size(16.dp), tint = scheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun Bench(
    items: List<BenchItem>,
    today: LocalDate,
    order: PeopleOrder,
    onOrder: (PeopleOrder) -> Unit,
    board: WeekBoard,
    selected: String?,
    onSelect: (String) -> Unit,
    drag: DragDropState<String>,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val hovered = drag.hovered == BENCH
    Surface(
        modifier.fillMaxWidth().dropTarget(drag, BENCH),
        color = if (hovered) scheme.secondaryContainer else scheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(width = 32.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(scheme.outlineVariant))
            }
            Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("People", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (hovered) Text("Drop to take off the plan", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(PeopleOrder.entries) { o ->
                    FilterChip(order == o, { onOrder(o) }, { Text(o.label) })
                }
            }
            // People already on the board go to the end, so the top shows who's left.
            val ordered = items.sortedBy { board.slotOf(it.person.id) != null }
            LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 8.dp)) {
                items(ordered, key = { it.person.id }) { item ->
                    BenchRow(item, today, order, board.slotOf(item.person.id), selected == item.person.id, drag) { onSelect(item.person.id) }
                }
            }
        }
    }
}

@Composable
private fun BenchRow(item: BenchItem, today: LocalDate, order: PeopleOrder, slot: Slot?, selected: Boolean, drag: DragDropState<String>, onTap: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val p = item.person
    Row(
        Modifier.fillMaxWidth()
            .alpha(if (drag.item == p.id) 0.35f else if (slot != null || item.planned != null) 0.6f else 1f)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .dragSource(drag, p.id)
            .clickable(onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(p.name, 40.dp, p.priority)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                item.planned != null -> Hint("Already planned ${weekday(item.planned!!.at.date)} ${shortDate(item.planned!!.at.date)}")
                order == PeopleOrder.SUGGESTED && item.reasons.isNotEmpty() -> ReasonPill(item.reasons.first(), today, Modifier.padding(top = 2.dp))
                order == PeopleOrder.PRIORITY -> Hint("${p.priority.label} priority · ${cadenceLabel(p.effectiveCadence()).lowercase()}")
                order == PeopleOrder.CADENCE -> Hint("${cadenceLabel(p.effectiveCadence())} · ${lastInTouch(p, today)}")
                else -> Hint(dueText(item, today) + " · " + lastInTouch(p, today))
            }
        }
        Spacer(Modifier.width(8.dp))
        when {
            slot != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Check, null, tint = scheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("${weekday(slot.date)} ${slot.time.format(When.HH_MM)}", style = MaterialTheme.typography.labelMedium, color = scheme.primary)
            }
            selected -> Icon(Icons.Rounded.TouchApp, null, tint = scheme.primary)
        }
    }
}

@Composable
private fun Hint(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)

private fun lastInTouch(p: Person, today: LocalDate) =
    p.lastContacted?.let { "last ${formatDaysAgo(daysBetween(it, today))}" } ?: "not in touch yet"

private fun dueText(item: BenchItem, today: LocalDate): String {
    val due = item.due ?: return "No rhythm set"
    val days = daysBetween(today, due)
    return when {
        item.person.lastContacted == null && item.person.nextContact == null -> "Due now"
        days < 0 -> describe(Reason.Overdue(-days), today)
        else -> describe(Reason.DueSoon(days), today)
    }
}

@Composable
private fun AddTimeSheet(weekStart: LocalDate, today: LocalDate, onDismiss: () -> Unit, onAdd: (Slot) -> Unit) {
    var day by remember { mutableStateOf(weekStart) }
    var time by remember { mutableStateOf(LocalTime.of(19, 0)) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Add a time", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (0..6).map { weekStart.plusDays(it.toLong()) }.forEach { d ->
                    FilterChip(day == d, { day = d }, { Text(if (d == today) "Today" else "${weekday(d)} ${d.dayOfMonth}") })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("At", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(12.dp))
                TimeChip(time, { it?.let { t -> time = t } }, clearable = false)
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(8.dp))
                Button({ onAdd(Slot(day, partOf(time), time)) }) { Text("Add") }
            }
        }
    }
}
