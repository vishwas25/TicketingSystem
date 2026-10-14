# Learning Roadmap

This roadmap builds the ticketing backend in verifiable slices. It borrows the
learning workflow from SecondHandSell, but the scope and decisions here are
specific to ticketing. The sibling implementation is a comparison point, not a
code template. The 21 inherited topic primers are copied locally under `topics/`
with original-project examples clearly labeled. Use the
[Phase 0 study guide](PHASE-0.md) for current files, commands, and differences.
Add any missing ticketing-specific primer just in time and record your own notes
after completing a slice. Phase 0's initial reference observations are in
[REFERENCE-STUDY.md](REFERENCE-STUDY.md); treat them as prompts to verify, not
as a substitute for doing slice 0.1 yourself.

## How to use this roadmap

### Who writes

| Label | Meaning |
|---|---|
| **You** | You write it; AI explains, hints, and reviews. |
| **Pair** | You write it with AI reviewing small steps and answering questions. |
| **You -> AI** | You write the first example; AI can generate later repetitions for review. |
| **AI -> review** | AI generates the slice and walks through the non-obvious parts with you. |

### Loop for each slice

1. Read the relevant spec sections and inspect the existing code/reference named by the slice.
2. Explain the problem, your intended design, and the test that can falsify it.
3. Implement with tests alongside, following the **Who** column.
4. Run the narrow check, then the project's verification gate.
5. Update the topic note and mark the slice complete only when its evidence exists.
6. Answer the phase explain-back questions without notes before moving on.

When stuck, ask for a hint first. If a full solution becomes necessary, study it,
then close it and reproduce the idea from memory.

## Phase 0 - Foundation and reference study

**Goal:** Establish this repository as the source of truth and baseline the
third-party reference without inheriting its unverified assumptions.

**Implementation:** completed by AI at your explicit request. **Learning:**
pending your study. The status column below separates implemented infrastructure
from your own explain-back work; Phase 1 remains unimplemented.

| # | Slice | Who | Learn | Done when | Status |
|---|---|---|---|---|---|
| 0.1 | Inspect the reference README, package tree, build, config, booking path, inventory lock, and tests | AI -> your study | Following request-to-database flow; distinguishing claims from evidence | Reference note exists; you can trace the lock path and missing tests | note ready; study pending |
| 0.2 | Decide scope and explain-back the booking invariant from SPECIFICATION.md | AI -> your study | Inventory invariants; atomicity | Scope is documented; you can state the invariant across failure, cancellation, and concurrency | scope ready; explain-back pending |
| 0.3 | Scaffold Java 21/Spring Boot build, package root `com.vishwas.ticketing`, configuration profiles, and health endpoint | AI -> review | Maven lifecycle, starters, configuration precedence | Clean build and health endpoint work from this repository | implemented; study pending |
| 0.4 | Add Docker Compose for PostgreSQL and Redis with health checks and documented local settings | AI -> review | Containers, ports, volumes, readiness | Services start, app connects to PostgreSQL, documented stop/start works | implemented; study pending |
| 0.5 | Add Flyway baseline, validation configuration, and initial user/event schema plan | AI -> review | Migration history, constraints, exact money/time types | Fresh database migrates; SQL constraints tested; no business entities until Phase 1 | implemented; study pending |
| 0.6 | Establish unit and PostgreSQL Testcontainers integration test setup | AI -> review | JUnit, Spring test slices, containers, test lifecycle | One unit and eight DB-backed/HTTP tests pass; no H2 substitute | implemented; study pending |
| 0.7 | Add the single `verify` command and CI workflow | AI -> review | Maven test phases, CI jobs and caching | Local gate passes; hosted CI result verified after push | local verified; CI defined, remote run pending |
| 0.8 | Walk the build/config/migration/test harness and deliberately trigger one safe failure | AI -> your study | Reading diagnostics and tracing configuration | Safe checksum failure test exists and passes; you can explain it | exercise verified; study pending |

**Explain-back**
1. What does the reference's lock annotation protect, and which transaction holds it?
2. Why does a context-load test not prove that two simultaneous bookings cannot oversell?
3. Which data store decides the final available-ticket count, and why?

## Phase 1 - Identity and authorization

**Goal:** Secure user and admin operations before business endpoints grow.

