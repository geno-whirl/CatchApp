import { describe, expect, it } from "vitest";
import { birthdaysCalendar, catchUpCalendar, googleCalendarUrl } from "./calendar";
import { parseDate } from "./dates";
import { parsePerson } from "./person";
import { buildAgenda, describeReason } from "./priorities";

const today = parseDate("2026-10-11")!; // A Sunday.
const person = (id: string, fm: string, body = "") => parsePerson(id, `---\nname: ${id}\n${fm}\n---\n${body}`).person!;

describe("buildAgenda", () => {
  const people = [
    person("birthday-today", "birthday: 1990-10-11"),
    person("overdue", "cadence: monthly\nlast_contacted: 2026-08-01", "- [ ] Ask about the move"),
    person("never", "cadence: weekly"),
    person("due-soon", "cadence: monthly\nlast_contacted: 2026-09-15"),
    person("fine", "cadence: monthly\nlast_contacted: 2026-10-05"),
    person("kid-birthday", "family:\n  - name: Leela\n    relation: daughter\n    birthday: 2022-10-15"),
    person("planned", "cadence: weekly\nlast_contacted: 2026-01-01\nnext_catchup: 2026-10-14 18:30"),
    person("far-birthday", "birthday: 12-25"),
  ];
  const { suggestions, upcoming } = buildAgenda(people, today);

  it("ranks who to reach out to", () => {
    expect(suggestions.map((s) => s.person.id)).toEqual(["birthday-today", "overdue", "never", "kid-birthday", "due-soon"]);
    expect(suggestions[1].openFollowUps).toEqual(["Ask about the move"]);
  });

  it("explains each suggestion", () => {
    const text = suggestions.map((s) => s.reasons.map((r) => describeReason(r, today)).join("; "));
    expect(text).toEqual([
      "Turns 36 today",
      "Overdue by 6 weeks",
      "No catch-up logged yet",
      "Leela (daughter)'s birthday on Thursday",
      "Due in 4 days",
    ]);
  });

  it("lists planned catch-ups instead of nagging about them", () => {
    expect(upcoming.map((u) => [u.person.id, u.days])).toEqual([["planned", 3]]);
  });
});

describe("calendar", () => {
  const maya = person(
    "maya-patel",
    "birthday: 1991-10-19\nfamily:\n  - name: Leela\n    relation: daughter\n    birthday: 02-29\nnext_catchup: 2026-10-20 18:30",
    "- [ ] Ask about the new team, the climbing trip, and whether Leela likes preschool",
  );
  const now = new Date(Date.UTC(2026, 9, 11, 12));

  it("builds recurring birthday events", () => {
    const ics = birthdaysCalendar([maya], now);
    expect(ics).toContain("DTSTART;VALUE=DATE:19911019\r\nRRULE:FREQ=YEARLY\r\n");
    expect(ics).toContain("SUMMARY:🎂 maya-patel's birthday\r\n");
    expect(ics).toContain("DTSTART;VALUE=DATE:20000229\r\nRRULE:FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=-1");
    expect(ics).toContain("SUMMARY:🎂 Leela's birthday (maya-patel's daughter)");
    expect(ics.split("\r\n").every((line) => new TextEncoder().encode(line).length <= 75)).toBe(true);
  });

  it("builds a catch-up event with follow-ups", () => {
    const ics = catchUpCalendar(maya, maya.nextCatchup!, now);
    expect(ics).toContain("DTSTART:20261020T183000\r\nDURATION:PT1H");
    const unfolded = ics.replace(/\r\n /g, "");
    expect(unfolded).toContain("DESCRIPTION:Remember to ask about:\\n• Ask about the new team\\, the climbing trip\\, and whether Leela likes preschool");
  });

  it("links to Google Calendar", () => {
    const url = new URL(googleCalendarUrl(maya, maya.nextCatchup!));
    expect(url.searchParams.get("dates")).toBe("20261020T183000/20261020T193000");
    expect(url.searchParams.get("text")).toBe("Catch up with maya-patel");
  });
});
