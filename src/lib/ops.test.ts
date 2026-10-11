import { describe, expect, it } from "vitest";
import { applyOp, applyOps, commitMessage, type Files, serializePerson, slugify } from "./ops";
import { parsePerson } from "./person";

const MAYA = `---
# Maya's file
name: Maya Patel
tags: [college]   # where we met
contact:
  phone: "+1 206 555 0142"
next_catchup: 2026-10-20 18:00
---

Loves matcha.

## Log

### 2026-08-30 · Video call
- Leela started preschool.

### 2026-06-14 · Coffee
- [ ] Ask about Portland trip
- [x] Send photos

## Gift ideas
- Chalk bag
`;

const P = "people/maya-patel.md";
const files: Files = { [P]: MAYA };
const run = (op: Parameters<typeof applyOp>[1]) => applyOp(files, op).files[P];

describe("addLog", () => {
  it("inserts newest entries at the top of the log", () => {
    const out = run({
      type: "addLog",
      path: P,
      entry: { date: "2026-10-11", kind: "Call", notes: ["New job!"], followUps: ["Ask about team"] },
    });
    expect(out).toContain(
      "## Log\n\n### 2026-10-11 · Call\n- New job!\n- [ ] Ask about team\n\n### 2026-08-30 · Video call",
    );
    const person = parsePerson("maya-patel", out).person!;
    expect(person.log[0].title).toBe("Call");
    expect(person.followUps.filter((f) => !f.done).map((f) => f.text)).toEqual([
      "Ask about team",
      "Ask about Portland trip",
    ]);
  });

  it("keeps date order for back-dated entries and stays inside the Log section", () => {
    const middle = run({ type: "addLog", path: P, entry: { date: "2026-07-01", kind: "Text", notes: [], followUps: [] } });
    expect(middle.indexOf("### 2026-07-01")).toBeGreaterThan(middle.indexOf("### 2026-08-30"));
    expect(middle.indexOf("### 2026-07-01")).toBeLessThan(middle.indexOf("### 2026-06-14"));

    const oldest = run({ type: "addLog", path: P, entry: { date: "2025-01-01", kind: "Text", notes: [], followUps: [] } });
    expect(oldest).toContain("- [x] Send photos\n\n### 2025-01-01 · Text\n\n## Gift ideas");
  });

  it("creates the Log section when missing", () => {
    const out = applyOp({ "people/x.md": "---\nname: X\n---\n\nNotes.\n" }, {
      type: "addLog",
      path: "people/x.md",
      entry: { date: "2026-10-11", kind: "Coffee", notes: ["Hi"], followUps: [] },
    }).files["people/x.md"];
    expect(out).toBe("---\nname: X\n---\n\nNotes.\n\n## Log\n\n### 2026-10-11 · Coffee\n- Hi\n");
  });

  it("clears a planned catch-up once it has happened", () => {
    const before = run({ type: "addLog", path: P, entry: { date: "2026-10-11", kind: "Call", notes: [], followUps: [] } });
    expect(before).toContain("next_catchup");
    const after = run({ type: "addLog", path: P, entry: { date: "2026-10-20", kind: "Dinner", notes: [], followUps: [] } });
    expect(after).not.toContain("next_catchup");
    expect(after).toContain("# Maya's file");
  });
});

