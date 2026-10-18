package com.example.demo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class WorkLocationRequestIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void workFromHomeRequestFollowsManagerThenAdminApproval() throws Exception {
        LocalDate day = LocalDate.now().plusDays(10);
        String member = login("member_1_1", "member123");
        String response = mockMvc.perform(post("/api/v1/work-location-requests")
                        .header("Authorization", "Bearer " + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "type", "WORK_FROM_HOME", "fromDate", day, "toDate", day,
                                "fromDayPart", "MORNING", "toDayPart", "AFTERNOON",
                                "reason", "Làm việc tập trung tại nhà"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_MANAGER"))
                .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(response).path("data").path("id").asLong();

        decide(id, login("lead_1", "lead123"), "APPROVED", "PENDING_ADMIN");
        decide(id, login("admin", "admin123"), "APPROVED", "APPROVED");
    }

    @Test
    void clientSiteRequiresClientIdentityAndAddress() throws Exception {
        LocalDate day = LocalDate.now().plusDays(11);
        mockMvc.perform(post("/api/v1/work-location-requests")
                        .header("Authorization", "Bearer " + login("member_1_1", "member123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(
                                "type", "CLIENT_SITE", "fromDate", day, "toDate", day,
                                "fromDayPart", "MORNING", "toDayPart", "AFTERNOON",
                                "reason", "Hỗ trợ triển khai"))))
                .andExpect(status().isBadRequest());
    }

    private void decide(long id, String token, String decision, String expectedStatus) throws Exception {
        mockMvc.perform(patch("/api/v1/work-location-requests/{id}/decision", id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("status", decision))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(expectedStatus));
    }

    private String login(String username, String password) throws Exception {
        String content = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("username", username, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode root = mapper.readTree(content);
        return root.path("data").path("token").asText();
    }
}
