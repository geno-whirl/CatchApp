import { type FormEvent, useState } from "react";
import type { SourceConfig } from "../lib/storage/backend";
import { GitHubBackend } from "../lib/storage/github";
import { engine, useSync } from "../store";

const TOKEN_URL = "https://github.com/settings/personal-access-tokens/new";

/** Connects CatchUp to a private GitHub repository holding the people/ folder. */
export function GitHubConnect({ onDone }: { onDone?: () => void }) {
  const { config, files } = useSync();
  const current = config?.kind === "github" ? config : null;
  const [repo, setRepo] = useState(current ? `${current.owner}/${current.repo}` : "");
  const [branch, setBranch] = useState(current?.branch ?? "main");
  const [root, setRoot] = useState(current?.root ?? "");
  const [token, setToken] = useState(current?.token ?? "");
  const [bring, setBring] = useState(true);
  const [publicOk, setPublicOk] = useState(false);
  const [isPublic, setIsPublic] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const peopleHere = Object.keys(files).length;

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const m = /^(?:https:\/\/github\.com\/)?([\w.-]+)\/([\w.-]+?)(?:\.git)?\/?$/.exec(repo.trim());
    if (!m) {
      setError("Enter the repository as owner/name, e.g. jane/catchup-data.");
      return;
    }
    const cfg: SourceConfig = { kind: "github", owner: m[1], repo: m[2], branch: branch.trim() || "main", root: root.trim() || undefined, token: token.trim() };
    setBusy(true);
    setError(null);
    try {
      const access = await new GitHubBackend(cfg).check();
      if (!access.canWrite) throw new Error(`The token can read ${m[1]}/${m[2]} but not write to it. Give it “Contents: Read and write”.`);
      if (!access.private && !publicOk) {
        setIsPublic(true);
        return;
      }
      await engine.configure(cfg, { bringData: bring && peopleHere > 0 });
      onDone?.();
    } catch (err) {
      setError(err instanceof Error ? err.message : String(err));
    } finally {
      setBusy(false);
    }
  };

  return (
    <form className="form" onSubmit={submit}>
      <ol className="steps small">
        <li>
          Create a <strong>private</strong> repository on GitHub (e.g. <code>catchup-data</code>), ticking “Add a README”.
        </li>
        <li>
          Create a{" "}
          <a href={TOKEN_URL} target="_blank" rel="noreferrer">fine-grained access token</a>: Repository access → Only select
          repositories → your data repo; Permissions → Contents → <strong>Read and write</strong>.
        </li>
        <li>Paste both below. The token is stored only on this device.</li>
      </ol>
      <label>
        <span>Repository</span>
        <input value={repo} onChange={(e) => setRepo(e.target.value)} placeholder="your-name/catchup-data" required autoCapitalize="off" autoCorrect="off" spellCheck={false} />
      </label>
      <label>
        <span>Access token</span>
        <input type="password" value={token} onChange={(e) => setToken(e.target.value)} placeholder="github_pat_…" required autoComplete="off" />
      </label>
      <details>
        <summary className="small muted">Advanced</summary>
        <div className="two-col">
          <label>
            <span>Branch</span>
            <input value={branch} onChange={(e) => setBranch(e.target.value)} autoCapitalize="off" />
          </label>
          <label>
            <span>Folder in repo (optional)</span>
            <input value={root} onChange={(e) => setRoot(e.target.value)} placeholder="contains people/" autoCapitalize="off" />
          </label>
        </div>
      </details>
      {peopleHere > 0 && (!current || current.repo !== repo) && (
        <label className="check">
          <input type="checkbox" checked={bring} onChange={(e) => setBring(e.target.checked)} />
          Copy the {peopleHere} {peopleHere === 1 ? "person" : "people"} currently in CatchUp into the repository
        </label>
      )}
      {isPublic && (
        <div className="callout warning">
          <strong>This repository is public.</strong> Anyone could read your notes about friends and family. Make it private on
          GitHub (Settings → Danger zone → Change visibility), or continue anyway.
          <label className="check">
            <input type="checkbox" checked={publicOk} onChange={(e) => setPublicOk(e.target.checked)} />
            I understand — use it anyway
          </label>
        </div>
      )}
      {error && <p className="error">{error}</p>}
      <button className="button primary" type="submit" disabled={busy}>
        {busy ? "Connecting…" : current ? "Save" : "Connect"}
      </button>
    </form>
  );
}
