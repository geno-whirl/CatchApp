// Reads .vcf files exported from iPhone Contacts, Google Contacts, Android and Outlook.

import { type Candidate, candidate, type ParseOutcome } from "./types";

interface Property {
  group?: string;
  name: string;
  params: Record<string, string[]>;
  value: string;
}

function decodeQuotedPrintable(value: string, charset = "utf-8"): string {
  const bytes: number[] = [];
  for (let i = 0; i < value.length; i++) {
    if (value[i] === "=" && /^[0-9A-Fa-f]{2}$/.test(value.slice(i + 1, i + 3))) {
      bytes.push(parseInt(value.slice(i + 1, i + 3), 16));
      i += 2;
    } else {
      bytes.push(...new TextEncoder().encode(value[i]));
    }
  }
  try {
    return new TextDecoder(charset).decode(new Uint8Array(bytes));
  } catch {
    return new TextDecoder().decode(new Uint8Array(bytes));
  }
}

function unescape(value: string): string {
  return value.replace(/\\([\\,;nN])/g, (_, c: string) => (c === "n" || c === "N" ? "\n" : c));
}

/** Splits on separators that aren't backslash-escaped. */
function splitUnescaped(value: string, separator: string): string[] {
  const parts: string[] = [];
  let current = "";
  for (let i = 0; i < value.length; i++) {
    if (value[i] === "\\" && i + 1 < value.length) {
      current += value[i] + value[i + 1];
      i++;
    } else if (value[i] === separator) {
      parts.push(current);
      current = "";
    } else {
      current += value[i];
    }
  }
  parts.push(current);
  return parts.map(unescape);
}

function parseLine(line: string): Property | null {
  // Find the ":" that ends the name/params, ignoring any inside quoted params.
  let colon = -1;
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    if (line[i] === '"') quoted = !quoted;
    else if (line[i] === ":" && !quoted) {
      colon = i;
      break;
    }
  }
  if (colon === -1) return null;
  const [head, ...paramParts] = line.slice(0, colon).split(";");
  const dot = head.lastIndexOf(".");
  const params: Record<string, string[]> = {};
  for (const part of paramParts) {
    const eq = part.indexOf("=");
    // vCard 2.1 allows bare types like `TEL;CELL:`.
    const key = (eq === -1 ? "TYPE" : part.slice(0, eq)).toUpperCase();
    const values = (eq === -1 ? part : part.slice(eq + 1)).replace(/"/g, "").split(",");
    params[key] = [...(params[key] ?? []), ...values.map((v) => v.toLowerCase())];
  }
  return {
    group: dot === -1 ? undefined : head.slice(0, dot).toLowerCase(),
    name: head.slice(dot + 1).toUpperCase(),
    params,
    value: line.slice(colon + 1),
  };
}

function* cards(text: string): Generator<Property[]> {
  const lines = text.replace(/\r\n?/g, "\n").split("\n");
  let props: Property[] | null = null;
  for (let i = 0; i < lines.length; i++) {
    let line = lines[i];
    // Folded continuation lines start with a space or tab.
    while (i + 1 < lines.length && /^[ \t]/.test(lines[i + 1])) line += lines[++i].slice(1);
    // Quoted-printable soft line breaks end with "=".
    if (/ENCODING=QUOTED-PRINTABLE/i.test(line.split(":")[0])) {
      while (line.endsWith("=") && i + 1 < lines.length) line = line.slice(0, -1) + lines[++i];
    }
    const prop = parseLine(line);
    if (!prop) continue;
    if (prop.name === "BEGIN" && prop.value.toUpperCase() === "VCARD") props = [];
    else if (prop.name === "END" && prop.value.toUpperCase() === "VCARD") {
      if (props) yield props;
      props = null;
    } else if (props) {
      if (prop.params.ENCODING?.includes("quoted-printable")) {
        prop.value = decodeQuotedPrintable(prop.value, prop.params.CHARSET?.[0]);
      }
      props.push(prop);
    }
  }
}

/** Accepts `1991-10-19`, `19911019`, `--10-19`, `--1019`; iOS uses year 1604 for "no year". */
export function parseVCardDate(value: string, omitYear = false): string | undefined {
  const v = value.trim().replace(/T.*$/, "");
  let m = /^(\d{4})-?(\d{2})-?(\d{2})$/.exec(v);
  if (m) return omitYear || m[1] === "1604" ? `${m[2]}-${m[3]}` : `${m[1]}-${m[2]}-${m[3]}`;
  m = /^--(\d{2})-?(\d{2})$/.exec(v);
  if (m) return `${m[1]}-${m[2]}`;
  return undefined;
}

