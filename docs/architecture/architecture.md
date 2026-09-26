# ARCHITECTURE.md

> Project: **DS03 – Ride-Hailing Distributed System**  
> Purpose: Architectural contract for developers and coding agents. All generated or modified code MUST follow these rules unless an explicit Architecture Decision Record (ADR) changes them.

---

## 1. System Goals

The system simulates a Grab/Uber-style ride-hailing platform and is designed to demonstrate distributed-systems concepts:

- independently deployable microservices;
- synchronous REST communication;
- asynchronous event-driven communication;
- service-owned data;
- concurrency control;
- eventual consistency;
- idempotency and duplicate handling;
- distributed transaction handling with Saga/compensation;
- retry, timeout, dead-letter queue, and recovery;
- distributed tracing and structured logging;
- fault-tolerance demonstrations;
- performance and concurrency testing.

The frontend is a **Demo Client**, not a production-grade consumer application. UI quality is secondary to making distributed flows observable and testable.

---

## 2. Architectural Style

The project uses:

- **Microservices Architecture**
- **Clean Architecture**
- **Hexagonal Architecture / Ports & Adapters**
- **DDD-lite**
- **Event-Driven Architecture**
- **CQRS-lite where useful**
- **Saga Orchestration**
- **Transactional Outbox**
- **Inbox / Idempotent Consumer**
- **API Gateway pattern**

These patterns solve different concerns:

```text
Microservices
    -> split the system by business capability

DDD-lite
    -> define bounded contexts, aggregates, invariants, domain events

Clean Architecture
    -> enforce dependency direction inside each service

Hexagonal Architecture
    -> isolate domain/application from HTTP, DB, RabbitMQ, Redis

Event-Driven Architecture
    -> asynchronous communication and eventual consistency
```

---

## 3. Technology Stack

### Backend

- Java 21
- Spring Boot 3.x
- Spring Web
- Spring Data JPA / Hibernate
- Spring Security
- Spring Cloud Gateway
- OpenFeign or WebClient for internal synchronous calls
- RabbitMQ
- PostgreSQL
- Redis
- Flyway
- Resilience4j
- Micrometer
- OpenTelemetry
- Spring Boot Actuator

### Contracts

- OpenAPI for REST contracts
- AsyncAPI for event contracts

### Testing

- JUnit 5
- Mockito
- Testcontainers
- ArchUnit
- k6

### Observability

- Prometheus
- Grafana
- Jaeger
- Structured JSON logs

### Frontend

- React
- Vite

### Deployment and CI

- Docker
- Docker Compose
- Maven
- GitHub Actions

---

## 4. Bounded Contexts and Services

The system contains **6 business microservices** plus one API Gateway.

```text
API Gateway
    |
    +-- User Service
    +-- Driver Service
    +-- Trip Service
    +-- Matching Service
    +-- Payment Service
    +-- Notification Service
```

The API Gateway is infrastructure and is NOT a business microservice.

---

## 5. Service Responsibilities

### 5.1 User Service

Owns:

- user accounts;
- authentication;
- passwords;
- roles;
- user profile;
- refresh-token data if refresh tokens are implemented.

Responsibilities:

- register passenger/driver accounts;
- login;
- issue JWT;
- manage roles;
- expose profile information.

MUST NOT:

- own driver availability;
- own trip state;
- access databases of other services.

### 5.2 Driver Service

Owns:

- driver profile;
- vehicle;
- driver status;
- current location;
- current reservation;
- current trip reference.

Main invariant:

> One driver MUST NOT be assigned or reserved for two trips at the same time.

Typical states:

```text
OFFLINE
   |
   v
AVAILABLE
   |
   v
RESERVED
   |
   v
ON_TRIP
   |
   v
AVAILABLE
```

Responsibilities:

- driver online/offline;
- update location;
- expose available drivers;
- atomic reservation;
- release reservation;
- mark driver on-trip;
- return driver to available state.

Driver Service is the **source of truth** for driver availability.

### 5.3 Trip Service

Owns:

- ride requests;
- passenger-trip relationship;
- assigned driver reference;
- trip lifecycle;
- fare assigned to the trip;
- trip state history.

Typical lifecycle:

