// Checks every file in people/ against the format in docs/data-format.md.
// Usage: npm run validate        (exits non-zero if any file has errors)

import { readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";
import { parsePerson } from "../src/lib/person";

const dir = join(import.meta.dirname, "..", "people");
const files = readdirSync(dir).filter(
  (f) => f.endsWith(".md") && !f.startsWith("_") && f !== "README.md",
);

let errors = 0;
let warnings = 0;
for (const file of files) {
  const id = file.replace(/\.md$/, "");
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(id)) {
    console.log(`people/${file}\n  warning: file names should be lowercase-hyphenated, e.g. ada-lovelace.md`);
    warnings++;
  }
  const { issues } = parsePerson(id, readFileSync(join(dir, file), "utf8"));
  if (issues.length === 0) continue;
  console.log(`people/${file}`);
  for (const issue of issues) {
    console.log(`  ${issue.level}: ${issue.message}`);
    if (issue.level === "error") errors++;
    else warnings++;
  }
}

console.log(`\n${files.length} people checked: ${errors} error(s), ${warnings} warning(s).`);
process.exit(errors > 0 ? 1 : 0);
