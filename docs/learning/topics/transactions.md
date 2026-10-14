# Transactions

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### What a transaction guarantees (ACID, the parts you'll feel)

- **Atomicity:** all statements commit, or none do. "Create user + create
  verification token" can't half-happen.
- **Isolation:** concurrent transactions don't see each other's uncommitted
  changes (details below).
- **Durability:** once committed, it survives a crash.

### `@Transactional` in Spring: how it actually works

```java
@Service
public class NoteService {
    @Transactional
    public NoteResponse create(CreateNoteRequest req) { … }
}
```
Spring wraps the bean in a **proxy**. A call from *outside* goes through it:
```
caller ─► proxy: BEGIN ─► your method ─► proxy: COMMIT (or ROLLBACK on exception)
```
Consequences:
1. **Self-invocation bypasses it.** `this.otherMethod()` inside the same class
   doesn't go through the proxy, so `@Transactional` on `otherMethod` is ignored.
2. It only works on **public** methods called on a **Spring bean**.
3. **Rollback rules:** by default, rollback on `RuntimeException` and `Error`,
   **commit on checked exceptions**. `ApiException` extends `RuntimeException`,
   so throwing it rolls back. Good.
4. The transaction spans the whole method, including slow calls you put inside.
   **Never do remote I/O (email, HTTP, Elasticsearch) inside a transaction**: it
   holds a DB connection the whole time, and the remote side effect can't be
   rolled back.

### Read-only

`@Transactional(readOnly = true)` on query methods: Hibernate skips dirty
checking (faster), and it documents intent. Reads that touch lazy associations
need *a* transaction anyway (`open-in-view: false`).

### Where the boundary goes

One **use case = one transactional service method**: `register`, `verifyEmail`,
`login`, `refresh`. Repositories already run each call in its own small
transaction if none is active; your service method makes several calls into
one unit.

### Propagation (just the two you need)

| Propagation | Behaviour |
|---|---|
| `REQUIRED` (default) | Join the existing transaction, or start one |
| `REQUIRES_NEW` | Suspend the current one, run in a separate transaction that commits independently |

`REQUIRES_NEW` is for "this must be recorded even if the outer work fails"
(e.g. audit), and it needs a second DB connection. Rarely needed here.

### Side effects after commit: the email problem (slice 1.8)

Naive:
```java
@Transactional
public void register(…) {
    users.save(user);
    tokens.save(token);
    mailer.sendVerification(user, rawToken);   // ← inside the transaction
}
```
What goes wrong:
- The email is sent, then the commit **fails** (constraint violation at flush,
  DB hiccup). The user gets a link for an account that doesn't exist.
- The SMTP call is slow or hangs, holding a DB connection and a transaction open.

Pattern: publish an **event** inside the transaction; handle it **after commit**.
```java
// inside the @Transactional method
events.publishEvent(new NoteCreated(noteId, …));      // ApplicationEventPublisher

// separate bean
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void on(NoteCreated e) { … send email … }
```
The listener runs only if the commit succeeded.

Remaining gap, and it's worth understanding: if the app crashes *after*
commit but *before* the email is sent, the email is lost. For verification
emails that's acceptable (add "resend verification" later). For search indexing
it is **not** acceptable, which is exactly why Phase 5 uses the **transactional
outbox** instead. Same problem, stronger solution. Note this now; it will make Phase 5 click.

Test consequence: in a test annotated `@Transactional`, the transaction rolls
back and **never commits**, so after-commit listeners never fire. Tests
for 1.8 must not be `@Transactional`.

### Isolation and races (slice 1.13)

Postgres default isolation: **READ COMMITTED**. Each statement sees data
committed before *it* started. Two concurrent transactions can both read the
same row, both decide "valid", and both write. The classic **lost update / race**.

Refresh-token rotation has exactly this shape: two requests present the same
refresh token at the same moment. Both read "not revoked", both rotate.

Tools to prevent it:

| Tool | How | Fits when |
|---|---|---|
| **Pessimistic lock** | `SELECT … FOR UPDATE` (JPA: `@Lock(LockModeType.PESSIMISTIC_WRITE)` on the repository query) | Second transaction **waits** until the first commits, then sees `revoked_at` set |
| **Conditional update** | `UPDATE … SET revoked_at = now() WHERE id = ? AND revoked_at IS NULL`, check rows affected = 1 | One statement, atomic; the loser sees 0 rows |
| **Optimistic lock** | `@Version` column; conflicting update throws `OptimisticLockException` | Conflicts rare; retry acceptable. Needs a column |
| Unique constraint | DB rejects the second insert | "Only one X may exist" (e.g. duplicate email race in 1.7) |

For 1.13 you'll choose one of the first two. `refresh-token-rotation.md` covers
the implications (the loser of the race looks like a *reuse*: is that acceptable?).

**Unique email race (1.7):** `existsByEmail` then `save` isn't atomic; two
simultaneous registrations can both pass the check. The unique index catches the
second one at flush/commit as `DataIntegrityViolationException`. Map that to 409
too. Note: it may surface at **commit**, i.e. after your method returned,
unless you `saveAndFlush`.

### `SKIP LOCKED` (preview of Phase 5)

`SELECT … FOR UPDATE SKIP LOCKED`: take the rows nobody else has locked and
skip the rest. It lets several relay instances share the outbox without
processing the same event twice. Same locking concept, different use.

### Seeing transactions

```yaml
logging.level.org.springframework.orm.jpa.JpaTransactionManager: DEBUG
```
Logs `Creating new transaction`, `Committing`, `Rolling back`. Use it once to see the proxy at work.

### Gotchas

- `@Transactional` on a `private` method, or called via `this.` → silently ignored.
- Catching an exception inside the transaction and continuing: if Hibernate
  already marked the transaction rollback-only, you get
  `UnexpectedRollbackException` at commit.
- Checked exception thrown → **commits** unless `rollbackFor` is set.
- Long work (bcrypt at high cost, remote calls) inside a transaction holds a pool
  connection (pool size is 10 locally).
- `jakarta.transaction.Transactional` vs `org.springframework.transaction.annotation.Transactional`:
  both work; use Spring's consistently (it has `readOnly`, `propagation`).

### Read next

- Spring Framework reference: *Data Access → Transaction Management → Declarative transaction management* (proxy model, rollback rules)
- Spring Framework reference: *Transaction-bound Events* (`@TransactionalEventListener`)
- PostgreSQL docs: *Concurrency Control → Transaction Isolation*, *Explicit Locking → Row-level locks*
- Spring Data JPA reference: *Locking*

### Check yourself

1. Why does `this.save()` inside a service skip `@Transactional` on `save`?
2. Name two things that go wrong if the verification email is sent inside the transaction.
3. What does `@TransactionalEventListener(AFTER_COMMIT)` still *not* guarantee, and which Phase-5 pattern closes that gap?
4. Two requests refresh with the same token at the same instant under READ COMMITTED. What happens without locking?
5. Why must a test of the after-commit email not be `@Transactional`?

---

## My notes

*Write your own notes after the corresponding TicketingSystem slice.*
