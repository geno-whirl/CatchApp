package io.github.genowhirl.catchup.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64

data class GitHubConfig(
    val owner: String,
    val repo: String,
    val branch: String = "main",
    /** Folder inside the repo that contains `people/` ("" = repository root). */
    val root: String = "",
    val token: String,
)

/**
 * A (private) GitHub repository. Every sync is one commit made with the Git Data API; if
 * someone else pushed meanwhile, the update is rejected and the engine retries on top of it.
 */
class GitHubStore(
    private val config: GitHubConfig,
    private val client: OkHttpClient = OkHttpClient(),
    private val apiUrl: String = "https://api.github.com",
) : RemoteStore {
    override val label = "${config.owner}/${config.repo}"
    private val prefix = config.root.trim('/').let { if (it.isEmpty()) "" else "$it/" }
    private val json = Json { ignoreUnknownKeys = true }
    private val repoUrl get() = "$apiUrl/repos/${config.owner}/${config.repo}"

    private suspend fun call(method: String, path: String, body: JsonElement? = null): JsonElement = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(repoUrl + path)
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer ${config.token}")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .method(method, body?.toString()?.toRequestBody("application/json".toMediaType()))
            .build()
        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw RemoteException("Can't reach GitHub. Check your connection.", RemoteException.Kind.NETWORK, e)
        }
        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) {
                val detail = runCatching { json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.content }.getOrNull().orEmpty()
                throw error(r.code, detail)
            }
            if (text.isBlank()) JsonNull else json.parseToJsonElement(text)
        }
    }

    private fun error(code: Int, detail: String): RemoteException {
        val repo = label
        return when {
            code == 401 -> RemoteException("GitHub didn't accept the access token. Create a new one in Settings.", RemoteException.Kind.AUTH)
            code == 403 && detail.contains("rate limit", true) -> RemoteException("GitHub's rate limit was reached. Try again in a few minutes.", RemoteException.Kind.OTHER)
            code == 403 -> RemoteException("The token can't write to $repo. Give it \"Contents: Read and write\".", RemoteException.Kind.AUTH)
            code == 404 -> RemoteException("Couldn't find $repo (or branch \"${config.branch}\"). Check the name and the token's repository access.", RemoteException.Kind.NOT_FOUND)
            code == 409 && detail.contains("empty", true) -> RemoteException("$repo is empty.", RemoteException.Kind.NOT_FOUND)
            code == 422 -> RemoteException("Someone else saved first.", RemoteException.Kind.CONFLICT)
            else -> RemoteException("GitHub error $code${if (detail.isNotEmpty()) ": $detail" else ""}", RemoteException.Kind.OTHER)
        }
    }

    private fun local(remotePath: String): String? {
        if (!remotePath.startsWith(prefix)) return null
        val p = remotePath.removePrefix(prefix)
        return p.takeIf { Regex("^people/[^/]+\\.md$").matches(it) }
    }

    /** Checks the token and repository; returns whether the repository is private. */
    suspend fun check(): Boolean {
        val repo = call("GET", "").jsonObject
        val canPush = repo["permissions"]?.jsonObject?.get("push")?.jsonPrimitive?.content == "true"
        if (!canPush) throw RemoteException("The token can read $label but not write to it. Give it \"Contents: Read and write\".", RemoteException.Kind.AUTH)
        return repo["private"]?.jsonPrimitive?.content == "true"
    }

    override suspend fun snapshot(): RemoteSnapshot {
        val head = try {
            call("GET", "/git/ref/heads/${config.branch}").jsonObject["object"]!!.jsonObject["sha"]!!.jsonPrimitive.content
        } catch (e: RemoteException) {
            // A brand-new repository has no commits yet.
            if (e.kind == RemoteException.Kind.NOT_FOUND && e.message?.contains("empty") == true) return RemoteSnapshot(emptyMap(), null)
            throw e
        }
        val treeSha = call("GET", "/git/commits/$head").jsonObject["tree"]!!.jsonObject["sha"]!!.jsonPrimitive.content
        val tree = call("GET", "/git/trees/$treeSha?recursive=1").jsonObject["tree"]!!.jsonArray
        val versions = tree.map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.content == "blob" }
            .mapNotNull { e -> local(e["path"]!!.jsonPrimitive.content)?.let { it to e["sha"]!!.jsonPrimitive.content } }
            .toMap()
        return RemoteSnapshot(versions, "$head:$treeSha")
    }

    override suspend fun read(path: String, version: String): String {
        val content = call("GET", "/git/blobs/$version").jsonObject["content"]!!.jsonPrimitive.content
        return String(Base64.getMimeDecoder().decode(content), Charsets.UTF_8)
    }

    override suspend fun write(base: RemoteSnapshot, changes: Map<String, String?>, message: String) {
        val token = base.token
        if (token == null) {
            // Empty repository: the Git Data API needs a first commit, so use the contents API.
            for ((path, content) in changes) {
                if (content == null) continue
                call("PUT", "/contents/${prefix + path}", buildJsonObject {
                    put("message", message)
                    put("content", Base64.getEncoder().encodeToString(content.toByteArray()))
                    put("branch", config.branch)
                })
            }
            return
        }
        val (head, treeSha) = token.split(":")
        val tree = call("POST", "/git/trees", buildJsonObject {
            put("base_tree", treeSha)
            put("tree", buildJsonArray {
                for ((path, content) in changes) add(buildJsonObject {
                    put("path", prefix + path)
                    put("mode", "100644")
                    put("type", "blob")
                    if (content == null) put("sha", JsonNull) else put("content", content)
                })
            })
        }).jsonObject["sha"]!!.jsonPrimitive.content
        val commit = call("POST", "/git/commits", buildJsonObject {
            put("message", message)
            put("tree", tree)
            put("parents", JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(head))))
        }).jsonObject["sha"]!!.jsonPrimitive.content
        call("PATCH", "/git/refs/heads/${config.branch}", buildJsonObject {
            put("sha", commit)
            put("force", false)
        })
    }

    override fun versionOf(content: String): String {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
        md.update(bytes)
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
