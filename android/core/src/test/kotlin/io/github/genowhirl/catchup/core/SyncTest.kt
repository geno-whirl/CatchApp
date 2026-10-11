package io.github.genowhirl.catchup.core

import io.github.genowhirl.catchup.core.sync.CachedFile
import io.github.genowhirl.catchup.core.sync.DriveStore
import io.github.genowhirl.catchup.core.sync.GitHubConfig
import io.github.genowhirl.catchup.core.sync.GitHubStore
import io.github.genowhirl.catchup.core.sync.MemoryPersistence
import io.github.genowhirl.catchup.core.sync.RemoteException
import io.github.genowhirl.catchup.core.sync.SyncEngine
import io.github.genowhirl.catchup.core.sync.SyncStatus
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Just enough of GitHub's REST API, in memory. */
private class FakeGitHub(initial: Map<String, String>) : Dispatcher() {
    val blobs = mutableMapOf<String, String>()
    val trees = mutableMapOf<String, Map<String, String>>()
    val commits = mutableMapOf<String, Triple<String, String?, String>>() // sha → (tree, parent, message)
    var head = ""
    var online = true
    var beforeRefUpdate: (() -> Unit)? = null
    val requests = mutableListOf<String>()
    private var n = 0

    init { commitFiles(initial, "initial") }

    private fun sha(content: String): String {
        val b = content.toByteArray()
        val md = MessageDigest.getInstance("SHA-1")
        md.update("blob ${b.size}\u0000".toByteArray()); md.update(b)
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun storeTree(files: Map<String, String>): String {
        val t = files.mapValues { (_, c) -> sha(c).also { blobs[it] = c } }
        return "t${++n}".also { trees[it] = t }
    }

    fun files(): Map<String, String> = trees.getValue(commits.getValue(head).first).mapValues { blobs.getValue(it.value) }

    fun commitFiles(changes: Map<String, String>, message: String) {
        val current = if (head.isEmpty()) emptyMap() else files()
        val c = "c${++n}"
        commits[c] = Triple(storeTree(current + changes), head.ifEmpty { null }, message)
        head = c
    }

    fun message() = commits.getValue(head).third

    private fun ok(body: Any) = MockResponse().setBody(body.toString())

    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path!!.substringBefore('?').removePrefix("/repos/me/data")
        requests += "${request.method} $path"
        val body = request.body.readUtf8().takeIf { it.isNotEmpty() }?.let { Json.parseToJsonElement(it).jsonObject }
        fun obj(vararg pairs: Pair<String, Any?>) = JsonObject(pairs.associate { (k, v) -> k to (v as? kotlinx.serialization.json.JsonElement ?: JsonPrimitive(v?.toString())) })
        return when {
            path == "" -> ok(obj("private" to true, "permissions" to obj("push" to true)))
            path == "/git/ref/heads/main" -> ok(obj("object" to obj("sha" to head)))
            path.startsWith("/git/commits/") -> ok(obj("tree" to obj("sha" to commits.getValue(path.substringAfterLast('/')).first)))
            path.startsWith("/git/trees/") && request.method == "GET" -> {
                val t = trees.getValue(path.substringAfterLast('/'))
                ok(obj("tree" to JsonArray(t.map { (p, s) -> obj("path" to p, "type" to "blob", "sha" to s) })))
            }
            path.startsWith("/git/blobs/") -> ok(obj("content" to Base64.getEncoder().encodeToString(blobs.getValue(path.substringAfterLast('/')).toByteArray())))
            path == "/git/trees" -> {
                val files = trees.getValue(body!!["base_tree"]!!.jsonPrimitive.content).mapValues { blobs.getValue(it.value) }.toMutableMap()
                for (e in body["tree"]!!.jsonArray.map { it.jsonObject }) {
                    val p = e["path"]!!.jsonPrimitive.content
                    if (e["sha"] is JsonNull) files.remove(p) else files[p] = e["content"]!!.jsonPrimitive.content
                }
                ok(obj("sha" to storeTree(files)))
            }
            path == "/git/commits" -> {
                val c = "c${++n}"
                commits[c] = Triple(body!!["tree"]!!.jsonPrimitive.content, body["parents"]!!.jsonArray[0].jsonPrimitive.content, body["message"]!!.jsonPrimitive.content)
                ok(obj("sha" to c))
            }
            path == "/git/refs/heads/main" -> {
                beforeRefUpdate?.also { beforeRefUpdate = null }?.invoke()
                val c = body!!["sha"]!!.jsonPrimitive.content
                if (commits.getValue(c).second != head) MockResponse().setResponseCode(422).setBody("""{"message":"Update is not a fast forward"}""")
                else { head = c; ok("{}") }
            }
            else -> MockResponse().setResponseCode(404).setBody("""{"message":"Not Found"}""")
        }
    }
}

