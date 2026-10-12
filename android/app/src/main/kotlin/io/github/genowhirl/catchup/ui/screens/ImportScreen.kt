package io.github.genowhirl.catchup.ui.screens

import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import io.github.genowhirl.catchup.core.RestorePlan
import io.github.genowhirl.catchup.core.planRestore
import io.github.genowhirl.catchup.core.readBackup
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.imports.ImportFile
import io.github.genowhirl.catchup.core.imports.ImportItem
import io.github.genowhirl.catchup.core.imports.ImportSource
import io.github.genowhirl.catchup.core.imports.Meta
import io.github.genowhirl.catchup.core.imports.ParseOutcome
import io.github.genowhirl.catchup.core.imports.VCard
import io.github.genowhirl.catchup.core.imports.WhatsApp
import io.github.genowhirl.catchup.core.imports.planImport
import io.github.genowhirl.catchup.core.imports.readImportFile
import io.github.genowhirl.catchup.data.ContactsReader
import io.github.genowhirl.catchup.ui.components.Avatar
import io.github.genowhirl.catchup.ui.components.Panel
import io.github.genowhirl.catchup.ui.components.Pill
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class Source(val id: ImportSource?, val icon: ImageVector, val title: String, val blurb: String, val how: List<String>, val mime: Array<String>)

private val SOURCES = listOf(
    Source(
        ImportSource.CONTACTS, Icons.Rounded.FileOpen, "Contacts file (.vcf)",
        "From another phone, iCloud or Google Contacts.",
        listOf("iPhone: Contacts → Lists → long-press “All Contacts” → Export.", "Google Contacts (contacts.google.com): select → ⋮ → Export → vCard."),
        arrayOf("text/x-vcard", "text/vcard", "text/directory", "*/*"),
    ),
    Source(
        ImportSource.WHATSAPP, Icons.AutoMirrored.Rounded.Chat, "WhatsApp chats",
        "When you last messaged someone. Message text is read on your phone and never saved.",
        listOf("Open a chat → ⋮ → More → Export chat → Without media.", "Save it to Files or Drive, then pick it here. You can pick several chats at once."),
        arrayOf("text/plain", "application/zip", "*/*"),
    ),
    Source(
        ImportSource.FACEBOOK, Icons.Rounded.Groups, "Facebook",
        "Your friends, and when you last talked on Messenger.",
        listOf("Facebook → Settings → Accounts Center → Your information and permissions → Download your information.", "Pick Friends and Messages, format JSON, media quality Low. Download the zip when it's ready."),
        arrayOf("application/zip", "application/json", "*/*"),
    ),
    Source(
        ImportSource.INSTAGRAM, Icons.Rounded.PhotoCamera, "Instagram",
        "People you follow who follow you back, and when you last talked in DMs.",
        listOf("Instagram → Settings → Accounts Center → Your information and permissions → Download your information.", "Pick Followers and following and Messages, format JSON."),
        arrayOf("application/zip", "application/json", "*/*"),
    ),
)

private fun parse(source: ImportSource, files: List<ImportFile>): ParseOutcome = when (source) {
    ImportSource.CONTACTS -> files.filter { it.name.endsWith(".vcf", true) || it.text.contains("BEGIN:VCARD", true) }
        .map { VCard.parse(it.text) }
        .let { o -> ParseOutcome(o.flatMap { it.candidates }, o.flatMap { it.notes }) }
    ImportSource.WHATSAPP -> WhatsApp.parse(files.map { if (it.name.endsWith(".txt", true)) it else it.copy(name = it.name + ".txt") }.filter { !it.name.endsWith(".json.txt") })
    ImportSource.FACEBOOK, ImportSource.INSTAGRAM -> Meta.parse(files, source)
}

