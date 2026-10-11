package io.github.genowhirl.catchup.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddToDrive
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.genowhirl.catchup.ui.components.Avatar
import io.github.genowhirl.catchup.ui.components.Panel
import io.github.genowhirl.catchup.ui.components.TimeChip
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

class OnboardingActions(
    val requestNotifications: () -> Unit,
    val connectDrive: () -> Unit,
    val openStorage: () -> Unit,
    val importContacts: () -> Unit,
    val addPerson: () -> Unit,
    val tryExamples: () -> Unit,
    val finish: (name: String, day: DayOfWeek, time: LocalTime, goal: Int) -> Unit,
)

@Composable
fun OnboardingScreen(notificationsAllowed: Boolean, driveConnected: Boolean, actions: OnboardingActions) {
    var step by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var day by remember { mutableStateOf(DayOfWeek.SUNDAY) }
    var time by remember { mutableStateOf(LocalTime.of(18, 0)) }
    var goal by remember { mutableIntStateOf(3) }
    val done = { actions.finish(name.trim(), day, time, goal) }

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier.weight(1f).height(4.dp).clip(CircleShape)
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            }
        }
        AnimatedContent(
            step,
            transitionSpec = { (slideInHorizontally { it / 3 } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 } + fadeOut()) },
            label = "onboarding",
            modifier = Modifier.weight(1f),
        ) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                when (s) {
                    0 -> {
                        Spacer(Modifier.height(24.dp))
                        Hero()
                        Spacer(Modifier.height(32.dp))
                        Text("Stay close to the people who matter", style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Birthdays, little details, a weekly moment to plan who to see — and a nudge when it's been too long.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(24.dp))
                        OutlinedTextField(
                            name, { name = it },
                            label = { Text("What should we call you?") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    1 -> {
                        Spacer(Modifier.height(16.dp))
                        Text("Your weekly moment", style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Once a week CatchUp asks when you're free and suggests who to see or call. It reminds you before each plan and asks how it went after.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(24.dp))
                        Text("Plan on", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DayOfWeek.entries.forEach { d -> FilterChip(day == d, { day = d }, { Text(d.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }) }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("at", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.width(12.dp))
                            TimeChip(time, { if (it != null) time = it })
                        }
                        Spacer(Modifier.height(16.dp))
                        Text("Catch-ups per week", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..7).forEach { n -> FilterChip(goal == n, { goal = n }, { Text("$n") }) }
                        }
                        Spacer(Modifier.height(24.dp))
                        if (notificationsAllowed) {
                            Text("✓ Notifications are on", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.titleSmall)
                        } else {
                            FilledTonalButton(onClick = actions.requestNotifications) {
                                Icon(Icons.Rounded.Notifications, null)
                                Spacer(Modifier.width(8.dp))
                                Text("Allow notifications")
                            }
                        }
                    }
                    2 -> {
                        Spacer(Modifier.height(16.dp))
                        Text("Where should your people live?", style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Each person is a simple Markdown file — yours to keep, back up, or let an AI assistant help with. You can change this any time.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))
                        Choice(Icons.Rounded.PhoneAndroid, "On this phone", "Simplest. Nothing leaves your phone.", selected = !driveConnected) { step = 3 }
                        Spacer(Modifier.height(10.dp))
                        Choice(Icons.Rounded.AddToDrive, "Google Drive", if (driveConnected) "Connected ✓" else "Synced and backed up across devices.", selected = driveConnected) {
                            if (driveConnected) step = 3 else actions.connectDrive()
                        }
                        TextButton(onClick = actions.openStorage) { Text("Use a GitHub repository instead") }
                    }
                    else -> {
                        Spacer(Modifier.height(16.dp))
                        Text("Who matters to you?", style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Start with a handful of people. You can set how often you'd like to be in touch with each of them.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(20.dp))
                        Choice(Icons.Rounded.Contacts, "Import from contacts", "Pick people from your phone, WhatsApp, Facebook…") { done(); actions.importContacts() }
                        Spacer(Modifier.height(10.dp))
                        Choice(Icons.Rounded.PersonAdd, "Add someone", "Start with one person.") { done(); actions.addPerson() }
                        Spacer(Modifier.height(10.dp))
                        Choice(Icons.Rounded.Science, "Try with examples", "A few made-up people to explore with.") { done(); actions.tryExamples() }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) TextButton({ step-- }) { Text("Back") }
            Spacer(Modifier.weight(1f))
            if (step < 3) Button({ step++ }, Modifier.height(52.dp)) { Text("Continue") }
            else TextButton(done) { Text("Skip for now") }
        }
    }
}

@Composable
private fun Hero() {
    // A small cluster of friendly faces.
    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.size(170.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer))
        Avatar("Maya Patel", 72.dp, modifier = Modifier.offset((-52).dp, (-30).dp))
        Avatar("Joe Moreno", 60.dp, modifier = Modifier.offset(56.dp, (-40).dp))
        Avatar("Linda Moreno", 84.dp, modifier = Modifier.offset(8.dp, 34.dp).border(4.dp, MaterialTheme.colorScheme.background, CircleShape))
        Avatar("Jonas Weber", 48.dp, modifier = Modifier.offset((-70).dp, 46.dp))
        Avatar("Ade Okafor", 44.dp, modifier = Modifier.offset(80.dp, 40.dp))
    }
}

@Composable
private fun Choice(icon: ImageVector, title: String, text: String, selected: Boolean = false, onClick: () -> Unit) {
    Panel(onClick = onClick, color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Start)
            }
        }
    }
}
