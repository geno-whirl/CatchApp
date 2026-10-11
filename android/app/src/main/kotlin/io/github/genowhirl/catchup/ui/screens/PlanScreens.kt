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
import io.github.genowhirl.catchup.core.Proposal
import io.github.genowhirl.catchup.core.Slot
import io.github.genowhirl.catchup.core.Suggestion
import io.github.genowhirl.catchup.core.Upcoming
import io.github.genowhirl.catchup.core.When
import io.github.genowhirl.catchup.core.planTitle
import io.github.genowhirl.catchup.core.planWeek
import io.github.genowhirl.catchup.core.planningWeekStart
import io.github.genowhirl.catchup.core.suggestedKind
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
import java.time.temporal.TemporalAdjusters

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
    val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
    val groups = listOf(
        "This week" to all.filter { !it.plan.at.date.isAfter(sunday) },
        "Next week" to all.filter { it.plan.at.date.isAfter(sunday) && !it.plan.at.date.isAfter(sunday.plusWeeks(1)) },
        "Later" to all.filter { it.plan.at.date.isAfter(sunday.plusWeeks(1)) },
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
    val proposals = remember { mutableStateListOf<Proposal>() }
    val more = remember { mutableStateListOf<Suggestion>() }
    var adding by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(when (step) { Step.FREE -> "Plan your week"; Step.PLAN -> "Your week"; Step.DONE -> "All set" }) },
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
                                val plan = planWeek(people, today, start, free.toList(), target)
                                proposals.clear(); proposals.addAll(plan.proposals)
                                // Suggestions first, then everyone else (longest since contact first).
                                val chosen = plan.proposals.map { it.person.id }.toSet() + plan.more.map { it.person.id }
                                val others = people.filter { it.id !in chosen }
                                    .sortedBy { it.lastContacted ?: java.time.LocalDate.MIN }
                                    .map { Suggestion(it, emptyList(), 0.0) }
                                more.clear(); more.addAll(plan.more + others)
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

                Step.PLAN -> LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                    item {
                        Text(
                            if (proposals.isEmpty()) "No one is due this week" else "Here's an idea",
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (proposals.isEmpty()) "Everyone's in touch. Add someone anyway if you'd like."
                            else "Swap, remove or switch between a call and meeting up. Nothing is sent until you invite them.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    items(proposals.size) { i ->
                        val pr = proposals[i]
                        ProposalCard(
                            pr, today,
                            onKind = { k -> proposals[i] = pr.copy(kind = k) },
                            onSwap = if (more.isNotEmpty()) ({
                                val next = more.removeAt(0)
                                more.add(Suggestion(pr.person, pr.reasons, 0.0))
                                proposals[i] = Proposal(next.person, pr.slot, suggestedKind(next.person), next.reasons)
                            }) else null,
                            onRemove = { proposals.removeAt(i) },
                        )
                    }
                    item {
                        val freeLeft = free.filter { s -> proposals.none { it.slot == s } }
                        if (freeLeft.isNotEmpty() && more.isNotEmpty()) {
                            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.Add, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Add someone")
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                val ops = proposals.map { pr ->
                                    Op.SavePlan(pr.person.path, PlanInput(pr.slot.at.format(), pr.kind))
                                }
                                onSave(ops, "Planned ${proposals.size} catch-up${if (proposals.size == 1) "" else "s"}")
                                step = Step.DONE
                            },
                            enabled = proposals.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                        ) {
                            Icon(Icons.Rounded.EventAvailable, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Save ${proposals.size} plan${if (proposals.size == 1) "" else "s"}")
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }

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
                    items(proposals.size) { i ->
                        val pr = proposals[i]
                        val plan = Plan(pr.slot.at, pr.kind)
                        Panel(Modifier.padding(bottom = 10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(pr.person.name, 44.dp, pr.person.priority)
                                Spacer(Modifier.width(14.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(planTitle(pr.person, plan), style = MaterialTheme.typography.titleMedium)
                                    Text(slotText(pr.slot, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilledTonalButton(onClick = { onInvite(pr.person, io.github.genowhirl.catchup.ui.inviteText(pr.person, pr.kind, slotInvite(pr.slot, today))) }) {
                                    Icon(Icons.AutoMirrored.Rounded.Send, null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Invite")
                                }
                                TextButton(onClick = { onAddToCalendar(pr.person, plan) }) { Text("Add to calendar") }
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

    if (adding) {
        ModalBottomSheet(onDismissRequest = { adding = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                item { Text("Add someone", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 12.dp)) }
                items(more.toList(), key = { it.person.id }) { s ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable {
                            val slot = free.sortedWith(compareBy<Slot> { it.date }.thenBy { it.part }).firstOrNull { f -> proposals.none { it.slot == f } }
                            if (slot != null) {
                                proposals.add(Proposal(s.person, slot, suggestedKind(s.person), s.reasons))
                                more.remove(s)
                            }
                            adding = false
                        }.padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(s.person.name, 44.dp, s.person.priority)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.person.name, style = MaterialTheme.typography.titleMedium)
                            if (s.reasons.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { s.reasons.take(1).forEach { ReasonPill(it, today) } }
                            } else {
                                Text(
                                    s.person.lastContacted?.let { "Last in touch ${io.github.genowhirl.catchup.core.formatDaysAgo(io.github.genowhirl.catchup.core.daysBetween(it, today))}" } ?: "Not in touch yet",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(32.dp)) }
            }
        }
    }
}

private fun partOf(w: When): DayPart = when {
    w.time == null -> DayPart.EVENING
    w.time!!.hour < 12 -> DayPart.MORNING
    w.time!!.hour < 14 -> DayPart.LUNCH
    w.time!!.hour < 17 -> DayPart.AFTERNOON
    else -> DayPart.EVENING
}

private fun slotText(s: Slot, today: LocalDate) = "${relativeDay(s.date, today)} · ${s.part.label.lowercase()}, ${s.part.time.format(When.HH_MM)}"

private fun slotInvite(s: Slot, today: LocalDate): String {
    val day = relativeDay(s.date, today).lowercase().let { if (it == "today" || it == "tomorrow") it else "on ${relativeDay(s.date, today)}" }
    val part = when (s.part) {
        DayPart.MORNING -> "morning"
        DayPart.LUNCH -> "lunchtime"
        DayPart.AFTERNOON -> "afternoon"
        DayPart.EVENING -> "evening"
    }
    return "$day $part"
}

@Composable
private fun ProposalCard(pr: Proposal, today: LocalDate, onKind: (String) -> Unit, onSwap: (() -> Unit)?, onRemove: () -> Unit) {
    Panel(Modifier.padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(pr.person.name, 48.dp, pr.person.priority)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(pr.person.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(slotText(pr.slot, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            onSwap?.let { IconButton(it) { Icon(Icons.Rounded.SwapHoriz, "Someone else") } }
            IconButton(onRemove) { Icon(Icons.Rounded.Close, "Remove") }
        }
        if (pr.reasons.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                pr.reasons.forEach { ReasonPill(it, today) }
            }
        }
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(KIND_CALL, KIND_MEET).forEachIndexed { i, k ->
                SegmentedButton(selected = pr.kind == k, onClick = { onKind(k) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                    Text(if (k == KIND_CALL) "Call" else "Meet up")
                }
            }
        }
    }
}
