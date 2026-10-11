import { type FormEvent, useMemo, useState } from "react";
import { Icon } from "../components/Icon";
import { idFromPath, personPath, serializePerson, slugify } from "../lib/ops";
import type { Person } from "../lib/person";
import { apply, toast, usePeople } from "../store";

const CADENCES = [
  ["", "No reminder"],
  ["weekly", "Every week"],
  ["biweekly", "Every 2 weeks"],
  ["monthly", "Every month"],
  ["bimonthly", "Every 2 months"],
  ["quarterly", "Every 3 months"],
  ["biannually", "Twice a year"],
  ["yearly", "Once a year"],
];

const pad = (n: number) => String(n).padStart(2, "0");

interface Values {
  name: string;
  nickname: string;
  relationship: string;
  tags: string;
  birthday: string; // YYYY-MM-DD for the date input
  noYear: boolean;
  location: string;
  phone: string;
  email: string;
  cadence: string;
  how_we_met: string;
  notes: string;
}

function initialValues(person?: Person): Values {
  const b = person?.birthday;
  return {
    name: person?.name ?? "",
    nickname: person?.nickname ?? "",
    relationship: person?.relationship ?? "",
    tags: person?.tags.join(", ") ?? "",
    // A leap year keeps Feb 29 valid when the year is unknown.
    birthday: b ? `${b.year ?? 2000}-${pad(b.month)}-${pad(b.day)}` : "",
    noYear: b ? b.year === undefined : false,
    location: person?.location ?? "",
    phone: person?.contact.phone ?? "",
    email: person?.contact.email ?? "",
    cadence: person?.cadence ?? "",
    how_we_met: person?.howWeMet ?? "",
    notes: "",
  };
}

function birthdayValue(v: Values): string {
  if (!v.birthday) return "";
  return v.noYear ? v.birthday.slice(5) : v.birthday;
}

const splitTags = (text: string) =>
  text
    .split(",")
    .map((t) => t.trim().toLowerCase().replace(/\s+/g, "-"))
    .filter(Boolean);

