# OpenAPI and Swagger UI (springdoc)

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### What it is

- **OpenAPI** is a standard JSON/YAML description of an HTTP API: paths,
  parameters, request/response schemas, auth.
- **springdoc-openapi** generates that description **from your code at runtime**
  (controllers, DTO records, validation annotations) at `/v3/api-docs`.
- **Swagger UI** renders it as an interactive page at `/swagger-ui.html`, where
  you can call endpoints from the browser.

In Phase 1 Swagger is your frontend (ADR-011): you'll register, verify and log in through it.

### What you get for free

- Every `@RestController` mapping becomes an operation.
- Records become schemas; field names and types are inferred.
- **Bean Validation annotations show up** in the schema (`@NotBlank` → required,
  `@Size(max=160)` → `maxLength: 160`). Validation and docs can't drift.
- `@PathVariable` / `@RequestParam` become parameters.

So most of 1.17 is not "document everything", but three targeted additions.

### 1. Bearer auth, so the Authorize button works

Declare a security scheme once (spec §5.2 `OpenApiConfig`):
```java
@Configuration
@SecurityScheme(name = "bearerAuth", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
public class OpenApiConfig { … }
```
Then mark protected operations (or a whole controller):
```java
@SecurityRequirement(name = "bearerAuth")
```
Flow in the UI: call `POST /auth/login` → copy `accessToken` → **Authorize** →
paste → protected calls now send `Authorization: Bearer …`.

The alternative is a global requirement on all operations, with public ones
opting out. Either works; per-controller is explicit.

### 2. Error responses documented

By default only the success response appears. Document the problem+json
responses an operation can return:
```java
@ApiResponse(responseCode = "409", description = "Email already registered",
             content = @Content(mediaType = "application/problem+json",
                                schema = @Schema(implementation = ProblemDetail.class)))
```
Repeating 400/401/403 on every operation is noisy. Options: annotate only
operation-specific errors (409, 404), and add the common ones globally with an
`OpenApiCustomizer` bean. Keep annotations proportional; the goal is accuracy,
not decoration.

### 3. Grouping and descriptions

```java
@Tag(name = "Auth", description = "Registration, login, token refresh")
@Operation(summary = "Register a new account",
           description = "Email domain must belong to a campus. Sends a verification email.")
```
`@Schema(description = "…", example = "rahul@mitaoe.ac.in")` on record
components gives useful examples in the UI.

Also set API-level info (title, version, description) in `OpenApiConfig` via an
`OpenAPI` bean.

### Security interaction

Once `spring-boot-starter-security` is added (1.6), Swagger returns 401 unless
permitted: `/swagger-ui.html`, `/swagger-ui/**`, `/v3/api-docs/**`
(`spring-security.md`). In production (Phase 8) you might disable Swagger
(`springdoc.swagger-ui.enabled: false` / `springdoc.api-docs.enabled: false`
in a prod profile) or leave it; decide then.

### Don't leak through docs

- Response DTOs are what's documented. If a schema shows `passwordHash`, the
  **DTO** is wrong, not the docs.
- `example` values: fake data only, no real emails or tokens.

### Using the generated spec

- `curl localhost:8080/v3/api-docs | jq` is the raw spec.
- Phase 2: the frontend's JSDoc typedefs (`src/types/apiShapes.js`) can be
  written against it, and it's the contract the frontend team (you) reads.

### Gotchas

- **springdoc version vs Boot version:** springdoc 2.x is for Boot 3; each minor
  targets specific Boot versions. When upgrading Boot, check springdoc's
  compatibility notes.
- Annotations from `io.swagger.v3.oas.annotations` (OpenAPI 3), not the old
  `io.swagger.annotations` (Swagger 2 / springfox). Tutorials mixing them won't compile.
- Records: put `@Schema` on the record **component**, which works in current springdoc.
- Generic wrappers (`PageResponse<T>`, Phase 2) sometimes render poorly; check the UI.

### Read next

- springdoc-openapi docs: *Getting started*, *Migrating from springfox* (for annotation names), *Security*
- OpenAPI Specification 3.x: *Security Scheme Object*, *Responses Object*
- Swagger docs: *Bearer Authentication*

### Check yourself

1. Why do `@NotBlank` / `@Size` appear in the generated schema, and why is that valuable?
2. What does the `@SecurityScheme` + `@SecurityRequirement` pair do in the UI?
3. A schema in Swagger shows a field that shouldn't be public. What do you fix?

---

## My notes

*Write your own notes after the corresponding TicketingSystem slice.*
