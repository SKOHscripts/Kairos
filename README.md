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

<p>
  <a href="https://f-droid.org/packages/com.skohscripts.kairos/"><img src="site/badges/en/fdroid.svg" alt="Get it on F-Droid" height="56"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/SKOHscripts/Kairos"><img src="site/badges/en/obtainium.svg" alt="Get it on Obtainium" height="56"></a>
  <a href="https://github.com/SKOHscripts/Kairos/releases/latest/download/Kairos-android.apk"><img src="site/badges/en/apk.svg" alt="Download the APK (GitHub)" height="56"></a>
  <a href="https://github.com/SKOHscripts/Kairos/releases/latest/download/Kairos-windows-x64.msi"><img src="site/badges/en/windows.svg" alt="Download for Windows" height="56"></a>
  <a href="https://github.com/SKOHscripts/Kairos/releases/latest/download/Kairos-linux-x64.deb"><img src="site/badges/en/linux.svg" alt="Download for Linux" height="56"></a>
  <a href="https://github.com/SKOHscripts/Kairos/releases/latest/download/Kairos-macos-arm64.dmg"><img src="site/badges/en/macos.svg" alt="Download for macOS" height="56"></a>
  <a href="https://skohscripts.github.io/Kairos/app/"><img src="site/badges/en/web.svg" alt="Use it in the Browser" height="56"></a>
</p>

