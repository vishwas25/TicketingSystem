# Spring Boot Actuator

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### What it is

Actuator adds **operational endpoints** to the app under `/actuator`: is it
healthy, what metrics does it have, what version is it. It's how Kubernetes,
Prometheus and humans look inside a running service without reading its code.

### What's exposed here

```yaml
management:
  endpoints.web.exposure.include: health,info,prometheus,metrics
```
Actuator has many endpoints (`env`, `beans`, `conditions`, `loggers`,
`threaddump`…). Only these four are exposed over HTTP, on purpose: `env` and
`beans` leak configuration and internals.

| Endpoint | Returns | Used by |
|---|---|---|
| `/actuator/health` | `{"status":"UP"}` (+ components if authorised) | Humans, load balancers |
| `/actuator/health/liveness` | Liveness state | Kubernetes `livenessProbe` (8.4) |
| `/actuator/health/readiness` | Readiness state | Kubernetes `readinessProbe` (8.4) |
| `/actuator/info` | Build/app info (empty until configured) | Humans |
| `/actuator/metrics` | List of metric names; `/actuator/metrics/{name}` for one | Humans exploring |
| `/actuator/prometheus` | All metrics in Prometheus text format | Prometheus scraper (Phase 3) |

### Health: how it's computed

Health is an **aggregate of health indicators** that Boot auto-registers for
what's on the classpath: `db` (runs a validation query on the DataSource),
`diskSpace`, `ping`; later `redis` (4.1), `mail` (1.8), `elasticsearch` (5.1).
If any is `DOWN`, the overall status is `DOWN` (HTTP 503).

`show-details: when-authorized` means anonymous callers see only
`{"status":"UP"}`; component details are shown only to an authenticated
(authorised) user. Details reveal infrastructure, so hiding them is a
deliberate security default.

### Liveness vs readiness: the concept that matters for Phase 8

```yaml
management.endpoint.health.probes.enabled: true
management.health.livenessstate.enabled: true
management.health.readinessstate.enabled: true
```

| | **Liveness** | **Readiness** |
|---|---|---|
| Question | Is the process broken beyond self-repair? | Can it serve traffic **right now**? |
| If it fails, Kubernetes… | **restarts** the container | **stops routing traffic** to it (no restart) |
| Should include the DB? | **No.** A DB outage would make K8s restart every pod in a loop, which fixes nothing | Arguably yes, a decision for 8.4 |
| During startup | — | Not ready until the app has fully started |

By default each group contains only the app's internal state
(`livenessState` / `readinessState`), not the `db` indicator. Adding
external checks to readiness is a deliberate choice you'll make in Phase 8.
Setting `probes.enabled: true` exposes the groups even outside Kubernetes, so you
can see them locally now.

### Metrics: Micrometer in one paragraph

Actuator uses **Micrometer**, a metrics facade (like SLF4J for metrics). Boot
auto-instruments HTTP requests (`http.server.requests`), JVM memory/GC/threads,
the HikariCP pool (`hikaricp.connections.*`), and more. The
`micrometer-registry-prometheus` dependency adds the `/actuator/prometheus`
format. `management.metrics.tags.application` adds `application="secondhandsell"`
to every metric so dashboards can tell services apart. Custom meters come in 3.5;
full detail in the `micrometer` and `prometheus` primers (Phase 3).

### Try it (slice 0.7)

```bash
make up && make run          # in another terminal:

curl -s localhost:8080/actuator | jq                     # index of exposed endpoints
curl -s localhost:8080/actuator/health | jq
curl -s localhost:8080/actuator/health/liveness | jq
curl -s localhost:8080/actuator/health/readiness | jq
curl -s localhost:8080/actuator/metrics | jq '.names | length'
curl -s localhost:8080/actuator/metrics/jvm.memory.used | jq
curl -s localhost:8080/actuator/metrics/hikaricp.connections.active | jq
curl -s localhost:8080/actuator/prometheus | head -40

# http.server.requests appears only after some requests have been served:
for i in $(seq 20); do curl -s localhost:8080/actuator/health >/dev/null; done
curl -s 'localhost:8080/actuator/metrics/http.server.requests?tag=uri:/actuator/health' | jq
```
(`jq` pretty-prints JSON: `sudo apt install jq`.)

**Experiment:** with the app running, `docker compose stop postgres`, then hit
`/actuator/health` again. What status and HTTP code? Then `docker compose start postgres`.
Compare with `/actuator/health/liveness`. This is the difference between the two
probes, observed directly. (The first health call after stopping Postgres may
hang for up to 30 s: that's HikariCP's default connection timeout. Worth noticing.)

Swagger UI (`/swagger-ui.html`) and the raw OpenAPI JSON (`/v3/api-docs`) are
served by springdoc, not Actuator, and are nearly empty until controllers exist
(Phase 1). Covered in the `openapi` primer.

### What changes in later slices

- **1.6 (Spring Security):** adding the security starter locks **everything**,
  including Actuator. You'll permit `health` explicitly.
- **3.7:** decide deliberately what's public. Common answer: `health` public,
  `prometheus` reachable only from inside the cluster/network.
- **8.4:** Kubernetes probes point at `/actuator/health/liveness` and `/readiness`.

### Gotchas

- **Exposing `*`** (`include: "*"`) is convenient locally and dangerous
  anywhere else: `env` and `heapdump` can leak secrets.
- **Health `DOWN` = HTTP 503.** Load balancers treat that as "remove me", which is correct.
- **High-cardinality metric tags** (user id, raw URL with ids) explode
  Prometheus memory. Spring uses the URI *template* (`/listings/{id}`), not the
  actual path, for exactly this reason.

### Read next

- Spring Boot reference: *Production-ready Features → Endpoints*, *Health*, *Kubernetes Probes*
- Spring Boot reference: *Production-ready Features → Metrics → Supported Metrics*
- Micrometer docs: *Concepts* (meters, registries, tags)
- Kubernetes docs: *Configure Liveness, Readiness and Startup Probes* (read now for the concept; use in Phase 8)

### Check yourself

1. Why shouldn't the liveness probe check the database?
2. Why is `env` not exposed?
3. You stop Postgres. What do `/actuator/health` and `/actuator/health/liveness` return?
4. Why does `http.server.requests` tag with `/listings/{id}` rather than `/listings/42`?

---

## My notes

*Write your own notes after the corresponding TicketingSystem slice.*
