package com.example.demo.service.impl;

import com.example.demo.dto.WorkLocationRequestDto;
import com.example.demo.entity.*;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.WorkLocationRequestRepository;
import com.example.demo.service.UserService;
import com.example.demo.service.WorkLocationRequestService;
import com.example.demo.service.LeaveWorkdayCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkLocationRequestServiceImpl implements WorkLocationRequestService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final WorkLocationRequestRepository requests;
    private final UserService users;

    @Override
    public WorkLocationRequestDto.Response create(WorkLocationRequestDto.SaveRequest request) {
        User current = users.currentUser();
        if (current.getRole() == Role.ADMIN || current.getRole().isReadOnly())
            throw new BussinessException("Your role cannot submit work location requests");
        validate(request);
        WorkLocationRequest entity = new WorkLocationRequest();
        entity.setUser(current);
        entity.setType(request.type());
        entity.setFromDate(request.fromDate());
        entity.setToDate(request.toDate());
        entity.setFromDayPart(request.fromDayPart());
        entity.setToDayPart(request.toDayPart());
        entity.setClientName(trimToNull(request.clientName()));
        entity.setClientAddress(trimToNull(request.clientAddress()));
        entity.setReason(request.reason().trim());
        entity.setStatus(current.getRole().canReviewPeopleInDepartment()
                ? LeaveRequestStatus.PENDING_ADMIN : LeaveRequestStatus.PENDING_MANAGER);
        if (current.getRole().canReviewPeopleInDepartment()) {
            entity.setManagerApprovedBy(current);
            entity.setManagerApprovedAt(LocalDateTime.now(BUSINESS_ZONE));
        }
        return response(requests.save(entity));
    }

    @Override @Transactional(readOnly = true)
    public List<WorkLocationRequestDto.Response> myRequests() {
        return requests.findByUserOrderByCreatedAtDesc(users.currentUser()).stream().map(this::response).toList();
    }

    @Override @Transactional(readOnly = true)
    public List<WorkLocationRequestDto.Response> pendingForApproval() {
        User current = users.currentUser();
        if (current.getRole() == Role.ADMIN)
            return requests.findByStatusOrderByCreatedAtDesc(LeaveRequestStatus.PENDING_ADMIN).stream().map(this::response).toList();
        if (current.getRole().canReviewPeopleInDepartment() && current.getDepartment() != null)
            return requests.findByUserDepartmentIdOrderByCreatedAtDesc(current.getDepartment().getId()).stream()
                    .filter(item -> item.getStatus() == LeaveRequestStatus.PENDING_MANAGER || item.getStatus() == LeaveRequestStatus.PENDING)
                    .map(this::response).toList();
        return List.of();
    }

    @Override
    public WorkLocationRequestDto.Response decide(Long id, LeaveRequestStatus decision) {
        if (decision != LeaveRequestStatus.APPROVED && decision != LeaveRequestStatus.REJECTED)
            throw new BussinessException("Status must be APPROVED or REJECTED");
        User current = users.currentUser();
        WorkLocationRequest request = requests.findById(id)
                .orElseThrow(() -> new BussinessException("Work location request not found: " + id));
        if (request.getUser().getId().equals(current.getId()))
            throw new BussinessException("You cannot approve your own request");
        LocalDateTime now = LocalDateTime.now(BUSINESS_ZONE);
        if (current.getRole().canReviewPeopleInDepartment()) {
            verifyManagerDepartment(current, request.getUser());
            if (request.getStatus() != LeaveRequestStatus.PENDING_MANAGER && request.getStatus() != LeaveRequestStatus.PENDING)
                throw new BussinessException("This request is not waiting for manager approval");
            request.setManagerApprovedBy(current);
            request.setManagerApprovedAt(now);
            request.setStatus(decision == LeaveRequestStatus.APPROVED
                    ? LeaveRequestStatus.PENDING_ADMIN : LeaveRequestStatus.REJECTED_MANAGER);
        } else if (current.getRole() == Role.ADMIN) {
            if (request.getStatus() != LeaveRequestStatus.PENDING_ADMIN)
                throw new BussinessException("This request is not waiting for admin approval");
            request.setAdminApprovedBy(current);
            request.setAdminApprovedAt(now);
            request.setStatus(decision == LeaveRequestStatus.APPROVED
                    ? LeaveRequestStatus.APPROVED : LeaveRequestStatus.REJECTED_ADMIN);
        } else throw new BussinessException("Only a manager and then an admin can decide this request");
        return response(requests.save(request));
    }

    @Override
    public WorkLocationRequestDto.Response cancel(Long id) {
        User current = users.currentUser();
        WorkLocationRequest request = requests.findById(id)
                .orElseThrow(() -> new BussinessException("Work location request not found: " + id));
        if (!request.getUser().getId().equals(current.getId()))
            throw new BussinessException("You can only cancel your own request");
        if (!request.getFromDate().isAfter(LocalDate.now(BUSINESS_ZONE)))
            throw new BussinessException("Only future requests can be cancelled");
        if (!List.of(LeaveRequestStatus.PENDING, LeaveRequestStatus.PENDING_MANAGER,
                LeaveRequestStatus.PENDING_ADMIN, LeaveRequestStatus.APPROVED).contains(request.getStatus()))
            throw new BussinessException("This request can no longer be cancelled");
        request.setStatus(LeaveRequestStatus.CANCELLED);
        request.setCancelledBy(current);
        request.setCancelledAt(LocalDateTime.now(BUSINESS_ZONE));
        return response(requests.save(request));
    }

    private void validate(WorkLocationRequestDto.SaveRequest request) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (request.fromDate().isBefore(today) || request.toDate().isBefore(request.fromDate()))
            throw new BussinessException("Work location dates must be today or later and in chronological order");
        if (request.fromDate().equals(request.toDate()) && request.fromDayPart() == LeaveDayPart.AFTERNOON
                && request.toDayPart() == LeaveDayPart.MORNING)
            throw new BussinessException("The ending session cannot be before the starting session");
        if (LeaveWorkdayCalculator.calculate(request.fromDate(), request.toDate(), request.fromDayPart(), request.toDayPart()) == 0)
            throw new BussinessException("The request must include at least one working session");
        if (request.type() == WorkLocationType.CLIENT_SITE
                && (trimToNull(request.clientName()) == null || trimToNull(request.clientAddress()) == null))
            throw new BussinessException("Client name and address are required for client-site work");
    }

    private void verifyManagerDepartment(User manager, User employee) {
        if (!manager.getRole().canReviewPeopleInDepartment() || manager.getDepartment() == null
                || employee.getDepartment() == null || !manager.getDepartment().getId().equals(employee.getDepartment().getId()))
            throw new BussinessException("You can only manage requests in your department");
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private WorkLocationRequestDto.Response response(WorkLocationRequest request) {
        return new WorkLocationRequestDto.Response(request.getId(), request.getUser().getId(), request.getUser().getFullName(),
                request.getType(), request.getFromDate(), request.getToDate(), request.getFromDayPart(), request.getToDayPart(),
                LeaveWorkdayCalculator.calculate(request.getFromDate(), request.getToDate(), request.getFromDayPart(), request.getToDayPart()),
                request.getClientName(), request.getClientAddress(),
                request.getReason(), request.getStatus(),
                request.getManagerApprovedBy() == null ? null : request.getManagerApprovedBy().getFullName(),
                request.getAdminApprovedBy() == null ? null : request.getAdminApprovedBy().getFullName(),
                request.getCancelledAt(), request.getCreatedAt());
    }
}
