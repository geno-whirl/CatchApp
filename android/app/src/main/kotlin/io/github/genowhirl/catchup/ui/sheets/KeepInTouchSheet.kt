package io.github.genowhirl.catchup.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.genowhirl.catchup.core.Cadence
import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.Priority
import io.github.genowhirl.catchup.core.effectiveCadence
import io.github.genowhirl.catchup.core.toJson
import io.github.genowhirl.catchup.ui.components.DateChip
import java.time.LocalDate

val CADENCE_LABELS = mapOf(
    "weekly" to "Every week",
    "biweekly" to "Every 2 weeks",
    "monthly" to "Every month",
    "bimonthly" to "Every 2 months",
    "quarterly" to "Every 3 months",
    "biannually" to "Twice a year",
    "yearly" to "Once a year",
)

fun cadenceLabel(c: Cadence?): String = c?.let { CADENCE_LABELS[it.label] ?: "Every ${it.label}" } ?: "Not set"

/** Importance, how often to catch up, and (optionally) when next. */
@Composable
fun KeepInTouchSheet(person: Person, onDismiss: () -> Unit, onSave: (Op, String) -> Unit) {
    val today = LocalDate.now()
    var priority by remember { mutableStateOf(person.priority) }
    var cadence by remember { mutableStateOf(person.cadence?.label) }
    var next by remember { mutableStateOf(person.nextContact) }
    val suggested = person.copy(priority = priority, priorityIsSet = true, cadence = null).effectiveCadence()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
            Text("Keep in touch with ${person.shortName}", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))
            Text("How important?", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Priority.entries.forEachIndexed { i, p ->
                    SegmentedButton(
                        selected = priority == p,
                        onClick = { priority = p },
                        shape = SegmentedButtonDefaults.itemShape(i, Priority.entries.size),
                    ) { Text(p.label) }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("How often?", style = MaterialTheme.typography.titleSmall)
            Text(
                "A suggestion — CatchUp nudges you when it's been longer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(cadence == null, { cadence = null }, { Text(suggested?.let { "Suggested: ${cadenceLabel(it).lowercase()}" } ?: "No reminder") })
                CADENCE_LABELS.forEach { (key, label) -> FilterChip(cadence == key, { cadence = key }, { Text(label) }) }
            }
            Spacer(Modifier.height(16.dp))
            Text("Next time to get in touch", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (next != null) {
                    DateChip(next!!, today, { next = it })
                    TextButton(onClick = { next = null }) { Text("Clear") }
                } else {
                    TextButton(onClick = { next = today.plusWeeks(2) }) { Text("Pick a date") }
                    Text("· otherwise based on how often", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    onSave(
                        Op.SetFields(
                            person.path,
                            mapOf(
                                "priority" to priority.key.toJson(),
                                "cadence" to cadence.toJson(),
                                "next_contact" to next?.toString().toJson(),
                            ),
                        ),
                        "Saved",
                    )
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Save") }
            Spacer(Modifier.height(16.dp))
        }
    }
}
