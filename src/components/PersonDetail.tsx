import Markdown from "react-markdown";
import {
  formatBirthday,
  formatDate,
  formatDaysAgo,
  formatDaysUntil,
  upcomingAge,
} from "../lib/dates";
import type { Person } from "../lib/person";
import type { PersonStatus } from "../lib/status";
import { Avatar } from "./Avatar";
import { ContactBadge } from "./ContactBadge";

function contactHref(key: string, value: string): string | undefined {
  const k = key.toLowerCase();
  if (/^https?:\/\//.test(value)) return value;
  if (k.includes("email") || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) return `mailto:${value}`;
  if (k.includes("phone") || k.includes("mobile")) return `tel:${value.replace(/[^\d+]/g, "")}`;
  return undefined;
}

function label(key: string): string {
  const text = key.replace(/_/g, " ");
  return text.charAt(0).toUpperCase() + text.slice(1);
}

export function PersonDetail({
  person,
  status,
  today,
}: {
  person: Person;
  status: PersonStatus;
  today: Date;
}) {
  const age = person.birthday && upcomingAge(person.birthday, today);
  const contactEntries = Object.entries(person.contact);

  return (
    <article className="detail">
      <a className="back" href="#/">
        ← All people
      </a>

      <header className="detail-head">
        <Avatar name={person.name} size="lg" />
        <div>
          <h1>
            {person.name}
            {person.nickname && <span className="nickname"> “{person.nickname}”</span>}
          </h1>
          <p className="muted">
            {[person.relationship, person.location].filter(Boolean).join(" · ")}
          </p>
          {person.tags.length > 0 && (
            <ul className="tags">
              {person.tags.map((tag) => (
                <li key={tag}>{tag}</li>
              ))}
            </ul>
          )}
        </div>
      </header>

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
          {person.cadence && (
            <div>
              <dt>Keep in touch</dt>
              <dd>
                {person.cadence}
                {person.cadenceDays !== undefined && (
                  <span className="muted"> · every ~{person.cadenceDays} days</span>
                )}
              </dd>
            </div>
          )}
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
                  <dd>{href ? <a href={href}>{value}</a> : value}</dd>
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
                {member.birthday && (
                  <span className="muted"> · 🎂 {formatBirthday(member.birthday)}</span>
                )}
                {member.notes && <p className="muted small">{member.notes}</p>}
              </li>
            ))}
          </ul>
        </section>
      )}

      {person.body && (
        <section className="panel notes">
          <Markdown>{person.body}</Markdown>
        </section>
      )}

      <p className="muted small source">
        Source: <code>people/{person.id}.md</code>
      </p>
    </article>
  );
}
