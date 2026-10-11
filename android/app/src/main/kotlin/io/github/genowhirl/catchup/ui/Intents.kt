package io.github.genowhirl.catchup.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.widget.Toast
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.Plan
import io.github.genowhirl.catchup.core.planTitle
import java.time.ZoneId

/** Ways to reach someone, from their contact fields. */
data class Reach(val phone: String?, val whatsapp: String?, val email: String?) {
    val any get() = phone != null || whatsapp != null || email != null

    companion object {
        fun of(p: Person): Reach {
            fun find(re: Regex) = p.contact.entries.firstOrNull { re.containsMatchIn(it.key.lowercase()) }?.value
            val phone = find(Regex("^(phone|mobile|cell)"))
            val wa = find(Regex("^whatsapp")) ?: phone
            return Reach(phone, wa?.takeIf { it.trim().startsWith("+") }, find(Regex("^e-?mail")))
        }
    }
}

private fun digits(phone: String) = phone.filter { it.isDigit() || it == '+' }

private fun Context.launch(intent: Intent) {
    try {
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(this, "No app found for that", Toast.LENGTH_SHORT).show()
    }
}

fun Context.call(phone: String) = launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${digits(phone)}")))

fun Context.text(phone: String, body: String = "") =
    launch(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${digits(phone)}")).putExtra("sms_body", body))

fun Context.whatsapp(phone: String, body: String = "") {
    val number = digits(phone).removePrefix("+")
    launch(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$number" + if (body.isNotEmpty()) "?text=${Uri.encode(body)}" else "")))
}

fun Context.email(address: String, subject: String = "") =
    launch(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$address")).putExtra(Intent.EXTRA_SUBJECT, subject))

fun Context.open(url: String) = launch(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

fun Context.share(text: String) =
    launch(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null))

/** Opens the calendar app with the plan filled in (no calendar permission needed). */
fun Context.addToCalendar(person: Person, plan: Plan) {
    val zone = ZoneId.systemDefault()
    val start = plan.at.date.atTime(plan.at.time ?: java.time.LocalTime.of(9, 0)).atZone(zone).toInstant().toEpochMilli()
    val end = start + if (plan.at.time == null) 0 else 60 * 60 * 1000
    val ask = person.openFollowUps.takeIf { it.isNotEmpty() }?.joinToString("\n", prefix = "Ask about:\n") { "• $it" }
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, planTitle(person, plan))
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, plan.at.time == null)
    plan.place?.let { intent.putExtra(CalendarContract.Events.EVENT_LOCATION, it) }
    ask?.let { intent.putExtra(CalendarContract.Events.DESCRIPTION, it) }
    launch(intent)
}

/** A friendly invitation to send when proposing a plan. */
fun inviteText(person: Person, kind: String, whenText: String): String {
    val k = kind.lowercase()
    val activity = when {
        "call" in k || "video" in k -> "a call"
        k == "meet" -> "catching up"
        else -> k
    }
    return "Hi ${person.shortName}! Are you free for $activity $whenText? 😊"
}
