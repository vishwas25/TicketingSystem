# Ticketing System - Working Agreement

The [SPECIFICATION.md](SPECIFICATION.md) is authoritative for scope and system
behavior. Read the relevant roadmap slice and spec sections before changing
code. If the design appears wrong or ambiguous, explain the trade-off and
propose a spec/ADR change before embedding the assumption in code.

## Learning mode

The owner explicitly requested AI implementation of Phase 0, followed by their
own study using [docs/learning/PHASE-0.md](docs/learning/PHASE-0.md). That exception
does not extend to Phase 1: the owner writes business code while pairing with
AI. Do not implement Phase 1 automatically when they ask to learn or begin it.

Follow the **Who** column in [docs/learning/ROADMAP.md](docs/learning/ROADMAP.md):

| Label | How we work |
|---|---|
| **You** | You write the code. AI explains concepts, gives hints, and reviews; it does not implement the slice. |
| **Pair** | You write in small steps with AI reviewing and answering as you go. |
| **You -> AI** | You implement the first example; AI may generate later repetitions for you to review. |
| **AI -> review** | AI may generate the slice; you review and explain every non-obvious decision. |

If a request would bypass the assigned mode, point that out briefly, then follow
the user's explicit direction. The sibling `../ticketing-system/` code is a
third-party reference only: do not copy its source or imply it was authored here.
Use it to compare approaches, and independently test the behavior being learned.

## Non-negotiables

1. Flyway owns database changes. Add a new migration; do not edit an applied
   migration. Hibernate uses schema validation, never automatic updates.
2. PostgreSQL is the source of truth for inventory. Redis cannot decide whether
   tickets are available.
3. Booking creation and inventory deduction are atomic. Every lock/transaction
   decision needs a PostgreSQL integration test, including concurrent requests.
4. Use exact monetary values and explicit currency; no `float` or `double` for
   prices or totals.
5. Use `Instant` for persisted moments; do not persist local wall-clock times.
6. Enforce authorization in services, not only controllers. Test anonymous,
   wrong-role, and resource-owner cases.
7. Never log passwords, password hashes, JWTs, refresh tokens, or sensitive
   personal data.
8. Controllers handle HTTP concerns; business rules belong in services.
   Entities do not cross the API boundary; use request/response DTOs.
9. Constructor injection only. Keep feature code inside its feature package.
10. Do not introduce payment processing, microservices, Kafka, or a frontend
    before the roadmap reaches an explicitly agreed extension.

## Working loop

For each slice:

1. Read the spec and the slice's listed concepts.
2. Explain the proposed behavior and name the test that could disprove it.
3. Implement according to the **Who** assignment, with tests alongside.
4. Run the narrow relevant test first, then the project verification command.
5. Review the diff and update the roadmap/spec/ADR if the design changed.
6. Create or update the relevant topic primer with your own notes, then complete the explain-back.

Work one phase at a time. Do not start the next phase while the current phase's
gate is failing. No commit is made unless the user asks for one.

## Commands

Run from the repository root: `make up`, `make down`, `make run`, `make verify`,
`make test-unit`, `make test-it`, `make psql`, and `make redis-cli`.
`make verify` is the gate: compile, unit tests, then isolated PostgreSQL
integration tests. `make test-it` explicitly skips only Surefire via
`skipUnitTests`; do not replace it with `-DskipTests`, which skips both suites.

## Phase 0 limits

Redis is present for local study but is not used by the app yet. Auth, business
entities, booking locks, notifications, and production hardening are deferred.
Do not confuse a passing foundation smoke suite with proof of booking safety.
Copied topic primers retain clearly labeled original marketplace examples;
the local Phase 0 study guide is authoritative for current commands and files.

## Definition of done

- The documented verification command passes, including relevant real-Postgres
  integration tests.
- New API behavior has accurate OpenAPI documentation and input validation.
- Authorization and failure cases are tested for every new endpoint.
- Schema changes are migrations and are validated on PostgreSQL.
- Booking and cancellation preserve inventory invariants under concurrency.
- The owner can explain the change and its trade-offs without reading a script.
- Project docs reflect the behavior that actually shipped.