**Your implementation starts here.** You attempt every slice first; AI explains,
reviews small changes, and helps debug while pairing. Any repeat generation in
**You -> AI** happens only after your first implementation is understood and
you ask for it. No Phase 1 solution is generated automatically.

| # | Slice | Who | Learn | Done when | Status |
|---|---|---|---|---|---|
| 1.1 | User and role schema, entities, repositories | You | JPA mapping, enums, uniqueness, constraints | PostgreSQL integration test saves and reloads a user | todo |
| 1.2 | Registration DTO, validation, service, password hashing | You | Bean Validation, service boundaries, password hashing | Valid registration succeeds; invalid/duplicate inputs fail safely | todo |
| 1.3 | Login and JWT issuance/validation | Pair | JWT claims, signing keys, expiry, filter chain | Valid token authenticates; invalid/expired tokens are rejected | todo |
| 1.4 | Refresh-token storage, rotation, logout/reuse policy | You | Token families, hashing, replay and concurrent refresh | Unit and integration tests cover rotate, expire, replay, logout | todo |
| 1.5 | Role and ownership authorization | You -> AI | 401 vs 403; service-layer rules; method security | Endpoint matrix covers anonymous, USER, ADMIN, and wrong owner | todo |
| 1.6 | Stable validation/auth error format and OpenAPI bearer scheme | Pair | Problem details, controller advice, API contracts | Swagger documents auth and errors; tests assert response shape | todo |
| 1.7 | Phase gate and explain-back | You | Threat boundaries and token lifecycle | Verification passes; explain request authentication end to end | todo |

**Explain-back**
1. What happens between bearer-token parsing and service authorization?
2. Why should refresh tokens be rotated and stored in a non-reversible form?
3. When should an API return 401 versus 403?

## Phase 2 - Event lifecycle and discovery

**Goal:** Create the inventory-bearing resource with explicit state transitions.

| # | Slice | Who | Learn | Done when | Status |
|---|---|---|---|---|---|
| 2.1 | Event schema migration, constraints, indexes, entity and repository | You | Flyway, enum mapping, indexes, exact money and currency | Migration and Hibernate validation pass on PostgreSQL | todo |
| 2.2 | Draft event create/update for admins | You | DTO mapping, validation, role checks | Admin can create/update; customer and anonymous requests are rejected | todo |
| 2.3 | Publish/cancel lifecycle and legal transition tests | You | State machines, concurrency implications | Every allowed and rejected transition is tested | todo |
| 2.4 | Public event details/list with filters and pagination | You -> AI | Specifications, sorting, query plans, N+1 queries | Only published events appear; filters and page metadata are correct | todo |
| 2.5 | OpenAPI, authorization matrix, phase gate | Pair | API review and regression coverage | Verification passes; endpoints are usable from Swagger | todo |

**Explain-back**
1. Why is a draft event not visible to a customer?
2. Which database constraints protect event inventory and price fields?
3. Where would an N+1 query appear in event listing responses?

## Phase 3 - Booking and inventory correctness

**Goal:** Implement the central flow and prove the invariant with parallel DB-backed requests.

| # | Slice | Who | Learn | Done when | Status |
|---|---|---|---|---|---|
| 3.1 | Define booking rules: window, quantity, duplicate policy, cancellation cutoff, retry/idempotency | You | Domain rules and client retry semantics | Rules are explicit in SPECIFICATION.md and test cases | todo |
| 3.2 | Booking schema, states, unique/idempotency constraints | You | Transactional schema design, indexes, unique constraints | Migrations encode the selected rules and pass on PostgreSQL | todo |
| 3.3 | Booking validation and query operations | You | Ownership, query boundaries, DTOs | Rules and private booking access are covered by tests | todo |
| 3.4 | Inventory reservation using a PostgreSQL row lock | Pair | Transaction propagation, pessimistic locks, lock ordering | Test proves the row lock is acquired and released with the transaction | todo |
| 3.5 | Create booking and deduct inventory in one transaction | You | Atomicity, rollback, isolation | Failure at booking persistence rolls back inventory deduction | todo |
| 3.6 | Parallel booking integration test with deliberately scarce stock | You | Race conditions, barriers/executors, real DB contention | Successful quantity never exceeds stock; inventory never goes negative | todo |
| 3.7 | Cancellation, single inventory release, concurrent duplicate cancellation | You | Idempotent state changes and lock behavior | Cancellation restores inventory exactly once under retries/races | todo |
| 3.8 | Compare pessimistic locking with conditional atomic update; record decision | You | Throughput/latency trade-offs, DB semantics | ADR records choice and measured or reasoned trade-offs | todo |
| 3.9 | Phase gate and explain-back | You | End-to-end invariant and rollback | Full verification passes and explain-back is clear | todo |

