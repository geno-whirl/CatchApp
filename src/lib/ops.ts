// Edits to the data files, expressed as plain data ("ops") so they can be queued while
// offline and re-applied on top of the latest version of a file when syncing. That way
// changes made in the app and changes made by an agent (or another device) both survive.

import { Document, isMap, isSeq, parseDocument } from "yaml";
import { parseDate } from "./dates";

export interface LogInput {
  /** YYYY-MM-DD */
  date: string;
  kind: string;
  notes: string[];
  followUps: string[];
}

/** Additive changes used by imports: never overwrites what's already in a file. */
export interface PersonPatch {
  /** Frontmatter fields to set only when missing or empty. */
  fill?: Record<string, unknown>;
  /** Contact fields to add (skipped if the same value is already present). */
  contact?: Record<string, string>;
  tags?: string[];
  /** YYYY-MM-DD; applied only if newer than the current `last_contacted`. */
  lastContacted?: string;
}

export type Op =
  /** `ifExists`: "rename" (default) saves as `name-2.md`; "skip" keeps the existing file. */
  | { type: "create"; path: string; content: string; ifExists?: "rename" | "skip" }
  | { type: "replace"; path: string; content: string }
  | { type: "delete"; path: string }
  | { type: "addLog"; path: string; entry: LogInput }
  | { type: "setFollowUp"; path: string; text: string; done: boolean }
  | { type: "setFields"; path: string; fields: Record<string, unknown> }
  | { type: "patch"; path: string; patch: PersonPatch };

export type Files = Record<string, string>;

export const PEOPLE_DIR = "people";

export function personPath(id: string): string {
  return `${PEOPLE_DIR}/${id}.md`;
}

