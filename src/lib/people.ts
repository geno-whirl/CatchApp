import { type Files, idFromPath, PEOPLE_DIR } from "./ops";
import { type Issue, type Person, parsePerson } from "./person";

export interface FileIssues {
  file: string;
  issues: Issue[];
}

/** People files are `people/*.md`, except `_template.md`-style and README files. */
export function isPersonFile(path: string): boolean {
  const name = path.slice(PEOPLE_DIR.length + 1);
  return path.startsWith(`${PEOPLE_DIR}/`) && /^[^/_][^/]*\.md$/.test(name) && name.toLowerCase() !== "readme.md";
}

export function parseFiles(files: Files): { people: Person[]; problems: FileIssues[] } {
  const people: Person[] = [];
  const problems: FileIssues[] = [];
  for (const [path, raw] of Object.entries(files)) {
    if (!isPersonFile(path)) continue;
    const { person, issues } = parsePerson(idFromPath(path), raw);
    if (person) people.push(person);
    if (issues.length > 0) problems.push({ file: path, issues });
  }
  people.sort((a, b) => a.name.localeCompare(b.name));
  problems.sort((a, b) => a.file.localeCompare(b.file));
  return { people, problems };
}
