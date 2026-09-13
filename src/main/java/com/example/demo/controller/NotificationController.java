package com.example.demo.controller;

import com.example.demo.dto.ApiResponse;
import com.example.demo.dto.NotificationDto;
import com.example.demo.entity.LeaveRequestStatus;
import com.example.demo.entity.Role;
import com.example.demo.service.UserService;
import com.example.demo.repository.LeaveRequestRepository;
import com.example.demo.repository.NotificationReadRepository;
import com.example.demo.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final UserService users;
    private final TaskRepository tasks;
    private final LeaveRequestRepository leaveRequests;
    private final NotificationReadRepository notificationReads;

    @GetMapping
    public ApiResponse<List<NotificationDto.Response>> all() {
        var current = users.currentUser();
        var result = new ArrayList<NotificationDto.Response>();
        var id = new AtomicLong(1);

        tasks.findByAssignedToIdOrderByDeadlineAsc(current.getId()).stream()
                .filter(task -> task.getStatus().name().equals("REVIEW") || task.getStatus().name().equals("IN_PROGRESS"))
                .filter(task -> task.getDeadline() != null && task.getStatus() != null)
                .forEach(task -> result.add(new NotificationDto.Response(
                        id.getAndIncrement(), "TASK", "Công việc cần theo dõi",
                        task.getTaskName() + " đang ở trạng thái " + task.getStatus(),
                        task.getStatus().name().equals("REVIEW") ? "HIGH" : "MEDIUM",
                        task.getDeadline().atStartOfDay(), task.getId())));

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
        } else if (current.getRole() == Role.MANAGER && current.getDepartment() != null) {
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
        return item.type() + ":" + item.relatedId();
    }

    private void appendPending(List<NotificationDto.Response> result, AtomicLong id, List<com.example.demo.entity.LeaveRequest> requests) {
        requests.stream().filter(request -> request.getStatus() == LeaveRequestStatus.PENDING && request.getUser() != null).forEach(request ->
                result.add(new NotificationDto.Response(
                        id.getAndIncrement(), "LEAVE", "Đơn nghỉ phép cần duyệt",
                        request.getUser().getFullName() + " đã gửi đơn nghỉ phép cần xử lý",
                        "HIGH", request.getCreatedAt(), request.getId())));
    }
}
