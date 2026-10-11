import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { parseDate } from "./dates";
import { parsePerson } from "./person";
import { personStatus } from "./status";

const today = parseDate("2026-10-11")!;

describe("parsePerson", () => {
  it("parses a full file", () => {
    const { person, issues } = parsePerson(
      "maya-patel",
      `---
name: Maya Patel
nickname: May
relationship: friend
tags: [college, climbing]
birthday: 1991-10-19
contact:
  phone: "+1 206 555 0142"
family:
  - name: Arjun
    relation: partner
  - Leela
cadence: monthly
last_contacted: 2026-08-01
---

Notes here.

## Log

### 2026-06-14 · Coffee
- old

### 2026-08-30 — Video call
- newest
`,
    );
    expect(issues).toEqual([]);
    expect(person).toMatchObject({
      id: "maya-patel",
      name: "Maya Patel",
      nickname: "May",
      tags: ["college", "climbing"],
      birthday: { year: 1991, month: 10, day: 19 },
      contact: { phone: "+1 206 555 0142" },
      family: [{ name: "Arjun", relation: "partner" }, { name: "Leela" }],
      cadenceDays: 30,
    });
    expect(person!.log.map((e) => e.title)).toEqual(["Video call", "Coffee"]);
    // The log entry is newer than last_contacted, so it wins.
    expect(person!.lastContacted).toEqual(parseDate("2026-08-30"));
    expect(person!.body.startsWith("Notes here.")).toBe(true);
  });

  it("keeps last_contacted when it is newer than the log", () => {
    const { person } = parsePerson(
      "x",
      "---\nname: X\nlast_contacted: 2026-10-01\n---\n## Log\n### 2026-09-01\n",
    );
    expect(person!.lastContacted).toEqual(parseDate("2026-10-01"));
  });

  it("ignores dated headings outside the Log section", () => {
    const { person } = parsePerson("x", "---\nname: X\n---\n## Trips\n### 2026-09-01 Paris\n");
    expect(person!.log).toEqual([]);
    expect(person!.lastContacted).toBeUndefined();
  });

  it("requires a name and frontmatter", () => {
    expect(parsePerson("x", "---\nnickname: X\n---\n").person).toBeNull();
    expect(parsePerson("x", "# Just markdown").issues[0].level).toBe("error");
    expect(parsePerson("x", "---\nname: [unclosed\n---\n").issues[0].message).toMatch(/YAML/);
  });

  it("reports bad values and unknown fields without failing", () => {
    const { person, issues } = parsePerson(
      "x",
      "---\nname: X\nbirthday: 19/10/1991\ncadence: often\nfavourite_color: blue\ncontact:\n  phone: 0123\n---\n",
    );
    expect(person).not.toBeNull();
    expect(issues.map((i) => i.level)).toEqual(["warning", "error", "warning", "error"]);
  });
});

describe("personStatus", () => {
  const make = (frontmatter: string) => parsePerson("x", `---\nname: X\n${frontmatter}\n---\n`).person!;

  it("flags contact status against the cadence", () => {
    expect(personStatus(make("cadence: monthly\nlast_contacted: 2026-10-01"), today).contact).toBe("ok");
    expect(personStatus(make("cadence: monthly\nlast_contacted: 2026-09-15"), today).contact).toBe("due-soon");
    expect(personStatus(make("cadence: monthly\nlast_contacted: 2026-08-01"), today).contact).toBe("overdue");
    expect(personStatus(make("cadence: monthly"), today).contact).toBe("overdue");
    expect(personStatus(make("last_contacted: 2020-01-01"), today).contact).toBe("unknown");
  });
});

describe("people/ directory", () => {
  const dir = join(__dirname, "../../people");
  const files = readdirSync(dir).filter((f) => f.endsWith(".md") && !f.startsWith("_"));

  it.each(files)("%s parses without errors", (file) => {
    const { person, issues } = parsePerson(file, readFileSync(join(dir, file), "utf8"));
    expect(person).not.toBeNull();
    expect(issues.filter((i) => i.level === "error")).toEqual([]);
  });
});
