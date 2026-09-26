package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.time.ZoneId;
import java.time.YearMonth;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PayrollIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void employeesSeeOnlyTheirOwnPayrollAndManagersSeeOnlyTheirDepartment() throws Exception {
        YearMonth period = YearMonth.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String employee = login("dev_1_1", "dev123");
        mockMvc.perform(get("/api/v1/payroll/monthly")
                        .param("year", String.valueOf(period.getYear()))
                        .param("month", String.valueOf(period.getMonthValue()))
                        .header("Authorization", "Bearer " + employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employees.length()").value(1))
                .andExpect(jsonPath("$.data.employees[0].username").value("dev_1_1"))
                .andReturn();

        String manager = login("manager_1", "lead123");
        var managerReport = mockMvc.perform(get("/api/v1/payroll/monthly")
                        .param("year", String.valueOf(period.getYear()))
                        .param("month", String.valueOf(period.getMonthValue()))
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.employees").isArray())
                .andReturn();
        String department = mapper.readTree(managerReport.getResponse().getContentAsString())
                .at("/data/employees/0/department").asText();
        for (var row : mapper.readTree(managerReport.getResponse().getContentAsString()).at("/data/employees")) {
            assertEquals(department, row.get("department").asText());
        }

        mockMvc.perform(get("/api/v1/payroll/monthly.xlsx")
                        .param("year", String.valueOf(period.getYear()))
                        .param("month", String.valueOf(period.getMonthValue()))
                        .header("Authorization", "Bearer " + employee))
                .andExpect(status().isForbidden());
    }

    @Test
    void payrollInputsCanBeReportedExportedAndClosedByAdmin() throws Exception {
        YearMonth period = YearMonth.now().minusMonths(1);
        String admin = login("admin", "admin123");
        String manager = login("manager_1", "lead123");

        mockMvc.perform(get("/api/v1/payroll/monthly")
                        .param("year", String.valueOf(period.getYear()))
                        .param("month", String.valueOf(period.getMonthValue()))
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.year").value(period.getYear()))
                .andExpect(jsonPath("$.data.employees").isArray());

        var workbook = mockMvc.perform(get("/api/v1/payroll/monthly.xlsx")
                        .param("year", String.valueOf(period.getYear()))
                        .param("month", String.valueOf(period.getMonthValue()))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        assertEquals('P', workbook[0]);
        assertEquals('K', workbook[1]);
        try (XSSFWorkbook exportedWorkbook = new XSSFWorkbook(new ByteArrayInputStream(workbook))) {
            var sheet = exportedWorkbook.getSheetAt(0);
            assertEquals("Công tính lương", sheet.getRow(0).getCell(3).getStringCellValue());
            assertEquals("Công thực tế", sheet.getRow(0).getCell(4).getStringCellValue());
            if (sheet.getLastRowNum() > 0) {
                var firstEmployee = sheet.getRow(1);
                assertEquals(firstEmployee.getCell(4).getNumericCellValue()
                                + firstEmployee.getCell(8).getNumericCellValue(),
                        firstEmployee.getCell(3).getNumericCellValue());
            }
        }

        mockMvc.perform(post("/api/v1/payroll/{year}/{month}/close", period.getYear(), period.getMonthValue())
                        .header("Authorization", "Bearer " + manager))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/payroll/{year}/{month}/close", period.getYear(), period.getMonthValue())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.closed").value(true));
        mockMvc.perform(post("/api/v1/payroll/{year}/{month}/reopen", period.getYear(), period.getMonthValue())
                        .header("Authorization", "Bearer " + manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Correct an approved attendance adjustment\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/payroll/{year}/{month}/reopen", period.getYear(), period.getMonthValue())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Correct an approved attendance adjustment\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.closed").value(false))
                .andExpect(jsonPath("$.data.reopenReason").value("Correct an approved attendance adjustment"));
        mockMvc.perform(post("/api/v1/payroll/{year}/{month}/close", period.getYear(), period.getMonthValue())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.closed").value(true));
    }

    private String login(String username, String password) throws Exception {
        String body = mapper.writeValueAsString(Map.of("username", username, "password", password));
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
    }
}