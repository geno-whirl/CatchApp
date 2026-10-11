import { useState } from "react";
import { toIsoDate } from "../lib/dates";
import type { Op } from "../lib/ops";
import { personPath } from "../lib/ops";
import type { Person } from "../lib/person";
import { apply } from "../store";
import { Sheet } from "./Sheet";

const KINDS = ["Call", "Video call", "In person", "Messages", "Other"];

const lines = (text: string) =>
  text
    .split("\n")
    .map((l) => l.replace(/^\s*[-*•]\s*/, "").trim())
    .filter(Boolean);

/** Quick "how did it go" logging: a few taps and a couple of lines. */
export function LogSheet({ person, onClose, onSaved }: { person: Person; onClose: () => void; onSaved: (msg: string) => void }) {
  const [date, setDate] = useState(toIsoDate(new Date()));
  const [kind, setKind] = useState("Call");
  const [notes, setNotes] = useState("");
  const [followUps, setFollowUps] = useState("");
  const open = person.followUps.filter((f) => !f.done);
  const [covered, setCovered] = useState<Set<string>>(new Set());

  const save = async () => {
    const path = personPath(person.id);
    const ops: Op[] = [
      ...[...covered].map((text) => ({ type: "setFollowUp" as const, path, text, done: true })),
      { type: "addLog", path, entry: { date, kind, notes: lines(notes), followUps: lines(followUps) } },
    ];
    await apply(...ops);
    onSaved(`Logged ${kind.toLowerCase()} with ${person.nickname ?? person.name.split(" ")[0]}`);
    onClose();
  };

  return (
    <Sheet title={`Log a catch-up with ${person.nickname ?? person.name.split(" ")[0]}`} onClose={onClose}>
      <div className="form">
        <div className="chips" role="radiogroup" aria-label="Type">
          {KINDS.map((k) => (
            <button key={k} type="button" role="radio" aria-checked={kind === k} className={kind === k ? "active" : ""} onClick={() => setKind(k)}>
              {k}
            </button>
          ))}
        </div>

        <label>
          <span>When</span>
          <input type="date" value={date} max={toIsoDate(new Date())} onChange={(e) => setDate(e.target.value)} required />
        </label>

        <label>
          <span>What did you talk about?</span>
          <textarea
            rows={4}
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            placeholder={"One thing per line\nNew job at the hospital\nTrip to Lisbon in May"}
            autoFocus
          />
        </label>

        {open.length > 0 && (
          <fieldset>
            <legend>Did you cover these?</legend>
            {open.map((f) => (
              <label key={f.text} className="check">
                <input
                  type="checkbox"
                  checked={covered.has(f.text)}
                  onChange={(e) => {
                    const next = new Set(covered);
                    if (e.target.checked) next.add(f.text);
                    else next.delete(f.text);
                    setCovered(next);
                  }}
                />
                {f.text}
              </label>
            ))}
          </fieldset>
        )}

        <label>
          <span>Next time, ask about…</span>
          <textarea rows={2} value={followUps} onChange={(e) => setFollowUps(e.target.value)} placeholder="How the interview went" />
        </label>

        <button className="button primary" onClick={save} disabled={!date}>
          Save
        </button>
      </div>
    </Sheet>
  );
}
