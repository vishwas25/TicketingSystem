# GitHub Actions

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### What it is

GitHub's built-in CI/CD. A **workflow** is a YAML file in `.github/workflows/`.
When an **event** happens (push, pull request…), GitHub starts a fresh virtual
machine (**runner**), checks out your code, and runs the **steps** you listed.
A red ❌ on a commit or PR means a step failed.

### Vocabulary

```
workflow (backend-ci.yml)
 └─ triggered by events (on: push / pull_request)
 └─ jobs (verify)                 ← each job = one fresh runner VM; jobs run in parallel by default
     └─ steps                     ← run in order; a failing step stops the job
         ├─ uses: some/action@v4  ← reusable action someone published
         └─ run: shell command    ← plain bash
```

### Walkthrough of `backend-ci.yml`

```yaml
on:
  push:
    branches: [main]
    paths: ["backend/**", ".github/workflows/backend-ci.yml"]
  pull_request:
    paths: ["backend/**", ".github/workflows/backend-ci.yml"]
```
Runs on pushes to `main` and on every PR, **but only if backend files or this
workflow changed**. A docs-only commit doesn't trigger it. (That also means a
change to the root `Makefile` or `docker-compose.yml` alone won't run CI.)

```yaml
concurrency:
  group: backend-ci-${{ github.ref }}
  cancel-in-progress: true
```
If you push twice quickly to the same branch, the first run is cancelled.
`${{ ... }}` is an expression evaluated by GitHub; `github.ref` is the branch.

```yaml
jobs:
  verify:
    runs-on: ubuntu-latest
    timeout-minutes: 25
```
One job on a GitHub-hosted Ubuntu VM. **Docker is pre-installed there**, which
is why Testcontainers works in CI with no extra setup. The timeout stops a hung
build from burning minutes.

| Step | What / why |
|---|---|
| `actions/checkout@v4` | Clone the repo into the runner |
| `actions/setup-java@v4` (temurin 21, `cache: maven`) | Install JDK 21; cache `~/.m2` keyed on the POM so dependencies aren't downloaded every run |
| `docker info` | Fail fast with a clear message if Docker were missing |
| `sysctl -w vm.max_map_count=262144` | Elasticsearch prerequisite (Phase 5); harmless now |
| `./mvnw -B verify` (in `backend/`) | **The same gate as `make verify`.** Local and CI run the same command, so "passes locally" means "passes in CI" |
| `upload-artifact` with `if: always()` | Save Surefire/Failsafe reports **even when tests failed** (that's when you need them). Download from the run's *Summary* page |

### Reading a failed run

1. PR → *Checks* tab, or repo → *Actions* tab → the run.
2. Click the red step and expand the log. Search for `ERROR` or `FAIL`.
3. For test failures, download the `surefire-reports` artifact and open the `.txt` files.
4. Reproduce locally with `make verify`. If it passes locally but not in CI,
   suspect: reused local containers with leftover data, a local
   `~/.testcontainers.properties`, OS differences (file case sensitivity), or
   time zones.

### Good practice for this project

- **Branch protection:** in repo *Settings → Branches*, require the `verify`
  check to pass before merging to `main`. Then CI is a real gate, not advice.
- **Pin action major versions** (`@v4`), as done here.
- **Secrets** (Phase 8 registry credentials) go in *Settings → Secrets* and are
  read as `${{ secrets.NAME }}`. Never in the YAML.
- **`mvnw` must be executable in Git.** If CI says `Permission denied`, run
  `git update-index --chmod=+x backend/mvnw` and commit.

### What's coming later

| Slice | Adds |
|---|---|
| 2.22 | `frontend-ci.yml`: `npm ci`, ESLint, `npm run build` |
| 8.6 | Build and push Docker images on `main` |
| optional | `codeql.yml` security scanning (spec §5.5) |

### Read next

- GitHub Docs: *Understanding GitHub Actions* (workflows, events, jobs, runners)
- GitHub Docs: *Workflow syntax for GitHub Actions* (`on.paths`, `concurrency`, `if`)
- `actions/setup-java` README: *Caching packages dependencies*
- GitHub Docs: *About protected branches*

### Check yourself

1. You change only `SPECIFICATION.md` and push to main. Does CI run? Should it?
2. Why is it important that CI runs `./mvnw -B verify` rather than something CI-specific?
3. Why does the artifact upload need `if: always()`?
4. A test passes locally and fails in CI. List three likely causes in *this* project.

---

## My notes

*Write your own notes after the corresponding TicketingSystem slice.*