export function idFromPath(path: string): string {
  return path.replace(/^.*\//, "").replace(/\.md$/, "");
}

/** Lowercase, hyphenated, ASCII file-name stem for a person's name. */
export function slugify(name: string): string {
  const slug = name
    .normalize("NFKD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "");
  return slug || "person";
}

/** `people/maya-patel.md`, or `people/maya-patel-2.md` if that's taken. */
export function uniquePath(files: Files, path: string): string {
  if (!(path in files)) return path;
  const stem = path.replace(/\.md$/, "");
  for (let n = 2; ; n++) {
    const candidate = `${stem}-${n}.md`;
    if (!(candidate in files)) return candidate;
  }
}

// ---------------------------------------------------------------------------
// Frontmatter editing that keeps comments, key order and formatting intact.

const FRONTMATTER = /^(﻿?---\r?\n)([\s\S]*?\r?\n)?(---[ \t]*(?:\r?\n|$))/;

function editFrontmatter(raw: string, edit: (doc: Document) => void): string {
  const m = FRONTMATTER.exec(raw);
  if (!m) return raw;
  const doc = parseDocument(m[2] ?? "");
  if (doc.errors.length > 0) return raw; // Never rewrite a file we can't parse.
  if (!isMap(doc.contents)) doc.contents = doc.createNode({}) as unknown as typeof doc.contents;
  edit(doc);
  const yaml = isMap(doc.contents) && doc.contents.items.length === 0 ? "" : doc.toString({ lineWidth: 0, flowCollectionPadding: false });
  return m[1] + yaml + m[3] + raw.slice(m[0].length);
}

function isEmpty(value: unknown): boolean {
  if (value === null || value === undefined || value === "") return true;
  if (isSeq(value) || isMap(value)) return value.items.length === 0;
  return false;
}

function setFields(raw: string, fields: Record<string, unknown>): string {
  return editFrontmatter(raw, (doc) => {
    for (const [key, value] of Object.entries(fields)) {
      if (value === null || value === undefined || value === "") doc.delete(key);
      else doc.set(key, doc.createNode(value, { flow: Array.isArray(value) }));
    }
  });
}

function patchPerson(raw: string, patch: PersonPatch): string {
  return editFrontmatter(raw, (doc) => {
    for (const [key, value] of Object.entries(patch.fill ?? {})) {
      if (value === undefined || value === null || value === "") continue;
      if (isEmpty(doc.get(key))) doc.set(key, doc.createNode(value, { flow: Array.isArray(value) }));
    }

    if (patch.contact && Object.keys(patch.contact).length > 0) {
      if (!isMap(doc.get("contact"))) doc.set("contact", doc.createNode({}));
      const contact = doc.get("contact");
      const current = (isMap(contact) ? contact.toJSON() : {}) as Record<string, unknown>;
      const values = new Set(Object.values(current).map((v) => normalizeContact(String(v))));
      for (const [key, value] of Object.entries(patch.contact)) {
        if (!value || values.has(normalizeContact(value))) continue;
        let k = key;
        for (let n = 2; doc.hasIn(["contact", k]); n++) k = `${key}_${n}`;
        doc.setIn(["contact", k], value);
        values.add(normalizeContact(value));
      }
    }

    if (patch.tags && patch.tags.length > 0) {
      const tags = doc.get("tags");
      if (!isSeq(tags)) {
        doc.set("tags", doc.createNode(patch.tags, { flow: true }));
      } else {
        const have = new Set(tags.toJSON().map(String));
        for (const tag of patch.tags) if (!have.has(tag)) tags.add(doc.createNode(tag));
      }
    }

    if (patch.lastContacted) {
      const current = doc.get("last_contacted");
      const currentDate = typeof current === "string" ? parseDate(current) : null;
      const next = parseDate(patch.lastContacted);
      if (next && (!currentDate || next > currentDate)) doc.set("last_contacted", patch.lastContacted);
    }
  });
}

function normalizeContact(value: string): string {
  const v = value.trim().toLowerCase();
  // Phone numbers: compare by their last 9 digits so "+1 206 555 0142" == "206-555-0142".
  const digits = v.replace(/\D/g, "");
  if (digits.length >= 7 && /^[\d\s+().-]+$/.test(v)) return digits.slice(-9);
  return v.replace(/^https?:\/\/(www\.)?/, "").replace(/\/+$/, "");
}

// ---------------------------------------------------------------------------
// Body editing.

const LOG_HEADING = /^##\s+log\s*$/i;
const H2 = /^##\s/;
const LOG_DATE = /^###\s+(\d{4}-\d{2}-\d{2})\b/;

export function formatLogEntry(entry: LogInput): string {
  const lines = [`### ${entry.date}${entry.kind.trim() ? ` · ${entry.kind.trim()}` : ""}`];
  for (const note of entry.notes) if (note.trim()) lines.push(`- ${note.trim()}`);
  for (const item of entry.followUps) if (item.trim()) lines.push(`- [ ] ${item.trim()}`);
  return lines.join("\n");
}

/** Inserts the entry under `## Log`, keeping entries newest first. */
function addLog(raw: string, entry: LogInput): string {
  const text = formatLogEntry(entry);
  const lines = raw.split("\n");
  const start = lines.findIndex((l) => LOG_HEADING.test(l.trimEnd()));

  if (start === -1) {
    const trimmed = raw.replace(/\s+$/, "");
    return `${trimmed}\n\n## Log\n\n${text}\n`;
  }

  let end = lines.length;
  for (let i = start + 1; i < lines.length; i++) {
    if (H2.test(lines[i])) {
      end = i;
      break;
    }
  }

  let at = -1;
  for (let i = start + 1; i < end; i++) {
    const m = LOG_DATE.exec(lines[i]);
    if (m && m[1] <= entry.date) {
      at = i;
      break;
    }
  }

  if (at !== -1) {
    lines.splice(at, 0, ...text.split("\n"), "");
    return lines.join("\n");
  }

  // Oldest entry (or empty section): append at the end of the section.
  let insertAt = end;
  while (insertAt > start + 1 && lines[insertAt - 1].trim() === "") insertAt--;
  const block = ["", ...text.split("\n")];
  if (end < lines.length) block.push("");
  lines.splice(insertAt, end - insertAt, ...block);
  const result = lines.join("\n");
  return result.endsWith("\n") ? result : `${result}\n`;
}

function setFollowUp(raw: string, text: string, done: boolean): string {
  const lines = raw.split("\n");
  const target = text.trim();
  const i = lines.findIndex((line) => {
    const m = /^(\s*[-*+]\s+\[)([ xX])(\]\s+)(.+?)\s*$/.exec(line);
    return m !== null && m[4] === target && (m[2] !== " ") !== done;
  });
  if (i === -1) return raw;
  lines[i] = lines[i].replace(/\[[ xX]\]/, done ? "[x]" : "[ ]");
  return lines.join("\n");
}

/** Removes a planned catch-up once a log entry on or after that day is added. */
function clearPlannedIfDone(raw: string, date: string): string {
  const m = FRONTMATTER.exec(raw);
  if (!m) return raw;
  const doc = parseDocument(m[2] ?? "");
  const planned = doc.get("next_catchup");
  if (typeof planned !== "string" || planned.slice(0, 10) > date) return raw;
  return setFields(raw, { next_catchup: null });
}

// ---------------------------------------------------------------------------

/** Applies one op to a set of files. Returns the new files and the path it touched. */
export function applyOp(files: Files, op: Op): { files: Files; path: string } {
  const next = { ...files };
  const current = files[op.path];

  switch (op.type) {
    case "create": {
      if (op.ifExists === "skip" && op.path in files) return { files, path: op.path };
      const path = uniquePath(files, op.path);
      next[path] = op.content;
      return { files: next, path };
    }
    case "replace":
      next[op.path] = op.content;
      return { files: next, path: op.path };
    case "delete":
      delete next[op.path];
      return { files: next, path: op.path };
  }

  // The remaining ops edit an existing file; if it was deleted elsewhere, skip them.
  if (current === undefined) return { files, path: op.path };

  switch (op.type) {
    case "addLog":
      next[op.path] = clearPlannedIfDone(addLog(current, op.entry), op.entry.date);
      break;
    case "setFollowUp":
      next[op.path] = setFollowUp(current, op.text, op.done);
      break;
    case "setFields":
      next[op.path] = setFields(current, op.fields);
      break;
    case "patch":
      next[op.path] = patchPerson(current, op.patch);
      break;
  }
  return { files: next, path: op.path };
}

export function applyOps(files: Files, ops: Op[]): Files {
  return ops.reduce((acc, op) => applyOp(acc, op).files, files);
}

/** Short, human commit message for an op. */
export function describeOp(op: Op): string {
  const who = idFromPath(op.path);
  switch (op.type) {
    case "create":
      return `Add ${who}`;
    case "replace":
      return `Edit ${who}`;
    case "delete":
      return `Remove ${who}`;
    case "addLog":
      return `Log ${op.entry.kind.toLowerCase() || "catch-up"} with ${who} (${op.entry.date})`;
    case "setFollowUp":
      return `${op.done ? "Complete" : "Reopen"} follow-up for ${who}`;
    case "setFields":
      return `Update ${Object.keys(op.fields).join(", ")} for ${who}`;
    case "patch":
      return `Import details for ${who}`;
  }
}

export function commitMessage(ops: Op[]): string {
  if (ops.length === 1) return describeOp(ops[0]);
  return `CatchUp: ${ops.length} changes\n\n${ops.map((op) => `- ${describeOp(op)}`).join("\n")}`;
}

// ---------------------------------------------------------------------------
// New files.

export interface NewPerson {
  name: string;
  nickname?: string;
  relationship?: string;
  tags?: string[];
  birthday?: string;
  location?: string;
  contact?: Record<string, string>;
  family?: { name: string; relation?: string; birthday?: string }[];
  how_we_met?: string;
  cadence?: string;
  last_contacted?: string;
  notes?: string;
}

/** Renders a new person file in the documented format, omitting empty fields. */
export function serializePerson(person: NewPerson): string {
  const { notes, ...fields } = person;
  const data: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(fields)) {
    if (value === undefined || value === null || value === "") continue;
    if (Array.isArray(value) && value.length === 0) continue;
    if (typeof value === "object" && !Array.isArray(value) && Object.keys(value).length === 0) continue;
    data[key] = value;
  }
  const doc = new Document(data);
  const tags = doc.get("tags");
  if (isSeq(tags)) tags.flow = true;
  const body = notes?.trim() ? `\n${notes.trim()}\n` : "";
  return `---\n${doc.toString({ lineWidth: 0, flowCollectionPadding: false })}---\n${body}`;
}
