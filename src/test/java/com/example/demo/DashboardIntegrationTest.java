package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void adminReceivesCompanyDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", "Bearer " + login("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEmployees").value(136))
                .andExpect(jsonPath("$.data.totalDepartments").value(15))
                .andExpect(jsonPath("$.data.totalProjects").value(45))
                .andExpect(jsonPath("$.data.totalTasks").value(180));
    }

    @Test
    void employeeReceivesScopedDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", "Bearer " + login("dev_1_1", "dev123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEmployees").value(1))
                .andExpect(jsonPath("$.data.totalDepartments").value(1))
                .andExpect(jsonPath("$.data.totalProjects").value(3));
    }

    @Test
    void managerReceivesDepartmentDashboardSummary() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary").header("Authorization", "Bearer " + login("manager_1", "lead123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalEmployees").value(9))
                .andExpect(jsonPath("$.data.totalDepartments").value(1))
                .andExpect(jsonPath("$.data.totalProjects").value(3))
                .andExpect(jsonPath("$.data.totalTasks").value(12));
    }

    private String login(String username, String password) throws Exception {
        String body = mapper.writeValueAsString(Map.of("username", username, "password", password));
        var result = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
    }
}