describe("frontmatter edits", () => {
  it("sets and removes fields while keeping comments", () => {
    const out = run({ type: "setFields", path: P, fields: { cadence: "monthly", next_catchup: null } });
    expect(out).toContain("# Maya's file");
    expect(out).toContain("tags: [college] # where we met");
    expect(out).toContain("cadence: monthly");
    expect(out).not.toContain("next_catchup");
    expect(out.endsWith("- Chalk bag\n")).toBe(true);
  });

  it("patches additively for imports", () => {
    const out = run({
      type: "patch",
      path: P,
      patch: {
        fill: { name: "Someone Else", birthday: "1991-10-19" },
        contact: { phone: "206-555-0142", email: "maya@example.com", instagram: "https://instagram.com/maya" },
        tags: ["college", "instagram"],
        lastContacted: "2026-09-01",
      },
    });
    const person = parsePerson("maya-patel", out).person!;
    expect(person.name).toBe("Maya Patel");
    expect(person.birthday).toEqual({ year: 1991, month: 10, day: 19 });
    expect(person.contact).toEqual({
      phone: "+1 206 555 0142",
      email: "maya@example.com",
      instagram: "https://instagram.com/maya",
    });
    expect(person.tags).toEqual(["college", "instagram"]);
    expect(out).toContain("last_contacted: 2026-09-01");

    const older = applyOp({ [P]: out }, { type: "patch", path: P, patch: { lastContacted: "2026-01-01" } }).files[P];
    expect(older).toContain("last_contacted: 2026-09-01");
  });

  it("adds a contact section and suffixes clashing keys", () => {
    const out = applyOps({ "people/x.md": "---\nname: X\n---\n" }, [
      { type: "patch", path: "people/x.md", patch: { contact: { phone: "+44 1234 567890" } } },
      { type: "patch", path: "people/x.md", patch: { contact: { phone: "+44 7777 000000" } } },
    ])["people/x.md"];
    expect(parsePerson("x", out).person!.contact).toEqual({ phone: "+44 1234 567890", phone_2: "+44 7777 000000" });
  });

  it("leaves unparseable frontmatter untouched", () => {
    const broken = "---\nname: [oops\n---\n";
    expect(applyOp({ "people/x.md": broken }, { type: "setFields", path: "people/x.md", fields: { a: 1 } }).files["people/x.md"]).toBe(broken);
  });
});

describe("follow-ups", () => {
  it("checks and unchecks items by text", () => {
    const done = run({ type: "setFollowUp", path: P, text: "Ask about Portland trip", done: true });
    expect(done).toContain("- [x] Ask about Portland trip");
    const reopened = applyOp({ [P]: done }, { type: "setFollowUp", path: P, text: "Send photos", done: false }).files[P];
    expect(reopened).toContain("- [ ] Send photos");
  });
});

describe("create / delete", () => {
  it("never overwrites an existing person", () => {
    const { files: out, path } = applyOp(files, { type: "create", path: P, content: "---\nname: Maya P\n---\n" });
    expect(path).toBe("people/maya-patel-2.md");
    expect(out[P]).toBe(MAYA);
  });

  it("skips edits to files deleted elsewhere", () => {
    const out = applyOps(files, [
      { type: "delete", path: P },
      { type: "setFields", path: P, fields: { cadence: "monthly" } },
    ]);
    expect(out).toEqual({});
  });
});

describe("serializePerson", () => {
  it("writes valid, minimal files", () => {
    const text = serializePerson({
      name: "Zoë O'Brien",
      tags: ["work", "running"],
      birthday: "03-04",
      contact: { phone: "+15551234567", email: "" },
      family: [],
      notes: "Met at the marathon.",
    });
    expect(text).toBe(`---\nname: Zoë O'Brien\ntags: [work, running]\nbirthday: 03-04\ncontact:\n  phone: "+15551234567"\n  email: ""\n---\n\nMet at the marathon.\n`);
    const { person, issues } = parsePerson("zoe-obrien", text);
    expect(issues).toEqual([]);
    expect(person!.contact.phone).toBe("+15551234567");
  });

  it("slugifies names", () => {
    expect(slugify("Zoë O'Brien")).toBe("zoe-o-brien");
    expect(slugify("  ")).toBe("person");
  });
});

describe("commitMessage", () => {
  it("summarises batches", () => {
    expect(commitMessage([{ type: "delete", path: P }])).toBe("Remove maya-patel");
    expect(commitMessage([{ type: "delete", path: P }, { type: "create", path: "people/a.md", content: "" }])).toBe(
      "CatchUp: 2 changes\n\n- Remove maya-patel\n- Add a",
    );
  });
});
