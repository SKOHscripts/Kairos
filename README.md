# Kairos

<img align="right" src="fastlane/metadata/android/en-US/images/icon.png" width="140" alt="Kairos logo">

[![Kairos 3](https://github.com/SKOHscripts/Kairos/actions/workflows/kmp.yml/badge.svg)](https://github.com/SKOHscripts/Kairos/actions/workflows/kmp.yml)
[![Release](https://img.shields.io/github/v/release/SKOHscripts/Kairos)](https://github.com/SKOHscripts/Kairos/releases/latest)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**English** · [Français](README.fr.md)

**The right time for every task.** Kairos answers a concrete question: "what do
I do now, and in what order, given that a meeting from 1 pm to 2 pm keeps me
from handling the urgent topic before 2:05 pm?" It sorts your tasks into the
order to do them and fits them into the gaps of your day. No account, no ads,
no network: your data stays on your device.

**Android, Windows, Linux, macOS, and in the browser.**
[**⬇ Download**](https://skohscripts.github.io/Kairos/en/)

## In short

- It ranks the day's tasks with a priority score, shown on each one, and
  explains that score in one tap.
- It places each task in the gaps of the calendar, around meetings, with a
  buffer after each one.
- It protects deep-work blocks and eases the afternoon dip.
- It times the real work, alerts you when you go over the estimate, forget a
  running timer or need a break, and compares your estimates with the time
  actually spent.

The priority score comes from the WSJF method ("Weighted Shortest Job First"):

```
              value(priority) + criticality(deadline)
  score  =  ──────────────────────────────────────────
                    effort (Fibonacci points)
```

- `value(priority)` is exponential: `4^(2 − p)`, so P0 = 16, P1 = 4, P2 = 1.
- `criticality(deadline)` ramps up as the deadline approaches, or the scheduled
  date if that is closer. An overdue task stays a tier of its own: it always
  comes first, outside the score.
- `effort` is the size in Fibonacci points (1 to 21).

So small and important goes before big and distant. Every weight can be tuned
in **Settings**.

> **Why "Kairos"?** In Greek, *καιρός* means the opportune moment, the right
> instant to act, as opposed to *Chronos*, the time that flows by. That is the
> tool's job: finding the right slot for every task. Code name: **14h55**, the
> post-lunch dip, the least productive hour of the day.

## Install

The [download page](https://skohscripts.github.io/Kairos/en/) offers the right
file for your system. All files, and their checksums (`SHA256SUMS`), are also
in the [GitHub releases](https://github.com/SKOHscripts/Kairos/releases).

| System | File | Note |
|---|---|---|
| Android 8 or later | `Kairos-android.apk` (F-Droid coming soon) | The same signed APK as on F-Droid: you can switch from one to the other without uninstalling. |
| Windows 10 or 11 | `Kairos-windows-x64.msi` | Per-user install, no administrator rights needed. |
| Windows, locked-down PC | `Kairos-windows-x64-portable.zip` | Unzip, run `Kairos.exe`: nothing is installed. Kairos offers to add its shortcuts (Start menu, Desktop). |
| Linux (x86-64) | `Kairos-linux-x64.deb`, or `Kairos-linux-x64-portable.tar.gz` | Portable version: Kairos offers to add itself to the applications menu, with its icon. |
| macOS | `Kairos-macos-arm64.dmg` (Apple Silicon), `Kairos-macos-x64.dmg` (Intel) | |
| Browser | [Kairos online](https://skohscripts.github.io/Kairos/app/) | Recent Edge or Chrome; nothing to install. |

**First install.**

- **macOS**: the app is not notarized by Apple. On first launch, right-click
  Kairos, choose "Open", then confirm.
- **Windows**: SmartScreen may show "Windows protected your PC": click "More
  info", then "Run anyway".
- **Android**: allow installation from the browser or the file manager when
  Android asks (not needed from F-Droid).

**Updates.** On Android, through F-Droid or a new APK. On the desktop, Kairos
checks every 6 hours for a new version and announces it in a banner
("Download" opens its page); setting in Settings → Updates. The web version is
always the latest.

## Features

### Notes (capture)

A screen to empty your head without thinking about structure: a single free
text field, no priority or deadline to choose on the spot (Ctrl/Cmd+Enter
captures without leaving the keyboard). When an idea is ready, **"→ Task"**
converts it: the first line becomes the title, the rest the description, and
the task lands in "To process". The note is archived, never lost. A note can
also be edited, archived without follow-up, or deleted.

### Tasks

- **One-line capture**: the task lands in "To process", and the field stays
  ready for the next one.
- **Full editing**: title, description, priority, points, deadline, scheduled
  date, project, estimated duration, type, recurrence, fixed time, link,
  subtasks, blockers. A single "Save". Date fields come with a date picker,
  quick choices (Today, Tomorrow, Next Monday, In a week) and loose typing
  (`2026-9-30`, or `30/09/2026` in French).
- **Editable subtasks**: a task's sheet lists its subtasks so you can rename,
  check or delete them, on top of adding new ones.
- **Keyboard shortcuts** (desktop and web): `N` to capture, `/` to search.
- **Subtasks**: n/m progress on the parent; only the leaves are scheduled.
- **Recurrence**: daily, weekdays, weekly, monthly (finishing an occurrence
  creates the next one), and "on day N of the month" (moved to the previous
  working day if it falls on a weekend or holiday).
- **"Snooze"**: always lands on a working day, and the task leaves "Today"
  until then. **"Move…"** schedules a task for another day (tomorrow, next
  Monday, in a week, or any date) and offers to undo it.
- **Blockers**: after blocking a task, Kairos says where it went ("Blocked"
  opens); the blocking task shows a "Blocks …" badge.

### "To process"

A task only enters the ranking once its **priority** and **points** are set,
in one tap on chips that say what each choice means (**P0 Critical**,
**P1 Important**, **P2 Useful**; from "1 trivial" to "21 huge"). "How to
qualify?" recalls the rule.

### Scheduling

- **Score** shown on every task; **"Why this position?"** details the
  calculation: what the priority is worth, what the deadline adds, and the
  effort that divides it all.
- **Placement**: tasks are laid into the gaps of the day with their duration,
  a buffer after every meeting, and overflow flagged. A task **pinned** to a
  time is never moved; a conflict is flagged. A task **scheduled** for later
  stays hidden, unless its deadline approaches.
- **Fibonacci points**: a relative size (1, 2, 3, 5, 8, 13, 21). The guide
  "How to estimate points?" shows, for each level, the median real time of
  your own finished tasks and two examples; editing suggests a duration based
  on your history.
- **Afternoon dip (14h55)**: during an adjustable window (1 pm → 4 pm by
  default), Kairos avoids placing complex tasks there and pulls light ones up.
  Deadlines and the critical path always come first; the displayed score does
  not change.
- **Hours by day of the week** (Settings): each day can keep the usual working
  day, have its own hours, or be a day off where nothing is scheduled
  automatically (pinned tasks keep their time). Nothing changes until you set one.
- **Several windows and contexts** (Settings): a day can have several working
  windows (morning, evening…), and a window can be reserved for a context
  (“Work”, “Home”) that groups task types. A task only goes in a window of its
  context; a task with no context goes anywhere, and a context with no window
  that day says so on its tasks. Nothing changes until you set one.
- **Learned durations**: a task without an estimate takes the median real time
  of its last timed occurrences (a recurring task, or tasks with the same
  title) once there are enough of them. It shows as `~45 min` with its source
  in “Why this position?”, and an estimate you type always wins. It can be
  switched off, or tuned, in Settings.
- **Working days and holidays**: built-in French calendar, with extra dates
  in Settings.

### Dependencies

"Blocked by": a task with a blocker still to do leaves the schedule (section
"Blocked", lifted automatically). A blocker of an urgent task moves up in the
order (critical path). Cycles are refused.

### Slots and deep work

- **Busy slots** (meetings, lunch…), one-off or recurring (daily, weekdays,
  weekly), editable and deletable.
- **Deep work**: a slot reserved for a single task, the most urgent one; the
  others work around it.
- **Timeline** of the day: scheduled, busy, pinned, deep work, conflicts, and a
  lane for the time actually tracked.

### Real time and alerts

- **Timer per task** (one at a time), live on the row, in "Up next", in the
  "Right now" card and in the window title; it survives closing the app and
  restarting the phone.
- **Three alerts**: going past the estimate, a forgotten timer, a suggested
  break. A banner in the app, and a system notification: Android (ongoing
  notification with a stopwatch, even with the app closed), the desktop
  notification area, the browser notification.

### Views

- **Day**: "Up next", the ordered agenda, sections "To process", "Blocked",
  "Scheduled later", filters, backlog, timeline; "lingering for N d" badge and
  P0 overload banner. You can look at another day.
- **Week**: seven days (deadlines, scheduled tasks, finished tasks, slots), the
  week's real time by type.
- **Statistics**: weekly throughput, estimation calibration (median real time
  per level, bias), time by type, flow, completeness. The sample size is
  shown; a small sample is marked "not reliable".
- **When you work on each type** (Statistics): for each task type, the 2-hour
  slots where you really work (from your timer sessions), compared with your
  usual rhythm. It only observes: it changes nothing in your plan.

### Data

- Everything stays on the device: no network connection (Android does not even
  have the permission), no account.
- JSON **export and import** (Settings → Data); an automatic backup precedes
  every import.
- **Web version**: data saved in the browser and, in Edge or Chrome, **linked
  to a file** kept up to date on every change.
- Interface in **French** and **English**: the system language (French if the
  system is in French, English otherwise), or the one you pick in Settings →
  Language. More languages are coming in a future version.

### Finding your way

- Kairos opens on the **Home** page, the first tab of the navigation: it
  recalls what Kairos is for and what it can do, with direct access to every
  screen; it also explains how to work with a manager or a team.
- **Settings** open from the gear icon, at the top right of every screen.
- A **"?"** button at the top of every screen: help for the screen you are on,
  access to Home and to the guided tour.
- A step-by-step **guided tour** (six steps), offered on first launch and
  available any time; a second tour presents the Team space when it is
  enabled.
- **Guided first task**: on first launch (or from the Day view while you have
  no task of your own), Kairos walks you through the real task sheet, field by
  field (title, priority, Fibonacci points, estimate, dates, type, advanced
  options), then shows where your task lands in the day and congratulates you
  once it is really created. Nothing is written if you give up.
- **Discover Kairos**: chapters on every feature (Settings, help or Home),
  reopened whenever you like, and a **“New in this version”** section on Home.

### Team management (optional)

- Turn it on in **Settings → Team**; when off, nothing changes in Kairos. When
  on, a **Personal | Team** selector appears at the top.
- **Members**: availability, hours per day, absences; "This is me" makes the
  team tasks assigned to you appear in your Day view.
- Team **backlog** sorted by the same WSJF score, categories = task types,
  **assignment** and **reassignment** (one by one or in bulk).
- **Tracking**: one row per member (in progress, to do, done), progress in 10 %
  steps, signals "late", "no progress", "bounced", limit on tasks in
  progress; a full **history** of every task.
- **Load**: everyone's capacity (availability, absences, public holidays,
  focus rate), load and rate per member, for the team and per category,
  forecast finish date of each task and deadlines at risk; **suggested split**
  of the backlog, to accept line by line.
- **Forecasts** by simulation (Monte Carlo) on the team's real history: finish
  date at 50, 85 and 95 % chance, probability of meeting each deadline, topics
  most at risk, bottleneck member, most likely outcome; **"What if…?"
  scenarios** (absence, reinforcement, reassignment, extra tasks…) compared
  side by side, then applied if you wish.
- **Your personal work counts**: if a member is "This is me", your personal
  tasks enter your load and your team forecasts (marked "Personal"), without
  leaving the Personal space; "Count in the team" (task sheet, more options)
  excludes one. Your meetings in the Day view reduce your capacity, and the
  forecasts offer "My work".
- Forecasts and load take **recurring tasks** into account (every upcoming
  occurrence over the horizon) and the **time already spent** on a task.
- **File exchanges** with members who have Kairos, gathered in the
  **Exchanges** tab of the Team screen: "Send their tasks…" produces a packet
  that the member receives with **Import** (their tasks join their day, marked
  "from <manager>"); they send their progress back ("Send back progress…"),
  which the manager receives in the same place and merges after a preview. For
  each member, the tab shows the date of the last packet and the last report,
  and flags "Waiting for their report". Everyone keeps control of their own
  fields; nothing is deleted unless you choose it.
- A single user, the manager: everything stays on the device, with no account
  or network; the files travel however you like (email, USB drive…).

### Support

- A **heart** in the top bar (and a button in "About & guide") opens the
  author's [donation page](https://skohscripts.github.io/donate.github.io/).
  Kairos stays free and identical for everyone.

### Appearance

- **Theme color** of your choice (Settings → Appearance): honey by default,
  seven other suggested colors, or a free color (`#RRGGBB`). The whole theme is
  derived from it (Material Design 3), contrast included.
- On **Android 12 and later**, "System colors" follows the wallpaper colors
  (Material You).
- At launch, a **startup page** (Android, desktop, web): the sundial hand makes
  one turn and settles on the logo, then the app fades in. A tap skips it.

## Settings

Every setting is explained under its field; a single "Save", and an invalid
field says why. Language and appearance come first; the priority score and the
afternoon dip sit under a collapsed "Advanced: how tasks are ordered" group,
and "Restore default settings" refills the form with the defaults (the team
space is untouched; nothing is saved until you press "Save").

| Section | Settings | Default |
|---|---|---|
| Working day | Default duration, buffer after a slot, start and end | 30 min, 5 min, 9 am – 6 pm |
| Priority score (WSJF), advanced | Value base, urgency horizon and weight, default points | 4, 14 d, 8, 3 |
| Afternoon dip, advanced | On/off, start, trough, end, strength | on, 1 pm – 3 pm – 4 pm, 1 |
| Safeguards | "Lingering" thresholds (overdue, undated), P0 overload | 7 d, 14 d, 5 |
| Task types | List of types | |
| Statistics | Window of the indicators | 8 weeks |
| Timer alerts | Forgotten timer, suggested break, fallback sound | 180 min, 50 min, off |
| Public holidays | French calendar, extra dates | on |
| Language | Interface language (System, Français, English) | System |
| Appearance | Theme color (suggested, free, or system on Android 12+) | honey `#C28417` |
| Shortcut (desktop, portable version) | Create, update or remove the shortcut | offered at launch |
| Updates (desktop) | Check for new versions | on |

## Development

Kairos 3 is written in **Kotlin Multiplatform** and **Compose Multiplatform**
(`kmp/` folder): one codebase for Android, the desktop (JVM) and the web
(Kotlin/Wasm). Free dependencies only, versions pinned in
`kmp/gradle/libs.versions.toml`.

```bash
cd kmp
./gradlew :core:jvmTest :data:jvmTest :ui:jvmTest :desktopApp:jvmTest   # tests
./gradlew :desktopApp:run                                                 # desktop app
./gradlew :desktopApp:run --args=--self-test=/tmp/captures                # off-screen rendering
./gradlew :androidApp:assembleRelease                                     # APK
./gradlew :webApp:wasmJsBrowserDistribution                               # web version
```

The design and contribution documents are written in French.

- Architecture, modules and decisions: [`docs/spec/architecture.md`](docs/spec/architecture.md);
  one specification per domain in [`docs/spec/`](docs/spec/README.md).
- Visual identity (Material Design 3, "honey" theme):
  [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md).
- Versions, releases and packages: [`docs/spec/distribution.md`](docs/spec/distribution.md);
  F-Droid and the download page:
  [`docs/spec/publication.md`](docs/spec/publication.md).
- Contribution rules: [`CLAUDE.md`](CLAUDE.md). Pull requests and issues are
  welcome in English.

## License

[MIT](LICENSE).
