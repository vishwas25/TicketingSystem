# JWT (JSON Web Tokens)

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### What a JWT is

A compact, **signed** string carrying claims about a user. The server issues it
at login; the client sends it on each request as `Authorization: Bearer <jwt>`;
the server verifies the **signature** and trusts the claims, with **no DB
lookup and no session**. That's what makes the API stateless.

```
eyJhbGciOiJIUzI1NiJ9 . eyJzdWIiOiI0MiIsInJvbGUiOiJVU0VSIiwiZXhwIjoxNzkw…} . 3q2-7w…
└────── header ─────┘   └──────────────── payload (claims) ──────────────┘   └ signature ┘
  base64url JSON           base64url JSON                                    HMAC/RSA over header.payload
```
Paste any JWT into a decoder (jwt.io works offline in the browser) and you'll
see the header and payload in plain text.

**Signed ≠ encrypted.** Anyone holding the token can *read* the payload.
The signature only proves it wasn't *altered* and was issued by someone with
the key. **Never put secrets or personal data in claims**: no email, no
password hash.

### Standard claims

| Claim | Meaning | Here |
|---|---|---|
| `sub` | subject: who | user id (as a string), **not** the email |
| `iat` | issued at | now |
| `exp` | expires | now + 15 min |
| `iss` | issuer | e.g. `secondhandsell`, checked on parse |
| `aud` | audience | optional |
| `jti` | unique token id | optional; useful for logging or deny-lists |

Custom claims you might add: `role`, `verified`, `campusId`. Trade-off: claims
are a **snapshot** at issue time. Change the role or verify the email, and the old
token still says the old thing until it expires (≤15 min). The short lifetime is
what keeps that tolerable. Decide per claim whether the filter trusts it or
re-reads the DB (see `spring-security.md`, `authorization.md`).

### Signing: HS256 vs RS256

| | HS256 (HMAC + SHA-256) | RS256 / ES256 (asymmetric) |
|---|---|---|
| Keys | One shared secret signs **and** verifies | Private key signs, public key verifies |
| Fits | One service issues and verifies (this project) | Many services verify tokens from one issuer |
| Key size | Secret ≥ 256 bits (32 bytes); JJWT enforces it | RSA ≥ 2048 bits |
| Risk | Anyone who can verify can also forge | Verifiers can't forge |

For a modular monolith, **HS256 is a sound choice**. Record it in the ADR at 1.18,
with "move to RS256 if another service ever needs to verify tokens" as the growth path.

### The key: never in Git

- Load it from configuration: `app.jwt.secret` ← env var `APP_JWT_SECRET`.
- Store it Base64-encoded; decode to bytes; `Keys.hmacShaKeyFor(bytes)`.
- Local dev: a default in `application-local.yml` is acceptable **only** if it's
  clearly a dev-only value. Tests: a fixed test key in `application-test.yml`.
  Production (Phase 8): a Kubernetes Secret.
- Fail fast on startup if the key is missing or shorter than 32 bytes.
- Generate one: `openssl rand -base64 32`.
- Consider a `@ConfigurationProperties` record (`JwtProperties`: secret, issuer,
  access TTL) instead of scattered `@Value`s. Spec §5.2 names this `JwtConfig`.

### JJWT library (spec §3.2)

Three artifacts (current 0.12.x line; not managed by Boot, so pin the version):
- `io.jsonwebtoken:jjwt-api` (compile)
- `io.jsonwebtoken:jjwt-impl` (runtime)
- `io.jsonwebtoken:jjwt-jackson` (runtime, JSON)

API shape (0.12.x; many online examples use the **older 0.11** API with
`setSubject`, `parserBuilder`, `parseClaimsJws`, which is deprecated or removed):
```java
String jwt = Jwts.builder()
        .issuer("…")
        .subject(String.valueOf(userId))
        .claim("role", role.name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(ttl)))
        .signWith(key)                         // algorithm inferred from key (HS256 for a 256-bit HMAC key)
        .compact();

Claims claims = Jwts.parser()
        .verifyWith(key)
        .requireIssuer("…")
        .clockSkewSeconds(30)
        .clock(() -> Date.from(clock.instant()))   // JJWT's own Clock interface; lets tests control time
        .build()
        .parseSignedClaims(jwt)                    // throws on bad signature, expiry, malformed…
        .getPayload();
```
JJWT uses `java.util.Date`; convert at the edges with `Date.from(instant)` /
`date.toInstant()`. Keep `Instant` everywhere else (CLAUDE.md #5).

**Exceptions to expect when parsing** (all extend `JwtException`):
`ExpiredJwtException`, `SignatureException` / `SecurityException`,
`MalformedJwtException`, `UnsupportedJwtException`, plus `IllegalArgumentException`
for empty input. `JwtService` should turn them into a single "invalid token"
outcome (a result type or one custom exception). The filter shouldn't need to
know JJWT's exception hierarchy.

### Clock skew

Servers' clocks drift. A small allowance (`clockSkewSeconds(30)`) avoids rejecting
a token that is valid by a few seconds. Don't make it large.

### What a JWT can't do: revocation

A JWT is valid until `exp`, full stop. You can't "log it out". Hence:
- **short access-token lifetime** (15 min, the spec's value);
- a **refresh token** (long-lived, stored server-side, revocable) to get new
  access tokens (`refresh-token-rotation.md`);
- logout revokes the refresh token; the access token dies within 15 minutes.
  That's the accepted trade-off, and a common interview question (slice 1.14).

### Alternative worth knowing

Spring Security has a built-in **OAuth2 Resource Server** (`spring-boot-starter-oauth2-resource-server`)
that validates JWTs with Nimbus and needs no custom filter. The spec chose JJWT +
a hand-written filter, which is more code but shows the mechanics. Knowing both exist is
useful in interviews: "I wrote the filter to understand it; in production I'd
consider the resource-server support."

### Testing `JwtService` (unit test, no Spring)

- Issue then parse → same subject and claims.
- **Expired:** issue with a clock in the past, parse with a clock now → rejected.
- **Tampered:** change one character in the payload segment → rejected.
- **Wrong key:** parse with a different key → rejected.
- **`alg: none`** / unsigned token → rejected (JJWT does this, but prove it).
- Key shorter than 256 bits → fails at construction.

### What never happens

- Logging the token or the `Authorization` header (CLAUDE.md #8).
- Putting the token in a URL query parameter (spec: WebSocket uses the STOMP
  CONNECT frame instead; CLAUDE.md gotcha).
- Email or other personal data in claims.

### Read next

- RFC 7519 *JSON Web Token*, sections 4.1 (registered claims) and 7.2 (validation)
- JJWT README (GitHub `jwtk/jjwt`): *Creating a JWT*, *Reading a JWT*, *Signature algorithms keys*
- OWASP *JSON Web Token Cheat Sheet for Java*
- Spring Security reference: *OAuth2 → Resource Server → JWT* (the alternative)

### Check yourself

1. Anyone can decode a JWT. Why can't they change their `role` claim?
2. Why is the subject the user id and not the email?
3. A user is made ADMIN. When does their access token reflect it?
4. Why HS256 here, and when would you switch to RS256?
5. How do you "log out" a JWT?

---

## My notes

*Write your own notes after the corresponding TicketingSystem slice.*
