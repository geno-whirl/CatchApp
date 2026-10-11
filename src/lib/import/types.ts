export type ImportSource = "contacts" | "whatsapp" | "facebook" | "instagram";

/** Someone found in an import, before matching against existing people. */
export interface Candidate {
  name: string;
  source: ImportSource;
  nickname?: string;
  /** YYYY-MM-DD or MM-DD */
  birthday?: string;
  location?: string;
  contact: Record<string, string>;
  family: { name: string; relation?: string }[];
  howWeMet?: string;
  /** YYYY-MM-DD of the most recent message. */
  lastContacted?: string;
  notes?: string;
  tags: string[];
  /** Short context for the preview, e.g. "312 messages". */
  detail?: string;
}

export interface ImportFile {
  /** Path including any zip it came from, e.g. `export.zip/connections/friends.json`. */
  name: string;
  text: string;
}

export interface ParseOutcome {
  candidates: Candidate[];
  /** Human-readable notes about what was skipped or assumed. */
  notes: string[];
}

export function candidate(name: string, source: ImportSource, extra: Partial<Candidate> = {}): Candidate {
  return { name, source, contact: {}, family: [], tags: [], ...extra };
}

export function normalizeName(name: string): string {
  return name
    .normalize("NFKD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^\p{L}\p{N}]+/gu, " ")
    .trim();
}
