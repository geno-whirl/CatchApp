package io.github.genowhirl.catchup

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.genowhirl.catchup.notify.Notifications
import io.github.genowhirl.catchup.ui.AppViewModel
import io.github.genowhirl.catchup.ui.CatchUpApp
import io.github.genowhirl.catchup.ui.pendingRoute

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent { CatchUpApp(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        // Pick up changes made elsewhere (another device, an AI assistant).
        vm.syncNow()
    }

    private fun handle(intent: Intent?) {
        intent?.getStringExtra(Notifications.EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }
}