/** Brings people in from the phone and from app exports; nothing existing is overwritten. */
@Composable
fun ImportScreen(
    people: List<Person>,
    files: () -> Map<String, String>,
    onBack: () -> Unit,
    startRestore: Boolean = false,
    onImport: (List<Op>, String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var items by remember { mutableStateOf<List<ImportItem>?>(null) }
    var notes by remember { mutableStateOf<List<String>>(emptyList()) }
    val selected = remember { mutableStateListOf<String>() }
    var expanded by remember { mutableStateOf<ImportSource?>(null) }
    var pending by remember { mutableStateOf<ImportSource?>(null) }
    var filter by remember { mutableStateOf("") }

    fun show(outcome: ParseOutcome) {
        val plan = planImport(outcome.candidates, people, files())
        items = plan
        notes = outcome.notes
        selected.clear()
        selected.addAll(plan.filter { it.op != null && it.match != null }.map { it.key })
    }

    fun load(block: () -> ParseOutcome) {
        busy = true
        error = null
        scope.launch {
            try {
                show(withContext(Dispatchers.IO) { block() })
            } catch (e: Exception) {
                error = "Couldn't read that: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun read(uri: Uri): Pair<String, ByteArray> {
        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        return name to (context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0))
    }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        val source = pending ?: return@rememberLauncherForActivityResult
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        load {
            val read = uris.flatMap { uri -> read(uri).let { (name, bytes) -> readImportFile(name, bytes) } }
            parse(source, read)
        }
    }

    // Restoring a backup: whole files, compared with what's here.
    var restore by remember { mutableStateOf<RestorePlan?>(null) }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        busy = true
        error = null
        scope.launch {
            try {
                val plan = withContext(Dispatchers.IO) { planRestore(readBackup(uris.map(::read)), files()) }
                if (plan.total == 0) error = "No people found in that file. Pick the .zip from Export a backup, or .md files from a people folder."
                else restore = plan
            } catch (e: Exception) {
                error = "Couldn't read that: ${e.message}"
            } finally {
                busy = false
            }
        }
    }
    val launchRestore = { pickBackup.launch(arrayOf("application/zip", "text/markdown", "text/plain", "application/octet-stream", "*/*")) }
    LaunchedEffect(Unit) { if (startRestore) launchRestore() }
    restore?.let { plan ->
        RestoreDialog(plan, onDismiss = { restore = null }) { replace ->
            restore = null
            val ops = plan.ops(replace)
            val n = plan.new.size + if (replace) plan.changed.size else 0
            onImport(ops, "Restored $n ${if (n == 1) "person" else "people"}")
        }
    }
    val contactsPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) load { ParseOutcome(ContactsReader.read(context), emptyList()) }
        else error = "CatchUp needs access to your contacts to import them. You can also import a .vcf file."
    }

    val current = items
    if (current != null) {
        Review(
            current, notes, selected, filter, { filter = it },
            onBack = { items = null },
            onImport = {
                val ops = current.filter { it.key in selected }.mapNotNull { it.op }
                onImport(ops, "Imported ${ops.size} ${if (ops.size == 1) "person" else "people"}")
            },
        )
        return
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Import people") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(
                    "Everything is read on this phone. You choose who to add, and nothing you've already written is overwritten.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                if (busy) Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Reading…")
                }
            }
            item {
                Panel(color = MaterialTheme.colorScheme.primaryContainer, onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        load { ParseOutcome(ContactsReader.read(context), emptyList()) }
                    } else contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Contacts, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("From your phone's contacts", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("Names, birthdays, numbers and family. You'll pick who to add.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
            item {
                Panel(onClick = { launchRestore() }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.SettingsBackupRestore, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("A CatchUp backup", style = MaterialTheme.typography.titleMedium)
                            Text("The .zip from Settings → Export a backup, a download of your data repository, or .md files.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            items(SOURCES, key = { it.title }) { s ->
                Panel(onClick = { expanded = if (expanded == s.id) null else s.id }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(s.icon, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.titleMedium)
                            Text(s.blurb, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(if (expanded == s.id) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
                    }
                    AnimatedVisibility(expanded == s.id) {
                        Column(Modifier.padding(top = 12.dp)) {
                            s.how.forEachIndexed { i, step ->
                                Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 2.dp))
                            }
                            Spacer(Modifier.height(12.dp))
                            Button(onClick = { pending = s.id; pickFiles.launch(s.mime) }, enabled = !busy) { Text("Choose file${if (s.id == ImportSource.CONTACTS) "" else "s"}") }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun Review(
    items: List<ImportItem>,
    notes: List<String>,
    selected: MutableList<String>,
    filter: String,
    onFilter: (String) -> Unit,
    onBack: () -> Unit,
    onImport: () -> Unit,
) {
    val actionable = items.filter { it.op != null }
    val visible = actionable.filter { it.candidate.name.contains(filter, ignoreCase = true) }
    val newCount = actionable.count { it.match == null }
    Column(Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Review") }, navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } })
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)) {
            item {
                Text(
                    "Found ${items.size}: $newCount new, ${actionable.size - newCount} with something to add" +
                        (items.size - actionable.size).let { if (it > 0) ", $it already up to date." else "." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 4.dp)) }
                Spacer(Modifier.height(12.dp))
                TextField(filter, onFilter, placeholder = { Text("Filter") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(false, { visible.forEach { if (it.key !in selected) selected += it.key } }, { Text("Select all") })
                    FilterChip(false, { selected.removeAll(visible.map { it.key }.toSet()) }, { Text("Select none") })
                }
            }
            items(visible, key = { it.key }) { item ->
                val on = item.key in selected
                Row(
                    Modifier.fillMaxWidth().clickable { if (on) selected -= item.key else selected += item.key }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(on, { if (it) selected += item.key else selected -= item.key })
                    Avatar(item.candidate.name, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.candidate.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                            if (item.match == null) {
                                Spacer(Modifier.width(6.dp))
                                Pill("new", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }
                        Text(
                            if (item.match != null) "Add to ${item.match!!.name}: ${item.changes.joinToString(", ")}"
                            else listOfNotNull(item.candidate.detail, item.candidate.birthday?.let { "birthday" }, item.candidate.contact.keys.firstOrNull()).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()) {
            Button(onClick = onImport, enabled = selected.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("Import ${selected.size}")
            }
        }
    }
}

@Composable
private fun RestoreDialog(plan: RestorePlan, onDismiss: () -> Unit, onRestore: (replaceChanged: Boolean) -> Unit) {
    val n = plan.new.size
    val c = plan.changed.size
    fun people(k: Int) = if (k == 1) "1 person" else "$k people"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Restore from backup") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The backup has ${people(plan.total)}.")
                if (n > 0) Text("• ${people(n)} not here yet will be added.")
                if (plan.unchanged > 0) Text("• ${people(plan.unchanged)} already up to date.")
                if (c > 0) Text("• ${people(c)} differ from what's here. Keep yours, or put back the backup's version (newer notes on them would be lost).")
                if (plan.unreadable.isNotEmpty()) Text("• ${plan.unreadable.size} file(s) couldn't be read and will be skipped.", color = MaterialTheme.colorScheme.error)
                if (n == 0 && c == 0) Text("There's nothing to restore.")
                Spacer(Modifier.height(4.dp))
                if (n > 0) Button(onClick = { onRestore(false) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (c > 0) "Add ${people(n)}, keep the rest" else "Add ${people(n)}")
                }
                if (c > 0) OutlinedButton(onClick = { onRestore(true) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (n > 0) "Add and restore all" else "Restore ${people(c)}")
                }
            }
        },
        confirmButton = { TextButton(onDismiss) { Text(if (n == 0 && c == 0) "OK" else "Cancel") } },
    )
}
