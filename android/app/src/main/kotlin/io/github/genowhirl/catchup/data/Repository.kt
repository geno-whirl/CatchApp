package io.github.genowhirl.catchup.data

import android.content.Context
import io.github.genowhirl.catchup.core.Issue
import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.Person
import io.github.genowhirl.catchup.core.parseAll
import io.github.genowhirl.catchup.core.sync.DriveStore
import io.github.genowhirl.catchup.core.sync.GitHubStore
import io.github.genowhirl.catchup.core.sync.RemoteStore
import io.github.genowhirl.catchup.core.sync.SyncEngine
import io.github.genowhirl.catchup.core.sync.SyncState
import io.github.genowhirl.catchup.notify.Scheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class Directory(val people: List<Person> = emptyList(), val problems: Map<String, List<Issue>> = emptyMap(), val loaded: Boolean = false)

/** The app's single source of truth: settings, people, and keeping them in sync. */
@OptIn(FlowPreview::class)
class Repository(private val context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val settingsStore = SettingsStore(context)
    private val engine = SyncEngine(FilePersistence(File(context.filesDir, "catchup-data.json")), null)
    private var syncJob: Job? = null

    val settings: StateFlow<AppSettings> = settingsStore.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
    val sync: StateFlow<SyncState> = engine.state

    val directory: StateFlow<Directory> = engine.state
        .map { it.files to it.loaded }
        .distinctUntilChanged()
        .map { (files, loaded) -> parseAll(files).let { (people, problems) -> Directory(people, problems, loaded) } }
        .stateIn(scope, SharingStarted.Eagerly, Directory())

    private val ready = scope.launch {
        engine.attach(remoteFor(settingsStore.settings.first().storage))
        engine.load()
    }

    init {
        // Keep reminders and notifications in step with the data and settings.
        combine(directory, settings) { d, s -> d to s }
            .debounce(500)
            .onEach { (d, s) -> if (d.loaded) Scheduler.reschedule(context, d.people, s) }
            .launchIn(scope)
        scope.launch {
            ready.join()
            syncNow()
        }
    }

    suspend fun awaitReady(): Directory {
        ready.join()
        return directory.first { it.loaded }
    }

    fun remoteFor(storage: Storage): RemoteStore? = when (storage) {
        Storage.Local -> null
        is Storage.GitHub -> GitHubStore(storage.config())
        is Storage.Drive -> DriveStore({ GoogleDrive.token(context) }, storage.folder)
    }

    /** Applies edits immediately and saves them in the background. */
    suspend fun apply(vararg ops: Op): List<String> {
        ready.join()
        val paths = engine.apply(*ops)
        requestSync()
        return paths
    }

    fun launchApply(vararg ops: Op) = scope.launch { apply(*ops) }

    /** Syncs soon, coalescing bursts of edits into one commit. */
    fun requestSync(delayMs: Long = 1500) {
        syncJob?.cancel()
        syncJob = scope.launch {
            delay(delayMs)
            syncNow()
        }
    }

    suspend fun syncNow() {
        ready.join()
        engine.sync()
    }

    /** Moves to another storage; with `carry`, everyone currently in the app comes along. */
    suspend fun switchStorage(storage: Storage, carry: Boolean) {
        ready.join()
        settingsStore.update { it.copy(storage = storage) }
        engine.switchRemote(remoteFor(storage), carry)
    }

    suspend fun updateSettings(change: (AppSettings) -> AppSettings) = settingsStore.update(change)

    fun files(): Map<String, String> = engine.state.value.files
}
