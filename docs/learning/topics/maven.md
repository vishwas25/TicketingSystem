# Maven

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

For this project's skip behavior, the POM defaults `skipUnitTests` to
`${skipTests}` and wires it into Surefire. That preserves global `-DskipTests`
while letting `make test-it` override only unit-test execution. An explicit
`<skipTests>false</skipTests>` in Surefire configuration would take precedence
over the global command-line property and unexpectedly run unit tests during
packaging. Check actual test counts, not just a successful build exit code.

## Primer

### What it is

Maven is the build tool: it downloads dependencies, compiles, runs tests, and
packages the jar. Everything is described in `pom.xml` (Project Object Model).
Maven is **convention over configuration**: source lives in `src/main/java`,
tests in `src/test/java`, output goes to `target/`, and you rarely say how to
build, only what the project is.

### Why this project uses it

Spec §10 decision 2: it matches enterprise Java norms. `make` targets are thin
wrappers around Maven commands.

### Core concepts

**1. Coordinates.** Every artifact is identified by `groupId:artifactId:version`.
This project is `com.vishwas:secondhandsell:0.1.0-SNAPSHOT`. `SNAPSHOT` means
"in development, not a fixed release".

**2. The parent POM, which is why most dependencies have no version.**
```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.3</version>
</parent>
```
The Spring Boot parent brings:
- **`dependencyManagement`**: a curated, tested-together version for hundreds
  of libraries (Hibernate, Jackson, Flyway, the Postgres driver…). You declare
  `flyway-core` with no version and get the one Boot 3.5.3 was tested with.
- **Plugin configuration**: compiler settings, and it reads `<java.version>21`
  to set the compiler release.
- Resource filtering, encoding (UTF-8), and so on.

Where a library is *not* managed by Boot (springdoc, logstash encoder), the POM
pins a version explicitly.

**3. BOM import.** Testcontainers isn't managed by Boot at the version wanted
(ADR-003 pins 1.21.3), so its **Bill of Materials** is imported:
```xml
<dependencyManagement>
  <dependency>
    <artifactId>testcontainers-bom</artifactId>
    <version>${testcontainers.version}</version>
    <type>pom</type><scope>import</scope>
  </dependency>
</dependencyManagement>
```
A BOM is a POM that only contains `dependencyManagement`. Importing it means
every `org.testcontainers:*` module gets a consistent version.
`dependencyManagement` **declares versions but adds nothing to the classpath**;
`dependencies` actually adds things.

**4. Scopes.**

| Scope | On compile classpath | On runtime classpath | In the jar | Example here |
|---|---|---|---|---|
| `compile` (default) | yes | yes | yes | `spring-boot-starter-web` |
| `runtime` | **no** | yes | yes | `postgresql` driver, `micrometer-registry-prometheus` |
| `test` | test code only | tests only | no | `spring-boot-starter-test`, Testcontainers |

`runtime` for the JDBC driver is deliberate: your code should talk to JDBC/JPA
interfaces, never import `org.postgresql.*` directly.

**5. Starters.** A `spring-boot-starter-*` is a dependency bundle plus
auto-configuration. `starter-web` = Spring MVC + embedded Tomcat + Jackson.
`starter-data-jpa` = Hibernate + Spring Data JPA + HikariCP. See
`spring-boot-basics.md`.

**6. The lifecycle: phases run in order.**
```
validate → compile → test → package → integration-test → verify → install
                      │                  │                  │
                  surefire           failsafe:          failsafe:
                  (*Test.java)       integration-test   verify
```
Running a phase runs **every phase before it**. `mvn verify` = compile + unit
tests + package + integration tests + fail if any IT failed. That's why
`make verify` is "the gate".

**7. Surefire vs Failsafe.** Why two test plugins?
- **Surefire** runs in `test`, fails the build immediately on failure. For fast unit tests.
- **Failsafe** runs ITs in `integration-test` but **records** failures instead of
  stopping, so any post-integration-test cleanup still runs, then fails the
  build in `verify`.

