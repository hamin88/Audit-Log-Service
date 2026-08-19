package com.example.auditlog;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "audit.retention.period=PT0S")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AuditSecurityBoundaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void anonymousUsersCannotReachSensitiveAuditEndpoints() throws Exception {
        String body = validAuditEvent();

        mockMvc.perform(post("/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/audit/events"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/audit/events/redacted"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/audit/export"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/audit/verify"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void readerCanQueryButCannotWriteExportOrVerify() throws Exception {
        mockMvc.perform(post("/audit/events")
                        .with(readerJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAuditEvent()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/audit/events")
                        .with(readerJwt()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit/events/redacted")
                        .with(readerJwt()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit/export")
                        .with(readerJwt()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/audit/verify")
                        .with(readerJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void exporterCanExportAndVerifyButCannotWriteOrQueryEvents() throws Exception {
        mockMvc.perform(post("/audit/events")
                        .with(exporterJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAuditEvent()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/audit/events")
                        .with(exporterJwt()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/audit/events/redacted")
                        .with(exporterJwt()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/audit/export")
                        .with(exporterJwt())
                        .param("resourceId", "acct-100"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"));

        mockMvc.perform(get("/audit/verify")
                        .with(exporterJwt()))
                .andExpect(status().isOk());
    }

    @Test
    void adminCanReachAllSensitiveEndpointsAndUsePreflight() throws Exception {
        mockMvc.perform(post("/audit/events")
                        .with(adminExporterJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validAuditEvent()))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/audit/events")
                        .with(adminExporterJwt()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit/events/redacted")
                        .with(adminExporterJwt()))
                .andExpect(status().isOk());

        mockMvc.perform(get("/audit/export")
                        .with(adminExporterJwt())
                        .param("resourceId", "acct-100"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"));

        mockMvc.perform(get("/audit/verify")
                        .with(adminExporterJwt()))
                .andExpect(status().isOk());

        mockMvc.perform(options("/audit/events")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Access-Control-Allow-Origin"));
    }

    private String validAuditEvent() throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "eventType", "RECORD_READ",
                "actorId", "auditor-1",
                "resourceType", "CLIENT_ACCOUNT",
                "resourceId", "acct-100",
                "payload", java.util.Map.of("reason", "security-test")
        ));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().authorities(createAuthorityList("ROLE_ADMIN"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminExporterJwt() {
        return jwt().authorities(createAuthorityList("ROLE_ADMIN", "ROLE_EXPORTER"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor readerJwt() {
        return jwt().authorities(createAuthorityList("ROLE_READER"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor exporterJwt() {
        return jwt().authorities(createAuthorityList("ROLE_EXPORTER"));
    }
}
