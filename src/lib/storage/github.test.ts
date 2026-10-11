import "fake-indexeddb/auto";
import { createHash } from "node:crypto";
import { beforeEach, describe, expect, it } from "vitest";
import { DeviceBackend } from "./backend";
import { decodeBase64, encodeBase64, gitBlobSha, GitHubBackend } from "./github";
import { SyncEngine } from "./sync";

/** A tiny in-memory stand-in for the parts of the GitHub REST API we use. */
class FakeGitHub {
  blobs = new Map<string, string>();
  trees = new Map<string, Record<string, string>>();
  commits = new Map<string, { tree: string; parent?: string; message: string }>();
  head = "";
  requests: string[] = [];
  online = true;
  /** Runs right before the next ref update, to simulate someone else pushing. */
  beforeRefUpdate?: () => Promise<void>;
  private n = 0;

  constructor(files: Record<string, string>) {
    void this.commitFiles(files, "initial");
  }

  private id(prefix: string) {
    return `${prefix}${++this.n}`;
  }

  private storeTree(files: Record<string, string>): string {
    const tree: Record<string, string> = {};
    for (const [path, content] of Object.entries(files)) {
      const bytes = Buffer.from(content);
      const sha = createHash("sha1").update(`blob ${bytes.length}\0`).update(bytes).digest("hex");
      this.blobs.set(sha, content);
      tree[path] = sha;
    }
    const sha = this.id("t");
    this.trees.set(sha, tree);
    return sha;
  }

  commitFiles(files: Record<string, string>, message: string) {
    const current = this.head ? this.files() : {};
    const tree = this.storeTree({ ...current, ...files });
    const sha = this.id("c");
    this.commits.set(sha, { tree, parent: this.head || undefined, message });
    this.head = sha;
  }

  files(): Record<string, string> {
    const tree = this.trees.get(this.commits.get(this.head)!.tree)!;
    return Object.fromEntries(Object.entries(tree).map(([p, sha]) => [p, this.blobs.get(sha)!]));
  }

  fetch: typeof fetch = async (input, init) => {
    if (!this.online) throw new TypeError("Failed to fetch");
    const url = new URL(String(input));
    const method = init?.method ?? "GET";
    const path = url.pathname.replace(/^\/repos\/me\/data/, "");
    this.requests.push(`${method} ${path}`);
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    const json = (data: unknown, status = 200) => new Response(JSON.stringify(data), { status });

    if (path === "") return json({ private: true, permissions: { push: true } });
    if (path === "/branches/main") return json({});
    if (path === "/git/ref/heads/main") return json({ object: { sha: this.head } });
    let m = /^\/git\/commits\/(\w+)$/.exec(path);
    if (m) return json({ tree: { sha: this.commits.get(m[1])!.tree } });
    m = /^\/git\/trees\/(\w+)$/.exec(path);
    if (m && method === "GET") {
      const commit = m[1] === "main" ? this.head : m[1];
      const tree = this.trees.get(this.commits.get(commit)!.tree)!;
      return json({ tree: Object.entries(tree).map(([p, sha]) => ({ path: p, type: "blob", sha })), truncated: false });
    }
    m = /^\/git\/blobs\/(\w+)$/.exec(path);
    if (m) return json({ content: encodeBase64(this.blobs.get(m[1])!) });
    if (path === "/git/trees" && method === "POST") {
      const files = Object.fromEntries(
        Object.entries(this.trees.get(body.base_tree)!).map(([p, sha]) => [p, this.blobs.get(sha)!]),
      );
      for (const entry of body.tree) {
        if (entry.sha === null) delete files[entry.path];
        else files[entry.path] = entry.content;
      }
      return json({ sha: this.storeTree(files) });
    }
    if (path === "/git/commits" && method === "POST") {
      const sha = this.id("c");
      this.commits.set(sha, { tree: body.tree, parent: body.parents[0], message: body.message });
      return json({ sha });
    }
    if (path === "/git/refs/heads/main" && method === "PATCH") {
      if (this.beforeRefUpdate) {
        const hook = this.beforeRefUpdate;
        this.beforeRefUpdate = undefined;
        await hook();
      }
      if (this.commits.get(body.sha)!.parent !== this.head) return json({ message: "Update is not a fast forward" }, 422);
      this.head = body.sha;
      return json({});
    }
    return json({ message: "Not Found" }, 404);
  };
}

const config = { kind: "github" as const, owner: "me", repo: "data", branch: "main", token: "t" };
const MAYA = "---\nname: Maya Patel\n---\n\nNotes.\n";
const entry = { date: "2026-10-11", kind: "Call", notes: ["Hi"], followUps: [] };

describe("encoding", () => {
  it("round-trips UTF-8 through base64", () => {
    expect(decodeBase64(encodeBase64("Zoë 🎂"))).toBe("Zoë 🎂");
  });

  it("computes git's blob SHA", async () => {
    // `printf 'hello\n' | git hash-object --stdin`
    expect(await gitBlobSha("hello\n")).toBe("ce013625030ba8dba906f756967f9e9ca394464a");
  });
});

