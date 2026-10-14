# Docker Compose

> Inherited concept primer copied from SecondHandSell. Its examples, slice numbers,
> commands, and original-project decisions describe that marketplace, not the
> current ticketing implementation. Use the [Phase 0 study guide](../PHASE-0.md)
> for TicketingSystem files, commands, and differences. Later-phase concepts are
> reference material only; Phase 1 implementation remains yours.

## Primer

### What it is

Docker runs one container. Compose describes **several containers as one
application** in a YAML file: which images, which ports, which volumes, how to
tell when each is healthy. `docker compose up` starts all of them;
`docker compose down` stops them.

You already know Docker, so the new part is the declarative model: you describe
the desired state and Compose converges to it.

### Why this project uses it

Spec §1.6: *"The full stack starts with one `docker compose up`."* Postgres,
Redis and Mailpit (and later Prometheus, Grafana, Elasticsearch, object storage)
run in containers. The **backend itself does not**; during development you run
it on the host with `make run` so restarts are fast and the debugger works.

```
 Host machine
 ┌──────────────────────────────────────────────────────────┐
 │  make run → Spring Boot (localhost:8080)                  │
 │        │ localhost:5432     │ localhost:6379  │ :1025     │
 │  ┌─────▼──────┐   ┌────────▼───┐   ┌─────────▼──┐        │
 │  │ shs-postgres│   │ shs-redis  │   │ shs-mailpit│        │
 │  └────────────┘   └────────────┘   └────────────┘        │
 │         Compose network "shs_default"                     │
 └──────────────────────────────────────────────────────────┘
```

### Core concepts

**1. Project name.** `name: shs` at the top. Compose prefixes everything it
creates with it: network `shs_default`, volumes `shs_pgdata`, `shs_redisdata`.
Without it, the folder name would be used.

**2. Services.** Each key under `services:` is one container definition.
`container_name` fixes the name (`shs-postgres`) so `docker logs shs-postgres`
works; without it you'd get `shs-postgres-1`.

**3. Variable interpolation.**
```yaml
POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-shs}
```
`${VAR:-default}` means "use `VAR` from the environment or `.env`, else `shs`".
Compose automatically reads a `.env` file in the project directory. That's why
`.env.example` says an empty `.env` is fine: every variable has a default.

**4. Ports: `"HOST:CONTAINER"`.**
```yaml
ports:
  - "${POSTGRES_PORT:-5432}:5432"
```
Left side is what you connect to from your machine; right side is what the
process listens on inside the container. If you already have a local Postgres on
5432, set `POSTGRES_PORT=5433` in `.env`.

**5. Service-name DNS.** Containers on the same Compose network reach each other
by **service name**: another container would use `postgres:5432`. Your host
cannot resolve `postgres`; it uses `localhost:5432`. This distinction bites in
Phase 2: a presigned URL containing a container hostname won't resolve in the
browser (CLAUDE.md gotcha).

**6. Named volumes = persistence.**
```yaml
volumes:
  - pgdata:/var/lib/postgresql/data
```
Data survives `docker compose down`. It is only deleted by `docker compose down -v`
(which `make clean` runs, which is why it's marked destructive).

**7. Healthchecks and `--wait`.**
```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U shs -d shs"]
  interval: 5s
  retries: 12
```
A container that is *running* is not necessarily *ready*. Postgres takes a few
seconds before it accepts connections. `make up` runs `docker compose up -d --wait`,
which blocks until every service with a healthcheck reports healthy (and those
without one, like Mailpit, are running). That's what CLAUDE.md means by
"healthchecks handle it; don't add sleeps".

**8. `command:` overrides the image's default command.** Redis runs with
`--appendonly yes`: append-only-file persistence, so cached data survives a
restart. (The cache is optional anyway, CLAUDE.md #6.)

### Walkthrough of this repo's file

| Service | Image | Why it's here | Notes |
|---|---|---|---|
| `postgres` | `postgres:16-alpine` | Primary datastore | `POSTGRES_*` env vars create the DB and user **on first start only** |
| `redis` | `redis:7-alpine` | Cache, rate limiting, pub/sub (Phases 4, 6) | AOF persistence on |
| `mailpit` | `axllent/mailpit:latest` | Catches outgoing email (Phase 1 verification) | SMTP on 1025, web UI on 8025. ADR-002 |

Services not there yet, on purpose: Elasticsearch (Phase 5), Prometheus/Grafana
(Phase 3), object storage (Phase 2). See ADR-007: a service arrives in the phase
that first needs it.

### Commands

```bash
make up                         # docker compose up -d --wait
make ps                         # status + health of each service
make logs                       # follow logs of all services
docker compose logs -f postgres # one service only
make psql                       # docker compose exec postgres psql -U shs -d shs
make redis-cli
docker compose config           # print the file with all variables resolved (very useful)
docker compose restart redis
docker compose stop redis       # stop one service (try this in Phase 4)
make down                       # stop + remove containers, keep volumes
make clean                      # also deletes volumes (all dev data)
docker volume ls | grep shs
```

### Gotchas

- **`POSTGRES_PASSWORD` only applies to an empty data directory.** Change it
  after the first `make up` and nothing happens: the existing volume already has
  a user with the old password. You need `down -v` to re-initialise.
- **`.env` is read by Compose, not by Spring.** `make run` doesn't load `.env`.
  `application-local.yml` has the same defaults, so it works out of the box, but
  if you change `POSTGRES_PORT` in `.env`, also `export POSTGRES_PORT=...` in
  your shell before `make run`.
- **`mailpit:latest`** is not pinned, so a future pull can change behaviour.
  Acceptable for a dev-only mail catcher; not acceptable for Postgres, which is
  why that's pinned to `16`.
- **Port already in use:** `Bind for 0.0.0.0:5432 failed` means something on
  the host holds the port. Change the host side in `.env`.
- **WSL2 + Elasticsearch** needs `vm.max_map_count` (CLAUDE.md), relevant from 5.1.

### Read next

- Docker docs: *Compose file reference*, sections `services`, `ports`, `volumes`, `healthcheck`
- Docker docs: *Interpolation* (the `${VAR:-default}` syntax)
- Docker docs: *Networking in Compose*
- Postgres image page on Docker Hub: *Initialization scripts* and environment variables

### Check yourself

1. You run `make down` then `make up`. Is your test user still in the DB? What about after `make clean`?
2. Why does the backend connect to `localhost:5432` rather than `postgres:5432`?
3. What would happen if `make up` didn't use `--wait` and a script immediately ran migrations?
4. You changed `POSTGRES_USER` in `.env` and now nothing can log in. Why, and what are two ways to fix it?

---

## My notes

*Write this yourself after slice 0.1: an explanation in your own words, anything
that surprised you, mistakes you made, and your answers to the questions above.*
