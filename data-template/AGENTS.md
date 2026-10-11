# CatchUp data — instructions for AI assistants

This repository holds the user's personal notes about friends and family. Treat it as
private: never copy its contents anywhere else, and never invent facts about people.

## Layout

- `people/<first-last>.md` — one file per person: YAML frontmatter + Markdown body.
  Start new people from `people/_template.md`, keeping only the fields you know.
- The CatchUp app reads and writes these same files, so keep to the format below exactly.

## Frontmatter

Only `name` is required.

| Field | Format |
| --- | --- |
| `name`, `nickname`, `relationship`, `location`, `how_we_met` | text |
| `tags` | list, lowercase-hyphenated: `[college, book-club]` |
| `birthday` | `YYYY-MM-DD`, or `MM-DD` if the year is unknown |
| `contact` | map of any keys; `phone`, `email`, `whatsapp`, `instagram`… **Quote phone numbers.** |
| `family` | list of `{ name, relation, birthday, notes }` |
| `cadence` | how often to keep in touch: `weekly`, `biweekly`, `monthly`, `bimonthly`, `quarterly`, `biannually`, `yearly`, or `6w` / `45d` / `2m` |
| `last_contacted` | `YYYY-MM-DD` (the newest `## Log` date wins automatically) |
| `next_catchup` | planned catch-up: `YYYY-MM-DD` or `YYYY-MM-DD HH:MM` |

## Body

Free-form notes, then a `## Log` section, newest first:

```markdown
## Log

### 2026-10-11 · Video call
- Moving to Denver in the spring.
- [ ] Ask how the house hunt is going
```

- One `### YYYY-MM-DD · <type>` heading per conversation (`Call`, `Video call`, `In person`, `Messages`, …).
- `- [ ] …` is something to bring up next time; mark it `- [x]` once it's been covered.

## Common tasks

- **"I had coffee with Maya, she's moving to Denver"** → add a log entry dated today at the
  top of Maya's `## Log` (create the section at the end of the file if needed). If a
  `next_catchup` on or before that date exists, remove it. Tick off any follow-ups the user
  says they covered.
- **"Add Sam, met at the climbing gym"** → create `people/sam-<last>.md` (ask for the last name
  if unknown; don't guess). Put only what you were told.
- **"Who should I call this week?" / "Prep me for my call with Jonas"** → read the files:
  look at `cadence` vs. last contact, upcoming birthdays (including `family`), open `- [ ]`
  follow-ups and recent log entries.
- Ambiguous person ("Sam" matches two files)? Ask.

Keep the user's wording; append rather than rewrite. Commit with a short message such as
`Log call with maya-patel (2026-10-11)`.
