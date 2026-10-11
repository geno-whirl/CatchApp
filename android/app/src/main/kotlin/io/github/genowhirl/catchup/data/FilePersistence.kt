package io.github.genowhirl.catchup.data

import io.github.genowhirl.catchup.core.sync.SyncData
import io.github.genowhirl.catchup.core.sync.SyncPersistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

/** Stores people and the offline edit queue in one JSON file, written atomically. */
class FilePersistence(private val file: File) : SyncPersistence {
    private val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }

    override suspend fun load(): SyncData = withContext(Dispatchers.IO) {
        if (!file.exists()) SyncData()
        else runCatching { json.decodeFromString(SyncData.serializer(), file.readText()) }.getOrElse { SyncData() }
    }

    override suspend fun save(data: SyncData) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(SyncData.serializer(), data))
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
        Unit
    }
}
