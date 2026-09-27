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
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DepartmentAttendanceIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void managerCanViewHistoricalDepartmentAttendanceSummary() throws Exception {
        LocalDate pastDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(2);
        String token = login("manager_1", "lead123");

        mockMvc.perform(get("/api/v1/attendance/department/summary")
                        .param("date", pastDate.toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value(pastDate.toString()))
                .andExpect(jsonPath("$.data.totalEmployees").value(9))
                .andExpect(jsonPath("$.data.absent").isNumber())
                .andExpect(jsonPath("$.data.present").isNumber())
                .andExpect(jsonPath("$.data.late").isNumber())
                .andExpect(jsonPath("$.data.leave").isNumber());
    }

    @Test
    void employeeCannotReadDepartmentSummaryAndFutureDateIsRejected() throws Exception {
        String manager = login("manager_1", "lead123");
        String employee = login("dev_1_1", "dev123");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        mockMvc.perform(get("/api/v1/attendance/department/summary")
                        .param("date", today.plusDays(1).toString())
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/attendance/department/summary")
                        .param("date", today.toString())
                        .header("Authorization", "Bearer " + employee))
                .andExpect(status().isForbidden());
    }

    @Test
    void approvedAnnualLeaveIsIncludedInHistoricalDepartmentChart() throws Exception {
        LocalDate leaveDate = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).minusDays(18);
        while (leaveDate.getDayOfWeek() == DayOfWeek.SATURDAY || leaveDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            leaveDate = leaveDate.minusDays(1);
        }

        mockMvc.perform(get("/api/v1/attendance/department/summary")
                        .param("date", leaveDate.toString())
                        .header("Authorization", "Bearer " + login("manager_1", "lead123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.leave").value(1));
    }

    private String login(String username, String password) throws Exception {
        String body = mapper.writeValueAsString(Map.of("username", username, "password", password));
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
    }
}
