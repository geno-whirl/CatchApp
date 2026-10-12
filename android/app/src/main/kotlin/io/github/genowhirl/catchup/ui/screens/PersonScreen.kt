package io.github.genowhirl.catchup.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Handshake
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.genowhirl.catchup.core.ContactStatus
import io.github.genowhirl.catchup.core.Interaction
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.Plan
import io.github.genowhirl.catchup.core.Priority
import io.github.genowhirl.catchup.core.contactStatus
import io.github.genowhirl.catchup.core.daysBetween
import io.github.genowhirl.catchup.core.effectiveCadence
import io.github.genowhirl.catchup.core.effectiveDueDate
import io.github.genowhirl.catchup.core.formatDaysAgo
import io.github.genowhirl.catchup.core.planTitle
import io.github.genowhirl.catchup.core.When
import io.github.genowhirl.catchup.ui.Reach
import io.github.genowhirl.catchup.ui.components.ActionButton
import io.github.genowhirl.catchup.ui.components.Avatar
import io.github.genowhirl.catchup.ui.components.DateTile
import io.github.genowhirl.catchup.ui.components.MarkdownNotes
import io.github.genowhirl.catchup.ui.components.Panel
import io.github.genowhirl.catchup.ui.components.Pill
import io.github.genowhirl.catchup.ui.components.SectionHeader
import io.github.genowhirl.catchup.ui.components.StatusPill
import io.github.genowhirl.catchup.ui.components.birthdayText
import io.github.genowhirl.catchup.ui.components.inDays
import io.github.genowhirl.catchup.ui.components.kindIcon
import io.github.genowhirl.catchup.ui.components.longDate
import io.github.genowhirl.catchup.ui.components.minutesLabel
import io.github.genowhirl.catchup.ui.components.planText
import io.github.genowhirl.catchup.ui.components.relativeDay
import io.github.genowhirl.catchup.ui.sheets.cadenceLabel
import io.github.genowhirl.catchup.ui.theme.CatchUpTheme
import java.time.LocalDate

class PersonActions(
    val back: () -> Unit,
    val edit: () -> Unit,
    val editMarkdown: () -> Unit,
    val delete: () -> Unit,
    val log: (Plan?) -> Unit,
    val plan: (Plan?) -> Unit,
    val keepInTouch: () -> Unit,
    val toggleFollowUp: (String, Boolean) -> Unit,
    val addFollowUp: (String) -> Unit,
    val call: (String) -> Unit,
    val text: (String) -> Unit,
    val whatsapp: (String) -> Unit,
    val email: (String) -> Unit,
    val openContact: (String, String) -> Unit,
)

