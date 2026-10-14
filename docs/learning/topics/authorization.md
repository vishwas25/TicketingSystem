# Authorization

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### Authentication vs authorization

- **Authentication:** *who are you?* (JWT filter → `SecurityContext`). Failure → **401**.
- **Authorization:** *may you do this?* Failure → **403** (or 404, see below).

### Why the service layer (CLAUDE.md #7)

URL rules in `SecurityConfig` (`requestMatchers(...).authenticated()`) are a
coarse **first gate**. They can't express most of §7.2:
- "owner only": needs the listing loaded from the DB;
- "not the seller": needs the listing;
- "participant only": needs the conversation;
- "email verified": needs user state.

And the same service method is reached by paths that **don't pass URL rules**:
WebSocket messages (Phase 6), scheduled jobs, future endpoints that reuse the
service. If the rule lives in the controller or URL config, one of those paths
will forget it. In the service, it can't be bypassed.

**Rule:** the controller passes *who is calling* to the service; the service decides.

### Two layers of rules

| Layer | Where | Examples |
|---|---|---|
| Coarse | `SecurityConfig` URL rules | public vs authenticated; `/moderation/**` needs MODERATOR/ADMIN |
| Fine | Service methods | verified, owner, participant, not-seller, suspended |

Defence in depth: both, never just the URL layer.

### The principal: what the service receives

A small immutable record, built by the JWT filter (`spring-security.md`):
```java
public record AuthenticatedUser(Long id, Role role, boolean emailVerified) { }
```
- Not the JPA `User` entity: detached, lazy fields, and it would tie security to persistence.
- Controllers get it with `@AuthenticationPrincipal AuthenticatedUser caller`.
- Spec §5.2 lists `common/security/CurrentUser.java` as an
  "`@AuthenticationPrincipal` wrapper". Typically a **meta-annotation**:
  ```java
  @Target(ElementType.PARAMETER) @Retention(RetentionPolicy.RUNTIME)
  @AuthenticationPrincipal
  public @interface CurrentUser { }
  ```
  so controllers write `@CurrentUser AuthenticatedUser caller`.

**Stale claims.** If `emailVerified` comes from the token, a user who just
verified still has `false` for up to 15 min (until refresh). Options: issue new
tokens on verification, re-read the user from the DB for verified-only actions,
or accept the delay. This connects to the DB-per-request decision in
`spring-security.md`. Decide once and apply consistently.

### Explicit checks vs method security

**Option A: explicit checks in the service** (neutral example):
```java
@Transactional
public NoteResponse update(AuthenticatedUser caller, Long noteId, UpdateNoteRequest req) {
    Note note = notes.findById(noteId).orElseThrow(() -> new NotFoundException(…));
    if (!note.isOwnedBy(caller.id())) {
        throw new ForbiddenException(…);
    }
    …
}
```
Small helpers (spec §5.2 `SecurityUtils`) keep it tidy:
`requireVerified(caller)`, `requireRole(caller, MODERATOR, ADMIN)`.

**Option B: Spring method security**: `@EnableMethodSecurity` +
```java
@PreAuthorize("hasRole('MODERATOR') or hasRole('ADMIN')")
public void resolve(…) { … }
@PreAuthorize("@noteSecurity.isOwner(#noteId, principal)")
```
Declarative and visible, but SpEL strings are not compile-checked, ownership
checks often load the entity twice, and it only applies through the Spring
proxy (like `@Transactional`).

A common, defensible mix: **`@PreAuthorize` for role checks, explicit code for
ownership/state checks.** Decide while pairing in 1.15, and write it in the
ADR at 1.18 so every later phase follows the same style.

### 403 or 404?

When user B requests user A's private resource:
- **403** says "it exists, but not for you". It confirms existence.
- **404** says "no such thing". It hides existence.

For guessable sequential ids (`/conversations/17`), 404 for non-participants
avoids confirming what exists. For public resources where only the *action* is
forbidden (editing someone else's public listing), 403 is honest and clearer.
Pick a rule per resource type and apply it consistently. The matrix test pins it down.

### Roles

- `USER`, `MODERATOR`, `ADMIN` (V1 CHECK constraint).
- Does ADMIN imply MODERATOR? §7.2 says "MODERATOR or ADMIN" for moderation. Either
  list both everywhere or define a `RoleHierarchy` bean (`ROLE_ADMIN > ROLE_MODERATOR > ROLE_USER`).
- How does anyone become MODERATOR/ADMIN? No endpoint for it in v1, so via
  SQL/seed. Worth one sentence in the spec.

### Phase 1 matrix (the starting point for 1.16)

Callers: **anonymous**, **unverified USER**, **verified USER**, **MODERATOR**, **ADMIN**.

| Endpoint | anon | unverified | USER | MOD | ADMIN |
|---|---|---|---|---|---|
| `POST /auth/register` | ✓ | ✓? | ✓? | ✓? | ✓? |
| `POST /auth/login` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `POST /auth/refresh` | ✓ (token in body) | ✓ | ✓ | ✓ | ✓ |
| `POST /auth/verify-email` | ✓ | ✓ | ✓ | ✓ | ✓ |
| `POST /auth/logout` | 401 | ✓ | ✓ | ✓ | ✓ |
| `GET /users/me` | 401 | ✓ | ✓ | ✓ | ✓ |

The `?` cells are decisions (should a logged-in user be able to call register?
Usually harmless). Every later phase adds rows. The spec's rules table (§7.2) is
the source; this matrix is its executable form.

### Writing the matrix test (1.16)

Goal: **one place** that fails if any endpoint's access rules drift.
Ingredients:
- A test helper that creates a user in a given state (role, verified) and
  returns a **real access token** from `JwtService`. Tests then send
  `Authorization: Bearer …` through the real filter. (`@WithMockUser` would skip
  your filter; see `spring-security.md`.)
- `@ParameterizedTest` + `@MethodSource` producing rows of
  `(caller, method, path, body, expectedStatus)`.
- Assert **status only** here: behaviour is tested elsewhere; the matrix
  tests access.

Workflow (roadmap: You → AI): write the helper and the first few rows yourself;
once the pattern is clear, AI fills in the rest and you review every expected status.

Spec §5.2 names it `security/AuthorizationRulesIT`.

### Gotchas

- Checking authorization **after** doing the work (loaded, mutated, *then*
  checked) → the mutation may already be flushed. Check first.
- Trusting ids from the request body (`"sellerId": 5`) → always use `caller.id()`.
- Forgetting the **suspended** state: a suspended user with a still-valid access
  token (≤ 15 min). Decide whether the service checks `suspended_at` (Phase 7).
- `@PreAuthorize` without `@EnableMethodSecurity` → silently ignored.
- Catch-all `@ExceptionHandler(Exception.class)` turning `AccessDeniedException`
  into 500 (`error-handling.md`).

### Read next

- OWASP *Authorization Cheat Sheet* (deny by default, check on every request, IDOR)
- OWASP Top 10: *A01 Broken Access Control* (IDOR is the classic bug this prevents)
- Spring Security reference: *Servlet → Authorization → Method Security*
- Spring Security reference: *Authorization → Hierarchical Roles*

### Check yourself

1. Give two paths into a service method that bypass `SecurityConfig` URL rules.
2. Why is the principal a record and not the `User` entity?
3. When would you return 404 rather than 403?
4. A user verifies their email. When can they create a listing, given your stale-claims decision?
5. What is IDOR, and which line of your service code prevents it?

---

## My notes

*Write this yourself after slices 1.15 and 1.16. Record: explicit vs
`@PreAuthorize`, 403 vs 404 rule, role hierarchy, stale-claims decision.*
