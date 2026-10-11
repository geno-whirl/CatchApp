# CatchUp data format

Every person is one Markdown file in [`people/`](../people). The file name (without `.md`)
is the person's id, lowercase and hyphenated: `people/maya-patel.md` → `maya-patel`.

A file has two parts:

1. **YAML frontmatter** between `---` lines: structured facts the app reads.
2. **Markdown body**: free-form notes, plus an optional `## Log` of conversations.

Files starting with `_` (like [`_template.md`](../people/_template.md)) are ignored.

## Frontmatter fields

Only `name` is required. Leave out fields you don't know rather than leaving them empty.

| Field | Type | Example | Notes |
| --- | --- | --- | --- |
| `name` | text | `Maya Patel` | **Required.** |
| `nickname` | text | `Mom` | Shown next to the name. |
| `relationship` | text | `friend` | Free text. Common: `family`, `friend`, `colleague`, `neighbour`. Also used as a filter. |
| `tags` | list | `[college, climbing]` | Lowercase, hyphenated. Used for filtering and search. |
| `birthday` | date | `1991-10-19` or `10-19` | `YYYY-MM-DD`, or `MM-DD` when the year is unknown. |
| `location` | text | `Seattle, USA` | |
| `contact` | map | see below | Any keys. `phone`, `email` and URLs become links. **Quote phone numbers.** |
| `family` | list | see below | Partner, kids, parents, pets… |
| `how_we_met` | text | `Freshman-year roommates.` | |
| `cadence` | text | `monthly` | How often you want to be in touch. `weekly`, `biweekly`, `monthly`, `bimonthly`, `quarterly`, `biannually`, `yearly`, or `<n>d` / `<n>w` / `<n>m` / `<n>y` (e.g. `6w`). |
| `last_contacted` | date | `2026-07-20` | `YYYY-MM-DD`. Optional when there is a `## Log`; the newer of the two is used. |

```yaml
contact:
  phone: "+1 206 555 0142"
  email: maya@example.com
  instagram: https://instagram.com/maya
family:
  - name: Arjun
    relation: partner
  - name: Leela
    relation: daughter
    birthday: 2022-04-03
    notes: Obsessed with dinosaurs.
  - Biscuit            # a bare name works too
```

## Body

Anything goes: interests, gift ideas, things to remember, topics to follow up on.

### The log

Conversations go under a `## Log` heading, **newest first**, one `###` heading per
interaction that starts with the date:

```markdown
## Log

### 2026-08-30 · Video call
- Leela started preschool.
- Follow up: ask how the new team is going.

### 2026-06-14 · Coffee
- Visiting Portland in the fall.
```

The date and the text after it (`Video call`) are read by the app; the most recent date
becomes "last contact". Separators `·`, `-`, `—`, `:` and `|` all work, or none at all.

## Checking files

Run `npm run validate` to check every file. Errors (missing name, malformed dates,
unknown cadence) fail the check; warnings (unknown fields, unquoted phone numbers) don't.
The running app also lists problems at the top of the page.
