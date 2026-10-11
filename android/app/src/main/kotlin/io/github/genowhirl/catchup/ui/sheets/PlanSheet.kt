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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.Plan
import io.github.genowhirl.catchup.core.PlanInput
import io.github.genowhirl.catchup.core.When
import io.github.genowhirl.catchup.core.formatRemind
import io.github.genowhirl.catchup.ui.components.DateChip
import io.github.genowhirl.catchup.ui.components.TimeChip
import io.github.genowhirl.catchup.ui.components.kindIcon
import io.github.genowhirl.catchup.ui.components.minutesLabel
import java.time.LocalDate
import java.time.LocalTime

val PLAN_KINDS = listOf("Call", "Video call", "Coffee", "Lunch", "Dinner", "Walk", "Visit")
private val REMIND_OPTIONS = listOf(15, 60, 120, 1440, 0)

/** Schedule a catch-up — tomorrow or months ahead — with a reminder. */
@Composable
fun PlanSheet(
    person: Person,
    existing: Plan?,
    defaultCallMinutes: Int,
    defaultMeetMinutes: Int,
    onDismiss: () -> Unit,
    onSave: (List<Op>, String, Plan) -> Unit,
    onAddToCalendar: (Plan) -> Unit,
    initialDate: LocalDate? = null,
    initialTime: LocalTime? = null,
    initialKind: String? = null,
) {
    val today = LocalDate.now()
    var kind by remember { mutableStateOf(existing?.kind ?: initialKind ?: "Call") }
    var date by remember { mutableStateOf(existing?.at?.date ?: initialDate ?: today.plusDays(1)) }
    var time by remember { mutableStateOf<LocalTime?>(if (existing != null) existing.at.time else initialTime ?: LocalTime.of(19, 0)) }
    var place by remember { mutableStateOf(existing?.place.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.notes.orEmpty()) }
    var remind by remember { mutableStateOf<Int?>(existing?.remindMinutes) }
    var calendar by remember { mutableStateOf(false) }
    val isCall = kind.lowercase().let { "call" in it }
    val defaultRemind = if (time == null) null else if (isCall) defaultCallMinutes else defaultMeetMinutes
    val kinds = (PLAN_KINDS + listOfNotNull(kind.takeIf { it !in PLAN_KINDS })).distinct()

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).navigationBarsPadding().imePadding(),
        ) {
            Text(if (existing != null) "Change plan" else "Plan a catch-up with ${person.shortName}", style = MaterialTheme.typography.headlineSmall)
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
                DateChip(date, today, { if (!it.isBefore(today)) date = it })
                TimeChip(time, { time = it })
            }
            if (!isCall) {
                OutlinedTextField(
                    place, { place = it },
                    label = { Text("Where") },
                    leadingIcon = { Icon(Icons.Rounded.Place, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                Text("Reminder", style = MaterialTheme.typography.titleSmall)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = remind == null, onClick = { remind = null }, label = {
                    Text(defaultRemind?.let { "Default (${minutesLabel(it)})" } ?: "Morning of the day")
                })
                REMIND_OPTIONS.filter { it != defaultRemind }.forEach { m ->
                    FilterChip(selected = remind == m, onClick = { remind = m }, label = { Text(minutesLabel(m)) })
                }
            }
            OutlinedTextField(
                notes, { notes = it },
                label = { Text("Notes (optional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Also add to my calendar", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(calendar, { calendar = it })
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    val at = When(date, time)
                    val input = PlanInput(at.format(), kind, place.trim().ifEmpty { null }?.takeIf { !isCall }, remind?.let(::formatRemind), notes.trim().ifEmpty { null })
                    val plan = Plan(at, kind, input.place, remind, input.notes)
                    onSave(listOf(Op.SavePlan(person.path, input, existing?.key)), "Planned ${kind.lowercase()} with ${person.shortName}", plan)
                    if (calendar) onAddToCalendar(plan)
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(if (existing != null) "Save changes" else "Save plan") }
            if (existing != null) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onAddToCalendar(existing) }, Modifier.weight(1f)) { Text("Add to calendar") }
                    TextButton(
                        onClick = { onSave(listOf(Op.RemovePlan(person.path, existing.key)), "Plan cancelled", existing) },
                        Modifier.weight(1f),
                    ) { Text("Cancel plan", color = MaterialTheme.colorScheme.error) }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
