// Reads Facebook and Instagram "Download your information" exports (JSON format):
// friends, mutual follows and 1:1 message threads (only dates and counts are kept).

import { toIsoDate } from "../dates";
import { type Candidate, candidate, type ImportFile, type ImportSource, normalizeName, type ParseOutcome } from "./types";

/** Meta's JSON stores UTF-8 bytes as \u00XX escapes ("ZoÃ«" instead of "Zoë"). */
export function fixMojibake(text: string): string {
  if (!/[\u0080-ÿ]/.test(text) || /[^\u0000-ÿ]/.test(text)) return text;
  try {
    return new TextDecoder("utf-8", { fatal: true }).decode(Uint8Array.from(text, (c) => c.charCodeAt(0)));
  } catch {
    return text;
  }
}

interface Thread {
  folder?: string;
  participants: string[];
  count: number;
  last?: number;
}

interface StringListItem {
  title?: string;
  string_list_data?: { href?: string; value?: string; timestamp?: number }[];
}

function handleOf(item: StringListItem): { handle: string; href?: string; timestamp?: number } | null {
  const data = item.string_list_data?.[0];
  const handle = item.title || data?.value || data?.href?.replace(/^.*instagram\.com\/(_u\/)?/, "").replace(/\/$/, "");
  return handle ? { handle, href: data?.href, timestamp: data?.timestamp } : null;
}

function asArray<T>(value: unknown): T[] {
  return Array.isArray(value) ? (value as T[]) : [];
}

export function parseMetaExport(files: ImportFile[], source: Extract<ImportSource, "facebook" | "instagram">): ParseOutcome {
  const notes: string[] = [];
  const friends: { name: string; since?: number }[] = [];
  const following: ReturnType<typeof handleOf>[] = [];
  const followers = new Set<string>();
  let sawFollowers = false;
  const threads: Thread[] = [];
  let selfName: string | undefined;

  for (const file of files) {
    if (!file.name.toLowerCase().endsWith(".json")) continue;
    let data: unknown;
    try {
      data = JSON.parse(file.text);
    } catch {
      notes.push(`Couldn't read ${file.name.split("/").pop()}.`);
      continue;
    }
    const obj = (data ?? {}) as Record<string, unknown>;

    // Facebook friends.
    for (const f of asArray<{ name?: string; timestamp?: number }>(obj.friends_v2 ?? obj.friends)) {
      if (f.name) friends.push({ name: fixMojibake(f.name), since: f.timestamp });
    }

    // Instagram follows. followers_1.json is a bare array.
    for (const item of asArray<StringListItem>(obj.relationships_following)) following.push(handleOf(item));
    const followerItems = Array.isArray(data) && /followers/i.test(file.name) ? (data as StringListItem[]) : asArray<StringListItem>(obj.relationships_followers);
    if (followerItems.length > 0) sawFollowers = true;
    for (const item of followerItems) {
      const h = handleOf(item);
      if (h) followers.add(h.handle.toLowerCase());
    }

    // Message threads (same shape on Facebook and Instagram).
    if (Array.isArray(obj.participants) && Array.isArray(obj.messages)) {
      const parts = file.name.split("/");
      const messages = obj.messages as { timestamp_ms?: number }[];
      threads.push({
        folder: parts.length > 1 ? parts[parts.length - 2] : undefined,
        participants: (obj.participants as { name?: string }[]).map((p) => fixMojibake(p.name ?? "")).filter(Boolean),
        count: messages.length,
        last: Math.max(0, ...messages.map((m) => m.timestamp_ms ?? 0)) || undefined,
      });
    }

    // Your own name, when the export includes your profile.
    const profile = obj.profile_v2 as { name?: { full_name?: string } } | undefined;
    if (profile?.name?.full_name) selfName = fixMojibake(profile.name.full_name);
    const igProfile = asArray<{ string_map_data?: Record<string, { value?: string }> }>(obj.profile_user)[0];
    const igName = igProfile?.string_map_data?.Name?.value;
    if (igName) selfName = fixMojibake(igName);
  }

  const candidates: Candidate[] = [];

  for (const f of friends) {
    const year = f.since ? new Date(f.since * 1000).getFullYear() : undefined;
    candidates.push(candidate(f.name, source, { howWeMet: year ? `Facebook friends since ${year}.` : undefined, detail: "Facebook friend" }));
  }

  for (const f of following) {
    if (!f) continue;
    // Prefer mutual follows: people you follow who follow you back.
    if (sawFollowers && !followers.has(f.handle.toLowerCase())) continue;
    candidates.push(
      candidate(f.handle, source, {
        contact: { instagram: f.href?.startsWith("http") ? f.href : `https://www.instagram.com/${f.handle}` },
        detail: sawFollowers ? "Mutual follow" : "You follow them",
      }),
    );
  }

  // Work out your name: given in the profile, or the one person in most threads.
  let self = selfName ? normalizeName(selfName) : undefined;
  if (!self && threads.length > 1) {
    const counts = new Map<string, number>();
    for (const t of threads) for (const p of new Set(t.participants.map(normalizeName))) counts.set(p, (counts.get(p) ?? 0) + 1);
    const [top, n] = [...counts.entries()].sort((a, b) => b[1] - a[1])[0] ?? [];
    if (top && n >= threads.length * 0.6) self = top;
  }

  let groups = 0;
  for (const t of threads) {
    if (t.participants.length !== 2) {
      if (t.participants.length > 2) groups++;
      continue;
    }
    let others = t.participants.filter((p) => normalizeName(p) !== self);
    if (others.length === 2 && t.folder) {
      // Thread folders are named after the other person, e.g. "mayapatel_10158…".
      const hint = t.folder.replace(/_\d+$/, "").toLowerCase();
      const byFolder = others.filter((p) => normalizeName(p).replace(/ /g, "") === hint);
      if (byFolder.length === 1) others = byFolder;
    }
    for (const name of others) {
      candidates.push(
        candidate(name, source, {
          lastContacted: t.last ? toIsoDate(new Date(t.last)) : undefined,
          detail: `${t.count} ${source === "facebook" ? "Messenger" : "Instagram"} messages`,
        }),
      );
    }
    if (others.length > 1) notes.push("Couldn't tell which name is yours in some threads — untick yourself.");
  }
  if (groups > 0) notes.push(`Skipped ${groups} group conversation${groups > 1 ? "s" : ""}.`);
  if (candidates.length === 0) {
    notes.push(
      source === "facebook"
        ? "No friends or messages found. Make sure the export is in JSON format and includes Connections and Messages."
        : "No follows or messages found. Make sure the export is in JSON format and includes Followers and following and Messages.",
    );
  }
  return { candidates, notes: [...new Set(notes)] };
}
