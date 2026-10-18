package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.NotificationDto;
import com.example.demo.entity.LeaveRequestStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.TaskStatus;
import com.example.demo.entity.WorkRequestStatus;
import com.example.demo.service.UserService;
import com.example.demo.repository.LeaveRequestRepository;
import com.example.demo.repository.NotificationReadRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.WorkRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final UserService users;
    private final TaskRepository tasks;
    private final LeaveRequestRepository leaveRequests;
    private final NotificationReadRepository notificationReads;
    private final WorkRequestRepository workRequests;

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<List<NotificationDto.Response>> all() {
        var current = users.currentUser();
        var result = new ArrayList<NotificationDto.Response>();
        var id = new AtomicLong(1);

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        tasks.findByAssignedToIdOrderByDeadlineAsc(current.getId()).stream()
            .filter(task -> task.getDeadline() != null && task.getStatus() != TaskStatus.DONE && task.getStatus() != TaskStatus.CANCELLED)
            .filter(task -> ChronoUnit.DAYS.between(today, task.getDeadline()) <= 3)
            .forEach(task -> {
                long days = ChronoUnit.DAYS.between(today, task.getDeadline());
                String title = days < 0 ? "Công việc đã quá hạn" : days == 0 ? "Công việc đến hạn hôm nay" : "Công việc sắp đến hạn";
                String message = task.getTaskName() + (days < 0 ? " đã quá hạn " + Math.abs(days) + " ngày"
                    : " còn " + days + " ngày để hoàn thành");
                result.add(new NotificationDto.Response(id.getAndIncrement(), "TASK", title, message,
                    days <= 0 ? "HIGH" : "MEDIUM", task.getDeadline().atStartOfDay(), task.getId()));
            });

        workRequests.findAll().stream()
            .filter(request -> request.getStatus() != WorkRequestStatus.COMPLETED && request.getStatus() != WorkRequestStatus.CANCELLED)
            .filter(request -> request.getCoordinator() != null && request.getCoordinator().getId().equals(current.getId())
                || current.getRole() == Role.PROJECT_MANAGER && current.getDepartment() != null
                    && request.getProject().getDepartment().getId().equals(current.getDepartment().getId())
                || current.getRole() == Role.TEAM_LEAD && current.getDepartment() != null
                    && request.getExecutingDepartment().getId().equals(current.getDepartment().getId()))
            .filter(request -> ChronoUnit.DAYS.between(today, request.getDeadline()) <= 7)
            .forEach(request -> {
                long days = ChronoUnit.DAYS.between(today, request.getDeadline());
                String title = days < 0 ? "Phiếu yêu cầu đã quá hạn" : "Phiếu yêu cầu sắp đến hạn";
                String message = request.getRequestCode() + " · " + request.getTitle()
                    + (days < 0 ? " đã quá hạn " + Math.abs(days) + " ngày" : " còn " + days + " ngày");
                result.add(new NotificationDto.Response(id.getAndIncrement(), "REQUEST", title, message,
                    days <= 1 ? "HIGH" : "MEDIUM", request.getDeadline().atStartOfDay(), request.getId()));
            });

        leaveRequests.findByUserOrderByCreatedAtDesc(current).stream()
            .filter(request -> request.getUser() != null && request.getFromDate() != null && request.getToDate() != null)
                .filter(request -> request.getStatus() != LeaveRequestStatus.PENDING)
                .forEach(request -> result.add(new NotificationDto.Response(
                        id.getAndIncrement(), "LEAVE", "Cập nhật đơn nghỉ phép",
                        "Đơn nghỉ từ " + request.getFromDate() + " đến " + request.getToDate() + " đã " + request.getStatus(),
                        request.getStatus() == LeaveRequestStatus.REJECTED ? "HIGH" : "MEDIUM",
                        request.getCreatedAt(), request.getId())));

        if (current.getRole() == Role.ADMIN) {
            appendPending(result, id, leaveRequests.findByStatusOrderByCreatedAtDesc(LeaveRequestStatus.PENDING));
        } else if (current.getRole().canReviewPeopleInDepartment() && current.getDepartment() != null) {
            appendPending(result, id, leaveRequests.findByUserDepartmentIdOrderByCreatedAtDesc(current.getDepartment().getId()));
        }

        result.replaceAll(item -> new NotificationDto.Response(
                item.id(), item.type(), item.title(), item.message(), item.priority(), item.createdAt(),
                item.relatedId(), notificationKey(item), !notificationReads.existsByUserAndNotificationKey(current, notificationKey(item))));
        result.sort(Comparator.comparing((NotificationDto.Response item) -> item.createdAt(),
            Comparator.nullsLast(Comparator.reverseOrder())));
        return ApiResponse.ok(result);
    }

    @org.springframework.web.bind.annotation.PostMapping("/read")
    public void markRead(@org.springframework.web.bind.annotation.RequestBody NotificationDto.ReadRequest request) {
        if (request == null || request.key() == null || request.key().isBlank()) return;
        var current = users.currentUser();
        if (!notificationReads.existsByUserAndNotificationKey(current, request.key())) {
            var read = new com.example.demo.entity.NotificationRead();
            read.setUser(current);
            read.setNotificationKey(request.key());
            notificationReads.save(read);
        }
    }

    private String notificationKey(NotificationDto.Response item) {
        return item.type() + ":" + item.relatedId() + ":" + Integer.toHexString((item.title() + item.message()).hashCode());
    }

    private void appendPending(List<NotificationDto.Response> result, AtomicLong id, List<com.example.demo.entity.LeaveRequest> requests) {
        requests.stream().filter(request -> request.getStatus() == LeaveRequestStatus.PENDING && request.getUser() != null).forEach(request ->
                result.add(new NotificationDto.Response(
                        id.getAndIncrement(), "LEAVE", "Đơn nghỉ phép cần duyệt",
                        request.getUser().getFullName() + " đã gửi đơn nghỉ phép cần xử lý",
                        "HIGH", request.getCreatedAt(), request.getId())));
    }
}
