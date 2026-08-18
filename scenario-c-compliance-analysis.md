# Scenario C: Compliance Reporting Ambiguity Analysis

## 1. Overview
This document addresses the ambiguous product requirement for compliance reporting: **"Regulators need to be able to audit access to client account data."** Built within a **Java 17**, **Spring Boot 3.5.0** microservice architecture, this analysis defines requirement interpretations, identifies ambiguities, lists explicit assumptions, and establishes clear scope boundaries before implementation.

---

## 2. Requirements Clarification Statement
* **Original Statement:** "Regulators need to be able to audit access to client account data."
* **Normalized Engineering Requirement:** Authorized regulatory auditors must be able to securely query, filter, and cryptographically verify all access and modification events associated with specific client account records (`resourceType: "CLIENT_ACCOUNT"`) over a specified time window via dedicated Spring Boot REST endpoints, ensuring that the history is complete, untampered, and exportable as a verifiable bundle.

---

## 3. Identified Ambiguities
* **Auditor Identity & Authentication:** How do regulators authenticate within the microservice mesh? Are they using machine-to-machine tokens (OAuth2/mTLS via Spring Security), or is there an explicit role-based access control (RBAC) layer required for human compliance officers?
* **Granularity of "Access":** Does "access" mean only write/update events, or does it include read operations (`RECORD_READ`), permission grants (`PERMISSION_GRANTED`), and administrative reviews captured across services?
* **Data Retention & Privacy Intersections:** How does regulatory auditing interact with data privacy and redaction requirements (Scenario B)? Can regulators see redacted PII, or is it obscured for them as well?
* **Delivery Mechanism:** Do regulators expect a live API query interface, a pre-scheduled batch report, or a cryptographically signed file download?

---

## 4. Explicit Assumptions
* **Assumption 1 (API-Driven Access via Spring Web):** Regulators (or compliance auditing systems acting on their behalf) will consume data programmatically via secured REST API endpoints rather than an administrative frontend UI.
* **Assumption 2 (Resource Mapping):** "Client account data" maps directly to the audit log's resource fields, specifically where `resourceType = "CLIENT_ACCOUNT"` and `resourceId` corresponds to a unique account identifier stored in the database.
* **Assumption 3 (Event Coverage):** All critical interactions—including reads, updates, and logins affecting the account—are already ingested into the core audit log microservice as distinct events with appropriate `actorId` and `eventType` fields.

---

## 5. Scope Boundaries (Scoped In vs. Scoped Out)

### Scoped In
* Utilizing and filtering the core Spring Boot Query API (`GET /audit/events`) specifically for `resourceType = "CLIENT_ACCOUNT"`.
* Integrating with the Bulk Export feature (Scenario B) to allow compliance officers to pull a self-contained, verifiable bundle of all logs related to a specific client account.
* Documenting the expected access pattern, Spring Security considerations, and architectural assumptions for regulatory compliance use cases.

### Scoped Out
* Building a custom user management portal or centralized OAuth authorization server specifically for external regulatory bodies.
* Automated scheduling and automated secure file transfer (SFTP/S3 drop) of compliance reports to external government servers.
* Legal-hold management workflows that override standard retention and deletion policies for long-term litigation.