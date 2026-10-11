# CatchUp

A simple, no-fuss app for keeping in touch with friends and family.

![CatchUp on a phone: Today, a person, and logging a catch-up](docs/screenshot.png)

- **Today** — who to reach out to and why: birthdays (theirs and their family's), people
  you're overdue to talk to, things you promised to ask about. Call or WhatsApp in one tap.
- **Log a catch-up in seconds** — type of call, a few lines, and what to ask next time.
- **Plan** the next catch-up and add it to your calendar (.ics or Google Calendar).
- **Directory** — search, filter by tag, sort by birthday or by longest since you talked.
- **Import** from phone contacts (.vcf, or directly on Android), WhatsApp chat exports,
  and Facebook / Instagram data downloads.
- **Your data stays yours** — plain Markdown files in your own private GitHub repository
  (or just on your phone). You, your editor, or an AI assistant can read and edit them.
- **Installable and offline** — add it to your home screen; changes sync when you're back online.

## Use it on your phone

1. Open the app (see *Hosting* below), then **iPhone:** Safari → Share → *Add to Home
   Screen*; **Android:** Chrome ⋮ → *Install app*.
2. Choose where your people live:
   - **Private GitHub repo (recommended):** create a private repo (e.g. `catchup-data`)
     with a README, copy in the files from [`data-template/`](data-template), then create a
     [fine-grained token](https://github.com/settings/personal-access-tokens/new) with
     *Contents: Read and write* on just that repo. The token never leaves your phone.
   - **Just this phone:** no account; download backups from Settings and move to GitHub later.
3. Import people, or add them one by one. Set *Keep in touch* (e.g. monthly) for the people
   you want reminders about.

## Your data

One file per person, `people/maya-patel.md`:

```markdown
---
name: Maya Patel
relationship: friend
birthday: 1991-10-19
cadence: monthly
contact:
  phone: "+1 206 555 0142"
family:
  - name: Leela
    relation: daughter
    birthday: 2022-04-03
next_catchup: 2026-10-20 18:30
---

Loves anything matcha.

## Log

### 2026-10-11 · Video call
- Moving to Denver in the spring.
- [ ] Ask how the house hunt is going
```

The full format is in [docs/data-format.md](docs/data-format.md). The data repo template
includes an [AGENTS.md](data-template/AGENTS.md) and a Claude Code skill, so you can say
*“had coffee with Maya, she's moving to Denver — remind me to ask about the house hunt”*
and an assistant updates the right file in the same format the app uses.

Edits from the app, from agents and from other devices are merged: every change is saved as
a small operation re-applied on top of the latest version of the file, one commit per sync.

## Hosting

The app is a static site with no server and no personal data, so it can be hosted for free
on GitHub Pages: in this repo's **Settings → Pages**, set *Source* to **GitHub Actions**.
The [CI workflow](.github/workflows/ci.yml) tests and deploys the default branch.

## Development

```bash
npm install
npm run dev                 # app with hot reload
npm test                    # unit tests
npm run validate -- ../catchup-data   # check a data repo's files
npm run build               # typecheck + production build (with service worker)
```

See [AGENTS.md](AGENTS.md) for how the code is organised.
