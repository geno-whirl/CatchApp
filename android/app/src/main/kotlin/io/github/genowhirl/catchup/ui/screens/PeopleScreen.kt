package io.github.genowhirl.catchup.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.genowhirl.catchup.core.ContactStatus
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.Priority
import io.github.genowhirl.catchup.core.contactStatus
import io.github.genowhirl.catchup.core.daysBetween
import io.github.genowhirl.catchup.core.effectiveDueDate
import io.github.genowhirl.catchup.core.formatDaysAgo
import io.github.genowhirl.catchup.ui.components.Avatar
import io.github.genowhirl.catchup.ui.components.EmptyState
import io.github.genowhirl.catchup.ui.components.StatusPill
import io.github.genowhirl.catchup.ui.components.inDays
import io.github.genowhirl.catchup.ui.components.relativeDay
import java.time.LocalDate

private enum class Sort(val label: String) { NAME("Name"), DUE("Who's due"), BIRTHDAY("Next birthday"), RECENT("Recently in touch") }

private fun matches(p: Person, q: String): Boolean {
    if (q.isBlank()) return true
    val hay = (listOfNotNull(p.name, p.nickname, p.relationship, p.location) + p.tags + p.family.map { it.name }).joinToString(" ").lowercase()
    return q.lowercase().split(" ").filter { it.isNotBlank() }.all { it in hay }
}

/** "Overdue", "Due in 3 days", "Next: 12 Nov" — the one thing to know about someone. */
fun statusText(p: Person, today: LocalDate): Pair<ContactStatus, String>? {
    p.upcomingPlans(today).firstOrNull()?.let { return ContactStatus.OK to "Planned ${relativeDay(it.at.date, today)}" }
    val due = p.effectiveDueDate(today) ?: return null
    val status = p.contactStatus(today)
    val days = daysBetween(today, due)
    return status to when (status) {
        ContactStatus.OVERDUE -> if (p.lastContacted == null) "Say hi" else "Overdue"
        ContactStatus.DUE_SOON -> if (days <= 0) "Due today" else "Due ${inDays(days)}"
        else -> "Next ${inDays(days)}"
    }
}

@Composable
fun PeopleScreen(
    people: List<Person>,
    today: LocalDate,
    onOpen: (String) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(Sort.NAME) }
    var sortMenu by remember { mutableStateOf(false) }

    val tags = remember(people) {
        (people.mapNotNull { it.relationship } + people.flatMap { it.tags }).distinct().sorted()
    }
    val visible = people
        .filter { matches(it, query) }
        .filter { p ->
            when (val f = filter) {
                null -> true
                "★" -> p.priority == Priority.HIGH
                "due" -> p.contactStatus(today).let { it == ContactStatus.OVERDUE || it == ContactStatus.DUE_SOON }
                else -> p.relationship == f || f in p.tags
            }
        }
        .let { list ->
            when (sort) {
                Sort.NAME -> list.sortedBy { it.name.lowercase() }
                Sort.DUE -> list.sortedBy { it.effectiveDueDate(today) ?: LocalDate.MAX }
                Sort.BIRTHDAY -> list.sortedBy { it.birthday?.daysUntil(today) ?: Int.MAX_VALUE }
                Sort.RECENT -> list.sortedByDescending { it.lastContacted ?: LocalDate.MIN }
            }
        }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(contentPadding = PaddingValues(bottom = 96.dp + contentPadding.calculateBottomPadding())) {
            item {
                Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 12.dp, top = 28.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("People", style = MaterialTheme.typography.displaySmall, modifier = Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort") }
                            DropdownMenu(sortMenu, { sortMenu = false }) {
                                Sort.entries.forEach { s ->
                                    DropdownMenuItem(text = { Text(s.label) }, onClick = { sort = s; sortMenu = false })
                                }
                            }
                        }
                    }
                    Text(
                        "${people.size} ${if (people.size == 1) "person" else "people"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    TextField(
                        query, { query = it },
                        placeholder = { Text("Search names, places, family…") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = if (query.isNotEmpty()) { { IconButton({ query = "" }) { Icon(Icons.Rounded.Close, "Clear") } } } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { FilterChip(filter == null, { filter = null }, { Text("All") }) }
                    item { FilterChip(filter == "due", { filter = if (filter == "due") null else "due" }, { Text("Due") }) }
                    item { FilterChip(filter == "★", { filter = if (filter == "★") null else "★" }, { Text("High priority") }) }
                    items(tags) { t -> FilterChip(filter == t, { filter = if (filter == t) null else t }, { Text(t) }) }
                }
                Spacer(Modifier.height(8.dp))
            }
            if (people.isEmpty()) {
                item {
                    EmptyState(Icons.Rounded.Groups, "No one here yet", "Import from your contacts or add people one by one.") {
                        androidx.compose.material3.Button(onClick = onImport) { Text("Import contacts") }
                    }
                }
            } else if (visible.isEmpty()) {
                item { Text("No one matches.", Modifier.padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(visible, key = { it.id }) { p -> PersonRow(p, today) { onOpen(p.id) } }
        }
        ExtendedFloatingActionButton(
            onClick = onAdd,
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Add person") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp + contentPadding.calculateBottomPadding()),
        )
    }
}

@Composable
fun PersonRow(p: Person, today: LocalDate, onClick: () -> Unit) {
    val status = statusText(p, today)
    val subtitle = listOfNotNull(
        p.nickname?.let { "“$it”" },
        p.relationship,
        p.lastContacted?.let { formatDaysAgo(daysBetween(it, today)).replaceFirstChar { c -> c.uppercase() } } ?: "Not in touch yet",
    ).joinToString(" · ")
    ListItem(
        headlineContent = { Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Avatar(p.name, 48.dp, p.priority) },
        trailingContent = if (status != null) {
            { StatusPill(status.first, status.second) }
        } else null,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    )
}
