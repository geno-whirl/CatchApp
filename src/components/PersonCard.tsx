import { formatBirthday, formatDaysAgo, formatDaysUntil } from "../lib/dates";
import type { Person } from "../lib/person";
import type { PersonStatus } from "../lib/status";
import { Avatar } from "./Avatar";
import { ContactBadge } from "./ContactBadge";
import { formatPlanned } from "./ScheduleSheet";

const BIRTHDAY_SOON_DAYS = 14;

export function PersonCard({ person, status }: { person: Person; status: PersonStatus }) {
  const subtitle = [person.relationship, person.location].filter(Boolean).join(" · ");
  const birthdaySoon =
    status.daysUntilBirthday !== undefined && status.daysUntilBirthday <= BIRTHDAY_SOON_DAYS;

  return (
    <a className="card" href={`#/people/${person.id}`}>
      <div className="card-head">
        <Avatar name={person.name} />
        <div className="card-title">
          <h2>
            {person.name}
            {person.nickname && <span className="nickname"> “{person.nickname}”</span>}
          </h2>
          {subtitle && <p className="muted">{subtitle}</p>}
        </div>
      </div>

      <dl className="card-facts">
        {person.birthday && (
          <div className={birthdaySoon ? "highlight" : undefined}>
            <dt>Birthday</dt>
            <dd>
              {formatBirthday({ ...person.birthday, year: undefined })}
              <span className="muted"> · {formatDaysUntil(status.daysUntilBirthday!)}</span>
            </dd>
          </div>
        )}
        <div>
          <dt>Last contact</dt>
          <dd>
            {status.daysSinceContact !== undefined ? formatDaysAgo(status.daysSinceContact) : "—"}
            <ContactBadge status={status.contact} />
          </dd>
        </div>
        {person.nextCatchup && (
          <div className="highlight">
            <dt>Planned</dt>
            <dd>{formatPlanned(person.nextCatchup)}</dd>
          </div>
        )}
        {person.family.length > 0 && (
          <div>
            <dt>Family</dt>
            <dd className="truncate">{person.family.map((f) => f.name).join(", ")}</dd>
          </div>
        )}
      </dl>

      {person.tags.length > 0 && (
        <ul className="tags">
          {person.tags.map((tag) => (
            <li key={tag}>{tag}</li>
          ))}
        </ul>
      )}
    </a>
  );
}