@Composable
fun PersonScreen(p: Person, today: LocalDate, actions: PersonActions) {
    var menu by remember { mutableStateOf(false) }
    var showAllInteractions by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    val reach = Reach.of(p)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                TopAppBar(
                    title = {},
                    navigationIcon = { IconButton(actions.back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                    actions = {
                        IconButton(actions.edit) { Icon(Icons.Rounded.Edit, "Edit") }
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem({ Text("Edit as Markdown") }, { menu = false; actions.editMarkdown() }, leadingIcon = { Icon(Icons.Rounded.Code, null) })
                                DropdownMenuItem({ Text("Remove") }, { menu = false; actions.delete() }, leadingIcon = { Icon(Icons.Rounded.Delete, null) })
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                )
            }
            item { Header(p) }
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    reach.phone?.let { ActionButton(Icons.Rounded.Call, "Call", { actions.call(it) }) }
                    reach.phone?.let { ActionButton(Icons.Rounded.Sms, "Text", { actions.text(it) }) }
                    reach.whatsapp?.let { ActionButton(Icons.AutoMirrored.Rounded.Chat, "WhatsApp", { actions.whatsapp(it) }) }
                    reach.email?.let { ActionButton(Icons.Rounded.Email, "Email", { actions.email(it) }) }
                    ActionButton(Icons.Rounded.EventAvailable, "Plan", { actions.plan(null) }, emphasized = true)
                }
            }
            item { Column(Modifier.padding(horizontal = 20.dp)) { KeepInTouch(p, today, actions.keepInTouch) } }

            val plans = p.upcomingPlans(today) + p.plans.filter { it.at.date.isBefore(today) }
            if (plans.isNotEmpty()) {
                item { SectionHeader("Plans", Modifier.padding(horizontal = 20.dp)) }
                items(plans, key = { "plan:${it.key}" }) { plan ->
                    Panel(Modifier.padding(horizontal = 20.dp, vertical = 4.dp), onClick = { if (plan.at.date.isBefore(today)) actions.log(plan) else actions.plan(plan) }, padding = PaddingValues(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DateTile(plan.at.date, highlight = plan.at.date == today)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(planTitle(p, plan), style = MaterialTheme.typography.titleMedium)
                                Text(planText(plan, today), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (plan.at.date.isBefore(today)) {
                                    Text("Tap to add notes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                } else plan.remindMinutes?.let {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.NotificationsActive, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(4.dp))
                                        Text(minutesLabel(it), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val open = p.followUps.filter { !it.done }
            val done = p.followUps.filter { it.done }
            item { SectionHeader("Next time, ask about", Modifier.padding(horizontal = 20.dp)) }
            item {
                Panel(Modifier.padding(horizontal = 20.dp), padding = PaddingValues(vertical = 8.dp, horizontal = 8.dp)) {
                    open.forEach { f -> CheckRow(f.text, false) { actions.toggleFollowUp(f.text, true) } }
                    if (showDone) done.forEach { f -> CheckRow(f.text, true) { actions.toggleFollowUp(f.text, false) } }
                    AddFollowUpField(actions.addFollowUp)
                    if (done.isNotEmpty()) {
                        TextButton({ showDone = !showDone }) { Text(if (showDone) "Hide done" else "Show ${done.size} done") }
                    }
                }
            }

            item {
                SectionHeader("Interactions", Modifier.padding(horizontal = 20.dp)) {
                    if (p.interactions.isNotEmpty()) Text("${p.interactions.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (p.interactions.isEmpty()) {
                item {
                    Panel(Modifier.padding(horizontal = 20.dp)) {
                        Text("Nothing logged yet. After your next call or meetup, tap “Log catch-up” — it takes a few seconds.", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            val shown = if (showAllInteractions) p.interactions else p.interactions.take(5)
            items(shown.size) { i -> TimelineEntry(shown[i], today, isLast = i == shown.lastIndex) }
            if (p.interactions.size > 5 && !showAllInteractions) {
                item { TextButton({ showAllInteractions = true }, Modifier.padding(horizontal = 20.dp)) { Text("Show all ${p.interactions.size}") } }
            }

            item { SectionHeader("About", Modifier.padding(horizontal = 20.dp)) }
            item { Column(Modifier.padding(horizontal = 20.dp)) { About(p, today, actions) } }
        }
        ExtendedFloatingActionButton(
            onClick = { actions.log(null) },
            icon = { Icon(Icons.Rounded.EditNote, null) },
            text = { Text("Log catch-up") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp).padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun Header(p: Person) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Avatar(p.name, 104.dp, p.priority)
        Spacer(Modifier.height(16.dp))
        Text(p.name, style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
        p.nickname?.let { Text("“$it”", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        val sub = listOfNotNull(p.relationship?.replaceFirstChar { it.uppercase() }, p.location).joinToString(" · ")
        if (sub.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(sub, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
        if (p.tags.isNotEmpty() || p.priorityIsSet) {
            Spacer(Modifier.height(12.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (p.priorityIsSet) {
                    val c = when (p.priority) {
                        Priority.HIGH -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                        Priority.MEDIUM -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
                        Priority.LOW -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    Pill("${p.priority.label} priority", c.first, c.second)
                }
                p.tags.forEach { Pill(it, MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun KeepInTouch(p: Person, today: LocalDate, onEdit: () -> Unit) {
    val due = p.effectiveDueDate(today)
    val status = p.contactStatus(today)
    Panel(onClick = onEdit, color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Handshake, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Keep in touch", style = MaterialTheme.typography.titleMedium)
                val cadence = p.effectiveCadence()
                Text(
                    when {
                        p.cadence != null -> cadenceLabel(p.cadence)
                        cadence != null -> "${cadenceLabel(cadence)} (suggested)"
                        else -> "Tap to set how often"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Rounded.Tune, "Change", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(12.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("Last contact", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    p.lastContacted?.let { formatDaysAgo(daysBetween(it, today)).replaceFirstChar { c -> c.uppercase() } } ?: "Not yet",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("Next", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (due == null) Text("—", style = MaterialTheme.typography.titleSmall)
                else {
                    val days = daysBetween(today, due)
                    StatusPill(
                        if (status == ContactStatus.NONE) ContactStatus.OK else status,
                        when {
                            days < 0 -> "Overdue · ${relativeDay(due, today)}"
                            days == 0 -> "Today"
                            else -> "${relativeDay(due, today)} · ${inDays(days)}"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AddFollowUpField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val submit = {
        if (text.isNotBlank()) onAdd(text.trim())
        text = ""
    }
    androidx.compose.material3.TextField(
        text, { text = it },
        placeholder = { Text("Add something to ask about…") },
        leadingIcon = { Icon(Icons.Rounded.Add, null) },
        trailingIcon = if (text.isNotBlank()) {
            { IconButton(onClick = submit) { Icon(Icons.Rounded.Check, "Add") } }
        } else null,
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Sentences,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { submit() }),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CheckRow(text: String, done: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onToggle).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
            null,
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
        )
    }
}

@Composable
private fun TimelineEntry(e: Interaction, today: LocalDate, isLast: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
        Column(Modifier.width(40.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(kindIcon(e.kind), null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            if (!isLast) Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).padding(bottom = 20.dp)) {
            Text(
                listOfNotNull(e.kind, e.place).joinToString(" · ").ifEmpty { "Catch-up" },
                style = MaterialTheme.typography.titleSmall,
            )
            val ago = daysBetween(e.date, today)
            Text(
                longDate(e.date) + (e.at.time?.let { " · ${it.format(When.HH_MM)}" } ?: "") + " · " + formatDaysAgo(ago),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (e.notes.isNotEmpty() || e.followUps.isNotEmpty()) Spacer(Modifier.height(6.dp))
            e.notes.forEach { n ->
                Row(Modifier.padding(vertical = 1.dp)) {
                    Text("•", color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(io.github.genowhirl.catchup.ui.components.inlineMarkdown(n), style = MaterialTheme.typography.bodyMedium)
                }
            }
            e.followUps.forEach { f ->
                Row(Modifier.padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (f.done) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text("Ask: ${f.text}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun About(p: Person, today: LocalDate, actions: PersonActions) {
    Panel(padding = PaddingValues(vertical = 8.dp, horizontal = 18.dp)) {
        p.birthday?.let { b ->
            val days = b.daysUntil(today)
            InfoRow(Icons.Rounded.Cake, "Birthday", birthdayText(b), (b.upcomingAge(today)?.let { "Turns $it " } ?: "") + inDays(days), highlight = days <= 14)
        }
        p.howWeMet?.let { InfoRow(Icons.Rounded.Handshake, "How we met", it) }
        p.contact.forEach { (k, v) ->
            InfoRow(contactIcon(k), k.replace('_', ' ').replaceFirstChar { it.uppercase() }, v, onClick = { actions.openContact(k, v) })
        }
        if (p.location != null) InfoRow(Icons.Rounded.Place, "Lives in", p.location!!)
        p.language?.let { InfoRow(Icons.Rounded.Translate, "Language", io.github.genowhirl.catchup.core.Languages.displayName(it)) }
        if (p.birthday == null && p.howWeMet == null && p.contact.isEmpty() && p.location == null) {
            TextButton(actions.edit) { Text("Add birthday, phone, how you met…") }
        }
    }
    if (p.family.isNotEmpty()) {
        SectionHeader("Family")
        Panel(padding = PaddingValues(vertical = 8.dp, horizontal = 18.dp)) {
            p.family.forEach { m ->
                Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m.name, 40.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall)
                        val sub = listOfNotNull(m.relation?.replaceFirstChar { it.uppercase() }, m.birthday?.let { "🎂 ${birthdayText(it)}" }).joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        m.notes?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
    if (p.notes.isNotBlank()) {
        SectionHeader("Notes")
        Panel { MarkdownNotes(p.notes) }
    }
    Spacer(Modifier.height(16.dp))
    Text(p.path, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
}

private fun contactIcon(key: String) = when {
    key.startsWith("phone") || key.startsWith("mobile") -> Icons.Rounded.Call
    key.startsWith("email") -> Icons.Rounded.Email
    else -> Icons.AutoMirrored.Rounded.Chat
}

@Composable
private fun InfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    extra: String? = null,
    highlight: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyLarge, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        extra?.let {
            val c = CatchUpTheme.colors
            Pill(it, if (highlight) c.birthday else MaterialTheme.colorScheme.surfaceContainerHigh, if (highlight) c.onBirthday else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
