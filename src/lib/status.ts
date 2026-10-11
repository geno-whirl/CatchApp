// Derived, display-ready facts about a person relative to "today".

import { daysBetween, daysUntilBirthday } from "./dates";
import type { Person } from "./person";

export type ContactStatus = "ok" | "due-soon" | "overdue" | "unknown";

export interface PersonStatus {
  daysSinceContact?: number;
  daysUntilBirthday?: number;
  /** Only meaningful when the person has a `cadence`. */
  contact: ContactStatus;
}

/** Fraction of the cadence after which someone is flagged as "due soon". */
const DUE_SOON_AT = 0.8;

export function personStatus(person: Person, today: Date): PersonStatus {
  const daysSinceContact = person.lastContacted
    ? daysBetween(person.lastContacted, today)
    : undefined;

  let contact: ContactStatus = "unknown";
  if (person.cadenceDays !== undefined) {
    if (daysSinceContact === undefined || daysSinceContact > person.cadenceDays) {
      contact = "overdue";
    } else if (daysSinceContact >= person.cadenceDays * DUE_SOON_AT) {
      contact = "due-soon";
    } else {
      contact = "ok";
    }
  }

  return {
    daysSinceContact,
    daysUntilBirthday: person.birthday ? daysUntilBirthday(person.birthday, today) : undefined,
    contact,
  };
}
