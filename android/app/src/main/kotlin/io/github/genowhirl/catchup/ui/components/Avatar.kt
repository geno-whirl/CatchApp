package io.github.genowhirl.catchup.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.genowhirl.catchup.core.Priority
import io.github.genowhirl.catchup.ui.theme.Display

private val PALETTE = listOf(
    Color(0xFFE07A5F) to Color(0xFFF2B5A0),
    Color(0xFF81B29A) to Color(0xFFC5E3D2),
    Color(0xFF6D8BC9) to Color(0xFFBCCDEF),
    Color(0xFFB07BAC) to Color(0xFFE4C6E1),
    Color(0xFFE0A458) to Color(0xFFF5D7A8),
    Color(0xFF5FA8A0) to Color(0xFFB6E0DB),
    Color(0xFFC9707D) to Color(0xFFF0C2C8),
    Color(0xFF8C7AC9) to Color(0xFFD3CBF0),
)

fun avatarColors(name: String): Pair<Color, Color> {
    // FNV-1a, folded: neighbouring names land on different colours.
    var h = 0x811c9dc5.toInt()
    for (c in name) {
        h = h xor c.code
        h *= 0x01000193
    }
    h = h xor (h ushr 16)
    return PALETTE[Math.floorMod(h ushr 8, PALETTE.size)]
}

fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val first = parts.firstOrNull()?.firstOrNull()?.toString().orEmpty()
    val last = if (parts.size > 1) parts.last().first().toString() else ""
    return (first + last).uppercase()
}

/** A soft gradient circle with initials; high-priority people get a little heart. */
@Composable
fun Avatar(name: String, size: Dp = 48.dp, priority: Priority? = null, modifier: Modifier = Modifier) {
    val (strong, soft) = avatarColors(name)
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val top = if (dark) lerp(strong, Color.Black, 0.35f) else soft
    val bottom = if (dark) lerp(strong, Color.Black, 0.55f) else lerp(soft, strong, 0.35f)
    val ink = if (dark) lerp(soft, Color.White, 0.3f) else lerp(strong, Color.Black, 0.45f)
    Box(modifier.size(size)) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(top, bottom))),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                initials(name),
                color = ink,
                style = TextStyle(fontFamily = Display, fontWeight = FontWeight.SemiBold, fontSize = (size.value * 0.38f).sp),
            )
        }
        if (priority == Priority.HIGH) {
            val badge = (size.value * 0.36f).coerceIn(14f, 26f).dp
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(2.dp, 2.dp)
                    .size(badge)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = "High priority", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(badge * 0.22f))
            }
        }
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
