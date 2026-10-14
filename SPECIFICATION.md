# Ticketing System - Specification

This document is the source of truth for scope, behavior, and architecture.
Amend it when a decision changes; do not silently let implementation and spec
drift apart.

## 1. Purpose

Build an independently implemented backend for event discovery and ticket
booking. The central engineering problem is reserving finite inventory safely
when many users submit requests at once. This is also a structured learning
project: the owner should be able to explain and demonstrate each shipped
behavior, not merely recognize the code.

The project should strengthen and provide evidence for the resume's Java 21,
Spring Boot, Spring Security/JWT, REST API, PostgreSQL, Redis, testing, and
distributed-systems experience. Resume claims should be updated only after the
corresponding behavior exists and has been verified in this repository.

## 2. Users and core flows

### 2.1 Customer

1. Register and sign in; receive and use a short-lived access token.
2. Browse published events with filters and pagination.
3. Request a quantity of tickets during the event's booking window.
4. View and, while policy allows, cancel their booking.

### 2.2 Administrator

1. Create and update draft events.
2. Publish or cancel events subject to lifecycle rules.
3. View operational booking and event statistics.

### 2.3 Booking invariant

For every event, confirmed reservations must not make available inventory
negative or exceed the event's total inventory. Inventory deduction and booking
creation are one atomic database transaction. If any step fails, neither change
commits. Cancellation returns inventory at most once. A Redis outage must not
allow overselling or prevent the database from enforcing these guarantees.

The initial implementation will evaluate a PostgreSQL row lock as the starting
approach for reservation serialization. The concurrency slice must test the
invariant under parallel requests against PostgreSQL; a unit test or H2 test
alone is insufficient.

## 3. Scope

### In scope

- Java 21 and Spring Boot REST backend, organized by feature.
- PostgreSQL as the source of truth; versioned schema migrations.
- Registration, login, JWT authentication, and role-based authorization.
- Event draft/publish lifecycle, browse/search filters, and pagination.
- Booking, cancellation, inventory consistency, and concurrency protection.
- Redis-backed caching for appropriate read paths and rate limiting, introduced
  only after database behavior is correct.
- Validation, stable API errors, OpenAPI documentation, structured logs,
  health/metrics, automated tests, and CI.
- Notification and audit flows after the transactional booking core is sound.

### Out of scope for the first release

- Real payment processing or handling card data.
- Microservices, Kubernetes, and a production deployment.
- A frontend. The sibling repository contains an Angular UI that may be
  consulted later, but this project starts backend-first and uses OpenAPI/curl.
- Kafka, waitlists, ticket PDF delivery, and production email delivery until
  the core flows and their tests are complete.

## 4. Technical direction

- **Java:** 21; Maven wrapper pins Maven 3.9.9.
- **Backend:** Spring Boot, Spring MVC, Spring Security, Spring Data JPA,
  Bean Validation, and OpenAPI. Phase 0 pins Spring Boot 3.5.3,
  springdoc 2.8.6, and Testcontainers 1.21.3, matching the exercised
  SecondHandSell foundation. These are compatibility pins, not a claim that
  they are the latest releases. Spring Security is added by you in Phase 1.
- **Persistence:** PostgreSQL with Flyway migrations and Hibernate schema
  validation; never let Hibernate mutate the schema.
- **Cache/limits:** Redis is optional for read acceleration and rate limiting.
  It is never the authoritative ticket counter.
- **Tests:** JUnit and Mockito for focused unit tests; Testcontainers with real
  PostgreSQL for persistence, security, and concurrent booking integration
  tests. Do not use H2 as a substitute for PostgreSQL behavior.
- **Structure:** modular monolith, packaged by feature (for example `auth`,
  `event`, `booking`, `inventory`, `notification`, `admin`) with shared code
  kept deliberately small.
- **Money/time:** use exact decimal or integer-minor-unit representation with
  an explicit currency; never floating point. Persist instants as UTC-aware
  timestamps and use `Instant` in Java.
- **Code generation:** no Lombok initially; explicit code keeps the learning
  path inspectable. Revisit only by an explicit decision.

Token rotation policy, booking limits, cancellation
cutoff, and monetary representation are confirmed in the relevant design slices
before they become schema/API commitments.

### 4.1 Phase 0 database baseline and later schema plan

V1 creates only `users`: identity ID, email, placeholder-ready password hash
column, `USER`/`ADMIN` role constraint, active flag, and UTC-aware created/updated
timestamps. An expression index enforces case-insensitive email uniqueness.
There are no seeded accounts. Phase 1 maps this table rather than recreating it;
refresh tokens take the next migration number when that slice is implemented.

Event schema is deferred to Phase 2: title/location, lifecycle state, event and
booking window instants, exact price/currency, total/available inventory, and
query-driven indexes. Capacity and window constraints will be designed before
the event migration. Booking schema is deferred to Phase 3 so quantity,
cancellation, duplicate-booking, and retry policies are settled first. Migration
numbers follow delivery order, not a predetermined feature numbering scheme.

Hibernate schema validation is enabled from Phase 0. There are no business
entities yet, so mapped-entity drift checks begin with your Phase 1 entities;
Phase 0 validates the baseline's actual PostgreSQL constraints via JDBC tests.

## 5. Security and correctness rules

1. Authorization is checked at the service boundary as well as at HTTP routes.
2. Passwords use a password-hashing algorithm supported by Spring Security;
   raw passwords and bearer/refresh tokens are never logged.
3. Every state transition is explicit and tested, including illegal transitions.
4. Database constraints backstop important invariants; application checks alone
   are not the only protection.
5. Do not call external notification/payment services inside the transaction
   that reserves inventory. Use after-commit handling or an outbox when the
   chosen delivery guarantee requires it.
6. Cache failures must not change booking correctness. Cache keys, TTL, and
   invalidation behavior must be tested.
7. Booking retry/idempotency behavior is decided and tested before calling the
   booking endpoint safe for client retries.

## 6. Delivery phases

The detailed, owner-assigned slices and completion checks are in
[docs/learning/ROADMAP.md](docs/learning/ROADMAP.md).

| Phase | Outcome |
|---|---|
| 0 | Inspect the reference, scaffold this project, and establish the build/test gate |
| 1 | Authenticated users and role boundaries |
| 2 | Event management and public event discovery |
| 3 | Transactional bookings, cancellations, and proven inventory safety |
| 4 | Redis caching and abuse protection without weakening correctness |
| 5 | Notifications, audit trail, and admin reporting |
| 6 | Observability, security review, load experiment, and project demo |

Implementation completion and learning completion are tracked separately.
Phase 0 was implemented by AI at the owner's request, with the study and
explain-back still pending. From Phase 1, the owner implements the assigned
slices while pairing with AI; a learning phase closes only after its tests pass
and the owner can answer its explain-back questions without notes.