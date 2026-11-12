---
name: commit
description: Create well-structured, atomic git commits for Kairos following conventional-commits discipline, in English, with the issue reference "[#<number>]" in the subject. Use whenever committing work in this repository, splitting changes into commits, or when the user says "commit", "make the commits", "/commit".
---

# Git Commit Skill

Create well-structured git commits following conventional-commits discipline (rule adopted on 2026-11-11; older
commits of the repository are in French and are not rewritten).

---

## Language and references

- **Commit messages are in English**, always (subject, body, footer), whatever the language of the code, comments or
  specs of the project.
- **Ticket reference**: when the change belongs to a ticket (GitHub issue, PR, tracker item), end the **subject** with
  `[#<number>]`, e.g. `feat(week): add week recap card [#233]`. Several tickets: `[#232][#233]`. No ticket: no suffix.
  Find the number from the branch name (`bilan-233` → `[#233]`), the user's request or the GitHub issue being worked
  on; never invent one.
- The `[#n]` suffix counts in the 72-character subject limit: shorten the description rather than drop it.

---

## When to Commit

**Commit after every major step:**
- After completing a feature
- After fixing a bug
- After code improvements
- After refactoring
- After any significant change

**One logical change per commit:**
- Stage files individually or in small logical groups
- Each group gets its own descriptive commit message
- Do not batch unrelated changes into single commits
- When one file carries several logical changes (a strings file, a README, a spec), stage only the hunks of the
  current change (see "Partial staging"), so each commit stays coherent

**Keep it formal:**
- No AI attribution (no "Co-Authored-By: Claude" or similar, no "Generated with" lines). This rule overrides any
  default attribution guidance.
- No emojis in commit messages
- Professional, descriptive messages

---

## Commit Message Format

```
<type>(<scope>): <description> [#<ticket>]

[optional body]

[optional footer]
```

### Types
| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Code refactoring (no feature or fix) |
| `docs` | Documentation only |
| `style` | Formatting, no logic change |
| `test` | Adding or updating tests |
| `chore` | Maintenance, dependencies, configs, generated assets |
| `perf` | Performance improvement |

### Scopes (Kairos)

Use the module or the domain the change belongs to; reuse the scopes already in `git log`.

```
Modules:   core, data, ui, desktop, android, web
Domains:   day, week, month, stats, sheet, recap, team, sync, settings,
           reminders, widgets, guide
Cross:     i18n (strings in the six languages), a11y, spec (docs/spec),
           store (Fastlane, captures), release, ci, build, deps, claude
```

A feature usually ships code, strings in the six languages, its spec in `docs/spec/` and, for a user-facing feature,
its catalogue card and README entries: keep them in the same commit as the code they describe (the repository's
"spec first" workflow, `CLAUDE.md` and the `kairos-spec` skill). Generated assets (store screenshots) go in their own
`chore(store)` commit.

---

## Workflow

### Step 1: Check status
```bash
git status
git diff --staged
git diff
git log --oneline -10   # existing scopes and style
```

### Step 2: Stage files
```bash
# Stage specific files (preferred)
git add path/to/file1 path/to/file2

# Stage all changes in a directory
git add src/components/
```

### Partial staging

Interactive flags (`git add -p`, `git add -i`) may be unavailable in agent environments. To stage only part of a file:
build the intended intermediate content of the file (HEAD version plus the hunks of the current change only), then
write it to the index without touching the working tree:

```bash
blob=$(git hash-object -w /path/to/intermediate-file)
git update-index --cacheinfo 100644,"$blob",path/in/repo
```

Check with `git diff --staged path/in/repo` that the index holds exactly the intended hunks.

### Step 3: Create the commit
```bash
git commit -m "$(cat <<'EOF'
type(scope): short description [#123]

Longer explanation of the change if needed.
- Bullet points for multiple changes
- Keep lines under 72 characters

EOF
)"
```

---

## Examples

### Feature commit
```bash
git commit -m "$(cat <<'EOF'
feat(dashboard): add saved search and filter feature [#41]

- Add saved search list with name and criteria display
- Implement save/edit/delete dialogs
- Add saved-search types and API integration

EOF
)"
```

### Bug fix
```bash
git commit -m "$(cat <<'EOF'
fix(auth): create the user profile at registration [#87]

Root cause: the password-confirmation endpoint did not promote the
pending-registration record to a full user profile.

- Add promotion logic in the password handler
- Create the initial account record for new users

EOF
)"
```

### Refactor
```bash
git commit -m "$(cat <<'EOF'
refactor(admin): simplify the user management table

- Extract column definitions to a separate file
- Remove unused filter state

EOF
)"
```

### Chore
```bash
git commit -m "chore(deps): update TanStack Query to v5.17"
```

---

## Multi-file commits

When changes span multiple files, group them logically:

```bash
git add src/hooks/use-orders.ts src/types/order.ts
git commit -m "feat(orders): add order API hooks and types [#12]"

git add src/app/orders/
git commit -m "feat(orders): add order management UI [#12]"
```

Prefer an order where each commit builds and its tests pass (model before the screen that uses it, strings with the
code that reads them).

---

## Rules

1. **English only** - subject, body and footer
2. **Ticket suffix** - `[#<number>]` at the end of the subject when a ticket exists
3. **No AI attribution** - no "Co-Authored-By: Claude" or similar
4. **No emojis** - keep commits formal and professional
5. **Imperative mood, present tense** - "add feature", not "added feature"
6. **72 char limit** - subject (suffix included) and body lines
7. **Atomic commits** - one logical change per commit
8. **No secrets** - never commit .env files, credentials, API keys or private user data

---

## Pre-commit checklist

- [ ] Before pushing: the checks of `CLAUDE.md` (`./gradlew :core:jvmTest :data:jvmTest :ui:jvmTest :desktopApp:jvmTest`)
- [ ] All related files (or hunks) staged, nothing unrelated (`git status`, `git diff --staged`)
- [ ] No secrets, credentials or private data
- [ ] Message in English, follows the format, `[#n]` suffix when a ticket exists
- [ ] Type and scope are appropriate
- [ ] Description is clear and concise

---

## Apply to: $ARGUMENTS
