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
| `priority` | text | `high` | How important keeping in touch is: `high`, `medium` (default) or `low`. Ranks suggestions; with no `cadence`, it implies one (high → monthly, medium → quarterly, low → yearly). |
| `tags` | list | `[college, climbing]` | Lowercase, hyphenated. Used for filtering and search. |
| `birthday` | date | `1991-10-19` or `10-19` | `YYYY-MM-DD`, or `MM-DD` when the year is unknown. |
| `location` | text | `Seattle, USA` | |
| `contact` | map | see below | Any keys. `phone`, `email` and URLs become links. **Quote phone numbers.** |
| `family` | list | see below | Partner, kids, parents, pets… |
| `how_we_met` | text | `Freshman-year roommates.` | |
| `cadence` | text | `monthly` | The suggested rhythm for keeping in touch. `weekly`, `biweekly`, `monthly`, `bimonthly`, `quarterly`, `biannually`, `yearly`, or `<n>d` / `<n>w` / `<n>m` / `<n>y` (e.g. `6w`). |
| `next_contact` | date | `2026-11-01` | Optional: a specific date to get in touch next, instead of last contact + cadence. Cleared automatically when a logged interaction satisfies it. |
| `last_contacted` | date | `2026-07-20` | `YYYY-MM-DD`. Optional when there is a `## Log`; the newer of the two is used. |
| `plans` | list | see below | Scheduled catch-ups, near or far ahead, each with an optional reminder. A plan is removed when it's logged. |

`next_catchup: <date>` (a single plan) from older files is still read, and becomes a
`plans` entry the next time the app edits the file.

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
plans:
  - when: 2026-10-20 18:30   # YYYY-MM-DD, or YYYY-MM-DD HH:MM
    what: Dinner             # Call, Video call, Coffee, Dinner… (optional)
    where: Luigi's           # optional
    remind: 2h               # 30m, 2h, 1d or none; default depends on the kind
    notes: Her treat this time
```

## Body

Anything goes: interests, gift ideas, things to remember, topics to follow up on.

### The log

Interactions go under a `## Log` heading, **newest first**, one `###` heading per
call, meetup or conversation:

```markdown
## Log

### 2026-10-20 21:00 · Dinner · Luigi's
- Moving to Denver in the spring.
- [ ] Ask how the house hunt is going

### 2026-08-30 · Video call
- Leela started preschool.
```

The heading is `### YYYY-MM-DD[ HH:MM] · Kind[ · Place]` — the time and place are
optional. Bullet lines are notes; `- [ ]` lines are follow-ups. The most recent date
becomes "last contact". Separators `·`, `-`, `—`, `:` and `|` all work after the date.

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
