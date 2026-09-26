package com.example.demo;

import com.example.demo.controller.HealthController;
import com.example.demo.service.HealthCheckService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class HealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void healthEndpointReturnsOk() throws Exception {
        var result = mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ok"))
            .andReturn();
        assertUtcTimestamp(result.getResponse().getContentAsString());
        }

        @Test
        void readinessIsPublicAndReportsDatabaseUp() throws Exception {
        var result = mockMvc.perform(get("/api/v1/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("ok"))
            .andExpect(jsonPath("$.data.database").value("up"))
            .andReturn();
        assertUtcTimestamp(result.getResponse().getContentAsString());
        }

        @Test
        void readinessReturnsSanitizedUnavailableResponseWhenDatabaseFails() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class))
            .thenThrow(new DataAccessResourceFailureException(
                "jdbc:mysql://internal-db:3306/company?user=admin&password=test-secret"));
        MockMvc failingDatabaseMvc = MockMvcBuilders
            .standaloneSetup(new HealthController(new HealthCheckService(jdbcTemplate)))
            .build();

        String response = failingDatabaseMvc.perform(get("/api/v1/health/readiness"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.data.status").value("down"))
            .andExpect(jsonPath("$.data.database").value("down"))
                .andExpect(jsonPath("$.message").value("Service unavailable"))
            .andExpect(content().string(not(containsString("internal-db"))))
            .andExpect(content().string(not(containsString("test-secret"))))
            .andReturn().getResponse().getContentAsString();
        assertUtcTimestamp(response);
        }

        private void assertUtcTimestamp(String response) throws Exception {
        String timestamp = objectMapper.readTree(response).at("/data/timestamp").asText();
        Assertions.assertDoesNotThrow(() -> Instant.parse(timestamp));
        Assertions.assertTrue(timestamp.endsWith("Z"));
    }
}
