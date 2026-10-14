# Bean Validation

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### What it is

Jakarta Bean Validation (implementation: Hibernate Validator, pulled in by
`spring-boot-starter-validation`) lets you declare constraints as annotations,
and Spring runs them automatically at the controller boundary.

### Two kinds of validation

| Kind | Where | Example | Tool |
|---|---|---|---|
| **Shape** (is the input well-formed?) | Request DTO | email looks like an email; password 10–72 chars | Bean Validation annotations |
| **Business** (is it allowed given current state?) | Service | email domain belongs to a campus; email not already taken | Code in the service, throws `ApiException` |

Shape validation is cheap and needs no DB. Business validation needs the DB and
belongs in the service. Don't try to do DB lookups from a constraint validator.

### Common constraints

| Annotation | Checks | Null OK? |
|---|---|---|
| `@NotNull` | not null | — |
| `@NotEmpty` | not null, length/size > 0 | no |
| `@NotBlank` | not null, contains a non-whitespace char (Strings) | no |
| `@Size(min, max)` | length of String / size of collection | yes |
| `@Email` | roughly an email | yes |
| `@Pattern(regexp)` | matches regex | yes |
| `@Min` / `@Max` / `@Positive` / `@PositiveOrZero` | numbers | yes |
| `@Past` / `@Future` | temporal | yes |

**Most constraints treat `null` as valid.** Combine with `@NotNull`/`@NotBlank`
for required fields.

### On records (neutral example)

```java
public record CreateNoteRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 5000) String body,
        @NotNull @PositiveOrZero Long priceCents) { }
```
And trigger it in the controller: `create(@Valid @RequestBody CreateNoteRequest request)`.
Failure throws `MethodArgumentNotValidException` → 400 via your advice (`error-handling.md`).

### Messages

Each constraint has a default message (`must not be blank`). Override per use:
```java
@Size(min = 10, max = 72, message = "must be between 10 and 72 characters")
```
The error response lists field + message, so make messages useful to a user.

### Custom constraints

When a rule is reusable and shape-only (password strength), write your own:
```java
@Target({ElementType.FIELD, ElementType.RECORD_COMPONENT, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {
    String message() default "…";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext ctx) {
        if (value == null) return true;            // let @NotBlank handle null
        …
    }
}
```
The three members (`message`, `groups`, `payload`) are mandatory boilerplate.
Validators are plain classes, easy to unit test without Spring.

### Password rules: what "strength" should mean (Flow A step 3)

Current guidance (NIST SP 800-63B) moved away from composition rules ("one
uppercase, one digit, one symbol"), which push users to `Password1!`. Instead:
- **minimum length** (commonly 8; 10–12 is reasonable here);
- **maximum length**, which matters with bcrypt: it only uses the first
  **72 bytes**, and recent Spring Security versions reject longer input (see
  `password-hashing.md`). Note that `@Size` counts *characters*, and non-ASCII
  characters take 2–4 bytes. A custom validator can check the UTF-8 byte length;
- optionally: reject very common passwords (a small list) or the user's own email.

Pick the rules in 1.7, write them in the spec (§2.2 says "checks password
strength" without defining it), and test the boundaries.

### Normalising input

Validation checks; it doesn't transform. Normalise in the service (or a
DTO factory method) before use:
```java
String email = request.email().trim().toLowerCase(Locale.ROOT);
```
**`Locale.ROOT`** matters: `toLowerCase()` with no argument uses the machine
locale, and in Turkish `"I".toLowerCase()` is `"ı"`, not `"i"`.

`@Email` is permissive (accepts `a@b`). That's fine here because the campus
domain check (business rule) is much stricter.

### Unknown fields (spec §7.4)

Bean Validation can't see fields that **aren't** in the record; Jackson drops
them silently by default. Set:
```yaml
spring:
  jackson:
    deserialization:
      fail-on-unknown-properties: true
```
Then `{"email":…, "password":…, "role":"ADMIN"}` becomes a 400
(`HttpMessageNotReadableException`) rather than a silently ignored privilege-escalation attempt.

### Validation beyond controllers

- `@Validated` on a Spring bean class + constraints on method parameters
  validates service calls too (`ConstraintViolationException`). Useful at
  boundaries other than HTTP (WebSocket messages in Phase 6).
- Spring 6.1+ also validates `@PathVariable` / `@RequestParam` constraints on
  controllers directly (`HandlerMethodValidationException`).

### Testing

- **Custom validator:** plain unit test, `new StrongPasswordValidator().isValid("…", null)`.
- **DTO constraints:** either through MockMvc (the real path, gives the full 400
  body) or with a `Validator` from `Validation.buildDefaultValidatorFactory()`.
- **Boundaries:** min − 1, min, max, max + 1. And null, empty, whitespace-only.

### Gotchas

- Annotation on the record but no `@Valid` in the controller → nothing validated.
- `@NotNull` on a `String` accepts `""`. You usually want `@NotBlank`.
- `@Size` on a `Long` does nothing useful; use `@Min`/`@Max`.
- Nested objects need `@Valid` on the field to cascade.

### Read next

- Jakarta Bean Validation spec: *Built-in constraint definitions*
- Hibernate Validator reference: *Creating custom constraints*
- Spring Framework reference: *Core → Validation → Java Bean Validation*, and *Web MVC → Validation*
- NIST SP 800-63B, section 5.1.1 (*Memorized Secrets*)

### Check yourself

1. Which checks for registration are shape and which are business?
2. Why does `@Email` alone not accept `null` as invalid?
3. Why is `fail-on-unknown-properties` a security setting, not just tidiness?
4. Why `Locale.ROOT`?

---

## My notes

*Write your own notes after the corresponding TicketingSystem slice.*
