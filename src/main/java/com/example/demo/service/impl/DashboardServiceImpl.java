package com.example.demo.service.impl;

import com.example.demo.dto.DashboardDto;
import com.example.demo.entity.Attendance;
import com.example.demo.entity.Project;
import com.example.demo.entity.Role;
import com.example.demo.entity.Task;
import com.example.demo.entity.TaskStatus;
import com.example.demo.entity.User;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.LeaveRequestRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.DashboardService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardServiceImpl implements DashboardService {
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final AttendanceRepository attendance;
    private final LeaveRequestRepository leaveRequests;
    private final UserService userService;

    @Override
    public DashboardDto.Summary summary() {
        User current = userService.currentUser();
        List<User> visibleUsers;
        List<Project> visibleProjects;
        List<Task> visibleTasks;
        List<Attendance> visibleAttendance;

        if (current.getRole() == Role.ADMIN || current.getRole() == Role.HR) {
            visibleUsers = users.findAll();
            visibleProjects = projects.findAll();
            visibleTasks = tasks.findAll();
            visibleAttendance = attendance.findAll();
        } else if (current.getRole() == Role.PROJECT_MANAGER) {
            visibleUsers = current.getDepartment() == null ? List.of(current) : users.findByDepartmentId(current.getDepartment().getId());
            visibleProjects = projects.findAll();
            visibleTasks = tasks.findAll();
            visibleAttendance = current.getDepartment() == null ? attendance.findByUserIdOrderByDateDesc(current.getId())
                : attendance.findByUserDepartmentIdOrderByDateDesc(current.getDepartment().getId());
        } else if (current.getRole() == Role.TEAM_LEAD && current.getDepartment() != null) {
            visibleUsers = users.findByDepartmentId(current.getDepartment().getId());
            visibleProjects = projects.findByParticipantDepartmentId(current.getDepartment().getId());
            visibleTasks = tasks.findAll().stream().filter(task -> task.getWorkRequest() != null
                && task.getWorkRequest().getExecutingDepartment().getId().equals(current.getDepartment().getId())).toList();
            visibleAttendance = attendance.findByUserDepartmentIdOrderByDateDesc(current.getDepartment().getId());
        } else if (current.getRole() == Role.MEMBER || current.getRole() == Role.QA) {
            visibleUsers = List.of(current);
            visibleProjects = projects.findAssignedToUser(current.getId());
            visibleTasks = tasks.findAssignedOrTesting(current.getId());
            visibleAttendance = attendance.findByUserIdOrderByDateDesc(current.getId());
        } else {
            visibleUsers = List.of(current);
            visibleProjects = current.getDepartment() == null ? projects.findAssignedToUser(current.getId())
                    : projects.findByParticipantDepartmentId(current.getDepartment().getId());
            visibleTasks = current.getDepartment() == null ? tasks.findAssignedOrTesting(current.getId())
                    : tasks.findAll().stream().filter(task -> task.getWorkRequest() != null
                        && task.getWorkRequest().getExecutingDepartment().getId().equals(current.getDepartment().getId())).toList();
            visibleAttendance = attendance.findByUserIdOrderByDateDesc(current.getId());
        }

        LocalDate today = LocalDate.now();
        Map<String, Long> attendanceToday = new java.util.HashMap<>();
        attendanceToday.put("PRESENT", 0L);
        attendanceToday.put("LATE", 0L);
        attendanceToday.put("LEAVE", 0L);
        attendanceToday.put("ABSENT", 0L);
        for (Attendance record : visibleAttendance) {
            if (today.equals(record.getDate()) && record.getStatus() != null) {
                String status = record.getStatus().name();
                attendanceToday.put(status, attendanceToday.getOrDefault(status, 0L) + 1L);
            }
        }
        long accountedToday = attendanceToday.get("PRESENT") + attendanceToday.get("LATE")
                + attendanceToday.get("LEAVE") + attendanceToday.get("ABSENT");
        if (accountedToday < visibleUsers.size()) {
            attendanceToday.put("ABSENT", attendanceToday.get("ABSENT") + visibleUsers.size() - accountedToday);
        }

        long completedTasks = visibleTasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE).count();
        long overdueTasks = visibleTasks.stream()
                .filter(task -> task.getDeadline() != null && task.getDeadline().isBefore(today)
                        && task.getStatus() != TaskStatus.DONE)
                .count();
        long pendingLeaveRequests = current.getRole() == Role.ADMIN
                ? leaveRequests.findByStatusOrderByCreatedAtDesc(com.example.demo.entity.LeaveRequestStatus.PENDING).size()
                : current.getRole().canReviewPeopleInDepartment() && current.getDepartment() != null
                    ? leaveRequests.findByUserDepartmentIdOrderByCreatedAtDesc(current.getDepartment().getId()).stream()
                        .filter(request -> request.getStatus() == com.example.demo.entity.LeaveRequestStatus.PENDING).count()
                    : 0;

        return new DashboardDto.Summary(
                visibleUsers.size(),
                current.getRole() == Role.ADMIN || current.getRole() == Role.HR
                        ? departments.count() : current.getDepartment() == null ? 0 : 1,
                visibleProjects.size(),
                visibleProjects.stream().filter(project -> project.getStatus() == com.example.demo.entity.ProjectStatus.IN_PROGRESS).count(),
                visibleProjects.stream().filter(project -> project.getStatus() == com.example.demo.entity.ProjectStatus.COMPLETED).count(),
                visibleTasks.size(),
                visibleTasks.size() - completedTasks,
                completedTasks,
                overdueTasks,
                attendanceToday,
                pendingLeaveRequests
        );
    }
}
