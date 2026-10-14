# Testing in Spring (JUnit 5, AssertJ, Mockito, Spring test support)

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### The layers used in this project

| Layer | Name pattern | Run by | Spring? | DB? | Speed | Use for |
|---|---|---|---|---|---|---|
| **Unit** | `*Test.java` | Surefire (`make test-unit`) | No | No (mocks) | ms | Business rules, state machine, token logic, calculations |
| **Integration** | `*IT.java`, extends `AbstractIntegrationTest` | Failsafe (`make test-it`) | Full context | Real Postgres (+Redis, ES later) | seconds | Endpoints end to end, repositories, queries, transactions, authorization |

The spec also mentions *slice tests* (`@WebMvcTest`, `@DataJpaTest`). They load
part of the context. In this project, prefer full ITs for anything touching the
DB: one shared cached context is usually faster overall than many slice
contexts, and it tests the real wiring. `@WebMvcTest` is still handy for pure
controller/validation tests with a mocked service.

### JUnit 5 essentials

```java
class PriceFormatterTest {

    @BeforeEach
    void setUp() { /* runs before every test */ }

    @Test
    @DisplayName("formats 12345 cents as ₹123.45")
    void formatsCents() { ... }

    @ParameterizedTest
    @CsvSource({ "0, ₹0.00", "5, ₹0.05", "100, ₹1.00" })
    void formatsEdgeCases(long cents, String expected) { ... }

    @Nested
    class WhenNegative {
        @Test void rejects() { ... }
    }
}
```
- Test classes and methods can be package-private (no `public`), as in `PhaseZeroSmokeIT`.
- `@ParameterizedTest` with `@CsvSource` / `@MethodSource` is how you'll write
  the state-machine tests (2.9) and the authorization matrix (1.16) compactly.

**Naming (CLAUDE.md):** describe behaviour: `rejectsRegistrationForUnknownDomain`,
never `testRegister2`.

### AssertJ: fluent assertions

```java
assertThat(domain).isEqualTo("mitaoe.ac.in");
assertThat(tables).contains("campuses", "users");
assertThat(count).as("V1 should have run").isGreaterThanOrEqualTo(1);   // .as() = message on failure
assertThat(user.getEmail()).startsWith("a").endsWith("@mitaoe.ac.in");
assertThat(optional).isPresent().get().extracting(User::getRole).isEqualTo(Role.USER);

assertThatThrownBy(() -> service.register(bad))
    .isInstanceOf(DuplicateEmailException.class)
    .hasMessageContaining("already registered");
```
Use AssertJ (`org.assertj.core.api.Assertions.*`) consistently, not JUnit's `assertEquals`.

### Mockito: unit tests without Spring

```java
@ExtendWith(MockitoExtension.class)
class SomeServiceTest {

    @Mock  SomeRepository repo;        // fake: returns null/empty unless stubbed
    @InjectMocks SomeService service;  // real object, mocks passed to its constructor

    @Test
    void returnsTheStoredThing() {
        when(repo.findById(1L)).thenReturn(Optional.of(thing));   // stub

        var result = service.get(1L);

        assertThat(result).isEqualTo(thing);
        verify(repo).findById(1L);                                // interaction check
        verifyNoMoreInteractions(repo);
    }
}
```
Because of constructor injection, you can skip `@InjectMocks` and write
`new SomeService(repo)`, which is often clearer.

**Mock at boundaries, not everything.** Mock repositories, clocks, mail
senders. Don't mock the object you're testing, and don't mock value objects.

**Time:** code that checks expiry (tokens in 1.9, 1.13) should take a
`java.time.Clock` bean so tests can use `Clock.fixed(...)`. Much better than sleeping.

### Integration tests: what `@SpringBootTest` gives you

`AbstractIntegrationTest` is `@SpringBootTest` (default `webEnvironment = MOCK`):
the full context with a *mock* servlet environment, no real port. You can
`@Autowired` any bean into the test (tests are the one place field `@Autowired`
is fine).

