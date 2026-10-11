package io.github.genowhirl.catchup.core.sync

import io.github.genowhirl.catchup.core.Op
import io.github.genowhirl.catchup.core.applyOp
import io.github.genowhirl.catchup.core.applyOps
import io.github.genowhirl.catchup.core.commitMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable

/** What's on the remote side: each file's version (git blob SHA, Drive checksum…). */
data class RemoteSnapshot(val versions: Map<String, String>, val token: String? = null)

class RemoteException(message: String, val kind: Kind, cause: Throwable? = null) : Exception(message, cause) {
    enum class Kind { NETWORK, AUTH, NOT_FOUND, CONFLICT, OTHER }
}

/** A place people files are synced to: a GitHub repository, a Google Drive folder… */
interface RemoteStore {
    val label: String
    suspend fun snapshot(): RemoteSnapshot

    suspend fun read(path: String, version: String): String

    /** Writes changes (null = delete) made on top of `base`; throws CONFLICT if the remote moved. */
    suspend fun write(base: RemoteSnapshot, changes: Map<String, String?>, message: String)

    /** The version the remote will assign to this content, if it can be computed locally. */
    fun versionOf(content: String): String?
}

@Serializable
data class CachedFile(val content: String, val version: String? = null)

@Serializable
data class SyncData(
    val files: Map<String, CachedFile> = emptyMap(),
    val outbox: List<Op> = emptyList(),
    val lastSync: Long? = null,
)

interface SyncPersistence {
    suspend fun load(): SyncData

    suspend fun save(data: SyncData)
}

enum class SyncStatus { IDLE, SYNCING, OFFLINE, ERROR }

data class SyncState(
    /** The files as the app should show them: last synced copy + queued edits. */
    val files: Map<String, String> = emptyMap(),
    val pending: Int = 0,
    val status: SyncStatus = SyncStatus.IDLE,
    val error: String? = null,
    val lastSync: Long? = null,
    val loaded: Boolean = false,
)

/**
 * Keeps a local copy of the data plus a queue of edits not yet saved remotely. Edits apply
 * instantly and work offline; syncing re-applies them on top of the latest remote files.
 * Without a remote, the local copy is the data.
 */
class SyncEngine(
    private val persistence: SyncPersistence,
    private var remote: RemoteStore?,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Guards `data`; held only briefly, never across network calls. */
    private val dataLock = Mutex()
    /** One sync at a time. */
    private val syncLock = Mutex()
    private var data = SyncData()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private fun publish(status: SyncStatus = _state.value.status, error: String? = _state.value.error) {
        val base = data.files.mapValues { it.value.content }
        _state.value = SyncState(applyOps(base, data.outbox), data.outbox.size, status, error, data.lastSync, loaded = true)
    }

    private suspend fun update(status: SyncStatus? = null, error: String? = null, clearError: Boolean = false, change: (SyncData) -> SyncData) =
        dataLock.withLock {
            data = change(data)
            persistence.save(data)
            publish(status ?: _state.value.status, if (clearError) null else error ?: _state.value.error)
        }

    suspend fun load() = dataLock.withLock {
        data = persistence.load()
        publish(SyncStatus.IDLE, null)
    }

    val hasRemote: Boolean get() = remote != null

    /**
     * Switches storage. With `carry`, the people currently in the app are added to the new
     * place (files that already exist there are left alone).
     */
    suspend fun switchRemote(newRemote: RemoteStore?, carry: Boolean) {
        syncLock.withLock {
            val current = _state.value.files
            remote = newRemote
            update(SyncStatus.IDLE, clearError = true) {
                if (newRemote == null) SyncData(current.mapValues { CachedFile(it.value) })
                else SyncData(outbox = if (carry) current.map { (p, c) -> Op.Create(p, c, skipIfExists = true) } else emptyList())
            }
        }
        sync()
    }

    /** Applies edits now (the UI updates at once) and queues them for the remote. */
    suspend fun apply(vararg ops: Op): List<String> {
        var paths = emptyList<String>()
        update { d ->
            var files = applyOps(d.files.mapValues { it.value.content }, d.outbox)
            paths = ops.map { op -> applyOp(files, op).also { files = it.first }.second }
            if (remote == null) {
                // Local only: the change is the data.
                SyncData(applyOps(d.files.mapValues { it.value.content }, ops.toList()).mapValues { CachedFile(it.value) }, emptyList(), d.lastSync)
            } else {
                d.copy(outbox = d.outbox + ops)
            }
        }
        return paths
    }

    /** Saves queued edits, then fetches the latest files. Safe to call any time. */
    suspend fun sync() {
        val store = remote ?: return
        syncLock.withLock {
            dataLock.withLock { publish(SyncStatus.SYNCING, null) }
            try {
                var attempt = 0
                while (true) {
                    attempt++
                    val snapshot = store.snapshot()
                    val fresh = refresh(store, snapshot, dataLock.withLock { data.files })
                    update { it.copy(files = fresh) }
                    val sending = dataLock.withLock { data.outbox }
                    if (sending.isEmpty()) break

                    val base = fresh.mapValues { it.value.content }
                    val result = applyOps(base, sending)
                    val changes = (base.keys + result.keys).filter { base[it] != result[it] }.associateWith { result[it] }
                    try {
                        if (changes.isNotEmpty()) store.write(snapshot, changes, commitMessage(sending))
                    } catch (e: RemoteException) {
                        if (e.kind == RemoteException.Kind.CONFLICT && attempt < 4) continue
                        throw e
                    }
                    update { d ->
                        val files = d.files.toMutableMap()
                        for ((path, content) in changes) {
                            if (content == null) files.remove(path) else files[path] = CachedFile(content, store.versionOf(content))
                        }
                        // Keep anything queued while we were saving.
                        SyncData(files, d.outbox.drop(sending.size), d.lastSync)
                    }
                    if (dataLock.withLock { data.outbox.isEmpty() }) break
                }
                update(SyncStatus.IDLE, clearError = true) { it.copy(lastSync = clock()) }
            } catch (e: RemoteException) {
                dataLock.withLock {
                    publish(if (e.kind == RemoteException.Kind.NETWORK) SyncStatus.OFFLINE else SyncStatus.ERROR, e.message)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Unexpected responses must never lose queued edits or crash the app.
                dataLock.withLock { publish(SyncStatus.ERROR, "Sync failed: ${e.message ?: e::class.simpleName}") }
            }
        }
    }

    /** Downloads files whose remote version differs from the cached one. */
    private suspend fun refresh(store: RemoteStore, snapshot: RemoteSnapshot, cache: Map<String, CachedFile>): Map<String, CachedFile> =
        coroutineScope {
            val limit = Semaphore(6)
            snapshot.versions.map { (path, version) ->
                async {
                    val cached = cache[path]
                    path to if (cached != null && cached.version == version) cached
                    else CachedFile(limit.withPermit { store.read(path, version) }, version)
                }
            }.awaitAll().toMap()
        }
}

/** For tests and previews. */
class MemoryPersistence(var data: SyncData = SyncData()) : SyncPersistence {
    override suspend fun load() = data

    override suspend fun save(data: SyncData) {
        this.data = data
    }
}
