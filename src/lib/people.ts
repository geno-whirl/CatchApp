// Loads every Markdown file in /people at build time. In `npm run dev`, editing a
// file (by hand or by an agent) hot-reloads the app.

import { type Issue, type Person, parsePerson } from "./person";

const files = import.meta.glob(["/people/*.md", "!/people/_*.md", "!/people/README.md"], {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

export interface FileIssues {
  file: string;
  issues: Issue[];
}

export function loadPeople(): { people: Person[]; problems: FileIssues[] } {
  const people: Person[] = [];
  const problems: FileIssues[] = [];
  for (const [path, raw] of Object.entries(files)) {
    const file = path.replace(/^\//, "");
    const id = file.replace(/^people\//, "").replace(/\.md$/, "");
    const { person, issues } = parsePerson(id, raw);
    if (person) people.push(person);
    if (issues.length > 0) problems.push({ file, issues });
  }
  people.sort((a, b) => a.name.localeCompare(b.name));
  return { people, problems };
}
