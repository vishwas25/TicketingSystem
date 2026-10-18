# Learning Roadmap

This roadmap builds the ticketing application in verifiable slices. It borrows the
learning workflow from SecondHandSell, but the scope and decisions here are
specific to ticketing. The sibling implementation is a comparison point, not a
code template. The 21 inherited topic primers are copied locally under `topics/`
with original-project examples clearly labeled. Use the
[Phase 0 study guide](PHASE-0.md) for current files, commands, and differences.
Add any missing ticketing-specific primer just in time and record your own notes
after completing a slice. Phase 0's initial reference observations are in
[REFERENCE-STUDY.md](REFERENCE-STUDY.md); treat them as prompts to verify, not
as a substitute for doing slice 0.1 yourself.

This is a **backend-first application with a small React frontend planned for
Phase 7**. Phases 0-6 use Swagger UI, HTTP requests, and automated tests while
you learn the backend. After Phase 6 passes, build the browser UI against those
tested APIs. The frontend is approved scope, but is not implemented yet.

Keep the existing phase numbers: Phase 6 remains the backend readiness/demo
gate; Phase 7 adds the full application demo. This order avoids learning Spring,
database transactions, and React simultaneously. Before freezing authentication
contracts in Phase 1, account for browser token/cookie/CORS/CSRF behavior as
specified in the technical direction; this does not mean building UI early.

### Beginner map

An HTTP request normally travels through these responsibilities:

1. **Controller:** receives HTTP input and chooses the HTTP response. It should
	not contain booking or authorization business rules.
2. **Request/response DTO:** describes the JSON accepted or returned by the
	API. Validate request DTOs and do not return database entities directly.
3. **Service:** applies business rules and coordinates the work. This is where
	authorization and transaction boundaries belong.
4. **Repository/entity:** repositories use Spring Data JPA to read/write
	entities, which map to database tables. Flyway migrations, not Hibernate,
	create or change those tables.
5. **Database:** PostgreSQL is authoritative for persisted data and inventory.
	Redis is introduced later only for cache/rate-limit use cases.

In Phase 7, React renders the page and handles user actions in the browser.
JavaScript uses `fetch` to call these controllers over HTTP and displays JSON
responses. React never connects directly to PostgreSQL or decides whether a
booking succeeds. A disabled button is useful feedback, not a security check.

Keep feature code together under `backend/src/main/java/com/vishwas/ticketing/`
(for example, `auth`, `event`, or `booking`). Put unit tests in the matching
package under `backend/src/test/java` with `*Test.java`; put tests that need
Spring, HTTP, or PostgreSQL there with `*IT.java`. A slice usually adds only the
classes, migration, configuration, and tests needed for that behavior; do not
scaffold every future feature up front.

For each slice, “finished” means its listed code or study artifact exists, its
specific checks pass, relevant notes/spec decisions are updated, and you can
explain the result. The roadmap's **Done when** column is the short acceptance
summary; the guide below names the work and evidence in more detail. Run the
narrow test first; use `make verify` at phase gates.

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

### Phase 0 slice guide (study only)

Phase 0 is implemented; these are study tasks, not new feature work. Use the
[Phase 0 study guide](PHASE-0.md) for the exact files and commands.

#### 0.1 - Inspect the reference

- **Study:** Read the reference README and inspect its package tree, Maven
	build, configuration, booking request path, lock annotation/query, and tests.
	Draw or write the path from HTTP entry point through business logic and
	transaction to the inventory update. Record which class/method acquires the
	lock and what transaction contains it. The sibling repository is evidence to
	inspect, not source code to copy.
- **At the end:** `REFERENCE-STUDY.md` identifies the inspected evidence and
	gaps. You can explain what is protected by the lock and which behavior the
	tests do not establish.

#### 0.2 - State scope and invariant

- **Study:** Read the purpose, customer/admin flows, booking invariant, scope,
	and technical direction in `SPECIFICATION.md`. Trace what must happen on
	booking success, any failure, cancellation, simultaneous requests, and Redis
	outage.
- **At the end:** The documented scope agrees with the spec, and you can state
	that confirmed bookings cannot exceed inventory, a failed booking changes
	neither record nor inventory, and cancellation restores inventory at most
	once. No booking implementation is added in Phase 0.

#### 0.3 - Understand the Spring Boot build

- **Study:** Inspect the Maven wrapper and `pom.xml` to identify Java/Spring
	versions, dependencies, and test/build plugins. Trace the Spring Boot main
	class, package scanning, base/local/test configuration, and Actuator health
	endpoint. Run the study guide's build and health commands.
- **At the end:** A clean build succeeds, the local app starts, and health
	responds. You can explain what Maven compiles/tests/packages and how active
	Spring profiles and environment settings affect configuration.

#### 0.4 - Understand local services

- **Study:** Read `docker-compose.yml`, the environment example, and datasource
	settings. Identify PostgreSQL and Redis container ports versus host ports,
	health checks, the PostgreSQL volume, and Redis's ephemeral data. Run the
	documented start/stop cycle and confirm the app connects to PostgreSQL.
- **At the end:** Compose services become healthy and stop/start as documented;
	the app connects to the local database. You can explain which data survives a
	container restart and why Redis is not yet used by the app.

#### 0.5 - Understand migrations and schema validation

- **Study:** Read the V1 Flyway migration and inspect the resulting PostgreSQL
	table, indexes, and constraints. Trace Flyway's version/checksum tracking and
	Hibernate's `validate` setting. Note that user entities are intentionally a
	Phase 1 task and event/booking schemas come later.
