package com.example.demo.service.impl;

import com.example.demo.dto.TaskDto;
import com.example.demo.entity.*;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.ProjectDepartmentRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.WorkRequestRepository;
import com.example.demo.service.TaskService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.text.Normalizer;
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
    private final WorkRequestRepository workRequests;
    private final ProjectDepartmentRepository projectDepartments;
    private final UserService users;

    @Override public List<TaskDto.Response> byProject(Long projectId) {
        Project project = project(projectId);
        verifyCanView(project);
        User current = users.currentUser();
        return tasks.findByProjectId(Objects.requireNonNull(projectId)).stream()
            .filter(task -> canViewTask(task, current)).map(this::response).toList();
    }

    @Override public List<TaskDto.Response> myTasks() {
        return tasks.findAssignedOrTesting(users.currentUser().getId()).stream().map(this::response).toList();
    }

    @Override public List<TaskDto.Response> byWorkRequest(Long requestId) {
        WorkRequest request = workRequest(requestId);
        verifyCanView(request.getProject());
        User current = users.currentUser();
        return tasks.findByWorkRequestIdOrderByDeadlineAsc(requestId).stream()
            .filter(task -> canViewTask(task, current)).map(this::response).toList();
    }

    @Override public List<TaskDto.Response> accessibleTasks(String keyword, Role assignedRole, TaskStatus status,
                                                             TaskPriority priority, boolean overdue, String sort,
                                                             Long workRequestId) {
        User current = users.currentUser();
        List<Task> accessible;
        if (current.getRole() == Role.ADMIN || current.getRole() == Role.HR || current.getRole() == Role.PROJECT_MANAGER) {
            accessible = tasks.findAll();
        } else if (current.getRole() == Role.MEMBER || current.getRole() == Role.QA) {
            accessible = tasks.findAssignedOrTesting(current.getId());
        } else if (current.getDepartment() != null && (current.getRole() == Role.TEAM_LEAD || current.getRole() == Role.VIEWER)) {
            accessible = tasks.findAll().stream().filter(task -> task.getWorkRequest() != null
                && task.getWorkRequest().getExecutingDepartment().getId().equals(current.getDepartment().getId())).toList();
        } else {
            accessible = List.of();
        }
        return filterAndSort(accessible, keyword, assignedRole, status, priority, overdue, sort, workRequestId)
            .stream().map(this::response).toList();
    }

    private boolean canViewTask(Task task, User current) {
        if (current.getRole() == Role.ADMIN || current.getRole() == Role.HR || current.getRole() == Role.PROJECT_MANAGER) return true;
        if (current.getRole() == Role.MEMBER || current.getRole() == Role.QA) {
            return task.getAssignedTo() != null && task.getAssignedTo().getId().equals(current.getId())
                || task.getTester() != null && task.getTester().getId().equals(current.getId())
                || task.getCreatedBy() != null && task.getCreatedBy().getId().equals(current.getId());
        }
        return current.getDepartment() != null && task.getWorkRequest() != null
            && task.getWorkRequest().getExecutingDepartment().getId().equals(current.getDepartment().getId());
    }

    private List<Task> filterAndSort(List<Task> source, String keyword, Role assignedRole, TaskStatus status,
                                     TaskPriority priority, boolean overdue, String sort, Long workRequestId) {
        String query = searchable(keyword);
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        var filtered = source.stream()
            .filter(task -> workRequestId == null || task.getWorkRequest() != null
                && task.getWorkRequest().getId().equals(workRequestId))
            .filter(task -> query.isBlank() || searchable(task.getTaskName()).contains(query)
                || searchable(task.getProject().getProjectName()).contains(query)
                || task.getWorkRequest() != null && (searchable(task.getWorkRequest().getRequestCode()).contains(query)
                    || searchable(task.getWorkRequest().getTitle()).contains(query))
                || task.getAssignedTo() != null && searchable(task.getAssignedTo().getFullName()).contains(query))
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

    private String searchable(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd').replace('Đ', 'D')
            .toLowerCase(Locale.ROOT).trim();
    }

    @Override public TaskDto.Response save(TaskDto.SaveRequest request) {
        WorkRequest workRequest = resolveWorkRequest(request);
        Project project = workRequest.getProject();
        verifyCanCreate(project, request.assignedToId());
        return response(tasks.save(build(new Task(), request, project, workRequest, true)));
    }

    @Override public TaskDto.Response update(Long id, TaskDto.SaveRequest request) {
        Task task = task(id);
        verifyCanEdit(task);
        WorkRequest workRequest = resolveWorkRequest(request);
        Project project = workRequest.getProject();
        verifyCanCreate(project, request.assignedToId());
        return response(build(task, request, project, workRequest, false));
    }

    @Override public TaskDto.Response updateStatus(Long id, TaskDto.StatusRequest request) {
        Task task = task(id);
        User current = users.currentUser();
        if (current.getRole().isReadOnly()) throw new BussinessException("Read-only users cannot update task status");
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
        synchronizeWorkRequest(task.getWorkRequest());
        return response(task);
    }

    @Override public void delete(Long id) {
        Task task = task(id);
        User current = users.currentUser();
        boolean participantDepartment = current.getDepartment() != null
            && projectDepartments.existsByProjectIdAndDepartmentId(task.getProject().getId(), current.getDepartment().getId());
        boolean allowed = current.getRole() == Role.ADMIN
            || current.getRole() == Role.PROJECT_MANAGER && participantDepartment
            || current.getRole() == Role.TEAM_LEAD && task.getWorkRequest() != null && current.getDepartment() != null
                && current.getDepartment().getId().equals(task.getWorkRequest().getExecutingDepartment().getId())
                && task.getStatus() != TaskStatus.DONE;
        if (!allowed) throw new BussinessException("You are not allowed to delete this task");
        tasks.delete(task);
    }

    private Task build(Task task, TaskDto.SaveRequest request, Project project, WorkRequest workRequest, boolean creating) {
        if (request.deadline().isBefore(workRequest.getStartDate()) || request.deadline().isAfter(workRequest.getDeadline()))
            throw new BussinessException("Task deadline must be within work request dates");
        LocalDate startDate = request.startDate() != null ? request.startDate()
            : task.getStartDate() != null ? task.getStartDate() : workRequest.getStartDate();
        if (startDate.isBefore(workRequest.getStartDate()) || startDate.isAfter(workRequest.getDeadline()) || startDate.isAfter(request.deadline()))
            throw new BussinessException("Task start date must be within work request dates and not after its deadline");
        TaskPriority priority = request.priority() != null ? request.priority()
            : task.getPriority() != null ? task.getPriority() : TaskPriority.NORMAL;
        // Status changes must go through updateStatus so workflow and role checks cannot be bypassed by PUT.
        TaskStatus status = creating ? TaskStatus.BACKLOG : task.getStatus();
        String blockedReason = request.blockedReason() != null ? request.blockedReason() : task.getBlockedReason();
        if (status == TaskStatus.BLOCKED && (blockedReason == null || blockedReason.isBlank()))
            throw new BussinessException("A reason is required when blocking a task");
        task.setAssignedTo(validateAssignee(request.assignedToId(), workRequest));
        User tester = validateTester(request.testerId(), project);
        task.setTester(tester);
        task.setTaskName(request.taskName()); task.setDescription(request.description()); task.setProject(project); task.setWorkRequest(workRequest);
        task.setDeadline(request.deadline()); task.setStartDate(startDate); task.setPriority(priority);
        task.setEstimatedHours(request.estimatedHours()); task.setAcceptanceCriteria(request.acceptanceCriteria());
        task.setStatus(status); task.setBlockedReason(status == TaskStatus.BLOCKED ? blockedReason.trim() : null);
        if (task.getCreatedBy() == null) task.setCreatedBy(users.currentUser());
        return task;
    }

    private User validateAssignee(Long userId, WorkRequest request) {
        if (userId == null) return null;
        User user = users.findEntity(userId);
        if (user.getDepartment() == null || !user.getDepartment().getId().equals(request.getExecutingDepartment().getId()))
            throw new BussinessException("Task assignee must belong to the executing department");
        return user;
    }

    private User validateTester(Long userId, Project project) {
        if (userId == null) return null;
        User user = users.findEntity(userId);
        if (user.getRole() != Role.QA) throw new BussinessException("Tester must have the QA role");
        if (user.getDepartment() == null || !projectDepartments.existsByProjectIdAndDepartmentId(project.getId(), user.getDepartment().getId()))
            throw new BussinessException("Tester must belong to a participating project department");
        return user;
    }

    private void verifyCanCreate(Project project, Long assigneeId) {
        User current = users.currentUser();
        if (current.getRole().isReadOnly() || current.getRole() == Role.HR)
            throw new BussinessException("Your role cannot create or edit tasks");
        if (current.getRole() == Role.ADMIN) return;
        if (current.getDepartment() == null || !projectDepartments.existsByProjectIdAndDepartmentId(project.getId(), current.getDepartment().getId()))
            throw new BussinessException("You can only manage tasks in participating projects");
        if (!current.getRole().canManageTasks() && assigneeId != null && !assigneeId.equals(current.getId()))
            throw new BussinessException("Members and QA can only assign a new task to themselves");
    }

    private void verifyCanEdit(Task task) {
        User current = users.currentUser();
        boolean participant = task.getAssignedTo() != null && task.getAssignedTo().getId().equals(current.getId())
            || task.getTester() != null && task.getTester().getId().equals(current.getId())
            || task.getCreatedBy() != null && task.getCreatedBy().getId().equals(current.getId());
        if (current.getRole() == Role.ADMIN) return;
        boolean inProject = current.getDepartment() != null
            && projectDepartments.existsByProjectIdAndDepartmentId(task.getProject().getId(), current.getDepartment().getId());
        if (!inProject || !current.getRole().canManageTasks() && !participant)
            throw new BussinessException("You are not allowed to edit this task");
    }

    private void verifyCanView(Project project) {
        User current = users.currentUser();
        if (current.getRole() == Role.ADMIN || current.getRole() == Role.HR) return;
        if (current.getDepartment() == null || !projectDepartments.existsByProjectIdAndDepartmentId(project.getId(), current.getDepartment().getId()))
            throw new BussinessException("You cannot view tasks in this project");
    }

    private void verifyTransitionRole(Task task, User current, TaskStatus from, TaskStatus to) {
        boolean manager = current.getRole() == Role.ADMIN
            || current.getRole().canManageTasks() && current.getDepartment() != null
                && projectDepartments.existsByProjectIdAndDepartmentId(task.getProject().getId(), current.getDepartment().getId());
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

    private WorkRequest resolveWorkRequest(TaskDto.SaveRequest request) {
        WorkRequest workRequest;
        if (request.workRequestId() != null) {
            workRequest = workRequest(request.workRequestId());
        } else if (request.projectId() != null) {
            workRequest = workRequests.findFirstByProjectIdOrderByIdAsc(request.projectId())
                .orElseThrow(() -> new BussinessException("Project has no work request; create one before adding tasks"));
        } else {
            throw new BussinessException("Work request is required");
        }
        if (request.projectId() != null && !request.projectId().equals(workRequest.getProject().getId())) {
            throw new BussinessException("Task project must match the work request project");
        }
        if (workRequest.getStatus() == WorkRequestStatus.COMPLETED || workRequest.getStatus() == WorkRequestStatus.CANCELLED) {
            throw new BussinessException("Tasks cannot be changed in a closed work request");
        }
        return workRequest;
    }

    private void synchronizeWorkRequest(WorkRequest request) {
        if (request == null || request.getStatus() == WorkRequestStatus.COMPLETED
                || request.getStatus() == WorkRequestStatus.CANCELLED) return;
        List<Task> requestTasks = tasks.findByWorkRequestIdOrderByDeadlineAsc(request.getId());
        boolean anyStarted = requestTasks.stream().anyMatch(task -> task.getStatus() != TaskStatus.BACKLOG && task.getStatus() != TaskStatus.TODO);
        boolean allClosed = !requestTasks.isEmpty() && requestTasks.stream()
            .allMatch(task -> task.getStatus() == TaskStatus.DONE || task.getStatus() == TaskStatus.CANCELLED);
        if (allClosed) request.setStatus(WorkRequestStatus.PENDING_ACCEPTANCE);
        else if (anyStarted && (request.getStatus() == WorkRequestStatus.READY || request.getStatus() == WorkRequestStatus.DRAFT))
            request.setStatus(WorkRequestStatus.IN_PROGRESS);
    }

    private Project project(Long id) { return projects.findById(Objects.requireNonNull(id))
        .orElseThrow(() -> new BussinessException("Project not found: " + id)); }
    private WorkRequest workRequest(Long id) { return workRequests.findById(Objects.requireNonNull(id))
        .orElseThrow(() -> new BussinessException("Work request not found: " + id)); }
    private Task task(Long id) { return tasks.findById(Objects.requireNonNull(id))
        .orElseThrow(() -> new BussinessException("Task not found: " + id)); }
    private TaskDto.Response response(Task t) { return new TaskDto.Response(t.getId(), t.getTaskName(), t.getDescription(),
        t.getProject().getId(), t.getProject().getProjectName(),
        t.getWorkRequest() == null ? null : t.getWorkRequest().getId(),
        t.getWorkRequest() == null ? null : t.getWorkRequest().getRequestCode(),
        t.getWorkRequest() == null ? null : t.getWorkRequest().getTitle(),
        t.getWorkRequest() == null ? null : t.getWorkRequest().getExecutingDepartment().getId(),
        t.getWorkRequest() == null ? null : t.getWorkRequest().getExecutingDepartment().getName(),
        t.getAssignedTo() == null ? null : t.getAssignedTo().getId(),
        t.getAssignedTo() == null ? null : t.getAssignedTo().getFullName(), t.getTester() == null ? null : t.getTester().getId(),
        t.getTester() == null ? null : t.getTester().getFullName(), t.getCreatedBy() == null ? null : t.getCreatedBy().getId(),
        t.getStatus(), t.getStartDate(), t.getDeadline(), t.getPriority(), t.getEstimatedHours(),
        t.getAcceptanceCriteria(), t.getBlockedReason()); }
}
