Full name: Haresh Amin
Email address : AminHaresh26Aug@gmail.com
Assignment title : Audit Log Service
Assignment Start Date : 18 Aug 2026
Assignment Submission Date : 19 Aug 2026

I, Haresh Amin, attest that this submission is my own individual work, completed on my own machine and accounts, and
that it honestly reflects my development process and use of AI.

# Project Attestation: Audit Log Service

## 1. Metadata & Source Control
* **Repository URL:** https://github.com/hamin88/Audit-Log-Service
* **Branch:** `main`
* **Commit SHA:** `f41d7e7`

---

## 2. AI Tool Disclosures & Usage
This project utilized artificial intelligence assistance (such as advanced LLM code generation and architecture review workflows) for:
* Scaffolding the initial Spring Boot module structure and REST controller interfaces.
* Drafting cryptographic hashing patterns for HMAC-SHA256 length-prefixed structures.
* Constructing unit and integration test frameworks (JUnit 5, Mockito).

**Human Oversight & Validation:** All generated snippets underwent manual code review, refactoring for transaction boundaries, and database-level constraint enforcement.

---

## 3. Human Validation Steps Performed
To verify application integrity prior to submission, the following manual checks were executed:
1. **Database Constraint Verification:** Ensured the `unique = true` constraint is active on the `previous_hash` column to structurally prevent hash-chain forking at the storage layer.
2. **Security & Role Testing:** Verified that endpoint protections block unauthenticated requests (`401 Unauthorized`) and cross-role violations (`403 Forbidden`).
3. **Ledger Verification Endpoint Check:** Tested the `GET /audit/verify` route against standard ledgers to ensure valid chains return success status indicators.

---

## 4. Known Limitations & Test Status
* **Test Suite Status:** The test suite includes 18 primary tests[cite: 1]. Concurrency testing validates single-threaded and isolated write flows; however, high-concurrency race conditions rely on database-level constraint exceptions (`DataIntegrityViolationException`) which require clean transaction retry boundaries.
* **Secret Configuration:** The hashing configuration defaults to a placeholder (`change-me-in-production`) if environment overrides are absent. Production deployments **must** supply a secure string via the `AUDIT_HASH_SECRET` environment variable.
* **Testcontainers:** References to Testcontainers have been decoupled from standard local builds; ensure external dependencies are provisioned if containerized integration runs are triggered.