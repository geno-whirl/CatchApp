import { useState } from "react";
import { catchUpCalendar, googleCalendarUrl } from "../lib/calendar";
import { formatDate, toIsoDate } from "../lib/dates";
import { personPath } from "../lib/ops";
import { parseDateTime, type Person, type PlannedCatchUp } from "../lib/person";
import { apply, download } from "../store";
import { Sheet } from "./Sheet";

export function formatPlanned(p: PlannedCatchUp): string {
  const time = p.hasTime ? ` at ${p.date.toLocaleTimeString([], { hour: "numeric", minute: "2-digit" })}` : "";
  return `${p.date.toLocaleDateString([], { weekday: "short" })}, ${formatDate(p.date)}${time}`;
}

export function CalendarButtons({ person, planned }: { person: Person; planned: PlannedCatchUp }) {
  return (
    <div className="button-row">
      <button
        className="button"
        onClick={() => download(`catch-up-${person.id}.ics`, catchUpCalendar(person, planned), "text/calendar")}
      >
        Add to calendar (.ics)
      </button>
      <a className="button" href={googleCalendarUrl(person, planned)} target="_blank" rel="noreferrer">
        Google Calendar
      </a>
    </div>
  );
}

/** Pick a day (and optionally a time) to catch up; it shows on Today and can go in your calendar. */
export function ScheduleSheet({ person, onClose, onSaved }: { person: Person; onClose: () => void; onSaved: (msg: string) => void }) {
  const tomorrow = new Date();
  tomorrow.setDate(tomorrow.getDate() + 1);
  const current = person.nextCatchup;
  const [date, setDate] = useState(current ? toIsoDate(current.date) : toIsoDate(tomorrow));
  const [time, setTime] = useState(
    current?.hasTime ? current.date.toTimeString().slice(0, 5) : "",
  );
  const [saved, setSaved] = useState<PlannedCatchUp | null>(null);
  const name = person.nickname ?? person.name.split(" ")[0];

  const save = async () => {
    const value = time ? `${date} ${time}` : date;
    await apply({ type: "setFields", path: personPath(person.id), fields: { next_catchup: value } });
    setSaved(parseDateTime(value));
    onSaved(`Catch-up with ${name} planned`);
  };

  const clear = async () => {
    await apply({ type: "setFields", path: personPath(person.id), fields: { next_catchup: null } });
    onSaved("Plan removed");
    onClose();
  };

  return (
    <Sheet title={`Plan a catch-up with ${name}`} onClose={onClose}>
      {saved ? (
        <div className="form">
          <p>
            <strong>{formatPlanned(saved)}</strong> — it'll show on your Today screen. Add it to your calendar to get a reminder:
          </p>
          <CalendarButtons person={person} planned={saved} />
          <button className="button primary" onClick={onClose}>
            Done
          </button>
        </div>
      ) : (
        <div className="form">
          <div className="two-col">
            <label>
              <span>Day</span>
              <input type="date" value={date} min={toIsoDate(new Date())} onChange={(e) => setDate(e.target.value)} required />
            </label>
            <label>
              <span>Time (optional)</span>
              <input type="time" value={time} onChange={(e) => setTime(e.target.value)} />
            </label>
          </div>
          <button className="button primary" onClick={save} disabled={!date}>
            Save plan
          </button>
          {current && (
            <button className="button subtle" onClick={clear}>
              Remove current plan ({formatPlanned(current)})
            </button>
          )}
        </div>
      )}
    </Sheet>
  );
}
