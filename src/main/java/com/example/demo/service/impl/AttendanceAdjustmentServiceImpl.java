package com.example.demo.service.impl;

import com.example.demo.dto.AttendanceAdjustmentDto;
import com.example.demo.entity.Attendance;
import com.example.demo.entity.AttendanceAdjustmentRequest;
import com.example.demo.entity.AttendanceAdjustmentStatus;
import com.example.demo.entity.AttendanceSession;
import com.example.demo.entity.AttendanceStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.User;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.AttendanceAdjustmentRequestRepository;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.AttendanceSessionRepository;
import com.example.demo.service.AttendanceAdjustmentService;
import com.example.demo.service.PayrollService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.time.YearMonth;

@Service
@RequiredArgsConstructor
@Transactional
public class AttendanceAdjustmentServiceImpl implements AttendanceAdjustmentService {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final List<AttendanceAdjustmentStatus> OPEN_STATUSES = List.of(
            AttendanceAdjustmentStatus.PENDING_MANAGER, AttendanceAdjustmentStatus.PENDING_ADMIN);

    private final AttendanceAdjustmentRequestRepository requests;
    private final AttendanceSessionRepository sessions;
    private final AttendanceRepository attendance;
    private final UserService users;
    private final PayrollService payroll;

    @Override
    public AttendanceAdjustmentDto.Response create(AttendanceAdjustmentDto.SaveRequest input) {
        User current = users.currentUser();
        if (current.getRole() == Role.ADMIN) throw new BussinessException("Admins cannot submit attendance corrections");
        AttendanceSession session = sessions.findById(Objects.requireNonNull(input.sessionId()))
                .orElseThrow(() -> new BussinessException("Attendance session not found"));
        Attendance parent = session.getAttendance();
        User target = parent.getUser();
        ensurePeriodOpen(parent.getDate());
        if (current.getRole() == Role.EMPLOYEE) {
            if (!target.getId().equals(current.getId()) || input.userId() != null && !input.userId().equals(current.getId())) {
                throw new BussinessException("Employees can only request corrections to their own attendance");
            }
        } else if (current.getRole() == Role.MANAGER) {
            verifyManagerDepartment(current, target);
            if (input.userId() != null && !input.userId().equals(target.getId())) {
                throw new BussinessException("Session does not belong to the selected employee");
            }
        }
        LocalDate today = LocalDate.now(BUSINESS_ZONE);
        if (parent.getDate().isAfter(today)) throw new BussinessException("Future attendance cannot be corrected");
        if (parent.getDate().isBefore(today.minusDays(2))) {
            throw new BussinessException("Attendance can only be corrected within the last 2 days");
        }
        if (input.requestedCheckOut().isBefore(input.requestedCheckIn())) {
            throw new BussinessException("Check-out must be after check-in");
        }
        if (!input.requestedCheckIn().toLocalDate().equals(parent.getDate())
                || !input.requestedCheckOut().toLocalDate().equals(parent.getDate())) {
            throw new BussinessException("Corrected times must be on the attendance date");
        }
        if (requests.existsBySession_IdAndStatusIn(session.getId(), OPEN_STATUSES)) {
            throw new BussinessException("This session already has a pending correction");
        }
        verifyNoOverlap(session, input.requestedCheckIn(), input.requestedCheckOut());

        AttendanceAdjustmentRequest request = new AttendanceAdjustmentRequest();
        request.setSession(session);
        request.setUser(target);
        request.setRequestedBy(current);
        request.setOriginalCheckIn(session.getCheckInTime());
        request.setOriginalCheckOut(session.getCheckOutTime());
        request.setRequestedCheckIn(input.requestedCheckIn());
        request.setRequestedCheckOut(input.requestedCheckOut());
        request.setReason(input.reason().trim());
        if (current.getRole() == Role.MANAGER) {
            request.setStatus(AttendanceAdjustmentStatus.PENDING_ADMIN);
            if (!current.getId().equals(target.getId())) {
                request.setManagerApprovedBy(current);
                request.setManagerApprovedAt(LocalDateTime.now());
            }
        } else {
            request.setStatus(AttendanceAdjustmentStatus.PENDING_MANAGER);
        }
        return response(requests.save(request));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceAdjustmentDto.Response> mine() {
        Long userId = users.currentUser().getId();
        return requests.findByUserIdOrRequestedByIdOrderByCreatedAtDesc(userId, userId).stream().map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AttendanceAdjustmentDto.Response> pending() {
        User current = users.currentUser();
        if (current.getRole() != Role.ADMIN && current.getRole() != Role.MANAGER) {
            throw new BussinessException("Only managers and admins can review attendance corrections");
        }
        AttendanceAdjustmentStatus stage = current.getRole() == Role.ADMIN
                ? AttendanceAdjustmentStatus.PENDING_ADMIN : AttendanceAdjustmentStatus.PENDING_MANAGER;
        return requests.findByStatusOrderByCreatedAtAsc(stage).stream()
                .filter(request -> current.getRole() == Role.ADMIN
                        || request.getUser().getDepartment() != null && current.getDepartment() != null
                        && request.getUser().getDepartment().getId().equals(current.getDepartment().getId()))
                .map(this::response).toList();
    }

    @Override
    public AttendanceAdjustmentDto.Response decide(Long id, AttendanceAdjustmentStatus decision) {
        User current = users.currentUser();
        if (decision != AttendanceAdjustmentStatus.APPROVED && decision != AttendanceAdjustmentStatus.REJECTED_MANAGER
                && decision != AttendanceAdjustmentStatus.REJECTED_ADMIN) {
            throw new BussinessException("Decision must be approve or reject");
        }
        AttendanceAdjustmentRequest request = requests.findById(Objects.requireNonNull(id))
                .orElseThrow(() -> new BussinessException("Attendance adjustment not found"));
        if (request.getUser().getId().equals(current.getId()) || request.getRequestedBy().getId().equals(current.getId())) {
            throw new BussinessException("You cannot approve your own attendance correction");
        }
        if (current.getRole() == Role.MANAGER) {
            verifyManagerDepartment(current, request.getUser());
            if (request.getStatus() != AttendanceAdjustmentStatus.PENDING_MANAGER) {
                throw new BussinessException("This correction is not waiting for manager approval");
            }
            request.setManagerApprovedBy(current);
            request.setManagerApprovedAt(LocalDateTime.now());
            request.setStatus(decision == AttendanceAdjustmentStatus.APPROVED
                    ? AttendanceAdjustmentStatus.PENDING_ADMIN : AttendanceAdjustmentStatus.REJECTED_MANAGER);
        } else if (current.getRole() == Role.ADMIN) {
            if (request.getStatus() != AttendanceAdjustmentStatus.PENDING_ADMIN) {
                throw new BussinessException("This correction is not waiting for admin approval");
            }
            request.setAdminApprovedBy(current);
            request.setAdminApprovedAt(LocalDateTime.now());
            request.setStatus(decision == AttendanceAdjustmentStatus.APPROVED
                    ? AttendanceAdjustmentStatus.APPROVED : AttendanceAdjustmentStatus.REJECTED_ADMIN);
            if (decision == AttendanceAdjustmentStatus.APPROVED) apply(request);
        } else {
            throw new BussinessException("Only managers and admins can decide attendance corrections");
        }
        return response(requests.save(request));
    }

    private void apply(AttendanceAdjustmentRequest request) {
        AttendanceSession session = request.getSession();
        ensurePeriodOpen(session.getAttendance().getDate());
        if (!Objects.equals(session.getCheckInTime(), request.getOriginalCheckIn())
                || !Objects.equals(session.getCheckOutTime(), request.getOriginalCheckOut())) {
            throw new BussinessException("Attendance session changed after the correction was submitted");
        }
        verifyNoOverlap(session, request.getRequestedCheckIn(), request.getRequestedCheckOut());
        session.setCheckInTime(request.getRequestedCheckIn());
        session.setCheckOutTime(request.getRequestedCheckOut());
        sessions.save(session);

        Attendance parent = session.getAttendance();
        List<AttendanceSession> daySessions = sessions.findByAttendanceIdOrderByCheckInTimeAsc(parent.getId());
        AttendanceSession firstSession = daySessions.getFirst();
        parent.setCheckInTime(firstSession.getCheckInTime());
        parent.setCheckOutTime(daySessions.getLast().getCheckOutTime());
        parent.setStatus(firstSession.getCheckInTime().toLocalTime().isAfter(java.time.LocalTime.of(9, 0))
            ? AttendanceStatus.LATE : AttendanceStatus.PRESENT);
        attendance.save(parent);
    }

    private void ensurePeriodOpen(LocalDate date) {
        if (payroll.isClosed(YearMonth.from(date))) {
            throw new BussinessException("This payroll period is closed; ask an admin to reopen it before changing attendance");
        }
    }

    private void verifyNoOverlap(AttendanceSession target, LocalDateTime from, LocalDateTime to) {
        boolean overlaps = sessions.findByAttendanceIdOrderByCheckInTimeAsc(target.getAttendance().getId()).stream()
                .filter(session -> !session.getId().equals(target.getId()))
                .anyMatch(session -> session.getCheckOutTime() == null
                        || from.isBefore(session.getCheckOutTime()) && to.isAfter(session.getCheckInTime()));
        if (overlaps) throw new BussinessException("Corrected attendance overlaps another work session");
    }

    private void verifyManagerDepartment(User manager, User target) {
        if (manager.getDepartment() == null || target.getDepartment() == null
                || !manager.getDepartment().getId().equals(target.getDepartment().getId())) {
            throw new BussinessException("You can only manage attendance corrections in your department");
        }
    }

    private AttendanceAdjustmentDto.Response response(AttendanceAdjustmentRequest request) {
        return new AttendanceAdjustmentDto.Response(request.getId(), request.getSession().getId(),
                request.getUser().getId(), request.getUser().getFullName(), request.getRequestedBy().getFullName(),
                request.getSession().getAttendance().getDate(), request.getOriginalCheckIn(), request.getOriginalCheckOut(),
                request.getRequestedCheckIn(), request.getRequestedCheckOut(), request.getReason(), request.getStatus(),
                request.getManagerApprovedBy() == null ? null : request.getManagerApprovedBy().getFullName(),
                request.getAdminApprovedBy() == null ? null : request.getAdminApprovedBy().getFullName(), request.getCreatedAt());
    }
}