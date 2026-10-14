# Phase 0 - Study the Working Foundation

The Phase 0 implementation is ready for you to study. You asked AI to implement
this phase; from Phase 1, you write the business code while we pair. Implementation
completion does not mark your study or explain-back as complete.

## Start here

Requires JDK 21, Docker with Compose, and Make. Maven is downloaded by the
wrapper on first use; a system Maven installation is not needed afterwards.

Run from the TicketingSystem repository root:

```bash
make up
make verify
make run
```

The app runs on http://localhost:8081. In another terminal:

```bash
curl -f http://localhost:8081/actuator/health
curl -f http://localhost:8081/actuator/health/readiness
curl -f http://localhost:8081/actuator/health/liveness
curl -f http://localhost:8081/v3/api-docs
```

Swagger UI: http://localhost:8081/swagger-ui.html. An empty business endpoint
list is expected: authentication and event APIs have not been implemented.
Stop the app with Ctrl+C; `make down` stops the dependency stack without deleting
the PostgreSQL volume. Redis data is intentionally ephemeral.

## Your study checklist

Read the linked primer for the concept, then trace the linked implementation.
The 21 primers were copied from SecondHandSell. Their banners distinguish
original marketplace examples from the actual ticketing implementation below.
No learning documents depend on SecondHandSell remaining present.

| Study | Concepts | Actual TicketingSystem files | Check yourself |
|---|---|---|---|
| Reference flow | [Reference study](REFERENCE-STUDY.md) | Named third-party files in the reference study | Trace where the inventory lock is acquired; identify what the smoke tests do not prove |
| Maven | [Maven](topics/maven.md) | [POM](../../backend/pom.xml), [wrapper properties](../../backend/.mvn/wrapper/maven-wrapper.properties), [Makefile](../../Makefile) | Why does `verify` run both test types while `test` runs only unit tests? |
| Spring startup/config | [Spring Boot](topics/spring-boot-basics.md) | [Application](../../backend/src/main/java/com/vishwas/ticketing/TicketingSystemApplication.java), [base configuration](../../backend/src/main/resources/application.yml), [local profile](../../backend/src/main/resources/application-local.yml), [test profile](../../backend/src/test/resources/application-test.yml) | Explain scanning, environment overrides, profiles, `open-in-view`, and virtual threads |
| Local services | [Compose](topics/docker-compose.md) | [Compose](../../docker-compose.yml), [environment example](../../.env.example) | Explain host vs container ports, the PostgreSQL volume, and health checks |
| Database baseline | [Flyway](topics/flyway.md), [PostgreSQL](topics/postgres-schema.md) | [V1 migration](../../backend/src/main/resources/db/migration/V1__create_users.sql) | Explain identity IDs, role constraint, case-insensitive email uniqueness, and timestamps |
| Test harness | [Testcontainers](topics/testcontainers.md), [Spring testing](topics/spring-testing.md) | [Shared integration setup](../../backend/src/test/java/com/vishwas/ticketing/AbstractIntegrationTest.java), [smoke IT](../../backend/src/test/java/com/vishwas/ticketing/PhaseZeroSmokeIT.java), [unit test](../../backend/src/test/java/com/vishwas/ticketing/config/OpenApiConfigTest.java) | Trace how Spring obtains the container's random JDBC port |
| CI | [GitHub Actions](topics/github-actions.md) | [Workflow](../../.github/workflows/backend-ci.yml) | What runs on push/PR and where are the test reports? |
| HTTP probes/docs | [Actuator](topics/actuator.md), [OpenAPI](topics/openapi.md) | [OpenAPI config](../../backend/src/main/java/com/vishwas/ticketing/config/OpenApiConfig.java), [base configuration](../../backend/src/main/resources/application.yml) | Why is DB connectivity in readiness but not liveness? |
| Safe failure | [Flyway](topics/flyway.md) | [Checksum fixture](../../backend/src/test/resources/db/checksum-mismatch/V1__create_users.sql), [smoke IT](../../backend/src/test/java/com/vishwas/ticketing/PhaseZeroSmokeIT.java) | Why is an expected exception a passing test? |

## Implementation verification record

Verified locally on 2026-10-14 with JDK 21.0.12.1, Docker Engine 29.8.1, and
PostgreSQL 16.15:

- Clean compilation followed by `make verify`: one unit test and eight
  integration tests passed, with zero failures/errors/skips.
- `make test-unit`: only the unit test executed.
- `make test-it`: Surefire skipped, all eight integration tests executed.
- `make build`: tests skipped and executable jar packaged.
- Compose config validated; PostgreSQL and Redis became healthy after both
  initial start and `make down` / `make up`. Redis returned `PONG`.
- `make run`: local profile started on 8081 against the Compose database;
  health/readiness returned `UP`, Swagger returned HTTP 200, OpenAPI identified
  the ticketing API, and V1 appeared as successful in migration history.
