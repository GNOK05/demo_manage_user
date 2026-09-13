package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LeaveRequestApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void employeeCanSubmitAndManagerCanApproveUsingDecisionPayload() throws Exception {
        String employeeToken = login("dev1", "dev123");
        String requestBody = mapper.writeValueAsString(Map.of(
                "type", "ANNUAL",
                "fromDate", LocalDate.now().plusDays(2).toString(),
                "toDate", LocalDate.now().plusDays(3).toString(),
                "reason", "Annual leave"
        ));

        var createResult = mockMvc.perform(post("/api/v1/leave-requests")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andReturn();
        long requestId = mapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(patch("/api/v1/leave-requests/{id}/approve", requestId)
                        .header("Authorization", "Bearer " + login("lead1", "lead123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.approvedBy").value("Engineering PO"));
    }

    private String login(String username, String password) throws Exception {
        String body = mapper.writeValueAsString(Map.of("username", username, "password", password));
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
    }
}