[More options on the download page](https://skohscripts.github.io/Kairos/en/)

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

<p>
  <a href="https://f-droid.org/packages/com.skohscripts.kairos/"><img src="site/badges/en/fdroid.svg" alt="Get it on F-Droid" height="56"></a>
  <a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/SKOHscripts/Kairos"><img src="site/badges/en/obtainium.svg" alt="Get it on Obtainium" height="56"></a>
  <a href="https://github.com/SKOHscripts/Kairos/releases/latest/download/Kairos-android.apk"><img src="site/badges/en/apk.svg" alt="Download the APK (GitHub)" height="56"></a>
  <a href="https://github.com/SKOHscripts/Kairos/releases/latest/download/Kairos-windows-x64.msi"><img src="site/badges/en/windows.svg" alt="Download for Windows" height="56"></a>
  <a href="https://github.com/SKOHscripts/Kairos/releases/latest/download/Kairos-linux-x64.deb"><img src="site/badges/en/linux.svg" alt="Download for Linux" height="56"></a>
  <a href="https://github.com/SKOHscripts/Kairos/releases/latest/download/Kairos-macos-arm64.dmg"><img src="site/badges/en/macos.svg" alt="Download for macOS" height="56"></a>
  <a href="https://skohscripts.github.io/Kairos/app/"><img src="site/badges/en/web.svg" alt="Use it in the Browser" height="56"></a>
</p>

| System | File | Note |
|---|---|---|
| Android 8 or later | `Kairos-android.apk`, or [F-Droid](https://f-droid.org/packages/com.skohscripts.kairos/) | The same signed APK as on F-Droid: you can switch from one to the other without uninstalling. |
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

**Updates.** On Android, through F-Droid or a new APK; with an APK installed
by hand, Settings → General → Updates shows your version and "See the latest
version" opens the download page (Kairos still never connects). On the desktop, Kairos
checks every 6 hours for a new version and announces it in a banner
("Download" opens its page); setting in Settings → General → Updates. The web version is
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
- **Skipped months**: if Kairos was not opened for a whole month, a "day N of
  the month" task gets no occurrence for that month. Today's Day view then
  says so, one line per series ("2 monthly occurrences were skipped: Expense
  report"): recreate them (already overdue) or ignore them, with an undo.
  Nothing is recreated without asking.
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
- **Level names** (Settings → Planning): call the priorities and the points
  in your own words ("Urgent", S/M/L…). The badge shows your name, the sheet
  and the guide show it with its value ("M (3)"); P0 stays red and Kairos still
  computes with the values.
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
  weekly, monthly), editable and deletable.
- **Weekday slots skip holidays**: a "weekdays" slot, like the daily stand-up,
  skips public holidays and extra days off, like a "weekdays" task.
- **Monthly slots, one day apart**: a slot can repeat every month, on a day
  ("the 15th", moved back to the previous working day like a monthly task) or
  on a weekday ("the 3rd Tuesday", "the last Friday"). On any recurring slot,
  "Only this one" skips that day or changes its title and hours for that day
  only; "Restore" brings the series back.
- **Calendar import**: Settings → Data → Calendars → "Import a calendar…" (or
  Import) reads an `.ics` file (Outlook, Google Calendar, Thunderbird,
  Nextcloud…). A preview comes first; each event becomes a read-only busy slot
  in your time zone (repeating events unfolded over the next 6 months,
  cancelled and "free" ones skipped, tentative ones kept, titles can be
  hidden), whole-day events become a badge on the day without blocking time.
  Description, location and attendees are never kept. "Reimport" replaces the
  calendar's slots from today on, never yours; "Remove" takes it all away; an
  imported slot can be deleted on its own. No network, no account.
- **Phone calendars** (Android): Settings → Data → "Use my phone calendars"
  shows the events of the calendars you tick (Google, Exchange, DAVx⁵,
  ICSx⁵, Outlook with "Sync calendars" on) as read-only busy slots, read live
  and never stored or exported. Kairos asks for calendar access only at that
  moment (an optional permission, not a network one).
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
- **Reminders** (Settings, off by default): a notification a few minutes
  before a fixed-time task and, if you want, when the plan moves on to the
  next task. On Android even with Kairos closed (possibly a few minutes late
  while the phone sleeps, no extra permission); on desktop and the web while
  Kairos is open.
- **Daily summary** (Settings, off by default): a "Your day" notification at
  the time you choose, with the number of tasks and slots the plan holds and the
  first three tasks with their time; nothing on a day with nothing planned. A
  tap opens the Day view.

### Views

- **Day**: "Up next", the ordered agenda, sections "To process", "Blocked",
  "Scheduled later", filters, backlog, timeline; "lingering for N d" badge and
  P0 overload banner. You can look at another day.
- **Home screen widgets** (Android): "Now" shows the task to do now, its times
  and the next one, with "Done" (tap twice to confirm), the timer and capture;
  "Day" lists the day hour by hour, deadlines first and finished tasks struck
  through. In the app's colours, with no extra permission; a tap opens the Day
  view.
- **Week**: seven days (deadlines, scheduled tasks, finished tasks, slots), the
  week's real time by type.
- **Move from the week**: tap a task in the Week view to move it to another
  day (public holidays and days off are flagged), with "Undo".
- **Month**: the "Month" switch of the Week view shows the whole month, one
  box per day with the number of planned tasks, a flag for deadlines, a warning
  for overloaded days and an icon for public holidays and days off, with the same
  information as lists below; tap a day to open it.
- **Statistics**: weekly throughput, estimation calibration (median real time
  per level, bias), time by type, flow, completeness. The sample size is
  shown; a small sample is marked "not reliable".
- **Statistics your way**: narrow them down by period (4, 8, 12 weeks or all)
  and with the same search and filters as the Day view (type, project,
  priority…); nothing is saved. "Export…" saves what the screen shows: the
  tables as one CSV file (ready for a spreadsheet), or one card as a PNG image
  with its date, period and filter.
- **Trends** (Statistics): tracked time day by day (week by week beyond 8
  weeks) and the estimation bias week by week, each chart with its written
  summary and sample size.
- **Correct the estimates** (Statistics): replaces the estimate of finished,
  timed tasks with the time really spent, after a preview where you untick
  the ones to keep; "Undo" brings the old estimates back.
- **When you work on each type** (Statistics): for each task type, the 2-hour
  slots where you really work (from your timer sessions), compared with your
  usual rhythm. This card only observes.
- **Your most efficient hours** (Statistics): for each type, the slot where you
  finish it fastest compared with your estimates; Best hours can use it
  (measure by efficiency, or both).
- **Best hours** (Settings, off by default): the plan prefers, between tasks of
  similar score, the hours when you usually work each type, the hours when you
  finish it faster than estimated, or both (detected from the timer, strength
  Low / Medium / High), or the preferred hour you set for a type.
  Overdue tasks and urgency still come first; the day view says when it made the
  difference.

### Data

- Everything stays on the device: no network connection (Android does not even
  have the permission), no account.
- **Export and import** (JSON, Settings → Data); an automatic backup precedes
  every import.
- On the desktop, the **data folder** can be changed (Settings → Data →
  "Change folder…", for a synced or encrypted folder) or set at launch with
  `--data-dir=<folder>`; it takes effect at the next start, and your data is
  copied, never moved. Settings → About → **Diagnostics**: an optional local log
  (off by default, no task content) and "Copy diagnostics" for a bug report.
  The update check follows the system proxy.
- **Linked file**: on the desktop and Android too, Settings → Data → "Create a
  file" keeps an up-to-date copy of your data (an export, which opens anywhere)
  in a folder of your choice, for example a Syncthing or Nextcloud folder,
  written 2 seconds after each change. If another device changed the file,
  Kairos stops writing to it and asks: overwrite, import it, or unlink.
- **Web version**: data saved in the browser and, in Edge or Chrome, **linked
  to a file** kept up to date on every change.
- **Languages**: the interface speaks **English, French, Spanish, German, Russian
  and Italian**. Kairos follows the system language when it is one of these
  (English otherwise), or the one you pick in Settings → General → Language.
  Dates, hours and numbers follow it, and so do the sample tasks of a new
  installation. Another language? See [Translating Kairos](#translating-kairos).

### Finding your way

- Kairos opens on the **Home** page, the first tab of the navigation: it
  recalls what Kairos is for and what it can do, with direct access to every
  screen; it also explains how to work with a manager or a team.
- **Settings** open from the gear icon, at the top right of every screen.
- **Settings by category**: each category has its icon and a summary of what
  it holds; on a wide window, the list and the category sit side by side.
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
- **The guide remembers**: a chapter read to the end is checked, “New in this
  version” stays hidden for the version once dismissed, and “Show me the
  screen” scrolls to the exact place a card talks about and marks it for a
  moment. Settings → About → “See the guide again from the start” brings
  everything back.
- **Recurring task and Settings, step by step**: in "Discover Kairos",
  "Create my first recurring task" walks you through the real sheet and shows,
  for each rule, the next deadline it would produce; "Set up Kairos step by
  step" goes through the working day, the hours, the contexts and the best
  hours, one card at a time, and saves nothing until you press Save.

### Team management (optional)

- Turn it on in **Settings → Team**; when off, nothing changes in Kairos. When
  on, a **Personal | Team** selector appears at the top.
- **Guided first team task**: from "Discover Kairos" (team space on), Kairos
  walks you through the team task sheet, field by field (assignee and their
  load, priority and size, effort, deadline, category), then says where the task
  landed. Nothing is written if you give up.
- **Members**: availability, hours per day (or different hours from Monday
  to Friday, a day at 0 being a day off, for a four-day week), absences;
  "This is me" makes the team tasks assigned to you appear in your Day view.
- **Team backlog** sorted by the same WSJF score, categories = task types.
- **Assignment** and **reassignment** of tasks to members, one by one or in
  bulk.
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
  fields; nothing is deleted unless you choose it. Each task can carry a short
  **note** each way ("Blocked until the API key arrives"), shown with its
  author and date and kept in the task's history. A member can also
  **propose** one of their own tasks to the manager: it goes with the next
  report (title, description, duration, type), and the manager adds it to the
  backlog, declines it or decides later.
- A single user, the manager: everything stays on the device, with no account
  or network; the files travel however you like (email, USB drive…).

### Support

- A **heart** in the top bar (and a button in "About & guide") opens the
  author's [donation page](https://skohscripts.github.io/donate.github.io/).
  Kairos stays free and identical for everyone.

### Appearance

- **Theme color** of your choice (Settings → General → Appearance): ocean by default,
  seven other suggested colors (honey, the color of the logo, among them), or a free color (`#RRGGBB`). The whole theme is
  derived from it (Material Design 3), contrast included.
- On **Android 12 and later**, "System colors" follows the wallpaper colors
  (Material You).
- **Dark theme**: Kairos follows the system (dark when your phone, computer or
  browser is). The "Dark theme" switch (Settings → General → Appearance) picks the
  other theme; switching it back follows the system again. Exported statistics
  images stay light.
- At launch, a **startup page** (Android, desktop, web): the sundial hand makes
  one turn and settles on the logo, then the app fades in. A tap skips it.

## Feature list

Every feature in one table, in the order of the in-app list (Home → "Feature list"), with the
version that brought it and the platforms it exists on.

| Chapter | Feature | Since | Platforms |
|---|---|---|---|
| First steps | Capture without thinking | 3.0.0 | All |
| First steps | Keyboard shortcuts | 3.0.0 | Desktop, Web |
| First steps | Qualify in one click | 3.0.0 | All |
| First steps | Do things in order | 3.0.0 | All |
| First steps | Discover Kairos | 3.4.0 | All |
| First steps | Create your first task | 3.4.0 | All |
| First steps | The guide remembers and shows the spot | 3.7.0 | All |
| First steps | Recurring task and Settings, step by step | 3.9.0 | All |
| Plan | Priority, points and estimate | 3.0.0 | All |
| Plan | Level names | 3.9.0 | All |
| Plan | Score | 3.0.0 | All |
| Plan | Placement in the day | 3.0.0 | All |
| Plan | Afternoon dip | 3.0.0 | All |
| Plan | Several windows and contexts | 3.5.0 | All |
| Plan | Plan around your best hours | 3.6.0 | All |
| Plan | Hours by day of the week | 3.4.0 | All |
| Plan | Working days and holidays | 3.0.0 | All |
| Follow | Timeline of the day | 3.0.0 | All |
| Follow | Track time | 3.0.0 | All |
| Follow | Three timer alerts | 3.0.0 | All |
| Follow | Learned durations | 3.4.0 | All |
| Follow | When you work on each type | 3.5.0 | All |
| Follow | Your most efficient hours | 3.9.0 | All |
| Follow | Your day on the home screen | 3.12.0 | Android |
| Follow | Plan the week | 3.0.0 | All |
| Follow | The month at a glance | 3.8.0 | All |
| Follow | Move a task from the week | 3.7.0 | All |
| Follow | Week and statistics | 3.0.0 | All |
| Follow | See how things evolve | 3.8.0 | All |
| Follow | Correct your estimates | 3.8.0 | All |
| Follow | Statistics your way | 3.7.0 | All |
| Organize | Subtasks | 3.0.0 | All |
| Organize | Editable subtasks | 3.3.0 | All |
| Organize | Recurrence | 3.0.0 | All |
| Organize | Skipped months | 3.13.0 | All |
| Organize | Snooze and move | 3.0.0 | All |
| Organize | Blockers | 3.0.0 | All |
| Organize | Busy slots | 3.0.0 | All |
| Organize | Deep work | 3.0.0 | All |
| Organize | Weekday slots skip holidays | 3.7.0 | All |
| Organize | Monthly slots, and one day apart | 3.8.0 | All |
| Organize | Import your meetings from a calendar | 3.10.0 | All |
| Organize | Phone calendars | 3.10.0 | Android |
| Team | Guided first team task | 3.8.0 | All |
| Team | The members | 3.1.0 | All |
| Team | Your personal work counts | 3.3.0 | All |
| Team | The backlog | 3.1.0 | All |
| Team | Assign | 3.1.0 | All |
| Team | Tracking | 3.1.0 | All |
| Team | Team load | 3.1.0 | All |
| Team | Forecasts | 3.1.0 | All |
| Team | Exchanges | 3.1.0 | All |
| Settings | Plain settings, defaults one tap away | 3.4.0 | All |
| Settings | Settings sorted by category | 3.7.0 | All |
| Settings | Reminders before it is time | 3.8.0 | All |
| Settings | Your day in one notification | 3.12.0 | All |
| Settings | Theme color | 3.1.0 | All |
| Settings | Dark theme | 3.7.0 | All |
| Settings | Languages | 3.11.0 | All |
| Settings | Your data and your manager | 3.0.0 | All |
| Settings | A copy of your data in a synced folder | 3.10.0 | All |
| Settings | Web version | 3.0.0 | Web |

## Settings

Settings are grouped by category, each with its icon and a summary of what it
holds: tap one to open it (on a wide window, the list and the category sit side
by side). Every setting is explained under its field; a single "Save" for all
categories (a change is kept while you move from one to another), and an
invalid field says why: a refused save opens its category and scrolls to it.
The priority score and the afternoon dip sit under a collapsed "Advanced: how
tasks are ordered" group, and "Restore default settings" (Data) refills the
form with the defaults (the team space is untouched; nothing is saved until
you press "Save").

| Category | Section | Settings | Default |
|---|---|---|---|
| General | Language | Interface language (System, Français, English, Español, Deutsch, Русский, Italiano) | System |
| | Appearance | Theme color (suggested, free, or system on Android 12+) | ocean `#2F6FED` |
| | Appearance | Dark theme (two-state switch) | follows the system |
| | Updates (desktop) | Check for new versions | on |
| | Updates (Android, APK installed by hand) | Installed version, "See the latest version" | |
| | Shortcut (desktop, portable version) | Create, update or remove the shortcut | offered at launch |
| Day and hours | Working day | Default duration, buffer after a slot, start and end | 30 min, 5 min, 9 am – 6 pm |
| | Hours by day of the week | Usual, custom (one to four windows) or day off | usual |
| | Contexts | Up to four, each with its task types | none |
| | Public holidays | French calendar, extra dates | on |
| Planning | Task types | List of types | |
| | Level names | A name for each priority and each points level (16 characters at most) | default names |
| | Best hours | Prefer my usual hours, strength, measure by (frequency, efficiency, both), preferred hour per type | off, medium, frequency |
| | Learned durations | On/off, occurrences considered, occurrences needed | on, 5, 3 |
| | Safeguards | "Lingering" thresholds (overdue, undated), P0 overload | 7 d, 14 d, 5 |
| | Priority score (WSJF), advanced | Value base, urgency horizon and weight, default points | 4, 14 d, 8, 3 |
| | Afternoon dip, advanced | On/off, start, trough, end, strength | on, 1 pm – 3 pm – 4 pm, 1 |
| Timer and statistics | Timer alerts | Forgotten timer, suggested break, fallback sound | 180 min, 50 min, off |
| | Reminders | Fixed-time tasks and lead time, next task, daily summary and its time | off, 10 min, off, off, 08:30 |
| | Statistics | Window of the indicators | 8 weeks |
| Team | Team | Team management, then its thresholds, load and forecasts | off |
| Data | Data, calendars, default settings | Export, import, import a calendar (reimport, remove), restore the defaults | |
| | Data folder (desktop) | Change folder…, back to the default folder (next start) | system folder |
| About | | Discover Kairos, About & guide, see the guide again from the start | |
| | Diagnostics (desktop) | Keep a diagnostic log, copy diagnostics | off |

## Translating Kairos

The interface is available in English, French, Spanish, German, Russian and
Italian. The English and French texts are the reference; the Spanish, German,
Russian and Italian ones were produced with the help of an AI model and have not
been proofread by native speakers: **corrections are very welcome**, and so is a
new language written in the Latin or Cyrillic alphabet (Portuguese, Dutch, Polish,
Ukrainian…).

- All the texts are in
  [`kmp/ui/src/commonMain/composeResources/values-xx/strings.xml`](kmp/ui/src/commonMain/composeResources/values-es/strings.xml)
  (`values/` is English, the fallback): one file per language, the same keys
  everywhere. Fix a text in the file of your language, or copy `values/strings.xml`
  into `values-xx/` and translate it. Keep the placeholders (`%1$s`, `%1$d`) and
  the typographic apostrophe `’`; the `StringsParityTest` test checks it.
- A new language also needs a few lines of code (month names, sample tasks, the
  name in the language list) and a store listing: the checklist is in
  [`docs/spec/i18n.md`](docs/spec/i18n.md) (in French), section “Ajouter une langue”.
- Open a pull request in English, or an issue if you would rather not touch the
  code: it is enough to send the translated file.
- Languages whose script the bundled font cannot display (Chinese, Japanese,
  Korean, Arabic, Hebrew, Hindi…) need more work: see
  [issue #147](https://github.com/SKOHscripts/Kairos/issues/147).

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
- Visual identity (Material Design 3, "ocean" theme):
  [`docs/DESIGN_SYSTEM.md`](docs/DESIGN_SYSTEM.md).
- Versions, releases and packages: [`docs/spec/distribution.md`](docs/spec/distribution.md);
  F-Droid and the download page:
  [`docs/spec/publication.md`](docs/spec/publication.md).
- Contribution rules: [`CLAUDE.md`](CLAUDE.md). Pull requests and issues are
  welcome in English.

## License

[MIT](LICENSE).
