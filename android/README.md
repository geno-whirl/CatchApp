# CatchUp for Android

A native Android app (Kotlin, Jetpack Compose, Material 3) for keeping in touch with the
people who matter.

- **Today** — who to reach out to and why (birthdays, overdue, follow-ups), what's coming
  up, and "How did it go?" after each plan.
- **Weekly planning** — every week (Sunday evening by default) a notification asks when
  you're free. Then plan on a board: your free times on top, everyone else below, ordered
  by suggestion, keep-in-touch date, priority or cadence. Hold and drag people onto a time
  (or tap a person, then a time); several people can share one. "Suggest" fills the gaps.
- **Invitations in their language** — set someone's language and invites are written in it
  (English, Spanish, French, German, Italian, Portuguese, Dutch).
- **Plans with reminders** — schedule a catch-up tomorrow or months ahead; get a reminder
  before and a nudge to note what you talked about after.
- **Interactions** — a timeline per person: date, time, kind, place, notes and things to
  ask next time.
- **Keep in touch** — importance (high/medium/low), a suggested rhythm, and an optional
  date for the next contact.
- **Add people** one at a time — from scratch or picked from the phone's contacts — or
  **import** many from the phone's contacts, .vcf files, WhatsApp chat exports, and
  Facebook / Instagram data downloads.
- **Backups** — export everyone as a .zip of Markdown files, and restore from one (or from
  a download of the data repository). Restoring adds missing people and only replaces
  existing ones if you choose to.
- **Your data** — plain Markdown files (format: [../docs/data-format.md](../docs/data-format.md)),
  kept on the phone and optionally synced with **Google Drive** or a private **GitHub** repo.
  Edits queue offline and merge with changes made elsewhere (another phone, an AI assistant).

## Install a test build

Every push to this branch builds an APK: on GitHub, open **Releases** → *CatchUp for
Android* → download `CatchUp.apk` on your phone and open it (allow installs from your
browser when asked). New builds install over old ones and keep your data.

## Build

```bash
cd android
./gradlew :app:assembleDebug              # needs the Android SDK
./gradlew :core:test                      # app logic, plain JVM
gradle -PcoreOnly :core:test              # logic only, without the Android SDK
./gradlew :app:recordRoborazziDebug       # screenshots → app/build/outputs/roborazzi
```

Modules:

- `core/` — pure Kotlin, unit-tested: Markdown/YAML parsing and writing, edit ops, sync
  engine (+ GitHub and Google Drive stores), suggestions, weekly planner, reminder
  schedule, importers.
- `app/` — the Android app: Compose UI, notifications and alarms, settings, sign-in.

## Google Drive sync: one-time setup

Google requires every app that uses Drive to be registered. Until this is done, choosing
Google Drive shows "isn't set up for this build yet"; the phone-only and GitHub options
work regardless.

1. In the [Google Cloud console](https://console.cloud.google.com/), create a project and
   enable the **Google Drive API**.
2. Configure the **OAuth consent screen** (External; add yourself as a test user while
   testing). The only scope needed is `.../auth/drive.file` (CatchUp only sees files it
   creates).
3. Create an **OAuth client ID** of type **Android** with:
   - Package name: `io.github.genowhirl.catchup`
   - SHA-1 of the signing key. For the shared development key in `app/debug.keystore`:
     `F4:02:88:93:10:11:F2:C4:A6:03:24:3C:29:8B:D7:A8:EA:20:C9:13`

No client ID needs to go into the code: Google matches the app by package name and key.

## Publishing (later)

`app/debug.keystore` is a shared development key so test builds update in place. Before a
Play Store release, create a private upload key, keep it in CI secrets, and register its
SHA-1 for Google Drive too.
