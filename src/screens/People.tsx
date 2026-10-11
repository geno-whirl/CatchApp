import { useMemo, useState } from "react";
import { PersonCard } from "../components/PersonCard";
import type { Person } from "../lib/person";
import { type PersonStatus, personStatus } from "../lib/status";
import { usePeople, useToday } from "../store";

type SortKey = "name" | "birthday" | "contact";

function matches(person: Person, query: string): boolean {
  if (!query) return true;
  const haystack = [person.name, person.nickname, person.relationship, person.location, ...person.tags, ...person.family.map((f) => f.name)]
    .filter(Boolean)
    .join(" ")
    .toLowerCase();
  return query
    .toLowerCase()
    .split(/\s+/)
    .every((word) => haystack.includes(word));
}

function compare(sort: SortKey, a: [Person, PersonStatus], b: [Person, PersonStatus]): number {
  const [pa, sa] = a;
  const [pb, sb] = b;
  if (sort === "birthday") return (sa.daysUntilBirthday ?? Infinity) - (sb.daysUntilBirthday ?? Infinity);
  // Longest since last contact first; never-contacted people at the very top.
  if (sort === "contact") return (sb.daysSinceContact ?? Infinity) - (sa.daysSinceContact ?? Infinity);
  return pa.name.localeCompare(pb.name);
}

export function People() {
  const { people } = usePeople();
  const today = useToday();
  const [query, setQuery] = useState("");
  const [tag, setTag] = useState<string | null>(null);
  const [sort, setSort] = useState<SortKey>("name");

  const statuses = useMemo(() => new Map(people.map((p) => [p.id, personStatus(p, today)])), [people, today]);

  const allTags = useMemo(() => {
    const set = new Set<string>();
    for (const p of people) {
      if (p.relationship) set.add(p.relationship);
      p.tags.forEach((t) => set.add(t));
    }
    return [...set].sort((a, b) => a.localeCompare(b));
  }, [people]);

  const visible = useMemo(
    () =>
      people
        .filter((p) => matches(p, query))
        .filter((p) => !tag || p.relationship === tag || p.tags.includes(tag))
        .map((p) => [p, statuses.get(p.id)!] as [Person, PersonStatus])
        .sort((a, b) => compare(sort, a, b)),
    [people, query, tag, sort, statuses],
  );

  return (
    <div>
      <header className="page-head">
        <h1>People</h1>
        <span className="muted small">{people.length}</span>
      </header>
      <div className="toolbar">
        <input type="search" placeholder="Search names, places, tags, family…" value={query} onChange={(e) => setQuery(e.target.value)} aria-label="Search people" />
        <select value={sort} onChange={(e) => setSort(e.target.value as SortKey)} aria-label="Sort by">
          <option value="name">Name</option>
          <option value="birthday">Upcoming birthday</option>
          <option value="contact">Longest since contact</option>
        </select>
      </div>

      {allTags.length > 0 && (
        <div className="chips scroll" role="group" aria-label="Filter by tag">
          <button className={tag === null ? "active" : ""} onClick={() => setTag(null)}>All</button>
          {allTags.map((t) => (
            <button key={t} className={tag === t ? "active" : ""} onClick={() => setTag(tag === t ? null : t)}>{t}</button>
          ))}
        </div>
      )}

      {visible.length > 0 ? (
        <div className="grid">
          {visible.map(([person, status]) => (
            <PersonCard key={person.id} person={person} status={status} />
          ))}
        </div>
      ) : (
        <div className="empty muted">
          {people.length === 0 ? (
            <>
              <p>No one here yet.</p>
              <div className="button-row center">
                <a className="button primary" href="#/new">Add someone</a>
                <a className="button" href="#/import">Import</a>
              </div>
            </>
          ) : (
            "No one matches that search."
          )}
        </div>
      )}
    </div>
  );
}
