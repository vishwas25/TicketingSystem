# Refresh token rotation with reuse detection

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

This is the part of Phase 1 an interviewer will ask about. It's labelled **You**
in the roadmap: this primer explains the design and the decisions, not the code.

## Primer

### Why two tokens

| | Access token | Refresh token |
|---|---|---|
| Format | JWT (`jwt.md`) | **Opaque random string** (recommended) |
| Lifetime | 15 min | 7 days |
| Sent | On every API request | Only to `POST /auth/refresh` (and logout) |
| Server state | None, validated by signature | **Row in `refresh_tokens`** (hashed) |
| Revocable | No (expires quickly) | **Yes** |

The access token is fast and stateless but can't be revoked, so it's short-lived.
The refresh token is revocable because the server checks it against the DB,
but it's only used every ~15 minutes, so the DB lookup costs little.

**Why opaque and not a JWT refresh token?** The server looks it up in the DB
anyway. A JWT would add nothing but size and a second place to validate.
Generate and hash exactly like verification tokens (`email-verification.md`).

### The table (spec §6.1)

```
refresh_tokens
  id              PK
  user_id         FK → users
  token_hash      SHA-256 of the raw token (index it: lookup by hash)
  family_id       all tokens descended from ONE login share this (index it: revoke-by-family)
  issued_at
  expires_at
  revoked_at      null = still usable
  replaced_by_id  FK → refresh_tokens (the token this one was rotated into)
```
Decisions for 1.2: `family_id` type (a `UUID` column is natural; generate it at
login), `ON DELETE` behaviour for `user_id`, and whether `replaced_by_id` is a
self-referencing FK.

### Rotation: the happy path

```
login           → T1 (family F)                       stored: T1 active
refresh with T1 → T1 revoked, replaced_by = T2; issue T2 (family F) + new access token
refresh with T2 → T2 revoked, replaced_by = T3; issue T3 …
```
Every refresh **consumes** the presented token and hands back a new one. A
refresh token is effectively single-use.

### Reuse detection: the point of the design

```
Attacker steals T2 (e.g. from a compromised device).

Case A: legitimate user refreshes first:
  user     → T2 ✓ → gets T3
  attacker → T2   → T2 is ALREADY REVOKED → REUSE DETECTED
                  → revoke EVERY token in family F (T3 included)
                  → both must log in again; only the real user can (they have the password)

Case B: attacker refreshes first:
  attacker → T2 ✓ → gets T3'
  user     → T2   → already revoked → REUSE DETECTED → revoke family F → attacker's T3' dies too
```
Either way the stolen token buys **at most one use**, and the next legitimate
action kills the whole session. The user notices (they're logged out), which
is also a signal (spec §7.1).

Why **family**, and not just the user's tokens? A user may be logged in on a phone and
a laptop, i.e. two families. Reuse on the phone session shouldn't log out the laptop.

### The refresh decision table (what 1.13 must implement and test)

| Presented token | Outcome |
|---|---|
| Hash not found | 401. Nothing to revoke (never issued, or cleaned up) |
| Found, active, not expired | **Rotate**: revoke it, link `replaced_by_id`, issue new refresh + access tokens |
| Found, `revoked_at` set | **Reuse**: revoke the whole family, 401. Log a security event (without the token) |
| Found, active, expired | 401. (Revoke it? Harmless either way; decide) |
| Found, but user suspended (7.2) or deleted | 401, revoke the family |

All of "rotate" happens in **one transaction**: revoke old + insert new must be atomic.

### The race you must think about (transactions.md)

Two requests with the **same** valid token arrive together: two browser
tabs, or a mobile app retrying after a timeout. Under READ COMMITTED without
locking, both read "active", both rotate, and there are two live children.

With a row lock (`SELECT … FOR UPDATE`) or a conditional update
(`… WHERE id = ? AND revoked_at IS NULL`), the second request finds it revoked,
which **looks like reuse**, so the family is revoked and the user is logged out.

That's **secure but can be unfriendly**. Options:
1. Accept it; the frontend serialises refreshes (only one refresh in flight;
   others wait for it). Common, and simplest on the server.
2. A short **grace period**: if a revoked token was replaced < N seconds ago,
   treat it as a benign race (return an error but don't revoke, or return the
   same child). More complex and weakens detection slightly.

Choose one, write it in the ADR at 1.18, and **test the concurrent case**
(two threads, one latch, same token). This is exactly the kind of thing
interviewers probe.

### Logout (1.14)

`POST /auth/logout` (authenticated): revoke the presented refresh token, or
its whole family. Decide which. The access token remains valid until its `exp`
(≤ 15 min), which is the known JWT trade-off (`jwt.md`). Say it explicitly in the
API docs.

"Log out everywhere" = revoke all families for the user. Useful for 7.2
(suspension) and a password change later.

### Lifetimes

- Each refresh token: 7 days (spec). With rotation, the session **slides**:
  an active user is never logged out.
- Optional **absolute** family lifetime (e.g. 30 days since login, via the
  first token's `issued_at`) forces periodic re-login. Not in the spec; a
  reasonable addition to discuss.

### Housekeeping

Revoked and expired rows accumulate. A scheduled cleanup (`@Scheduled`,
delete rows expired > N days ago) belongs in a later phase. Note it; don't
build it now.

### Client side (preview of Phase 2, slice 2.18)

Where the browser keeps the refresh token is a real design decision:
memory + httpOnly cookie, or localStorage (XSS-exposed). Tokens in cookies
reintroduce CSRF concerns (`spring-security.md`). Phase 1 returns both tokens in
the JSON body; revisit in 2.18.

### Testing checklist (1.13 done-when)

Unit (`RefreshTokenServiceTest`, mocks + fixed `Clock`):
- rotate: old revoked, `replaced_by_id` set, new token same family, new expiry
- reuse: presenting a revoked token revokes every token in the family
- expired: rejected
- unknown: rejected, nothing revoked

Integration:
- login → refresh → refresh works end to end through HTTP
- replaying the first refresh token → 401 **and** the latest token no longer works
- concurrent double refresh with the same token → behaves as your ADR says
- the stored `token_hash` ≠ the raw token returned to the client

### Read next

- IETF *OAuth 2.0 Security Best Current Practice* (RFC 9700), section on refresh token rotation
- Auth0 docs: *Refresh Token Rotation* and *Automatic Reuse Detection* (clear diagrams of the same design)
- OWASP *Session Management Cheat Sheet*

### Check yourself

1. Why does a replayed refresh token mean theft rather than a bug? When might it actually be a bug?
2. Why revoke the **family** and not all of the user's tokens?
3. Walk through Case B (attacker first). When does the attacker lose access?
4. Two tabs refresh simultaneously. What does your implementation do, and why is that acceptable?
5. After logout, how long can the access token still be used, and why is that tolerable?

---

## My notes

*Write this yourself after slices 1.13 and 1.14. Include your race-condition
decision, and draw the family diagram from memory.*
