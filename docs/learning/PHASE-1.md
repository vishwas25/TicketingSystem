# Phase 1 - Learn and Build Identity and Authorization

This is the TicketingSystem-specific companion to [roadmap Phase 1](ROADMAP.md#phase-1---identity-and-authorization).
You write the business code; AI explains, reviews, and helps debug. This guide
provides reading, decisions, and checks, not a finished implementation.

## Before starting

- Complete the [Phase 0 study checklist](PHASE-0.md#before-phase-1) and
  explain-back. A passing foundation test suite is not the same as understanding
  Spring configuration, Maven, Flyway, or the test harness.
- Read [spec sections 2-5](../../SPECIFICATION.md): customer/admin flows,
  technical direction (including browser integration), baseline schema, and
  security rules. Use it rather than inherited examples for product decisions.
- Run `make verify` from the repository root to establish a passing baseline.
  Docker is required for integration tests; Testcontainers does not need
  `make up`. Use `make up` and `make run` for manual local API exploration.
- Study only the current slice's required reading before attempting its first
  small change. You do not need to memorize every linked document first.

## Resource map

Read the local primers first; consult the official references for specific
questions. Local primers inherited from SecondHandSell contain original-project
examples, slice numbers, and policies. They are concept resources, not the
TicketingSystem implementation contract. This guide and the spec take precedence.

| Slice | Required local reading, in order | Expected learning artifact |
|---|---|---|
| 1.1 | [Spring beans/injection](topics/spring-boot-basics.md), [JPA](topics/jpa-hibernate.md), [PostgreSQL](topics/postgres-schema.md), [Spring testing](topics/spring-testing.md), [Testcontainers](topics/testcontainers.md) | User column/type map and PostgreSQL save/reload test |
| 1.2 | [MVC](topics/spring-mvc.md), [validation](topics/bean-validation.md), [password hashing](topics/password-hashing.md), [errors](topics/error-handling.md), [Maven dependencies](topics/maven.md) | Registration request flow, validation table, and hash/duplicate tests |
| 1.3 | [Security](topics/spring-security.md), [JWT](topics/jwt.md), [browser auth](topics/browser-auth.md) | Authentication sequence and token validation/expiry tests |
| 1.4 | [Refresh rotation](topics/refresh-token-rotation.md), [transactions](topics/transactions.md), [Flyway](topics/flyway.md), [browser auth](topics/browser-auth.md) | Policy table, migration design, and replay/concurrency evidence |
| 1.5 | [Authorization](topics/authorization.md), [Security](topics/spring-security.md) | Caller/operation matrix and HTTP/service denial tests |
| 1.6 | [Errors](topics/error-handling.md), [OpenAPI](topics/openapi.md), [validation](topics/bean-validation.md) | Agreed error examples, documented security, and response assertions |
| 1.7 | [Spring testing](topics/spring-testing.md), [Maven](topics/maven.md), [CI](topics/github-actions.md) | Passing gate, notes, and unaided explain-back |

## What exists and what you will add

Existing anchors to inspect, not recreate:

- [Maven build](../../backend/pom.xml): MVC, JPA, validation, Flyway, PostgreSQL,
  testing, and springdoc dependencies already exist. Security/JWT dependencies
  are not installed yet; inspect dependency scope/version management before
  adding a library. Use Boot 3.5-compatible `jakarta.*` APIs and Security 6
  documentation, not tutorials using `javax.persistence` or
  `WebSecurityConfigurerAdapter`.
- [V1 users migration](../../backend/src/main/resources/db/migration/V1__create_users.sql):
  already creates `users`. Do not rewrite it or create another users table.
- [Integration-test base](../../backend/src/test/java/com/vishwas/ticketing/AbstractIntegrationTest.java):
  Spring Boot random HTTP port, test profile, and real PostgreSQL container.
- [Foundation tests](../../backend/src/test/java/com/vishwas/ticketing/PhaseZeroSmokeIT.java):
  examples of PostgreSQL constraints and HTTP checks. Preserve them; review
  expected health/docs access when adding the security chain.
- [Makefile](../../Makefile): supported build/test commands. Maven builds the
  backend; no frontend/npm setup is needed in Phase 1.

Add production code under the `com.vishwas.ticketing` feature packages and tests
in matching test packages. Use constructor injection and explicit Java code,
not Lombok. Suggested class names below describe responsibilities, not mandatory
architecture: do not generate empty layers or a generic base entity in advance.

## 1.1 - Map the existing user schema

**Read first:** JPA sections on entity anatomy, defaults, persistence context,
and repositories; PostgreSQL constraints/indexes; test isolation in Testcontainers.

1. Write down the existing mapping before adding annotations:

| Database column | Java concept to investigate | Important question |
|---|---|---|
| `id` | Generated `Long` identity | Why is an unsaved ID null? |
| `email` | Required string, maximum 254 characters | How will lookup match case-insensitive uniqueness? |
| `password_hash` | Required string, maximum 255 characters | Why must it never appear in DTOs or `toString`? |
| `role` | String-valued USER/ADMIN enum | Why not ordinal storage or a new role table? |
| `active` | Required boolean | How will inactive users be treated during authentication? |
| `created_at`, `updated_at` | `Instant` | Who initializes them and updates `updated_at`? |

2. Implement one entity, role representation, and repository. JPA requires a
   no-argument constructor; explore why a protected constructor is sufficient.
   Map V1 rather than introducing fields from inherited examples.
3. Decide timestamp handling and case-insensitive lookup. SQL defaults only
   apply when an insert omits a column; explicitly inserting null can violate a
   NOT NULL constraint. A `DEFAULT now()` does not maintain `updated_at` on updates.
4. Write a PostgreSQL test that saves, flushes, clears the persistence context,
   and reloads. Clearing prevents a first-level cache hit from masquerading as
   a database round trip. Exercise duplicate case-insensitive email and invalid
   role constraints; use direct SQL for a value the Java enum cannot represent.

**Do not import:** campuses, email verification, MODERATOR, suspension fields,
or a shared `BaseEntity` from marketplace examples. The actual roles are USER
and ADMIN. Email normalization is a local design decision; the database's
`lower(email)` uniqueness remains the concurrency backstop, not an existence check.

**Ready to move on:** You can explain every mapped field, Hibernate validation
passes, and a PostgreSQL test proves a genuine reload. No API is required yet.

## 1.2 - Register users safely

**Read first:** MVC request path/layer responsibilities, Bean Validation on
DTOs, password encoding/matching, and safe duplicate/validation errors.

1. Sketch JSON input/output and decide field constraints and duplicate-email
   behavior. Registration must not let a caller assign ADMIN or bypass active
   account policy. Do not use the entity as the request body.
2. Add a request DTO, validation, service, response DTO, and thin controller in
   small steps. Validation annotations and `@Valid` serve different purposes;
   explain both. The service owns normalization and persistence coordination.
3. Add the dependency/configuration needed for a `PasswordEncoder` now. The
   full security filter-chain work is slice 1.3. If you choose the security
   starter now, observe and test its default endpoint protection immediately;
   do not accidentally break registration, health, or Swagger. Alternatively,
   discuss using the crypto module first and the starter in 1.3.
4. Encode before saving and use `matches` to verify the hash in tests. Salted
   hashes need not be identical for identical inputs. Select encoder/password
   limits deliberately; inherited bcrypt-specific limits are not universal.
5. Test valid registration, bad/missing fields, case-insensitive duplicates,
   and stored hash behavior. A pre-insert existence check cannot prevent a race;
   also handle the unique constraint safely at the transaction boundary.

**Ready to move on:** A valid request creates a USER with a hash, invalid requests
are rejected, duplicates are safe, and no response/log exposes credentials.
Use an agreed minimal safe error response now; consolidate it in 1.6 rather
than postponing validation and leakage protection.

## 1.3 - Understand login and JWT authentication

**Read first:** Security filter chain/context, password matching, JWT claims,
and the browser-auth primer. Read the MVC/security error boundary too.

1. Draw two flows: login (credentials to authenticated identity to issued token)
   and later API request (bearer token to validation to security context).
2. Choose the login authentication approach and maintained JWT library with
   compatible dependencies. The inherited JJWT examples are not a pinned local
   dependency or a requirement to hand-write a filter. Explain the chosen
   framework integration before implementing it.
3. Decide accepted algorithms, signing configuration, issuer/audience policy,
   subject, expiry, and inactive-user behavior. Decoding is not validation;
   the server must verify signature and applicable claims. Do not put secrets
   or sensitive personal data in readable JWT claims.
4. Configure explicit route rules and an authenticated principal that is not
   a JPA entity. Preserve deliberate health/docs access. Never assume all
   `/auth/**` operations should be public.
5. Test real token issuance/validation through HTTP: success, wrong credentials,
   missing token, malformed token, tampered/invalid signature, and expiry.
   Use a controllable clock where possible rather than slow expiry sleeps.

**Decide before freezing auth:** Browser token transport/storage, refresh/logout,
origins and CSRF must be agreed for Phase 7. No UI needs to be built now.
Do not copy the inherited blanket CSRF-disable example for cookie endpoints.

**Ready to move on:** A real issued token authenticates a protected operation;
invalid tokens do not. You can identify who produces the HTTP 401 response.

## 1.4 - Refresh, rotation, and logout

**Read first:** Rotation and replay examples, transaction proxy/rollback
behavior, Flyway new-migration workflow, and browser credential transport.

1. Write a decision table for unknown, active, expired, revoked/replayed tokens,
   inactive users, logout, and simultaneous refreshes. Choose lifetimes,
   revocation scope, and whether families are needed; inherited 15-minute,
   seven-day, and family policies are examples, not approved local values.
2. Sketch the minimum schema required by that policy and add the next migration.
   Store only non-reversible refresh-token hashes. Learn why a random
   high-entropy token can use different hashing from a human password.
3. Implement atomic consume/rotate and revocation. Work through two concurrent
   requests with the same token before choosing a lock/conditional update.
   Test the chosen PostgreSQL behavior, not only a mocked repository.
4. Check failure semantics: if reuse triggers revocation followed by an
   exception, Spring's rollback must not silently undo the intended revocation.
   Assert persisted state after the transaction finishes.
5. Test issue/rotate/expire/replay/logout and concurrent use; check stored
   hashes differ from presented raw values without printing those values.
   Document whether already-issued access tokens remain valid after logout.

**Ready to move on:** Policy, HTTP behavior, migration, persisted revocation,
and concurrent outcomes agree. No hard-coded assumption about cookie versus
JSON refresh-token delivery is imported from the old primer.

## 1.5 - Role and ownership rules

**Read first:** Authentication versus authorization, authorities/roles,
service checks/method security, 401/403, and private-resource disclosure policy.

1. List existing operations against anonymous, USER, ADMIN, correct owner,
   and wrong owner where applicable. Do not invent event/booking endpoints
   before their phases just to fill an authorization matrix.
2. Enforce HTTP rules and service permissions; derive caller identity from the
   authenticated principal, never an untrusted request owner ID.
3. Test direct service denial as well as HTTP denial. Mocked principals can
   isolate authorization rules but do not prove JWT parsing; retain real-token
   integration tests from 1.3.

**Ready to move on:** Existing operations have tested rules. If no owned resource
exists yet, explicitly mark owner scenarios deferred to the first owning feature
and record its slice (booking ownership in 3.3/5.1); do not claim those cases
already pass. Future endpoints must extend the matrix when implemented.

## 1.6 - Stable errors and usable API docs

**Read first:** Problem Details, controller advice, security entry points/access
denied handlers, validation fields, and OpenAPI bearer security.

1. Agree a consistent error shape and examples for bad input, duplicate email,
   bad credentials, missing/invalid token, and insufficient permission.
2. Learn why controller advice alone cannot format every security-filter
   failure. Cover both boundaries without exposing stack traces or secrets.
3. Document DTOs, status codes, bearer requirements, and refresh transport in
   generated OpenAPI. Avoid marking public registration/login as bearer-required.
4. Test status, content type, and stable fields through HTTP and inspect the
   generated OpenAPI. Swagger authorization is a manual aid, not test evidence
   of browser cookie/CORS behavior.

**Ready to move on:** Expected failures have one documented contract, security
errors are included, and Swagger reflects the endpoints that actually exist.

## 1.7 - Verification and explain-back

Run from the repository root:

```bash
make test-unit
make test-it
make verify
```

During a slice, target the test you just wrote instead of waiting for the full
suite. These names are placeholders; substitute a class/method that exists:

```bash
cd backend
./mvnw -B test '-Dtest=YourUnitTest#yourMethod'
./mvnw -B verify -DskipUnitTests=true '-Dit.test=YourFeatureIT#yourMethod'
```

Use `verify`, not just `integration-test`, to evaluate Failsafe results. Do not
replace `skipUnitTests` with `skipTests`: the latter skips both suites. See
[Phase 0](PHASE-0.md) for Docker/IDE compatibility settings. Review reports under
`backend/target/surefire-reports` and `backend/target/failsafe-reports` on failure.

- [ ] Every implemented slice has its positive and failure tests.
- [ ] Entity mapping and refresh transaction/concurrency tests use PostgreSQL.
- [ ] Existing foundation tests still pass under security configuration.
- [ ] Browser transport/CORS/CSRF choices are recorded in the spec or an ADR.
- [ ] Dependencies are intentional; no keys, tokens, hashes, or personal data leak.
- [ ] OpenAPI/error contracts match real HTTP behavior.
- [ ] Relevant topic **My notes** sections contain your own explanations.
- [ ] `make verify` passes and the roadmap status reflects actual evidence.
- [ ] You can answer the Phase 1 explain-back without reading a script.

## Official references by task

These supplement the local reading rather than assigning entire manuals.
Use versions matching the project's Boot-managed dependencies; avoid copying
Spring Boot 4/Security 7 examples into this Boot 3.5 project.

| Use when | Reference and section to look for |
|---|---|
| Maven dependency/test questions (1.2/1.7) | [Maven lifecycle](https://maven.apache.org/guides/introduction/introduction-to-the-lifecycle.html), [dependency mechanism](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html) |
| Entity/repository questions (1.1) | [Hibernate 6.6 user guide](https://docs.jboss.org/hibernate/orm/6.6/userguide/html_single/Hibernate_User_Guide.html): entity types, enums, generated values; [Spring Data JPA 3.5](https://docs.spring.io/spring-data/jpa/reference/3.5/): query methods |
| Validation (1.2) | [Jakarta Bean Validation](https://beanvalidation.org/): constraints and object validation; [Spring Framework 6.2](https://docs.spring.io/spring-framework/reference/6.2/): MVC validation |
| Passwords/filter chain (1.2/1.3) | [Spring Security 6.5](https://docs.spring.io/spring-security/reference/6.5/): password storage, servlet architecture, authentication, JWT resource server, testing |
| Refresh/browser policy (1.3/1.4) | [OAuth security BCP, section 4.14](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14): refresh token protection (guidance, not a requirement to build an OAuth server); [browser primer references](topics/browser-auth.md#official-reading) |
| Permission/error questions (1.5/1.6) | [Spring Security 6.5](https://docs.spring.io/spring-security/reference/6.5/): request/method authorization, exception handling; [Problem Details RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html) |
| API documentation (1.6) | [springdoc](https://springdoc.org/): bearer security schemes, operation security, response schemas |

## Start with one small step

Begin with 1.1 only: read V1 and the JPA primer, write your proposed user
column-to-field mapping, then describe the save/flush/clear/reload test that
would disprove it. Bring that small attempt for review before moving to
registration, JWT, or refresh-token code.