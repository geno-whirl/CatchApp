package io.github.genowhirl.catchup

import android.app.Application
import android.content.Context
import io.github.genowhirl.catchup.data.Repository
import io.github.genowhirl.catchup.notify.Notifications
import io.github.genowhirl.catchup.notify.SyncWorker

class CatchUpApplication : Application() {
    val repository: Repository by lazy { Repository(this) }

    override fun onCreate() {
        super.onCreate()
        Notifications.createChannels(this)
        SyncWorker.schedule(this)
        repository // start loading
    }
}

val Context.repository: Repository get() = (applicationContext as CatchUpApplication).repository
