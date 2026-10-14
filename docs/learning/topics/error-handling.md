# Error handling and RFC 7807 Problem Details

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### The goal

Every error the API returns has **the same shape**, a correct HTTP status, a
message safe to show a user, and **no internals** (stack traces, SQL, class names).
The frontend then needs exactly one error-handling path.

### RFC 7807 / RFC 9457: Problem Details

A standard JSON error format, content type `application/problem+json`:
```json
{
  "type": "https://shs.example/problems/email-taken",
  "title": "Email already registered",
  "status": 409,
  "detail": "An account with this email already exists.",
  "instance": "/api/v1/auth/register",
  "code": "EMAIL_TAKEN"
}
```
| Field | Meaning |
|---|---|
| `type` | URI identifying the *kind* of problem (doesn't need to resolve; `about:blank` is the default) |
| `title` | Short, human-readable summary of the *type* (same for every occurrence) |
| `status` | HTTP status, repeated in the body |
| `detail` | Explanation of *this* occurrence |
| `instance` | Which request / resource |
| extra members | Allowed: e.g. `code`, `errors` (field list), `retryAfter` |

A stable machine-readable **`code`** is worth adding: the frontend switches on
`code`, never on English text.

### Spring's built-in support (Spring Framework 6+)

- **`ProblemDetail`**: a class representing the body. Returning one from an
  `@ExceptionHandler` makes Spring write `application/problem+json`.
  ```java
  ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, "…");
  pd.setTitle("Email already registered");
  pd.setType(URI.create("https://shs.example/problems/email-taken"));
  pd.setProperty("code", "EMAIL_TAKEN");
  ```
- **`ErrorResponseException`**: a `RuntimeException` that already carries a
  `ProblemDetail`. `ApiException` can extend it, or be a plain `RuntimeException`
  with a status and code that the handler converts. Both are fine.
- **`ResponseEntityExceptionHandler`**: a base class for your advice that already
  maps Spring MVC's own exceptions (bad JSON, unsupported media type, missing
  parameter, validation…) to `ProblemDetail`. Extend it and override only what
  you want to customise, typically validation errors.

### The pieces in this project (spec §5.2)

```
common/exception/
  ApiException              base: HTTP status + code + safe message (RuntimeException → rolls back tx)
  NotFoundException         404
  ForbiddenException        403
  ConflictException?        409 (e.g. email taken; not in the spec list, likely needed)
  RateLimitExceededException 429 (Phase 4)
  GlobalExceptionHandler    @RestControllerAdvice, extends ResponseEntityExceptionHandler
```
Spec §5.2 also lists `common/dto/ApiError.java` as the RFC 7807 DTO. With Spring's
`ProblemDetail` you likely **don't need it**. If you drop it, amend the spec (1.4 review).

### What the handler must cover

| Situation | Exception | Status |
|---|---|---|
| Your domain errors | `ApiException` subclasses | their own |
| `@Valid @RequestBody` fails | `MethodArgumentNotValidException` | 400 + per-field `errors` |
| Constraint on `@PathVariable`/`@RequestParam` | `HandlerMethodValidationException` (Spring 6.1+) | 400 |
| Malformed JSON / unknown field (see `bean-validation.md`) | `HttpMessageNotReadableException` | 400 |
| DB unique violation that slipped past checks (race) | `DataIntegrityViolationException` | 409, **generic** message |
| Anything else | `Exception` | 500, generic message, **log it** with the stack trace server-side |

Validation errors example:
```json
{
  "type": "about:blank", "title": "Bad Request", "status": 400,
  "detail": "Validation failed",
  "code": "VALIDATION_FAILED",
  "errors": [ { "field": "email", "message": "must be a well-formed email address" } ]
}
```

### Security errors don't reach `@RestControllerAdvice`

Spring Security's filters run **before** the `DispatcherServlet`. A missing or
invalid token is rejected in the filter chain, where controller advice never runs.
You configure (in 1.6/1.12):
- an **`AuthenticationEntryPoint`** → writes the **401** problem body;
- an **`AccessDeniedHandler`** → writes the **403** problem body.

A common trick to keep one source of truth: inject Spring's
`HandlerExceptionResolver` (bean name `handlerExceptionResolver`) into the entry
point and delegate to it, so the advice formats security errors too.

### 401 vs 403 vs 404

| Status | Means | Example |
|---|---|---|
| 401 Unauthorized | "I don't know who you are" (no/invalid/expired token) | `/users/me` without a token |
| 403 Forbidden | "I know who you are; you may not do this" | Unverified user creates a listing |
| 404 Not Found | Doesn't exist, **or you're not allowed to know it exists** | Someone else's conversation (a choice, see `authorization.md`) |

### Safe messages and logging

- `detail` must be safe for an end user: no SQL, no class names, no IDs of other users.
- **Log unexpected (500) errors with the stack trace**; log expected 4xx at
  `DEBUG`/`INFO` without one. A 409 for a duplicate email is not an incident.
- Never include the submitted email or password in logs or `detail` (CLAUDE.md #8).
- `server.error.include-stacktrace: never` (already set) covers anything that
  escapes to Spring Boot's fallback `/error` handling.

### Testing it (done-when for 1.4)

With MockMvc (`spring-testing.md`):
```java
mockMvc.perform(get("/api/v1/does-not-exist"))
       .andExpect(status().isNotFound())
       .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
       .andExpect(jsonPath("$.status").value(404))
       .andExpect(jsonPath("$.trace").doesNotExist());
```
A handy pattern: a test-only controller under `src/test/java` that throws each
exception type, so the handler is tested independently of real features.

### Gotchas

- **Unmatched URLs** (404 for a path with no controller): by default Spring MVC
  handles these differently from thrown exceptions. Check what the body looks like; in
  Spring 6.1+ `NoResourceFoundException` is handled by `ResponseEntityExceptionHandler`.
- **Once security is on**, errors forwarded to `/error` can turn into a 401 unless
  `/error` is permitted (see `spring-security.md`).
- **Catching `Exception` in the advice** also catches exceptions Spring would
  have mapped correctly (e.g. `AccessDeniedException` from method security → you'd
  return 500 instead of 403). Handle those explicitly or let them propagate.
- `DataIntegrityViolationException` messages contain SQL and constraint names.
  Never put `ex.getMessage()` into `detail`.

### Read next

- RFC 9457 *Problem Details for HTTP APIs* (supersedes RFC 7807; same format), sections 3–4
- Spring Framework reference: *Web MVC → Error Responses* (`ProblemDetail`, `ErrorResponse`)
- Spring Framework reference: *Web MVC → Exceptions → `@ExceptionHandler`*, *Controller Advice*
- Spring Security reference: *Servlet → Architecture → Handling Security Exceptions*

### Check yourself

1. Why can't `@RestControllerAdvice` produce the 401 for a missing token?
2. Why a `code` field in addition to `title`?
3. A unique-violation reaches the handler. What status and what `detail`, and why not `ex.getMessage()`?
4. Which errors deserve a stack trace in the logs?

---

## My notes

*Write this yourself after reviewing slice 1.4. Walk through one request that
fails validation and describe every class it passes through.*