This POM restricts Surefire to `**/*Test.java` and Failsafe to `**/*IT.java`,
which is where the naming convention in CLAUDE.md comes from. **A test named
`FooTests.java` or `FooIntegrationTest.java` would not run at all.**

Failsafe also passes `-Dapi.version=1.44` to the test JVM (ADR-009).

**8. Plugin parameters vs user properties** (you need this for slice 0.9). A
plugin has *parameters* (e.g. Surefire's `skipTests`). Some parameters can be
set from the command line via a *user property* (`-DskipTests=true`). The mapping
is fixed by the plugin, and **Maven silently ignores properties nothing reads**.
`help:describe` shows the real mapping:
```bash
./mvnw help:describe -Dplugin=org.apache.maven.plugins:maven-surefire-plugin -Dgoal=test -Ddetail
```
Look for lines `User property: ...`. Then compare them with what `make test-it`
passes. Hint for the fix: a parameter inside `<configuration>` can be given a
`${yourOwnProperty}` value, and you can give that property a default in `<properties>`.

**9. The Maven Wrapper.** `mvnw` downloads and uses exactly Maven 3.9.9
(`.mvn/wrapper/maven-wrapper.properties`), so you, CI and anyone cloning get the
same version. Always use `./mvnw` over a system `mvn`.

**10. `spring-boot-maven-plugin`.** Its `repackage` goal (bound to `package`)
turns the normal jar into an **executable fat jar** containing all dependencies.
The original thin jar is kept as `*.jar.original` in `target/`. It also provides
`spring-boot:run` (used by `make run`).

### Commands

```bash
cd backend
./mvnw -B verify                    # full gate (-B = batch mode, no colour/progress noise)
./mvnw test                         # unit tests only
./mvnw test -Dtest=CampusServiceTest            # one unit test class
./mvnw verify -Dit.test=PhaseZeroSmokeIT        # one IT class (still runs unit tests; see 0.9)
./mvnw -DskipTests package          # build the jar only
./mvnw dependency:tree              # who pulled in what (great for conflicts)
./mvnw help:effective-pom           # the POM after the parent is merged in; long, but revealing
./mvnw versions:display-dependency-updates
```

Test reports land in `target/surefire-reports/` and `target/failsafe-reports/`.
CI uploads both as artifacts.

### How the Dockerfile uses Maven

```dockerfile
COPY pom.xml .
RUN mvn -B -q dependency:go-offline   # cached layer: only rebuilt when pom.xml changes
COPY src ./src
RUN mvn -B -q package -DskipTests
```
Copying the POM before the source means a code change doesn't re-download every
dependency. It's a standard Docker layer-caching trick.

### Gotchas

- **Wrong test suffix = silently skipped test.** Check the test count in the output.
- **Adding a Boot-managed dependency with an explicit `<version>`** overrides
  Boot's tested version. Don't, unless you have a reason (write an ADR, like ADR-003).
- **`-DskipTests` skips both Surefire and Failsafe.** `-Dmaven.test.skip=true`
  also skips *compiling* tests.
- **Dependency conflicts** show up as `NoSuchMethodError` at runtime. Use
  `dependency:tree` to find two versions of the same library.
- **IDE vs command line:** if IntelliJ/VS Code builds differently from `./mvnw`,
  trust `./mvnw`. CI uses it.

### Read next

- Maven docs: *Introduction to the Build Lifecycle*
- Maven docs: *Introduction to the Dependency Mechanism* (scopes, dependencyManagement, BOM import)
- Spring Boot reference: *Build Tool Plugins → Maven Plugin*
- Maven Failsafe plugin docs: *Usage* (why integration-test and verify are separate goals)

### Check yourself

1. Why does `spring-boot-starter-web` have no `<version>` but `springdoc-openapi-starter-webmvc-ui` does?
2. You name a test `ListingServiceTests.java`. What happens when you run `make verify`?
3. What's the difference between `dependencyManagement` and `dependencies`?
4. Why is the Postgres driver `runtime` scope?
5. After 0.9: explain why the original `make test-it` flags did nothing.

---

## My notes

*Write your own notes after the corresponding TicketingSystem slice.*
