package io.github.genowhirl.catchup.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.idFromPath
import io.github.genowhirl.catchup.core.sync.GitHubStore
import io.github.genowhirl.catchup.data.GoogleDrive
import io.github.genowhirl.catchup.data.Storage
import io.github.genowhirl.catchup.notify.Notifications
import io.github.genowhirl.catchup.notify.Scheduler
import io.github.genowhirl.catchup.ui.screens.EditPersonScreen
import io.github.genowhirl.catchup.ui.screens.ImportScreen
import io.github.genowhirl.catchup.ui.screens.MarkdownScreen
import io.github.genowhirl.catchup.ui.screens.OnboardingActions
import io.github.genowhirl.catchup.ui.screens.OnboardingScreen
import io.github.genowhirl.catchup.ui.screens.PeopleScreen
import io.github.genowhirl.catchup.ui.screens.PersonActions
import io.github.genowhirl.catchup.ui.screens.PersonRow
import io.github.genowhirl.catchup.ui.screens.PersonScreen
import io.github.genowhirl.catchup.ui.screens.PlanWeekScreen
import io.github.genowhirl.catchup.ui.screens.PlansTab
import io.github.genowhirl.catchup.ui.screens.SettingsActions
import io.github.genowhirl.catchup.ui.screens.SettingsScreen
import io.github.genowhirl.catchup.ui.screens.StorageScreen
import io.github.genowhirl.catchup.ui.screens.TodayActions
import io.github.genowhirl.catchup.ui.screens.TodayScreen
import io.github.genowhirl.catchup.ui.sheets.KeepInTouchSheet
import io.github.genowhirl.catchup.ui.sheets.LogSheet
import io.github.genowhirl.catchup.ui.sheets.PlanSheet
import io.github.genowhirl.catchup.ui.theme.CatchUpTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Sheets that float over any screen. */
private sealed interface Sheet {
    data class Log(val personId: String, val planKey: String?) : Sheet
    data class PlanIt(val personId: String, val planKey: String?) : Sheet
    data class KeepInTouch(val personId: String) : Sheet
    data object PickPerson : Sheet
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val TABS = listOf(
    Tab("today", "Today", Icons.Rounded.WbSunny),
    Tab("people", "People", Icons.Rounded.Groups),
    Tab("plans", "Plans", Icons.Rounded.CalendarMonth),
    Tab("settings", "Settings", Icons.Rounded.Settings),
)

/** Routes requested from outside (notifications): consumed once by the UI. */
val pendingRoute = MutableStateFlow<String?>(null)

@Composable
fun CatchUpApp(vm: AppViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val directory by vm.directory.collectAsStateWithLifecycle()
    val sync by vm.sync.collectAsStateWithLifecycle()
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(60_000)
            value = LocalDateTime.now()
        }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nav = rememberNavController()
    val snackbar = remember { SnackbarHostState() }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var confirmDelete by remember { mutableStateOf<Person?>(null) }
    var permissionTick by remember { mutableStateOf(0) }
    val people = directory.people
    fun person(id: String) = people.firstOrNull { it.id == id }

    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    // Permissions and system screens.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    val requestNotifications = {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
    }
    val requestExact = {
        if (Build.VERSION.SDK_INT >= 31) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:${context.packageName}")))
        }
    }
    val notificationsAllowed = remember(permissionTick, now) { Notifications.canNotify(context) }
    val exactAllowed = remember(permissionTick, now) { Scheduler.canScheduleExact(context) }

    // Google Drive sign-in.
    var driveCarry by remember { mutableStateOf(true) }
    var driveDone by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    val driveConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val token = if (result.resultCode == Activity.RESULT_OK) GoogleDrive.tokenFromConsent(context, result.data) else null
        if (token != null) vm.switchStorage(Storage.Drive(), driveCarry) { driveDone?.invoke(null); vm.say("Connected to Google Drive") }
        else driveDone?.invoke("Google Drive wasn't connected.")
    }
    fun connectDrive(carry: Boolean, done: (String?) -> Unit) {
        driveCarry = carry
        driveDone = done
        scope.launch {
            try {
                when (val r = GoogleDrive.authorize(context)) {
                    is GoogleDrive.Result.Token -> vm.switchStorage(Storage.Drive(), carry) { done(null); vm.say("Connected to Google Drive") }
                    is GoogleDrive.Result.NeedsConsent -> driveConsent.launch(IntentSenderRequest.Builder(r.intent.intentSender).build())
                }
            } catch (e: Exception) {
                done(
                    if (e.message?.contains("10:") == true || e.message?.contains("DEVELOPER_ERROR") == true)
                        "Google Drive isn't set up for this build yet (it needs an OAuth client for the app's signing key)."
                    else "Couldn't connect to Google: ${e.message}",
                )
            }
        }
    }
    fun connect(storage: Storage, carry: Boolean, done: (String?) -> Unit) {
        when (storage) {
            Storage.Local -> vm.switchStorage(Storage.Local, carry) { done(null); vm.say("Now stored on this phone"); nav.popBackStack() }
            is Storage.Drive -> connectDrive(carry) { e -> done(e); if (e == null) nav.popBackStack() }
            is Storage.GitHub -> scope.launch {
                try {
                    val private = GitHubStore(storage.config()).check()
                    if (!private) {
                        done("${storage.owner}/${storage.repo} is public. Make it private on GitHub first — your notes about people shouldn't be public.")
                        return@launch
                    }
                    vm.switchStorage(storage, carry) { done(null); vm.say("Connected to ${storage.owner}/${storage.repo}"); nav.popBackStack() }
                } catch (e: Exception) {
                    done(e.message ?: "Couldn't connect.")
                }
            }
        }
    }

    // Backup.
    val backup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    ZipOutputStream(out).use { zip ->
                        vm.files().forEach { (path, text) ->
                            zip.putNextEntry(ZipEntry(path))
                            zip.write(text.toByteArray())
                            zip.closeEntry()
                        }
                    }
                }
            }
            vm.say("Backup saved")
        }
    }

    fun addExamples() {
        val ops = context.assets.list("people").orEmpty().filter { it.endsWith(".md") && !it.startsWith("_") }.map { name ->
            Op.Create("people/$name", context.assets.open("people/$name").bufferedReader().readText(), skipIfExists = true)
        }
        vm.apply(*ops.toTypedArray(), message = "Added ${ops.size} example people")
    }

    // Notification taps.
    val requested by pendingRoute.collectAsStateWithLifecycle()
    LaunchedEffect(requested, directory.loaded, settings.onboarded) {
        val route = requested ?: return@LaunchedEffect
        if (!directory.loaded || !settings.onboarded) return@LaunchedEffect
        pendingRoute.value = null
        when {
            route.startsWith("log/") -> {
                val (id, key) = route.removePrefix("log/").split("/", limit = 2).let { it[0] to it.getOrNull(1) }
                nav.navigate("person/$id") { launchSingleTop = true }
                sheet = Sheet.Log(id, key)
            }
            route.startsWith("person/") -> nav.navigate(route) { launchSingleTop = true }
            route == "plan-week" -> nav.navigate("plan-week") { launchSingleTop = true }
            else -> nav.navigate("today") { popUpTo("today") { inclusive = true } }
        }
    }

    CatchUpTheme(dynamic = settings.dynamicColor) {
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val start = if (settings.onboarded) "today" else "onboarding"
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                if (route in TABS.map { it.route }) {
                    NavigationBar {
                        TABS.forEach { tab ->
                            NavigationBarItem(
                                selected = route == tab.route,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo("today") { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, null) },
                                label = { Text(tab.label) },
                            )
                        }
                    }
                }
            },
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        ) { padding ->
            if (!directory.loaded) {
                Box(Modifier.fillMaxSize())
                return@Scaffold
            }
            NavHost(nav, startDestination = start, modifier = Modifier.fillMaxSize()) {
                composable("onboarding") {
                    OnboardingScreen(
                        notificationsAllowed,
                        settings.storage is Storage.Drive,
                        OnboardingActions(
                            requestNotifications = requestNotifications,
                            connectDrive = { connectDrive(true) { e -> e?.let(vm::say) } },
                            openStorage = { nav.navigate("storage") },
                            importContacts = { nav.navigate("import") },
                            addPerson = { nav.navigate("new") },
                            tryExamples = { addExamples() },
                            finish = { name, day, time, goal ->
                                vm.updateSettings { it.copy(onboarded = true, userName = name, planningDay = day, planningTime = time, weeklyGoal = goal) }
                                nav.navigate("today") { popUpTo("onboarding") { inclusive = true } }
                            },
                        ),
                    )
                }
                composable("today") {
                    TodayScreen(
                        people, now, settings.userName, settings.weeklyGoal,
                        TodayActions(
                            openPerson = { nav.navigate("person/$it") },
                            log = { p, plan -> sheet = Sheet.Log(p.id, plan?.key) },
                            plan = { p -> sheet = Sheet.PlanIt(p.id, null) },
                            didntHappen = { p, plan -> vm.apply(Op.RemovePlan(p.path, plan.key), message = "Removed") },
                            planWeek = { nav.navigate("plan-week") },
                            call = { context.call(it) },
                            message = { p -> Reach.of(p).let { r -> r.whatsapp?.let { context.whatsapp(it) } ?: r.phone?.let { context.text(it) } } },
                            addPerson = { nav.navigate("new") },
                            import = { nav.navigate("import") },
                        ),
                        padding,
                    )
                }
                composable("people") {
                    PeopleScreen(people, now.toLocalDate(), { nav.navigate("person/$it") }, { nav.navigate("new") }, { nav.navigate("import") }, padding)
                }
                composable("plans") {
                    PlansTab(
                        people, now.toLocalDate(),
                        onPlanWeek = { nav.navigate("plan-week") },
                        onOpenPlan = { p, plan -> sheet = Sheet.PlanIt(p.id, plan.key) },
                        onNewPlan = { sheet = Sheet.PickPerson },
                        contentPadding = padding,
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        settings, sync, directory.problems, notificationsAllowed, exactAllowed,
                        SettingsActions(
                            update = { vm.updateSettings(it) },
                            syncNow = { vm.syncNow() },
                            storage = { nav.navigate("storage") },
                            import = { nav.navigate("import") },
                            backup = { backup.launch("catchup-backup-${now.toLocalDate()}.zip") },
                            requestNotifications = requestNotifications,
                            requestExactAlarms = requestExact,
                            openFile = { nav.navigate("markdown/${idFromPath(it)}") },
                        ),
                        padding,
                    )
                }
                composable("person/{id}") { entry ->
                    val p = person(entry.arguments?.getString("id").orEmpty())
                    if (p == null) {
                        LaunchedEffect(Unit) { nav.popBackStack() }
                        return@composable
                    }
                    PersonScreen(
                        p, now.toLocalDate(),
                        PersonActions(
                            back = { nav.popBackStack() },
                            edit = { nav.navigate("edit/${p.id}") },
                            editMarkdown = { nav.navigate("markdown/${p.id}") },
                            delete = { confirmDelete = p },
                            log = { plan -> sheet = Sheet.Log(p.id, plan?.key) },
                            plan = { plan -> sheet = Sheet.PlanIt(p.id, plan?.key) },
                            keepInTouch = { sheet = Sheet.KeepInTouch(p.id) },
                            toggleFollowUp = { text, done -> vm.apply(Op.SetFollowUp(p.path, text, done)) },
                            call = { context.call(it) },
                            text = { context.text(it) },
                            whatsapp = { context.whatsapp(it) },
                            email = { context.email(it) },
                            openContact = { key, value ->
                                when {
                                    value.startsWith("http") -> context.open(value)
                                    key.startsWith("email") -> context.email(value)
                                    key.startsWith("instagram") -> context.open("https://www.instagram.com/${value.removePrefix("@")}")
                                    key.startsWith("whatsapp") -> context.whatsapp(value)
                                    else -> context.call(value)
                                }
                            },
                        ),
                    )
                }
                composable("edit/{id}") { entry ->
                    val p = person(entry.arguments?.getString("id").orEmpty()) ?: return@composable
                    EditPersonScreen(p, { nav.popBackStack() }) { ops, message, _ ->
                        vm.apply(*ops.toTypedArray(), message = message)
                        nav.popBackStack()
                    }
                }
                composable("new") {
                    EditPersonScreen(null, { nav.popBackStack() }) { ops, message, _ ->
                        vm.apply(*ops.toTypedArray(), message = message) { paths ->
                            nav.navigate("person/${idFromPath(paths.first())}") { popUpTo("new") { inclusive = true } }
                        }
                    }
                }
                composable("markdown/{id}") { entry ->
                    val id = entry.arguments?.getString("id").orEmpty()
                    val path = "people/$id.md"
                    MarkdownScreen(path, vm.files()[path].orEmpty(), { nav.popBackStack() }) { op, message ->
                        vm.apply(op, message = message)
                        nav.popBackStack()
                    }
                }
                composable("import") {
                    ImportScreen(people, vm::files, { nav.popBackStack() }) { ops, message ->
                        vm.apply(*ops.toTypedArray(), message = message)
                        nav.navigate("people") { popUpTo("today") }
                    }
                }
                composable("storage") {
                    StorageScreen(settings.storage, people.size, { nav.popBackStack() }, ::connect, { context.open(it) })
                }
                composable("plan-week") {
                    PlanWeekScreen(
                        people, now, settings.weeklyGoal, settings.usualSlots(),
                        onBack = { nav.popBackStack() },
                        onSave = { ops, message -> vm.apply(*ops.toTypedArray(), message = message) },
                        onRememberUsual = { slots -> vm.updateSettings { s -> s.copy(usualFreeTime = slots.map { "${it.first.name}:${it.second.name}" }.toSet()) } },
                        onInvite = { p, text -> Reach.of(p).let { r -> r.whatsapp?.let { context.whatsapp(it, text) } ?: r.phone?.let { context.text(it, text) } ?: context.share(text) } },
                        onAddToCalendar = { p, plan -> context.addToCalendar(p, plan) },
                    )
                }
            }
        }

        when (val s = sheet) {
            is Sheet.Log -> person(s.personId)?.let { p ->
                LogSheet(p, p.plans.firstOrNull { it.key == s.planKey }, { sheet = null }) { ops, message ->
                    vm.apply(*ops.toTypedArray(), message = message)
                    sheet = null
                }
            }
            is Sheet.PlanIt -> person(s.personId)?.let { p ->
                PlanSheet(
                    p, p.plans.firstOrNull { it.key == s.planKey }, settings.callReminderMinutes, settings.meetReminderMinutes,
                    onDismiss = { sheet = null },
                    onSave = { ops, message, _ -> vm.apply(*ops.toTypedArray(), message = message); sheet = null },
                    onAddToCalendar = { plan -> context.addToCalendar(p, plan) },
                )
            }
            is Sheet.KeepInTouch -> person(s.personId)?.let { p ->
                KeepInTouchSheet(p, { sheet = null }) { op, message ->
                    vm.apply(op, message = message)
                    sheet = null
                }
            }
            Sheet.PickPerson -> PersonPicker(people, now, { sheet = null }) { sheet = Sheet.PlanIt(it.id, null) }
            null -> {}
        }

        confirmDelete?.let { p ->
            AlertDialog(
                onDismissRequest = { confirmDelete = null },
                title = { Text("Remove ${p.name}?") },
                text = { Text("This deletes ${p.path}, including all notes and interactions.") },
                confirmButton = {
                    TextButton({
                        vm.apply(Op.Delete(p.path), message = "Removed ${p.name}")
                        confirmDelete = null
                        nav.popBackStack()
                    }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun PersonPicker(people: List<Person>, now: LocalDateTime, onDismiss: () -> Unit, onPick: (Person) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)) {
        Text("Plan with…", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
        LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
            items(people, key = { it.id }) { p -> PersonRow(p, now.toLocalDate()) { onPick(p) } }
        }
    }
}
