# Task 2.1: Data Model & Storage Design (Scenario A)

## 1. Overview
This document defines the core data model, schema structures, hash-chain mechanics, and storage choices for the tamper-evident Audit Log Service, built using **Java 17**, **Spring Boot 3.5.0**, and a **microservice architecture**.

---

## 2. Storage Strategy & Append-Only Guarantee
* **Tech Stack:** Java 17, Spring Boot 3.5.0 (Spring Web, Spring Data JPA / JDBC).
* **Storage Medium:** Relational database or embedded database (such as H2 or PostgreSQL) structured as an append-only ledger table, backed by Spring Boot entities.
* **Append-Only Enforcement:** The Spring Data repository and service layer will strictly expose ingestion (write) and retrieval (query) methods. 
* **Immutability Safeguards:** Update and delete functions will be completely omitted from the service and repository interfaces. Any direct manipulation or corruption of the underlying database store will be caught during cryptographic chain verification.

---

## 3. Event Record Data Model (Java 17 Record / Entity)
Each event record ingested by the Write API must contain the following fields:

| Field Name | Java Type | Description | Required / Optional |
| :--- | :--- | :--- | :--- |
| `eventId` | `UUID` / `String` | Unique identifier for the individual audit record. | Required (Server-assigned) |
| `timestamp` | `Instant` / `String` | When the event occurred (server-assigned ISO 8601 UTC). | Required |
| `eventType` | `String` | What happened (e.g., `USER_LOGIN`, `RECORD_UPDATED`, `PERMISSION_GRANTED`). | Required |
| `actorId` | `String` | Who or what caused the event (user ID, service account). | Required |
| `resourceType` | `String` | The type of resource affected (e.g., `CLIENT_ACCOUNT`, `DOCUMENT`). | Required |
| `resourceId` | `String` | The specific resource affected. | Required |
| `payload` | `String` (JSON) | Structured object with event-specific details stored as JSON. | Required (can be empty `{}`) |

---

## 4. Tamper-Evidence & Hash Chain Design
To guarantee tamper-evidence, each stored record includes two cryptographic hash fields:

1. **`currentHash`:** A cryptographic hash computed over the serialized contents of the event record itself (incorporating all fields listed in Section 3 plus the `previousHash`).
2. **`previousHash`:** The `currentHash` value of the immediately preceding record in the sequence. For the very first record in the ledger, a predefined genesis value (e.g., `0000000000000000000000000000000000000000000000000000000000000000`) is used.

### Hash Algorithm Choice
* **Algorithm:** **SHA-256** (implemented via Java's standard `java.security.MessageDigest` library).
* **Rationale:** SHA-256 provides a robust, collision-resistant cryptographic hash standard that is highly performant in Java and fully secure for audit verification trails.

### Hashing Formula
```text
currentHash = SHA256(eventId + timestamp + eventType + actorId + resourceType + resourceId + payload + previousHash)