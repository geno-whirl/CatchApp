# Working on CatchUp

CatchUp helps someone keep in touch with friends and family. It's an installable mobile
web app (PWA). People are plain Markdown files kept **outside this repo** — in the user's
private data repository (seeded from `data-template/`) or on their phone.

## Layout

- `src/lib/` — framework-free logic, all unit-tested:
  - `person.ts` parses a file (format: [docs/data-format.md](docs/data-format.md)).
  - `ops.ts` — every edit is a serialisable op (`addLog`, `setFields`, `patch`, …) applied to
    file text. Ops are queued offline and re-applied on the latest version when syncing, so
    edits from the app, agents and other devices don't clobber each other. Frontmatter edits
    go through `yaml`'s Document API to keep comments and formatting.
  - `storage/` — `SyncEngine` (local cache + outbox in IndexedDB), `DeviceBackend`, and
    `GitHubBackend` (Git Data API, one commit per sync, retries on concurrent pushes).
  - `priorities.ts` (Today screen), `calendar.ts` (.ics, Google Calendar links),
    `import/` (vCard, Contact Picker, WhatsApp, Facebook, Instagram + matching).
- `src/screens/`, `src/components/` — React UI. Hash routes: `#/`, `#/people`,
  `#/people/:id[/edit|/markdown]`, `#/new`, `#/import`, `#/settings`.
- `examples/people/` — fictional people for tests and "Add example people".
- `data-template/` — starting point for a user's private data repo (README, AGENTS.md, a
  Claude Code skill, `people/_template.md`).

## Rules

- If you change the file format, update `src/lib/person.ts`, `docs/data-format.md`,
  `data-template/AGENTS.md` and `data-template/people/_template.md` together.
- Importers must never overwrite existing data (use `patch`), and must not keep message text.
- Never put real personal data in this repository.
- Checks: `npm run typecheck`, `npm test`, `npm run validate`, `npm run build`.