- **At the end:** A fresh database migrates, existing SQL constraint tests
	pass, and no business entities have been added prematurely. You can explain
	why a future schema change requires a new migration rather than editing V1.

#### 0.6 - Understand the test harness

- **Study:** Read the unit test, integration-test base class, and smoke test.
	Identify JUnit, Spring test startup, Testcontainers PostgreSQL, random port
	configuration, and the `*Test.java` versus `*IT.java` naming convention.
	Run unit and integration tests separately using the documented Make targets.
- **At the end:** The unit test and all DB-backed/HTTP integration tests pass;
	you can explain why a real PostgreSQL container is used instead of H2 for
	database-specific behavior.

#### 0.7 - Understand verification and CI

- **Study:** Trace `make verify` into the Maven lifecycle and Surefire/Failsafe
	test phases, then inspect the CI workflow and its triggers. Distinguish
	compiling, unit tests, integration tests, and packaging.
- **At the end:** Local verification passes. The CI workflow is understood;
	hosted CI remains pending until the workflow runs after a push. You can say
	which command skips only unit tests and why `-DskipTests` is not equivalent.

#### 0.8 - Reproduce the safe failure

- **Study:** Inspect the checksum-mismatch fixture and test. Run the documented
	targeted test and follow how Flyway validates a deliberately different
	migration against a fresh test database. Do not edit the applied V1 file or
	alter the development database.
- **At the end:** The targeted test passes because it observes the expected
	checksum validation failure; the production migration remains unchanged. You
	can explain the key diagnostic and why an expected exception is a passing
	assertion.

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

Start with the [Phase 1 study and implementation guide](PHASE-1.md). It maps
existing files, readings, decisions, tests, and inherited-primer differences
to each slice. Complete the Phase 0 learning gate first.

| # | Slice | Who | Learn | Done when | Status |
|---|---|---|---|---|---|
| 1.1 | User and role schema, entities, repositories | You | JPA mapping, enums, uniqueness, constraints | PostgreSQL integration test saves and reloads a user | implemented; study and explain-back pending |
| 1.2 | Registration DTO, validation, service, password hashing | You | Bean Validation, service boundaries, password hashing | Valid registration succeeds; invalid/duplicate inputs fail safely | todo |
| 1.3 | Login and JWT issuance/validation | Pair | JWT claims, signing keys, expiry, filter chain | Valid token authenticates; invalid/expired tokens are rejected | todo |
| 1.4 | Refresh-token storage, rotation, logout/reuse policy | You | Token families, hashing, replay and concurrent refresh | Unit and integration tests cover rotate, expire, replay, logout | todo |
| 1.5 | Role and ownership authorization | You -> AI | 401 vs 403; service-layer rules; method security | Endpoint matrix covers anonymous, USER, ADMIN, and wrong owner | todo |
| 1.6 | Stable validation/auth error format and OpenAPI bearer scheme | Pair | Problem details, controller advice, API contracts | Swagger documents auth and errors; tests assert response shape | todo |
| 1.7 | Phase gate and explain-back | You | Threat boundaries and token lifecycle | Verification passes; explain request authentication end to end | todo |

### Phase 1 slice guide

#### 1.1 - User and role persistence