class SyncTest {
    private val server = MockWebServer()
    private val maya = "---\nname: Maya Patel\n---\n\nNotes.\n"
    private val entry = InteractionInput("2026-10-11", kind = "Call", notes = listOf("Hi"))

    @AfterTest fun stop() = server.shutdown()

    private fun github(fake: FakeGitHub, root: String = ""): GitHubStore {
        server.dispatcher = fake
        // "Offline": the request never leaves the phone.
        val client = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            if (!fake.online) throw java.io.IOException("offline")
            chain.proceed(chain.request())
        }.build()
        return GitHubStore(GitHubConfig("me", "data", "main", root, "t"), client, server.url("/").toString().trimEnd('/'))
    }

    @Test fun `pulls people, commits edits once, and reuses unchanged files`() = runBlocking {
        val fake = FakeGitHub(mapOf("README.md" to "# Data", "people/maya-patel.md" to maya, "people/archive/old.md" to "x"))
        val engine = SyncEngine(MemoryPersistence(), github(fake))
        engine.sync()
        assertEquals(setOf("people/maya-patel.md"), engine.state.value.files.keys)

        engine.apply(
            Op.Log("people/maya-patel.md", entry),
            Op.Create("people/leo.md", "---\nname: Leo\n---\n"),
        )
        engine.sync()
        val files = fake.files()
        assertTrue(files.getValue("people/maya-patel.md").contains("### 2026-10-11 · Call"))
        assertEquals("# Data", files["README.md"])
        assertTrue(fake.message().startsWith("CatchUp: 2 changes"))
        assertEquals(0, engine.state.value.pending)

        fake.requests.clear()
        engine.sync()
        assertEquals(emptyList(), fake.requests.filter { it.contains("/blobs/") })
    }

    @Test fun `edits made elsewhere meanwhile are kept`() = runBlocking {
        val fake = FakeGitHub(mapOf("people/maya-patel.md" to maya))
        val engine = SyncEngine(MemoryPersistence(), github(fake))
        engine.sync()
        fake.beforeRefUpdate = { fake.commitFiles(mapOf("people/maya-patel.md" to maya.replace("Notes.", "Notes from an agent.")), "agent") }
        engine.apply(Op.Log("people/maya-patel.md", entry))
        engine.sync()
        val text = fake.files().getValue("people/maya-patel.md")
        assertTrue(text.contains("Notes from an agent."))
        assertTrue(text.contains("### 2026-10-11 · Call"))
    }

    @Test fun `works offline and catches up later, surviving a restart`() = runBlocking {
        val fake = FakeGitHub(mapOf("people/maya-patel.md" to maya))
        val persistence = MemoryPersistence()
        val engine = SyncEngine(persistence, github(fake))
        engine.sync()
        fake.online = false
        engine.apply(Op.Log("people/maya-patel.md", entry))
        engine.sync()
        assertEquals(SyncStatus.OFFLINE, engine.state.value.status)
        assertEquals(1, engine.state.value.pending)
        assertTrue(engine.state.value.files.getValue("people/maya-patel.md").contains("Call"))

        val restarted = SyncEngine(persistence, github(fake))
        restarted.load()
        assertEquals(1, restarted.state.value.pending)
        fake.online = true
        restarted.sync()
        assertEquals(SyncStatus.IDLE, restarted.state.value.status)
        assertTrue(fake.files().getValue("people/maya-patel.md").contains("### 2026-10-11 · Call"))
    }

    @Test fun `sub-folders and auth errors`() = runBlocking {
        val fake = FakeGitHub(mapOf("catchup/people/sam.md" to "---\nname: Sam\n---\n", "people/maya-patel.md" to maya))
        val engine = SyncEngine(MemoryPersistence(), github(fake, root = "/catchup/"))
        engine.sync()
        assertEquals(setOf("people/sam.md"), engine.state.value.files.keys)
        engine.apply(Op.Delete("people/sam.md"))
        engine.sync()
        assertEquals(setOf("people/maya-patel.md"), fake.files().keys)

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest) = MockResponse().setResponseCode(401).setBody("""{"message":"Bad credentials"}""")
        }
        val denied = GitHubStore(GitHubConfig("me", "data", token = "x"), apiUrl = server.url("/").toString().trimEnd('/'))
        val e = assertFailsWith<RemoteException> { denied.snapshot() }
        assertEquals(RemoteException.Kind.AUTH, e.kind)
    }

    @Test fun `local only, then moving everyone to GitHub`() = runBlocking {
        val engine = SyncEngine(MemoryPersistence(), null)
        engine.apply(Op.Create("people/sam.md", "---\nname: Sam\n---\n"))
        assertEquals(0, engine.state.value.pending)
        val fake = FakeGitHub(mapOf("people/maya-patel.md" to maya))
        engine.switchRemote(github(fake), carry = true)
        assertEquals(setOf("people/maya-patel.md", "people/sam.md"), fake.files().keys)
        assertEquals(setOf("people/maya-patel.md", "people/sam.md"), engine.state.value.files.keys)
    }

    @Test fun `computes git blob ids`() {
        // `printf 'hello\n' | git hash-object --stdin`
        val store = GitHubStore(GitHubConfig("a", "b", token = ""))
        assertEquals("ce013625030ba8dba906f756967f9e9ca394464a", store.versionOf("hello\n"))
        assertEquals("b1946ac92492d2347c6235b4d2611184", DriveStore({ "" }).versionOf("hello\n"))
    }

    @Test fun `google drive round trip`() = runBlocking {
        // A minimal Drive: folders and files keyed by id.
        val files = mutableMapOf<String, Triple<String, String, String>>() // id → (name, parent, content)
        val folders = mutableMapOf<String, Pair<String, String>>() // id → (name, parent)
        var n = 0
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl!!
                val body = request.body.readUtf8()
                fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
                return when {
                    request.method == "GET" && url.encodedPath == "/drive/v3/files" -> {
                        val q = url.queryParameter("q")!!
                        val parent = Regex("'([^']+)' in parents").find(q)!!.groupValues[1]
                        val list = if (q.contains("folder")) {
                            val name = Regex("name = '([^']+)'").find(q)!!.groupValues[1]
                            folders.filter { it.value == name to parent }.map { """{"id":"${it.key}","name":"$name"}""" }
                        } else files.filter { it.value.second == parent }.map { (id, f) -> """{"id":"$id","name":"${f.first}","md5Checksum":"${md5(f.third)}"}""" }
                        MockResponse().setBody("""{"files":[${list.joinToString(",")}]}""")
                    }
                    request.method == "POST" && url.encodedPath == "/drive/v3/files" -> {
                        val meta = Json.parseToJsonElement(body).jsonObject
                        val id = "f${++n}"
                        folders[id] = meta["name"]!!.jsonPrimitive.content to meta["parents"]!!.jsonArray[0].jsonPrimitive.content
                        MockResponse().setBody("""{"id":"$id"}""")
                    }
                    request.method == "POST" && url.encodedPath == "/upload/drive/v3/files" -> {
                        val metaJson = Regex("\\{.*\\}").find(body)!!.value
                        val meta = Json.parseToJsonElement(metaJson).jsonObject
                        val content = body.substringAfter("text/markdown; charset=UTF-8").substringAfter("\r\n\r\n").substringBeforeLast("\r\n--")
                        val id = "f${++n}"
                        files[id] = Triple(meta["name"]!!.jsonPrimitive.content, meta["parents"]!!.jsonArray[0].jsonPrimitive.content, content)
                        MockResponse().setBody("""{"id":"$id"}""")
                    }
                    request.method == "PATCH" && url.encodedPath.startsWith("/upload/drive/v3/files/") -> {
                        val id = url.pathSegments.last()
                        files[id] = files.getValue(id).copy(third = body)
                        MockResponse().setBody("{}")
                    }
                    request.method == "PATCH" -> {
                        files.remove(url.pathSegments.last())
                        MockResponse().setBody("{}")
                    }
                    request.method == "GET" && url.queryParameter("alt") == "media" ->
                        MockResponse().setBody(files.getValue(url.pathSegments.last()).third)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        val store = { DriveStore({ "token" }, apiUrl = server.url("/").toString().trimEnd('/')) }
        val engine = SyncEngine(MemoryPersistence(), store())
        engine.apply(Op.Create("people/maya-patel.md", maya))
        engine.sync()
        assertEquals(SyncStatus.IDLE, engine.state.value.status, engine.state.value.error)
        assertEquals(listOf("maya-patel.md"), files.values.map { it.first })

        engine.apply(Op.Log("people/maya-patel.md", entry))
        engine.sync()
        assertTrue(files.values.single().third.contains("### 2026-10-11 · Call"))

        // A second device sees it.
        val other = SyncEngine(MemoryPersistence(), store())
        other.sync()
        assertEquals(engine.state.value.files, other.state.value.files)
        assertEquals(CachedFile::class, CachedFile("").let { it::class })
    }
}
