import { type ReactNode, useMemo, useState } from "react";
import { canPickContacts, pickContacts } from "../lib/import/contactPicker";
import { readImportFiles } from "../lib/import/files";
import { type ImportItem, planImport } from "../lib/import/match";
import { parseMetaExport } from "../lib/import/meta";
import type { Candidate, ImportSource, ParseOutcome } from "../lib/import/types";
import { parseVCards } from "../lib/import/vcard";
import { parseWhatsApp } from "../lib/import/whatsapp";
import type { Op } from "../lib/ops";
import { apply, toast, usePeople, useSync } from "../store";

interface SourceInfo {
  id: ImportSource;
  title: string;
  blurb: string;
  accept: string;
  how: ReactNode;
}

const SOURCES: SourceInfo[] = [
  {
    id: "contacts",
    title: "Contacts file",
    blurb: "Names, birthdays, phone numbers, emails and family from your address book.",
    accept: ".vcf,text/vcard,text/x-vcard",
    how: (
      <ul>
        <li><strong>iPhone:</strong> Contacts app → Lists → long-press a list (e.g. “All Contacts”) → Export → Save to Files.</li>
        <li><strong>Android:</strong> Contacts app → Fix &amp; manage → Export to file.</li>
        <li><strong>Google Contacts</strong> (contacts.google.com): select people → ⋮ → Export → vCard.</li>
      </ul>
    ),
  },
  {
    id: "whatsapp",
    title: "WhatsApp chats",
    blurb: "When you last messaged someone. Message text stays on your phone and is never saved.",
    accept: ".txt,.zip,text/plain,application/zip",
    how: (
      <ul>
        <li>Open a chat → tap the name (iPhone) or ⋮ → More (Android) → <strong>Export chat</strong> → <strong>Without media</strong>.</li>
        <li>Save the file, then pick it here. You can pick several chats at once.</li>
      </ul>
    ),
  },
  {
    id: "facebook",
    title: "Facebook",
    blurb: "Your friends list, and when you last talked on Messenger.",
    accept: ".zip,.json,application/zip,application/json",
    how: (
      <ul>
        <li>Facebook → Settings → Accounts Center → <strong>Your information and permissions</strong> → Download your information.</li>
        <li>Choose <strong>Some of your information</strong>: Friends and Messages. Format <strong>JSON</strong>, media quality <strong>Low</strong>.</li>
        <li>When it's ready (can take a while), download the zip and pick it here.</li>
      </ul>
    ),
  },
  {
    id: "instagram",
    title: "Instagram",
    blurb: "People you follow who follow you back, and when you last talked in DMs.",
    accept: ".zip,.json,application/zip,application/json",
    how: (
      <ul>
        <li>Instagram → Settings → Accounts Center → <strong>Your information and permissions</strong> → Download your information.</li>
        <li>Choose <strong>Some of your information</strong>: Followers and following, Messages. Format <strong>JSON</strong>.</li>
        <li>Download the zip when it's ready and pick it here.</li>
      </ul>
    ),
  },
];

function parse(source: ImportSource, files: { name: string; text: string }[]): ParseOutcome {
  switch (source) {
    case "contacts": {
      const outcomes = files.filter((f) => /\.vcf$/i.test(f.name)).map((f) => parseVCards(f.text));
      return { candidates: outcomes.flatMap((o) => o.candidates), notes: outcomes.flatMap((o) => o.notes) };
    }
    case "whatsapp":
      return parseWhatsApp(files.filter((f) => /\.txt$/i.test(f.name)));
    case "facebook":
    case "instagram":
      return parseMetaExport(files, source);
  }
}

function describe(item: ImportItem): string {
  if (!item.match) return [item.candidate.detail, item.candidate.birthday && "birthday", item.candidate.contact.phone && "phone"].filter(Boolean).join(" · ") || "New person";
  return item.changes.length > 0 ? `Update ${item.match.name}: + ${item.changes.join(", ")}` : `Already up to date (${item.match.name})`;
}

