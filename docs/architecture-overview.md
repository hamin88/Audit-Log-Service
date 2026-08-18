# Architecture Overview

## 1. System Purpose
The tamper-evident Audit Log Service is a Spring Boot 3.5.0 application built with Java 17 to record, verify, query, and export audit events in a manner that is resistant to falsification. The design is tailored for regulated and compliance-oriented workflows where integrity, traceability, and privacy are critical.

The core goal is simple: maintain an append-only ledger of audit events whose sequence and content can be independently validated with cryptographic proof.

## 2. High-Level Architecture
The service is organized around a small set of responsibilities:

- API layer for write, query, verification, and export endpoints
- domain model for immutable audit event records
- repository layer for append-only persistence and chronological search
- hash service for SHA-256 chaining and validation
- redaction service for privacy-preserving response filtering
- archival and retention logic for lifecycle-aware compliance management

### Primary domain object
The central entity is an `AuditEvent` value that includes:

- `eventId`
- `timestamp`
- `eventType`
- `actorId`
- `resourceType`
- `resourceId`
- `payload`
- `previousHash`
- `currentHash`
- `status`
- `archivedAt`

This model is intentionally designed to preserve both business context and chain integrity metadata.

## 3. Why Append-Only + Hash Chaining Was Chosen
Traditional mutable audit logs are vulnerable because an attacker can alter earlier records without leaving an obvious trail. In contrast, the append-only ledger with cryptographic chaining creates an immutable sequence by design.

Each new record references the previous record's hash, and each record's `currentHash` is computed from its own content plus the previous hash. If any historical field is changed in the database, the verification service can detect the break because the recalculated hash no longer matches the stored value.

This approach is superior to a simple database audit table because it does not depend solely on database permissions or transactional logs. It provides evidence that can be independently checked.

## 4. Hash Formula and Genesis Definition
The SHA-256 formula used by the application is aligned with the repository design definition:

```text
currentHash = SHA256(eventId + timestamp + eventType + actorId + resourceType + resourceId + payload + previousHash)
```

The first record uses a genesis value:

```text
0000000000000000000000000000000000000000000000000000000000000000
```

This ensures that the ledger can always be initialized in a deterministic way and that the first event has a valid previous-chain reference.

## 5. API Layer Responsibilities

### Write API: POST /audit/events
This endpoint accepts an event request containing the required fields and assigns:

- a UUID `eventId`;
- a UTC `Instant` timestamp;
- a canonical JSON payload string;
- a `previousHash` from the last event in the ledger;
- a `currentHash` computed from the event and previous chain state.

The repository/service architecture forbids update or delete methods so the ledger remains append-only.

### Query API: GET /audit/events
The query API supports filtering by:

- `actorId`
- `resourceType`
- `resourceId`
- `eventType`
- time range (`from` / `to`)

The API also supports pagination via Spring Data `Pageable`.

### Verification Endpoint: GET /audit/verify
Verification checks the entire chain chronologically and reports:

- `isValid`
- `brokenAtEventId`
- `violationType`
- a message describing the failure

The service stops at the first broken link and returns a precise violation classification, such as:

- `NONE`
- `PREVIOUS_HASH_MISMATCH`
- `HASH_MISMATCH`

### Export Endpoint: GET /audit/export
The export service returns a self-contained bundle containing the matching records and the metadata needed to assess export integrity. The bundle includes cryptographic linkage information and a summary of the filter criteria used.

## 6. Query-Time Redaction vs Immutable Storage
Redaction is handled dynamically at query time rather than by mutating stored payloads. This is a deliberate architectural trade-off.

### Why this decision is correct
Modifying a stored payload would change the historical evidence and invalidate the `currentHash`. Since the hash chain is meant to be tamper-evident, mutating the source record is not acceptable.

### Benefits
- Immutable evidence is preserved.
- Privacy obligations can still be met by masking fields in responses.
- Verification continues to validate raw stored data.

### Trade-off
The query-time redaction approach introduces a small amount of runtime transformation cost, but the integrity guarantee outweighs the overhead. In regulated environments, preserving proof of what happened is more important than preserving a mutable, redacted copy of the original ledger.

## 7. Retention and Soft Deletion Strategy
The retention model uses a status enum and an archival timestamp instead of hard deletes.

This preserves the chain and allows compliance workflows to distinguish:

- active events;
- archived events;
- historical ledger entries that remain part of the chain.

The verification flow is intentionally compatible with archived entries so that archival does not trigger false-positive tamper alerts. This prevents the retention lifecycle from destroying chain validity while still supporting data minimization and lifecycle management.

## 8. Security Assumptions and Risks

### Assumptions
- the underlying database is access-controlled and protected from unauthorized direct writes;
- the genesis hash is initialized once and protected as part of the baseline ledger configuration;
- all writes go through the service layer rather than direct SQL mutation in production systems;
- the application is used in a trusted deployment environment with standard network and operational protections.

### Risks
- Full-chain verification is computationally heavier on very large datasets.
- Concurrent writes could create race conditions if two processes read the same latest `currentHash` simultaneously.
- Extremely large payloads increase serialization and hashing cost.

### Mitigation strategies
- keep verification as a dedicated operational endpoint rather than a frequent runtime check in hot paths;
- use transactional write control and database constraints to reduce write window race exposure;
- ensure canonical payload serialization remains stable and deterministic;
- keep archival and redaction logic separate from the immutable store so the ledger remains valid.

## 9. Compliance and Governance Alignment
The system aligns with the compliance goals expressed in the Scenario C analysis:

- regulators can query account-specific events using the query API;
- exports provide a self-contained, cryptographically-linked evidence bundle;
- redaction reduces exposure of sensitive data while retaining the original immutable record;
- verification has a clear mechanism to prove whether the ledger has been tampered with;
- retention and archival policies do not destroy historical evidence or invalidate the chain.

This makes the service suitable for privacy-sensitive governance environments where both auditability and disclosure minimization matter.

## 10. Summary
The final architecture blends three forces:

1. append-only event storage for trust,
2. SHA-256 ledger chaining for verifiable integrity,
3. privacy-aware query semantics for compliance and minimization.

This balance keeps the system auditable, practical to operate, and compatible with real-world retention and redaction requirements.
