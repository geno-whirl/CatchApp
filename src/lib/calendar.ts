// Calendar files (.ics) for birthdays and planned catch-ups, plus Google Calendar links.

import { type Birthday, toIsoDate } from "./dates";
import type { PlannedCatchUp, Person } from "./person";

function escapeText(text: string): string {
  return text.replace(/\\/g, "\\\\").replace(/\n/g, "\\n").replace(/([,;])/g, "\\$1");
}

/** Folds lines longer than 75 bytes, as RFC 5545 requires. */
function fold(line: string): string {
  const out: string[] = [];
  let current = "";
  let bytes = 0;
  for (const ch of line) {
    const size = new TextEncoder().encode(ch).length;
    if (bytes + size > (out.length === 0 ? 75 : 74)) {
      out.push(current);
      current = "";
      bytes = 0;
    }
    current += ch;
    bytes += size;
  }
  out.push(current);
  return out.join("\r\n ");
}

function calendar(name: string, events: string[][]): string {
  const lines = [
    "BEGIN:VCALENDAR",
    "VERSION:2.0",
    "PRODID:-//CatchUp//EN",
    "CALSCALE:GREGORIAN",
    `X-WR-CALNAME:${escapeText(name)}`,
    ...events.flat(),
    "END:VCALENDAR",
  ];
  return lines.map(fold).join("\r\n") + "\r\n";
}

const compact = (iso: string) => iso.replace(/-/g, "");

function stamp(now: Date): string {
  return now.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
}

function localDateTime(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${compact(toIsoDate(d))}T${pad(d.getHours())}${pad(d.getMinutes())}00`;
}

function birthdayEvent(uid: string, summary: string, b: Birthday, now: Date): string[] {
  const leapDay = b.month === 2 && b.day === 29;
  // Without a known year, start the series in 2000 (a leap year, so Feb 29 is valid).
  const year = b.year ?? 2000;
  const pad = (n: number) => String(n).padStart(2, "0");
  return [
    "BEGIN:VEVENT",
    `UID:${uid}@catchup`,
    `DTSTAMP:${stamp(now)}`,
    `DTSTART;VALUE=DATE:${year}${pad(b.month)}${pad(b.day)}`,
    // Feb 29 birthdays fall on the last day of February in other years.
    leapDay ? "RRULE:FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1" : "RRULE:FREQ=YEARLY",
    `SUMMARY:${escapeText(summary)}`,
    "TRANSP:TRANSPARENT",
    "BEGIN:VALARM",
    "ACTION:DISPLAY",
    `DESCRIPTION:${escapeText(summary)}`,
    "TRIGGER:PT9H",
    "END:VALARM",
    "END:VEVENT",
  ];
}

/** Recurring all-day events for everyone's birthdays, including family members. */
export function birthdaysCalendar(people: Person[], now = new Date()): string {
  const events: string[][] = [];
  for (const person of people) {
    if (person.birthday) {
      events.push(birthdayEvent(`birthday-${person.id}`, `🎂 ${person.name}'s birthday`, person.birthday, now));
    }
    person.family.forEach((member, i) => {
      if (!member.birthday) return;
      const who = member.relation ? `${person.name}'s ${member.relation}` : `with ${person.name}`;
      events.push(
        birthdayEvent(`birthday-${person.id}-family-${i}`, `🎂 ${member.name}'s birthday (${who})`, member.birthday, now),
      );
    });
  }
  return calendar("CatchUp birthdays", events);
}

export function catchUpTitle(person: Person): string {
  return `Catch up with ${person.nickname ?? person.name}`;
}

function catchUpDescription(person: Person): string {
  const open = person.followUps.filter((f) => !f.done).map((f) => `• ${f.text}`);
  return open.length > 0 ? `Remember to ask about:\n${open.join("\n")}` : "";
}

export function catchUpCalendar(person: Person, planned: PlannedCatchUp, now = new Date()): string {
  const date = toIsoDate(planned.date);
  const end = new Date(planned.date.getFullYear(), planned.date.getMonth(), planned.date.getDate() + 1);
  const description = catchUpDescription(person);
  return calendar("CatchUp", [
    [
      "BEGIN:VEVENT",
      `UID:catchup-${person.id}-${compact(date)}@catchup`,
      `DTSTAMP:${stamp(now)}`,
      ...(planned.hasTime
        ? [`DTSTART:${localDateTime(planned.date)}`, "DURATION:PT1H"]
        : [`DTSTART;VALUE=DATE:${compact(date)}`, `DTEND;VALUE=DATE:${compact(toIsoDate(end))}`]),
      `SUMMARY:${escapeText(catchUpTitle(person))}`,
      ...(description ? [`DESCRIPTION:${escapeText(description)}`] : []),
      "END:VEVENT",
    ],
  ]);
}

export function googleCalendarUrl(person: Person, planned: PlannedCatchUp): string {
  let dates: string;
  if (planned.hasTime) {
    const end = new Date(planned.date.getTime() + 60 * 60 * 1000);
    dates = `${localDateTime(planned.date)}/${localDateTime(end)}`;
  } else {
    const next = new Date(planned.date.getFullYear(), planned.date.getMonth(), planned.date.getDate() + 1);
    dates = `${compact(toIsoDate(planned.date))}/${compact(toIsoDate(next))}`;
  }
  const params = new URLSearchParams({ action: "TEMPLATE", text: catchUpTitle(person), dates });
  const description = catchUpDescription(person);
  if (description) params.set("details", description);
  return `https://calendar.google.com/calendar/render?${params}`;
}
