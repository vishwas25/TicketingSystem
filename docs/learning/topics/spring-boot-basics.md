# Spring Boot basics

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### What it is

**Spring** is a framework that creates and wires your objects for you
(*Inversion of Control* / *Dependency Injection*). **Spring Boot** sits on top and
adds **auto-configuration**: it looks at what's on the classpath and in your
properties and sets up sensible infrastructure (web server, DataSource, JPA,
Flyway, Actuator…) so you write almost no configuration code.

### Core concepts

**1. Beans and the ApplicationContext.** A *bean* is an object Spring creates and
manages. The *ApplicationContext* is the container holding all beans. You mark
your classes (`@Service`, `@RestController`, `@Repository`, `@Component`,
`@Configuration`) and Spring instantiates them at startup.

**2. Dependency injection via constructor (CLAUDE.md rule).**
```java
@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AuthService(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }
}
```
With a single constructor, no `@Autowired` is needed: Spring sees the
constructor and supplies the beans. Why constructor over field injection: fields
can be `final`, dependencies are explicit, and unit tests can just call
`new AuthService(mockRepo, mockEncoder)` with no Spring at all. No Lombok here
(ADR-004), so you write constructors by hand.

**3. `@SpringBootApplication`** = three annotations:
- `@Configuration`: this class can define beans.
- `@EnableAutoConfiguration`: turn on auto-config.
- `@ComponentScan`: scan **this package and every sub-package** for components.

It sits in `com.vishwas.shs`, so `com.vishwas.shs.auth.service.AuthService` is
found automatically. A class in `com.vishwas.other` would **not** be. That's
one reason package-by-feature lives under this root (ADR-001).

**4. Auto-configuration is conditional.** Each auto-config class has conditions
such as "if class X is on the classpath" or "if property Y is set" or "if no
bean of type Z exists yet". Example chain in this project:

| On the classpath | Boot creates |
|---|---|
| `starter-web` | Embedded Tomcat on port 8080, Spring MVC, Jackson `ObjectMapper` |
| `starter-data-jpa` + `postgresql` + `spring.datasource.url` | HikariCP `DataSource`, Hibernate `EntityManagerFactory`, transaction manager |
| `flyway-core` | A `Flyway` bean that runs `migrate()` **before** JPA starts |
| `starter-actuator` + `micrometer-registry-prometheus` | `/actuator/*` endpoints, metrics registry |
| `springdoc-openapi-starter-webmvc-ui` | `/v3/api-docs`, `/swagger-ui.html` |

**"If no bean of type Z exists yet"** is the key rule: if you define your own
bean (e.g. a `SecurityFilterChain` in slice 1.6), Boot backs off and uses yours.

To see what was and wasn't configured and why, run
`./mvnw spring-boot:run -Dspring-boot.run.arguments=--debug` and read the
*CONDITIONS EVALUATION REPORT*.

**5. Startup order you'll care about.**
```
Load properties/profiles → create DataSource → Flyway migrate
→ Hibernate EntityManagerFactory (ddl-auto: validate checks entities vs tables)
→ your beans → Tomcat starts accepting requests
```
If Flyway fails, or validation finds drift, the app **refuses to start**. That's intended.

**6. Configuration properties and profiles.**
- `application.yml` is always loaded.
- `application-{profile}.yml` is loaded on top when that profile is active.
- `spring.profiles.default: local` means: *if no profile was activated
  explicitly*, use `local`. So `make run` gets `local`. Tests use
  `@ActiveProfiles("test")`, so `local` is **not** loaded in tests.
- Later values override earlier ones. Simplified precedence (highest first):
  command-line args → OS environment variables → `application-{profile}.yml` → `application.yml`.
- **Relaxed binding:** env var `SPRING_DATASOURCE_URL` overrides
  `spring.datasource.url`. This is how Kubernetes will configure the app in Phase 8.
- **Placeholders with defaults:** `${POSTGRES_HOST:localhost}` reads the env
  var `POSTGRES_HOST` or falls back to `localhost`.

**7. Virtual threads.** `spring.threads.virtual.enabled: true` makes Tomcat run
each request on a Java 21 virtual thread. Blocking I/O (JDBC, Redis, HTTP) no
longer ties up a scarce platform thread. Catch: a `synchronized` block that
blocks **pins** the carrier thread. Use `ReentrantLock` in blocking paths
(CLAUDE.md gotcha).

### Every non-default setting in `application.yml`, and why

