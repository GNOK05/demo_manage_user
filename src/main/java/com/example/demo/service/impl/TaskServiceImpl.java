package com.example.demo.service.impl;

import com.example.demo.dto.TaskDto;
import com.example.demo.entity.*;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.service.TaskService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class TaskServiceImpl implements TaskService {
    private static final Map<TaskStatus, EnumSet<TaskStatus>> TRANSITIONS = Map.of(
        TaskStatus.BACKLOG, EnumSet.of(TaskStatus.TODO, TaskStatus.CANCELLED),
        TaskStatus.TODO, EnumSet.of(TaskStatus.IN_PROGRESS, TaskStatus.BACKLOG, TaskStatus.BLOCKED, TaskStatus.CANCELLED),
        TaskStatus.IN_PROGRESS, EnumSet.of(TaskStatus.REVIEW, TaskStatus.BLOCKED, TaskStatus.CANCELLED),
        TaskStatus.REVIEW, EnumSet.of(TaskStatus.TESTING, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.CANCELLED),
        TaskStatus.TESTING, EnumSet.of(TaskStatus.DONE, TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.CANCELLED),
        TaskStatus.BLOCKED, EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.CANCELLED),
        TaskStatus.DONE, EnumSet.noneOf(TaskStatus.class),
        TaskStatus.CANCELLED, EnumSet.noneOf(TaskStatus.class)
    );

    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final UserService users;

    @Override public List<TaskDto.Response> byProject(Long projectId) {
        Project project = project(projectId);
        verifyCanView(project);
        return tasks.findByProjectId(Objects.requireNonNull(projectId)).stream().map(this::response).toList();
    }

    @Override public List<TaskDto.Response> myTasks() {
        return tasks.findAssignedOrTesting(users.currentUser().getId()).stream().map(this::response).toList();
    }

    @Override public List<TaskDto.Response> accessibleTasks(String keyword, Role assignedRole, TaskStatus status,
                                                             TaskPriority priority, boolean overdue, String sort) {
        User current = users.currentUser();
        List<Task> accessible;
        if (current.getRole() == Role.ADMIN || current.getRole() == Role.HR) {
            accessible = tasks.findAll();
        } else if (current.getRole() == Role.MEMBER) {
            accessible = tasks.findAssignedOrTesting(current.getId());
        } else if (current.getDepartment() != null) {
            accessible = projects.findByDepartmentId(current.getDepartment().getId()).stream()
                .flatMap(project -> tasks.findByProjectId(project.getId()).stream()).toList();
        } else {
            accessible = List.of();
        }
        return filterAndSort(accessible, keyword, assignedRole, status, priority, overdue, sort)
            .stream().map(this::response).toList();
    }

    private List<Task> filterAndSort(List<Task> source, String keyword, Role assignedRole, TaskStatus status,
                                     TaskPriority priority, boolean overdue, String sort) {
        String query = keyword == null ? "" : keyword.trim().toLowerCase();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var filtered = source.stream()
            .filter(task -> query.isBlank() || task.getTaskName().toLowerCase().contains(query)
                || task.getProject().getProjectName().toLowerCase().contains(query)
                || task.getAssignedTo() != null && task.getAssignedTo().getFullName().toLowerCase().contains(query))
            .filter(task -> assignedRole == null || task.getAssignedTo() != null && task.getAssignedTo().getRole() == assignedRole)
            .filter(task -> status == null || task.getStatus() == status)
            .filter(task -> priority == null || task.getPriority() == priority)
            .filter(task -> !overdue || task.getDeadline().isBefore(today)
                && task.getStatus() != TaskStatus.DONE && task.getStatus() != TaskStatus.CANCELLED)
            .toList();
        Comparator<Task> comparator = switch (sort == null ? "DEFAULT" : sort.toUpperCase()) {
            case "NEWEST" -> Comparator.comparing(Task::getId, Comparator.nullsLast(Comparator.reverseOrder()));
            case "OLDEST" -> Comparator.comparing(Task::getId, Comparator.nullsLast(Comparator.naturalOrder()));
            case "DEADLINE_DESC" -> Comparator.comparing(Task::getDeadline, Comparator.nullsLast(Comparator.reverseOrder()));
            case "NAME_ASC" -> Comparator.comparing(task -> task.getTaskName().toLowerCase());
            case "NAME_DESC" -> Comparator.comparing((Task task) -> task.getTaskName().toLowerCase()).reversed();
            default -> Comparator.comparing(Task::getDeadline, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return filtered.stream().sorted(comparator).toList();
    }

    @Override public TaskDto.Response save(TaskDto.SaveRequest request) {
        Project project = project(request.projectId());
        verifyCanCreate(project, request.assignedToId());
        return response(tasks.save(build(new Task(), request, project, true)));
    }

    @Override public TaskDto.Response update(Long id, TaskDto.SaveRequest request) {
        Task task = task(id);
        verifyCanEdit(task);
        Project project = project(request.projectId());
        verifyCanCreate(project, request.assignedToId());
        return response(build(task, request, project, false));
    }

    @Override public TaskDto.Response updateStatus(Long id, TaskDto.StatusRequest request) {
        Task task = task(id);
        User current = users.currentUser();
        TaskStatus from = task.getStatus(), to = request.status();
        if (from == to) return response(task);
        if (!TRANSITIONS.getOrDefault(from, EnumSet.noneOf(TaskStatus.class)).contains(to)) {
            throw new BussinessException("Invalid task transition: " + from + " -> " + to);
        }
        verifyTransitionRole(task, current, from, to);
        if (to == TaskStatus.BLOCKED && (request.blockedReason() == null || request.blockedReason().isBlank())) {
            throw new BussinessException("A reason is required when blocking a task");
        }
        task.setStatus(to);
        task.setBlockedReason(to == TaskStatus.BLOCKED ? request.blockedReason().trim() : null);
        return response(task);
    }

    @Override public void delete(Long id) {
        Task task = task(id);
        User current = users.currentUser();
        boolean allowed = current.getRole() == Role.ADMIN
            || current.getRole() == Role.PROJECT_MANAGER && sameDepartment(current, task.getProject())
            || current.getRole() == Role.TEAM_LEAD && sameDepartment(current, task.getProject())
                && task.getStatus() != TaskStatus.DONE;
        if (!allowed) throw new BussinessException("You are not allowed to delete this task");
        tasks.delete(task);
    }

    private Task build(Task task, TaskDto.SaveRequest request, Project project, boolean creating) {
        if (request.deadline().isBefore(project.getStartDate()) || request.deadline().isAfter(project.getEndDate()))
            throw new BussinessException("Task deadline must be within project dates");
        LocalDate startDate = request.startDate() != null ? request.startDate()
            : task.getStartDate() != null ? task.getStartDate() : project.getStartDate();
        if (startDate.isBefore(project.getStartDate()) || startDate.isAfter(project.getEndDate()) || startDate.isAfter(request.deadline()))
            throw new BussinessException("Task start date must be within project dates and not after its deadline");
        TaskPriority priority = request.priority() != null ? request.priority()
            : task.getPriority() != null ? task.getPriority() : TaskPriority.NORMAL;
        // Status changes must go through updateStatus so workflow and role checks cannot be bypassed by PUT.
        TaskStatus status = creating ? TaskStatus.BACKLOG : task.getStatus();
        String blockedReason = request.blockedReason() != null ? request.blockedReason() : task.getBlockedReason();
        if (status == TaskStatus.BLOCKED && (blockedReason == null || blockedReason.isBlank()))
            throw new BussinessException("A reason is required when blocking a task");
        task.setAssignedTo(validateProjectUser(request.assignedToId(), project, "Task assignee"));
        User tester = validateProjectUser(request.testerId(), project, "Tester");
        if (tester != null && tester.getRole() != Role.QA) throw new BussinessException("Tester must have the QA role");
        task.setTester(tester);
        task.setTaskName(request.taskName()); task.setDescription(request.description()); task.setProject(project);
        task.setDeadline(request.deadline()); task.setStartDate(startDate); task.setPriority(priority);
        task.setEstimatedHours(request.estimatedHours()); task.setAcceptanceCriteria(request.acceptanceCriteria());
        task.setStatus(status); task.setBlockedReason(status == TaskStatus.BLOCKED ? blockedReason.trim() : null);
        if (task.getCreatedBy() == null) task.setCreatedBy(users.currentUser());
        return task;
    }

    private User validateProjectUser(Long userId, Project project, String label) {
        if (userId == null) return null;
        User user = users.findEntity(userId);
        if (user.getDepartment() == null || !user.getDepartment().getId().equals(project.getDepartment().getId()))
            throw new BussinessException(label + " must belong to the project department");
        return user;
    }

    private void verifyCanCreate(Project project, Long assigneeId) {
        User current = users.currentUser();
        if (current.getRole().isReadOnly() || current.getRole() == Role.HR)
            throw new BussinessException("Your role cannot create or edit tasks");
        if (current.getRole() == Role.ADMIN) return;
        if (!sameDepartment(current, project)) throw new BussinessException("You can only manage tasks in your department");
        if (!current.getRole().canManageTasks() && assigneeId != null && !assigneeId.equals(current.getId()))
            throw new BussinessException("Members and QA can only assign a new task to themselves");
    }

    private void verifyCanEdit(Task task) {
        User current = users.currentUser();
        boolean participant = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(current.getId())
            || task.getTester() != null && task.getTester().getId().equals(current.getId())
            || task.getCreatedBy() != null && task.getCreatedBy().getId().equals(current.getId());
        if (current.getRole() == Role.ADMIN) return;
        if (!sameDepartment(current, task.getProject()) || !current.getRole().canManageTasks() && !participant)
            throw new BussinessException("You are not allowed to edit this task");
    }

    private void verifyCanView(Project project) {
        User current = users.currentUser();
        if (current.getRole() == Role.ADMIN || current.getRole() == Role.HR) return;
        if (!sameDepartment(current, project)) throw new BussinessException("You cannot view tasks in this project");
    }

    private void verifyTransitionRole(Task task, User current, TaskStatus from, TaskStatus to) {
        boolean manager = current.getRole() == Role.ADMIN
            || current.getRole().canManageTasks() && sameDepartment(current, task.getProject());
        boolean assignee = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(current.getId());
        boolean tester = task.getTester() != null && task.getTester().getId().equals(current.getId());
        if (to == TaskStatus.CANCELLED || from == TaskStatus.BACKLOG || to == TaskStatus.BACKLOG) {
            if (!manager) throw new BussinessException("Only project managers or team leads can plan or cancel tasks");
        } else if (from == TaskStatus.REVIEW && (to == TaskStatus.TESTING || to == TaskStatus.IN_PROGRESS)) {
            if (!manager) throw new BussinessException("Only project managers or team leads can approve or reject review");
        } else if (from == TaskStatus.TESTING && (to == TaskStatus.DONE || to == TaskStatus.IN_PROGRESS)) {
            if (!tester && current.getRole() != Role.QA && !manager)
                throw new BussinessException("Only the assigned QA or a task manager can complete testing");
        } else if (!manager && !assignee) {
            throw new BussinessException("Only the assignee or a task manager can update this task");
        }
    }

    private boolean sameDepartment(User user, Project project) {
        return user.getDepartment() != null && project.getDepartment() != null
            && user.getDepartment().getId().equals(project.getDepartment().getId());
    }

    private Project project(Long id) { return projects.findById(Objects.requireNonNull(id))
        .orElseThrow(() -> new BussinessException("Project not found: " + id)); }
    private Task task(Long id) { return tasks.findById(Objects.requireNonNull(id))
        .orElseThrow(() -> new BussinessException("Task not found: " + id)); }
    private TaskDto.Response response(Task t) { return new TaskDto.Response(t.getId(), t.getTaskName(), t.getDescription(),
        t.getProject().getId(), t.getProject().getProjectName(), t.getAssignedTo() == null ? null : t.getAssignedTo().getId(),
        t.getAssignedTo() == null ? null : t.getAssignedTo().getFullName(), t.getTester() == null ? null : t.getTester().getId(),
        t.getTester() == null ? null : t.getTester().getFullName(), t.getCreatedBy() == null ? null : t.getCreatedBy().getId(),
        t.getStatus(), t.getStartDate(), t.getDeadline(), t.getPriority(), t.getEstimatedHours(),
        t.getAcceptanceCriteria(), t.getBlockedReason()); }
}
