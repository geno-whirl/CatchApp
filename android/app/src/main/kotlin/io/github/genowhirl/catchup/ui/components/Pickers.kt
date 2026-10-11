package io.github.genowhirl.catchup.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.genowhirl.catchup.core.When
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** A chip showing a date; tapping opens a date picker. */
@Composable
fun DateChip(date: LocalDate, today: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { open = true },
        label = { Text(relativeDay(date, today).let { if (it.length < 10) "$it, ${shortDate(date)}" else it }) },
        leadingIcon = { Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(AssistChipDefaults.IconSize)) },
        modifier = modifier,
    )
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/** A chip showing an optional time; tapping opens a clock picker. */
@Composable
fun TimeChip(time: LocalTime?, onChange: (LocalTime?) -> Unit, modifier: Modifier = Modifier, defaultTime: LocalTime = LocalTime.of(19, 0)) {
    var open by remember { mutableStateOf(false) }
    val context = LocalContext.current
    AssistChip(
        onClick = { open = true },
        label = { Text(time?.format(When.HH_MM) ?: "Add time") },
        leadingIcon = { Icon(Icons.Rounded.Schedule, null, Modifier.size(AssistChipDefaults.IconSize)) },
        trailingIcon = if (time != null) {
            { Icon(Icons.Rounded.Close, "Remove time", Modifier.size(AssistChipDefaults.IconSize).clickableNoRipple { onChange(null) }) }
        } else null,
        modifier = modifier,
    )
    if (open) {
        val start = time ?: defaultTime
        val state = rememberTimePickerState(start.hour, start.minute, DateFormat.is24HourFormat(context))
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    onChange(LocalTime.of(state.hour, state.minute))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
            text = { TimePicker(state) },
        )
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
