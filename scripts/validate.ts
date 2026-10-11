// Checks every person file against docs/data-format.md.
// Usage: npm run validate -- [path]   path = a data repo (containing people/) or a people/ folder.
// Defaults to examples/. Exits non-zero if any file has errors.

import { existsSync, readdirSync, readFileSync } from "node:fs";
import { basename, join, resolve } from "node:path";
import { parsePerson } from "../src/lib/person";

const arg = resolve(process.argv[2] ?? join(import.meta.dirname, "..", "examples"));
const dir = basename(arg) === "people" || !existsSync(join(arg, "people")) ? arg : join(arg, "people");
const files = readdirSync(dir).filter((f) => f.endsWith(".md") && !f.startsWith("_") && f.toLowerCase() !== "readme.md");

let errors = 0;
let warnings = 0;
for (const file of files) {
  const id = file.replace(/\.md$/, "");
  const lines: string[] = [];
  if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(id)) {
    lines.push("  warning: file names should be lowercase-hyphenated, e.g. ada-lovelace.md");
    warnings++;
  }
  for (const issue of parsePerson(id, readFileSync(join(dir, file), "utf8")).issues) {
    lines.push(`  ${issue.level}: ${issue.message}`);
    if (issue.level === "error") errors++;
    else warnings++;
  }
  if (lines.length > 0) console.log(`${join(dir, file)}\n${lines.join("\n")}`);
}

console.log(`\n${files.length} people checked in ${dir}: ${errors} error(s), ${warnings} warning(s).`);
process.exit(errors > 0 ? 1 : 0);
