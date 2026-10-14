# Testcontainers

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### What it is

A Java library that starts **real Docker containers from test code**: a real
Postgres 16, a real Redis 7, later a real Elasticsearch. Your integration
tests run against the same engines as production, with no shared test DB to
set up and no "works on my machine".

### Why not H2 / an in-memory fake (ADR-006)

H2 in "Postgres mode" accepts SQL that Postgres rejects and lacks features this
schema relies on (functional indexes, `TIMESTAMPTZ` semantics, partial indexes,
`JSONB`, `FOR UPDATE SKIP LOCKED`). A test suite that passes on H2 and fails in
production is worse than none, because you trust it.

### How it works

```
JUnit starts
  └─ AbstractIntegrationTest class loads
       └─ static { POSTGRES.start(); REDIS.start(); }
            ├─ docker-java talks to the Docker socket
            ├─ pulls postgres:16-alpine if needed, starts it on a RANDOM host port
            ├─ waits until ready (for Postgres: the "ready to accept connections" log line)
            └─ also starts "Ryuk", a reaper container
  └─ @DynamicPropertySource tells Spring the real URL/port
  └─ Spring context starts → Flyway migrates the fresh DB → tests run
JVM exits → Ryuk notices and removes the containers
```

**Random host ports** mean tests never clash with your `make up` stack on 5432,
and two test runs can happen in parallel.

### The pieces in `AbstractIntegrationTest`, line by line

```java
@SpringBootTest                // start the full application context
@ActiveProfiles("test")        // load application-test.yml (and NOT application-local.yml)
public abstract class AbstractIntegrationTest {

    protected static final PostgreSQLContainer<?> POSTGRES =
        new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
            .withDatabaseName("shs").withUsername("shs").withPassword("shs")
            .withReuse(true);
```
- `PostgreSQLContainer` is a specialised module: knows the default port, the
  wait strategy, and builds the JDBC URL for you (`getJdbcUrl()`).
- Same image tag as Compose: test and dev run the same Postgres version.

```java
    protected static final GenericContainer<?> REDIS =
        new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .withReuse(true);
```
- `GenericContainer` = any image. You say which container port to expose; the
  host port is random, and you ask for it with `getMappedPort(6379)`.

```java
    static { POSTGRES.start(); REDIS.start(); }
```
- **Singleton pattern (ADR-005).** Started once per JVM, the first time any IT
  class loads. All IT classes share them. The alternative, `@Testcontainers` +
  `@Container`, stops and starts containers **per test class**, costing minutes
  across a suite.

```java
    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        ...
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
```
- Runs **before** the Spring context is created and adds properties with top
  priority. The values are `Supplier`s (method references), so they're resolved
  lazily, after containers have started.
- That's why `application-test.yml` says *"do not set datasource here"*.

### Container reuse: a speedup with a catch

`.withReuse(true)` does nothing unless you opt in on your machine:
```properties
# ~/.testcontainers.properties
testcontainers.reuse.enable=true
```
With it enabled, containers **survive after the JVM exits** and the next test
run reattaches to them instead of starting fresh. Big local speedup; CI never
has the file, so CI always gets fresh containers.

**The catch:** a reused Postgres **keeps its data between runs**. So:
- Tests must not assume an empty table. A test inserting `a@mitaoe.ac.in` passes
  once and fails with a unique violation next run. Use unique data per test
  (e.g. a UUID in the email) or clean up.
- If you edit a migration locally (only acceptable before commit), the reused
  DB has the old checksum, so Flyway fails in tests. Remove the container:
  `docker ps` → `docker rm -f <id>`.
- Changing container config (image, env) creates a new container; the old
  one lingers until you remove it.

### Spring test context caching: the other half of speed

Spring caches the application context between test classes **if their
configuration is identical**. All ITs extend the same base class with the same
profile, so one context is started and reused. Things that create a **new
context** (slow, and each one opens its own DB pool):
- a different `@ActiveProfiles` or `@TestPropertySource` on one class;
- `@MockitoBean` on some classes but not others (each distinct set = new context);
- `@DirtiesContext`.

Keep IT classes uniform; put per-test variation in the test data, not in the context.

### Test data isolation: pick a strategy per test

| Strategy | How | Good for | Caveat |
|---|---|---|---|
| Unique data | emails like `u-<uuid>@mitaoe.ac.in` | most tests | tables grow (harmless) |
| `@Transactional` on the test | Spring rolls back after each test | repository/service tests, MockMvc tests | **doesn't** work when the code under test runs in another thread or commits itself (after-commit events in 1.8, the outbox relay in 5.5) |
| Explicit cleanup | `TRUNCATE ... CASCADE` / `deleteAll()` in `@BeforeEach` | tests that count rows | slower; careful with seed rows like the campus |

### Commands

```bash
make test-it                                   # all ITs (see slice 0.9 about unit tests)
cd backend && ./mvnw verify -Dit.test=PhaseZeroSmokeIT
docker ps                                      # during a run: see testcontainers + ryuk containers
docker ps --filter label=org.testcontainers    # only Testcontainers-managed ones
```

### Gotchas

- **Docker not running:** `Could not find a valid Docker environment`. Start Docker Desktop / the daemon.
- **`client version 1.32 is too old`:** the docker-java API version default.
  Fixed by the failsafe `api.version` property (ADR-009). Running an IT
  **from the IDE** bypasses failsafe's config, so add `-Dapi.version=1.44` to
  the IDE's JUnit run configuration if you hit this.
- **First run is slow**: images are downloading.
- **An IT class that doesn't extend `AbstractIntegrationTest`** gets no
  containers and tries `localhost:5432`, then passes or fails depending on
  whether `make up` happens to be running. Always extend the base class.
- Spring Boot 3.1+ also offers `@ServiceConnection` as an alternative to
  `@DynamicPropertySource`. This project uses the explicit form; don't mix both.

### Read next

- Testcontainers for Java docs: *Quickstart → JUnit 5*, *Singleton containers pattern*, *Reusable containers*
- Testcontainers docs: *Modules → Databases → Postgres*
- Spring Framework reference: *Testing → Context Caching*, *`@DynamicPropertySource`*
- Spring Boot reference: *Testing → Testcontainers*

### Check yourself

1. Why can tests run while `make up` also has Postgres on 5432?
2. What does `@DynamicPropertySource` do that a value in `application-test.yml` couldn't?
3. With reuse enabled, your test passes once and fails on the second run with a unique violation. Why? Two ways to fix it?
4. What would make the suite start two Spring contexts instead of one?
5. Who deletes the containers when reuse is **off**?

---

## My notes

*Write your own notes after the corresponding TicketingSystem slice.*
