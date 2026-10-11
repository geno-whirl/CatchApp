// Stores people in a (private) GitHub repository so the same Markdown files can be read
// and edited by agents, other devices and the user's own editor. All writes go through
// the Git Data API as a single commit, re-applying the queued ops on top of the latest
// commit, so concurrent edits elsewhere are kept.

import { applyOps, type Files, type Op } from "../ops";
import { type Backend, BackendError, type Cache, type SourceConfig } from "./backend";

type GitHubConfig = Extract<SourceConfig, { kind: "github" }>;

interface TreeEntry {
  path: string;
  type: string;
  sha: string;
}

const API = "https://api.github.com";
const MAX_ATTEMPTS = 3;
const CONCURRENCY = 6;

export function encodeBase64(text: string): string {
  let binary = "";
  for (const byte of new TextEncoder().encode(text)) binary += String.fromCharCode(byte);
  return btoa(binary);
}

export function decodeBase64(b64: string): string {
  const binary = atob(b64.replace(/\s/g, ""));
  const bytes = Uint8Array.from(binary, (c) => c.charCodeAt(0));
  return new TextDecoder().decode(bytes);
}

/** The SHA git assigns to a file's content, so we can recognise unchanged files. */
export async function gitBlobSha(text: string): Promise<string> {
  const body = new TextEncoder().encode(text);
  const header = new TextEncoder().encode(`blob ${body.length}\0`);
  const data = new Uint8Array(header.length + body.length);
  data.set(header);
  data.set(body, header.length);
  const hash = await crypto.subtle.digest("SHA-1", data);
  return [...new Uint8Array(hash)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function mapLimit<T, R>(items: T[], limit: number, fn: (item: T) => Promise<R>): Promise<R[]> {
  const results: R[] = new Array(items.length);
  let next = 0;
  const worker = async () => {
    while (next < items.length) {
      const i = next++;
      results[i] = await fn(items[i]);
    }
  };
  await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
  return results;
}

export class GitHubBackend implements Backend {
  private readonly prefix: string;

  constructor(
    private readonly config: GitHubConfig,
    private readonly fetchImpl: typeof fetch = (...args) => fetch(...args),
  ) {
    const root = (config.root ?? "").replace(/^\/+|\/+$/g, "");
    this.prefix = root ? `${root}/` : "";
  }

  private get repoUrl(): string {
    return `${API}/repos/${encodeURIComponent(this.config.owner)}/${encodeURIComponent(this.config.repo)}`;
  }

  private async api<T>(path: string, init: RequestInit = {}): Promise<T> {
    let res: Response;
    try {
      res = await this.fetchImpl(`${this.repoUrl}${path}`, {
        ...init,
        cache: "no-store",
        headers: {
          Accept: "application/vnd.github+json",
          Authorization: `Bearer ${this.config.token}`,
          "X-GitHub-Api-Version": "2022-11-28",
          ...(init.body ? { "Content-Type": "application/json" } : {}),
        },
      });
    } catch {
      throw new BackendError("Can't reach GitHub. Check your connection.");
    }
    if (!res.ok) {
      let detail = "";
      try {
        detail = ((await res.json()) as { message?: string }).message ?? "";
      } catch {
        // Not JSON.
      }
      throw new BackendError(this.explain(res.status, detail), res.status);
    }
    return (await res.json()) as T;
  }

  private explain(status: number, detail: string): string {
    const repo = `${this.config.owner}/${this.config.repo}`;
    if (status === 401) return "GitHub rejected the access token. Create a new one in Settings.";
    if (status === 403 && /rate limit/i.test(detail)) return "GitHub rate limit reached. Try again in a few minutes.";
    if (status === 403) return `The token can't write to ${repo}. Give it "Contents: Read and write" access.`;
    if (status === 404) return `Couldn't find ${repo} (or branch "${this.config.branch}"). Check the name and the token's repository access.`;
    if (status === 409 && /empty/i.test(detail)) return `${repo} is empty. Add a README on GitHub first.`;
    return `GitHub error ${status}${detail ? `: ${detail}` : ""}`;
  }

  /** Checks access; returns whether the repository is private. */
  async check(): Promise<{ private: boolean; canWrite: boolean }> {
    const repo = await this.api<{ private: boolean; permissions?: { push?: boolean } }>("");
    await this.api(`/branches/${encodeURIComponent(this.config.branch)}`);
    return { private: repo.private, canWrite: repo.permissions?.push ?? false };
  }

  private toLocal(remotePath: string): string | null {
    if (!remotePath.startsWith(this.prefix)) return null;
    const local = remotePath.slice(this.prefix.length);
    // Only Markdown files directly inside people/.
    return /^people\/[^/]+\.md$/.test(local) ? local : null;
  }

  private async peopleTree(treeish: string): Promise<{ path: string; sha: string }[]> {
    let tree: { tree: TreeEntry[]; truncated: boolean };
    try {
      tree = await this.api(`/git/trees/${encodeURIComponent(treeish)}?recursive=1`);
    } catch (e) {
      // A brand-new repository with no commits has no tree yet.
      if (e instanceof BackendError && e.status === 409) return [];
      throw e;
    }
    return tree.tree
      .filter((e) => e.type === "blob")
      .map((e) => ({ path: this.toLocal(e.path), sha: e.sha }))
      .filter((e): e is { path: string; sha: string } => e.path !== null);
  }

  private async blob(sha: string): Promise<string> {
    const blob = await this.api<{ content: string }>(`/git/blobs/${sha}`);
    return decodeBase64(blob.content);
  }

  private async load(entries: { path: string; sha: string }[], cache: Cache): Promise<Cache> {
    const loaded = await mapLimit(entries, CONCURRENCY, async ({ path, sha }) => {
      const cached = cache[path];
      const content = cached?.sha === sha ? cached.content : await this.blob(sha);
      return [path, { content, sha }] as const;
    });
    return Object.fromEntries(loaded);
  }

  async pull(cache: Cache): Promise<Cache> {
    return this.load(await this.peopleTree(this.config.branch), cache);
  }

  async push(ops: Op[], cache: Cache, message: string): Promise<Cache> {
    const branch = encodeURIComponent(this.config.branch);
    for (let attempt = 1; ; attempt++) {
      const ref = await this.api<{ object: { sha: string } }>(`/git/ref/heads/${branch}`);
      const head = ref.object.sha;
      const commit = await this.api<{ tree: { sha: string } }>(`/git/commits/${head}`);
      const entries = await this.peopleTree(head);

      // Only download the files these ops touch; the rest just need to exist for name checks.
      const touched = new Set(ops.map((op) => op.path));
      const latest = await this.load(entries.filter((e) => touched.has(e.path)), cache);
      const base: Files = Object.fromEntries(entries.map((e) => [e.path, latest[e.path]?.content ?? ""]));
      const result = applyOps(base, ops);

      const changes: { path: string; content: string | null }[] = [];
      for (const path of new Set([...Object.keys(base), ...Object.keys(result)])) {
        if (!(path in result)) changes.push({ path, content: null });
        else if (!(path in base) || (touched.has(path) && result[path] !== base[path])) {
          changes.push({ path, content: result[path] });
        }
      }

      const next: Cache = { ...cache };
      if (changes.length > 0) {
        const tree = await this.api<{ sha: string }>("/git/trees", {
          method: "POST",
          body: JSON.stringify({
            base_tree: commit.tree.sha,
            tree: changes.map(({ path, content }) =>
              content === null
                ? { path: this.prefix + path, mode: "100644", type: "blob", sha: null }
                : { path: this.prefix + path, mode: "100644", type: "blob", content },
            ),
          }),
        });
        const created = await this.api<{ sha: string }>("/git/commits", {
          method: "POST",
          body: JSON.stringify({ message, tree: tree.sha, parents: [head] }),
        });
        try {
          await this.api(`/git/refs/heads/${branch}`, {
            method: "PATCH",
            body: JSON.stringify({ sha: created.sha, force: false }),
          });
        } catch (e) {
          // Someone else pushed in the meantime: start over from their commit.
          if (e instanceof BackendError && e.status === 422 && attempt < MAX_ATTEMPTS) continue;
          throw e;
        }
      }

      for (const { path, content } of changes) {
        if (content === null) delete next[path];
        else next[path] = { content, sha: await gitBlobSha(content) };
      }
      return next;
    }
  }
}
