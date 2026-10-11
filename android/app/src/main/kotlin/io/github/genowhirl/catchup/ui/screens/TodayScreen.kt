package io.github.genowhirl.catchup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.Plan
import io.github.genowhirl.catchup.core.Suggestion
import io.github.genowhirl.catchup.core.awaitingNotes
import io.github.genowhirl.catchup.core.planTitle
import io.github.genowhirl.catchup.core.suggestions
import io.github.genowhirl.catchup.core.upcomingBirthdays
import io.github.genowhirl.catchup.core.upcomingPlans
import io.github.genowhirl.catchup.ui.Reach
import io.github.genowhirl.catchup.ui.components.Avatar
import io.github.genowhirl.catchup.ui.components.DateTile
import io.github.genowhirl.catchup.ui.components.EmptyState
import io.github.genowhirl.catchup.ui.components.Panel
import io.github.genowhirl.catchup.ui.components.ReasonPill
import io.github.genowhirl.catchup.ui.components.SectionHeader
import io.github.genowhirl.catchup.ui.components.birthdayText
import io.github.genowhirl.catchup.ui.components.inDays
import io.github.genowhirl.catchup.ui.components.longDate
import io.github.genowhirl.catchup.ui.components.planText
import io.github.genowhirl.catchup.ui.components.weekday
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

class TodayActions(
    val openPerson: (String) -> Unit,
    val log: (Person, Plan?) -> Unit,
    val plan: (Person) -> Unit,
    val didntHappen: (Person, Plan) -> Unit,
    val planWeek: () -> Unit,
    val call: (String) -> Unit,
    val message: (Person) -> Unit,
    val addPerson: () -> Unit,
    val import: () -> Unit,
)

private fun greeting(now: LocalDateTime) = when (now.hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
fun TodayScreen(people: List<Person>, now: LocalDateTime, userName: String, weeklyGoal: Int, actions: TodayActions, contentPadding: PaddingValues = PaddingValues()) {
    val today = now.toLocalDate()
    val reach = suggestions(people, today, 7).take(8)
    val awaiting = awaitingNotes(people, now).filter { !it.plan.at.date.isBefore(today.minusDays(14)) }
    val coming = upcomingPlans(people, today, 21).filter { it.plan.at.dateTime.isAfter(now) || it.plan.at.time == null }
    val birthdays = upcomingBirthdays(people, today, 30)
    val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val sunday = monday.plusDays(6)
    val doneThisWeek = people.sumOf { p -> p.interactions.count { !it.date.isBefore(monday) && !it.date.isAfter(today) } }
    val plannedThisWeek = coming.count { !it.plan.at.date.isAfter(sunday) }

    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp + contentPadding.calculateBottomPadding())) {
        item {
            Column(Modifier.statusBarsPadding().padding(top = 28.dp, bottom = 8.dp)) {
                Text(
                    "${weekday(today, TextStyle.FULL)}, ${longDate(today)}".uppercase(),
                    style = io.github.genowhirl.catchup.ui.theme.SectionLabel,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    greeting(now) + if (userName.isNotBlank()) ",\n$userName" else "",
                    style = MaterialTheme.typography.displaySmall,
                )
            }
        }
        if (people.isEmpty()) {
            item {
                EmptyState(
                    Icons.Rounded.PersonAdd,
                    "Who matters to you?",
                    "Add the people you want to stay close to, or bring them in from your contacts. CatchUp will help you keep in touch.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = actions.import) { Text("Import contacts") }
                        OutlinedButton(onClick = actions.addPerson) { Text("Add someone") }
                    }
                }
            }
            return@LazyColumn
        }

        item {
            Spacer(Modifier.height(16.dp))
            WeekCard(doneThisWeek, plannedThisWeek, weeklyGoal, actions.planWeek)
        }

        if (awaiting.isNotEmpty()) {
            item { SectionHeader("How did it go?") }
            items(awaiting, key = { "a:${it.person.id}:${it.plan.key}" }) { (p, plan) ->
                Panel(Modifier.padding(bottom = 10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(p.name, 44.dp, p.priority)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(planTitle(p, plan), style = MaterialTheme.typography.titleMedium)
                            Text(planText(plan, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { actions.log(p, plan) }) {
                            Icon(Icons.Rounded.EditNote, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Add notes")
                        }
                        TextButton(onClick = { actions.didntHappen(p, plan) }) { Text("Didn't happen") }
                    }
                }
            }
        }

        if (coming.isNotEmpty()) {
            item { SectionHeader("Coming up") }
            items(coming.take(6), key = { "c:${it.person.id}:${it.plan.key}" }) { (p, plan) ->
                Panel(Modifier.padding(bottom = 8.dp), onClick = { actions.openPerson(p.id) }, padding = PaddingValues(12.dp)) {
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

        item {
            SectionHeader("Reach out")
        }
        if (reach.isEmpty()) {
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Celebration, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "You're all caught up. Set how often you'd like to talk to people and CatchUp will nudge you when it's time.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
        items(reach, key = { "r:${it.person.id}" }) { s -> SuggestionCard(s, now, actions) }

        if (birthdays.isNotEmpty()) {
            item { SectionHeader("Birthdays") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(birthdays.take(12)) { b ->
                        Panel(Modifier.width(150.dp), onClick = { actions.openPerson(b.person.id) }, padding = PaddingValues(14.dp)) {
                            Avatar(b.person.name, 40.dp)
                            Spacer(Modifier.height(10.dp))
                            Text(b.label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                birthdayText(b.birthday.copy(year = null)) + " · " + inDays(b.days),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (b.days <= 7) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            b.birthday.upcomingAge(today)?.let {
                                Text("Turns $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCard(done: Int, planned: Int, goal: Int, onPlan: () -> Unit) {
    val total = done + planned
    val progress = if (goal <= 0) 0f else (total.toFloat() / goal).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer)))
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 8.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    strokeCap = StrokeCap.Round,
                )
                Text("$total/$goal", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text("This week", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    when {
                        total >= goal && goal > 0 -> "You've hit your goal. Lovely."
                        total == 0 -> "No catch-ups yet. Who would you like to see?"
                        else -> "$done done · $planned planned"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(10.dp))
                FilledTonalButton(onClick = onPlan, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
                    Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Plan your week")
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(s: Suggestion, now: LocalDateTime, actions: TodayActions) {
    val p = s.person
    val today = now.toLocalDate()
    val reach = Reach.of(p)
    Panel(Modifier.padding(bottom = 10.dp), onClick = { actions.openPerson(p.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.name, 52.dp, p.priority)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    s.reasons.forEach { ReasonPill(it, today) }
                }
            }
        }
        if (p.openFollowUps.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Ask about: " + p.openFollowUps.take(2).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            reach.phone?.let { phone -> FilledTonalIconButton(onClick = { actions.call(phone) }) { Icon(Icons.Rounded.Call, "Call") } }
            if (reach.phone != null || reach.whatsapp != null) {
                FilledTonalIconButton(onClick = { actions.message(p) }) { Icon(Icons.AutoMirrored.Rounded.Chat, "Message") }
            }
            FilledTonalIconButton(onClick = { actions.plan(p) }) { Icon(Icons.Rounded.EventAvailable, "Plan") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { actions.log(p, null) }) {
                Text("Log")
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
            }
        }
    }
}
