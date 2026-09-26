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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
                String employeeToken = login("dev_1_1", "dev123");
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
                        .andExpect(jsonPath("$.data.status").value("PENDING_MANAGER"))
                .andReturn();
        long requestId = mapper.readTree(createResult.getResponse().getContentAsString()).at("/data/id").asLong();

        mockMvc.perform(patch("/api/v1/leave-requests/{id}/approve", requestId)
                        .header("Authorization", "Bearer " + login("manager_1", "lead123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("PENDING_ADMIN"))
                        .andExpect(jsonPath("$.data.managerApprovedBy").value("Engineering Manager"));

                mockMvc.perform(patch("/api/v1/leave-requests/{id}/approve", requestId)
                                .header("Authorization", "Bearer " + login("admin", "admin123"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"APPROVED\"}"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("APPROVED"))
                        .andExpect(jsonPath("$.data.approvedBy").value("System Administrator"));
    }

            @Test
            void employeeCanCancelFutureLeaveWithoutApprovalAndAnnualBalanceIsReported() throws Exception {
                                String employeeToken = login("dev_1_2", "dev123");
                String requestBody = mapper.writeValueAsString(Map.of(
                        "type", "ANNUAL",
                        "fromDate", LocalDate.now().plusDays(10).toString(),
                        "toDate", LocalDate.now().plusDays(10).toString(),
                        "reason", "Cancel this planned day off"
                ));
                var created = mockMvc.perform(post("/api/v1/leave-requests")
                                .header("Authorization", "Bearer " + employeeToken)
                                .contentType(MediaType.APPLICATION_JSON).content(requestBody))
                        .andExpect(status().isOk())
                        .andReturn();
                long id = mapper.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();

                mockMvc.perform(post("/api/v1/leave-requests/{id}/cancel", id)
                                .header("Authorization", "Bearer " + employeeToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.status").value("CANCELLED"));

                mockMvc.perform(get("/api/v1/leave-requests/balance").param("year", String.valueOf(LocalDate.now().getYear()))
                                .header("Authorization", "Bearer " + employeeToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.data.entitledDays").value(12))
                        .andExpect(jsonPath("$.data.usedDays").value(0.0))
                        .andExpect(jsonPath("$.data.pendingDays").value(0.0));
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