export function PersonForm({ person }: { person?: Person }) {
  const { people } = usePeople();
  const initial = useMemo(() => initialValues(person), [person]);
  const [v, setV] = useState<Values>(initial);
  const set = <K extends keyof Values>(key: K, value: Values[K]) => setV((prev) => ({ ...prev, [key]: value }));

  const relationships = useMemo(
    () => [...new Set(["family", "friend", "colleague", "neighbour", ...people.map((p) => p.relationship).filter(Boolean)])],
    [people],
  );
  const cadenceOptions = CADENCES.some(([value]) => value === v.cadence) ? CADENCES : [...CADENCES, [v.cadence, v.cadence]];

  const submit = async (e: FormEvent) => {
    e.preventDefault();
    const name = v.name.trim();
    if (!name) return;

    if (!person) {
      const [path] = await apply({
        type: "create",
        path: personPath(slugify(name)),
        content: serializePerson({
          name,
          nickname: v.nickname.trim(),
          relationship: v.relationship.trim(),
          tags: splitTags(v.tags),
          birthday: birthdayValue(v),
          location: v.location.trim(),
          contact: Object.fromEntries(Object.entries({ phone: v.phone.trim(), email: v.email.trim() }).filter(([, x]) => x)),
          how_we_met: v.how_we_met.trim(),
          cadence: v.cadence,
          notes: v.notes,
        }),
      });
      toast(`Added ${name}`);
      location.hash = `#/people/${idFromPath(path)}`;
      return;
    }

    // Only write fields that changed, so the rest of the file stays exactly as it was.
    const fields: Record<string, unknown> = {};
    const text: [keyof Values, string][] = [
      ["name", "name"],
      ["nickname", "nickname"],
      ["relationship", "relationship"],
      ["location", "location"],
      ["cadence", "cadence"],
      ["how_we_met", "how_we_met"],
    ];
    for (const [key, field] of text) if (v[key] !== initial[key]) fields[field] = String(v[key]).trim() || null;
    if (v.tags !== initial.tags) fields.tags = splitTags(v.tags).length > 0 ? splitTags(v.tags) : null;
    if (v.birthday !== initial.birthday || v.noYear !== initial.noYear) fields.birthday = birthdayValue(v) || null;
    if (v.phone !== initial.phone || v.email !== initial.email) {
      const contact = { ...person.contact, phone: v.phone.trim(), email: v.email.trim() };
      const cleaned = Object.fromEntries(Object.entries(contact).filter(([, x]) => x));
      fields.contact = Object.keys(cleaned).length > 0 ? cleaned : null;
    }
    if (Object.keys(fields).length > 0) {
      await apply({ type: "setFields", path: personPath(person.id), fields });
      toast("Saved");
    }
    location.hash = `#/people/${person.id}`;
  };

  return (
    <form className="form page-form" onSubmit={submit}>
      <a className="back" href={person ? `#/people/${person.id}` : "#/people"}>
        <Icon name="back" size={18} /> {person ? person.name : "People"}
      </a>
      <h1>{person ? "Edit details" : "Add someone"}</h1>

      <label>
        <span>Name</span>
        <input value={v.name} onChange={(e) => set("name", e.target.value)} required autoFocus={!person} autoComplete="off" />
      </label>
      <div className="two-col">
        <label>
          <span>Nickname</span>
          <input value={v.nickname} onChange={(e) => set("nickname", e.target.value)} autoComplete="off" />
        </label>
        <label>
          <span>Relationship</span>
          <input value={v.relationship} onChange={(e) => set("relationship", e.target.value)} list="relationships" autoComplete="off" />
          <datalist id="relationships">
            {relationships.map((r) => (
              <option key={r} value={r} />
            ))}
          </datalist>
        </label>
      </div>

      <label>
        <span>Keep in touch</span>
        <select value={v.cadence} onChange={(e) => set("cadence", e.target.value)}>
          {cadenceOptions.map(([value, text]) => (
            <option key={value} value={value}>{text}</option>
          ))}
        </select>
      </label>

      <div className="two-col">
        <label>
          <span>Birthday</span>
          <input type="date" value={v.birthday} onChange={(e) => set("birthday", e.target.value)} />
        </label>
        <label className="check inline">
          <input type="checkbox" checked={v.noYear} onChange={(e) => set("noYear", e.target.checked)} />
          Don't know the year
        </label>
      </div>

      <div className="two-col">
        <label>
          <span>Phone</span>
          <input type="tel" value={v.phone} onChange={(e) => set("phone", e.target.value)} placeholder="+1 555 123 4567" autoComplete="off" />
        </label>
        <label>
          <span>Email</span>
          <input type="email" value={v.email} onChange={(e) => set("email", e.target.value)} autoComplete="off" />
        </label>
      </div>

      <label>
        <span>Location</span>
        <input value={v.location} onChange={(e) => set("location", e.target.value)} placeholder="City, Country" />
      </label>
      <label>
        <span>Tags</span>
        <input value={v.tags} onChange={(e) => set("tags", e.target.value)} placeholder="college, hiking" />
      </label>
      <label>
        <span>How you met</span>
        <input value={v.how_we_met} onChange={(e) => set("how_we_met", e.target.value)} />
      </label>
      {!person && (
        <label>
          <span>Notes</span>
          <textarea rows={3} value={v.notes} onChange={(e) => set("notes", e.target.value)} placeholder="Interests, kids' names, gift ideas…" />
        </label>
      )}

      <button className="button primary" type="submit">
        {person ? "Save" : "Add"}
      </button>
      {person && (
        <p className="muted small">
          Family, notes and anything else: <a href={`#/people/${person.id}/markdown`}>edit as Markdown</a>.
        </p>
      )}
    </form>
  );
}
