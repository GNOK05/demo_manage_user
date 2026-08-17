package com.example.demo.service.impl;

import com.example.demo.dto.LeaveRequestDto;
import com.example.demo.entity.*;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.LeaveRequestRepository;
import com.example.demo.service.LeaveRequestService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LeaveRequestServiceImpl implements LeaveRequestService {
    private final LeaveRequestRepository leaveRequests;
    private final UserService userService;

    @Override
    public LeaveRequestDto.Response create(LeaveRequestDto.SaveRequest request) {
        User current = userService.currentUser();
        validateRequest(request, current);

        LeaveRequest entity = new LeaveRequest();
        entity.setUser(current);
        entity.setType(request.type());
        entity.setFromDate(request.fromDate());
        entity.setToDate(request.toDate());
        entity.setReason(request.reason());
        entity.setStatus(LeaveRequestStatus.PENDING);

        LeaveRequest saved = leaveRequests.save(entity);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestDto.Response> myRequests() {
        User current = userService.currentUser();
        return leaveRequests.findByUserOrderByCreatedAtDesc(current).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestDto.Response> pendingForApproval() {
        User current = userService.currentUser();

        if (current.getRole() == Role.ADMIN) {
            return leaveRequests.findByStatusOrderByCreatedAtDesc(LeaveRequestStatus.PENDING).stream().map(this::toResponse).toList();
        }

        if (current.getRole() == Role.MANAGER && current.getDepartment() != null) {
            return leaveRequests.findByUserDepartmentIdOrderByCreatedAtDesc(current.getDepartment().getId()).stream()
                    .filter(r -> r.getStatus() == LeaveRequestStatus.PENDING)
                    .map(this::toResponse)
                    .toList();
        }

        return List.of();
    }

    @Override
    public LeaveRequestDto.Response approve(Long id, LeaveRequestStatus status) {
        User current = userService.currentUser();
        LeaveRequest request = leaveRequests.findById(id)
                .orElseThrow(() -> new BussinessException("Leave request not found: " + id));

        if (current.getRole() != Role.MANAGER && current.getRole() != Role.ADMIN) {
            throw new BussinessException("Only PO or Admin can approve leave requests");
        }

        if (current.getRole() == Role.MANAGER && request.getUser().getDepartment() != null && current.getDepartment() != null
                && !current.getDepartment().getId().equals(request.getUser().getDepartment().getId())) {
            throw new BussinessException("You can only approve requests in your department");
        }

        if (status != LeaveRequestStatus.APPROVED && status != LeaveRequestStatus.REJECTED) {
            throw new BussinessException("Status must be APPROVED or REJECTED");
        }

        request.setStatus(status);
        request.setApprovedBy(current);
        LeaveRequest saved = leaveRequests.save(request);
        return toResponse(saved);
    }

    private void validateRequest(LeaveRequestDto.SaveRequest request, User current) {
        if (request.fromDate().isAfter(request.toDate())) {
            throw new BussinessException("From date must be before or equal to to date");
        }

        LocalDate today = LocalDate.now();
        if (request.fromDate().isBefore(today)) {
            throw new BussinessException("Leave request dates must be today or in the future");
        }

        if (current.getRole() == Role.ADMIN) {
            throw new BussinessException("Admin account cannot submit leave request");
        }
    }

    private LeaveRequestDto.Response toResponse(LeaveRequest request) {
        User user = request.getUser();
        String userName = user == null ? null : user.getFullName();
        String approvedByName = request.getApprovedBy() == null ? null : request.getApprovedBy().getFullName();
        String createdAtValue = request.getCreatedAt() == null ? null : request.getCreatedAt().toString();

        return new LeaveRequestDto.Response(
                request.getId(),
                user == null ? null : user.getId(),
                userName,
                request.getType(),
                request.getFromDate(),
                request.getToDate(),
                request.getReason(),
                request.getStatus(),
                approvedByName,
                createdAtValue
        );
    }
}
