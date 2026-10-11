package io.github.genowhirl.catchup.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.genowhirl.catchup.MainActivity
import io.github.genowhirl.catchup.R

object Notifications {
    const val PLANS = "plans"
    const val FOLLOW_UPS = "follow_ups"
    const val WEEKLY = "weekly"
    const val DIGEST = "digest"

    const val EXTRA_ROUTE = "route"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(PLANS, "Upcoming catch-ups", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders before calls and meetups you've planned."
                },
                NotificationChannel(FOLLOW_UPS, "How did it go?", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A nudge after a catch-up to note anything worth remembering."
                },
                NotificationChannel(WEEKLY, "Weekly planning", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Your weekly prompt to plan who to see or call."
                },
                NotificationChannel(DIGEST, "Morning summary", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Birthdays and plans for the day."
                },
            ),
        )
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Opens the app at a route, e.g. `person/maya-patel` or `log/maya-patel/2026-10-20 18:30`. */
    fun open(context: Context, route: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setData(Uri.parse("catchup://open/" + Uri.encode(route)))
            .putExtra(EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, route.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun show(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        route: String,
        actions: List<NotificationCompat.Action> = emptyList(),
    ) {
        if (!canNotify(context)) return
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.brand))
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(context, route))
            .setAutoCancel(true)
            .setCategory(if (channel == PLANS) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_RECOMMENDATION)
        actions.forEach { builder.addAction(it) }
        try {
            NotificationManagerCompat.from(context).notify(id, builder.build())
        } catch (_: SecurityException) {
            // Permission revoked between the check and the call.
        }
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
