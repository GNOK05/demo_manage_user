package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.entity.Attendance;
import com.example.demo.entity.AttendanceSession;
import com.example.demo.entity.AttendanceStatus;
import com.example.demo.entity.User;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.AttendanceSessionRepository;
import com.example.demo.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AttendanceAdjustmentIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AttendanceRepository attendance;
    @Autowired AttendanceSessionRepository sessions;

    @Test
    void attendanceCorrectionRequiresManagerThenAdminApproval() throws Exception {
        String employeeToken = login("dev_1_1", "dev123");
        var checkIn = mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk()).andReturn();
        mockMvc.perform(post("/api/v1/attendance/check-out").header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk());
        long sessionId = mapper.readTree(checkIn.getResponse().getContentAsString()).at("/data/sessions/0/id").asLong();
        LocalDate today = LocalDate.now();
        String request = mapper.writeValueAsString(Map.of(
                "sessionId", sessionId,
                "requestedCheckIn", LocalDateTime.of(today, java.time.LocalTime.of(8, 45)).toString(),
                "requestedCheckOut", LocalDateTime.of(today, java.time.LocalTime.of(17, 15)).toString(),
                "reason", "Forgot to check in before starting work"
        ));
        var created = mockMvc.perform(post("/api/v1/attendance/adjustments")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_MANAGER"))
                .andReturn();
        long requestId = mapper.readTree(created.getResponse().getContentAsString()).at("/data/id").asLong();

        String managerToken = login("manager_1", "lead123");
        mockMvc.perform(patch("/api/v1/attendance/adjustments/{id}/decision", requestId)
                        .header("Authorization", "Bearer " + managerToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_ADMIN"));
        mockMvc.perform(patch("/api/v1/attendance/adjustments/{id}/decision", requestId)
                        .header("Authorization", "Bearer " + login("admin", "admin123"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        mockMvc.perform(get("/api/v1/attendance/my").header("Authorization", "Bearer " + employeeToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("PRESENT"))
                .andExpect(jsonPath("$.data[0].sessions[0].checkInTime").value(today + "T08:45:00"))
                .andExpect(jsonPath("$.data[0].sessions[0].checkOutTime").value(today + "T17:15:00"));
    }

    @Test
    void attendanceCorrectionsAreAllowedForTwoPastDaysButNotEarlier() throws Exception {
        String employeeToken = login("dev_1_1", "dev123");
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        for (LocalDate allowedDate : new LocalDate[]{today.minusDays(1), today.minusDays(2)}) {
            long allowedSessionId = createSession("dev_1_1", allowedDate);
            mockMvc.perform(post("/api/v1/attendance/adjustments")
                            .header("Authorization", "Bearer " + employeeToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(adjustmentRequest(allowedSessionId, allowedDate)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("PENDING_MANAGER"));
        }

        LocalDate threeDaysAgo = today.minusDays(3);
        long expiredSessionId = createSession("dev_1_1", threeDaysAgo);
        mockMvc.perform(post("/api/v1/attendance/adjustments")
                        .header("Authorization", "Bearer " + employeeToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adjustmentRequest(expiredSessionId, threeDaysAgo)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Attendance can only be corrected within the last 2 days"));
    }

    private long createSession(String username, LocalDate date) {
        User user = users.findByUsername(username).orElseThrow();
        Attendance record = attendance.findByUserIdAndDate(user.getId(), date).orElseGet(() -> {
            Attendance created = new Attendance();
            created.setUser(user);
            created.setDate(date);
            return created;
        });
        record.setCheckInTime(LocalDateTime.of(date, LocalTime.of(22, 0)));
        record.setCheckOutTime(LocalDateTime.of(date, LocalTime.of(23, 0)));
        record.setStatus(AttendanceStatus.PRESENT);
        record = attendance.save(record);

        AttendanceSession session = new AttendanceSession();
        session.setAttendance(record);
        session.setCheckInTime(record.getCheckInTime());
        session.setCheckOutTime(record.getCheckOutTime());
        return sessions.save(session).getId();
    }

    private String adjustmentRequest(long sessionId, LocalDate date) throws Exception {
        return mapper.writeValueAsString(Map.of(
                "sessionId", sessionId,
                "requestedCheckIn", LocalDateTime.of(date, LocalTime.of(21, 45)).toString(),
                "requestedCheckOut", LocalDateTime.of(date, LocalTime.of(23, 15)).toString(),
                "reason", "Forgot to check in before starting work"
        ));
    }

    private String login(String username, String password) throws Exception {
        String body = mapper.writeValueAsString(Map.of("username", username, "password", password));
        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsString()).at("/data/token").asText();
    }
}