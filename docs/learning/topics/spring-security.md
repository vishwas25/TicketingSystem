# Spring Security

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### The mental model: a chain of servlet filters

Spring Security is a set of **servlet filters** that run *before* your
controllers. Each filter does one job; any of them can stop the request.

```
Request
  │
  ▼
DelegatingFilterProxy ─► FilterChainProxy ─► SecurityFilterChain (yours, from SecurityConfig)
                                                │
          ┌─────────────────────────────────────┘
          ▼
   CorsFilter
   CsrfFilter                   (you'll disable: stateless token API)
   … LogoutFilter, etc.
   JwtAuthenticationFilter      ← yours (1.12): reads Bearer token, sets SecurityContext
   AnonymousAuthenticationFilter  (no auth yet? mark as anonymous)
   ExceptionTranslationFilter     (turns auth exceptions into 401 / 403)
   AuthorizationFilter            (checks your requestMatchers rules)
          │
          ▼
DispatcherServlet → your controller
```

**`SecurityContextHolder`** holds the current **`Authentication`** for this
request (thread-local, which works per virtual thread). If a filter puts an
authenticated `Authentication` there, later code sees the user. If none is set,
the request is anonymous.

### What adding the starter does (step 1 of slice 1.6)

Add `spring-boot-starter-security` and restart, **before writing any config**:
- every endpoint (including `/actuator/health`, Swagger) returns **401**;
- a generated password is printed in the log for a user named `user`;
- a login form and HTTP Basic are enabled.

Seeing this default first makes the rest make sense: your `SecurityConfig`
replaces it.

### Your `SecurityFilterChain`

Security 6 style: a `@Bean` method with the lambda DSL (the old
`WebSecurityConfigurerAdapter` is gone, so ignore tutorials that use it).

```java
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http /*, your filter, entry point … */) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(f -> f.disable())
            .httpBasic(b -> b.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/actuator/health/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            // 1.12: .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            // 1.12: .exceptionHandling(e -> e.authenticationEntryPoint(…).accessDeniedHandler(…))
            ;
        return http.build();
    }
}
```
Treat this as a starting shape to discuss while pairing, not a finished answer.
Questions to settle:
- **Order matters:** rules are checked top to bottom; the first match wins. `anyRequest()` goes last.
- Which `/auth/**` endpoints should really be public? `logout` requires a user (§8).
- Which public GETs come in Phase 2 (`GET /listings/**`, `/categories`)?
- `/actuator/prometheus` public or not? (Decided in 3.7.)
- CORS for the frontend (Phase 2): `cors(...)` with a `CorsConfigurationSource`
  allowing only known origins (§7.4).

### Why these settings

| Setting | Why |
|---|---|
| **STATELESS** | No `HttpSession`/`JSESSIONID`. Every request carries its token. Scales across instances with no sticky sessions |
| **CSRF disabled** | CSRF exploits the browser *automatically* attaching cookies. A token in the `Authorization` header is never auto-attached, so there's nothing to forge. **If you ever move tokens into cookies, CSRF protection comes back.** |
| **formLogin / httpBasic disabled** | Login is your own JSON endpoint issuing JWTs |
| **`/error` permitted** | When a request fails, Spring forwards to `/error`. If that's secured, a 404 or 400 becomes a confusing 401 |

### Authentication for login (slice 1.11): two approaches

1. **Manual in `AuthService`:** load the user by email, `passwordEncoder.matches`,
   check suspended, issue tokens. Explicit, easy to follow.
2. **Spring's `AuthenticationManager`** with a `UserDetailsService` (spec §5.2
   lists `UserDetailsServiceImpl`) + `DaoAuthenticationProvider`: Spring does
   the lookup and password check and throws `BadCredentialsException`,
   `DisabledException`, etc.

Both are legitimate. (2) is "the Spring way" and plugs into features like
password upgrade; (1) has less magic. Decide while pairing. Whichever you choose,
unknown-email and wrong-password must look identical (`password-hashing.md`).

### The JWT filter (slice 1.12): outline, not code

`JwtAuthenticationFilter extends OncePerRequestFilter`. Per request:
1. No `Authorization: Bearer …` header → do nothing, continue the chain
   (the request stays anonymous; the rules decide if that's OK).
2. Header present → parse and validate with `JwtService` (`jwt.md`).
3. Invalid or expired → **don't** set an authentication. Either continue (the
   entry point will produce a 401 for protected paths) or respond 401 directly.
   Pick one and be consistent.
