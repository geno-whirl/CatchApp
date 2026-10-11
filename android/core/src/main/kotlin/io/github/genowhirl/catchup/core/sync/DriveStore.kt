package io.github.genowhirl.catchup.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest

/**
 * A "CatchUp/people" folder in the user's Google Drive, using the `drive.file` scope (the app
 * only sees files it created). `token` returns a fresh OAuth access token.
 */
class DriveStore(
    private val token: suspend () -> String,
    private val folderName: String = "CatchUp",
    private val client: OkHttpClient = OkHttpClient(),
    private val apiUrl: String = "https://www.googleapis.com",
) : RemoteStore {
    override val label = "Google Drive › $folderName"
    private val json = Json { ignoreUnknownKeys = true }
    private var peopleFolderId: String? = null
    /** file name → Drive file id, from the latest snapshot. */
    private var ids = emptyMap<String, String>()

    private suspend fun call(method: String, url: String, body: RequestBody? = null, raw: Boolean = false): Any = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer ${token()}")
            .method(method, body)
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw RemoteException("Can't reach Google Drive. Check your connection.", RemoteException.Kind.NETWORK, e)
        }
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                throw when (r.code) {
                    401 -> RemoteException("Google Drive access has expired. Reconnect it in Settings.", RemoteException.Kind.AUTH)
                    403 -> RemoteException("Google Drive refused access: ${text.take(200)}", RemoteException.Kind.AUTH)
                    404 -> RemoteException("A file disappeared from Google Drive; syncing again will fix it.", RemoteException.Kind.CONFLICT)
                    else -> RemoteException("Google Drive error ${r.code}", RemoteException.Kind.OTHER)
                }
            }
            if (raw) text else if (text.isBlank()) JsonNull else json.parseToJsonElement(text)
        }
    }

    private suspend fun list(query: String): List<Map<String, String>> {
        val out = mutableListOf<Map<String, String>>()
        var pageToken: String? = null
        do {
            val url = "$apiUrl/drive/v3/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("fields", "nextPageToken,files(id,name,md5Checksum)")
                .addQueryParameter("pageSize", "1000")
                .addQueryParameter("spaces", "drive")
                .apply { pageToken?.let { addQueryParameter("pageToken", it) } }
                .build().toString()
            val res = (call("GET", url) as JsonElement).jsonObject
            res["files"]?.jsonArray?.forEach { f ->
                out += f.jsonObject.mapValues { it.value.jsonPrimitive.content }
            }
            pageToken = res["nextPageToken"]?.jsonPrimitive?.content
        } while (pageToken != null)
        return out
    }

    private suspend fun folder(name: String, parent: String?): String {
        val q = "name = '${name.replace("'", "\\'")}' and mimeType = 'application/vnd.google-apps.folder' and trashed = false" +
            (parent?.let { " and '$it' in parents" } ?: " and 'root' in parents")
        list(q).firstOrNull()?.get("id")?.let { return it }
        val meta = buildJsonObject {
            put("name", name)
            put("mimeType", "application/vnd.google-apps.folder")
            put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(parent ?: "root")) })
        }
        val created = (call("POST", "$apiUrl/drive/v3/files?fields=id", meta.toString().toRequestBody(JSON)) as JsonElement)
        return created.jsonObject["id"]!!.jsonPrimitive.content
    }

    private suspend fun peopleFolder(): String =
        peopleFolderId ?: folder("people", folder(folderName, null)).also { peopleFolderId = it }

    override suspend fun snapshot(): RemoteSnapshot {
        val files = list("'${peopleFolder()}' in parents and trashed = false")
            .filter { it["name"]?.endsWith(".md") == true }
        ids = files.associate { it.getValue("name") to it.getValue("id") }
        return RemoteSnapshot(files.associate { "people/${it.getValue("name")}" to (it["md5Checksum"] ?: it.getValue("id")) })
    }

    override suspend fun read(path: String, version: String): String {
        val id = ids[path.removePrefix("people/")] ?: throw RemoteException("Missing $path", RemoteException.Kind.CONFLICT)
        return call("GET", "$apiUrl/drive/v3/files/$id?alt=media", raw = true) as String
    }

    override suspend fun write(base: RemoteSnapshot, changes: Map<String, String?>, message: String) {
        // Drive has no commits: check nothing we're about to change moved since the snapshot.
        val now = snapshot()
        for (path in changes.keys) {
            if (now.versions[path] != base.versions[path]) throw RemoteException("Changed elsewhere", RemoteException.Kind.CONFLICT)
        }
        val folder = peopleFolder()
        for ((path, content) in changes) {
            val name = path.removePrefix("people/")
            val id = ids[name]
            when {
                content == null && id != null ->
                    call("PATCH", "$apiUrl/drive/v3/files/$id", buildJsonObject { put("trashed", true) }.toString().toRequestBody(JSON))
                content == null -> {}
                id != null ->
                    call("PATCH", "$apiUrl/upload/drive/v3/files/$id?uploadType=media", content.toRequestBody(MARKDOWN))
                else -> {
                    val meta = buildJsonObject {
                        put("name", name)
                        put("mimeType", "text/markdown")
                        put("parents", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(folder)) })
                    }
                    val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
                        .addPart(meta.toString().toRequestBody(JSON))
                        .addPart(content.toRequestBody(MARKDOWN))
                        .build()
                    call("POST", "$apiUrl/upload/drive/v3/files?uploadType=multipart&fields=id", body)
                }
            }
        }
    }

    override fun versionOf(content: String): String =
        MessageDigest.getInstance("MD5").digest(content.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private companion object {
        val JSON = "application/json; charset=UTF-8".toMediaType()
        val MARKDOWN = "text/markdown; charset=UTF-8".toMediaType()
    }
}
