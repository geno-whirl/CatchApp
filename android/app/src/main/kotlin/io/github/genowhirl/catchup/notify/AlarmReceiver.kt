package io.github.genowhirl.catchup.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import io.github.genowhirl.catchup.R
import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.personPath
import io.github.genowhirl.catchup.core.upcomingBirthdays
import io.github.genowhirl.catchup.core.upcomingPlans
import io.github.genowhirl.catchup.core.weeklyPromptText
import io.github.genowhirl.catchup.core.When
import io.github.genowhirl.catchup.repository
import kotlinx.coroutines.launch
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val repo = context.repository
        repo.scope.launch {
            try {
                handle(context, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, intent: Intent) {
        val repo = context.repository
        val person = intent.getStringExtra(Scheduler.EXTRA_PERSON).orEmpty()
        val plan = intent.getStringExtra(Scheduler.EXTRA_PLAN).orEmpty()

        if (intent.action == ACTION_DIDNT_HAPPEN) {
            repo.apply(Op.RemovePlan(personPath(person), plan))
            Notifications.cancel(context, Scheduler.notificationId(person, plan))
            return
        }

        when (intent.getStringExtra(Scheduler.EXTRA_TYPE)) {
            Scheduler.TYPE_REMINDER -> {
                // Skip plans that were moved or cancelled since the alarm was set.
                val d = repo.awaitReady()
                val p = d.people.firstOrNull { it.id == person } ?: return
                val planned = p.plans.firstOrNull { it.key == plan } ?: return
                val phone = p.contact.entries.firstOrNull { Regex("^(phone|mobile|cell)").containsMatchIn(it.key.lowercase()) }?.value
                val isCall = planned.kind?.lowercase()?.let { "call" in it || "phone" in it } == true
                Notifications.show(
                    context, Scheduler.notificationId(person, plan), Notifications.PLANS,
                    intent.getStringExtra(Scheduler.EXTRA_TITLE).orEmpty(), intent.getStringExtra(Scheduler.EXTRA_TEXT).orEmpty(),
                    "person/$person",
                    listOfNotNull(
                        if (isCall && phone != null) NotificationCompat.Action(R.drawable.ic_notification, "Call", dial(context, phone)) else null,
                        NotificationCompat.Action(R.drawable.ic_notification, "Log it", Notifications.open(context, "log/$person/$plan")),
                    ),
                )
            }
            Scheduler.TYPE_FOLLOW_UP -> {
                // Skip if it was already logged or removed.
                val d = repo.awaitReady()
                if (d.people.none { p -> p.id == person && p.plans.any { it.key == plan } }) return
                Notifications.show(
                    context, Scheduler.notificationId(person, plan), Notifications.FOLLOW_UPS,
                    intent.getStringExtra(Scheduler.EXTRA_TITLE).orEmpty(), intent.getStringExtra(Scheduler.EXTRA_TEXT).orEmpty(),
                    "log/$person/$plan",
                    listOf(
                        NotificationCompat.Action(R.drawable.ic_notification, "Add notes", Notifications.open(context, "log/$person/$plan")),
                        NotificationCompat.Action(R.drawable.ic_notification, "Dismiss", didntHappen(context, person, plan)),
                    ),
                )
            }
            Scheduler.TYPE_WEEKLY -> {
                val d = repo.awaitReady()
                Notifications.show(context, 1, Notifications.WEEKLY, "Plan your week", weeklyPromptText(d.people, LocalDate.now()), "plan-week")
                Scheduler.reschedule(context, d.people, repo.settings.value)
            }
            Scheduler.TYPE_DIGEST -> {
                val d = repo.awaitReady()
                val today = LocalDate.now()
                val lines = upcomingBirthdays(d.people, today, 0).map { "🎂 ${it.label}" } +
                    upcomingPlans(d.people, today, 0).map { (p, plan) ->
                        "📅 ${plan.kind ?: "Catch-up"} with ${p.shortName}" + (plan.at.time?.let { " at ${it.format(When.HH_MM)}" } ?: "")
                    }
                if (lines.isNotEmpty()) {
                    Notifications.show(context, 2, Notifications.DIGEST, "Today", lines.joinToString("\n"), "today")
                }
                Scheduler.reschedule(context, d.people, repo.settings.value)
            }
        }
    }

    private fun dial(context: Context, phone: String): PendingIntent {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(context, phone.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun didntHappen(context: Context, person: String, plan: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(ACTION_DIDNT_HAPPEN)
            .setData(Uri.parse("catchup://cancel/" + Uri.encode("$person/$plan")))
            .putExtra(Scheduler.EXTRA_PERSON, person)
            .putExtra(Scheduler.EXTRA_PLAN, plan)
        return PendingIntent.getBroadcast(context, "$person/$plan".hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val ACTION_DIDNT_HAPPEN = "io.github.genowhirl.catchup.DIDNT_HAPPEN"
    }
}
