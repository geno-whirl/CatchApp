package io.github.genowhirl.catchup.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.genowhirl.catchup.repository
import kotlinx.coroutines.launch

/** Alarms don't survive a reboot or clock change; schedule them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val repo = context.repository
        repo.scope.launch {
            try {
                val d = repo.awaitReady()
                Scheduler.reschedule(context, d.people, repo.settings.value)
            } finally {
                pending.finish()
            }
        }
    }
}
