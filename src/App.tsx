import { useEffect, useMemo, useState } from "react";
import { PersonCard } from "./components/PersonCard";
import { PersonDetail } from "./components/PersonDetail";
import { loadPeople } from "./lib/people";
import type { Person } from "./lib/person";
import { type PersonStatus, personStatus } from "./lib/status";

type SortKey = "name" | "birthday" | "contact";

const { people, problems } = loadPeople();

function useHashRoute(): string {
  const [hash, setHash] = useState(() => window.location.hash);
  useEffect(() => {
    const onChange = () => {
      setHash(window.location.hash);
      window.scrollTo(0, 0);
    };
    window.addEventListener("hashchange", onChange);
    return () => window.removeEventListener("hashchange", onChange);
  }, []);
  return hash;
}

function matches(person: Person, query: string): boolean {
  if (!query) return true;
  const haystack = [
    person.name,
    person.nickname,
    person.relationship,
    person.location,
    ...person.tags,
    ...person.family.map((f) => f.name),
  ]
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
  if (sort === "birthday") {
    return (sa.daysUntilBirthday ?? Infinity) - (sb.daysUntilBirthday ?? Infinity);
  }
  if (sort === "contact") {
    // Longest since last contact first; never-contacted people at the very top.
    return (sb.daysSinceContact ?? Infinity) - (sa.daysSinceContact ?? Infinity);
  }
  return pa.name.localeCompare(pb.name);
}

export function App() {
  const hash = useHashRoute();
  const [query, setQuery] = useState("");
  const [tag, setTag] = useState<string | null>(null);
  const [sort, setSort] = useState<SortKey>("name");
  const today = useMemo(() => new Date(), []);

  const statuses = useMemo(
    () => new Map(people.map((p) => [p.id, personStatus(p, today)])),
    [today],
  );

  const allTags = useMemo(() => {
    const set = new Set<string>();
    for (const p of people) {
      if (p.relationship) set.add(p.relationship);
      p.tags.forEach((t) => set.add(t));
    }
    return [...set].sort((a, b) => a.localeCompare(b));
  }, []);

  const visible = useMemo(
    () =>
      people
        .filter((p) => matches(p, query))
        .filter((p) => !tag || p.relationship === tag || p.tags.includes(tag))
        .map((p) => [p, statuses.get(p.id)!] as [Person, PersonStatus])
        .sort((a, b) => compare(sort, a, b)),
    [query, tag, sort, statuses],
  );

  const routeId = /^#\/people\/(.+)$/.exec(hash)?.[1];
  const selected = routeId && people.find((p) => p.id === decodeURIComponent(routeId));

  return (
    <div className="app">
      <header className="topbar">
        <a href="#/" className="brand">
          CatchUp
        </a>
        <span className="muted small">{people.length} people</span>
      </header>

      {problems.length > 0 && (
        <details className="problems">
          <summary>
            {problems.length} file{problems.length > 1 ? "s" : ""} need attention
          </summary>
          <ul>
            {problems.map(({ file, issues }) => (
              <li key={file}>
                <code>{file}</code>
                <ul>
                  {issues.map((issue, i) => (
                    <li key={i} className={issue.level}>
                      {issue.message}
                    </li>
                  ))}
                </ul>
              </li>
            ))}
          </ul>
        </details>
      )}

      {selected ? (
        <PersonDetail person={selected} status={statuses.get(selected.id)!} today={today} />
      ) : (
        <main>
          <div className="toolbar">
            <input
              type="search"
              placeholder="Search names, places, tags, family…"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              aria-label="Search people"
            />
            <select
              value={sort}
              onChange={(e) => setSort(e.target.value as SortKey)}
              aria-label="Sort by"
            >
              <option value="name">Name</option>
              <option value="birthday">Upcoming birthday</option>
              <option value="contact">Longest since contact</option>
            </select>
          </div>

          {allTags.length > 0 && (
            <div className="chips" role="group" aria-label="Filter by tag">
              <button className={tag === null ? "active" : ""} onClick={() => setTag(null)}>
                All
              </button>
              {allTags.map((t) => (
                <button
                  key={t}
                  className={tag === t ? "active" : ""}
                  onClick={() => setTag(tag === t ? null : t)}
                >
                  {t}
                </button>
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
            <p className="empty muted">
              {people.length === 0
                ? "No one here yet. Add a Markdown file to the people/ folder to get started."
                : "No one matches that search."}
            </p>
          )}
        </main>
      )}
    </div>
  );
}
