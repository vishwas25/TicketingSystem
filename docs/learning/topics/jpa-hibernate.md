# JPA and Hibernate

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### What it is

- **JPA** (Jakarta Persistence API) is a *specification*: annotations and
  interfaces for mapping Java objects to relational tables (`@Entity`,
  `EntityManager`…).
- **Hibernate** is the *implementation* Spring Boot uses (Hibernate 6.x in Boot 3.5).
- **Spring Data JPA** sits on top: you declare a repository *interface*, and it
  generates the implementation, including queries derived from method names.

```
Your service
   │ calls
   ▼
UserRepository (interface you write) ──► Spring Data generates impl
   │                                          │ uses
   ▼                                          ▼
EntityManager (JPA) ──► Hibernate ──► JDBC ──► HikariCP pool ──► Postgres
```

In this project **Flyway creates the tables; JPA only maps to them.**
`ddl-auto: validate` makes Hibernate fail at startup if an entity doesn't match.

### Anatomy of an entity (neutral example)

```java
@Entity
@Table(name = "notes")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // matches GENERATED ALWAYS AS IDENTITY
    private Long id;

    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Enumerated(EnumType.STRING)                           // store "DRAFT", never the ordinal 0
    @Column(nullable = false, length = 20)
    private NoteStatus status = NoteStatus.DRAFT;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;                             // TIMESTAMPTZ ↔ Instant (CLAUDE.md #5)

    protected Note() { }                                   // JPA needs a no-arg constructor; protected keeps it out of your API

    public Note(String title) {                            // the constructor your code uses
        this.title = title;
    }

    // getters; setters only where mutation is a real business operation
}
```

Key points:
- **`protected` no-arg constructor** is required by JPA (Hibernate instantiates
  via reflection). With no Lombok (ADR-004), write it by hand.
- **Column names:** Boot's naming strategy converts `createdAt` → `created_at`
  automatically, so `@Column(name=...)` is optional for snake_case. Being
  explicit is fine and greppable.
- **`@Enumerated(EnumType.STRING)`** is mandatory. The default, `ORDINAL`,
  stores 0/1/2, and reordering the enum silently corrupts data.
- **IDs:** `GenerationType.IDENTITY` lets Postgres assign the id on insert. The
  id is `null` until the entity is persisted.
- **Prefer rich entities to anaemic ones.** A method like `user.markEmailVerified()`
  is better than `setEmailVerified(true)` from anywhere. The entity guards its own rules.

### Type mapping for this schema

| Column | Java field | Note |
|---|---|---|
| `BIGINT` identity | `Long` | wrapper, because it's null before insert |
| `VARCHAR(n)` / `TEXT` | `String` | `validate` checks the type, not the length |
| `BOOLEAN NOT NULL` | `boolean` | |
| `TIMESTAMPTZ` | `Instant` | never `LocalDateTime` (CLAUDE.md #5) |
| `TIMESTAMPTZ` nullable (e.g. `suspended_at`) | `Instant` (null = not suspended) | |
| `VARCHAR` + `CHECK IN (...)` | `enum` + `@Enumerated(STRING)` | |
| `BIGINT` cents | `long` / `Long` | CLAUDE.md #4 |
| FK column | `@ManyToOne` **or** plain `Long` | see below |

### Gotcha: DB defaults don't apply to Hibernate inserts

V1 has `created_at DEFAULT now()`, `role DEFAULT 'USER'`, `email_verified DEFAULT FALSE`.
Hibernate's INSERT **lists every mapped column**, including those that are
`null`. A default only applies when a column is *omitted*, so you get:
```
ERROR: null value in column "created_at" violates not-null constraint
```
Options:
1. Initialise in Java: field initialisers (`= Role.USER`, `= false`) and set
   timestamps in `@PrePersist` / `@PreUpdate` callbacks, or Hibernate's
   `@CreationTimestamp` / `@UpdateTimestamp`.
2. Map the column `insertable = false, updatable = false` and let the DB fill it
   (but then the Java field stays `null` until you reload the entity).

Option 1 is simpler and makes the value visible immediately. It also answers
"who updates `updated_at`?" from `postgres-schema.md`.

### Relationships

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "campus_id", nullable = false)
private Campus campus;
```
- **`@ManyToOne` defaults to EAGER.** Always write `fetch = FetchType.LAZY`.
  EAGER loads the related row on every query, usually with extra SELECTs you didn't ask for.
- **LAZY** gives you a *proxy*; the SELECT happens when you first call a getter
  on it, and only if the persistence context is still open (inside a transaction).
- With `open-in-view: false`, touching a lazy proxy outside a transaction throws
  `LazyInitializationException`. That's intended: it forces you to load what
  you need inside the service and map it to a DTO there.
- **Avoid `@OneToMany` collections unless you need them.** `User.getRefreshTokens()`
  would be a trap (large, rarely needed). Query the child repository instead.

**`@ManyToOne` vs plain `Long userId`** (a choice for 1.3):

| | `@ManyToOne User user` | `Long userId` |
|---|---|---|
| Navigate `token.getUser().getRole()` | yes (lazy) | no, look it up |
| Risk of accidental lazy loads | yes | none |
| JPQL joins | natural | manual |
| Coupling between feature packages | `auth` depends on `user.domain.User` | looser |

Either is defensible. Pick one, and be able to say why.

### The persistence context: the concept most people miss

Inside a transaction, Hibernate keeps a **persistence context** (first-level
cache): every entity loaded is *managed*.
- **Dirty checking:** change a field on a managed entity and Hibernate issues the
  UPDATE at flush/commit. **No `save()` call needed.**
  ```java
  @Transactional
  public void rename(Long id, String title) {
      Note n = notes.findById(id).orElseThrow(...);
      n.rename(title);            // that's it: UPDATE happens at commit
  }
  ```
- **Identity:** loading the same id twice in one transaction returns the *same* object.
- **Flush** = send pending SQL to the DB (before queries that could be affected,
  and at commit). Constraint violations often surface **at flush/commit**, not at
  `save()`. `saveAndFlush()` forces it earlier (see `transactions.md`).

### Spring Data repositories

```java
public interface NoteRepository extends JpaRepository<Note, Long> {

