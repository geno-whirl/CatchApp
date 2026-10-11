// Parses a person's Markdown file (YAML frontmatter + Markdown body) into a Person.
// The file format is documented in docs/data-format.md — keep the two in sync.

import { parse as parseYaml } from "yaml";
import { type Birthday, cadenceToDays, parseBirthday, parseDate } from "./dates";

export interface FamilyMember {
  name: string;
  relation?: string;
  birthday?: Birthday;
  notes?: string;
}

export interface LogEntry {
  date: Date;
  title?: string;
}

/** A `- [ ] …` task anywhere in the body: something to bring up next time. */
export interface FollowUp {
  text: string;
  done: boolean;
}

export interface PlannedCatchUp {
  /** Local date (and time, when `hasTime`). */
  date: Date;
  hasTime: boolean;
}

export interface Person {
  /** File name without `.md`, e.g. `ada-lovelace`. */
  id: string;
  name: string;
  nickname?: string;
  relationship?: string;
  tags: string[];
  birthday?: Birthday;
  location?: string;
  contact: Record<string, string>;
  family: FamilyMember[];
  howWeMet?: string;
  cadence?: string;
  cadenceDays?: number;
  /** Most recent of `last_contacted` and the newest `## Log` entry. */
  lastContacted?: Date;
  log: LogEntry[];
  followUps: FollowUp[];
  nextCatchup?: PlannedCatchUp;
  /** Markdown after the frontmatter: free-form notes and the log. */
  body: string;
}

export interface Issue {
  level: "error" | "warning";
  message: string;
}

export interface ParseResult {
  person: Person | null;
  issues: Issue[];
}

const KNOWN_KEYS = new Set([
  "name",
  "nickname",
  "relationship",
  "tags",
  "birthday",
  "location",
  "contact",
  "family",
  "how_we_met",
  "cadence",
  "last_contacted",
  "next_catchup",
  "priority",
  "next_contact",
  "plans",
]);

const FRONTMATTER = /^---\r?\n([\s\S]*?)\r?\n---[ \t]*(?:\r?\n|$)/;
const LOG_HEADING = /^##\s+log\s*$/i;
const LOG_ENTRY = /^###\s+(\d{4}-\d{2}-\d{2})(?:[ T]\d{1,2}:\d{2})?\b\s*(?:[-–—·:|]\s*)?(.*)$/;
const TASK = /^\s*[-*+]\s+\[([ xX])\]\s+(.+?)\s*$/;
const DATE_TIME = /^(\d{4}-\d{2}-\d{2})(?:[ T](\d{1,2}):(\d{2}))?$/;

export function splitFrontmatter(raw: string): { data: unknown; body: string } | null {
  const text = raw.replace(/^﻿/, "");
  const m = FRONTMATTER.exec(text);
  if (!m) return null;
  return { data: parseYaml(m[1]) ?? {}, body: text.slice(m[0].length) };
}

/** Reads dated `### YYYY-MM-DD` entries under the `## Log` heading, newest first. */
export function parseLog(body: string): LogEntry[] {
  const entries: LogEntry[] = [];
  let inLog = false;
  for (const line of body.split(/\r?\n/)) {
    if (/^##\s/.test(line)) {
      inLog = LOG_HEADING.test(line);
      continue;
    }
    if (!inLog) continue;
    const m = LOG_ENTRY.exec(line);
    const date = m && parseDate(m[1]);
    if (date) entries.push({ date, title: m[2].trim() || undefined });
  }
  return entries.sort((a, b) => b.date.getTime() - a.date.getTime());
}

/** All `- [ ]` / `- [x]` items in the body, in file order. */
export function parseFollowUps(body: string): FollowUp[] {
  const items: FollowUp[] = [];
  for (const line of body.split(/\r?\n/)) {
    const m = TASK.exec(line);
    if (m) items.push({ text: m[2], done: m[1] !== " " });
  }
  return items;
}

/** Parses `YYYY-MM-DD` or `YYYY-MM-DD HH:MM`. */
export function parseDateTime(value: string): PlannedCatchUp | null {
  const m = DATE_TIME.exec(value.trim());
  const day = m && parseDate(m[1]);
  if (!m || !day) return null;
  if (m[2] === undefined) return { date: day, hasTime: false };
  const [hours, minutes] = [Number(m[2]), Number(m[3])];
  if (hours > 23 || minutes > 59) return null;
  day.setHours(hours, minutes);
  return { date: day, hasTime: true };
}

function asText(value: unknown): string | undefined {
  if (value === null || value === undefined) return undefined;
  if (typeof value === "string") return value.trim() || undefined;
  if (typeof value === "number" || typeof value === "boolean") return String(value);
  return undefined;
}