```text
REQUESTED
    |
    v
MATCHING
    |
    v
DRIVER_ASSIGNED
    |
    v
ACCEPTED
    |
    v
IN_PROGRESS
    |
    v
COMPLETED
    |
    v
PAYMENT_PENDING
   / \
  v   v
PAID PAYMENT_FAILED
```

Trip Service MUST enforce legal state transitions in the domain layer.

### 5.4 Matching Service

Owns:

- matching attempts;
- candidate ranking;
- matching Saga state;
- driver read projection;
- matching history.

Responsibilities:

- react to `trip.created`;
- find suitable drivers;
- rank candidates;
- request atomic reservation from Driver Service;
- request assignment from Trip Service;
- compensate by releasing the driver if assignment cannot be completed;
- maintain Redis GEO/read model for driver search.

Matching Service is the preferred **Saga Orchestrator** for the driver matching flow.

Matching Service MUST NOT write directly to Driver DB or Trip DB.

### 5.5 Payment Service

Owns:

- simulated payment transactions;
- payment attempts;
- payment state.

Responsibilities:

- consume payment requests;
- simulate payment result;
- publish payment success/failure events;
- support retry and idempotent processing.

### 5.6 Notification Service

Owns:

- notification records if persistence is enabled;
- processed notification events.

Responsibilities:

- consume business events;
- simulate delivery to passenger/driver;
- demonstrate asynchronous processing;
- demonstrate retry and DLQ behavior.

Notification Service MUST NOT participate synchronously in critical trip creation/matching paths.

---

## 6. High-Level System Architecture

```text
                         +---------------------+
                         | React + Vite Client |
                         |     Demo Client     |
                         +----------+----------+
                                    |
                               HTTPS / JWT
                                    |
                                    v
                         +----------------------+
                         |     API Gateway      |
                         | Spring Cloud Gateway |
                         +----------+-----------+
                                    |
             +----------------------+----------------------+
             |                      |                      |
             v                      v                      v
     +---------------+      +---------------+      +---------------+
     | User Service  |      | Driver Service|      | Trip Service  |
     +-------+-------+      +-------+-------+      +-------+-------+
             |                      |                      |
             v                      v                      v
          User DB               Driver DB               Trip DB
                                    |                      |
                                    | events               | events
                                    +----------+-----------+
                                               |
                                               v
                                      +------------------+
                                      |    RabbitMQ      |
                                      | Topic/Retry/DLQ  |
                                      +--------+---------+
                                               |
                      +------------------------+------------------------+
                      |                        |                        |
                      v                        v                        v
             +----------------+      +----------------+      +-------------------+
             |Matching Service|      |Payment Service |      |Notification Svc   |
             +-------+--------+      +-------+--------+      +---------+---------+
                     |                       |                         |
                     v                       v                         v
                Matching DB             Payment DB              Notification DB
                     |
                     v
                   Redis
             GEO / Read Model
```

---

## 7. Data Ownership Rules

Each service owns its own database/schema.

Local development MAY use one PostgreSQL instance, but databases and credentials MUST be logically separated.

Recommended databases:

```text
goride_user
goride_driver
goride_trip
goride_matching
goride_payment
goride_notification
```

Rules:

1. A service MUST NOT query another service's database.
2. A service MUST NOT use another service's JPA repository.
3. Cross-service information MUST be obtained by REST or events/read projections.
4. Foreign keys MUST NOT cross service database boundaries.
5. Cross-service IDs are plain identifiers, not relational joins.

---

## 8. Clean Architecture Inside Every Service

Every business service follows this dependency rule:

```text
Adapters
   |
   v
Application
   |
   v
Domain
```

The **Domain layer MUST NOT depend on Spring, JPA, RabbitMQ, HTTP, Redis, or any infrastructure technology**.

Reference layout:

```text
service/
└── src/main/java/com/goride/<service>/
    |
    ├── domain/
    │   ├── model/
    │   ├── event/
    │   ├── repository/
    │   └── exception/
    |
    ├── application/
    │   ├── port/
    │   │   ├── in/
    │   │   └── out/
    │   ├── command/
    │   ├── query/
    │   └── service/
    |
    ├── adapter/
    │   ├── in/
    │   │   ├── web/
    │   │   └── messaging/
    │   └── out/
    │       ├── persistence/
    │       ├── messaging/
    │       └── client/
    |
    └── infrastructure/
        ├── config/
        ├── security/
        └── observability/
```

