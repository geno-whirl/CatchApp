package io.github.genowhirl.catchup.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.genowhirl.catchup.core.Issue
import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.idFromPath
import io.github.genowhirl.catchup.core.parsePerson

/** The file itself — for anything the forms don't cover. Same format AI assistants use. */
@Composable
fun MarkdownScreen(path: String, initial: String, onBack: () -> Unit, onSave: (Op, String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val issues = remember(text) { parsePerson(idFromPath(path), text).issues }
    val errors = issues.filter { it.level == Issue.Level.ERROR }
    Column(Modifier.fillMaxSize().imePadding()) {
        TopAppBar(
            title = { Text(path, style = MaterialTheme.typography.titleMedium) },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
            actions = { TextButton(onClick = { onSave(Op.Replace(path, text), "Saved") }, enabled = errors.isEmpty() && text != initial) { Text("Save") } },
        )
        issues.forEach {
            Text(
                it.message,
                color = if (it.level == Issue.Level.ERROR) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }
        OutlinedTextField(
            text, { text = it },
            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp),
            modifier = Modifier.fillMaxWidth().weight(1f).padding(12.dp),
        )
    }
}
