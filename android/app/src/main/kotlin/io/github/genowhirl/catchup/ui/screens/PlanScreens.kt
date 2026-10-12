package io.github.genowhirl.catchup.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.genowhirl.catchup.core.DayPart
import io.github.genowhirl.catchup.core.KIND_CALL
import io.github.genowhirl.catchup.core.KIND_MEET
import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.Plan
import io.github.genowhirl.catchup.core.PlanInput
import io.github.genowhirl.catchup.core.Slot
import io.github.genowhirl.catchup.core.Upcoming
import io.github.genowhirl.catchup.core.When
import io.github.genowhirl.catchup.core.planTitle
import io.github.genowhirl.catchup.core.InviteWhen
import io.github.genowhirl.catchup.core.Languages
import io.github.genowhirl.catchup.core.WeekBoard
import io.github.genowhirl.catchup.core.inviteText
import io.github.genowhirl.catchup.core.planningWeekStart
import io.github.genowhirl.catchup.core.upcomingPlans
import io.github.genowhirl.catchup.ui.components.Avatar
import io.github.genowhirl.catchup.ui.components.DateTile
import io.github.genowhirl.catchup.ui.components.EmptyState
import io.github.genowhirl.catchup.ui.components.Panel
import io.github.genowhirl.catchup.ui.components.ReasonPill
import io.github.genowhirl.catchup.ui.components.SectionHeader
import io.github.genowhirl.catchup.ui.components.planText
import io.github.genowhirl.catchup.ui.components.relativeDay
import io.github.genowhirl.catchup.ui.components.shortDate
import io.github.genowhirl.catchup.ui.components.weekday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

// ---------------------------------------------------------------------------------------------
// The Plans tab: the week ahead and everything scheduled further out.