4. Valid → build an `Authentication` whose **principal** is your own small
   `AuthenticatedUser` record (id, role, verified flag; **not** the JPA entity),
   with authorities like `ROLE_USER`, then
   `SecurityContextHolder.getContext().setAuthentication(auth)`.
5. Continue the chain.

Design decision to make: does the filter **hit the DB** per request (to see
suspension immediately) or trust the token's claims until expiry (15 min)?
This connects to 7.2 (suspending users). Write down your choice.

Registering the filter: if it's a `@Component`, Spring Boot *also* registers it
as a normal servlet filter, so it runs twice. Either don't annotate it (create
it in `SecurityConfig`) or add a `FilterRegistrationBean` with `setEnabled(false)`.

### 401 vs 403 in the filter world

- `ExceptionTranslationFilter` catches `AuthenticationException` → calls the
  **`AuthenticationEntryPoint`** → **401**.
- It catches `AccessDeniedException`: if the user is anonymous → entry point (401);
  if authenticated → **`AccessDeniedHandler`** → **403**.
- Defaults return empty or HTML bodies. Replace both so they return
  `application/problem+json` (`error-handling.md`).

### Roles and authorities

- An **authority** is a string the user has, e.g. `ROLE_ADMIN`.
- `hasRole("ADMIN")` checks for the authority `ROLE_ADMIN` (prefix added for
  you). `hasAuthority("ROLE_ADMIN")` is the same thing, spelled out.
- Your `Role` enum `USER / MODERATOR / ADMIN` maps to `ROLE_USER` etc.
- Is ADMIN automatically also MODERATOR? Only if you say so (`RoleHierarchy`
  bean, or grant both authorities). §7.2 implies "MODERATOR or ADMIN". Decide.

### Getting the current user in a controller

```java
@GetMapping("/me")
public MeResponse me(@AuthenticationPrincipal AuthenticatedUser user) { … }
```
Covered further in `authorization.md` (spec §5.2 `common/security/CurrentUser`).

### Testing security (`spring-security-test`, add as a test dependency)

```java
mockMvc.perform(get("/api/v1/users/me"))                       // no token
       .andExpect(status().isUnauthorized());

mockMvc.perform(get("/api/v1/users/me")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + realTokenFromJwtService))
       .andExpect(status().isOk());
```
`@WithMockUser` exists, but it bypasses your JWT filter entirely. For this
project, prefer **real tokens** issued by `JwtService` in ITs, which test the
filter too. `authorization.md` covers the full matrix (1.16).

### Gotchas

- **Tutorials for Spring Security 5** (`WebSecurityConfigurerAdapter`,
  `antMatchers`, `authorizeRequests`) no longer compile. Boot 3.5 uses Security 6.5.
- **Filter as `@Component`** → registered twice (see above).
- **`/error` not permitted** → every error looks like a 401.
- **Logging the `Authorization` header** or the token anywhere (CLAUDE.md #8).
  Watch DEBUG logging on `org.springframework.security`: useful when learning,
  but check what it prints before committing.
- **CORS preflight (`OPTIONS`)** fails with 401 if CORS isn't configured in the
  security chain (Phase 2 frontend).
- Debugging: `@EnableWebSecurity(debug = true)` or
  `logging.level.org.springframework.security: TRACE` prints the filter chain
  for each request. Great for learning; remove afterwards.

### Read next

- Spring Security reference: *Servlet Applications → Architecture* (read this first; it has the filter diagrams)
- Spring Security reference: *Servlet → Authentication → Architecture* (`SecurityContextHolder`, `Authentication`, `AuthenticationManager`)
- Spring Security reference: *Servlet → Authorization → Authorize HttpServletRequests*
- Spring Security reference: *Servlet → Exploits → CSRF* (*When to use CSRF protection*)
- Spring Security reference: *Testing*

### Check yourself

1. Draw the path of a request with an expired token to `/api/v1/users/me`. Which component produces the 401?
2. Why is disabling CSRF safe here, and what change would make it unsafe?
3. What goes in the `SecurityContext` principal, and why not the `User` entity?
4. Why must `anyRequest()` be the last rule?
5. What's the trade-off of loading the user from the DB on every request?

---

## My notes

*Write this yourself after slices 1.6 and 1.12. Record: manual vs
AuthenticationManager login, DB-per-request vs claims-only, role hierarchy decision.*
