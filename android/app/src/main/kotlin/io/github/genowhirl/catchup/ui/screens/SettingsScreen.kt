package io.github.genowhirl.catchup.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.genowhirl.catchup.core.Issue
import io.github.genowhirl.catchup.core.sync.SyncState
import io.github.genowhirl.catchup.core.sync.SyncStatus
import io.github.genowhirl.catchup.data.AppSettings
import io.github.genowhirl.catchup.data.Storage
import io.github.genowhirl.catchup.ui.components.Panel
import io.github.genowhirl.catchup.ui.components.SectionHeader
import io.github.genowhirl.catchup.ui.components.TimeChip
import io.github.genowhirl.catchup.ui.components.minutesLabel
import java.time.DayOfWeek
import java.time.format.TextStyle

class SettingsActions(
    val update: ((AppSettings) -> AppSettings) -> Unit,
    val syncNow: () -> Unit,
    val storage: () -> Unit,
    val import: () -> Unit,
    val backup: () -> Unit,
    val requestNotifications: () -> Unit,
    val requestExactAlarms: () -> Unit,
    val openFile: (String) -> Unit,
)

fun storageLabel(s: Storage): String = when (s) {
    Storage.Local -> "On this phone"
    is Storage.GitHub -> "GitHub · ${s.owner}/${s.repo}"
    is Storage.Drive -> "Google Drive · ${s.folder}"
}

fun syncLabel(s: SyncState): String = when (s.status) {
    SyncStatus.SYNCING -> "Syncing…"
    SyncStatus.OFFLINE -> "Offline" + if (s.pending > 0) " · ${s.pending} change${if (s.pending > 1) "s" else ""} waiting" else ""
    SyncStatus.ERROR -> s.error ?: "Sync problem"
    SyncStatus.IDLE -> s.lastSync?.let { "Up to date · " + android.text.format.DateUtils.getRelativeTimeSpanString(it).toString().lowercase() } ?: "Up to date"
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    sync: SyncState,
    problems: Map<String, List<Issue>>,
    notificationsAllowed: Boolean,
    exactAlarmsAllowed: Boolean,
    actions: SettingsActions,
    contentPadding: PaddingValues = PaddingValues(),
) {
    LazyColumn(contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp + contentPadding.calculateBottomPadding())) {
        item {
            Text("Settings", style = MaterialTheme.typography.displaySmall, modifier = Modifier.statusBarsPadding().padding(top = 28.dp))
        }

        if (!notificationsAllowed || !exactAlarmsAllowed) {
            item {
                Spacer(Modifier.height(16.dp))
                Panel(color = MaterialTheme.colorScheme.errorContainer) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (!notificationsAllowed) "Notifications are off" else "Reminders may be late",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                if (!notificationsAllowed) "CatchUp can't remind you about plans or your weekly planning."
                                else "Allow exact alarms so reminders arrive right on time.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        TextButton(if (!notificationsAllowed) actions.requestNotifications else actions.requestExactAlarms) { Text("Allow") }
                    }
                }
            }
        }

        item { SectionHeader("Your data") }
        item {
            Panel(padding = PaddingValues(vertical = 6.dp)) {
                Row_(
                    if (settings.storage == Storage.Local) Icons.Rounded.CloudOff else Icons.Rounded.Cloud,
                    storageLabel(settings.storage),
                    if (settings.storage == Storage.Local) "Not synced — tap to sync with Google Drive or GitHub" else syncLabel(sync),
                    onClick = actions.storage,
                )
                if (settings.storage != Storage.Local) Row_(Icons.Rounded.Sync, "Sync now", null, onClick = actions.syncNow)
                Row_(Icons.Rounded.Download, "Import people", "Contacts, WhatsApp, Facebook, Instagram", onClick = actions.import)
                Row_(Icons.Rounded.Backup, "Export a backup", "All people as Markdown files in a .zip", onClick = actions.backup)
            }
        }
        if (problems.isNotEmpty()) {
            item { SectionHeader("Files that need attention") }
            item {
                Panel(padding = PaddingValues(vertical = 6.dp)) {
                    problems.forEach { (file, issues) ->
                        Row_(Icons.Rounded.ErrorOutline, file, issues.joinToString("\n") { it.message }, onClick = { actions.openFile(file) })
                    }
                }
            }
        }

        item { SectionHeader("Weekly planning") }
        item {
            Panel {
                Toggle("Ask me to plan my week", "A notification to pick when you're free and who to see.", settings.weeklyPrompt) { v -> actions.update { it.copy(weeklyPrompt = v) } }
                if (settings.weeklyPrompt) {
                    Spacer(Modifier.height(12.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { d ->
                            FilterChip(settings.planningDay == d, { actions.update { it.copy(planningDay = d) } }, { Text(d.getDisplayName(TextStyle.SHORT, java.util.Locale.getDefault())) })
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("At", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.width(12.dp))
                        TimeChip(settings.planningTime, { t -> if (t != null) actions.update { it.copy(planningTime = t) } }, clearable = false)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Catch-ups per week", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (1..7).forEach { n -> FilterChip(settings.weeklyGoal == n, { actions.update { it.copy(weeklyGoal = n) } }, { Text("$n") }) }
                }
            }
        }

        item { SectionHeader("Reminders") }
        item {
            Panel {
                Text("Before a call", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 15, 30, 60).forEach { m -> FilterChip(settings.callReminderMinutes == m, { actions.update { it.copy(callReminderMinutes = m) } }, { Text(minutesLabel(m).removeSuffix(" before")) }) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Before meeting up", style = MaterialTheme.typography.bodyLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 60, 120, 1440).forEach { m -> FilterChip(settings.meetReminderMinutes == m, { actions.update { it.copy(meetReminderMinutes = m) } }, { Text(minutesLabel(m).removeSuffix(" before")) }) }
                }
                Spacer(Modifier.height(12.dp))
                Toggle("Ask how it went", "After each plan, a nudge to note anything worth remembering.", settings.followUps) { v -> actions.update { it.copy(followUps = v) } }
                Spacer(Modifier.height(8.dp))
                Toggle("Morning summary", "Birthdays and plans for the day.", settings.morningDigest) { v -> actions.update { it.copy(morningDigest = v) } }
                if (settings.morningDigest) {
                    TimeChip(settings.digestTime, { t -> if (t != null) actions.update { it.copy(digestTime = t) } }, clearable = false)
                }
            }
        }

        item { SectionHeader("Appearance") }
        item {
            Panel {
                Toggle("Use my wallpaper colours", "Material You colours instead of CatchUp's warm palette.", settings.dynamicColor) { v -> actions.update { it.copy(dynamicColor = v) } }
            }
        }
        item {
            Spacer(Modifier.height(24.dp))
            Text(
                "CatchUp keeps your people as plain Markdown files. Fonts: Fraunces and Manrope (SIL Open Font License).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun Toggle(title: String, text: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!value) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(value, onChange)
    }
}

@Composable
private fun Row_(icon: ImageVector, title: String, text: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (text != null) {
            { Text(text) }
        } else null,
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
