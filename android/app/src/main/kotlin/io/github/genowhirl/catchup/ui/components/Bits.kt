package io.github.genowhirl.catchup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.genowhirl.catchup.core.ContactStatus
import io.github.genowhirl.catchup.core.Reason
import io.github.genowhirl.catchup.core.describe
import io.github.genowhirl.catchup.ui.theme.CatchUpTheme
import io.github.genowhirl.catchup.ui.theme.SectionLabel
import java.time.LocalDate

/** An icon for an interaction or plan kind. */
fun kindIcon(kind: String?): ImageVector {
    val k = kind?.lowercase().orEmpty()
    return when {
        "video" in k -> Icons.Rounded.Videocam
        "call" in k || "phone" in k -> Icons.Rounded.Call
        "message" in k || "text" in k || "chat" in k || "whatsapp" in k -> Icons.AutoMirrored.Rounded.Chat
        "email" in k || "mail" in k -> Icons.Rounded.Email
        "coffee" in k || "tea" in k -> Icons.Rounded.Coffee
        "lunch" in k || "dinner" in k || "breakfast" in k || "brunch" in k || "drink" in k -> Icons.Rounded.Restaurant
        "walk" in k || "hike" in k || "run" in k -> Icons.AutoMirrored.Rounded.DirectionsWalk
        k.isEmpty() -> Icons.Rounded.Event
        else -> Icons.Rounded.People
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text.uppercase(), style = SectionLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** A rounded surface card used across the app. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    padding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = color)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors) {
            Column(Modifier.padding(padding), content = content)
        }
    } else {
        Card(modifier = modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = colors) {
            Column(Modifier.padding(padding), content = content)
        }
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(50)).background(container).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ReasonPill(reason: Reason, today: LocalDate, modifier: Modifier = Modifier) {
    val c = CatchUpTheme.colors
    val (bg, fg, icon) = when (reason) {
        is Reason.Birthday, is Reason.FamilyBirthday -> Triple(c.birthday, c.onBirthday, Icons.Rounded.Cake)
        is Reason.Overdue -> Triple(c.overdue, c.onOverdue, Icons.Rounded.HourglassTop)
        is Reason.DueSoon -> Triple(c.dueSoon, c.onDueSoon, Icons.Rounded.Schedule)
        Reason.NeverContacted -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, Icons.Rounded.NewReleases)
    }
    Pill(describe(reason, today), bg, fg, icon, modifier)
}

@Composable
fun StatusPill(status: ContactStatus, text: String, modifier: Modifier = Modifier) {
    val c = CatchUpTheme.colors
    val (bg, fg) = when (status) {
        ContactStatus.OVERDUE -> c.overdue to c.onOverdue
        ContactStatus.DUE_SOON -> c.dueSoon to c.onDueSoon
        ContactStatus.OK -> c.ok to c.onOk
        ContactStatus.NONE -> MaterialTheme.colorScheme.surfaceContainerHighest to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Pill(text, bg, fg, modifier = modifier)
}

/** A calendar-style tile: weekday over day number. */
@Composable
fun DateTile(date: LocalDate, highlight: Boolean = false, modifier: Modifier = Modifier) {
    val bg = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer
    val fg = if (highlight) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
    Column(
        modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(weekday(date).uppercase(), style = MaterialTheme.typography.labelSmall, color = fg)
        Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.titleLarge, color = fg)
    }
}

/** A round tonal icon with a label underneath, for quick actions. */
@Composable
fun ActionButton(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, emphasized: Boolean = false) {
    Column(modifier.padding(horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
            contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Icon(icon, contentDescription = label) }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier, action: @Composable (() -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
