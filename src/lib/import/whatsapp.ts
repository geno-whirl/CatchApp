// Reads WhatsApp "Export chat" files (Without media). Only the date of the last
// message and the message count are kept — message text never leaves the parser.

import { toIsoDate } from "../dates";
import { type Candidate, candidate, type ImportFile, normalizeName, type ParseOutcome } from "./types";

const LINE =
  /^\[?(\d{1,4})[./-](\d{1,2})[./-](\d{1,4}),?\s+(\d{1,2})[:.](\d{2})(?:[:.]\d{2})?(?:\s*([AaPp])\.?\s?[Mm]\.?)?\]?\s*(?:[-–]\s+)?(.*)$/;

interface Chat {
  title?: string;
  participants: Map<string, { name: string; count: number }>;
  messages: number;
  last?: Date;
}

/** Name from the export's file name, e.g. "WhatsApp Chat with Maya Patel.txt". */
export function chatTitle(fileName: string): string | undefined {
  const parts = fileName.split("/");
  // iOS exports are zips ("WhatsApp Chat - Maya Patel.zip") containing "_chat.txt".
  const base = parts[parts.length - 1] === "_chat.txt" && parts.length > 1 ? parts[parts.length - 2] : parts[parts.length - 1];
  const stem = base.replace(/\.(txt|zip)$/i, "").trim();
  // "WhatsApp Chat with X", "WhatsApp Chat - X", "WhatsApp-Chat mit X", "Chat de WhatsApp con X"…
  const m = /whats\s*app[\s-]*(?:chat)?\s*(?:with|con|mit|com|avec|met|-|–|:)\s*(.+)$/i.exec(stem);
  return m ? m[1].trim() : undefined;
}

const INVISIBLE = /[‎‏‪-‮⁦-⁩]/g;

function parseChat(text: string, title?: string): Chat {
  const rows: { a: number; b: number; c: string; h: number; min: number; ampm?: string; sender: string }[] = [];
  for (const raw of text.split(/\r?\n/)) {
    const line = raw.replace(INVISIBLE, "").replace(/[  ]/g, " ");
    const m = LINE.exec(line);
    if (!m) continue;
    const rest = m[7];
    const colon = rest.indexOf(": ");
    if (colon <= 0) continue; // System message ("Messages are end-to-end encrypted").
    rows.push({
      a: Number(m[1]),
      b: Number(m[2]),
      c: m[3],
      h: Number(m[4]),
      min: Number(m[5]),
      ampm: m[6]?.toLowerCase(),
      sender: rest.slice(0, colon).trim(),
    });
  }

  // Work out the date order: Y-M-D, D/M/Y (most of the world) or M/D/Y (US, with AM/PM).
  const yearFirst = rows.some((r) => r.a > 31);
  const dayFirst = rows.some((r) => r.a > 12) || (!rows.some((r) => r.b > 12) && !rows.some((r) => r.ampm));

  const chat: Chat = { title, participants: new Map(), messages: 0 };
  for (const r of rows) {
    let year: number, month: number, day: number;
    if (yearFirst) [year, month, day] = [r.a, r.b, Number(r.c)];
    else {
      year = Number(r.c) < 100 ? 2000 + Number(r.c) : Number(r.c);
      [day, month] = dayFirst ? [r.a, r.b] : [r.b, r.a];
    }
    let hours = r.h % (r.ampm ? 12 : 24);
    if (r.ampm === "p") hours += 12;
    const date = new Date(year, month - 1, day, hours, r.min);
    if (Number.isNaN(date.getTime()) || date.getMonth() !== month - 1) continue;

    const key = normalizeName(r.sender);
    const p = chat.participants.get(key) ?? { name: r.sender, count: 0 };
    p.count++;
    chat.participants.set(key, p);
    chat.messages++;
    if (!chat.last || date > chat.last) chat.last = date;
  }
  return chat;
}

const isPhone = (s: string) => /^\+?[\d\s()-]{7,}$/.test(s);

export function parseWhatsApp(files: ImportFile[]): ParseOutcome {
  const chats = files.map((f) => parseChat(f.text, chatTitle(f.name))).filter((c) => c.messages > 0);
  const notes: string[] = [];

  // You appear in every chat; with several chats we can tell which name is yours.
  const appearances = new Map<string, number>();
  for (const chat of chats) for (const key of chat.participants.keys()) appearances.set(key, (appearances.get(key) ?? 0) + 1);
  const self =
    chats.length > 1 ? [...appearances.entries()].find(([, n]) => n === chats.length)?.[0] : undefined;

  const candidates: Candidate[] = [];
  for (const chat of chats) {
    const titleKey = chat.title ? normalizeName(chat.title) : undefined;
    let others = [...chat.participants.entries()].filter(([key]) => key !== self);
    const byTitle = others.find(([key]) => key === titleKey);
    if (byTitle) others = [byTitle];
    else if (others.length > 2 || (self && others.length > 1)) {
      notes.push(`Skipped group chat${chat.title ? ` "${chat.title}"` : ""}.`);
      continue;
    }

    for (const [, { name }] of others) {
      const c = candidate(name, "whatsapp", {
        lastContacted: chat.last && toIsoDate(chat.last),
        detail: `${chat.messages} WhatsApp messages`,
      });
      if (isPhone(name)) c.contact.whatsapp = name;
      candidates.push(c);
    }
    if (others.length > 1) notes.push(`Couldn't tell which name in "${chat.title ?? "a chat"}" is yours — untick it.`);
  }
  if (files.length > 0 && chats.length === 0) notes.push("No WhatsApp messages found in these files.");
  return { candidates, notes };
}
