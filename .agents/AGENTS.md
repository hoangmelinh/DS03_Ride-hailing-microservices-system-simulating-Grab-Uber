# AGENTS.md

# 1. Source of Truth

Before making any non-trivial code change, read:

1. `/docs/architecture/architecture.md`
2. `/docs/plan/DS03_Project_Plan.md`
3. Relevant API contracts under `/contracts/openapi/`
4. Relevant event contracts under `/contracts/asyncapi/`
5. Relevant ADRs under `/docs/adr/`

Priority when documents conflict:

1. `/docs/architecture/architecture.md`
2. API / Event contracts
3. ADRs
4. `/docs/plan/DS03_Project_Plan.md`

`architecture.md` is the architectural source of truth.

The Project Plan defines scope, milestones, testing and demonstration goals.
It MUST NOT override architectural invariants.
---

# 2. Planning Before Coding

For any non-trivial task:

1. Inspect the existing implementation first.
2. Identify affected bounded contexts/services.
3. List files that need to change.
4. Explain the implementation plan.
5. Check whether API/event/database contracts are affected.
6. Only then implement the change.

Do not modify unrelated code.

---

# 3. Architecture Rules

The system uses:

- Microservices
- Clean Architecture
- Hexagonal Architecture / Ports & Adapters
- DDD-lite
- Event-Driven Architecture

Dependency direction:

Adapters -> Application -> Domain

The Domain layer MUST NOT depend on:

- Spring
- JPA / Hibernate
- RabbitMQ
- Redis
- HTTP
- infrastructure adapters

Persistence entities MUST NOT be domain entities.

Controllers MUST remain thin.

Business rules belong in Domain or Application layers.

---

# 4. Microservice Boundaries

Business services:

- user-service
- driver-service
- trip-service
- matching-service
- payment-service
- notification-service

Infrastructure:

- api-gateway
- RabbitMQ
- PostgreSQL
- Redis

Rules:

1. A service MUST NOT access another service's database.
2. A service MUST NOT import another service's repository.
3. Cross-service communication uses REST or events.
4. Foreign keys MUST NOT cross bounded-context databases.
5. Do not create shared business-domain libraries.
6. Shared libraries may contain technical concerns only.

---

# 5. Data Ownership

Each service owns its database/schema.

A service may reference another context using an ID,
but MUST NOT join or query another service's database.

Example:

Trip Service -> Driver DB        NOT ALLOWED
Trip Service -> Driver REST API  ALLOWED
Trip Service <- Driver Event     ALLOWED

---

# 6. Messaging Rules

RabbitMQ is used for asynchronous communication.

Every business event MUST contain:

- eventId
- eventType
- eventVersion
- correlationId
- causationId when applicable
- aggregateId
- producer
- occurredAt
- payload

Consumers MUST tolerate duplicate messages.

Critical event publication MUST use Transactional Outbox.

Consumers requiring duplicate protection MUST use Inbox /
idempotent-consumer logic.

Retries MUST be bounded.

Failed messages eventually go to DLQ.

Never implement infinite retry loops.

---

# 7. Distributed Transaction Rules

Matching uses Saga orchestration.

Matching Service coordinates:

1. driver candidate selection;
2. driver reservation;
3. trip assignment;
4. compensation when required.

Matching Service MUST NOT update Driver DB or Trip DB directly.

If driver reservation succeeds but trip assignment permanently fails,
the Saga MUST release the driver.

Saga state MUST be persisted.

---

# 8. Concurrency Rules

Driver Service owns the driver reservation invariant:

One driver cannot be reserved for two trips simultaneously.

Reservation MUST be enforced atomically at the database level.

Do NOT rely only on:

- Java synchronized
- in-memory locks
- local application state

Distributed IDs should use UUID.

---

# 9. Idempotency

Operations that may be retried MUST be idempotent.

Examples:

- reserve driver
- release driver
- assign driver
- payment processing
- event consumption

Use Idempotency-Key or equivalent persistent records where required.

---

# 10. API & Event Contracts

REST contracts:

`/contracts/openapi/`

Event contracts:

`/contracts/asyncapi/`

If implementation changes a public API or event:

1. update the corresponding contract;
2. update tests;
3. preserve backward compatibility when possible;
4. document breaking changes.

Do not silently change contracts.

---

# 11. Database Changes

Use Flyway for all schema changes.

Do NOT rely on automatic Hibernate schema mutation in shared environments.

Migration files belong to the service that owns the database.

A service MUST NOT create migrations for another service.

---

# 12. Testing Rules

Every non-trivial business change requires tests.

Use:

- JUnit 5
- Mockito
- Testcontainers
- ArchUnit
- k6 where performance testing is required

Important distributed-system scenarios must include tests for:

- concurrency;
- duplicate delivery;
- retries;
- Saga compensation;
- service failure/recovery.

Run relevant tests after implementation.

Do not claim a task is complete if tests are failing.

---

# 13. Architecture Enforcement

ArchUnit must enforce important Clean Architecture rules.

In particular:

- domain must not depend on Spring;
- domain must not depend on JPA;
- domain must not depend on adapters;
- application must not depend on infrastructure adapters.

Do not disable architectural tests to make a build pass.

---

# 14. Security

Never hard-code:

- passwords;
- private keys;
- JWT secrets;
- DB credentials;
- RabbitMQ credentials.

Secrets belong in environment variables or ignored local files.

Authentication uses JWT.

Authorization must be enforced server-side.

Never trust role checks performed only by the frontend.

Passwords must be securely hashed.

Validate all external input.

---

# 15. Observability

Use application logging, not System.out.println.

Distributed flows should propagate:

- traceId
- correlationId

Important business logs should include useful identifiers such as:

- tripId
- driverId
- eventId
- Saga ID

Do not log passwords, tokens, or secrets.

---

# 16. Code Generation & Overwrite Prevention

Never blindly overwrite existing files containing implemented code.

Before changing an existing file:

1. read its current contents;
2. preserve existing business behavior;
3. make the smallest necessary modification.

Code-generation utilities MUST use safe merge/update behavior.

Never replace working implementations with empty skeletons or TODOs.

---

# 17. Workspace Cleanliness

Do not create temporary files in the project root.

Reusable scripts belong under:

`scripts/`

Deployment files belong under:

`deploy/`

Temporary disposable files should be created in a temporary workspace
and removed after use.

Keep the repository root clean.

---

# 18. Git Rules

DO NOT automatically run:

git add
git commit
git push

unless explicitly requested by the user.

The user reviews and commits changes manually.

Agents may show recommended commit messages after completing a task.

---

# 19. Task Completeness

Do not silently skip task requirements.

If a task contains requirements for:

- validation;
- security;
- logging;
- tests;
- error handling;
- documentation;

all of them are part of the task.

If something cannot be completed, explicitly report it and explain why.

Do not arbitrarily defer required items as "phase 2" or "later".

---

# 20. Definition of Done

Before reporting a task as complete:

1. Code compiles.
2. Relevant tests pass.
3. Architecture rules remain satisfied.
4. API/event contracts are updated when applicable.
5. Flyway migrations are included when applicable.
6. Logging/error handling are implemented.
7. No secrets are committed.
8. No unrelated files were changed.
9. No existing implemented code was accidentally overwritten.
10. Summarize changed files and verification results.