---

## 9. Domain Modeling Rules

Use DDD-lite:

- Aggregate Root
- Entity
- Value Object
- Domain Event
- Repository abstraction
- Domain Service where justified
- Bounded Context

Avoid unnecessary DDD ceremony.

Example Trip Aggregate:

```text
Trip
├── TripId
├── PassengerId
├── DriverId
├── PickupLocation
├── DestinationLocation
├── Fare
└── TripStatus
```

State must be changed through behavior:

```java
trip.assignDriver(driverId);
trip.accept();
trip.start();
trip.complete();
trip.markPaymentPending();
trip.markPaid();
```

Avoid public setters such as `trip.setStatus(...)`.

---

## 10. Domain Objects vs Persistence Objects

Domain models MUST NOT be JPA entities.

Preferred:

```java
public class Trip {
    // pure domain model
}
```

Persistence adapter:

```java
@Entity
@Table(name = "trips")
class TripJpaEntity {
}
```

Use an explicit mapper:

```text
Trip <-> TripJpaEntity
```

---

## 11. Ports and Adapters

Application logic MUST depend on interfaces/ports.

Example output port:

```java
public interface SaveTripPort {
    Trip save(Trip trip);
}
```

Example event port:

```java
public interface PublishEventPort {
    void publish(DomainEvent event);
}
```

Controllers MUST call input use cases, not persistence repositories directly.

---

## 12. Synchronous vs Asynchronous Communication

Use REST when the caller needs an immediate result before continuing.

Examples:

```text
Matching -> Driver Service
reserve driver

Matching -> Trip Service
assign driver
```

Use RabbitMQ when the producer does not need to wait for the consumer.

Examples:

```text
Trip Service -> trip.created -> Matching Service
Trip Service -> trip.completed -> Payment Service
Trip Service -> trip.driver-assigned -> Notification Service
```

Do not use RabbitMQ everywhere merely because it exists.

---

## 13. Service Discovery

For the prototype, service naming/discovery is provided by Docker Compose internal DNS.

Example:

```text
http://driver-service:8080
http://trip-service:8080
```

No Eureka or Consul is required unless introduced later through an ADR.

---

## 14. Driver Atomic Reservation

Driver reservation MUST be concurrency-safe.

Matching Service selects a candidate, but Driver Service owns the final reservation decision.

Example atomic SQL:

```sql
UPDATE drivers
SET
    status = 'RESERVED',
    current_trip_id = :tripId
WHERE
    id = :driverId
AND
    status = 'AVAILABLE';
```

Interpretation:

```text
affected rows = 1
    -> reservation succeeded

affected rows = 0
    -> another request already reserved the driver
```

Do NOT implement this invariant only with an in-memory Java lock.

---

## 15. Matching Flow

```text
Passenger creates trip
        |
        v
Trip Service
status = REQUESTED/MATCHING
        |
        | trip.created
        v
RabbitMQ
        |
        v
Matching Service
        |
        v
Find candidates from Redis GEO/read model
        |
        v
Reserve candidate via Driver Service REST
        |
        +---- failure ---> next candidate
        |
        v
Driver RESERVED
        |
        v
Assign driver via Trip Service REST
        |
        +---- failure ---> Saga compensation
        |
        v
Trip DRIVER_ASSIGNED
        |
        v
Matching Saga COMPLETED
```

---

## 16. Saga Orchestration

Matching Service orchestrates driver matching.

Recommended Saga state table:

```text
matching_saga

saga_id
trip_id
driver_id
status
current_step
created_at
updated_at
```

Possible states:

```text
STARTED
CANDIDATE_SELECTED
DRIVER_RESERVED
TRIP_ASSIGNED
COMPLETED
COMPENSATING
COMPENSATED
FAILED
```

Compensation example:

```text
Driver reserved
      |
      v
Trip assignment fails permanently
      |
      v
Matching enters COMPENSATING
      |
      v
Driver Service release reservation
      |
      v
Driver AVAILABLE
      |
      v
Saga COMPENSATED
```

Saga state MUST be persisted.

---

## 17. Idempotency

All operations that can be retried MUST be idempotent.

Examples:

- reserve driver;
- assign driver;
- release driver;
- process event;
- payment request.

For internal commands, use an `Idempotency-Key`.

