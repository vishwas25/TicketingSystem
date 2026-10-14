# Flyway

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### What it is

Flyway versions your database schema like Git versions your code. Each schema
change is a SQL file with a version number. On startup Flyway checks which
versions the DB already has, and applies the missing ones **in order**, exactly
once. Every environment (your laptop, Testcontainers, CI, Kubernetes) ends up
with an identical schema, built the same way.

### Why this project uses it (and not Hibernate `ddl-auto: update`)

`ddl-auto: update` lets Hibernate guess DDL from your entities. It never drops
anything, can't rename, can't migrate data, produces different results on
different starting states, and leaves no record. Flyway gives you:
- reviewed, explicit SQL (indexes, CHECK constraints, partial indexes, seeds);
- a history table showing what ran, when, and whether it succeeded;
- identical schemas everywhere.

Hibernate runs in `validate` mode and only **checks** that entities match what
Flyway built.

### Core concepts

**1. File naming.**
```
V1__create_campuses_and_users.sql
│ │ └── description: underscores become spaces in the history table
│ └──── TWO underscores separator (one underscore = file silently ignored or error)
└────── version
```
Project convention: `V{n}__snake_case_description.sql`, next free integer
(ADR-010), one logical change per file, a *why* comment at the top.

**2. The history table.** Flyway creates `flyway_schema_history`:

| Column | Meaning |
|---|---|
| `installed_rank` | Order applied |
| `version`, `description`, `script` | From the file name |
| `checksum` | CRC32 of the file contents **at the time it ran** |
| `installed_on`, `execution_time` | When and how long |
| `success` | `false` = failed partway |

**3. Validation and checksums: why you never edit an applied migration.** On
every startup Flyway recalculates each file's checksum and compares it with the
stored one. Edit `V1` after it ran and startup fails:
```
Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 1
```
This is a feature. An edited migration means the DB was built from SQL that no
longer exists, so other environments would diverge. **Fix forward:** write
`V{n+1}` that corrects the mistake.

**4. Ordering.** Flyway refuses a new migration whose version is **lower** than
the highest applied (`outOfOrder: false`, the default, and the project keeps it
that way; ADR-010). That's why numbers are assigned in delivery order.

**5. Transactions: a Postgres advantage.** Postgres supports transactional DDL:
each migration runs in a transaction, so if statement 5 of 7 fails, statements
1–4 roll back too. The DB is never left half-migrated. (MySQL can't do this.)
Exception you'll meet later: `CREATE INDEX CONCURRENTLY` can't run inside a
transaction.

**6. How it runs in Spring Boot.** `flyway-core` on the classpath means
auto-config runs `flyway.migrate()` at startup, **before** Hibernate initialises.
`flyway-database-postgresql` is needed because Flyway 10 split database
support into separate modules.

**7. Seed data in migrations.** V1 inserts the one campus:
```sql
INSERT INTO campuses (name, email_domain, city)
VALUES ('MIT Academy of Engineering', 'mitaoe.ac.in', 'Pune')
ON CONFLICT DO NOTHING;
```
Reference data that the app *needs to function* (campuses, categories in 2.2)
belongs in migrations. Demo data (the 500 listings) does not; that's `make seed`.

**8. Settings in this project.**
- `baseline-on-migrate: false`: the DB is always built from V1; there is no
  pre-existing schema to adopt.
- `clean-disabled: true` (test profile): `flyway clean` drops **everything**;
  it's disabled so nothing can call it by accident.

### Writing a new migration: the workflow

1. Check the highest existing version: `ls backend/src/main/resources/db/migration`.
2. Create `V{next}__what_it_does.sql` with a *why* comment at the top.
3. Put the table, its constraints **and its indexes** in the same file (CLAUDE.md).
4. `make verify`: `PhaseZeroSmokeIT` and every IT boot against a fresh Postgres
   and apply all migrations from scratch.
5. `make run` applies it to your dev DB. Inspect with `make psql`.
6. Once committed, the file is **frozen**.

### Inspecting in psql

```sql
SELECT installed_rank, version, description, checksum, success
FROM flyway_schema_history ORDER BY installed_rank;

\dt              -- list tables
\d users         -- columns, constraints, indexes of a table
\di              -- list indexes
```

### Local recovery

- **You edited an applied migration in dev only (not committed):** revert the
  edit. Or, if you really want the new version, `make clean` (destroys dev
  data) and restart; the DB is rebuilt from scratch.
- **A migration failed halfway in dev:** on Postgres the transaction rolled it
  back; fix the SQL and rerun. (If a `success=false` row remains, `flyway repair`
  removes it. Rarely needed on Postgres.)
- **Never** "fix" by editing `flyway_schema_history` by hand.

### Gotchas

- **Single underscore** in the file name: the migration isn't recognised.
- **Editing a migration that already ran in Testcontainers with reuse enabled**
  gives a checksum failure in tests too (the reused container kept the old
  history). See `testcontainers.md`.
- **Entity and migration disagree**: `ddl-auto: validate` fails with
  `Schema-validation: missing column [...]` or `wrong column type`. Read the
  message; it names the table and column.
- **`TIMESTAMP` instead of `TIMESTAMPTZ`**: validation against an `Instant`
  field may pass or fail depending on mapping, but it breaks CLAUDE.md #5 either way.

### Read next

- Flyway documentation: *Concepts → Migrations* (versioned vs repeatable, naming)
- Flyway documentation: *Commands → Validate*, *Repair*
- Spring Boot reference: *How-to → Database Initialization → Use a Higher-level Database Migration Tool*
- PostgreSQL docs: *Transactional DDL* (search the wiki)

### Check yourself

1. What exactly does Flyway compare to detect an edited migration?
2. A teammate merged `V5` while your branch has `V5` too. What happens, and how do you resolve it?
3. Why is it good that Hibernate refuses to start when the schema doesn't match?
4. Why is the campus seed in a migration but the 500 demo listings are not?
5. Statement 3 of a 5-statement migration fails on Postgres. What state is the DB in?

---

## My notes

*Write this yourself after slices 0.4 and 0.8. Include the exact error you saw
when you edited V1.*
