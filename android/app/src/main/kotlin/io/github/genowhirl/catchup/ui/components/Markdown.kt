package io.github.genowhirl.catchup.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** **bold**, *italic* and `code` inline. */
fun inlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    val re = Regex("""\*\*(.+?)\*\*|\*(.+?)\*|_(.+?)_|`(.+?)`""")
    var last = 0
    for (m in re.findAll(text)) {
        append(text.substring(last, m.range.first))
        when {
            m.groups[1] != null -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(m.groupValues[1]) }
            m.groups[2] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[2]) }
            m.groups[3] != null -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(m.groupValues[3]) }
            else -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(m.groupValues[4]) }
        }
        last = m.range.last + 1
    }
    append(text.substring(last))
}

/** Free-form notes: paragraphs, headings and bullet lists. Task items are shown elsewhere. */
@Composable
fun MarkdownNotes(text: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        val blocks = text.lines()
        for (line in blocks) {
            val t = line.trimEnd()
            when {
                t.isBlank() -> Spacer(Modifier.height(8.dp))
                Regex("""^\s*[-*+]\s+\[[ xX]]\s""").containsMatchIn(t) -> {}
                t.startsWith("#") -> Text(
                    t.trimStart('#').trim(),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
                Regex("""^\s*[-*+]\s+""").containsMatchIn(t) -> Row(Modifier.padding(vertical = 1.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text(inlineMarkdown(t.replaceFirst(Regex("""^\s*[-*+]\s+"""), "")), style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(inlineMarkdown(t), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