| Setting | Why |
|---|---|
| `spring.profiles.default: local` | `make run` works with zero flags |
| `spring.threads.virtual.enabled: true` | I/O-bound app; see above |
| `spring.jpa.hibernate.ddl-auto: validate` | Hibernate **checks** the schema, never changes it. Flyway owns DDL (CLAUDE.md #1) |
| `spring.jpa.open-in-view: false` | Open-Session-In-View keeps a DB connection open for the whole HTTP request, including JSON serialisation. It hides lazy-loading bugs (N+1 queries fire silently during serialisation) and holds connections longer. Off = lazy-loading outside a transaction fails loudly. |
| `hibernate.jdbc.time_zone: UTC` | `Instant` ↔ `TIMESTAMPTZ` conversions never depend on the server's local zone (CLAUDE.md #5) |
| `spring.flyway.*` | Explicit location; no baseline, since the DB is always created by Flyway from V1 |
| `jackson.default-property-inclusion: non_null` | `null` fields are omitted from JSON responses |
| `jackson.serialization.write-dates-as-timestamps: false` | `Instant` becomes `"2026-09-30T10:15:00Z"`, not `1790000000.000` |
| `server.error.include-stacktrace: never` | Never leak stack traces (CLAUDE.md conventions). The RFC 7807 handler in 1.4 takes over most errors anyway |
| `management.*` | See `actuator.md` |
| `springdoc.*` | Swagger UI path, sort operations by HTTP method |
| `logging.level.com.vishwas.shs: DEBUG` | Verbose logs for your own code locally |

`application-local.yml` adds connection details for the Compose stack, a Hikari
pool size, and SQL logging (`org.hibernate.SQL: DEBUG`, so you'll see every query
Hibernate runs; very useful for learning JPA). It also has `spring.data.redis`
and `spring.mail` properties; nothing reads them until the Redis (4.1) and mail
(1.8) starters are added, and unused properties are harmless.

`application-test.yml` deliberately **doesn't** set datasource/Redis URLs; the
test base class injects them at runtime (see `testcontainers.md`).
`spring.flyway.clean-disabled: true` makes sure nothing can wipe the schema.

### Logging: `logback-spring.xml`

Two modes, chosen by `<springProfile>`:
- `local` / `test`: coloured, human-readable one-line logs.
- anything else (prod, k8s): **JSON, one object per line** (LogstashEncoder),
  so a log system can filter on fields.

`%X{correlationId}` reads from the **MDC** (Mapped Diagnostic Context, a
per-thread map of values added to every log line). It's empty until slice 3.3
adds the filter that fills it.

Use SLF4J in your code:
```java
private static final Logger log = LoggerFactory.getLogger(AuthService.class);
log.info("User registered userId={}", user.getId());   // placeholders, not string concat
```
Never log tokens, password hashes or full emails (CLAUDE.md #8).

### Commands

```bash
make up && make run                       # start stack, then the app on :8080
curl localhost:8080/actuator/health
./mvnw spring-boot:run -Dspring-boot.run.arguments=--debug        # conditions report
./mvnw spring-boot:run -Dspring-boot.run.profiles=local           # explicit profile
SERVER_PORT=8081 ./mvnw spring-boot:run   # env var override via relaxed binding
```

### Gotchas

- **A class outside `com.vishwas.shs`** is never scanned; you get "No qualifying bean" errors.
- **Two constructors** means Spring doesn't know which to use. Keep one.
- **Circular dependencies** (A needs B, B needs A) fail at startup. Usually a
  sign one class does too much.
- **`@Transactional`, `@Cacheable` etc. work through proxies.** Calling such a
  method from *another method in the same class* bypasses the proxy, so the
  annotation silently does nothing. You'll meet this in 1.8 and 4.3.
- **`spring.profiles.active` vs `spring.profiles.default`**: `active` wins and
  replaces `default` entirely.

### Read next

- Spring Boot reference: *Using Spring Boot → Spring Beans and Dependency Injection*
- Spring Boot reference: *Core Features → Externalized Configuration* (precedence list, relaxed binding)
- Spring Boot reference: *Core Features → Profiles*
- Spring Boot reference: *Auto-configuration → Understanding Auto-configured Beans*
- Spring Boot reference: *Core Features → Logging*

### Check yourself

1. Why don't you need `@Autowired` on a single constructor?
2. You run the app with `SPRING_PROFILES_ACTIVE=prod`. Which YAML files load, and what log format do you get?
3. What does `open-in-view: false` protect you from?
4. Flyway fails on startup. Does Tomcat ever start? Why is that desirable?
5. How would you override the DB URL without editing any file?

---

## My notes

*Write your own notes after the corresponding TicketingSystem slice.*
