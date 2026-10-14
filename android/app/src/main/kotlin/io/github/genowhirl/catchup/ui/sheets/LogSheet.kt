package io.github.genowhirl.catchup.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.genowhirl.catchup.core.InteractionInput
import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.Plan
import io.github.genowhirl.catchup.core.When
import io.github.genowhirl.catchup.ui.components.DateChip
import io.github.genowhirl.catchup.ui.components.TimeChip
import io.github.genowhirl.catchup.ui.components.kindIcon
import java.time.LocalDate
import java.time.LocalTime

val LOG_KINDS = listOf("Call", "Video call", "Voice message", "In person", "Messages", "Other")

private fun lines(text: String) = text.lines().map { it.trim().removePrefix("-").removePrefix("•").trim() }.filter { it.isNotEmpty() }

/** "How was it?" — a few taps and a couple of lines. */
@Composable
fun LogSheet(person: Person, plan: Plan?, onDismiss: () -> Unit, onSave: (List<Op>, String) -> Unit) {
    val today = LocalDate.now()
    val initialKind = plan?.kind?.let { if (it == "Meet") "In person" else it } ?: "Call"
    var kind by remember { mutableStateOf(initialKind) }
    var date by remember { mutableStateOf(plan?.at?.date?.takeIf { !it.isAfter(today) } ?: today) }
    // The plan's time if there was one, else now (rounded down to 5 minutes).
    var time by remember {
        mutableStateOf<LocalTime?>(plan?.at?.time ?: LocalTime.now().withSecond(0).withNano(0).let { it.withMinute(it.minute / 5 * 5) })
    }
    var place by remember { mutableStateOf(plan?.place.orEmpty()) }
    var notes by remember { mutableStateOf("") }
    var next by remember { mutableStateOf("") }
    val covered = remember { mutableStateListOf<String>() }
    val kinds = (LOG_KINDS + listOfNotNull(initialKind.takeIf { it !in LOG_KINDS })).distinct()
    val remote = kind.lowercase().let { "call" in it || "message" in it || "voice" in it }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Text(
                if (plan != null) "How was it with ${person.shortName}?" else "Log a catch-up with ${person.shortName}",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                kinds.forEach { k ->
                    FilterChip(
                        selected = kind == k,
                        onClick = { kind = k },
                        label = { Text(k) },
                        leadingIcon = { Icon(kindIcon(k), null, Modifier.size(FilterChipDefaults.IconSize)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateChip(date, today, { if (!it.isAfter(today)) date = it })
                TimeChip(time, { time = it })
            }
            if (!remote) {
                OutlinedTextField(
                    place, { place = it },
                    label = { Text("Where") },
                    leadingIcon = { Icon(Icons.Rounded.Place, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                Spacer(Modifier.height(8.dp))
            }
            OutlinedTextField(
                notes, { notes = it },
                label = { Text("What did you talk about?") },
                placeholder = { Text("One thing per line\nNew job at the hospital\nTrip to Lisbon in May") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            if (person.openFollowUps.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("Did you cover these?", style = MaterialTheme.typography.titleSmall)
                person.openFollowUps.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(f in covered, { if (it) covered += f else covered -= f })
                        Text(f, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                next, { next = it },
                label = { Text("Next time, ask about…") },
                placeholder = { Text("How the interview went") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val entry = InteractionInput(
                        date = date.toString(),
                        time = time?.format(When.HH_MM),
                        kind = kind,
                        place = place.trim().takeIf { it.isNotEmpty() && !remote },
                        notes = lines(notes),
                        followUps = lines(next),
                    )
                    val ops = covered.map { Op.SetFollowUp(person.path, it, true) } + Op.Log(person.path, entry, plan?.key)
                    onSave(ops, "Logged ${kind.lowercase()} with ${person.shortName}")
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
