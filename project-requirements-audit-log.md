# Requirement Summary Document: Audit Log Service

## Overview & Objective

Build an AI-Assisted Software Engineering System — Audit Log Service.

Build a working prototype that transforms software requirements into a reviewable engineering outcome via AI-assisted engineering execution, demonstrating requirement understanding, task decomposition, multi-step autonomous orchestration, and engineer-led execution.

## Core Requirements & Principles

Requirement Understanding:  Interpret intent, identify ambiguity, and normalize requirements into a clear engineering problem.

Task Decomposition:  Convert high-level requirements into actionable tasks with defined dependencies and sequencing.

AI-Assisted Execution:  Utilize AI across implementation, debugging, refactoring, test generation, documentation, and review preparation with structured prompts, iterative refinement, quality gates, and maintained traceability.

 
Controlled Oversight: Take complete ownership of correctness, maintainability, production readiness, and high-impact sign-offs.
 
 Validation \& Risk Control: Identify risks, trade-offs, and failure scenarios, while establishing safety guardrails.



## Scenario Breakdown

### Scenario A — Greenfield: Core Audit Log Service

Write API: Accepts event records containing `eventType`, `actorId`, `resourceType`, `resourceId`, `payload`, and `timestamp` (append-only; no update or delete operations exposed). 
Query API: Retrieves events filtered by actor ID, resource type and ID, event type, or a time range (`from` / `to`), with support for pagination.

Tamper Evidence (Hash Chain):  Each stored record includes a hash of its own content and a hash of the preceding record (or genesis value) to ensure that any past tampering invalidates subsequent hashes.
 
 Chain Verification Endpoint (`GET /audit/verify`):  Walks the full chain to report whether it is intact, identifying the first inconsistency and violation type if broken.

### Scenario B — Extend Your Own System: Retention and Redaction

Retention Policy:  Implement archivable or soft-deletable records for data older than a configurable window, ensuring chain verification handles them without false positives.

Structured Redaction:  Design a mechanism to redact sensitive payload fields (e.g., account numbers, personal identifiers) to meet data privacy requirements without breaking the underlying hash chain.

Bulk Export:  Provide an endpoint to export all records for a given resource ID or actor ID as a self-contained, verifiable bundle including necessary chain metadata.

### Scenario C — Ambiguous: Compliance Reporting

Under-specified Requirement: "Regulators need to be able to audit access to client account data.
 
Deliverables: Document requirement clarification, identified ambiguities, assumptions made, technical design translations, and scoped-in versus scoped-out features.