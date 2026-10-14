# Email verification tokens

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### What the flow must guarantee

1. Only someone with access to the inbox can verify (proves campus membership, §1.4 goal 4).
2. A token works **once** (`consumed_at`) and **expires** (24 h, `expires_at`).
3. A database leak doesn't expose usable tokens (stored **hashed**).
4. Nothing sensitive leaks through logs, URLs in logs, or error messages.

```
register ─► generate random token ─► store SHA-256(token) + expires_at
         └► (after commit) email link containing the RAW token ─► Mailpit
user clicks ─► frontend page reads token from URL ─► POST /auth/verify-email {token}
            ─► server hashes it ─► finds row ─► checks not expired, not consumed
            ─► sets users.email_verified = true, token.consumed_at = now()  (one transaction)
```

### Generating a token

```java
private static final SecureRandom RANDOM = new SecureRandom();

byte[] bytes = new byte[32];                                      // 256 bits
RANDOM.nextBytes(bytes);
String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);   // URL-safe, 43 chars
```
- **`SecureRandom`**, never `Random` or `Math.random()`: those are predictable.
- **`UUID.randomUUID()`** uses `SecureRandom` and gives 122 random bits. That's
  acceptable, but 32 random bytes is the more deliberate choice.
- **URL-safe Base64** because it goes into a link.

### Storing it hashed, and why SHA-256 is fine here (bcrypt isn't needed)

```java
MessageDigest sha = MessageDigest.getInstance("SHA-256");
byte[] digest = sha.digest(token.getBytes(StandardCharsets.UTF_8));
String hash = HexFormat.of().formatHex(digest);                  // 64 hex chars
```
Passwords need a slow hash because humans pick **guessable** passwords. A
256-bit random token can't be guessed, so a fast hash is enough to make a leaked
DB useless, and it allows a direct **indexed lookup** by hash
(`WHERE token_hash = ?`), which bcrypt (random salt) wouldn't.

The same helper will be reused for refresh tokens (1.13). A small shared utility
(e.g. in `common/security` or `auth`) is justified once there are two users of it.

### Sending the email

- Add `spring-boot-starter-mail`. `application-local.yml` already has
  `spring.mail.host/port` pointing at Mailpit (1025). View mail at http://localhost:8025.
- `JavaMailSender` + `SimpleMailMessage` (plain text) is enough for now.
- Send **after commit** (`transactions.md`): `@TransactionalEventListener(AFTER_COMMIT)`.
- **The link:** where does it point? The frontend doesn't exist until Phase 2
  (ADR-011). Make the base URL a property (e.g. `app.frontend-base-url`) and
  point it at a future frontend route like `/verify-email?token=…`. Until then,
  copy the token from Mailpit into Swagger.
- **Adding the mail starter adds a `mail` health indicator** (`actuator.md`): if
  Mailpit is down, `/actuator/health` goes DOWN. Is that what you want? Consider
  `management.health.mail.enabled: false`, since mail is not critical to serving traffic.

### Token in URL vs body

The link must carry the token in the URL (that's how email links work). But the
**API call** should be `POST /auth/verify-email` with the token in the **body**:
- query strings end up in access logs, proxies, browser history;
- GET requests must be safe/idempotent; this changes state;
- email scanners and link previewers **GET every link** automatically. A GET
  endpoint that verifies would be triggered by the scanner, not the user.

So: link → frontend page → page POSTs the token. That's why §8 defines it as POST.

### Consuming a token (slice 1.9)

In one `@Transactional` method:
1. Hash the submitted token; look up by hash.
2. Not found → reject.
3. `consumed_at` not null → reject (already used).
4. `expires_at` before now → reject.
5. Set `consumed_at = now`, mark the user verified.

Decisions to make and test:
- **Error responses:** one generic "invalid or expired link" for all failure
  cases, or distinct ones? Distinct messages help users ("already verified, just
  log in") and leak little here, since the token itself is the secret. Choose deliberately.
- **Already-verified user clicks again:** error, or success (idempotent)? An
  idempotent 200 is friendlier.
- **Multiple outstanding tokens** (after a future "resend"): invalidate older
  ones when issuing a new one?
- **Concurrency:** two clicks at once. Worst case both succeed, which is
  harmless here (unlike refresh tokens), but you could guard with a conditional update.

### Time and testing

- Use an injected `java.time.Clock` bean (`Clock.systemUTC()` in production).
  Tests use `Clock.fixed(...)` or a mutable test clock to test expiry at
  exactly 24 h − 1 s and 24 h + 1 s. Never `Thread.sleep`.
- **Testing mail:** options for the IT (decide in 1.8):

| Option | How | Trade-off |
|---|---|---|
| Mock `JavaMailSender` (`@MockitoBean`) | verify `send(...)` was called, capture the message | Fast; doesn't test SMTP; creates a separate Spring context |
| **GreenMail** (in-JVM SMTP server library) | real SMTP to localhost, read received messages | Real SMTP path; extra test dependency |
| Mailpit container (Testcontainers `GenericContainer`) | real SMTP; read via Mailpit's HTTP API | Most realistic; another container, more code |

Whichever you choose, the test should extract the raw token from the email and
use it to call `/auth/verify-email`. That's the real end-to-end path.

### What never happens

- Logging the raw token or the full verification link (CLAUDE.md #8).
- Storing the raw token.
- Logging the full recipient email. Mask it (`a***@mitaoe.ac.in`) if you log anything.

### Read next

- OWASP *Forgot Password Cheat Sheet* (same token mechanics: generation, storage, expiry, single use)
- Spring Boot reference: *IO → Sending Email*
- Java API docs: `java.security.SecureRandom`, `java.util.HexFormat`, `java.time.Clock`

### Check yourself

1. Why SHA-256 for the token but bcrypt for the password?
2. Why does the verify endpoint use POST with the token in the body?
3. How do you test the 24-hour expiry without waiting?
4. What happens to `/actuator/health` when Mailpit is stopped after you add the mail starter?

---

## My notes

*Write this yourself after slices 1.8 and 1.9. Record your choices: error
messages, idempotency, and how you tested mail.*
