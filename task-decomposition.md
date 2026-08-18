# Task Decomposition & Execution Plan: Audit Log Service

## 1. Overview
This document outlines the phased task breakdown, sequencing, technical constraints, and dependencies for building the AI-assisted Audit Log Service assessment. The system will be built using **Java 17**, **Spring Boot 3.5.0**, and a **microservice architecture**. The plan prioritizes a minimal viable product (MVP) core before layering on advanced extensions and ambiguity resolutions.

---

## 2. Phase 1: Foundation & Ambiguity Resolution (Scenario C)
* **Task 1.1: Repository Initialization, Tech Stack Setup & Attestation**
  * **Description:** Initialize the private GitHub repository, establish the Java 17 and Spring Boot 3.5.0 microservice project structure (using Maven/Gradle), and create the required `ATTESTATION.md` file with candidate details and the mandatory honesty statement.
  * **Acceptance Criteria:** Private repo initialized with development history tracked; base Spring Boot project configured; `ATTESTATION.md` properly formatted.
  * **Dependencies:** None.

* **Task 1.2: Scenario C — Compliance Reporting Ambiguity Analysis**
  * **Description:** Clarify the under-specified requirement: *"Regulators need to be able to audit access to client account data"*. Document assumptions, identified ambiguities (e.g., regulator identity, scope of access logs), and explicit scope boundaries.
  * **Acceptance Criteria:** A documented requirements clarification statement defining what is scoped in versus scoped out.
  * **Dependencies:** Task 1.1.

---

## 3. Phase 2: Core Architecture & Scenario A (Greenfield MVP)
* **Task 2.1: Data Model & Storage Design (Spring Boot Data / JPA)**
  * **Description:** Define the data schema for audit events including `eventType`, `actorId`, `resourceType`, `resourceId`, `payload`, and `timestamp`, alongside hash-chain fields (self-hash and parent hash). Document the choice of hash algorithm (e.g., SHA-256) within the Spring Boot architecture.
  * **Acceptance Criteria:** Data model schema defined, documented, and integrated into the storage layer using Java 17 records/entities.
  * **Dependencies:** Task 1.1.

* **Task 2.2: Write API Implementation**
  * **Description:** Build the ingestion endpoint using Spring Web (REST controllers) to accept event records, assign or validate timestamps, calculate cryptographic hashes, link to the preceding record, and write strictly to an append-only store (no update or delete operations exposed).
  * **Acceptance Criteria:** Successful event ingestion; API structurally forbids update and delete methods.
  * **Dependencies:** Task 2.1.

* **Task 2.3: Query API Implementation**
  * **Description:** Implement retrieval endpoints allowing filtering by `actorId`, `resourceType` and `resourceId`, `eventType`, and time ranges (`from`/`to`), including pagination support.
  * **Acceptance Criteria:** Filters return correct matching subsets with functional pagination.
  * **Dependencies:** Task 2.2.

* **Task 2.4: Hash Chain Verification Endpoint (`GET /audit/verify`)**
  * **Description:** Implement the chain-walking verification endpoint that walks the full chain to report whether it is intact, identifying the first inconsistency and violation type if broken.
  * **Acceptance Criteria:** Endpoint correctly validates integrity, flags tampering, and pinpoints the exact point of failure.
  * **Dependencies:** Task 2.2.

---

## 4. Phase 3: Feature Extensions (Scenario B)
* **Task 3.1: Retention Policy & Soft Deletion**
  * **Description:** Implement a configuration window for archiving or soft-deleting older records. Ensure the verification endpoint handles archived entries cleanly without false-positive breaks.
  * **Acceptance Criteria:** Old records can be archived under policy while chain verification remains accurate.
  * **Dependencies:** Task 2.4.

* **Task 3.2: Structured Redaction Scheme**
  * **Description:** Design and implement a redaction scheme for sensitive payload fields (e.g., PII, account numbers) satisfying privacy requirements without invalidating the cryptographic hash chain. Document trade-offs and limitations.
  * **Acceptance Criteria:** Sensitive fields can be redacted while preserving tamper-evidence, backed by design documentation.
  * **Dependencies:** Task 2.2, Task 2.4.

* **Task 3.3: Bulk Export Bundle**
  * **Description:** Provide an endpoint to export all records for a specific `resourceId` or `actorId` as a self-contained, verifiable bundle containing required chain metadata.
  * **Acceptance Criteria:** Exported bundle enables independent, external verification of the contained events.
  * **Dependencies:** Task 2.3, Task 2.4.

---

## 5. Phase 4: Validation, Testing, & Final Packaging
* **Task 4.1: Automated Testing & Tamper Validation**
  * **Description:** Write comprehensive JUnit 5 unit and integration tests covering the core APIs, filter constraints, chain verification, and a manual data store modification test to explicitly confirm tampering detection.
  * **Acceptance Criteria:** Test suite passes cleanly, proving tamper-detection works end-to-end.
  * **Dependencies:** Phase 2 and Phase 3 completed.

* **Task 4.2: AI Usage Log & Final Engineering Summary**
  * **Description:** Compile AI traceability notes (prompts, edits, rejections, and rationale) and draft the final architecture overview, assumptions, risks, and trade-offs summary.
  * **Acceptance Criteria:** All required documentation and setup instructions are complete within the repository.
  * **Dependencies:** Task 4.1.