```http
POST /internal/trips/{tripId}/assign-driver
Idempotency-Key: MATCH-T001-D01
```

Recommended table:

```text
idempotency_record

idempotency_key PK
operation
request_hash
response_payload
status_code
created_at
expires_at
```

---

## 18. Event Envelope

All events MUST use a common logical envelope.

```json
{
  "eventId": "uuid",
  "eventType": "trip.created",
  "eventVersion": 1,
  "correlationId": "uuid",
  "causationId": "uuid-or-null",
  "aggregateId": "trip-id",
  "producer": "trip-service",
  "occurredAt": "2026-10-01T10:30:00Z",
  "payload": {}
}
```

Definitions:

- `eventId`: globally unique event ID;
- `eventType`: event routing/business type;
- `eventVersion`: contract version;
- `correlationId`: tracks a business flow end-to-end;
- `causationId`: request/event that caused this event;
- `aggregateId`: primary domain object ID;
- `producer`: originating service;
- `occurredAt`: event creation time.

---

## 19. Event Naming

Use lower-case dotted event names.

```text
trip.created
trip.driver-assigned
trip.accepted
trip.started
trip.completed

driver.location-updated
driver.availability-changed
driver.reserved
driver.released

payment.requested
payment.succeeded
payment.failed

notification.requested
notification.sent
notification.failed
```

Event names are contracts. Do not rename them casually.

---

## 20. RabbitMQ Topology

Use a topic exchange:

```text
goride.events
```

Example bindings:

```text
matching.trip-created.q
    <- trip.created

matching.driver-projection.q
    <- driver.location-updated
    <- driver.availability-changed

payment.trip-completed.q
    <- trip.completed

trip.payment-result.q
    <- payment.succeeded
    <- payment.failed

notification.trip-events.q
    <- trip.*
```

Each business consumer should have its own queue.

---

## 21. Retry and Dead-Letter Queue

Retries MUST be bounded.

Recommended logical policy:

```text
Attempt 1
    |
    v
Retry after 2s
    |
    v
Retry after 5s
    |
    v
Retry after 10s
    |
    v
DLQ
```

No infinite retry loops.

---

## 22. Transactional Outbox

Do NOT perform unsafe dual writes:

```text
save DB
then publish RabbitMQ
```

Use Transactional Outbox:

```text
BEGIN

INSERT/UPDATE business entity
INSERT outbox_event

COMMIT
```

Recommended table:

```text
outbox_event

id
aggregate_id
event_type
event_version
payload
correlation_id
causation_id
created_at
published_at
status
retry_count
```

An Outbox Publisher sends pending records to RabbitMQ and marks them published.

---

## 23. Inbox / Idempotent Consumer

Consumers MUST tolerate duplicate delivery.

Recommended table:

```text
inbox_event

event_id PK
event_type
consumer
processed_at
```

Desired semantics:

```text
at-least-once delivery
+
idempotent consumer
=
exactly-once business effect
```

Do NOT claim RabbitMQ provides global exactly-once delivery.

---

## 24. Redis and CQRS-lite

Redis belongs logically to Matching Service.

Use it for:

- available-driver projection;
- driver GEO index;
- fast candidate search.

Driver Service remains source of truth.

Redis is a derived read model.

Matching MUST NOT treat Redis as the authoritative reservation store.

---

## 25. Driver Projection

Driver Service publishes:

```text
driver.location-updated
driver.availability-changed
```

Matching Service consumes them and updates Redis.

```text
Driver DB
   |
   | event
   v
RabbitMQ
   |
   v
Matching Redis Projection
```

If Redis is lost, the projection should be rebuildable.

---

## 26. API Gateway

Responsibilities:

- external routing;
- JWT validation;
- request correlation ID creation/propagation;
- basic rate limiting if implemented;
- access logs.

Gateway MUST NOT contain business logic.

Gateway MUST NOT directly access business databases.

---

## 27. Security

Authentication uses JWT.

Preferred signing:

```text
RS256
```

User Service owns the private signing key.

Gateway/services use the public verification key.

Roles:

```text
PASSENGER
DRIVER
ADMIN
```

Secrets MUST come from environment variables or secret files excluded from Git.

Never commit passwords, JWT private keys, database passwords, RabbitMQ passwords, or API secrets.

---

## 28. Internal REST Reliability

Internal REST calls MUST define:

- connection timeout;
- read timeout;
- bounded retry where safe;
- circuit breaker;
- idempotency where retries can duplicate state changes.

Use Resilience4j.

Do not blindly retry non-idempotent requests.

---

## 29. Observability

All services SHOULD expose:

```text
/actuator/health
/actuator/metrics
```

Use:

- Micrometer;
- Prometheus;
- Grafana;
- OpenTelemetry;
- Jaeger.

Trace context MUST propagate across HTTP and RabbitMQ messages.

---

## 30. Structured Logging

Recommended fields:

```json
{
  "timestamp": "...",
  "service": "matching-service",
  "level": "INFO",
  "traceId": "...",
  "spanId": "...",
  "correlationId": "...",
  "eventType": "driver.reserved",
  "tripId": "T001",
  "driverId": "D01",
  "result": "SUCCESS"
}
```

Do not use `System.out.println` for application logging.

---

## 31. Correlation and Causation

Every external request receives or propagates a `correlationId`.

Example causal chain:

```text
HTTP request
correlationId = C01

trip.created
Event ID = E01
correlationId = C01

driver.reserved
Event ID = E02
correlationId = C01
causationId = E01

trip.driver-assigned
Event ID = E03
correlationId = C01
causationId = E02
```

---

## 32. API Contracts

REST contracts live in:

```text
contracts/openapi/
```

Events live in:

```text
contracts/asyncapi/goride-events.yaml
```

Contracts are source-controlled artifacts.

Breaking contract changes require explicit versioning or an ADR.

---

## 33. Shared Libraries

Allowed shared libraries are TECHNICAL only.

Allowed examples:

```text
platform-observability
platform-security
platform-messaging
```

Do NOT create a shared business-domain library containing:

```text
Trip
Driver
Payment
business repositories
business DTOs
business services
```

Each bounded context owns its own domain model.

---

## 34. Repository Layout

```text
goride-distributed-system/
│
├── pom.xml
├── README.md
├── .env.example
├── .gitignore
│
├── services/
│   ├── api-gateway/
│   ├── user-service/
│   ├── driver-service/
│   ├── trip-service/
│   ├── matching-service/
│   ├── payment-service/
│   └── notification-service/
│
├── libs/
│   ├── platform-observability/
│   ├── platform-security/
│   └── platform-messaging/
│
├── contracts/
│   ├── openapi/
│   └── asyncapi/
│
├── frontend/
│   └── demo-client/
│
├── infrastructure/
│   ├── postgres/
│   ├── rabbitmq/
│   ├── redis/
│   └── observability/
│
├── deploy/
│   ├── docker-compose.yml
│   ├── docker-compose.observability.yml
│   └── docker/
│
├── tests/
│   ├── integration/
│   ├── contract/
│   ├── e2e/
│   ├── concurrency/
│   ├── fault/
│   └── performance/
│
├── scripts/
│   ├── bootstrap/
│   ├── seed/
│   └── fault-injection/
│
└── docs/
    ├── architecture/
    ├── diagrams/
    ├── adr/
    ├── report/
    └── demo/
```

---

## 35. Maven Strategy

The repository MAY use a parent Maven project for dependency/version management.

Each business service MUST still build independently into its own artifact/container.

Monorepo does NOT mean monolith.

---

## 36. Database Migration

Every service owns its Flyway migrations.

Example:

```text
services/trip-service/
└── src/main/resources/db/migration/
    ├── V1__create_trip_tables.sql
    ├── V2__create_outbox.sql
    └── V3__create_idempotency_table.sql
```

A service MUST NOT ship migrations for another service's database.

---

## 37. Suggested Tables

### User DB

```text
users
roles
user_roles
refresh_tokens
outbox_event
```

### Driver DB

```text
drivers
vehicles
driver_locations
driver_reservations
outbox_event
inbox_event
idempotency_record
```

### Trip DB

```text
trips
trip_status_history
outbox_event
inbox_event
idempotency_record
```

### Matching DB

```text
matching_saga
matching_attempt
inbox_event
outbox_event
```

### Payment DB

```text
payments
payment_attempt
inbox_event
outbox_event
idempotency_record
```

### Notification DB

```text
notifications
inbox_event
```

---

## 38. Testing Strategy

### Domain Tests

Test state transitions, invariants, value objects, and domain rules.

