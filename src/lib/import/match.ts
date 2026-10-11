// Merges duplicate candidates and matches them to people already in CatchUp, producing
// the ops an import would apply: a new file, or additive updates to an existing one.

import { toIsoDate } from "../dates";
import { applyOp, type Files, type Op, personPath, type PersonPatch, serializePerson, slugify } from "../ops";
import type { Person } from "../person";
import { type Candidate, normalizeName } from "./types";

export interface ImportItem {
  key: string;
  candidate: Candidate;
  match?: Person;
  /** What would change, e.g. ["birthday", "phone", "last contact"]. */
  changes: string[];
  op: Op | null;
}

function contactKey(value: string): string {
  const v = value.trim().toLowerCase();
  const digits = v.replace(/\D/g, "");
  if (digits.length >= 7 && /^[\d\s+().-]+$/.test(v)) return `tel:${digits.slice(-9)}`;
  return v.replace(/^https?:\/\/(www\.)?/, "").replace(/\/+$/, "");
}

function later(a?: string, b?: string): string | undefined {
  if (!a) return b;
  if (!b) return a;
  return a > b ? a : b;
}

/** Combines candidates for the same person (e.g. a friend you also message). */
export function mergeCandidates(candidates: Candidate[]): Candidate[] {
  const byName = new Map<string, Candidate>();
  for (const c of candidates) {
    const key = normalizeName(c.name);
    const existing = byName.get(key);
    if (!existing) {
      byName.set(key, { ...c, contact: { ...c.contact }, family: [...c.family], tags: [...c.tags] });
      continue;
    }
    existing.nickname ??= c.nickname;
    existing.birthday ??= c.birthday;
    existing.location ??= c.location;
    existing.howWeMet ??= c.howWeMet;
    existing.notes ??= c.notes;
    existing.lastContacted = later(existing.lastContacted, c.lastContacted);
    existing.detail = [existing.detail, c.detail].filter(Boolean).join(" · ");
    for (const [k, v] of Object.entries(c.contact)) {
      if (!Object.values(existing.contact).some((x) => contactKey(x) === contactKey(v))) {
        let key2 = k;
        for (let n = 2; key2 in existing.contact; n++) key2 = `${k}_${n}`;
        existing.contact[key2] = v;
      }
    }
    for (const t of c.tags) if (!existing.tags.includes(t)) existing.tags.push(t);
    for (const f of c.family) if (!existing.family.some((x) => normalizeName(x.name) === normalizeName(f.name))) existing.family.push(f);
  }
  return [...byName.values()];
}

export function findMatch(c: Candidate, people: Person[]): Person | undefined {
  const name = normalizeName(c.name);
  const byName = people.find(
    (p) => normalizeName(p.name) === name || (p.nickname && normalizeName(p.nickname) === name),
  );
  if (byName) return byName;
  const keys = new Set(Object.values(c.contact).map(contactKey));
  if (keys.size === 0) return undefined;
  return people.find((p) => Object.values(p.contact).some((v) => keys.has(contactKey(v))));
}

export function planImport(candidates: Candidate[], people: Person[], files: Files): ImportItem[] {
  return mergeCandidates(candidates).map((candidate, i) => {
    const match = findMatch(candidate, people);
    const key = `${i}:${normalizeName(candidate.name)}`;

    if (!match) {
      const op: Op = {
        type: "create",
        path: personPath(slugify(candidate.name)),
        content: serializePerson({
          name: candidate.name,
          nickname: candidate.nickname,
          birthday: candidate.birthday,
          location: candidate.location,
          tags: candidate.tags,
          contact: candidate.contact,
          family: candidate.family,
          how_we_met: candidate.howWeMet,
          last_contacted: candidate.lastContacted,
          notes: candidate.notes,
        }),
      };
      return { key, candidate, changes: ["new"], op };
    }

    const patch: PersonPatch = {
      fill: {
        nickname: candidate.nickname,
        birthday: candidate.birthday,
        location: candidate.location,
        how_we_met: candidate.howWeMet,
        family: candidate.family.length > 0 ? candidate.family : undefined,
      },
      contact: candidate.contact,
      tags: candidate.tags,
      lastContacted: candidate.lastContacted,
    };
    const op: Op = { type: "patch", path: personPath(match.id), patch };

    // Describe only what would actually change.
    const changes: string[] = [];
    if (candidate.nickname && !match.nickname) changes.push("nickname");
    if (candidate.birthday && !match.birthday) changes.push("birthday");
    if (candidate.location && !match.location) changes.push("location");
    if (candidate.howWeMet && !match.howWeMet) changes.push("how you met");
    if (candidate.family.length > 0 && match.family.length === 0) changes.push("family");
    const have = new Set(Object.values(match.contact).map(contactKey));
    for (const [k, v] of Object.entries(candidate.contact)) if (!have.has(contactKey(v))) changes.push(k.replace(/_\d+$/, ""));
    const newTags = candidate.tags.filter((t) => !match.tags.includes(t));
    if (newTags.length > 0) changes.push("tags");
    if (candidate.lastContacted && (!match.lastContacted || candidate.lastContacted > toIsoDate(match.lastContacted))) {
      changes.push("last contact");
    }

    const before = files[op.path];
    const unchanged = changes.length === 0 || (before !== undefined && applyOp(files, op).files[op.path] === before);
    return { key, candidate, match, changes: unchanged ? [] : [...new Set(changes)], op: unchanged ? null : op };
  });
}
