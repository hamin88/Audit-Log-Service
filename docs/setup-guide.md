# Setup Guide

This guide explains how to build, run, test, and verify the tamper-evident Audit Log Service locally using Java 17 and Spring Boot 3.5.0.

> This project is designed for local development and validation. The default runtime now uses PostgreSQL and Keycloak through Docker Compose so the local environment matches the production-style security stack more closely.

---

## 1. Prerequisites

### Required software

- Java 17 or newer
- Maven 3.8+ (this repository uses a standard Maven build, not a Maven wrapper)
- Optional: PostgreSQL 14+ for local database parity testing
- Optional: curl or HTTPie for smoke tests
- Optional: jq for formatting JSON responses

### Recommended tooling

- Git
- IntelliJ IDEA or VS Code with Java support
- Docker Desktop (optional, if you want to run PostgreSQL locally in a container)

### Default runtime assumptions

The application now expects PostgreSQL for persistence and Keycloak as the OAuth 2.0 issuer. The defaults in `src/main/resources/application.yml` point to local Docker services:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/auditlog
    username: auditlog
    password: auditlog
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8081/realms/audit-log
```

### Environment variables and configuration

The application reads Spring Boot configuration properties from `application.yml`, and these can also be overridden via environment variables.

#### Common overrides

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/auditlog
export SPRING_DATASOURCE_USERNAME=auditlog
export SPRING_DATASOURCE_PASSWORD=auditlog
export SERVER_PORT=8080
```

#### Retention settings

```bash
export AUDIT_RETENTION_PERIOD=365d
export AUDIT_RETENTION_ARCHIVE_CRON="0 0 2 * * *"
```

#### Redaction settings

```bash
export AUDIT_REDACTION_REPLACEMENT="[REDACTED]"
```

#### Security / auth notes

- Authentication and authorization now use Keycloak-issued JWTs.
- The service runs as a Spring Security OAuth 2.0 resource server and expects `AUDIT_HASH_SECRET` to be set to a strong value at startup.
- Use the Docker Compose stack for local development so the application, PostgreSQL, and Keycloak stay aligned.

---

## 2. Local Build Instructions

### Option A: Run with Docker Compose

From the project root:

```bash
cd Audit-Log-Service
docker compose up -d postgres keycloak
```

Then start the service:

```text
mvn clean install
mvn spring-boot:run
```

### Option B: Run without Docker Compose

Set the datasource and Keycloak issuer variables to point at your own services:

```bash
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/auditlog
export SPRING_DATASOURCE_USERNAME=auditlog
export SPRING_DATASOURCE_PASSWORD=auditlog
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI=http://localhost:8081/realms/audit-log
```

### Windows PowerShell examples

```powershell
cd C:\Users\Admin\Documents\Assignment\Audit-Log-Service
docker compose up -d postgres keycloak
mvn clean install
mvn spring-boot:run
```

---

## 3. Database Initialization and Genesis Hash

### Schema initialization

The project uses JPA with flyway, so the application will initialize the schema on startup against PostgreSQL.

The database table is created as `audit_events` using the `AuditEvent` entity mapping.

### Genesis hash behavior

The first record in the ledger uses the hardcoded genesis hash defined in the application:

```java
0000000000000000000000000000000000000000000000000000000000000000
```

This is defined in `AuditHashService.GENESIS_HASH` and is automatically used when no previous record exists.

> No extra startup script is required for genesis initialization. The service automatically sets the genesis hash for the first appended event.

---

## 4. Smoke Testing

Once the app is listening on `localhost:8080`, try the following checks.

### 4.1 Write an audit event

```bash
curl -sS -X POST http://localhost:8080/audit/events \
  -H 'Content-Type: application/json' \
  -d '{
    "eventType": "RECORD_READ",
    "actorId": "auditor-1",
    "resourceType": "CLIENT_ACCOUNT",
    "resourceId": "acct-100",
    "payload": {
      "reason": "regulatory-review",
      "region": "EU"
    }
  }'
```

Expected result: HTTP `201 Created` with a JSON response containing the generated event metadata, including `eventId`, `timestamp`, `previousHash`, and `currentHash`.

For authenticated calls, send a Bearer token from Keycloak. A typical `curl` flow is:

```bash
export TOKEN="eyJ..."
curl -sS -X POST http://localhost:8080/audit/events \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{ ... }'
```

### 4.2 Query audit events

```bash
curl -sS "http://localhost:8080/audit/events?resourceId=acct-100&page=0&size=10"
```

Optional filters:

```bash
curl -sS "http://localhost:8080/audit/events?actorId=auditor-1&resourceType=CLIENT_ACCOUNT&eventType=RECORD_READ&from=2026-01-01T00:00:00Z&to=2026-12-31T23:59:59Z&page=0&size=20"
```

### 4.3 Verify the hash chain

```bash
curl -sS http://localhost:8080/audit/verify
```

Example response:

```json
{
  "isValid": true,
  "brokenAtEventId": null,
  "violationType": "NONE",
  "message": "Audit hash chain is valid across 1 record(s)."
}
```

### 4.4 Export a compliance bundle

```bash
curl -sS "http://localhost:8080/audit/export?resourceId=acct-100" \
  -H 'Accept: application/json'
```

You can also filter by actor:

```bash
curl -sS "http://localhost:8080/audit/export?actorId=auditor-1" \
  -H 'Accept: application/json'
```

The response is a JSON bundle with metadata and the exported records.

---

## 5. Running the Included Test Suite

### Full Maven test run

```bash
mvn test
```

### Focused suite run

```bash
mvn -Dtest=AuditHashServiceTest,AuditLogServiceQaIntegrationTest test
```

These tests validate:

- append-only write logic;
- query filtering and pagination;
- tamper-detection via direct database mutation;
- hash-chain integrity across verification;
- export bundle generation.

---

## 6. Troubleshooting

### Application fails to start

Check the following:

- Java 17 is installed and active in PATH
- Maven is installed and version is 3.8 or newer
- the database URL is valid
- no unsupported JDBC driver conflict is present

### Database is not initialized

Verify:

```bash
mvn -q test
```

and inspect the application logs for Hibernate schema creation messages.

### Verification fails unexpectedly

This usually indicates one of the following:

- a historical record was mutated directly in the database;
- a payload was changed without rehashing the chain;
- the `previousHash` was altered manually;
- a retention or archival workflow unexpectedly changed the chain state.

Use `GET /audit/verify` to identify the first broken record and its violation type.

---

## 7. Quick Start Summary

```bash
cd Audit-Log-Service
mvn clean install
mvn spring-boot:run
```

Then validate with:

```bash
curl -sS -X POST http://localhost:8080/audit/events \
  -H 'Content-Type: application/json' \
  -d '{
    "eventType": "RECORD_READ",
    "actorId": "auditor-1",
    "resourceType": "CLIENT_ACCOUNT",
    "resourceId": "acct-100",
    "payload": {"reason": "local-check"}
  }'

curl -sS http://localhost:8080/audit/verify
curl -sS "http://localhost:8080/audit/events?resourceId=acct-100"
curl -sS "http://localhost:8080/audit/export?resourceId=acct-100"
```

---

## 8. Notes for Production Readiness

This local setup is intended for developer onboarding and validation. For a production deployment, the following should be added or hardened next:

- Spring Security with authentication and authorization
- database credentials managed via secrets or environment injection
- migration tooling for schema evolution
- observability and metrics (Prometheus, Grafana, structured logs)
- controlled access for regulator export workflows

This repo intentionally focuses on the tamper-evident audit ledger core and compliance-oriented verification behavior first, before layering in production security controls.
