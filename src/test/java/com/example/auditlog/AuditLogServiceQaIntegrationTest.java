package com.example.auditlog;

import com.example.auditlog.service.AuditHashService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "audit.retention.period=PT0S")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AuditLogServiceQaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void writeApi_createsEventWithHashChainAndPersistedState() throws Exception {
        JsonNode created = postEvent("""
                {
                  "eventType": "RECORD_READ",
                  "actorId": "auditor-qa",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-qa-1",
                  "payload": {"reason": "compliance-review"}
                }
                """);

        assertThat(created.get("eventId").asText()).isNotBlank();
        assertThat(created.get("timestamp").asText()).endsWith("Z");
        assertThat(created.get("previousHash").asText()).isEqualTo(AuditHashService.GENESIS_HASH);
        assertThat(created.get("currentHash").asText()).hasSize(64);
        assertThat(created.get("payload").get("reason").asText()).isEqualTo("compliance-review");
    }

    @Test
    void queryApi_filtersEventsByActorAndResourceWithPagination() throws Exception {
        postEvent("""
                {
                  "eventType": "USER_LOGIN",
                  "actorId": "operator-1",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-qa-2",
                  "payload": {"ip": "10.0.0.3"}
                }
                """);
        postEvent("""
                {
                  "eventType": "RECORD_UPDATED",
                  "actorId": "operator-1",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-qa-2",
                  "payload": {"field": "status", "newValue": "LOCKED"}
                }
                """);
        postEvent("""
                {
                  "eventType": "PERMISSION_GRANTED",
                  "actorId": "operator-2",
                  "resourceType": "DOCUMENT",
                  "resourceId": "doc-99",
                  "payload": {"scope": "read"}
                }
                """);

        mockMvc.perform(get("/audit/events")
                        .param("actorId", "operator-1")
                        .param("resourceType", "CLIENT_ACCOUNT")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].actorId").value("operator-1"))
                .andExpect(jsonPath("$.content[0].resourceType").value("CLIENT_ACCOUNT"));
    }

    @Test
    void verificationEndpoint_detectsPreviousHashTampering() throws Exception {
        postEvent("""
                {
                  "eventType": "USER_LOGIN",
                  "actorId": "auditor-1",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-qa-3",
                  "payload": {"reason": "login"}
                }
                """);
        JsonNode second = postEvent("""
                {
                  "eventType": "RECORD_READ",
                  "actorId": "auditor-1",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-qa-3",
                  "payload": {"reason": "review"}
                }
                """);

        UUID secondEventId = UUID.fromString(second.get("eventId").asText());
        jdbcTemplate.update(
                "update audit_events set previous_hash = ? where event_id = ?",
                "1111111111111111111111111111111111111111111111111111111111111111",
                secondEventId
        );

        mockMvc.perform(get("/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(false))
                .andExpect(jsonPath("$.brokenAtEventId").value(secondEventId.toString()))
                .andExpect(jsonPath("$.violationType").value("PREVIOUS_HASH_MISMATCH"));
    }

    @Test
    void verificationEndpoint_detectsHashTamperingAfterDatabaseMutation() throws Exception {
        JsonNode first = postEvent("""
                {
                  "eventType": "USER_LOGIN",
                  "actorId": "auditor-2",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-qa-4",
                  "payload": {"reason": "login"}
                }
                """);

        UUID firstEventId = UUID.fromString(first.get("eventId").asText());
        jdbcTemplate.update(
                "update audit_events set payload = ? where event_id = ?",
                "{\"reason\":\"tampered-value\"}",
                firstEventId
        );

        mockMvc.perform(get("/audit/verify"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(false))
                .andExpect(jsonPath("$.brokenAtEventId").value(firstEventId.toString()))
                .andExpect(jsonPath("$.violationType").value("HASH_MISMATCH"));
    }

    @Test
    void exportEndpoint_returnsVerifiableBundleForRequestedSubset() throws Exception {
        postEvent("""
                {
                  "eventType": "RECORD_READ",
                  "actorId": "compliance-audit",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-qa-5",
                  "payload": {"reason": "regular-review"}
                }
                """);
        postEvent("""
                {
                  "eventType": "RECORD_UPDATED",
                  "actorId": "compliance-audit",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-qa-5",
                  "payload": {"field": "status", "newValue": "ACTIVE"}
                }
                """);
        postEvent("""
                {
                  "eventType": "RECORD_READ",
                  "actorId": "compliance-audit",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-qa-6",
                  "payload": {"reason": "other-account"}
                }
                """);

        mockMvc.perform(get("/audit/export")
                        .param("resourceId", "acct-qa-5")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, org.hamcrest.Matchers.containsString("audit-export-")))
                .andExpect(jsonPath("$.metadata.totalRecordCount").value(2))
                .andExpect(jsonPath("$.metadata.filters.resourceId").value("acct-qa-5"))
                .andExpect(jsonPath("$.metadata.subsetLinksToLedger").value(true))
                .andExpect(jsonPath("$.records[0].resourceId").value("acct-qa-5"))
                .andExpect(jsonPath("$.records[1].resourceId").value("acct-qa-5"));
    }

    @Test
    void verificationEndpoint_producesValidChainForUnmodifiedLedger() throws Exception {
        postEvent("""
                {
                  "eventType": "RECORD_READ",
                  "actorId": "regulator-user",
                  "resourceType": "CLIENT_ACCOUNT",
                  "resourceId": "acct-qa-7",
                  "payload": {"reason": "regulatory-access"}
                }
                """);

        mockMvc.perform(get("/audit/verify")
                        .with(httpBasic("audit-admin", "audit-admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(true))
                .andExpect(jsonPath("$.violationType").value("NONE"));
    }

    private JsonNode postEvent(String requestBody) throws Exception {
        String response = mockMvc.perform(post("/audit/events")
                        .with(httpBasic("audit-admin", "audit-admin-pass"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response);
    }
}