### Application Tests

Test use cases, orchestration, and port interactions.

### Integration Tests

Use Testcontainers for PostgreSQL, RabbitMQ, and Redis.

### Contract Tests

Verify OpenAPI behavior and event payload compatibility.

### End-to-End Tests

Test full business flows.

### Concurrency Tests

Mandatory examples:

```text
two passengers -> same driver
only one reservation succeeds
```

and:

```text
duplicate assign/accept requests
only one logical side effect
```

### Fault Tests

Examples:

```text
stop Notification Service
-> event retries
-> service restarts
-> processing continues
```

```text
Trip assignment fails after driver reservation
-> Saga compensation releases driver
```

### Performance

Use k6 and measure:

- average latency;
- p95;
- throughput;
- success rate;
- recovery time.

---

## 39. ArchUnit Rules

Architecture MUST be testable, not only documented.

Rules include:

```text
domain MUST NOT depend on Spring / JPA / RabbitMQ / Redis / Web
```

```text
adapter MAY depend on application/domain
application MAY depend on domain
domain MUST NOT depend on adapter/infrastructure
```

---

## 40. CI Pipeline

Suggested GitHub Actions pipeline:

```text
checkout
   |
   v
compile
   |
   v
unit tests
   |
   v
ArchUnit
   |
   v
integration tests
   |
   v
contract validation
   |
   v
build service jars
   |
   v
build Docker images
```

---

## 41. Demo Client

Frontend is intentionally thin.

Recommended screens:

```text
Passenger
Driver
System Monitor
```

### Passenger

- login;
- create ride request;
- view trip status.

### Driver

- set available/offline;
- update simulated location;
- accept/reject ride;
- start/complete trip.

### System Monitor

Show:

- current trip states;
- current driver states;
- events;
- correlation IDs;
- retry/DLQ state where possible;
- failure/recovery demonstrations.

---

## 42. Mandatory Demo Flows

### Flow A — Happy Path

```text
Passenger requests trip
-> Matching
-> Driver reserved
-> Trip assigned
-> Driver accepts
-> Trip starts
-> Trip completes
-> Payment succeeds
-> Notification processed
```

### Flow B — Concurrency

```text
Passenger A
Passenger B
    |
    v
same Driver D01

only one atomic reservation succeeds
```

### Flow C — Notification Failure

```text
stop Notification Service
-> produce notification event
-> retry/DLQ behavior visible
-> restart service
-> processing resumes
```

### Flow D — Duplicate Event

```text
send same eventId twice
-> consumer processes business effect once
```

### Flow E — Saga Compensation

```text
reserve driver
-> force trip assignment failure
-> release driver
-> Saga ends COMPENSATED
```

---

## 43. Agent Coding Rules

Coding agents MUST follow these rules.

### MUST

1. Respect service boundaries.
2. Keep domain independent of frameworks.
3. Use ports/adapters.
4. Place business rules in domain/application layers.
5. Keep controllers thin.
6. Use DTOs at API boundaries.
7. Keep persistence entities out of domain.
8. Use Flyway for schema changes.
9. Propagate correlation IDs.
10. Use event IDs for asynchronous messages.
11. Make retryable commands idempotent.
12. Use structured logs.
13. Add tests for non-trivial domain behavior.
14. Preserve service-owned data.
15. Use Outbox for critical business-event publication.
16. Use Inbox/dedup for duplicate-safe consumption where duplicates matter.
17. Update OpenAPI/AsyncAPI contracts when interfaces change.
18. Update architecture docs/ADR when introducing a new architectural pattern.

### MUST NOT

1. Access another service's DB.
2. Share JPA entities across services.
3. Share business-domain classes through a common library.
4. Put business logic in controllers.
5. Put Spring/JPA annotations in pure domain models.
6. Add infinite retries.
7. Use in-memory state as the source of truth for distributed coordination.
8. Publish critical business events using unsafe DB-then-Rabbit dual writes.
9. Ignore duplicate messages.
10. Hard-code secrets.
11. Use `System.out.println` as application logging.
12. Introduce a new framework/tool without a concrete architectural reason.
13. Silently change event contracts.
14. Bypass a service boundary for convenience.

---

## 44. Naming Conventions

Packages:

```text
com.goride.user
com.goride.driver
com.goride.trip
com.goride.matching
com.goride.payment
com.goride.notification
```

