# Spring MVC: REST controllers and the request path

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### The path of one request

```
HTTP POST /api/v1/notes  {"title":"…"}
   │
   ▼
Tomcat (virtual thread)
   │
   ▼
Servlet filter chain ── Spring Security filters (1.6+): may reject with 401/403 here
   │
   ▼
DispatcherServlet
   ├─ HandlerMapping: which controller method matches POST /api/v1/notes?
   ├─ Argument resolution:
   │     @RequestBody  → Jackson deserialises JSON → record
   │     @Valid        → Bean Validation runs → 400 if invalid
   │     @PathVariable, @RequestParam, @AuthenticationPrincipal…
   ├─ Controller method runs → calls ONE service method
   ├─ Return value → Jackson serialises DTO → JSON
   └─ Exception anywhere above → @RestControllerAdvice → problem+json
```

### Layers and their jobs (CLAUDE.md)

| Layer | Package | Does | Never does |
|---|---|---|---|
| Controller | `feature.api` | HTTP: parse, `@Valid`, call one service method, pick status, map to response | Business rules, repository access, transactions |
| DTOs | `feature.api.dto` | Request/response shapes (Java **records**) | JPA annotations |
| Service | `feature.service` | Business rules, authorisation (CLAUDE.md #7), `@Transactional` | Know about HTTP (`HttpServletRequest`, status codes) |
| Mapper | `feature.mapper` | Entity ↔ DTO | Logic |
| Domain | `feature.domain` | Entities, repositories, enums | Know about DTOs |

A good test: could this service method be called from a WebSocket handler or a
scheduled job (Phases 5, 6) without change? If it takes an `HttpServletRequest`, no.

### A thin controller (neutral example)

```java
@RestController
@RequestMapping("/api/v1/notes")
public class NoteController {

    private final NoteService notes;

    public NoteController(NoteService notes) { this.notes = notes; }

    @PostMapping
    public ResponseEntity<NoteResponse> create(@Valid @RequestBody CreateNoteRequest request) {
        NoteResponse created = notes.create(request);
        URI location = URI.create("/api/v1/notes/" + created.id());
        return ResponseEntity.created(location).body(created);          // 201 + Location header
    }

    @GetMapping("/{id}")
    public NoteResponse get(@PathVariable Long id) {                    // 200 implied
        return notes.get(id);                                           // service throws NotFoundException → 404
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)                              // 204, no body
    public void delete(@PathVariable Long id) { notes.delete(id); }
}
```

```java
public record CreateNoteRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 5000) String body) { }

public record NoteResponse(Long id, String title, Instant createdAt) { }
```
Records are ideal DTOs: immutable, concise, and Jackson (de)serialises them natively.

### Status codes this API uses

| Code | When |
|---|---|
| 200 OK | Successful read or action returning a body |
| 201 Created | Resource created, with `Location` header |
| 204 No Content | Success, nothing to return (e.g. logout) |
| 400 Bad Request | Malformed JSON, validation failure |
| 401 Unauthorized | Not authenticated |
| 403 Forbidden | Authenticated but not allowed |
| 404 Not Found | Doesn't exist (or hidden, see `authorization.md`) |
| 409 Conflict | State conflict: duplicate email, illegal status transition |
| 429 Too Many Requests | Rate limited (Phase 4) |

### Designing an endpoint: questions to answer before coding

1. What exactly is in the request DTO? Which fields does the **server** decide
   (id, role, campus, timestamps)? Those must never be accepted from the client.
2. What does the response contain? Anything sensitive (hash, full email of others)?
3. Which statuses can it return, and which exception produces each?
4. Who may call it (the authorization matrix, §7.2)?
5. Is it idempotent? What happens on a double submit?

For `POST /auth/register`: the client sends email, password, display name.
The server decides campus (from the domain), role (`USER`), `email_verified` (`false`).

### Jackson settings that matter

- `default-property-inclusion: non_null` (set): null fields omitted.
- `write-dates-as-timestamps: false` (set): `Instant` → ISO-8601 string.
- **Unknown fields:** spec §7.4 says *reject unknown JSON fields*. Boot's default
  is to **ignore** them. You need
  `spring.jackson.deserialization.fail-on-unknown-properties: true`, which turns
  `{"email":…, "role":"ADMIN"}` into a 400 instead of silently dropping `role`.
  Decide in 1.7 and add a test.

### Base path `/api/v1`

Options: put `/api/v1/...` in every `@RequestMapping` (explicit, greppable), or
a shared constant. Avoid `server.servlet.context-path`; it would also move
`/actuator` and Swagger.

### Gotchas

- **Missing `@RequestBody`** → fields silently null (Spring tries to bind from
  query params instead).
- **Missing `@Valid`** → annotations on the record are ignored; no validation.
- **Returning an entity** → leaks fields and triggers lazy-loading errors. Always a DTO.
- **Business logic creeping into the controller** ("if user is unverified,
  return 403") → it belongs in the service (CLAUDE.md #7), where WebSocket and
  jobs also pass through.
- **`@Transactional` on a controller** → CLAUDE.md says service methods only.

### Read next

- Spring Framework reference: *Web MVC → Annotated Controllers* (request mapping, handler methods, `@RequestBody`, `ResponseEntity`)
- Spring Framework reference: *Web MVC → DispatcherServlet* (processing sequence)
- Spring Boot reference: *Web → Servlet Web Applications → Spring MVC* (JSON, Jackson customisation)
- MDN: *HTTP response status codes*

### Check yourself

1. List every component a `POST` passes through before your controller method runs.
2. Why is `campus_id` never a field of the registration request?
3. When is 201 appropriate vs 200? What header comes with 201?
4. Why keep `HttpServletRequest` out of services?

---

## My notes

*Write your own notes after the corresponding TicketingSystem slice.*
