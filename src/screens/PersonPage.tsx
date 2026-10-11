import { useState } from "react";
import Markdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Avatar } from "../components/Avatar";
import { ContactBadge } from "../components/ContactBadge";
import { Icon } from "../components/Icon";
import { PersonActions } from "../components/PersonActions";
import { CalendarButtons, formatPlanned } from "../components/ScheduleSheet";
import { contactHref } from "../lib/contactLinks";
import { formatBirthday, formatDate, formatDaysAgo, formatDaysUntil, upcomingAge } from "../lib/dates";
import { personPath } from "../lib/ops";
import type { Person } from "../lib/person";
import { personStatus } from "../lib/status";
import { apply, toast, useToday } from "../store";

function label(key: string): string {
  const text = key.replace(/_/g, " ");
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function PersonPage({ person }: { person: Person }) {
  const today = useToday();
  const status = personStatus(person, today);
  const age = person.birthday && upcomingAge(person.birthday, today);
  const contactEntries = Object.entries(person.contact);
  const open = person.followUps.filter((f) => !f.done);
  const done = person.followUps.filter((f) => f.done);
  const [showDone, setShowDone] = useState(false);
  const path = personPath(person.id);

  const toggle = (text: string, isDone: boolean) => apply({ type: "setFollowUp", path, text, done: isDone });

  const remove = async () => {
    if (!confirm(`Remove ${person.name} from CatchUp? This deletes ${path}.`)) return;
    await apply({ type: "delete", path });
    toast(`Removed ${person.name}`);
    location.hash = "#/people";
  };

  return (
    <article className="detail">
      <a className="back" href="#/people">
        <Icon name="back" size={18} /> People
      </a>

      <header className="detail-head">
        <Avatar name={person.name} size="lg" />
        <div>
          <h1>
            {person.name}
            {person.nickname && <span className="nickname"> “{person.nickname}”</span>}
          </h1>
          <p className="muted">{[person.relationship, person.location].filter(Boolean).join(" · ")}</p>
          {person.tags.length > 0 && (
            <ul className="tags">
              {person.tags.map((tag) => (
                <li key={tag}>{tag}</li>
              ))}
            </ul>
          )}
        </div>
      </header>

      <PersonActions person={person} />

      {person.nextCatchup && (
        <section className="panel planned">
          <h3>Planned catch-up</h3>
          <p>
            <strong>{formatPlanned(person.nextCatchup)}</strong>
          </p>
          <CalendarButtons person={person} planned={person.nextCatchup} />
        </section>
      )}

      {(open.length > 0 || done.length > 0) && (
        <section className="panel">
          <h3>Next time, ask about</h3>
          {open.length === 0 && <p className="muted small">Nothing open.</p>}
          <ul className="checklist">
            {open.map((f) => (
              <li key={f.text}>
                <label className="check">
                  <input type="checkbox" checked={false} onChange={() => toggle(f.text, true)} />
                  {f.text}
                </label>
              </li>
            ))}
            {showDone &&
              done.map((f) => (
                <li key={f.text} className="done">
                  <label className="check">
                    <input type="checkbox" checked onChange={() => toggle(f.text, false)} />
                    {f.text}
                  </label>
                </li>
              ))}
          </ul>
          {done.length > 0 && (
            <button className="link-button small" onClick={() => setShowDone(!showDone)}>
              {showDone ? "Hide" : "Show"} {done.length} done
            </button>
          )}
        </section>
      )}

      <section className="panel">
        <dl className="facts">
          {person.birthday && (
            <div>
              <dt>Birthday</dt>
              <dd>
                {formatBirthday(person.birthday)}
                <span className="muted">
                  {" · "}
                  {age !== undefined ? `turns ${age} ` : ""}
                  {formatDaysUntil(status.daysUntilBirthday!)}
                </span>
              </dd>
            </div>
          )}
          <div>
            <dt>Last contact</dt>
            <dd>
              {person.lastContacted ? (
                <>
                  {formatDate(person.lastContacted)}
                  <span className="muted"> · {formatDaysAgo(status.daysSinceContact!)}</span>
                </>
              ) : (
                "Not logged yet"
              )}
              <ContactBadge status={status.contact} />
            </dd>
          </div>
          <div>
            <dt>Keep in touch</dt>
            <dd>
              {person.cadence ? (
                <>
                  {person.cadence}
                  {person.cadenceDays !== undefined && <span className="muted"> · every ~{person.cadenceDays} days</span>}
                </>
              ) : (
                <a href={`#/people/${person.id}/edit`} className="small">Set how often…</a>
              )}
            </dd>
          </div>
          {person.howWeMet && (
            <div>
              <dt>How we met</dt>
              <dd>{person.howWeMet}</dd>
            </div>
          )}
        </dl>
      </section>

      {contactEntries.length > 0 && (
        <section className="panel">
          <h3>Contact</h3>
          <dl className="facts">
            {contactEntries.map(([key, value]) => {
              const href = contactHref(key, value);
              return (
                <div key={key}>
                  <dt>{label(key)}</dt>
                  <dd>{href ? <a href={href} target={href.startsWith("http") ? "_blank" : undefined} rel="noreferrer">{value}</a> : value}</dd>
                </div>
              );
            })}
          </dl>
        </section>
      )}

      {person.family.length > 0 && (
        <section className="panel">
          <h3>Family</h3>
          <ul className="family">
            {person.family.map((member, i) => (
              <li key={`${member.name}-${i}`}>
                <strong>{member.name}</strong>
                {member.relation && <span className="muted"> · {member.relation}</span>}
                {member.birthday && <span className="muted"> · 🎂 {formatBirthday(member.birthday)}</span>}
                {member.notes && <p className="muted small">{member.notes}</p>}
              </li>
            ))}
          </ul>
        </section>
      )}

      {person.body && (
        <section className="panel notes">
          <Markdown remarkPlugins={[remarkGfm]}>{person.body}</Markdown>
        </section>
      )}

      <div className="button-row center">
        <a className="button" href={`#/people/${person.id}/edit`}>Edit details</a>
        <a className="button" href={`#/people/${person.id}/markdown`}>Edit as Markdown</a>
        <button className="button subtle danger" onClick={remove}>Remove</button>
      </div>
      <p className="muted small source">
        <code>{path}</code>
      </p>
    </article>
  );
}