Use cases:

```text
CreateTripUseCase
ReserveDriverUseCase
AssignDriverUseCase
CompleteTripUseCase
```

Commands:

```text
CreateTripCommand
ReserveDriverCommand
AssignDriverCommand
```

Events:

```text
TripCreatedEvent
DriverReservedEvent
PaymentSucceededEvent
```

REST DTOs:

```text
CreateTripRequest
CreateTripResponse
```

Persistence:

```text
TripJpaEntity
TripPersistenceAdapter
SpringDataTripRepository
```

---

## 45. Error Handling

Use a consistent error response.

```json
{
  "code": "DRIVER_NOT_AVAILABLE",
  "message": "Driver is not available",
  "correlationId": "uuid",
  "timestamp": "..."
}
```

Avoid exposing stack traces to clients.

---

## 46. Time and IDs

Use:

- UUID for distributed IDs;
- UTC timestamps;
- ISO-8601 serialization.

Do not rely on database auto-increment IDs across service boundaries.

---

## 47. Versioning

REST:

```text
/api/v1/...
```

Events:

```json
"eventVersion": 1
```

Breaking changes require a new API/event version or documented migration strategy.

---

## 48. Architecture Decision Records

Important architectural changes should create an ADR in:

```text
docs/adr/
```

Examples:

```text
ADR-001-use-rabbitmq.md
ADR-002-use-clean-hexagonal.md
ADR-003-use-matching-saga-orchestrator.md
ADR-004-use-transactional-outbox.md
ADR-005-use-redis-driver-projection.md
```

ADR template:

```text
# Context
# Decision
# Alternatives
# Consequences
```

---

## 49. Initial Implementation Order

```text
1. Repository skeleton
2. Docker Compose infrastructure
3. API Gateway
4. User Service
5. Driver Service
6. Trip Service
7. RabbitMQ event infrastructure
8. Matching Service
9. Atomic reservation
10. Matching Saga
11. Outbox/Inbox
12. Payment Service
13. Notification Service
14. Redis projection
15. Security hardening
16. Observability
17. Concurrency tests
18. Fault tests
19. Performance tests
20. Demo Client refinement
```

---

## 50. Definition of Done for a Service

A service is not complete merely because its REST endpoints work.

Minimum completion criteria:

- domain model defined;
- service boundary respected;
- DB ownership respected;
- Flyway migration included;
- OpenAPI updated;
- events documented if applicable;
- outbox/inbox implemented where applicable;
- error handling defined;
- logs include correlation information;
- health endpoint works;
- unit tests exist;
- integration tests exist for important infrastructure;
- Docker image builds;
- service starts through Docker Compose;
- README/config example updated.

---

## 51. Architectural Invariants

These rules are non-negotiable unless an ADR explicitly changes them:

```text
1. No shared database across business services.
2. No direct cross-service DB access.
3. Domain layer is framework-independent.
4. Every service owns its business state.
5. Driver reservation is atomic.
6. Matching uses Driver Service for reservation.
7. Redis is a projection, not the source of truth.
8. Critical events use Transactional Outbox.
9. Consumers tolerate duplicate delivery.
10. Retried commands are idempotent.
11. Matching distributed transaction uses Saga/compensation.
12. Gateway contains no business logic.
13. Event and API contracts are versioned.
14. Distributed flows carry correlation IDs.
15. Failure handling uses bounded retry and DLQ, not infinite retry.
```

---

## 52. Final Architecture Summary

```text
Frontend
   |
API Gateway
   |
Business Microservices
|
+-- User
+-- Driver
+-- Trip
+-- Matching
+-- Payment
+-- Notification
|
+-- PostgreSQL per bounded context
+-- RabbitMQ for asynchronous events
+-- Redis for Matching read projection
|
+-- Saga for distributed matching transaction
+-- Outbox for reliable publication
+-- Inbox for duplicate-safe consumption
+-- Idempotency keys for retry-safe commands
+-- Resilience4j for partial failures
+-- OpenTelemetry for distributed traces
+-- Prometheus/Grafana for metrics
+-- Clean/Hexagonal Architecture inside every service
```

The primary design objective is not to maximize the number of technologies. Every architectural component must solve a concrete distributed-system problem and remain explainable during implementation, demonstration, and oral defense.
