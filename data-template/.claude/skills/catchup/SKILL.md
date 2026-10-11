---
name: catchup
description: Log a catch-up with a friend or family member, add someone new, or get ready for a call, by editing the Markdown files in people/. Use whenever the user mentions talking to, meeting, or planning to see someone, or asks who to reach out to.
---

Follow [AGENTS.md](../../../AGENTS.md) for the file format.

1. Find the person in `people/` by name, nickname or family member. If none or several
   match, ask before creating or editing anything.
2. Make the smallest edit that captures what the user said:
   - Conversation → new `### YYYY-MM-DD · <type>` entry at the top of `## Log`, with bullet
     notes and `- [ ]` items for things to ask next time. Use today's date unless told otherwise.
   - Covered follow-ups → change `- [ ]` to `- [x]`.
   - Planned meeting → add `{ when, what, where, remind }` to `plans` (sorted by date).
   - New facts (birthday, kids, new city) → frontmatter fields, not the log.
3. Show the user a one-line summary of what changed, then commit.

When asked who to reach out to, rank people by: birthday within a week, then how far past
their `cadence` (or `next_contact`) they are — weighting `priority: high` people up — then
open follow-ups. Give one line of context per person.

**"Plan my week"**: ask when they're free (e.g. "Tue evening, Sat afternoon") and how many
catch-ups they want, then propose who goes in which slot (birthdays on or near the day;
people they last *called* get a call, people they last *met* a meetup). Only after they
agree, add the `plans` entries.
