# Browser Authentication - TicketingSystem Primer

This local primer supports slices 1.3-1.4 and the React integration in Phase 7.
It explains decisions to make, not an already implemented authentication design.
The [specification](../../../SPECIFICATION.md) is authoritative.

## Browser versus server

An access token identifies a caller to the API; it does not make the browser
trusted. React can hide an admin link, but a caller can send HTTP directly.
Authorization must still run in backend services. Never embed signing secrets,
database passwords, or refresh-token secrets in frontend environment settings:
browser bundles and configuration are public.

## Storage and transport are separate decisions

| Option | Benefit | Limitation to understand |
|---|---|---|
| Access token in JavaScript memory, sent as bearer header | Not retained in persistent browser storage; not automatically attached cross-site | Lost on reload; injected scripts can act as the user while running |
| Refresh token in HttpOnly cookie | JavaScript cannot directly read the cookie | Browser attaches matching cookies automatically; CSRF and cookie configuration matter |
| Token in localStorage/sessionStorage | Easy JavaScript access | Same-origin injected scripts can read it; localStorage persists across reloads |

The planned direction prefers access tokens in memory and forbids refresh tokens
in localStorage. Decide whether refresh uses an HttpOnly cookie and how reload
restores a session. HttpOnly reduces token extraction; it does not make XSS safe
or prevent injected JavaScript from making authenticated requests.

## CORS is not authorization

An origin is scheme, host, and port. A Vite development server and the Spring
server usually have different origins unless a proxy is used. CORS controls
which cross-origin responses a browser permits JavaScript to access. It does
not stop curl or replace API permissions.

For credentialed cross-origin requests, explicitly allow trusted origins; do
not use wildcard origins with credentials. Explain `OPTIONS` preflight, which
headers/methods are allowed, and when `fetch` needs `credentials`. A dev proxy
is an integration convenience, not a reason to leave server security undefined.

## CSRF depends on automatic credentials

CSRF can make a victim's browser send an unwanted request with credentials it
attaches automatically, such as cookies. An API being called stateless does not
by itself eliminate CSRF. Refresh and logout endpoints using cookies need an
explicit protection strategy even if most API requests use bearer headers.

Discuss Spring's CSRF handling, token/header transport for protected mutations,
origin checks, and cookie policy. `SameSite` helps but should not be the only
reason for broadly disabling CSRF. HttpOnly and CORS are not substitutes for a
CSRF design.

Cookie settings to understand: `HttpOnly`, `Secure`, `SameSite`, `Path`, expiry,
and host/domain scope. Cross-site cookies may require `SameSite=None; Secure`;
cross-origin is not synonymous with cross-site. Document local HTTP versus
HTTPS behavior rather than weakening settings silently when the UI fails.

## Decision sheet before auth contracts are frozen

Record answers in the spec/ADR during 1.3-1.4:

1. Which credential is sent in a header, JSON body, or cookie for each operation?
2. What happens on page reload, access expiry, refresh expiry, and logout?
3. If cookies are used, what are their attributes and CSRF protections?
4. Which local origins/proxy arrangement and credentialed requests are supported?
5. How are concurrent refreshes handled, including multiple tabs and response loss?
6. Does logout revoke one session/family or more, and can existing access tokens
   still work until expiry?
7. What does an inactive account do at login, refresh, and access-token validation?

Do not solve uncertain refresh response loss by returning a stored raw token:
storage must remain non-reversible. Any grace/recovery policy needs independent
security reasoning and tests.

## Checks to build alongside the policy

- Real HTTP tests assert cookie attributes/clearing and refresh/logout outcomes
  if cookies are selected. Verify forbidden origins and CSRF failure cases.
- PostgreSQL tests establish that refresh consumption/revocation persists and
  behaves correctly under concurrency, including rollback paths.
- Phase 7 adds real-browser checks for reload, expiry, logout, and the actual
  local origin arrangement. HTTP clients alone do not enforce browser CORS,
  SameSite, or JavaScript cookie access restrictions.
- Never print credentials, authorization headers, or cookie values in logs or
  assertion diagnostics. Use fixed clocks/test configuration for expiry tests.

## Official reading

- [Spring Security 6.5](https://docs.spring.io/spring-security/reference/6.5/):
  servlet CSRF and CORS integration; read the explanations before configuration.
- [MDN CORS](https://developer.mozilla.org/en-US/docs/Web/HTTP/Guides/CORS):
  origins, preflight, and credentialed requests.
- [MDN Set-Cookie](https://developer.mozilla.org/en-US/docs/Web/HTTP/Reference/Headers/Set-Cookie):
  cookie attributes and browser constraints.
- [OWASP CSRF prevention](https://cheatsheetseries.owasp.org/cheatsheets/Cross-Site_Request_Forgery_Prevention_Cheat_Sheet.html)
  and [session management](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html).

## Check yourself

1. Why can a stateless API still have a CSRF problem?
2. What does HttpOnly protect, and what can an injected script still do?
3. Why does a successful curl request not prove the React origin will work?
4. Where does the authenticated identity come from in a service call?

## My notes

Record your chosen transport/reload/expiry/logout policy, origin arrangement,
and evidence from tests here after slices 1.3-1.4. Do not record actual secrets.