    Optional<Note> findByTitle(String title);                  // derived query
    boolean existsByTitle(String title);
    List<Note> findByStatusOrderByCreatedAtDesc(NoteStatus s);

    @Query("select n from Note n where n.owner.id = :ownerId and n.status = :status")
    List<Note> findOwned(@Param("ownerId") Long ownerId, @Param("status") NoteStatus status);
}
```
- `JpaRepository` gives `save`, `findById`, `findAll`, `delete`, paging, etc.
- **Return `Optional` for single results** (CLAUDE.md).
- `@Query` uses **JPQL**: entity and field names, not table and column names.
  `nativeQuery = true` for real SQL when needed.
- Parameters are always bound, never concatenated (spec §7.4, SQL injection).

**Case-insensitive email lookup.** V1's unique index is on `lower(email)`.
`findByEmailIgnoreCase` generates `upper(email) = upper(?)`, which **does not use
that index**. The simplest design: normalise email (`trim().toLowerCase(Locale.ROOT)`)
before saving *and* before querying, then use plain `findByEmail`. Same for `email_domain`.

### `BaseEntity`: check before copying the spec

Spec §5.2 lists `common/domain/BaseEntity` (`id`, `createdAt`, `updatedAt`) as a
`@MappedSuperclass`. But `campuses` has **no `updated_at` column**, so a
`Campus extends BaseEntity` fails `validate`. Options: a BaseEntity with only
`id` + `createdAt`, two base classes, or none. Decide in 1.1.

### equals / hashCode / toString

- Don't generate `equals`/`hashCode` from all fields. Entities change, and
  `id` is null before persist. The common safe approach: equal if same class and
  **non-null** equal id; `hashCode` returns a constant per class (`getClass().hashCode()`).
- `toString` must **not** touch lazy associations (it triggers queries or
  exceptions) and must not include `passwordHash` or the full email (CLAUDE.md #8).

### Seeing what Hibernate does

`application-local.yml` enables `org.hibernate.SQL: DEBUG`, so every SQL
statement is printed during `make run`. Watch it while you work: surprise
queries are how you'll spot N+1 problems (Phase 2).

### Gotchas

- `Schema-validation: missing column [x] in table [y]`: entity field with no
  matching column. Check the name and `@Column(name)`.
- `Schema-validation: wrong column type encountered in column [x]`: e.g.
  `LocalDateTime` vs `TIMESTAMPTZ`, or `Integer` vs `BIGINT`.
- Enum added in Java but not in the DB `CHECK` → insert fails at runtime. New migration.
- Returning an entity from a controller: lazy proxy explodes during JSON
  serialisation, or leaks `passwordHash`. DTOs only (CLAUDE.md).
- `repository.save(entity)` on an entity loaded in the same transaction is
  redundant (harmless, but shows a misunderstanding of dirty checking).

### Read next

- Spring Data JPA reference: *JPA Repositories → Query Methods* (derived query keywords table)
- Hibernate ORM User Guide: *Domain Model → Entity types*, *Associations*, *Fetching*
- Vlad Mihalcea's blog: articles on `@ManyToOne` fetch type, entity equals/hashCode, and why ORDINAL enums are dangerous
- Jakarta Persistence spec: *Entity lifecycle callbacks* (`@PrePersist`, `@PreUpdate`)

### Check yourself

1. Why does inserting a `User` fail with a NOT NULL violation on `created_at`, when the column has a default?
2. You load a user, change `displayName`, and never call `save`. Is it persisted? Under what condition?
3. Why must `@ManyToOne` be explicitly LAZY?
4. What happens if you return a `User` entity from a controller with `open-in-view: false`?
5. Why is `findByEmailIgnoreCase` a poor fit for V1's index?

---

## My notes

*Write this yourself after slices 1.1 and 1.3. Record your `@ManyToOne` vs
`Long` decision and your BaseEntity decision, with reasons.*