export function parsePerson(id: string, raw: string): ParseResult {
  const issues: Issue[] = [];
  const error = (message: string) => issues.push({ level: "error", message });
  const warn = (message: string) => issues.push({ level: "warning", message });

  let split: ReturnType<typeof splitFrontmatter>;
  try {
    split = splitFrontmatter(raw);
  } catch (e) {
    error(`Frontmatter is not valid YAML: ${(e as Error).message.split("\n")[0]}`);
    return { person: null, issues };
  }
  if (!split) {
    error("Missing YAML frontmatter (the file must start with a `---` block).");
    return { person: null, issues };
  }
  if (typeof split.data !== "object" || Array.isArray(split.data)) {
    error("Frontmatter must be a set of `key: value` fields.");
    return { person: null, issues };
  }
  const data = split.data as Record<string, unknown>;

  for (const key of Object.keys(data)) {
    if (!KNOWN_KEYS.has(key)) warn(`Unknown field \`${key}\` (ignored).`);
  }

  const name = asText(data.name);
  if (!name) {
    error("`name` is required.");
    return { person: null, issues };
  }

  let tags: string[] = [];
  if (Array.isArray(data.tags)) {
    tags = data.tags.map(asText).filter((t): t is string => !!t);
  } else if (data.tags !== undefined && data.tags !== null) {
    warn("`tags` should be a list, e.g. `tags: [college, hiking]`.");
  }

  let birthday: Birthday | undefined;
  const birthdayRaw = asText(data.birthday);
  if (birthdayRaw) {
    birthday = parseBirthday(birthdayRaw) ?? undefined;
    if (!birthday) error(`\`birthday\` "${birthdayRaw}" must be YYYY-MM-DD or MM-DD.`);
  }

  const contact: Record<string, string> = {};
  if (data.contact && typeof data.contact === "object" && !Array.isArray(data.contact)) {
    for (const [key, value] of Object.entries(data.contact)) {
      if (typeof value === "number") {
        warn(`\`contact.${key}\` was read as a number; wrap it in quotes to keep leading + or 0.`);
      }
      const text = asText(value);
      if (text) contact[key] = text;
    }
  } else if (data.contact !== undefined && data.contact !== null) {
    warn("`contact` should be a set of fields, e.g. `phone`, `email`.");
  }

  const family: FamilyMember[] = [];
  if (Array.isArray(data.family)) {
    data.family.forEach((entry, i) => {
      if (typeof entry === "string") {
        family.push({ name: entry });
        return;
      }
      const member = entry as Record<string, unknown> | null;
      const memberName = member && asText(member.name);
      if (!member || !memberName) {
        warn(`\`family[${i}]\` needs a \`name\`.`);
        return;
      }
      const memberBirthdayRaw = asText(member.birthday);
      const memberBirthday = memberBirthdayRaw ? parseBirthday(memberBirthdayRaw) : null;
      if (memberBirthdayRaw && !memberBirthday) {
        error(`\`family[${i}].birthday\` "${memberBirthdayRaw}" must be YYYY-MM-DD or MM-DD.`);
      }
      family.push({
        name: memberName,
        relation: asText(member.relation),
        birthday: memberBirthday ?? undefined,
        notes: asText(member.notes),
      });
    });
  } else if (data.family !== undefined && data.family !== null) {
    warn("`family` should be a list of people.");
  }

  const cadence = asText(data.cadence);
  const cadenceDays = cadence ? cadenceToDays(cadence) ?? undefined : undefined;
  if (cadence && cadenceDays === undefined) {
    error(`\`cadence\` "${cadence}" is not recognised (try monthly, quarterly, or 6w).`);
  }

  let lastContacted: Date | undefined;
  const lastContactedRaw = asText(data.last_contacted);
  if (lastContactedRaw) {
    lastContacted = parseDate(lastContactedRaw) ?? undefined;
    if (!lastContacted) error(`\`last_contacted\` "${lastContactedRaw}" must be YYYY-MM-DD.`);
  }

  const priority = asText(data.priority);
  if (priority && !["high", "medium", "low"].includes(priority.toLowerCase())) {
    error(`\`priority\` "${priority}" should be high, medium or low.`);
  }
  const nextContact = asText(data.next_contact);
  if (nextContact && !parseDate(nextContact)) error(`\`next_contact\` "${nextContact}" must be YYYY-MM-DD.`);

  // Plans (scheduled catch-ups): the earliest one is shown as the next catch-up here.
  const plans: PlannedCatchUp[] = [];
  if (Array.isArray(data.plans)) {
    data.plans.forEach((entry, i) => {
      const when = asText((entry as Record<string, unknown> | null)?.when);
      const parsed = when ? parseDateTime(when) : null;
      if (!parsed) error(`\`plans[${i}].when\` must be YYYY-MM-DD or YYYY-MM-DD HH:MM.`);
      else plans.push(parsed);
    });
  } else if (data.plans !== undefined && data.plans !== null) {
    warn("`plans` should be a list.");
  }

  let nextCatchup: PlannedCatchUp | undefined;
  const nextCatchupRaw = asText(data.next_catchup);
  if (nextCatchupRaw) {
    nextCatchup = parseDateTime(nextCatchupRaw) ?? undefined;
    if (!nextCatchup) {
      error(`\`next_catchup\` "${nextCatchupRaw}" must be YYYY-MM-DD or YYYY-MM-DD HH:MM.`);
    }
  }

  if (!nextCatchup && plans.length > 0) {
    nextCatchup = plans.sort((a, b) => a.date.getTime() - b.date.getTime())[0];
  }

  const log = parseLog(split.body);
  if (log.length > 0 && (!lastContacted || log[0].date > lastContacted)) {
    lastContacted = log[0].date;
  }

  return {
    person: {
      id,
      name,
      nickname: asText(data.nickname),
      relationship: asText(data.relationship),
      tags,
      birthday,
      location: asText(data.location),
      contact,
      family,
      howWeMet: asText(data.how_we_met),
      cadence,
      cadenceDays,
      lastContacted,
      log,
      followUps: parseFollowUps(split.body),
      nextCatchup,
      body: split.body.trim(),
    },
    issues,
  };
}