**Explain-back**
1. Draw the transaction boundary for a booking and show what rolls back on failure.
2. Why does a unit test or H2 test not establish PostgreSQL lock behavior?
3. How do simultaneous requests for the final tickets resolve?
4. How does cancellation avoid returning tickets twice?
5. What does the client observe if a request commits but its response is lost?

## Phase 4 - Redis, caching, and rate limits

**Goal:** Add performance and abuse controls without putting correctness in the cache.

| # | Slice | Who | Learn | Done when | Status |
|---|---|---|---|---|---|
| 4.1 | Explore Redis data types, expiry, and failure behavior | You | Redis basics and operational trade-offs | You can choose a data structure and explain its TTL | todo |
| 4.2 | Cache published event reads with bounded TTL and invalidation | You | Cache-aside, stale data, Spring proxies | Tests prove cache hit/eviction and DB fallback on Redis outage | todo |
| 4.3 | Add atomic rate limiting to booking/auth requests | Pair | Redis Lua/atomic operations, key design, 429 | Parallel limit test proves exact allowance and expiry behavior | todo |
| 4.4 | Measure DB/cache request behavior; record limits and failure mode | You | Metrics and simple load tests | Results and known limits are documented | todo |
| 4.5 | Phase gate and explain-back | You | Cache correctness boundaries | Booking invariants still pass with Redis unavailable | todo |

**Explain-back**
1. Why can event-read cache data be stale without making inventory unsafe?
2. What race appears if rate-limit check and increment are separate commands?
3. What is the system's behavior when Redis is down?

## Phase 5 - Secondary workflows, notifications, audit, admin

**Goal:** Add dependable secondary workflows after the transactional core is proven.

| # | Slice | Who | Learn | Done when | Status |
|---|---|---|---|---|---|
| 5.1 | Booking history and detail views with ownership checks | You -> AI | Query projections and authorization | User sees only their own bookings | todo |
| 5.2 | Notification contract and after-commit delivery | Pair | Transaction events, failure isolation | Notification failure cannot undo a confirmed booking | todo |
| 5.3 | Audit events for admin and booking lifecycle actions | You | Audit modeling, PII minimization | Required state changes are attributable without secrets | todo |
| 5.4 | Admin summary queries and dashboard API | You | Aggregate queries and query performance | Counts/totals match data fixtures and remain role-protected | todo |
| 5.5 | Retry strategy/outbox only if required by delivery guarantees | You | At-least-once delivery, idempotent consumers | ADR explains need; failure/retry path has integration coverage | todo |
| 5.6 | Phase gate and explain-back | You | Post-commit side effects | Failure handling and guarantees are understood | todo |

## Phase 6 - Operational readiness and demo

**Goal:** Make the backend measurable, reviewable, and credible to demonstrate.

| # | Slice | Who | Learn | Done when | Status |
|---|---|---|---|---|---|
| 6.1 | Health/readiness and metrics for API, DB, booking outcomes and lock waits | Pair | Actuator, Micrometer, cardinality | Health endpoints are scoped; useful metrics appear | todo |
| 6.2 | Structured logs, request/correlation ID, secret/PII review | You | Operational debugging and secure logging | A request can be traced; secret scan/test finds no sensitive logs | todo |
| 6.3 | Security and authorization review | You | OWASP/API threat model | Findings are fixed or explicitly accepted and documented | todo |
| 6.4 | Run concurrent load experiment and describe limits honestly | You | Throughput, latency percentiles, bottlenecks | Reproducible command and environment/results are recorded | todo |
| 6.5 | Final demo, architecture diagram, and resume-claim audit | Pair | Technical communication | Demo proves a scarce-stock race; every resume claim matches repository evidence | todo |

**Final explain-back**
1. State the invariants and identify the code, constraints, and tests enforcing each.
2. Explain the booking transaction and retry behavior, including failure cases.
3. Explain what Redis accelerates and what happens when it is unavailable.
4. Identify the current bottleneck and what evidence would justify the next architecture change.