- All relative Markdown links in the 28 documentation files resolved.
- GitHub Actions workflow is defined but its hosted execution is pending push.

These checks establish the foundation, not authentication or booking safety.
The application's development stack is independent of the sibling repositories.

## Important differences from the inherited primers

- Package root: `com.vishwas.ticketing`, not `com.vishwas.shs`.
- Database: `ticketing`, local user `ticketing`; there are no campuses,
  listings, or campus-membership rules in this project.
- Local host ports: PostgreSQL `5433`, Redis `6380`, application `8081`.
  Testcontainers uses random host ports and does not depend on `make up`.
- PostgreSQL 16 and Redis 7 match the inherited local service versions. Redis
  runs for hands-on study but the application does not connect to it yet.
- No Mailpit, Elasticsearch, object storage, frontend, JSON logging, or
  Prometheus integration is installed in Phase 0.
- Unit tests use `*Test.java`; integration tests use `*IT.java`. The POM
  explicitly wires `skipUnitTests` into Surefire, so `make test-it` really
  skips unit tests. `-DskipTests` skips both categories, not just unit tests.
- Containers start once per integration-test JVM, with reuse disabled. Ryuk
  handles cleanup. Do not enable reuse without deciding how data is isolated.
- `api.version=1.44` is passed to the integration JVM for this machine's Docker
  API compatibility. If an IDE-run integration test fails with an old client
  API error, use the Make command or set that JVM property in the run config.
- Hibernate uses `ddl-auto: validate`, but no business entities exist yet.
  The Phase 0 tests verify SQL constraints directly. Validation of entity/table
  mappings becomes meaningful when you implement the user entity in Phase 1.
- Password hashing and authentication are not implemented. Test SQL uses
  explicitly fake placeholder hashes; those are not user accounts or a login
  mechanism. Swagger and health are local development surfaces, not a secure
  production deployment.
- `TIMESTAMPTZ` stores instants, not the original named timezone. A display
  timezone for an event can be modeled separately later.

## Walk through the database

```bash
make psql
```

At the psql prompt:

```sql
\dt
\d+ users
\di
SELECT version, description, checksum, success FROM flyway_schema_history;
SELECT column_name, data_type FROM information_schema.columns
WHERE table_name = 'users' ORDER BY ordinal_position;
\q
```

Ask: what prevents two differently cased copies of the same email? Why is
`updated_at DEFAULT now()` not enough to update the timestamp on future writes?
You will choose and implement update-time handling with the Phase 1 entity.

## Walk through Redis

```bash
make redis-cli
```

At the Redis prompt:

```text
PING
SET learning:phase0 hello EX 60
GET learning:phase0
TTL learning:phase0
DEL learning:phase0
QUIT
```

Explain why this transient data must never be the authoritative ticket count.

## Repeat the safe failure exercise

```bash
cd backend
./mvnw -B verify -DskipUnitTests=true \
  '-Dit.test=PhaseZeroSmokeIT#detectsAnAlteredMigrationWithoutChangingTheAppliedFile'
```

The production V1 is migrated into a fresh PostgreSQL test container. The test
then points a separate Flyway validator at a different V1 fixture (`SELECT 1`)
with the same version/name but a different checksum. Validation throws
`FlywayValidateException` containing `checksum mismatch`; the test asserts that
exception and passes. The real migration and database are not modified by this
validator. Study the fixture and test assertion together.

The invalid-role test separately demonstrates a named PostgreSQL CHECK
constraint rejecting invalid data. Neither exercise requires a destructive
change to your development database.

## Build commands to understand

```bash
make test-unit    # One unit test; no Docker needed
make test-it      # Eight integration tests; Surefire is skipped
make verify       # Both categories; the project gate
```

Run a clean build with `cd backend && ./mvnw -B clean verify` when you want to
ensure generated artifacts are not hiding a problem. Reports are in
`backend/target/surefire-reports` and `backend/target/failsafe-reports`.

`make run` sources an optional root `.env` as shell environment and then starts
the local profile. The example values are development-only. If PostgreSQL has
already initialized its volume, changing its password in `.env` does not rotate
the stored database password automatically.

## Before Phase 1

- [ ] Run and inspect all commands above.
- [ ] Explain every dependency and test plugin in the POM.
- [ ] Trace configuration from environment/profile into the datasource.
- [ ] Explain the migration, constraints, and the safe failure exercise.
- [ ] Explain why these tests do not yet establish booking correctness.
- [ ] Answer the Phase 0 questions in the roadmap without notes.
- [ ] Tell me which concepts need another explanation before you begin slice 1.1.

Record your notes in the relevant copied primer's **My notes** section. Phase 1
remains unimplemented; we will pair on your first user-entity/repository slice.