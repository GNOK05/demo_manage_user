package com.example.demo;

import com.example.demo.dto.LeaveRequestDto;
import com.example.demo.entity.*;
import com.example.demo.repository.LeaveRequestRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.UserService;
import com.example.demo.service.impl.LeaveRequestServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRequestServiceTest {

    @Mock
    private LeaveRequestRepository leaveRequestRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private LeaveRequestServiceImpl leaveRequestService;

    @Test
    void shouldCreatePendingLeaveRequestForEmployee() {
        User employee = new User();
        employee.setId(10L);
        employee.setRole(Role.EMPLOYEE);
        employee.setFullName("Alice");

        when(userService.currentUser()).thenReturn(employee);
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestDto.SaveRequest request = new LeaveRequestDto.SaveRequest(
                LeaveRequestType.ANNUAL,
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(5),
                "Nghỉ phép năm"
        );

        LeaveRequestDto.Response response = leaveRequestService.create(request);

        assertEquals(LeaveRequestStatus.PENDING, response.status());
        assertEquals("Alice", response.userName());
        assertEquals("Nghỉ phép năm", response.reason());
    }

    @Test
    void shouldApproveLeaveRequestForManager() {
        User manager = new User();
        manager.setId(20L);
        manager.setRole(Role.MANAGER);
        manager.setFullName("PO Nguyen");

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setId(99L);
        leaveRequest.setUser(new User());
        leaveRequest.getUser().setId(10L);
        leaveRequest.setType(LeaveRequestType.PERSONAL);
        leaveRequest.setFromDate(LocalDate.now().plusDays(1));
        leaveRequest.setToDate(LocalDate.now().plusDays(2));
        leaveRequest.setReason("Việc gia đình");
        leaveRequest.setStatus(LeaveRequestStatus.PENDING);

        when(userService.currentUser()).thenReturn(manager);
        when(leaveRequestRepository.findById(99L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestDto.Response response = leaveRequestService.approve(99L, LeaveRequestStatus.APPROVED);

        assertEquals(LeaveRequestStatus.APPROVED, response.status());
        assertEquals("PO Nguyen", response.approvedBy());
    }
}
