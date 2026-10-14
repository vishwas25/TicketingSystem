# Architecture Decision Records

Short records capture decisions, alternatives, and reasons so the learning
project does not quietly change direction between sessions. Add an ADR when a
choice changes schema, API contracts, correctness guarantees, or operational
behavior.

## ADR-001: Backend-first modular monolith

**Status:** Accepted

Start as one Spring Boot deployable organized by feature. The core goal is to
learn and demonstrate API design, data consistency, and concurrency. Splitting
services or adding a frontend before that is proven would add moving parts
without strengthening the core result. Revisit only when a concrete requirement
justifies the cost.

## ADR-002: PostgreSQL owns ticket inventory

**Status:** Accepted

Ticket availability and bookings are persisted together in PostgreSQL. Redis is
for read caching and rate limiting, not the source of truth. Cache loss or
staleness must never create inventory. The booking phase will compare the
selected lock strategy with an atomic conditional update, then verify the chosen
approach using real PostgreSQL concurrency tests.

## ADR-003: Build independently; use the sibling project as a reference

**Status:** Accepted

`../ticketing-system/` is an existing third-party project. Read it to understand
the domain and compare designs, but do not copy its implementation into this
repository. Its README describes pessimistic locking, while the current tests
are only context-load smoke tests; treat claims as hypotheses to verify, not as
evidence that the behavior is correct. Preserve attribution when discussing the
reference.

## ADR-004: No Lombok in the initial learning implementation

**Status:** Accepted

Write constructors, accessors, and DTOs explicitly at first. This keeps JPA
entity behavior and generated methods visible while learning. Reconsider only
after the owner can explain the generated-code and JPA implications.

## ADR-005: Reuse exercised foundation versions, not business code

**Date:** 2026-10-14 | **Status:** Accepted

Java 21, Spring Boot 3.5.3, springdoc 2.8.6, Testcontainers 1.21.3, PostgreSQL 16,
and Redis 7 follow the exercised SecondHandSell foundation. Maven 3.9.9 is pinned
by a generated wrapper. No claim is made that these are the newest versions;
upgrades require compatibility verification. The Docker API system property
1.44 is scoped to the integration test JVM because this environment's Docker
Engine rejects the old docker-java default.

## ADR-006: Independent local stack; real isolated test databases

**Date:** 2026-10-14 | **Status:** Accepted

Compose project `ticketing` binds local dependency ports to loopback: PostgreSQL
5433 and Redis 6380. The backend defaults to 8081. This avoids SecondHandSell's
ports and volumes. Integration tests start a fresh PostgreSQL container once
per JVM using random host ports; reuse is disabled to avoid retained test data.
Redis is a study dependency only until Phase 4, so it is not needed by Phase 0
tests or readiness. No Mailpit, Elasticsearch, or frontend is copied.

## ADR-007: Minimal baseline; business mappings remain learning work

**Date:** 2026-10-14 | **Status:** Accepted

V1 creates the user table and constraints so Phase 1 can focus on understanding
entity mapping and identity behavior. Event and booking schema are only planned
until their business-rule phases. There are no business entities in Phase 0;
Hibernate `validate` is configured, while JDBC integration tests prove the SQL
baseline. We do not imply mapped-entity validation has been demonstrated yet.
The safe failure exercise validates an intentionally different V1 fixture
against the migrated test database without changing the real migration.

## ADR-008: Phase 0 implementation exception, learning gate preserved

**Date:** 2026-10-14 | **Status:** Accepted

The owner asked AI to implement all Phase 0 infrastructure and copy the learning
documents, then will study it before writing Phase 1 while pairing. The roadmap
therefore separates implementation completion from study completion. All 21
existing topic primers are copied locally with provenance banners; their
marketplace examples are reference material, while PHASE-0.md maps concepts to
the actual ticketing files and commands. Neither sibling project is modified.