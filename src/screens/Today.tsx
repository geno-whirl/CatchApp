import { useMemo } from "react";
import { Avatar } from "../components/Avatar";
import { PersonActions } from "../components/PersonActions";
import { CalendarButtons, formatPlanned } from "../components/ScheduleSheet";
import { daysUntilBirthday, formatBirthday, formatDaysUntil } from "../lib/dates";
import { buildAgenda, describeReason } from "../lib/priorities";
import { usePeople, useToday } from "../store";

const BIRTHDAY_LOOKAHEAD = 30;

function greeting(): string {
  const h = new Date().getHours();
  return h < 12 ? "Good morning" : h < 18 ? "Good afternoon" : "Good evening";
}

export function Today() {
  const { people } = usePeople();
  const today = useToday();
  const { suggestions, upcoming } = useMemo(() => buildAgenda(people, today), [people, today]);

  const birthdays = useMemo(
    () =>
      people
        .flatMap((p) => [
          ...(p.birthday ? [{ key: p.id, label: p.name, person: p, birthday: p.birthday }] : []),
          ...p.family
            .filter((f) => f.birthday)
            .map((f, i) => ({ key: `${p.id}-${i}`, label: `${f.name} (${p.nickname ?? p.name.split(" ")[0]}'s ${f.relation ?? "family"})`, person: p, birthday: f.birthday! })),
        ])
        .map((b) => ({ ...b, days: daysUntilBirthday(b.birthday, today) }))
        .filter((b) => b.days > 7 && b.days <= BIRTHDAY_LOOKAHEAD)
        .sort((a, b) => a.days - b.days),
    [people, today],
  );

  if (people.length === 0) {
    return (
      <div className="empty">
        <h1>Welcome to CatchUp</h1>
        <p className="muted">Add the people you want to stay close to, or import them from your contacts.</p>
        <div className="button-row center">
          <a className="button primary" href="#/new">Add someone</a>
          <a className="button" href="#/import">Import</a>
        </div>
      </div>
    );
  }

  return (
    <div className="today">
      <header className="page-head">
        <p className="muted small">{today.toLocaleDateString([], { weekday: "long", month: "long", day: "numeric" })}</p>
        <h1>{greeting()}</h1>
      </header>

      {upcoming.length > 0 && (
        <section>
          <h2 className="section-title">Coming up</h2>
          <ul className="list">
            {upcoming.map(({ person, planned }) => (
              <li key={person.id} className="panel row-card">
                <a className="row-link" href={`#/people/${person.id}`}>
                  <Avatar name={person.name} />
                  <div>
                    <strong>{person.name}</strong>
                    <p className="muted small">{formatPlanned(planned)}</p>
                  </div>
                </a>
                <CalendarButtons person={person} planned={planned} />
              </li>
            ))}
          </ul>
        </section>
      )}

      <section>
        <h2 className="section-title">Reach out</h2>
        {suggestions.length === 0 ? (
          <div className="panel muted">
            You're all caught up. 🎉 Set how often you'd like to talk to people (“Keep in touch”) and CatchUp will
            nudge you when it's been a while.
          </div>
        ) : (
          <ul className="list">
            {suggestions.map(({ person, reasons, openFollowUps }) => (
              <li key={person.id} className="panel suggestion">
                <a className="row-link" href={`#/people/${person.id}`}>
                  <Avatar name={person.name} />
                  <div>
                    <strong>{person.name}</strong>
                    <ul className="reasons">
                      {reasons.map((r, i) => (
                        <li key={i} className={`reason reason-${r.kind}`}>
                          {describeReason(r, today)}
                        </li>
                      ))}
                    </ul>
                  </div>
                </a>
                {openFollowUps.length > 0 && (
                  <p className="small follow-up-hint">
                    <span className="muted">Ask about:</span> {openFollowUps.slice(0, 2).join(" · ")}
                    {openFollowUps.length > 2 && <span className="muted"> +{openFollowUps.length - 2}</span>}
                  </p>
                )}
                <PersonActions person={person} />
              </li>
            ))}
          </ul>
        )}
      </section>

      {birthdays.length > 0 && (
        <section>
          <h2 className="section-title">Birthdays this month</h2>
          <ul className="panel plain-list">
            {birthdays.map((b) => (
              <li key={b.key}>
                <a href={`#/people/${b.person.id}`}>{b.label}</a>
                <span className="muted small">
                  {formatBirthday({ ...b.birthday, year: undefined })} · {formatDaysUntil(b.days)}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