export function Import() {
  const { people } = usePeople();
  const { files } = useSync();
  const [source, setSource] = useState<SourceInfo | null>(null);
  const [outcome, setOutcome] = useState<{ candidates: Candidate[]; notes: string[] } | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [filter, setFilter] = useState("");

  const items = useMemo(() => (outcome ? planImport(outcome.candidates, people, files) : []), [outcome, people, files]);
  const actionable = items.filter((i) => i.op);
  const visible = actionable.filter((i) => i.candidate.name.toLowerCase().includes(filter.toLowerCase()));

  const load = async (run: () => Promise<ParseOutcome>) => {
    setBusy(true);
    setError(null);
    try {
      const result = await run();
      setOutcome(result);
      // Pre-select updates to people you already have; new people are opt-in.
      const plan = planImport(result.candidates, people, files);
      setSelected(new Set(plan.filter((i) => i.op && i.match).map((i) => i.key)));
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e));
    } finally {
      setBusy(false);
    }
  };

  const onFiles = (from: SourceInfo, picked: FileList | null) => {
    if (!picked || picked.length === 0) return;
    const list = [...picked];
    setSource(from);
    void load(async () => parse(from.id, await readImportFiles(list)));
  };

  const run = async () => {
    const ops = actionable.filter((i) => selected.has(i.key)).map((i) => i.op as Op);
    if (ops.length === 0) return;
    await apply(...ops);
    toast(`Imported ${ops.length} ${ops.length === 1 ? "person" : "people"}`);
    location.hash = "#/people";
  };

  const toggleAll = (which: "new" | "update", on: boolean) => {
    const next = new Set(selected);
    for (const i of visible) if ((which === "new") === !i.match) (on ? next.add(i.key) : next.delete(i.key));
    setSelected(next);
  };

  if (outcome && source) {
    const newCount = actionable.filter((i) => !i.match).length;
    const updateCount = actionable.length - newCount;
    const upToDate = items.length - actionable.length;
    return (
      <div className="import">
        <button className="back link-button" onClick={() => setOutcome(null)}>← {source.title}</button>
        <h1>Review import</h1>
        <p className="muted">
          Found {items.length} {items.length === 1 ? "person" : "people"}: {newCount} new, {updateCount} with something to add
          {upToDate > 0 ? `, ${upToDate} already up to date` : ""}. Nothing you already have is overwritten.
        </p>
        {outcome.notes.length > 0 && (
          <ul className="issues">
            {outcome.notes.map((n) => (
              <li key={n} className="warning">{n}</li>
            ))}
          </ul>
        )}

        {actionable.length > 0 && (
          <>
            <div className="toolbar">
              <input type="search" placeholder="Filter…" value={filter} onChange={(e) => setFilter(e.target.value)} />
            </div>
            <div className="button-row">
              {newCount > 0 && (
                <>
                  <button className="button small" onClick={() => toggleAll("new", true)}>Select new</button>
                  <button className="button small" onClick={() => toggleAll("new", false)}>Clear new</button>
                </>
              )}
              {updateCount > 0 && <button className="button small" onClick={() => toggleAll("update", true)}>Select updates</button>}
            </div>
            <ul className="panel import-list">
              {visible.map((item) => (
                <li key={item.key}>
                  <label className="check">
                    <input
                      type="checkbox"
                      checked={selected.has(item.key)}
                      onChange={(e) => {
                        const next = new Set(selected);
                        if (e.target.checked) next.add(item.key);
                        else next.delete(item.key);
                        setSelected(next);
                      }}
                    />
                    <span>
                      <strong>{item.candidate.name}</strong>
                      {!item.match && <span className="badge badge-new">new</span>}
                      <span className="muted small block">{describe(item)}</span>
                    </span>
                  </label>
                </li>
              ))}
            </ul>
          </>
        )}
        <div className="sticky-footer">
          <button className="button primary" onClick={run} disabled={selected.size === 0}>
            Import {selected.size} {selected.size === 1 ? "person" : "people"}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="import">
      <header className="page-head">
        <h1>Import</h1>
      </header>
      <p className="muted">
        Bring people in from your phone and social apps. Everything is read on this device; you choose who to add, and
        nothing you've already written is overwritten.
      </p>

      {canPickContacts() && (
        <button className="panel source-card" onClick={() => { setSource(SOURCES[0]); void load(async () => ({ candidates: await pickContacts(), notes: [] })); }}>
          <strong>Pick from phone contacts</strong>
          <span className="muted small">Choose people straight from your address book.</span>
        </button>
      )}

      {SOURCES.map((s) => (
        <details key={s.id} className="panel source-card" open={source?.id === s.id} onToggle={(e) => (e.currentTarget as HTMLDetailsElement).open && setSource(s)}>
          <summary>
            <strong>{s.title}</strong>
            <span className="muted small block">{s.blurb}</span>
          </summary>
          <div className="how">{s.how}</div>
          <label className="button primary file-button">
            {busy && source?.id === s.id ? "Reading…" : "Choose file"}
            <input type="file" accept={s.accept} multiple onChange={(e) => { onFiles(s, e.target.files); e.target.value = ""; }} hidden />
          </label>
        </details>
      ))}
      {error && <p className="error">Couldn't read that file: {error}</p>}
    </div>
  );
}