`PhaseZeroSmokeIT` uses `JdbcTemplate` to query the DB directly: the
simplest way to assert on schema and data.

**Calling endpoints (from slice 1.7):** add `@AutoConfigureMockMvc` and inject `MockMvc`:
```java
mockMvc.perform(post("/api/v1/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"email":"x@mitaoe.ac.in","password":"...","displayName":"X"}
                """))
       .andExpect(status().isCreated())
       .andExpect(jsonPath("$.id").isNumber());
```
MockMvc runs the full MVC stack (filters, security, validation, exception
handler, JSON) in the same thread, without HTTP. Consider putting
`@AutoConfigureMockMvc` on the **base class** so every IT shares one context
(see *context caching* in `testcontainers.md`).

**Replacing a bean with a mock in an IT:** `@MockitoBean` (Spring Framework 6.2+;
the older `@MockBean` is deprecated). Use sparingly: it creates a separate context.

### Exception translation, seen in `PhaseZeroSmokeIT`

```java
assertThatThrownBy(() -> jdbc.update("INSERT ... role 'SUPERUSER'"))
    .isInstanceOf(DataIntegrityViolationException.class);
```
Postgres raises SQLState `23514` (check violation). Spring translates
driver-specific `SQLException`s into its own `DataAccessException` hierarchy,
so your code and tests don't depend on Postgres error codes.
`DataIntegrityViolationException` covers unique, FK, check and not-null violations.
You'll catch it in 1.7 to return 409 for duplicate email.

### Structure of a good test

```java
@Test
void rejectsExpiredVerificationToken() {
    // given: the state the world is in
    // when: the one action under test
    // then: the observable outcome
}
```
One behaviour per test. If the name needs "and", split it.

### What to test in each slice (checklist)

- Happy path.
- Each validation rule, at the boundary (e.g. min length − 1, min length).
- Each authorization rule × each role, including anonymous (CLAUDE.md).
- Each error mapping (404, 409, 400 shape).
- For state: what's in the DB afterwards, not just the HTTP status.

### Commands

```bash
make test-unit
make test-it
cd backend && ./mvnw test -Dtest=CampusServiceTest
cd backend && ./mvnw test -Dtest='CampusServiceTest#rejectsUnknownDomain'
cd backend && ./mvnw verify -Dit.test=PhaseZeroSmokeIT
```
Failures: read `target/surefire-reports/*.txt` or `target/failsafe-reports/*.txt`.

### Gotchas

- **Wrong suffix = never runs.** `FooTests.java` is ignored (see `maven.md`).
- **Unit test that starts Spring** (`@SpringBootTest` in a `*Test.java`) runs
  under Surefire without containers and fails or is very slow. Unit tests are plain Java.
- **Mockito strict stubs:** an unused `when(...)` fails the test with
  `UnnecessaryStubbingException`. Remove the stub; it's telling you the test is wrong.
- **Test order is not guaranteed.** Tests must not depend on each other.
- **`@Transactional` on a test** rolls back, which also means after-commit logic
  never runs in that test (relevant in 1.8).

### Read next

- JUnit 5 User Guide: *Writing Tests*, *Parameterized Tests*, *Nested Tests*
- AssertJ docs: *Core assertions guide*
- Mockito docs: *Mockito javadoc main page* (sections 1–10 cover most needs)
- Spring Boot reference: *Testing → Testing Spring Boot Applications* (MockMvc, `@MockitoBean`)
- Spring Framework reference: *Testing → MockMvc*

### Check yourself

1. Which tests should mock the repository, and which must use the real one?
2. Why is `DataIntegrityViolationException` better to catch than `PSQLException`?
3. What does `@AutoConfigureMockMvc` on only *some* IT classes cost you?
4. How will you test "token expires after 24 h" without waiting 24 h?
5. Rewrite the name `testLogin3` as a behaviour-describing name for "login fails for a suspended user".

---

## My notes

*Write this yourself after slice 0.5, and add to it after 1.5 (first Mockito
test) and 1.7 (first MockMvc test).*
