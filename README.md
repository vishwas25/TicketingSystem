# Ticketing System

A learning-first event ticketing backend focused on one hard guarantee: concurrent
booking requests must never sell more tickets than an event has available.

**Stack:** Java 21 | Spring Boot | Spring Security | PostgreSQL | Redis | Maven

This is a new, independently built project. The sibling `../ticketing-system/`
repository is a third-party reference for workflows and design comparisons; its
source is not the implementation baseline. The project is tailored to the
backend experience and skills described in the resume, while the roadmap marks
what is newly implemented and verified here.

## Current status

**Phase 0 implementation complete; your study is next.** The runnable backend,
database baseline, test harness, local stack, and CI definition are in place.
Phase 1 business code is intentionally not implemented.
See [SPECIFICATION.md](SPECIFICATION.md) for scope and
[docs/learning/ROADMAP.md](docs/learning/ROADMAP.md) for the incremental build.

## Learning workflow

Work one roadmap slice at a time. You write the slices marked **You**; I explain,
review, and help debug. We pair on **Pair** slices. I generate only the repeats
marked **You -> AI** after you have implemented one by hand, and generate
**AI -> review** slices only when the roadmap calls for it. Ask for a hint before
asking for a finished solution; if we do use a solution, rewrite it from memory.

The other person's project is a reference, not proof that a behavior is correct.
Compare designs, preserve attribution, and independently test every important
claim, especially around transactions and concurrency.

## Documentation

| Document | Purpose |
|---|---|
| [SPECIFICATION.md](SPECIFICATION.md) | Product scope, design constraints, and delivery phases |
| [CLAUDE.md](CLAUDE.md) | Working agreement, conventions, and definition of done |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Decisions and the reasoning behind them |
| [Learning roadmap](docs/learning/ROADMAP.md) | Slices, ownership, concepts, and explain-back questions |
| [Phase 0 study guide](docs/learning/PHASE-0.md) | File-by-file walkthrough, exercises, and copied topic primers |

## Quick start

Requires Docker with Compose, JDK 21, and Make. Maven 3.9.9 is downloaded by the
checked-in wrapper. Run from this repository root:

```bash
make up
make verify
make run
```

API health: http://localhost:8081/actuator/health.
Swagger: http://localhost:8081/swagger-ui.html.
No business endpoints exist yet. PostgreSQL uses `localhost:5433`; Redis uses
`localhost:6380` and is not yet connected to the application. These ports avoid
the default SecondHandSell stack ports.

`make help` lists routine commands. `make test-unit` runs only unit tests;
`make test-it` runs only PostgreSQL-backed integration tests. `make verify` is
the gate for later work and uses isolated Testcontainers databases rather than
the Compose database. Stop the app with Ctrl+C; `make down` retains dev data.

Optional root `.env` values follow [.env.example](.env.example); `make run`
exports them to the backend. Defaults are local development credentials only.
This foundation is not production-hardened; authentication arrives in Phase 1.