**Learning:** [Slice 1.1 guide](PHASE-1.md#11---map-the-existing-user-schema),
[JPA](topics/jpa-hibernate.md), [schema](topics/postgres-schema.md),
[testing](topics/spring-testing.md), [Testcontainers](topics/testcontainers.md).

- **Build:** Inspect V1 before coding. Map its existing `users` table with a
	JPA entity; represent the allowed roles; add a Spring Data repository and
	only the lookup methods needed by later registration/login work. Keep this
	in the auth/user feature package. Do not edit V1 or add a duplicate table.
- **Test:** A PostgreSQL integration test saves a user, reads it back, and
	checks the important mapped fields. Add tests that the database rejects an
	invalid role and duplicate case-insensitive email. Confirm Hibernate schema
	validation starts successfully.
- **At the end:** The existing migration and entity agree, the repository
	works against real PostgreSQL, and there is no registration or login API yet.

#### 1.2 - Registration

**Learning:** [Slice 1.2 guide](PHASE-1.md#12---register-users-safely),
[MVC](topics/spring-mvc.md), [validation](topics/bean-validation.md),
[password hashing](topics/password-hashing.md), [errors](topics/error-handling.md).

- **Build:** Add a registration request DTO, Bean Validation constraints,
	response DTO, service, and HTTP controller. The service normalizes/looks up
	email as decided by the spec, encodes the password through Spring Security's
	`PasswordEncoder`, and persists the new user. Controller returns a DTO, not
	the entity. Decide and document duplicate-email behavior before locking the
	API contract.
- **Test:** Unit-test service behavior where practical; HTTP/integration-test
	a valid registration, missing/malformed fields, duplicate email, and the
	stored value being a password hash rather than the submitted password.
	Ensure responses and logs do not reveal the password/hash.
- **At the end:** A valid request creates a user; invalid and duplicate cases
	fail in the agreed stable way; raw passwords are never persisted or returned.

#### 1.3 - Login and access JWT

**Learning:** [Slice 1.3 guide](PHASE-1.md#13---understand-login-and-jwt-authentication),
[Spring Security](topics/spring-security.md), [JWT](topics/jwt.md),
[browser authentication](topics/browser-auth.md).

- **Build:** Pair on adding Spring Security dependencies/configuration, a
	credential-authentication operation, access-token creation, and bearer-token
	validation in the request security chain. Decide claims, expiry, and signing
	configuration from the spec; keep signing secrets out of source control.
	Successful validation must establish the authenticated principal used by
	services/controllers.
- **Test:** Verify correct and incorrect credentials, malformed and expired
	tokens, and one protected request with and without a valid token. Do not
	print token contents in logs or test failure messages.
- **At the end:** A successful login produces a usable short-lived access
	token, and protected requests distinguish unauthenticated callers from valid
	authenticated callers. API paths/claims are documented rather than assumed.

#### 1.4 - Refresh, rotation, and logout

**Learning:** [Slice 1.4 guide](PHASE-1.md#14---refresh-rotation-and-logout),
[rotation](topics/refresh-token-rotation.md), [transactions](topics/transactions.md),
[Flyway](topics/flyway.md), [browser authentication](topics/browser-auth.md).

- **Decide first:** Specify refresh-token lifetime, rotation, reuse/replay,
	logout, and concurrent-use behavior before implementing storage. These are
	intentional design questions, not defaults this guide answers for you.
- **Build:** Add a new Flyway migration for refresh-token state; never change
	an applied migration. Persist only a non-reversible token hash and the
	minimum state/timestamps needed by the chosen policy. Add service operations
	for refresh and revocation, with a transaction/constraint strategy that
	prevents one token from being successfully rotated twice.
- **Test:** Cover initial issue, successful rotation, expiry, logout, replay,
	and two concurrent refresh requests against PostgreSQL where persistence or
	locking matters. Assert the exact result selected in the spec.
- **At the end:** Refresh/logout behavior matches the written policy, tokens
	cannot be recovered from storage, and replay/concurrency outcomes are tested.

#### 1.5 - Role and ownership authorization

**Learning:** [Slice 1.5 guide](PHASE-1.md#15---role-and-ownership-rules),
[authorization](topics/authorization.md), [Spring Security](topics/spring-security.md).

- **Build:** List each existing operation and expected caller (anonymous,
	USER, ADMIN, resource owner). Configure HTTP access rules in Spring Security
	and add service-level checks for business permissions/ownership. Use the
	authenticated principal to determine identity; never trust a user ID from
	request JSON as proof of ownership.
- **Test:** Create an authorization matrix and test allowed and denied cases
	through HTTP, including anonymous, wrong role, correct role, and wrong
	owner wherever an owned resource exists. Include service tests for rules
	that must hold even if the service is called outside a controller.
- **At the end:** Each operation has an explicit access rule, and tests prove
	authorization is not only enforced by the HTTP layer.

#### 1.6 - Error format and OpenAPI security

**Learning:** [Slice 1.6 guide](PHASE-1.md#16---stable-errors-and-usable-api-docs),
[errors](topics/error-handling.md), [OpenAPI](topics/openapi.md),
[validation](topics/bean-validation.md).

- **Build:** Define a stable error response for validation, authentication,
	and authorization failures. Map expected failures centrally without
	returning stack traces, credentials, token values, or implementation
	details. Configure the OpenAPI bearer scheme and mark protected operations
	with their expected errors.
- **Test:** HTTP tests assert status, content type, and stable error fields for
	representative invalid input, missing/invalid credentials, and insufficient
	permission. Inspect generated OpenAPI/Swagger to confirm the bearer scheme
	and security requirements appear.
- **At the end:** A client can understand expected errors and authenticate
	using the documented API contract; failure responses do not leak internals.

#### 1.7 - Phase gate

**Learning:** [Slice 1.7 guide and commands](PHASE-1.md#17---verification-and-explain-back),
[testing](topics/spring-testing.md), [Maven](topics/maven.md),
[CI](topics/github-actions.md).

- **Do:** Run focused tests for the changed slice as you work, then run
	`make verify`. Review dependencies/configuration and the flow from request
	through token parsing, user identity, authorization, service, and database.
	Update relevant topic notes and status only when evidence exists.
- **At the end:** Unit and PostgreSQL integration suites pass, the Phase 1
	explain-back is clear without notes, and no Phase 2 work has started early.

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

### Phase 2 slice guide

#### 2.1 - Event schema and persistence

- **Decide first:** Define event fields and constraints in the spec before
	creating a table: lifecycle states, event and booking-window instants,
	inventory capacity/available-count relationship, exact price representation
	and currency, and query-driven indexes. Use `Instant` and exact money, not
	local persisted times or floating point.
- **Build:** Add the next Flyway migration, event entity, state representation,
	and repository under the event feature package. Do not edit prior migrations
	or let Hibernate create/update schema.
- **Test:** Start against PostgreSQL; verify migration, Hibernate validation,
	save/reload mappings, and database constraints for required fields, inventory,
	state, and price/currency as designed.
- **At the end:** A fresh PostgreSQL database has the event schema and entity
	mapping matches it; the spec records the decisions behind constraints.

#### 2.2 - Admin draft create/update

- **Build:** Add validated create/update request DTOs, response DTOs, event
	service methods, and controller endpoints. New events start in the agreed
	draft state. Updates must not provide an unrestricted way to change lifecycle
	state or bypass validation. Protect operations for admins.
- **Test:** Exercise valid create/update and persisted values; invalid/missing
	fields; anonymous access; USER denial; and ADMIN success. Use the real DB for
	persistence assertions and HTTP tests for status/response behavior.
- **At the end:** An admin can manage drafts through the documented API, while
	non-admins cannot and entity objects do not escape the API.

#### 2.3 - Publish and cancel transitions

- **Decide first:** Write the legal state transitions and prerequisites in the
	spec, including repeated transition behavior and any impact cancellation has
	on future bookings (booking policy is finalized in Phase 3).
- **Build:** Add explicit service operations and HTTP endpoints for publishing
	and cancelling. Do not accept arbitrary state changes via the draft update
	request. Apply admin authorization and validate current state before update.
- **Test:** Test every allowed and rejected transition, missing event, repeated
	request, and relevant concurrent change using PostgreSQL where transaction
	behavior is involved.
- **At the end:** The event cannot enter an illegal state through the API and
	the legal lifecycle is recorded in tests and the spec.

#### 2.4 - Public event discovery

- **Build:** Add public detail/list service queries, request query parameters,
	response DTOs, filters and pagination. Only published events are visible.
	Choose deterministic ordering and page defaults/limits as documented; avoid
	loading related rows one-by-one while mapping a list.
- **Test:** Check matching/nonmatching filters, empty result, page boundaries,
	ordering, public visibility of published events, hiding drafts/cancelled
	events, and query count/N+1 behavior for list responses.
- **At the end:** A client can browse and page through only public events with
	stable response metadata and no excessive per-row queries.

#### 2.5 - API contract and phase gate

- **Build/review:** Document event operations, fields, validation, pagination,
	errors, and security in OpenAPI. Check that examples reflect the DTOs and
	actual lifecycle rules; update the event/API topic notes.
- **Test:** Verify Swagger/OpenAPI output and the anonymous/USER/ADMIN matrix;
	run `make verify` after focused tests pass.
- **At the end:** Phase 2's DB, HTTP, and unit tests pass, endpoints are
	understandable from Swagger, and the event explain-back is complete.

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

### Phase 3 slice guide

#### 3.1 - Decide booking rules before coding

- **Decide/write:** Specify when booking opens/closes and whether the boundary
	is inclusive; minimum/maximum quantity; duplicate booking behavior; what
	cancellation means and its cutoff; how a client retries after timeout; and
	whether/how an idempotency key is used. Add examples for success and each
	rejection to `SPECIFICATION.md` before schema/API choices depend on them.
- **Test plan:** For each rule, write at least one expected-success and one
	expected-failure case. Include the uncertain response-loss case: the server
	may commit while the client does not receive the response.
- **At the end:** The policy is explicit and internally consistent in the spec
	and test cases. No booking behavior is implemented in this decision slice.

#### 3.2 - Booking database model

- **Build:** Add a new migration for booking records and any idempotency state
	required by 3.1. Define relationships, status values, quantities, timestamps,
	unique constraints, and indexes to support the agreed rules. Add entity and
	repository mappings; do not edit old migrations or let Hibernate mutate the
	schema.
- **Test:** Against PostgreSQL, apply the migration, start with Hibernate
	validation, save/reload representative rows, and assert database constraints
	reject invalid quantities/states or duplicate keys as designed.
- **At the end:** Database structure represents the documented booking rules,
	and PostgreSQL verifies both schema and JPA mapping.

#### 3.3 - Booking validation and read operations

- **Build:** Add request/response DTOs and service/repository operations needed
	to validate a booking request and privately retrieve a user's booking data.
	Enforce event state/window, quantity, existing-booking/retry policy, and
	ownership in service logic. Avoid exposing entity objects from controllers.
- **Test:** Unit-test individual rule decisions; HTTP/DB-test invalid quantity,
	closed window, unavailable event, duplicate/retry cases, missing booking,
	and attempts to access another user's booking. Do not test a race here as a
	substitute for 3.6.
- **At the end:** Ordinary valid/invalid requests follow the spec and private
	booking data is protected; lock/concurrency correctness remains for later
	slices.

#### 3.4 - Prove the PostgreSQL lock behavior

- **Build:** Add the repository operation that locks the inventory-bearing
	event row and call it from a transaction. Document why the lock is acquired
	before checking/updating inventory and keep the order of database locks
	consistent across booking/cancellation operations.
- **Test:** Use real PostgreSQL with two transactions/connections. Hold the
	first transaction after acquiring the lock; verify the second transaction
	cannot pass that lock until the first commits or rolls back, then verify it
	proceeds. Ensure the test has bounded waits/timeouts so a broken test cannot
	hang indefinitely. H2/unit tests are not evidence for PostgreSQL locking.
- **At the end:** The integration test demonstrates serialization and lock
	lifetime, and the code path acquires the lock inside the intended transaction.

#### 3.5 - Create booking atomically

- **Build:** Implement the create service operation as one transaction: obtain
	the database lock, re-check event/window/stock after acquiring it, deduct
	inventory, create the booking, and apply the chosen retry/idempotency rule.
	Return a response DTO only after the operation succeeds.
- **Test:** Verify success updates booking and inventory together. Force booking
	persistence to fail after the inventory change and assert after rollback
	that neither booking nor deduction remains. Also test insufficient inventory
	leaves both unchanged.
- **At the end:** Database state is all-or-nothing for booking creation, and
	the response matches committed state.

#### 3.6 - Parallel booking invariant

- **Build/test:** Create a PostgreSQL integration test with known initial
	inventory smaller than total requested parallel quantity. Synchronize
	competing calls so they contend at approximately the same time; use bounded
	executor/barrier waits and collect each outcome. Exercise the production
	service/HTTP path as appropriate, not a separately reimplemented algorithm.
- **Assert:** Sum of successful booking quantities never exceeds initial
	inventory; available inventory never becomes negative; persisted successful
	bookings and remaining inventory agree; rejected callers receive the
	documented outcome.
- **At the end:** The invariant has repeatable evidence under real PostgreSQL
	contention, not only sequential or mocked tests.

#### 3.7 - Cancellation and inventory release

- **Build:** Add an owner-authorized cancellation operation that checks policy,
	changes booking state, and restores inventory transactionally. Make inventory
	restoration conditional on the booking actually transitioning from active
	to cancelled, rather than restoring on every request.
- **Test:** Cover success, after-cutoff/invalid-state rejection, repeat request,
	wrong owner, and two concurrent cancellation attempts. Assert a booking
	releases its quantity exactly once and never makes inventory exceed the
	allowed total.
- **At the end:** Cancellation is safe to retry and concurrent duplicate
	cancellations cannot return tickets twice.

#### 3.8 - Compare inventory approaches

- **Study/record:** Compare the row-lock implementation with a PostgreSQL
	conditional atomic update that changes inventory only when sufficient stock
	remains. Compare correctness reasoning, query complexity, contention, and
	any measured timing in the same environment. Do not replace the selected
	mechanism without rerunning its invariant tests.
- **At the end:** A short architecture decision record (ADR) captures options,
	evidence/assumptions, selected approach, and known trade-offs. The record
	makes no unsupported production throughput claim.

#### 3.9 - Phase gate

- **Do:** Run the targeted rollback, locking, parallel-booking, and cancellation
	tests, then `make verify`. Review status transitions and every path that
	changes inventory. Update topic notes and the ADR/spec if evidence changed a
	decision.
- **At the end:** Full verification passes, the explain-back can trace the
	transaction and invariant, and Redis work has not started before this gate.

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

### Phase 4 slice guide

#### 4.1 - Explore Redis without app changes

- **Study:** Use `make redis-cli` to create/read/delete keys, try a suitable
	data structure, set/inspect expiry, and observe behavior when Redis stops or
	restarts. Relate each experiment to cache or rate-limit data.
- **At the end:** Your notes explain key/expiry behavior and why Redis is
	disposable support data, while PostgreSQL remains the inventory authority.
	No production application code is added in this slice.

#### 4.2 - Cache published event reads

- **Decide/build:** Choose which read responses are safe to cache, cache key
	contents, TTL, and invalidation behavior for draft update/publish/cancel.
	Configure Spring's cache integration or the chosen existing mechanism; keep
	cache access out of booking inventory decisions.
- **Test:** Prove first read loads from DB, repeated read uses cache, expiry or
	mutation prevents stale results beyond the documented bound, and Redis
	outage falls back to PostgreSQL. Include a check that booking still reads
	authoritative inventory from PostgreSQL.
- **At the end:** Event reads gain a bounded cache behavior with tested
	invalidation/fallback; cache data cannot authorize a booking.

#### 4.3 - Atomic rate limit

- **Decide first:** Document the limited operations, quota/window, identity
	used for a key, reset behavior, and whether requests are allowed or rejected
	during Redis outage. Avoid storing sensitive identity data directly in keys.
- **Build:** Implement one atomic check-and-count operation (such as a Redis
	script), connect it to the selected HTTP/service boundary, and return a
	documented HTTP 429 when the quota is exceeded.
- **Test:** Send synchronized parallel requests and assert exactly the allowed
	number pass, excess requests get 429, expiry/reset works, and outage behavior
	matches the decision.
- **At the end:** Concurrent requests cannot bypass the limit through a
	check-then-increment race, and the failure policy is explicit.

#### 4.4 - Measure cache impact

- **Run/record:** Compare the same representative event-read workload with
	cache disabled/enabled. Record environment, request count, database query
	count where available, latency, cache TTL, and limitations. Keep correctness
	tests separate from performance observations.
- **At the end:** The notes contain reproducible steps and honest results; no
	local measurement is presented as a production guarantee.

#### 4.5 - Phase gate

- **Do:** Run cache/rate-limit tests, booking concurrency tests with Redis
	available and unavailable, then `make verify`. Check logs and errors for
	graceful failure consistent with the selected outage policy.
- **At the end:** Booking correctness and PostgreSQL inventory invariants pass
	without Redis; the explain-back identifies exactly what Redis does and does
	not decide.

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

### Phase 5 slice guide

#### 5.1 - Booking history and detail

- **Build:** Add authenticated list and detail operations in the booking
	feature. Determine the current user from the authenticated principal, query
	only that user's records, and return DTOs/projections rather than entities.
	Define ordering and pagination if the history can grow without bound.
- **Test:** Verify empty and non-empty history, detail for an owned booking,
	missing booking, and attempts to list/read another user's booking. Assert
	response fields do not expose internal or unrelated user data.
- **At the end:** A user can inspect only their own booking history through
	documented endpoints, and ownership is enforced in the service.

#### 5.2 - After-commit notification contract

- **Build:** Define the notification data/recipient contract and trigger it
	only after a booking transaction commits. Use an in-process test/fake sender;
	production email delivery is out of scope unless separately agreed. Do not
	make a network call while holding the inventory transaction open.
- **Test:** Verify no notification is sent for a rolled-back booking, one is
	attempted after a committed booking, and a sender failure cannot undo the
	committed booking or inventory result.
- **At the end:** Notification behavior is separated from the booking
	transaction, and its failure guarantee is documented and tested.

#### 5.3 - Audit records

- **Decide/build:** List which admin and booking lifecycle actions must be
	attributable and what minimal fields are needed (for example actor, action,
	target, timestamp). Select a persistence/event approach consistent with the
	required retention and transaction behavior; minimize or omit personal data.
- **Test:** Perform each audited action and verify the corresponding record and
	actor/action/target. Check that failed actions are handled as specified and
	that passwords, hashes, access/refresh tokens, and unnecessary PII never
	appear in audit data or logs.
- **At the end:** Required actions have useful, minimal audit evidence with
	explicit failure and data-retention assumptions.

#### 5.4 - Admin summary API

- **Decide/build:** Define which counts/totals administrators need and their
	time/status meanings before writing aggregate queries. Add service/repository
	queries and response DTOs, plus admin-protected HTTP endpoints. Avoid loading
	every booking into Java if the database can calculate the aggregate.
- **Test:** Seed known events/bookings and assert exact counts/totals; include
	relevant states/time boundaries and empty data. Verify anonymous and USER
	callers are denied while ADMIN succeeds.
- **At the end:** Documented summary values match known PostgreSQL fixtures and
	are available only to admins.

#### 5.5 - Decide whether an outbox/retry is needed

- **Decide first:** Describe a concrete failure (for example, process exits
	after booking commit but before notification delivery) and decide whether the
	project requires delivery despite that failure. Record delivery semantics,
	duplicate handling, retry limits, monitoring, and complexity in an ADR.
- **Build only if justified:** If durable delivery is required, implement an
	outbox record in the booking transaction and a separate dispatcher/retry
	path. Make delivery idempotent; do not introduce Kafka or a microservice.
	If not required, record why the simpler after-commit behavior is sufficient.
- **Test:** If implemented, prove a pending record survives process-level
	delivery failure, can be retried, and duplicate delivery does not duplicate
	the effect. If not implemented, review and accept the documented loss window.
- **At the end:** There is an evidence-based decision either way; any selected
	retry mechanism has a tested recovery and duplicate policy.

#### 5.6 - Phase gate

- **Do:** Run ownership/role, audit, notification failure, and any outbox tests,
	then `make verify`. Inspect transaction boundaries and confirm secondary work
	cannot undo a successful booking. Update notes and the ADR/spec as needed.
- **At the end:** Full verification passes and you can explain what is
	synchronous, what happens after commit, and how each failure is handled.

## Phase 6 - Operational readiness and demo

**Goal:** Make the backend measurable, reviewable, and credible to demonstrate.

| # | Slice | Who | Learn | Done when | Status |
|---|---|---|---|---|---|
| 6.1 | Health/readiness and metrics for API, DB, booking outcomes and lock waits | Pair | Actuator, Micrometer, cardinality | Health endpoints are scoped; useful metrics appear | todo |
| 6.2 | Structured logs, request/correlation ID, secret/PII review | You | Operational debugging and secure logging | A request can be traced; secret scan/test finds no sensitive logs | todo |
| 6.3 | Security and authorization review | You | OWASP/API threat model | Findings are fixed or explicitly accepted and documented | todo |
| 6.4 | Run concurrent load experiment and describe limits honestly | You | Throughput, latency percentiles, bottlenecks | Reproducible command and environment/results are recorded | todo |
| 6.5 | Final demo, architecture diagram, and resume-claim audit | Pair | Technical communication | Demo proves a scarce-stock race; every resume claim matches repository evidence | todo |

### Phase 6 slice guide

#### 6.1 - Health and metrics

- **Build:** Review existing Actuator probes before adding anything. Define
	which dependencies affect readiness and liveness, then expose a small useful
	set of Micrometer metrics for request/booking outcomes and lock waits if
	available. Use bounded labels; never tag metrics with user IDs, emails, or
	other unbounded/sensitive values.
- **Test:** Check health/readiness/liveness with healthy and unavailable
	dependencies according to the chosen policy. Inspect metric output and
	verify expected meters appear without high-cardinality labels.
- **At the end:** Operators can distinguish app liveness from dependency
	readiness and inspect useful, bounded metrics.

#### 6.2 - Structured logs and request IDs

- **Build:** Define how a request/correlation ID is accepted or generated,
	placed in request context, returned to the caller if appropriate, and included
	in structured logs. Add only lifecycle fields useful for diagnosis.
- **Test/review:** Exercise a request and confirm the same ID can connect its
	relevant logs. Review log output and automated checks for passwords, hashes,
	access/refresh tokens, and unnecessary personal data.
- **At the end:** A request can be traced without exposing secrets or
	collecting unnecessary personal data.

#### 6.3 - Security and authorization review

- **Review:** Inspect registration/password handling, token validation,
	authorization at service boundaries, ownership checks, validation, error
	responses, configuration/secrets, and logging. Use a practical API security
	checklist and record findings with severity and evidence.
- **Fix/test:** For each in-scope finding, make a focused fix and add a
	regression test. For accepted risks, document impact, reason, and any
	compensating control rather than silently leaving the finding open.
- **At the end:** Findings are fixed or explicitly accepted with rationale;
	the review record points to tests/evidence.

#### 6.4 - Concurrent load experiment

- **Prepare/run:** Specify hardware/runtime, database, initial stock, request
	count, concurrency, and command/configuration so another person can repeat
	the experiment. Measure throughput and latency percentiles and collect
	booking outcomes. Keep the workload bounded and do not send real customer
	data.
- **Assert/record:** Recheck inventory and booking invariants after load; record
	results, bottleneck evidence, and limitations. Separate correctness claims
	from performance observations.
- **At the end:** A documented experiment is reproducible and demonstrates
	both behavior and honest performance limits, without implying production
	capacity.

#### 6.5 - Demo, diagram, and claims audit

- **Prepare:** Create a short API-driven walkthrough using the documented local
	commands and Swagger/HTTP requests. Show registration/authentication, event
	discovery, a booking, and competing requests for scarce PostgreSQL inventory.
	Capture database/test evidence for the concurrency result.
- **Review:** Update the architecture diagram to match shipped code. Check
	each resume/project claim against implementation, tests, and recorded
	measurements; remove or qualify claims without evidence.
- **At the end:** Another person can run the demo from written steps, the
	diagram matches the repository, and every claim is evidence-backed.

**Final explain-back**
1. State the invariants and identify the code, constraints, and tests enforcing each.
2. Explain the booking transaction and retry behavior, including failure cases.
3. Explain what Redis accelerates and what happens when it is unavailable.
4. Identify the current bottleneck and what evidence would justify the next architecture change.

## Phase 7 - Basic React frontend and full application demo

**Goal:** Learn how a browser application uses the backend by building a small,
usable ticket-booking UI. Start only after Phase 6's gate and explain-back pass.
No frontend scaffolding or dependency installation is part of this planning edit.

**Keep it small:** Use JavaScript/JSX, React, Vite, basic CSS, React Router, and
`fetch`. Start with component state; use context only for genuinely shared
session state. Do not add TypeScript, Redux, Next.js, payments, seat selection,
real-time updates, or an elaborate UI framework. The first screen is event
discovery, not a marketing landing page.

**Who writes:** You write the first component, form, request, and test. AI gives
hints and reviews small steps; it does not generate the application for you.
For **You -> AI** slices, request repetition only after you understand your
first implementation. Add short HTML/CSS/JavaScript/React topic notes just in
time; the existing backend primers do not cover these new topics.

| # | Slice | Who | Learn | Done when | Status |
|---|---|---|---|---|---|
| 7.1 | Browser and JavaScript foundations | You | HTML, CSS, modules, promises, JSON, HTTP | Small exercises work and you can explain request-to-render flow | todo |
| 7.2 | React setup and navigation | Pair | Node/npm, Vite, JSX, components, props, state, routes | UI starts, navigation works, lint/build pass | todo |
| 7.3 | Public event list and details | You | Effects, fetch, query parameters, loading/error states | Real API discovery works with filters and pagination | todo |
| 7.4 | Registration, login, session, logout | Pair | Controlled forms, context, browser security | Auth works according to the Phase 1 browser contract | todo |
| 7.5 | Book tickets and show outcome | You | Mutation requests, pending state, retries | UI shows committed booking or documented failure accurately | todo |
| 7.6 | My bookings and cancellation | You -> AI | Private queries, confirmation, refreshed state | User can view/cancel only their own bookings | todo |
| 7.7 | Minimal admin event tools and summary | You -> AI | Reusable forms, date conversion, role-aware navigation | Admin manages events and sees summary; USER is denied | todo |
| 7.8 | Usability, accessibility, responsive layout | You | Semantic HTML, focus, forms, CSS layout | Core flows work by keyboard on mobile and desktop | todo |
| 7.9 | Frontend and integrated browser tests | Pair | Vitest, React Testing Library, Playwright, CI | Focused UI tests and real-backend browser flows pass | todo |
| 7.10 | Full project gate, demo, explain-back | You | Reproducible setup, architecture, evidence | Both stacks pass documented gates and full demo works | todo |

### Phase 7 slice guide

#### 7.1 - Learn browser basics before React

- **Study/practise:** Learn HTML headings, links, labels, inputs, forms, and
	buttons; CSS selectors, box model, flex/grid, and responsive widths. Practise
	JavaScript objects/arrays, functions, imports/exports, `map`, `async`/`await`,
	promises, `try`/`catch`, and JSON. Keep small exercises separate from shipped
	business screens; this is not a second application.
- **Try:** Use browser developer tools to inspect an element and an HTTP
	request. Call an existing public API and inspect status, JSON, and a failed
	response. Explain that `fetch` does not throw automatically for HTTP 4xx/5xx.
- **At the end:** Your notes explain browser versus server responsibilities
	and how an HTTP response becomes visible content. You can write a small
	function to transform event data without copying a React solution.

#### 7.2 - Create the React workspace and page shell

- **Build:** Create `frontend/` with Vite's React JavaScript template. Pin a
	supported Node version at implementation time, commit npm's lockfile, and
	ignore dependencies/build output. Explain `package.json`, npm scripts,
	`index.html`, the React entry point, JSX, and component files. Maven and npm
	have separate dependency/build lifecycles.
- **Build:** Add a simple header/navigation and React Router routes for event
	discovery/detail, register/login, my bookings, and admin views. Start with
	placeholders only for later slices. Add a not-found route and basic CSS;
	do not create a component abstraction for every element.
- **Configure/test:** Document local API origin/proxy settings and frontend
	environment variables. Anything exposed to Vite/browser code is public:
	never place signing keys or database credentials there. Start both servers
	and verify navigation, back/forward, deep-link refresh, lint, and build.
- **At the end:** The browser opens a working app shell, not the default Vite
	demo. Exact Node/npm startup commands and URLs are documented.

#### 7.3 - Read and display public events

- **Build:** Add a small API helper for base URL, JSON parsing, HTTP errors,
	and request cancellation. Render event list/detail using real backend DTOs,
	with filters and pagination matching Phase 2. Learn effects by explaining
	dependencies and cleanup; avoid stale responses replacing newer search
	results or an endless fetch/render loop.
- **Build states:** Show loading, empty results, network/server error, missing
	event, and populated results. Format money using the API's exact amount and
	currency contract without calculating booking charges using floating point.
	Display event times with an explicit timezone convention.
- **Test:** Change filters/pages, open details directly, simulate slow/failed
	requests, and confirm drafts/cancelled events are not publicly exposed.
- **At the end:** A visitor can browse real published events without logging
	in; each request state has clear feedback and no fabricated success data.

#### 7.4 - Authentication in the browser

- **Review first:** Revisit the Phase 1 browser contract: access/refresh token
	handling, reload/expiry behavior, logout, cookie attributes if used, allowed
	origins, and CSRF. Resolve any mismatch in the spec/backend with tests before
	bypassing it in JavaScript. No refresh token goes into localStorage.
- **Build:** Add labelled controlled registration/login forms, field/general
	errors, pending state, and small shared session state. Use the real API,
	clear passwords after use, attach credentials only as specified, and add
	logout. Define whether reload restores a session or requires login. Handle
	expiry without infinite refresh/retry loops.
- **Test:** Cover registration, invalid credentials, expiry, reload, logout,
	and access to a private page while logged out. Hidden links/route guards are
	convenience only; backend authorization must still reject direct requests.
- **At the end:** Session behavior matches the server's documented policy and
	credentials never appear in application logs, URLs, or public build settings.

#### 7.5 - Book from an event detail screen

- **Build:** Add a quantity input and booking command on event detail; require
	authentication before mutation. Show validation and pending feedback, then
	render the server's booking result or documented rejection. Refresh relevant
	availability after the outcome; never decrement a browser-only counter as
	proof a booking succeeded.
- **Handle retries:** Follow Phase 3's idempotency contract for double clicks,
	timeouts, and response loss. Do not blindly retry a mutation with a new key
	or display a timeout as certain failure if the server may have committed.
- **Test:** Cover valid quantity, invalid quantity, sold out, closed window,
	rapid repeat click, and uncertain/network outcome. Test a race between two
	users for limited stock without treating the UI as an inventory lock.
- **At the end:** A user can book through the UI and see an accurate result;
	PostgreSQL and the backend still decide every inventory change.

#### 7.6 - My bookings and cancellation

- **Build:** Show the current user's history/details with loading, empty,
	failed, and populated states. Reuse the Phase 5 queries. Add a clear cancel
	confirmation and pending/result feedback, reflecting the server's cutoff and
	state rules. Refresh booking status after cancellation.
- **Test:** Cover no history, multiple bookings, successful/too-late/repeated
	cancellation, expired session, and direct navigation to another user's
	booking ID. Verify the server rejects access, not merely that UI hides it.
- **At the end:** The user can manage their own bookings, and retries cannot
	make the UI claim inventory was returned twice.

#### 7.7 - Minimal administrator tools

- **Build:** Add a straightforward admin event list and draft create/edit
	form, explicit publish/cancel actions, and the existing summary response.
	Reuse form patterns after your first example. Do not build a charting suite
	or duplicate backend rules in a client-only implementation.
- **Handle data:** Convert browser date inputs deliberately to/from the API's
	instants; state the input/display timezone so local wall-clock values are
	not silently sent as UTC. Preserve exact amount/currency representations.
	Define a safe local admin-account setup using the backend policy; never add
	a public self-registration field that grants ADMIN.
- **Test:** Exercise legal/illegal transitions, validation, failed requests,
	anonymous/USER direct navigation and API denial, and successful ADMIN work.
- **At the end:** An admin can operate the existing lifecycle and summaries
	from simple screens while services remain the permission/rule authority.

#### 7.8 - Make the small UI usable

- **Review/build:** Use semantic HTML, visible labels, readable contrast,
	keyboard-accessible navigation/forms, visible focus, and understandable
	errors/status announcements. Use basic CSS for small and large viewports;
	avoid overflowing tables, hidden commands, and overlapping text.
- **Test:** Walk the full customer flow using only a keyboard; check phone
	and desktop layouts, long event titles, slow responses, and server errors.
	Keep loading controls stable and prevent accidental repeated submissions.
- **At the end:** Core screens are usable on desktop/mobile without requiring
	complex visual effects or a design framework.

#### 7.9 - Tests and combined verification

- **Build alongside earlier slices:** Use Vitest and React Testing Library for
	focused component/request-state tests, mocking HTTP only at that boundary.
	Add Playwright for a small set of browser flows against the actual backend
	and isolated test data, not the personal development database.
- **Cover:** Browse, register/login, book, inspect/cancel a booking, and admin
	permission denial. Include at least one server rejection in the UI. Backend
	PostgreSQL concurrency tests remain the proof of inventory correctness;
	mocked UI tests cannot replace them.
- **Gate:** Define/document npm scripts for lint, non-watch tests, production
	build, and browser tests. Extend CI and add a clearly named combined Make
	target that runs frontend checks plus the existing backend `make verify`.
	Document any server/DB/Redis setup needed by browser tests and cleanup.
- **At the end:** Both independent builds and the integrated browser flows
	pass reproducibly. These frontend commands/targets are planned, not available
	in Phase 0.

#### 7.10 - Full application demo and explain-back

- **Do:** Run the combined gate; document how to start dependencies, backend,
	and frontend from a fresh checkout. Update README/architecture and demonstrate
	visitor discovery, customer login/book/cancel, admin event actions, and a
	documented sold-out failure. Keep the backend scarce-stock concurrency demo.
- **At the end:** You can demonstrate the app without relying on undocumented
	manual database edits, explain a click's full browser-to-PostgreSQL path, and
	distinguish React feedback from server-enforced security/correctness.

**Frontend explain-back**
1. What are props and state, and what makes React render again?
2. Why can an effect issue repeated or stale requests, and how do you prevent it?
3. What happens from clicking Book through HTTP, transaction, and rendered result?
4. Why do hidden admin links and disabled booking buttons not enforce security?
5. Where are credentials held, and what happens on expiry, reload, and logout?
6. How do npm/Vite checks differ from Maven and PostgreSQL integration tests?