@Composable
fun PlansTab(
    people: List<Person>,
    today: LocalDate,
    onPlanWeek: () -> Unit,
    onOpenPlan: (Person, Plan) -> Unit,
    onNewPlan: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val all = upcomingPlans(people, today, 400)
    // Grouped by the same rolling 7 days as the strip above, so the two always agree.
    val inAWeek = today.plusDays(7)
    val groups = listOf(
        "Next 7 days" to all.filter { it.plan.at.date.isBefore(inAWeek) },
        "The week after" to all.filter { !it.plan.at.date.isBefore(inAWeek) && it.plan.at.date.isBefore(inAWeek.plusDays(7)) },
        "Later" to all.filter { !it.plan.at.date.isBefore(inAWeek.plusDays(7)) },
    ).filter { it.second.isNotEmpty() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp + contentPadding.calculateBottomPadding())) {
            item {
                Column(Modifier.statusBarsPadding().padding(top = 28.dp)) {
                    Text("Plans", style = MaterialTheme.typography.displaySmall)
                    Spacer(Modifier.height(16.dp))
                    WeekStrip(today, all, onPlanWeek)
                }
            }
            if (all.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.CalendarMonth,
                        "Nothing planned yet",
                        "Plan your week in a minute, or schedule a catch-up further ahead — CatchUp reminds you before and asks how it went after.",
                    )
                }
            }
            groups.forEach { (title, list) ->
                item { SectionHeader(title) }
                items(list, key = { "${it.person.id}:${it.plan.key}" }) { (p, plan) ->
                    Panel(Modifier.padding(bottom = 8.dp), onClick = { onOpenPlan(p, plan) }, padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DateTile(plan.at.date, highlight = plan.at.date == today)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(planTitle(p, plan), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(planText(plan, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Avatar(p.name, 36.dp)
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNewPlan,
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("New plan") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp + contentPadding.calculateBottomPadding()),
        )
    }
}

@Composable
private fun WeekStrip(today: LocalDate, all: List<Upcoming>, onPlanWeek: () -> Unit) {
    Panel(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            (0..6).forEach { i ->
                val d = today.plusDays(i.toLong())
                val count = all.count { it.plan.at.date == d }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(weekday(d).take(2), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                    Spacer(Modifier.height(4.dp))
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (i == 0) MaterialTheme.colorScheme.primary else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            d.dayOfMonth.toString(),
                            style = MaterialTheme.typography.titleSmall,
                            color = if (i == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        repeat(count.coerceAtMost(3)) { Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.primary)) }
                        if (count == 0) Spacer(Modifier.size(6.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPlanWeek, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Plan your week")
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Weekly planning: when are you free → who to see or call → invite them.

private enum class Step { FREE, PLAN, DONE }

@Composable
fun PlanWeekScreen(
    people: List<Person>,
    now: LocalDateTime,
    goal: Int,
    usual: Set<Pair<DayOfWeek, DayPart>>,
    onBack: () -> Unit,
    onSave: (List<Op>, String) -> Unit,
    onRememberUsual: (Set<Pair<DayOfWeek, DayPart>>) -> Unit,
    onInvite: (Person, String) -> Unit,
    onAddToCalendar: (Person, Plan) -> Unit,
) {
    val today = now.toLocalDate()
    val start = planningWeekStart(now)
    val days = (0..6).map { start.plusDays(it.toLong()) }
    val busy = upcomingPlans(people, start, 7).associateBy { it.plan.at.date to partOf(it.plan.at) }
    val free = remember {
        mutableStateListOf<Slot>().apply {
            days.forEach { d -> DayPart.entries.forEach { p -> if ((d.dayOfWeek to p) in usual && (d to p) !in busy) add(Slot(d, p)) } }
        }
    }
    var target by remember { mutableStateOf(goal.coerceAtLeast(1)) }
    var keepAsUsual by remember { mutableStateOf(false) }
    var step by remember { mutableStateOf(Step.FREE) }
    var board by remember { mutableStateOf(WeekBoard()) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(when (step) { Step.FREE -> "Plan your week"; Step.PLAN -> "Who and when"; Step.DONE -> "All set" }) },
            navigationIcon = {
                IconButton({ if (step == Step.PLAN) step = Step.FREE else onBack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
        )
        AnimatedContent(step, label = "step", modifier = Modifier.weight(1f)) { s ->
            when (s) {
                Step.FREE -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                    item {
                        Text("When are you free?", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Tap the times you could see or call someone, ${shortDate(days.first())} – ${shortDate(days.last())}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(Modifier.fillMaxWidth().padding(start = 72.dp)) {
                            DayPart.entries.forEach {
                                Text(it.label, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    items(days) { d ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.width(72.dp)) {
                                Text(if (d == today) "Today" else weekday(d), style = MaterialTheme.typography.titleSmall)
                                Text(shortDate(d), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            DayPart.entries.forEach { part ->
                                val slot = Slot(d, part)
                                val taken = busy[d to part]
                                val on = slot in free
                                val past = d == today && part.time.isBefore(now.toLocalTime())
                                Box(
                                    Modifier.weight(1f).padding(3.dp).aspectRatio(1.25f).clip(RoundedCornerShape(14.dp))
                                        .background(
                                            when {
                                                taken != null -> MaterialTheme.colorScheme.tertiaryContainer
                                                on -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                            },
                                        )
                                        .then(if (past) Modifier else Modifier.clickable(enabled = taken == null) { if (on) free.remove(slot) else free.add(slot) }),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    when {
                                        taken != null -> Text(io.github.genowhirl.catchup.ui.components.initials(taken.person.name), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                                        on -> Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary)
                                        past -> Text("–", color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(20.dp))
                        Panel {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("How many catch-ups?", style = MaterialTheme.typography.titleMedium)
                                    Text("Calls count too.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FilledTonalIconButton({ if (target > 1) target-- }) { Icon(Icons.Rounded.Remove, "Fewer") }
                                Text("$target", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 12.dp))
                                FilledTonalIconButton({ if (target < 14) target++ }) { Icon(Icons.Rounded.Add, "More") }
                            }
                        }
                        Row(Modifier.clickable { keepAsUsual = !keepAsUsual }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(keepAsUsual, { keepAsUsual = it })
                            Text("Remember these times as my usual week", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                if (keepAsUsual) onRememberUsual(free.map { it.date.dayOfWeek to it.part }.toSet())
                                board = boardFor(free.toList(), board, people, today, start, target)
                                step = Step.PLAN
                            },
                            enabled = free.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Suggest who to see")
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

                Step.PLAN -> WeekBoardStep(
                    people, today, start, target, board, { board = it },
                    onSave = {
                        val plans = board.plans()
                        onSave(
                            plans.map { (p, plan) -> Op.SavePlan(p.path, PlanInput(plan.at.format(), plan.kind)) },
                            "Planned ${plans.size} catch-up${if (plans.size == 1) "" else "s"}",
                        )
                        step = Step.DONE
                    },
                )

                Step.DONE -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                    item {
                        Text("Your week is planned 🎉", style = MaterialTheme.typography.headlineMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Let them know — CatchUp will remind you before, and ask how it went after.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    board.slots.filter { board.at(it).isNotEmpty() }.forEach { slot ->
                        item(key = "slot:" + slot.at.format() + slot.part) {
                            Text(
                                "${relativeDay(slot.date, today)} · ${slot.time.format(When.HH_MM)}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
                            )
                        }
                        items(board.at(slot), key = { "p:" + it.person.id }) { pl ->
                            val plan = Plan(slot.at, pl.kind)
                            Panel(Modifier.padding(bottom = 10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(pl.person.name, 44.dp, pl.person.priority)
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(planTitle(pl.person, plan), style = MaterialTheme.typography.titleMedium)
                                        pl.person.language?.let {
                                            Text("Invite in ${Languages.displayName(it)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilledTonalButton(onClick = { onInvite(pl.person, inviteText(pl.person, pl.kind, slot.inviteWhen(), today)) }) {
                                        Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Invite")
                                    }
                                    TextButton(onClick = { onAddToCalendar(pl.person, plan) }) { Text("Add to calendar") }
                                }
                            }
                        }
                    }
                    item {
                        Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(52.dp).navigationBarsPadding()) { Text("Done") }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

/** Free times from step one, keeping anyone already placed; a fresh board is pre-filled. */
private fun boardFor(free: List<Slot>, old: WeekBoard, people: List<Person>, today: LocalDate, start: LocalDate, goal: Int): WeekBoard {
    val kept = old.slots.filter { s -> Slot(s.date, s.part) in free || old.at(s).isNotEmpty() }
    var b = WeekBoard((free.filter { f -> kept.none { it.date == f.date && it.part == f.part } } + kept).sortedBy { it.at })
    kept.forEach { s -> old.at(s).forEach { pl -> b = b.place(pl.person, s, pl.kind, pl.reasons) } }
    return if (b.count == 0) b.autoFill(people, today, start, goal) else b
}

/** "Tuesday evening" when the time is the usual one for that part of the day, else the time. */
fun Slot.inviteWhen(): InviteWhen =
    if (time == part.time) InviteWhen(date, part = part) else InviteWhen(date, time = time)

private fun partOf(w: When): DayPart = io.github.genowhirl.catchup.core.partOf(w.time)
