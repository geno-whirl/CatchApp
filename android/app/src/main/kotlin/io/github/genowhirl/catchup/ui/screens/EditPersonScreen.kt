package io.github.genowhirl.catchup.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.genowhirl.catchup.core.Birthday
import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.Priority
import io.github.genowhirl.catchup.core.newPersonFile
import io.github.genowhirl.catchup.core.personPath
import io.github.genowhirl.catchup.core.slugify
import io.github.genowhirl.catchup.core.toJson
import io.github.genowhirl.catchup.ui.components.DateChip
import io.github.genowhirl.catchup.ui.components.SectionHeader
import io.github.genowhirl.catchup.ui.sheets.CADENCE_LABELS
import java.time.LocalDate

private class FamilyRow(name: String, relation: String, birthday: String) {
    var name by mutableStateOf(name)
    var relation by mutableStateOf(relation)
    var birthday by mutableStateOf(birthday)
}

private val RELATIONSHIPS = listOf("family", "friend", "partner", "colleague", "neighbour")

/** Add someone new, or edit the basics. Anything else lives in "Edit as Markdown". */
@Composable
fun EditPersonScreen(person: Person?, onBack: () -> Unit, onSave: (List<Op>, String, String) -> Unit) {
    val today = LocalDate.now()
    var name by remember { mutableStateOf(person?.name.orEmpty()) }
    var nickname by remember { mutableStateOf(person?.nickname.orEmpty()) }
    var relationship by remember { mutableStateOf(person?.relationship.orEmpty()) }
    var priority by remember { mutableStateOf(person?.priority ?: Priority.MEDIUM) }
    var cadence by remember { mutableStateOf(person?.cadence?.label) }
    var birthday by remember { mutableStateOf(person?.birthday?.let { LocalDate.of(it.year ?: 2000, it.month, it.day) }) }
    var noYear by remember { mutableStateOf(person?.birthday?.let { it.year == null } ?: false) }
    var phone by remember { mutableStateOf(person?.contact?.get("phone").orEmpty()) }
    var email by remember { mutableStateOf(person?.contact?.get("email").orEmpty()) }
    var location by remember { mutableStateOf(person?.location.orEmpty()) }
    var tags by remember { mutableStateOf(person?.tags?.joinToString(", ").orEmpty()) }
    var howWeMet by remember { mutableStateOf(person?.howWeMet.orEmpty()) }
    var notes by remember { mutableStateOf(person?.notes.orEmpty()) }
    val family = remember {
        mutableStateListOf<FamilyRow>().apply { person?.family?.forEach { add(FamilyRow(it.name, it.relation.orEmpty(), it.birthday?.format().orEmpty())) } }
    }

    fun save() {
        val bday = birthday?.let { if (noYear) Birthday(it.monthValue, it.dayOfMonth) else Birthday(it.monthValue, it.dayOfMonth, it.year) }?.format()
        val tagList = tags.split(",").map { it.trim().lowercase().replace(Regex("\\s+"), "-") }.filter { it.isNotEmpty() }
        val familyList = family.filter { it.name.isNotBlank() }.map {
            linkedMapOf("name" to it.name.trim(), "relation" to it.relation.trim().ifEmpty { null }, "birthday" to it.birthday.trim().ifEmpty { null })
        }
        val contact = LinkedHashMap(person?.contact ?: emptyMap()).apply {
            if (phone.isBlank()) remove("phone") else put("phone", phone.trim())
            if (email.isBlank()) remove("email") else put("email", email.trim())
        }
        val fields = linkedMapOf<String, Any?>(
            "name" to name.trim(),
            "nickname" to nickname.trim(),
            "relationship" to relationship.trim(),
            "priority" to priority.key.takeIf { person?.priorityIsSet == true || priority != Priority.MEDIUM },
            "tags" to tagList,
            "birthday" to bday,
            "location" to location.trim(),
            "contact" to contact,
            "family" to familyList,
            "how_we_met" to howWeMet.trim(),
            "cadence" to cadence,
        )
        if (person == null) {
            val path = personPath(slugify(name))
            onSave(listOf(Op.Create(path, newPersonFile(fields, notes))), "Added ${name.trim()}", path)
        } else {
            val ops = mutableListOf<Op>(Op.SetFields(person.path, fields.mapValues { (_, v) -> v.toJson() }))
            if (notes.trim() != person.notes.trim()) ops += Op.SetNotes(person.path, notes)
            onSave(ops, "Saved", person.path)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (person == null) "Add someone" else "Edit ${person.shortName}") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = { TextButton(onClick = ::save, enabled = name.isNotBlank()) { Text("Save") } },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val words = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
            OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = words)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(nickname, { nickname = it }, label = { Text("Nickname") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = words)
                OutlinedTextField(relationship, { relationship = it }, label = { Text("Relationship") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RELATIONSHIPS.forEach { r -> FilterChip(relationship == r, { relationship = if (relationship == r) "" else r }, { Text(r) }) }
            }

            SectionHeader("Keep in touch")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Priority.entries.forEachIndexed { i, p ->
                    SegmentedButton(priority == p, { priority = p }, SegmentedButtonDefaults.itemShape(i, Priority.entries.size)) { Text(p.label) }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(cadence == null, { cadence = null }, { Text("Suggested") })
                CADENCE_LABELS.forEach { (key, label) -> FilterChip(cadence == key, { cadence = key }, { Text(label) }) }
            }

            SectionHeader("Details")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Birthday", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (birthday != null) {
                    DateChip(birthday!!, today, { birthday = it })
                    IconButton({ birthday = null }) { Icon(Icons.Rounded.Close, "Remove birthday") }
                } else TextButton({ birthday = LocalDate.of(1990, today.monthValue, today.dayOfMonth) }) { Text("Add") }
            }
            if (birthday != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("I don't know the year", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(noYear, { noYear = it })
                }
            }
            OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, placeholder = { Text("+1 555 123 4567") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
            OutlinedTextField(location, { location = it }, label = { Text("Lives in") }, placeholder = { Text("City, Country") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = words)
            OutlinedTextField(tags, { tags = it }, label = { Text("Tags") }, placeholder = { Text("college, climbing") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(howWeMet, { howWeMet = it }, label = { Text("How we met") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))

            SectionHeader("Family") {
                TextButton({ family.add(FamilyRow("", "", "")) }) {
                    Icon(Icons.Rounded.Add, null)
                    Spacer(Modifier.width(4.dp))
                    Text("Add")
                }
            }
            family.forEachIndexed { i, f ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(f.name, { f.name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.weight(1.3f), keyboardOptions = words)
                    OutlinedTextField(f.relation, { f.relation = it }, label = { Text("Relation") }, singleLine = true, modifier = Modifier.weight(1f))
                    IconButton({ family.removeAt(i) }) { Icon(Icons.Rounded.Close, "Remove") }
                }
                OutlinedTextField(
                    f.birthday, { f.birthday = it },
                    label = { Text("Birthday (YYYY-MM-DD or MM-DD)") },
                    isError = f.birthday.isNotBlank() && Birthday.parse(f.birthday) == null,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                )
            }

            SectionHeader("Notes")
            OutlinedTextField(
                notes, { notes = it },
                placeholder = { Text("Interests, things to remember, gift ideas…\n- [ ] Something to ask next time") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = ::save, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(if (person == null) "Add ${name.trim().substringBefore(' ').ifEmpty { "person" }}" else "Save")
            }
            Spacer(Modifier.height(24.dp).navigationBarsPadding())
        }
    }
}
