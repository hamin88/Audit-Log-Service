package com.example.auditlog;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void rejectsNonObjectPayloads() throws Exception {
        mockMvc.perform(post("/audit/events")
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
    void rejectsInvalidTimeRange() throws Exception {
        mockMvc.perform(get("/audit/events")
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

        mockMvc.perform(get("/audit/verify"))
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

        mockMvc.perform(get("/audit/verify"))
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

        mockMvc.perform(get("/audit/verify"))
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
                        .param("resourceId", "acct-100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].eventId").value(event.get("eventId").asText()))
                .andExpect(jsonPath("$.content[0].status").value("ARCHIVED"))
                .andExpect(jsonPath("$.content[0].archivedAt").isNotEmpty());

        mockMvc.perform(get("/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(true))
                .andExpect(jsonPath("$.violationType").value("NONE"));
    }

    private JsonNode postEvent(String requestBody) throws Exception {
        String response = mockMvc.perform(post("/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }
}
