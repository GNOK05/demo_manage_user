package com.example.demo;

import com.example.demo.dto.LeaveRequestDto;
import com.example.demo.entity.*;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.LeaveRequestRepository;
import com.example.demo.repository.AnnualLeaveBalanceRepository;
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
    private AnnualLeaveBalanceRepository annualLeaveBalanceRepository;

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
        when(annualLeaveBalanceRepository.findByUserIdAndYear(10L, LocalDate.now().getYear())).thenReturn(Optional.empty());
        when(annualLeaveBalanceRepository.save(any(AnnualLeaveBalance.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(leaveRequestRepository.findByUserOrderByCreatedAtDesc(employee)).thenReturn(java.util.List.of());
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestDto.SaveRequest request = new LeaveRequestDto.SaveRequest(
                LeaveRequestType.ANNUAL,
                LocalDate.now().plusDays(3),
                LocalDate.now().plusDays(5),
                "Nghỉ phép năm"
        );

        LeaveRequestDto.Response response = leaveRequestService.create(request);

        assertEquals(LeaveRequestStatus.PENDING_MANAGER, response.status());
        assertEquals("Alice", response.userName());
        assertEquals("Nghỉ phép năm", response.reason());
    }

    @Test
    void managerApprovalMustBeFollowedByAdminApproval() {
        User manager = new User();
        manager.setId(20L);
        manager.setRole(Role.MANAGER);
        manager.setFullName("PO Nguyen");
        Department department = new Department();
        department.setId(1L);
        manager.setDepartment(department);

        LeaveRequest leaveRequest = new LeaveRequest();
        leaveRequest.setId(99L);
        leaveRequest.setUser(new User());
        leaveRequest.getUser().setId(10L);
        leaveRequest.getUser().setDepartment(department);
        leaveRequest.setType(LeaveRequestType.PERSONAL);
        leaveRequest.setFromDate(LocalDate.now().plusDays(1));
        leaveRequest.setToDate(LocalDate.now().plusDays(2));
        leaveRequest.setReason("Việc gia đình");
        leaveRequest.setStatus(LeaveRequestStatus.PENDING);

        when(userService.currentUser()).thenReturn(manager);
        when(leaveRequestRepository.findById(99L)).thenReturn(Optional.of(leaveRequest));
        when(leaveRequestRepository.save(any(LeaveRequest.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LeaveRequestDto.Response managerResponse = leaveRequestService.approve(99L, LeaveRequestStatus.APPROVED);
        assertEquals(LeaveRequestStatus.PENDING_ADMIN, managerResponse.status());
        assertEquals("PO Nguyen", managerResponse.managerApprovedBy());

        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        admin.setFullName("Admin");
        when(userService.currentUser()).thenReturn(admin);
        LeaveRequestDto.Response adminResponse = leaveRequestService.approve(99L, LeaveRequestStatus.APPROVED);
        assertEquals(LeaveRequestStatus.APPROVED, adminResponse.status());
        assertEquals("Admin", adminResponse.approvedBy());
    }

    @Test
    void managerWithoutDepartmentCannotApprove() {
        User manager = new User();
        manager.setId(20L);
        manager.setRole(Role.MANAGER);
        LeaveRequest request = pendingRequest(99L, 10L);
        when(userService.currentUser()).thenReturn(manager);
        when(leaveRequestRepository.findById(99L)).thenReturn(Optional.of(request));

        assertThrows(BussinessException.class,
                () -> leaveRequestService.approve(99L, LeaveRequestStatus.APPROVED));
    }

    @Test
    void requesterCannotApproveOwnLeave() {
        User employee = new User();
        employee.setId(10L);
        employee.setRole(Role.MANAGER);
        Department department = new Department();
        department.setId(1L);
        employee.setDepartment(department);
        LeaveRequest request = pendingRequest(99L, 10L);
        request.getUser().setDepartment(department);
        when(userService.currentUser()).thenReturn(employee);
        when(leaveRequestRepository.findById(99L)).thenReturn(Optional.of(request));

        assertThrows(BussinessException.class,
                () -> leaveRequestService.approve(99L, LeaveRequestStatus.APPROVED));
    }

    @Test
    void processedLeaveCannotBeChanged() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        LeaveRequest request = pendingRequest(99L, 10L);
        request.setStatus(LeaveRequestStatus.APPROVED);
        when(userService.currentUser()).thenReturn(admin);
        when(leaveRequestRepository.findById(99L)).thenReturn(Optional.of(request));

        assertThrows(BussinessException.class,
                () -> leaveRequestService.approve(99L, LeaveRequestStatus.REJECTED));
    }

    private LeaveRequest pendingRequest(Long id, Long userId) {
        LeaveRequest request = new LeaveRequest();
        request.setId(id);
        User owner = new User();
        owner.setId(userId);
        owner.setFullName("Employee");
        request.setUser(owner);
        request.setType(LeaveRequestType.PERSONAL);
        request.setFromDate(LocalDate.now().plusDays(1));
        request.setToDate(LocalDate.now().plusDays(2));
        request.setReason("Việc gia đình");
        request.setStatus(LeaveRequestStatus.PENDING);
        return request;
    }
}
