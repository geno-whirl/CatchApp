// Decides who to reach out to and why. Pure and deterministic, so it's easy to test and
// to explain in the UI ("Birthday on Thursday", "Overdue by 2 weeks").

import { daysBetween, daysUntilBirthday, startOfDay, upcomingAge } from "./dates";
import type { PlannedCatchUp, Person } from "./person";

export type Reason =
  | { kind: "birthday"; days: number; age?: number }
  | { kind: "family-birthday"; days: number; member: string; relation?: string }
  | { kind: "overdue"; daysSince?: number; cadenceDays: number }
  | { kind: "due-soon"; daysSince: number; cadenceDays: number };

export interface Suggestion {
  person: Person;
  reasons: Reason[];
  score: number;
  openFollowUps: string[];
}

export interface Upcoming {
  person: Person;
  planned: PlannedCatchUp;
  days: number;
}

export const BIRTHDAY_WINDOW_DAYS = 7;
export const UPCOMING_WINDOW_DAYS = 30;
const DUE_SOON_AT = 0.8;

function reasonScore(reason: Reason): number {
  switch (reason.kind) {
    case "birthday":
      return 100 - reason.days * 8;
    case "family-birthday":
      return 40 - reason.days * 3;
    case "overdue": {
      if (reason.daysSince === undefined) return 50;
      const ratio = reason.daysSince / reason.cadenceDays;
      return 30 + Math.min(ratio - 1, 2) * 20;
    }
    case "due-soon":
      return 15;
  }
}

export function reasonsFor(person: Person, today: Date): Reason[] {
  const reasons: Reason[] = [];

  if (person.birthday) {
    const days = daysUntilBirthday(person.birthday, today);
    if (days <= BIRTHDAY_WINDOW_DAYS) {
      reasons.push({ kind: "birthday", days, age: upcomingAge(person.birthday, today) });
    }
  }

  for (const member of person.family) {
    if (!member.birthday) continue;
    const days = daysUntilBirthday(member.birthday, today);
    if (days <= BIRTHDAY_WINDOW_DAYS) {
      reasons.push({ kind: "family-birthday", days, member: member.name, relation: member.relation });
    }
  }

  const planned = person.nextCatchup && startOfDay(person.nextCatchup.date) >= startOfDay(today);
  if (person.cadenceDays !== undefined && !planned) {
    const daysSince = person.lastContacted ? daysBetween(person.lastContacted, today) : undefined;
    if (daysSince === undefined || daysSince > person.cadenceDays) {
      reasons.push({ kind: "overdue", daysSince, cadenceDays: person.cadenceDays });
    } else if (daysSince >= person.cadenceDays * DUE_SOON_AT) {
      reasons.push({ kind: "due-soon", daysSince, cadenceDays: person.cadenceDays });
    }
  }

  return reasons;
}

export function buildAgenda(people: Person[], today: Date): { suggestions: Suggestion[]; upcoming: Upcoming[] } {
  const suggestions: Suggestion[] = [];
  const upcoming: Upcoming[] = [];

  for (const person of people) {
    if (person.nextCatchup) {
      const days = daysBetween(today, person.nextCatchup.date);
      if (days >= 0 && days <= UPCOMING_WINDOW_DAYS) upcoming.push({ person, planned: person.nextCatchup, days });
    }

    const reasons = reasonsFor(person, today);
    if (reasons.length === 0) continue;
    const openFollowUps = person.followUps.filter((f) => !f.done).map((f) => f.text);
    const score = Math.max(...reasons.map(reasonScore)) + (openFollowUps.length > 0 ? 5 : 0);
    suggestions.push({ person, reasons, score, openFollowUps });
  }

  suggestions.sort((a, b) => b.score - a.score || a.person.name.localeCompare(b.person.name));
  upcoming.sort((a, b) => a.planned.date.getTime() - b.planned.date.getTime());
  return { suggestions, upcoming };
}

const WEEKDAYS = ["Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"];

/** "today", "tomorrow", "on Thursday" (within a week). */
export function dayPhrase(days: number, today: Date): string {
  if (days === 0) return "today";
  if (days === 1) return "tomorrow";
  const date = new Date(today.getFullYear(), today.getMonth(), today.getDate() + days);
  return `on ${WEEKDAYS[date.getDay()]}`;
}

function weeks(days: number): string {
  if (days < 14) return `${days} day${days === 1 ? "" : "s"}`;
  if (days < 60) return `${Math.round(days / 7)} weeks`;
  return `${Math.round(days / 30)} months`;
}

export function describeReason(reason: Reason, today: Date): string {
  switch (reason.kind) {
    case "birthday":
      return `${reason.age !== undefined ? `Turns ${reason.age}` : "Birthday"} ${dayPhrase(reason.days, today)}`;
    case "family-birthday":
      return `${reason.member}${reason.relation ? ` (${reason.relation})` : ""}'s birthday ${dayPhrase(reason.days, today)}`;
    case "overdue":
      return reason.daysSince === undefined
        ? "No catch-up logged yet"
        : `Overdue by ${weeks(reason.daysSince - reason.cadenceDays)}`;
    case "due-soon":
      return `Due in ${weeks(Math.max(reason.cadenceDays - reason.daysSince, 1))}`;
  }
}
