package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.entity.Attendance;
import com.example.demo.entity.AttendanceStatus;
import com.example.demo.entity.User;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.time.ZoneId;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired AttendanceRepository attendance;
    @Autowired UserRepository users;

        @Test
        void applicationUsesVietnamTimezone() {
                org.junit.jupiter.api.Assertions.assertEquals(
                                ZoneId.of("Asia/Ho_Chi_Minh").getRules().getOffset(Instant.now()),
                                ZoneId.systemDefault().getRules().getOffset(Instant.now()));
        }

    @Test
    void employeeCanCheckInAndOutMultipleTimesInOneDay() throws Exception {
        String token = login("dev_1_1", "dev123");

        var firstCheckIn = mockMvc.perform(post("/api/v1/attendance/check-in").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkInTime").exists())
                .andExpect(jsonPath("$.data.checkOutTime").doesNotExist())
                .andReturn();
        int initialSessionCount = mapper.readTree(firstCheckIn.getResponse().getContentAsString()).at("/data/sessions").size();
        org.junit.jupiter.api.Assertions.assertTrue(initialSessionCount >= 1);
        mockMvc.perform(post("/api/v1/attendance/check-in").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/attendance/check-out").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkOutTime").exists());
        mockMvc.perform(post("/api/v1/attendance/check-out").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/v1/attendance/check-in").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkInTime").exists())
                .andExpect(jsonPath("$.data.checkOutTime").doesNotExist())
                .andExpect(jsonPath("$.data.sessions.length()").value(initialSessionCount + 1));
        mockMvc.perform(post("/api/v1/attendance/check-out").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkOutTime").exists())
                .andExpect(jsonPath("$.data.sessions.length()").value(initialSessionCount + 1));
    }

    @Test
    void legacyAttendanceHistoryGetsSessionIdsForCorrectionRequests() throws Exception {
        User employee = users.findByUsername("dev_1_2").orElseThrow();
        LocalDate legacyDate = LocalDate.of(2001, 1, 2);
        Attendance legacyRecord = attendance.findByUserIdAndDate(employee.getId(), legacyDate).orElseGet(() -> {
            Attendance record = new Attendance();
            record.setUser(employee);
            record.setDate(legacyDate);
            record.setCheckInTime(LocalDateTime.of(legacyDate, java.time.LocalTime.of(9, 0)));
            record.setCheckOutTime(LocalDateTime.of(legacyDate, java.time.LocalTime.of(17, 0)));
            record.setStatus(AttendanceStatus.PRESENT);
            return attendance.save(record);
        });
        org.junit.jupiter.api.Assertions.assertNotNull(legacyRecord.getId());

        var response = mockMvc.perform(get("/api/v1/attendance/my")
                        .header("Authorization", "Bearer " + login("dev_1_2", "dev123")))
                .andExpect(status().isOk())
                .andReturn();
        var records = mapper.readTree(response.getResponse().getContentAsString()).at("/data");
        var migratedRecord = java.util.stream.StreamSupport.stream(records.spliterator(), false)
                .filter(record -> legacyDate.toString().equals(record.get("date").asText()))
                .findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(migratedRecord.at("/sessions/0/id").isNumber());
    }

    private String login(String username, String password) throws Exception {
        String body = mapper.writeValueAsString(Map.of("username", username, "password", password));
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
    }
}