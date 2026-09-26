package com.example.demo.service.impl;

import com.example.demo.dto.LeaveRequestDto;
import com.example.demo.entity.*;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.AnnualLeaveBalanceRepository;
import com.example.demo.repository.LeaveRequestRepository;
import com.example.demo.service.LeaveRequestService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class LeaveRequestServiceImpl implements LeaveRequestService {
    private final LeaveRequestRepository leaveRequests;
    private final AnnualLeaveBalanceRepository balances;
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
        entity.setStatus(current.getRole() == Role.MANAGER
            ? LeaveRequestStatus.PENDING_ADMIN : LeaveRequestStatus.PENDING_MANAGER);
        if (current.getRole() == Role.MANAGER) {
            entity.setManagerApprovedBy(current);
            entity.setManagerApprovedAt(LocalDateTime.now());
        }
        int requestedWorkdays = workdays(request.fromDate(), request.toDate());
        if (requestedWorkdays == 0) throw new BussinessException("Leave request must include at least one working day");
        if (request.type() == LeaveRequestType.ANNUAL) {
            if (request.fromDate().getYear() != request.toDate().getYear()) {
                throw new BussinessException("Annual leave requests cannot cross calendar years");
            }
            LeaveRequestDto.BalanceResponse balance = balance(current, request.fromDate().getYear());
            if (balance.remainingDays() - balance.pendingDays() < requestedWorkdays) {
                throw new BussinessException("Insufficient annual leave balance");
            }
        }

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
    public List<LeaveRequestDto.Response> requestsForUser(Long userId) {
        User current = userService.currentUser();
        User selected = userService.findEntity(userId);
        if (current.getRole() == Role.MANAGER) verifyManagerDepartment(current, selected);
        else if (current.getRole() != Role.ADMIN && !current.getId().equals(selected.getId())) {
            throw new BussinessException("You can only view your own leave requests");
        }
        return leaveRequests.findByUserOrderByCreatedAtDesc(selected).stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<LeaveRequestDto.Response> pendingForApproval() {
        User current = userService.currentUser();

        if (current.getRole() == Role.ADMIN) {
            return leaveRequests.findByStatusOrderByCreatedAtDesc(LeaveRequestStatus.PENDING_ADMIN).stream().map(this::toResponse).toList();
        }

        if (current.getRole() == Role.MANAGER && current.getDepartment() != null) {
            return leaveRequests.findByUserDepartmentIdOrderByCreatedAtDesc(current.getDepartment().getId()).stream()
                    .filter(r -> r.getStatus() == LeaveRequestStatus.PENDING_MANAGER || r.getStatus() == LeaveRequestStatus.PENDING)
                    .map(this::toResponse)
                    .toList();
        }

        return List.of();
    }

    @Override
    public LeaveRequestDto.Response approve(Long id, LeaveRequestStatus status) {
        User current = userService.currentUser();
        LeaveRequest request = leaveRequests.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new BussinessException("Leave request not found: " + id));

        if (request.getUser() == null) {
            throw new BussinessException("Leave request owner not found");
        }
        if (status != LeaveRequestStatus.APPROVED && status != LeaveRequestStatus.REJECTED) {
            throw new BussinessException("Status must be APPROVED or REJECTED");
        }

        if (request.getUser().getId().equals(current.getId())) {
            throw new BussinessException("You cannot approve your own leave request");
        }

        LocalDateTime now = LocalDateTime.now();
        if (current.getRole() == Role.MANAGER) {
            verifyManagerDepartment(current, request.getUser());
            if (request.getStatus() != LeaveRequestStatus.PENDING_MANAGER && request.getStatus() != LeaveRequestStatus.PENDING) {
                throw new BussinessException("This request is not waiting for manager approval");
            }
            request.setManagerApprovedBy(current);
            request.setManagerApprovedAt(now);
            request.setStatus(status == LeaveRequestStatus.APPROVED
                    ? LeaveRequestStatus.PENDING_ADMIN : LeaveRequestStatus.REJECTED_MANAGER);
        } else if (current.getRole() == Role.ADMIN) {
            if (request.getStatus() != LeaveRequestStatus.PENDING_ADMIN) {
                throw new BussinessException("This request is not waiting for admin approval");
            }
            if (status == LeaveRequestStatus.APPROVED && request.getType() == LeaveRequestType.ANNUAL) {
                LeaveRequestDto.BalanceResponse balance = balance(request.getUser(), request.getFromDate().getYear());
                if (balance.usedDays() + workdays(request.getFromDate(), request.getToDate()) > balance.entitledDays()) {
                    throw new BussinessException("Insufficient annual leave balance");
                }
            }
            request.setAdminApprovedBy(current);
            request.setAdminApprovedAt(now);
            request.setApprovedBy(current);
            request.setApprovedAt(now);
            request.setStatus(status == LeaveRequestStatus.APPROVED
                    ? LeaveRequestStatus.APPROVED : LeaveRequestStatus.REJECTED_ADMIN);
        } else {
            throw new BussinessException("Only a manager and then an admin can decide this request");
        }

        LeaveRequest saved = leaveRequests.save(request);
        return toResponse(saved);
    }

    @Override
    public LeaveRequestDto.Response cancel(Long id) {
        User current = userService.currentUser();
        LeaveRequest request = leaveRequests.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new BussinessException("Leave request not found: " + id));
        if (!request.getUser().getId().equals(current.getId())) {
            throw new BussinessException("You can only cancel your own leave requests");
        }
        if (!request.getFromDate().isAfter(LocalDate.now())) {
            throw new BussinessException("Only leave requests starting in the future can be cancelled");
        }
        if (request.getStatus() != LeaveRequestStatus.PENDING && request.getStatus() != LeaveRequestStatus.PENDING_MANAGER
                && request.getStatus() != LeaveRequestStatus.PENDING_ADMIN && request.getStatus() != LeaveRequestStatus.APPROVED) {
            throw new BussinessException("This leave request can no longer be cancelled");
        }
        request.setStatus(LeaveRequestStatus.CANCELLED);
        request.setCancelledBy(current);
        request.setCancelledAt(LocalDateTime.now());
        return toResponse(leaveRequests.save(request));
    }

    @Override
    public LeaveRequestDto.BalanceResponse myBalance(int year) {
        return balance(userService.currentUser(), year);
    }

    @Override
    public LeaveRequestDto.BalanceResponse userBalance(Long userId, int year) {
        User current = userService.currentUser();
        User selected = userService.findEntity(userId);
        if (current.getRole() == Role.MANAGER) verifyManagerDepartment(current, selected);
        else if (current.getRole() != Role.ADMIN && !current.getId().equals(selected.getId())) {
            throw new BussinessException("You can only view your own annual leave balance");
        }
        return balance(selected, year);
    }

    @Override
    public LeaveRequestDto.BalanceResponse setBalance(Long userId, int year, int entitledDays) {
        if (userService.currentUser().getRole() != Role.ADMIN) throw new BussinessException("Only admins can set annual leave balances");
        if (year < 2000 || year > 2100 || entitledDays < 0 || entitledDays > 366) {
            throw new BussinessException("Invalid year or entitled leave days");
        }
        User selected = userService.findEntity(userId);
        AnnualLeaveBalance balance = balances.findByUserIdAndYear(userId, year).orElseGet(() -> {
            AnnualLeaveBalance created = new AnnualLeaveBalance();
            created.setUser(selected);
            created.setYear(year);
            return created;
        });
        balance.setEntitledDays(entitledDays);
        balances.save(balance);
        return balance(selected, year);
    }

    private LeaveRequestDto.BalanceResponse balance(User user, int year) {
        if (year < 2000 || year > 2100) throw new BussinessException("Invalid leave year");
        AnnualLeaveBalance record = balances.findByUserIdAndYear(user.getId(), year).orElseGet(() -> {
            AnnualLeaveBalance created = new AnnualLeaveBalance();
            created.setUser(user);
            created.setYear(year);
            created.setEntitledDays(12);
            return balances.save(created);
        });
        LocalDate first = LocalDate.of(year, 1, 1);
        LocalDate last = LocalDate.of(year, 12, 31);
        List<LeaveRequest> annualRequests = leaveRequests.findByUserOrderByCreatedAtDesc(user).stream()
                .filter(request -> request.getType() == LeaveRequestType.ANNUAL
                        && !request.getFromDate().isAfter(last) && !request.getToDate().isBefore(first))
                .toList();
        double used = annualRequests.stream().filter(request -> request.getStatus() == LeaveRequestStatus.APPROVED)
                .mapToInt(request -> workdays(request.getFromDate().isBefore(first) ? first : request.getFromDate(),
                        request.getToDate().isAfter(last) ? last : request.getToDate())).sum();
        double pending = annualRequests.stream().filter(request -> request.getStatus() == LeaveRequestStatus.PENDING
                        || request.getStatus() == LeaveRequestStatus.PENDING_MANAGER
                        || request.getStatus() == LeaveRequestStatus.PENDING_ADMIN)
                .mapToInt(request -> workdays(request.getFromDate().isBefore(first) ? first : request.getFromDate(),
                        request.getToDate().isAfter(last) ? last : request.getToDate())).sum();
        return new LeaveRequestDto.BalanceResponse(user.getId(), user.getFullName(), year,
                record.getEntitledDays(), used, pending, Math.max(0, record.getEntitledDays() - used));
    }

    private int workdays(LocalDate from, LocalDate to) {
        if (from.isAfter(to)) return 0;
        return (int) from.datesUntil(to.plusDays(1))
                .filter(date -> date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY)
                .count();
    }

    private void verifyManagerDepartment(User manager, User employee) {
        if (manager.getRole() != Role.MANAGER || manager.getDepartment() == null || employee.getDepartment() == null
                || !manager.getDepartment().getId().equals(employee.getDepartment().getId())) {
            throw new BussinessException("You can only manage leave requests in your department");
        }
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
                createdAtValue,
                request.getManagerApprovedBy() == null ? null : request.getManagerApprovedBy().getFullName(),
                request.getAdminApprovedBy() == null ? null : request.getAdminApprovedBy().getFullName(),
                request.getRejectionReason(),
                request.getCancelledAt(),
                workdays(request.getFromDate(), request.getToDate())
        );
    }
}
