package com.example.auditlog;

import com.example.auditlog.api.AuditEventRequest;
import com.example.auditlog.domain.AuditEvent;
import com.example.auditlog.service.AuditEventService;
import com.example.auditlog.service.AuditHashService;
import com.example.auditlog.service.AuditRetentionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "audit.retention.period=PT0S")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AuditEventApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuditRetentionService auditRetentionService;

    @Autowired
    private AuditEventService auditEventService;

    @Test
    void appendsEventsWithServerAssignedIdsTimestampsAndHashChain() throws Exception {
        JsonNode first = postEvent("""
                {
                  "eventType": "RECORD_READ",
                  "actorId": "auditor-1",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-100",
                  "payload": {"reason": "regulatory-review"}
                }
                """);
        JsonNode second = postEvent("""
                {
                  "eventType": "RECORD_UPDATED",
                  "actorId": "service-1",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-100",
                  "payload": {"field": "status", "newValue": "ACTIVE"}
                }
                """);

        assertThat(first.get("eventId").asText()).isNotBlank();
        assertThat(first.get("timestamp").asText()).endsWith("Z");
        assertThat(first.get("previousHash").asText()).isEqualTo(AuditHashService.GENESIS_HASH);
        assertThat(first.get("currentHash").asText()).hasSize(64);
        assertThat(second.get("previousHash").asText()).isEqualTo(first.get("currentHash").asText());
    }

    @Test
    void appendsConcurrentlyWithoutForkingTheHashChain() throws Exception {
        int concurrentWrites = 8;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentWrites);
        CountDownLatch ready = new CountDownLatch(concurrentWrites);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<AuditEvent>> futures = new ArrayList<>();

        for (int i = 0; i < concurrentWrites; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                start.await();
                return auditEventService.append(new AuditEventRequest(
                        "CONCURRENT_WRITE_" + index,
                        "actor-" + index,
                        "RESOURCE",
                        "resource-" + index,
                        objectMapper.readTree("{\"iteration\":" + index + "}")
                ));
            }));
        }

        ready.await();
        start.countDown();

        List<AuditEvent> events = new ArrayList<>();
        for (Future<AuditEvent> future : futures) {
            events.add(future.get());
        }
        executor.shutdown();

        Set<String> previousHashes = events.stream()
                .map(AuditEvent::getPreviousHash)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(events).hasSize(concurrentWrites);
        assertThat(previousHashes).hasSize(concurrentWrites);
    }

    @Test
    void searchesEventsBySupportedFiltersWithPagination() throws Exception {
        postEvent("""
                {
                  "eventType": "PERMISSION_GRANTED",
                  "actorId": "admin-1",
                  "resourceType": "DOCUMENT",
                  "resourceId": "doc-9",
                  "payload": {"scope": "read"}
                }
                """);

        mockMvc.perform(get("/audit/events")
                        .with(readerJwt())
                        .param("actorId", "admin-1")
                        .param("resourceType", "DOCUMENT")
                        .param("eventType", "PERMISSION_GRANTED")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].actorId").value("admin-1"))
                .andExpect(jsonPath("$.content[0].resourceType").value("DOCUMENT"))
                .andExpect(jsonPath("$.content[0].eventType").value("PERMISSION_GRANTED"));
    }

    @Test
    void redactedSearchMasksConfiguredSensitivePayloadFieldsWithoutChangingStoredPayload() throws Exception {
        postEvent("""
                {
                  "eventType": "RECORD_READ",
                  "actorId": "auditor-1",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-200",
                  "payload": {
                    "email": "client@example.com",
                    "reason": "regulatory-review",
                    "metadata": {
                      "password": "do-not-return",
                      "nested": [{"cardNumber": "4111111111111111"}]
                    }
                  }
                }
                """);

        mockMvc.perform(get("/audit/events")
                        .with(readerJwt())
                        .param("resourceId", "acct-200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].payload.email").value("client@example.com"))
                .andExpect(jsonPath("$.content[0].payload.metadata.password").value("do-not-return"))
                .andExpect(jsonPath("$.content[0].payload.metadata.nested[0].cardNumber").value("4111111111111111"));

        mockMvc.perform(get("/audit/events/redacted")
                        .with(readerJwt())
                        .param("resourceId", "acct-200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].payload.email").value("[REDACTED]"))
                .andExpect(jsonPath("$.content[0].payload.reason").value("regulatory-review"))
                .andExpect(jsonPath("$.content[0].payload.metadata.password").value("[REDACTED]"))
                .andExpect(jsonPath("$.content[0].payload.metadata.nested[0].cardNumber").value("[REDACTED]"));

        mockMvc.perform(get("/audit/verify")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(true))
                .andExpect(jsonPath("$.violationType").value("NONE"));
    }

    @Test
    void rejectsNonObjectPayloads() throws Exception {
        mockMvc.perform(post("/audit/events")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "RECORD_READ",
                                  "actorId": "auditor-1",
                                  "resourceType": "CLIENT_ACCOUNT",
                                  "resourceId": "acct-100",
                                  "payload": ["not", "an", "object"]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnonymousRequestsWithBasicAuthChallenge() throws Exception {
        mockMvc.perform(get("/audit/events"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesNonAdminWritesAndNonExporterVerification() throws Exception {
        mockMvc.perform(post("/audit/events")
                        .with(readerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "eventType": "RECORD_READ",
                                  "actorId": "auditor-1",
                                  "resourceType": "CLIENT_ACCOUNT",
                                  "resourceId": "acct-100",
                                  "payload": {"reason": "regulatory-review"}
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/audit/verify")
                        .with(readerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsOptionsPreflightWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/audit/events"))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsInvalidTimeRange() throws Exception {
        mockMvc.perform(get("/audit/events")
                        .with(readerJwt())
                        .param("from", "2026-08-18T12:00:00Z")
                        .param("to", "2026-08-18T11:00:00Z"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void verifiesIntactAuditHashChain() throws Exception {
        postEvent("""
                {
                  "eventType": "RECORD_READ",
                  "actorId": "auditor-1",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-100",
                  "payload": {"reason": "regulatory-review"}
                }
                """);

        mockMvc.perform(get("/audit/verify")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(true))
                .andExpect(jsonPath("$.brokenAtEventId").doesNotExist())
                .andExpect(jsonPath("$.violationType").value("NONE"));
    }

    @Test
    void reportsFirstPreviousHashMismatch() throws Exception {
        postEvent("""
                {
                  "eventType": "RECORD_READ",
                  "actorId": "auditor-1",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-100",
                  "payload": {"reason": "regulatory-review"}
                }
                """);

        String replacement = "1111111111111111111111111111111111111111111111111111111111111111";
        jdbcTemplate.update("update audit_events set previous_hash = ?", replacement);

        mockMvc.perform(get("/audit/verify")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(false))
                .andExpect(jsonPath("$.brokenAtEventId").isNotEmpty())
                .andExpect(jsonPath("$.violationType").value("PREVIOUS_HASH_MISMATCH"));
    }

    @Test
    void reportsHashMismatchWhenStoredEventContentChanges() throws Exception {
        postEvent("""
                {
                  "eventType": "RECORD_READ",
                  "actorId": "auditor-1",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-100",
                  "payload": {"reason": "regulatory-review"}
                }
                """);

        jdbcTemplate.update("update audit_events set payload = ?", "{\"reason\":\"altered\"}");

        mockMvc.perform(get("/audit/verify")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(false))
                .andExpect(jsonPath("$.brokenAtEventId").isNotEmpty())
                .andExpect(jsonPath("$.violationType").value("HASH_MISMATCH"));
    }

    @Test
    void archivesExpiredEventsWithoutBreakingHashChainVerification() throws Exception {
        JsonNode event = postEvent("""
                {
                  "eventType": "RECORD_READ",
                  "actorId": "auditor-1",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-100",
                  "payload": {"reason": "regulatory-review"}
                }
                """);

        assertThat(auditRetentionService.archiveExpiredEvents()).isEqualTo(1);

        mockMvc.perform(get("/audit/events")
                        .with(readerJwt())
                        .param("resourceId", "acct-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value(event.get("eventId").asText()))
                .andExpect(jsonPath("$.content[0].status").value("ARCHIVED"))
                .andExpect(jsonPath("$.content[0].archivedAt").isNotEmpty());

        mockMvc.perform(get("/audit/verify")
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(true))
                .andExpect(jsonPath("$.violationType").value("NONE"));
    }

    private JsonNode postEvent(String requestBody) throws Exception {
        String response = mockMvc.perform(post("/audit/events")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().authorities(createAuthorityList("ROLE_ADMIN"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor readerJwt() {
        return jwt().authorities(createAuthorityList("ROLE_READER"));
    }
}
