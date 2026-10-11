import { zipSync, strToU8 } from "fflate";
import { useEffect, useState } from "react";
import { GitHubConnect } from "../components/GitHubConnect";
import { birthdaysCalendar } from "../lib/calendar";
import { toIsoDate } from "../lib/dates";
import { canPromptInstall, isInstalled, onInstallChange, promptInstall } from "../pwa";
import { apply, download, engine, exampleOps, toast, usePeople, useSync } from "../store";

function timeAgo(ms: number): string {
  const s = Math.round((Date.now() - ms) / 1000);
  if (s < 60) return "just now";
  if (s < 3600) return `${Math.floor(s / 60)} min ago`;
  if (s < 86400) return `${Math.floor(s / 3600)} h ago`;
  return new Date(ms).toLocaleDateString();
}

export function SyncSummary() {
  const { config, status, error, pending, syncedAt } = useSync();
  if (!config) return null;
  return (
    <div>
      <p>
        {config.kind === "github" ? (
          <>
            Synced with{" "}
            <a href={`https://github.com/${config.owner}/${config.repo}`} target="_blank" rel="noreferrer">
              {config.owner}/{config.repo}
            </a>
            {config.root ? ` (${config.root}/)` : ""}
          </>
        ) : (
          "Stored on this device only"
        )}
      </p>
      <p className="muted small">
        {status === "syncing" && "Syncing…"}
        {status === "idle" && (syncedAt ? `Up to date · ${timeAgo(syncedAt)}` : "Up to date")}
        {status === "offline" && `Offline${pending ? ` · ${pending} change${pending > 1 ? "s" : ""} waiting` : ""}`}
        {status === "error" && <span className="error">{error}</span>}
      </p>
    </div>
  );
}

export function Settings() {
  const { config, files } = useSync();
  const { people, problems } = usePeople();
  const [changing, setChanging] = useState(false);
  const [, rerender] = useState(0);
  useEffect(() => onInstallChange(() => rerender((n) => n + 1)), []);

  const backup = () => {
    const zip = zipSync(Object.fromEntries(Object.entries(files).map(([path, text]) => [path, strToU8(text)])));
    download(`catchup-backup-${toIsoDate(new Date())}.zip`, zip as BlobPart, "application/zip");
  };

  const useDevice = async () => {
    if (!confirm("Keep people on this device only? They'll be copied here; the GitHub repository is left as it is.")) return;
    await engine.configure({ kind: "device" }, { bringData: true });
    setChanging(false);
    toast("Now storing on this device");
  };

  const reset = async () => {
    if (!confirm("Remove all CatchUp data and settings from this device? Data in a connected GitHub repository is not affected.")) return;
    await engine.reset();
    location.hash = "#/";
  };

  return (
    <div className="settings">
      <header className="page-head">
        <h1>Settings</h1>
      </header>

      {problems.length > 0 && (
        <section className="panel callout warning">
          <h3>
            {problems.length} file{problems.length > 1 ? "s" : ""} need attention
          </h3>
          <ul className="issues">
            {problems.map(({ file, issues }) => (
              <li key={file}>
                <code>{file}</code>
                <ul>
                  {issues.map((issue, i) => (
                    <li key={i} className={issue.level}>{issue.message}</li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </section>
      )}

      <section className="panel">
        <h3>Your data</h3>
        <SyncSummary />
        <div className="button-row">
          <button className="button" onClick={() => void engine.sync()}>Sync now</button>
          <button className="button" onClick={() => setChanging(!changing)}>{changing ? "Cancel" : "Change…"}</button>
        </div>
        {changing && (
          <div className="sub-panel">
            <h4>Sync with a private GitHub repository</h4>
            <p className="muted small">
              Your people stay plain Markdown files in your own repo — readable by you, your editor and AI agents, and
              synced across devices.
            </p>
            <GitHubConnect onDone={() => { setChanging(false); toast("Connected"); }} />
            {config?.kind === "github" && (
              <>
                <h4>Or keep everything on this device</h4>
                <button className="button" onClick={useDevice}>Store on this device only</button>
              </>
            )}
          </div>
        )}
      </section>

      <section className="panel">
        <h3>Calendar</h3>
        <p className="muted small">
          Add everyone's birthdays (and their family's) to your phone's calendar as yearly events with a reminder.
          Re-download after adding people.
        </p>
        <button className="button" onClick={() => download("catchup-birthdays.ics", birthdaysCalendar(people), "text/calendar")} disabled={people.length === 0}>
          Download birthdays (.ics)
        </button>
      </section>

      <section className="panel">
        <h3>Install on your phone</h3>
        {isInstalled() ? (
          <p className="muted small">CatchUp is installed. 🎉</p>
        ) : canPromptInstall() ? (
          <button className="button primary" onClick={() => void promptInstall()}>Install CatchUp</button>
        ) : (
          <ul className="small">
            <li><strong>iPhone:</strong> open this page in Safari → Share → <em>Add to Home Screen</em>.</li>
            <li><strong>Android:</strong> Chrome menu ⋮ → <em>Install app</em> (or <em>Add to Home screen</em>).</li>
          </ul>
        )}
      </section>

      <section className="panel">
        <h3>Backup</h3>
        <p className="muted small">All {Object.keys(files).length} files as a zip of Markdown, the same format as the repository.</p>
        <button className="button" onClick={backup} disabled={Object.keys(files).length === 0}>Download backup</button>
      </section>

      <section className="panel">
        <h3>Try it out</h3>
        <button
          className="button"
          onClick={async () => {
            await apply(...exampleOps());
            toast("Added example people");
          }}
        >
          Add example people
        </button>
      </section>

      <section className="panel">
        <h3>Reset</h3>
        <button className="button subtle danger" onClick={reset}>Remove CatchUp data from this device</button>
      </section>
    </div>
  );
}
