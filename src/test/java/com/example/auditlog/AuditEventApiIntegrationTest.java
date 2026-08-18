package com.example.auditlog;

import com.example.auditlog.service.AuditHashService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AuditEventApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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
