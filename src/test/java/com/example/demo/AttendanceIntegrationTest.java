package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.entity.Attendance;
import com.example.demo.entity.AttendanceStatus;
import com.example.demo.entity.User;
import com.example.demo.entity.LeaveRequest;
import com.example.demo.entity.LeaveRequestStatus;
import com.example.demo.entity.LeaveRequestType;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.LeaveRequestRepository;
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
    @Autowired LeaveRequestRepository leaveRequests;
    @Autowired UserRepository users;

        @Test
        void applicationUsesVietnamTimezone() {
                org.junit.jupiter.api.Assertions.assertEquals(
                                ZoneId.of("Asia/Ho_Chi_Minh").getRules().getOffset(Instant.now()),
                                ZoneId.systemDefault().getRules().getOffset(Instant.now()));
        }

    @Test
    void employeeCanCheckInAndOutMultipleTimesInOneDay() throws Exception {
        String token = login("dev_1_1", "member123");

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
    void readOnlyViewerCannotCheckInOrOut() throws Exception {
        String token = login("viewer_1", "viewer123");
        mockMvc.perform(post("/api/v1/attendance/check-in")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/v1/attendance/check-out")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void hrCanLoadCompanyEmployeeAttendanceHistory() throws Exception {
        Long employeeId = users.findByUsername("dev_1_1").orElseThrow().getId();
        var today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        mockMvc.perform(get("/api/v1/attendance/user/{userId}", employeeId)
                        .param("month", String.valueOf(today.getMonthValue()))
                        .param("year", String.valueOf(today.getYear()))
                        .header("Authorization", "Bearer " + login("hr", "hr123")))
                .andExpect(status().isOk());
    }

    @Test
    void companyDayUsesApprovedLeaveInsteadOfRawAbsence() throws Exception {
        LocalDate day = LocalDate.of(2001, 1, 3);
        User paidUser = users.findByUsername("dev_1_1").orElseThrow();
        User unpaidUser = users.findByUsername("dev_1_2").orElseThrow();
        User pendingUser = users.findByUsername("test_1_1").orElseThrow();
        for (User user : java.util.List.of(paidUser, unpaidUser, pendingUser)) {
            Attendance record = new Attendance();
            record.setUser(user);
            record.setDate(day);
            record.setStatus(AttendanceStatus.ABSENT);
            attendance.save(record);
        }
        saveLeave(paidUser, day, LeaveRequestType.ANNUAL, LeaveRequestStatus.APPROVED);
        saveLeave(unpaidUser, day, LeaveRequestType.UNPAID, LeaveRequestStatus.APPROVED);
        saveLeave(pendingUser, day, LeaveRequestType.ANNUAL, LeaveRequestStatus.PENDING);

        mockMvc.perform(get("/api/v1/attendance/company/day").param("date", day.toString())
                .header("Authorization", "Bearer " + login("hr", "hr123")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.paidAnnualLeave").value(1))
            .andExpect(jsonPath("$.data.unpaidLeave").value(1))
            .andExpect(jsonPath("$.data.employees[?(@.username == 'dev_1_1')].category").value("PAID_ANNUAL_LEAVE"))
            .andExpect(jsonPath("$.data.employees[?(@.username == 'dev_1_2')].category").value("UNPAID_LEAVE"))
            .andExpect(jsonPath("$.data.employees[?(@.username == 'test_1_1')].category").value("ABSENT"));
        mockMvc.perform(get("/api/v1/attendance/company/day").param("date", day.toString())
                .header("Authorization", "Bearer " + login("dev_1_1", "member123")))
            .andExpect(status().isForbidden());
    }

    private void saveLeave(User user, LocalDate day, LeaveRequestType type, LeaveRequestStatus status) {
        LeaveRequest leave = new LeaveRequest();
        leave.setUser(user);
        leave.setType(type);
        leave.setFromDate(day);
        leave.setToDate(day);
        leave.setReason("Attendance summary test");
        leave.setStatus(status);
        leaveRequests.save(leave);
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
                        .header("Authorization", "Bearer " + login("dev_1_2", "member123")))
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
