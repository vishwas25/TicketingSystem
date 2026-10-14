# PostgreSQL schema design

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### Why this matters more than it seems

Code is cheap to change; a schema with data in it is not. Every constraint you
put in the database is a rule that **no bug in any code path can break**. The
application validates for good error messages; the database enforces for correctness.

### Column types used in this project

| Need | Postgres type | Java type | Why |
|---|---|---|---|
| Primary key | `BIGINT GENERATED ALWAYS AS IDENTITY` | `Long` | Standard SQL identity. `ALWAYS` rejects a manually supplied id (unless `OVERRIDING SYSTEM VALUE`), so the DB alone assigns ids. Preferred over the older `SERIAL`. |
| Short text with a limit | `VARCHAR(n)` | `String` | In Postgres `VARCHAR(n)` and `TEXT` perform the same; `n` is just a length constraint |
| Long free text | `TEXT` | `String` | Descriptions, message bodies |
| Point in time | `TIMESTAMPTZ` | `Instant` | Stores an absolute instant (internally UTC). `TIMESTAMP` without zone is ambiguous: "10:00" where? **Never use it** (CLAUDE.md #5) |
| Flag | `BOOLEAN` | `boolean` | |
| Money | `BIGINT` cents **+** `VARCHAR(3)` currency | `long` + `String` | Floating point can't represent 0.10 exactly; integer cents can (CLAUDE.md #4) |
| Enum-like value | `VARCHAR(20)` + `CHECK (... IN (...))` | Java `enum` via `@Enumerated(EnumType.STRING)` | Easier to evolve than a Postgres `ENUM` type (adding a value is just replacing the CHECK) |
| Event payload (Phase 5) | `JSONB` | `String` / mapped type | Binary JSON, indexable |

### Constraints

```sql
role VARCHAR(20) NOT NULL DEFAULT 'USER',
CONSTRAINT chk_users_role CHECK (role IN ('USER', 'MODERATOR', 'ADMIN'))
```

| Constraint | Enforces | Seen in V1 |
|---|---|---|
| `NOT NULL` | value required | most columns |
| `PRIMARY KEY` | unique + not null + index | `id` |
| `REFERENCES t(id)` (foreign key) | the referenced row exists | `users.campus_id` |
| `UNIQUE` / unique index | no duplicates | email, email_domain |
| `CHECK (expr)` | arbitrary rule per row | `chk_users_role` |
| `DEFAULT` | value when the insert omits it | `created_at DEFAULT now()` |

**Name your constraints** (`chk_users_role`, `idx_users_email`). Error messages
and future migrations refer to them by name. `PhaseZeroSmokeIT.roleCheckConstraintIsEnforced`
proves the CHECK works. Read it.

**Foreign key `ON DELETE`** options (you choose these in 1.2):

| Option | When the parent row is deleted |
|---|---|
| (default) `NO ACTION` / `RESTRICT` | Delete is refused while children exist |
| `CASCADE` | Children are deleted too |
| `SET NULL` | Child FK becomes NULL (column must be nullable) |

Rule of thumb: `CASCADE` only when the child is meaningless without its parent
*and* is owned by it (e.g. `listing_images` → `listings`, spec §6.1). This
project soft-deletes users and listings, so hard deletes are rare anyway.

### Indexes

An index is a separate sorted structure (B-tree by default) that lets Postgres
find rows without scanning the whole table.

**1. Postgres does not index foreign keys automatically.** Hence
```sql
CREATE INDEX idx_users_campus ON users (campus_id);
```
Without it, "all users of campus 1" and deleting a campus both scan every user.

**2. Expression (functional) unique index for case-insensitive email:**
```sql
CREATE UNIQUE INDEX idx_users_email ON users (lower(email));
```
Uniqueness is enforced on the *lowercased* value, so `Rahul@x.in` and
`rahul@x.in` collide. **Important for Phase 1:** a query uses this index only if
it filters on the same expression: `WHERE lower(email) = lower(?)`. A Spring
Data method `findByEmailIgnoreCase` generates `upper(...)` in most setups, which
**won't** use a `lower()` index. Normalising the email to lowercase in Java
before saving and querying is the simplest fix. Think about this in 1.1/1.7.

**3. Composite index column order matters** (Phase 2):
`(campus_id, status, created_at DESC)` serves "campus X, status ACTIVE, newest
first", but not a query on `status` alone. Leftmost columns first.

**4. Partial index** (Phase 5): `... WHERE published_at IS NULL` only indexes
unpublished outbox rows. Small and fast even when the table is huge.

**5. Indexes aren't free:** each one slows writes and uses disk. Add them for
queries you actually run.

**Verify with `EXPLAIN`:**
```sql
EXPLAIN ANALYZE SELECT * FROM users WHERE lower(email) = 'a@mitaoe.ac.in';
-- look for "Index Scan using idx_users_email" vs "Seq Scan"
```
(On a tiny table Postgres may pick a Seq Scan anyway, because it's cheaper. The
500-listing seed in 2.15 makes this realistic.)

### Timestamps in practice

- `DEFAULT now()` is the **transaction start** time.
- `updated_at DEFAULT now()` is only set on insert. **Nothing updates it
  automatically on UPDATE.** Either the application sets it (JPA
  `@PreUpdate` or Hibernate `@UpdateTimestamp`) or a DB trigger does. Decide in 1.1.
- The JDBC session is UTC (`hibernate.jdbc.time_zone: UTC`); psql shows
  timestamps in your session's zone, which is only display.

### Soft delete (Phase 2 preview)

Listings have `deleted_at` rather than being deleted: history, chats and reports
still reference them. Every read query must then filter `deleted_at IS NULL`.
Easy to forget; tests catch it.

### psql cheat sheet (`make psql`)

```
\dt                 list tables
\d+ users           table detail: columns, constraints, indexes
\di                 list indexes
\x                  toggle expanded (vertical) output
\timing             show query time
\q                  quit
```

### Gotchas

- `VARCHAR` without a length is legal but unconventional here; follow the V1 style.
- A `CHECK` on an enum must be updated in a **new** migration when you add an
  enum value in Java, or inserts fail at runtime with a constraint violation.
- `ON CONFLICT DO NOTHING` with no target swallows conflicts on **any** unique
  constraint. Fine for idempotent seeds, dangerous in app code.
- Case-insensitive uniqueness at DB level + case-sensitive lookup in Java = "user
  not found" for users who exist.

### Read next

- PostgreSQL docs: *Data Types → Date/Time Types* (TIMESTAMPTZ semantics)
- PostgreSQL docs: *DDL → Constraints*
- PostgreSQL docs: *Indexes → Indexes on Expressions*, *Multicolumn Indexes*, *Partial Indexes*
- PostgreSQL docs: *CREATE TABLE → GENERATED ... AS IDENTITY*
- *Use The Index, Luke* (free online book on SQL indexing), chapters 1–2

### Check yourself

1. Why is `lower(email)` indexed rather than `email`? What must every email query look like to use it?
2. Why does `users.campus_id` need its own index?
3. Why store money as `BIGINT` cents and not `NUMERIC(10,2)` or `DOUBLE`? (Note: `NUMERIC` is exact, so what's the argument?)
4. What's wrong with `TIMESTAMP` for a campus with students across time zones, or a server moved to another region?
5. Who updates `updated_at` in this schema today?

---

## My notes

*Write your own notes after the corresponding TicketingSystem slice.*
