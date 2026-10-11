package io.github.genowhirl.catchup.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddToDrive
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.genowhirl.catchup.data.Storage
import io.github.genowhirl.catchup.ui.components.Panel

const val TOKEN_URL = "https://github.com/settings/personal-access-tokens/new"

/** Where people live: just this phone, Google Drive, or a private GitHub repository. */
@Composable
fun StorageScreen(
    current: Storage,
    peopleCount: Int,
    onBack: () -> Unit,
    onConnect: (Storage, Boolean, (String?) -> Unit) -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var busy by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var carry by remember { mutableStateOf(true) }
    val gh = current as? Storage.GitHub
    var repo by remember { mutableStateOf(gh?.let { "${it.owner}/${it.repo}" }.orEmpty()) }
    var token by remember { mutableStateOf(gh?.token.orEmpty()) }
    var branch by remember { mutableStateOf(gh?.branch ?: "main") }
    var folder by remember { mutableStateOf(gh?.root.orEmpty()) }

    fun connect(storage: Storage, key: String) {
        busy = key
        error = null
        onConnect(storage, carry && peopleCount > 0) { e ->
            busy = null
            error = e
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Where your people live") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Everyone is kept as plain Markdown files — one per person — so they stay yours and readable by any app or AI assistant.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (peopleCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(carry, { carry = it })
                    Text("Bring the $peopleCount ${if (peopleCount == 1) "person" else "people"} I have now", style = MaterialTheme.typography.bodyMedium)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

            Option(Icons.Rounded.PhoneAndroid, "On this phone", "Private and simple. Back up from Settings, or sync later.", current == Storage.Local) {
                OutlinedButton(onClick = { connect(Storage.Local, "local") }, enabled = busy == null && current != Storage.Local) { Text("Use this phone") }
            }

            Option(Icons.Rounded.AddToDrive, "Google Drive", "Synced to a CatchUp folder in your Drive. Works across devices.", current is Storage.Drive) {
                Button(onClick = { connect(Storage.Drive(), "drive") }, enabled = busy == null) {
                    if (busy == "drive") Progress() else Text(if (current is Storage.Drive) "Reconnect" else "Connect Google Drive")
                }
            }

            Option(Icons.Rounded.Code, "GitHub repository", "For the technically minded: every change is a commit, and AI assistants can edit the same files.", current is Storage.GitHub) {
                OutlinedTextField(repo, { repo = it }, label = { Text("Repository") }, placeholder = { Text("you/catchup-data") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    token, { token = it },
                    label = { Text("Access token") },
                    placeholder = { Text("github_pat_…") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton({ onOpenUrl(TOKEN_URL) }) { Text("Create a token (Contents: read and write)") }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(branch, { branch = it }, label = { Text("Branch") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(folder, { folder = it }, label = { Text("Folder (optional)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val m = Regex("^(?:https://github\\.com/)?([\\w.-]+)/([\\w.-]+?)(?:\\.git)?/?$").find(repo.trim())
                        if (m == null) error = "Enter the repository as owner/name, e.g. jane/catchup-data."
                        else connect(Storage.GitHub(m.groupValues[1], m.groupValues[2], branch.trim().ifEmpty { "main" }, folder.trim(), token.trim()), "github")
                    },
                    enabled = busy == null && repo.isNotBlank() && token.isNotBlank(),
                ) { if (busy == "github") Progress() else Text(if (current is Storage.GitHub) "Save" else "Connect") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Progress() = CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)

@Composable
private fun Option(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    text: String,
    selected: Boolean,
    content: @Composable () -> Unit,
) {
    Panel(color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (selected) Icon(Icons.Rounded.CheckCircle, "In use", tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(4.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        content()
    }
}
