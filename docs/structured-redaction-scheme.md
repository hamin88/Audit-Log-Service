# Structured Redaction Scheme

## Decision
The service uses dynamic query-time redaction. Stored audit records remain immutable: the original `payload` string is retained and continues to be the value used by `currentHash = SHA256(eventId + timestamp + eventType + actorId + resourceType + resourceId + payload + previousHash)`.

## Rationale
Changing payload data in place would break the append-only ledger and invalidate the SHA-256 chain. Query-time masking supports privacy-oriented disclosure minimization for GDPR/CCPA-style access patterns while preserving historical integrity. If a future workflow needs to record a legal redaction action, it should append a new audit event such as `PAYLOAD_REDACTION_APPLIED` rather than mutate the original record.

## API Behavior
`GET /audit/events` returns the stored payload.

`GET /audit/events/redacted` supports the same filters and pagination as the regular Query API, but masks configured sensitive payload keys in the response only.

`GET /audit/verify` always validates against the original stored payload and ignores redacted response projections.

## Configuration
Sensitive keys and the replacement token are configured under `audit.redaction` in `application.yml`. Matching is case-insensitive and recursive for nested objects and arrays.
