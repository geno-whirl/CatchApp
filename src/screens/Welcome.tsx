import { useState } from "react";
import { GitHubConnect } from "../components/GitHubConnect";
import { apply, engine, exampleOps } from "../store";

/** First run: choose where people are stored. */
export function Welcome() {
  const [mode, setMode] = useState<"choose" | "github">("choose");
  const [examples, setExamples] = useState(false);

  const startOnDevice = async () => {
    await engine.configure({ kind: "device" });
    if (examples) await apply(...exampleOps());
    location.hash = "#/";
  };

  return (
    <div className="welcome">
      <div className="welcome-mark" aria-hidden>
        <img src="icon.svg" alt="" width={72} height={72} />
      </div>
      <h1>CatchUp</h1>
      <p className="lead">Stay close to the people who matter — birthdays, little details, and a nudge when it's been too long.</p>

      {mode === "choose" ? (
        <>
          <h2 className="section-title">Where should your people live?</h2>
          <button className="panel source-card" onClick={() => setMode("github")}>
            <strong>A private GitHub repository</strong> <span className="badge badge-new">recommended</span>
            <span className="muted small block">
              Synced across your devices and backed up. Plain Markdown files that you — or an AI assistant — can read and edit.
            </span>
          </button>
          <div className="panel source-card">
            <strong>Just this phone</strong>
            <span className="muted small block">No account needed. You can move to GitHub later. Download backups from Settings.</span>
            <label className="check">
              <input type="checkbox" checked={examples} onChange={(e) => setExamples(e.target.checked)} />
              Start with a few example people
            </label>
            <button className="button primary" onClick={startOnDevice}>Start on this phone</button>
          </div>
        </>
      ) : (
        <div className="panel">
          <button className="link-button small" onClick={() => setMode("choose")}>← Back</button>
          <h2>Connect your repository</h2>
          <GitHubConnect onDone={() => (location.hash = "#/")} />
        </div>
      )}
    </div>
  );
}
