# Password hashing

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### Why hash, and why not SHA-256

The database will leak one day (backup, SQL injection, insider). The goal:
leaked data must not reveal passwords.
- **Encryption** is reversible with a key, which also leaks. No.
- **Plain SHA-256** is one-way, but **fast**: a GPU computes billions per
  second, so every common password is cracked in minutes. And identical passwords
  give identical hashes. No.
- **A password-hashing function** (bcrypt, scrypt, Argon2, PBKDF2) is
  deliberately **slow**, **salted** (a random value per password, stored with
  the hash, so identical passwords hash differently and precomputed tables are
  useless), and **tunable** (raise the cost as hardware improves).

### bcrypt in one picture

```
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 │  │  └──── 22 chars salt ──────┘└──── 31 chars hash ──────┘
 │  └─ cost: 2^10 = 1024 rounds
 └──── algorithm version
```
Everything needed to verify is in the string. There is no separate salt column.
Each cost +1 **doubles** the time. Target roughly 100–300 ms per hash on your
server: slow for an attacker, tolerable for one login.

### In Spring Security

```java
@Bean
PasswordEncoder passwordEncoder() {
    return PasswordEncoderFactories.createDelegatingPasswordEncoder();
}
```
or explicitly `new BCryptPasswordEncoder(12)`.

| Option | Stored value | Upside |
|---|---|---|
| `BCryptPasswordEncoder` | `$2a$10$…` | Simple; matches the spec literally |
| `DelegatingPasswordEncoder` (default bcrypt) | `{bcrypt}$2a$10$…` | Prefix lets you migrate to Argon2 later without breaking existing hashes; `upgradeEncoding()` tells you when to rehash |

Either is fine. Pick one in 1.6 and note it in the ADR at 1.18. The
`password_hash VARCHAR(255)` column fits both.

Usage:
```java
String hash = encoder.encode(rawPassword);           // new random salt each call
boolean ok  = encoder.matches(rawPassword, hash);    // re-hashes with the stored salt, compares
```
**Never compare hashes yourself** (`encode(x).equals(stored)` is always false,
because the salt differs). Always use `matches`.

### The 72-byte limit

bcrypt only uses the first **72 bytes** of the input. Two passwords sharing the
first 72 bytes would both match. Recent Spring Security versions refuse longer
input (encode throws; matches returns false) after a 2025 CVE. So:
- enforce a **max length** in the request DTO (`bean-validation.md`), measured in
  UTF-8 bytes if you want to be exact;
- test the boundary.

### Timing and user enumeration (slice 1.11)

A naive login:
```
user not found  → return 401 immediately              (~1 ms)
user found      → bcrypt.matches → 401 or 200         (~100 ms)
```
The **response time** reveals whether an email is registered, even when the
message is identical. Mitigation: when the user doesn't exist, still run
`matches` against a fixed dummy bcrypt hash, so both paths cost the same.
Registration can also leak existence ("email already registered"). That's an
accepted, common trade-off for usability; rate limiting (Phase 4) limits abuse.
Note which trade-off you chose.

### Virtual threads note

bcrypt is **CPU-bound**, not I/O. Virtual threads don't make it faster; many
concurrent logins still compete for CPU cores. It's another reason login gets a
rate limit (§7.3).

### What never happens

- Logging the raw password or the hash (CLAUDE.md #8). Watch `toString()` on
  entities and DTOs: a record's default `toString` prints **every field**,
  including `password`. Override it on request records that carry passwords.
- Returning `passwordHash` in any DTO.
- Sending the password in a query string (it ends up in access logs).

### Testing

- Assert the stored value **is not** the raw password and **starts with** `$2`
  (or `{bcrypt}$2`), and that `encoder.matches(raw, stored)` is true.
- Tests get slower with high cost factors. A test profile can use a lower
  cost (e.g. 4) via a property. Keep the production value in config.

### Read next

- OWASP *Password Storage Cheat Sheet*
- Spring Security reference: *Features → Authentication → Password Storage*
- NIST SP 800-63B §5.1.1.2 (*Memorized Secret Verifiers*)

### Check yourself

1. Why is bcrypt safer than SHA-256 for passwords, while SHA-256 is fine for random tokens?
2. Where is the salt stored?
3. How can response *timing* leak whether an email is registered, and how do you prevent it?
4. What goes wrong with a 100-character password and plain bcrypt?

---

## My notes

*Write your own notes after the corresponding TicketingSystem slice.*
