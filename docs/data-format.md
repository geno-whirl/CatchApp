# CatchUp data format

Every person is one Markdown file in a `people/` folder. The file name (without `.md`)
is the person's id, lowercase and hyphenated: `people/maya-patel.md` → `maya-patel`.

Your `people/` folder lives **outside this app's repository**: in a private GitHub repo
(start from [`data-template/`](../data-template)) or on your phone. Fictional examples are in
[`examples/people/`](../examples/people).

A file has two parts:

1. **YAML frontmatter** between `---` lines: structured facts the app reads.
2. **Markdown body**: free-form notes, plus an optional `## Log` of conversations.

Files starting with `_` (like [`_template.md`](../data-template/people/_template.md)) are ignored.

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
| `next_catchup` | date/time | `2026-10-20 18:30` | A planned catch-up: `YYYY-MM-DD` or `YYYY-MM-DD HH:MM`. Removed automatically when you log a catch-up on or after that day. |

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

### Follow-ups

Any task-list item in the body is a follow-up — something to bring up next time:

```markdown
- [ ] Ask how the house hunt is going
- [x] Send her the photos
```

The app shows open items on the person's page and on Today, lets you tick them off while
logging, and rewrites `[ ]` to `[x]`.

## Checking files

Run `npm run validate -- path/to/your/data-repo` to check every file. Errors (missing name, malformed dates,
unknown cadence) fail the check; warnings (unknown fields, unquoted phone numbers) don't.
The app lists problems under Settings.