describe("GitHubBackend", () => {
  let gh: FakeGitHub;
  let backend: GitHubBackend;

  beforeEach(() => {
    gh = new FakeGitHub({
      "README.md": "# Data",
      "people/maya-patel.md": MAYA,
      "people/archive/old.md": "---\nname: Old\n---\n",
    });
    backend = new GitHubBackend(config, gh.fetch);
  });

  it("pulls only people/*.md and reuses unchanged files", async () => {
    const cache = await backend.pull({});
    expect(Object.keys(cache)).toEqual(["people/maya-patel.md"]);
    expect(cache["people/maya-patel.md"].content).toBe(MAYA);

    gh.requests = [];
    await backend.pull(cache);
    expect(gh.requests.filter((r) => r.includes("/blobs/"))).toEqual([]);
  });

  it("commits queued ops in one commit", async () => {
    const cache = await backend.pull({});
    const next = await backend.push(
      [
        { type: "addLog", path: "people/maya-patel.md", entry },
        { type: "create", path: "people/leo.md", content: "---\nname: Leo\n---\n" },
      ],
      cache,
      "Two changes",
    );
    const files = gh.files();
    expect(files["people/maya-patel.md"]).toContain("### 2026-10-11 · Call");
    expect(files["people/leo.md"]).toContain("Leo");
    expect(files["README.md"]).toBe("# Data");
    expect(gh.commits.get(gh.head)!.message).toBe("Two changes");
    // The returned cache matches what's on GitHub, so the next pull downloads nothing.
    gh.requests = [];
    await backend.pull(next);
    expect(gh.requests.filter((r) => r.includes("/blobs/"))).toEqual([]);
  });

  it("re-applies edits on top of changes pushed by someone else", async () => {
    const cache = await backend.pull({});
    gh.beforeRefUpdate = async () => {
      // An agent edits the same file while we're saving.
      gh.commitFiles({ "people/maya-patel.md": MAYA.replace("Notes.", "Notes from an agent.") }, "agent");
    };
    await backend.push([{ type: "addLog", path: "people/maya-patel.md", entry }], cache, "Log");
    const maya = gh.files()["people/maya-patel.md"];
    expect(maya).toContain("Notes from an agent.");
    expect(maya).toContain("### 2026-10-11 · Call");
  });

  it("supports a sub-folder", async () => {
    gh.commitFiles({ "catchup/people/sam.md": "---\nname: Sam\n---\n" }, "sub");
    const sub = new GitHubBackend({ ...config, root: "/catchup/" }, gh.fetch);
    const cache = await sub.pull({});
    expect(Object.keys(cache)).toEqual(["people/sam.md"]);
    await sub.push([{ type: "delete", path: "people/sam.md" }], cache, "rm");
    expect(gh.files()["catchup/people/sam.md"]).toBeUndefined();
    expect(gh.files()["people/maya-patel.md"]).toBe(MAYA);
  });

  it("explains auth errors", async () => {
    const denied = new GitHubBackend(config, async () => new Response('{"message":"Bad credentials"}', { status: 401 }));
    await expect(denied.pull({})).rejects.toThrow(/access token/);
  });
});

describe("SyncEngine", () => {
  it("queues edits offline and saves them when back online", async () => {
    const gh = new FakeGitHub({ "people/maya-patel.md": MAYA });
    const engine = new SyncEngine(() => new GitHubBackend(config, gh.fetch));
    await engine.init();
    await engine.reset();
    await engine.configure(config);
    expect(Object.keys(engine.getState().files)).toEqual(["people/maya-patel.md"]);

    gh.online = false;
    await engine.apply({ type: "addLog", path: "people/maya-patel.md", entry });
    await engine.sync();
    let state = engine.getState();
    expect(state.status).toBe("offline");
    expect(state.pending).toBe(1);
    // The edit is visible right away.
    expect(state.files["people/maya-patel.md"]).toContain("### 2026-10-11 · Call");

    gh.online = true;
    await engine.sync();
    state = engine.getState();
    expect(state.status).toBe("idle");
    expect(state.pending).toBe(0);
    expect(gh.files()["people/maya-patel.md"]).toContain("### 2026-10-11 · Call");

    // Survives a restart.
    const reopened = new SyncEngine(() => new GitHubBackend(config, gh.fetch));
    await reopened.init();
    expect(reopened.getState().files).toEqual(state.files);
  });

  it("copies people across when switching source", async () => {
    const engine = new SyncEngine();
    await engine.init();
    await engine.reset();
    await engine.configure({ kind: "device" });
    await engine.apply({ type: "create", path: "people/sam.md", content: "---\nname: Sam\n---\n" });
    await engine.sync();

    const gh = new FakeGitHub({ "people/maya-patel.md": MAYA });
    const withGitHub = new SyncEngine((c) => (c.kind === "github" ? new GitHubBackend(c, gh.fetch) : new DeviceBackend()));
    await withGitHub.init();
    await withGitHub.configure(config, { bringData: true });
    expect(Object.keys(gh.files()).sort()).toEqual(["people/maya-patel.md", "people/sam.md"]);
  });
});