/** iOS labels look like `_$!<Spouse>!$_`. */
function cleanLabel(label: string): string {
  return label.replace(/^_\$!<(.*)>!\$_$/, "$1").trim().toLowerCase();
}

const IGNORED_CATEGORIES = new Set(["mycontacts", "starred", "imported", "card"]);

function addContact(contact: Record<string, string>, key: string, value: string) {
  if (!value || Object.values(contact).includes(value)) return;
  let k = key;
  for (let n = 2; k in contact; n++) k = `${key}_${n}`;
  contact[k] = value;
}

function toCandidate(props: Property[]): Candidate | null {
  const get = (name: string) => props.find((p) => p.name === name);
  const labels = new Map<string, string>();
  for (const p of props) if (p.name === "X-ABLABEL" && p.group) labels.set(p.group, cleanLabel(unescape(p.value)));

  const fn = get("FN") ? unescape(get("FN")!.value).trim() : "";
  const n = get("N") ? splitUnescaped(get("N")!.value, ";") : [];
  const name = fn || [n[3], n[1], n[2], n[0], n[4]].filter((s) => s?.trim()).join(" ").trim();
  if (!name) return null;

  const c = candidate(name, "contacts");
  const nickname = get("NICKNAME");
  if (nickname) c.nickname = unescape(nickname.value).split(",")[0].trim() || undefined;

  const bday = get("BDAY");
  if (bday) c.birthday = parseVCardDate(bday.value, "X-APPLE-OMIT-YEAR" in bday.params);

  const notes: string[] = [];
  for (const p of props) {
    const types = p.params.TYPE ?? [];
    const label = p.group ? labels.get(p.group) : undefined;
    switch (p.name) {
      case "TEL": {
        const value = unescape(p.value).replace(/^tel:/i, "").trim();
        const kind = label ?? types.find((t) => ["home", "work"].includes(t));
        addContact(c.contact, kind === "home" || kind === "work" ? `phone_${kind}` : "phone", value);
        break;
      }
      case "EMAIL": {
        const kind = label ?? types.find((t) => t === "work");
        addContact(c.contact, kind === "work" ? "email_work" : "email", unescape(p.value).trim());
        break;
      }
      case "ADR": {
        if (c.location) break;
        const [, , , city, region, , country] = splitUnescaped(p.value, ";").map((s) => s.trim());
        const place = [city, country || region].filter(Boolean).join(", ");
        if (place) c.location = place;
        break;
      }
      case "X-SOCIALPROFILE": {
        const service = types.find((t) => t !== "pref") ?? "social";
        const handle = p.params["X-USER"]?.[0];
        const value = unescape(p.value).replace(/^x-apple:/i, "").trim();
        addContact(c.contact, service, /^https?:\/\//.test(value) ? value : handle ?? value);
        break;
      }
      case "URL":
        addContact(c.contact, "website", unescape(p.value).trim());
        break;
      case "NOTE":
        notes.push(unescape(p.value).trim());
        break;
      case "ORG": {
        const org = splitUnescaped(p.value, ";").filter(Boolean).join(", ");
        if (org) notes.unshift(`Works at ${org.replace(/\.$/, "")}.`);
        break;
      }
      case "CATEGORIES":
        for (const cat of splitUnescaped(p.value, ",")) {
          const tag = cat.trim().toLowerCase().replace(/\s+/g, "-");
          if (tag && !IGNORED_CATEGORIES.has(tag) && !c.tags.includes(tag)) c.tags.push(tag);
        }
        break;
      case "X-ABRELATEDNAMES":
      case "RELATED": {
        const relName = unescape(p.value).replace(/^text:/i, "").trim();
        if (relName && !/^(urn|https?):/i.test(relName)) {
          c.family.push({ name: relName, relation: label ?? types[0] });
        }
        break;
      }
    }
  }
  const note = notes.filter(Boolean).join("\n\n");
  if (note) c.notes = note;
  return c;
}

export function parseVCards(text: string): ParseOutcome {
  const candidates: Candidate[] = [];
  let skipped = 0;
  for (const props of cards(text)) {
    const c = toCandidate(props);
    if (c) candidates.push(c);
    else skipped++;
  }
  return {
    candidates,
    notes: skipped > 0 ? [`Skipped ${skipped} contact${skipped > 1 ? "s" : ""} without a name.`] : [],
  };
}
