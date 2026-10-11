# Working on CatchUp

CatchUp helps someone keep in touch with friends and family. The data is plain Markdown,
so agents can read and update it directly.

## Data (most common agent task)

- One person = one file: `people/<first-last>.md`. Format: [docs/data-format.md](docs/data-format.md).
  Start new people from [`people/_template.md`](people/_template.md), keeping only the fields you have.
- To **log a conversation**, add a `### YYYY-MM-DD · <type>` entry at the *top* of the person's
  `## Log` section (create the section at the end of the file if missing). Don't edit
  `last_contacted` — the log date takes precedence automatically.
- Never invent facts. If something is unclear (which "Sam"?), ask.
- Preserve the user's wording in notes; append rather than rewrite.
- Dates are always `YYYY-MM-DD` (birthdays may be `MM-DD`). Quote phone numbers.
- Run `npm run validate` after editing data.

## Code

- Vite + React + TypeScript. Files are loaded at build time via `import.meta.glob`
  in `src/lib/people.ts`; `npm run dev` hot-reloads when a file in `people/` changes.
- Parsing and validation live in `src/lib/person.ts` (shared by the app and
  `scripts/validate.ts`). If you change the format, update `docs/data-format.md` and
  `people/_template.md` too.
- Checks: `npm run typecheck`, `npm test`, `npm run validate`, `npm run build`.
