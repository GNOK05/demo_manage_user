package com.example.demo.service.impl;

import com.example.demo.dto.WorkRequestDto;
import com.example.demo.entity.*;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.*;
import com.example.demo.service.UserService;
import com.example.demo.service.WorkRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.text.Normalizer;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class WorkRequestServiceImpl implements WorkRequestService {
    private static final Map<WorkRequestStatus, EnumSet<WorkRequestStatus>> TRANSITIONS = Map.ofEntries(
        Map.entry(WorkRequestStatus.DRAFT, EnumSet.of(WorkRequestStatus.ANALYSIS, WorkRequestStatus.READY, WorkRequestStatus.CANCELLED)),
        Map.entry(WorkRequestStatus.ANALYSIS, EnumSet.of(WorkRequestStatus.READY, WorkRequestStatus.PAUSED, WorkRequestStatus.CANCELLED)),
        Map.entry(WorkRequestStatus.READY, EnumSet.of(WorkRequestStatus.DEVELOPMENT, WorkRequestStatus.IN_PROGRESS, WorkRequestStatus.ANALYSIS, WorkRequestStatus.PAUSED, WorkRequestStatus.CANCELLED)),
        Map.entry(WorkRequestStatus.IN_PROGRESS, EnumSet.of(WorkRequestStatus.TESTING, WorkRequestStatus.PENDING_ACCEPTANCE, WorkRequestStatus.PAUSED, WorkRequestStatus.CANCELLED)),
        Map.entry(WorkRequestStatus.DEVELOPMENT, EnumSet.of(WorkRequestStatus.TESTING, WorkRequestStatus.PAUSED, WorkRequestStatus.CANCELLED)),
        Map.entry(WorkRequestStatus.TESTING, EnumSet.of(WorkRequestStatus.PENDING_ACCEPTANCE, WorkRequestStatus.DEVELOPMENT, WorkRequestStatus.PAUSED, WorkRequestStatus.CANCELLED)),
        Map.entry(WorkRequestStatus.PENDING_ACCEPTANCE, EnumSet.of(WorkRequestStatus.COMPLETED, WorkRequestStatus.CHANGES_REQUESTED)),
        Map.entry(WorkRequestStatus.CHANGES_REQUESTED, EnumSet.of(WorkRequestStatus.ANALYSIS, WorkRequestStatus.DEVELOPMENT, WorkRequestStatus.TESTING, WorkRequestStatus.PAUSED)),
        Map.entry(WorkRequestStatus.PAUSED, EnumSet.of(WorkRequestStatus.ANALYSIS, WorkRequestStatus.DEVELOPMENT, WorkRequestStatus.TESTING, WorkRequestStatus.CANCELLED)),
        Map.entry(WorkRequestStatus.COMPLETED, EnumSet.noneOf(WorkRequestStatus.class)),
        Map.entry(WorkRequestStatus.CANCELLED, EnumSet.noneOf(WorkRequestStatus.class))
    );

    private final WorkRequestRepository requests;
    private final WorkRequestHistoryRepository history;
    private final ProjectRepository projects;
    private final ProjectDepartmentRepository projectDepartments;
    private final DepartmentRepository departments;
    private final TaskRepository tasks;
    private final UserService users;

    @Override
    @Transactional(readOnly = true)
    public List<WorkRequestDto.Response> search(String keyword, Long projectId, Long departmentId,
            WorkRequestStatus status, TaskPriority priority, WorkRequestHealth health, boolean overdue) {
        String query = searchable(keyword);
        LocalDate today = today();
        return requests.findAll().stream()
            .filter(this::canView)
            .filter(item -> projectId == null || item.getProject().getId().equals(projectId))
            .filter(item -> departmentId == null || item.getExecutingDepartment().getId().equals(departmentId))
            .filter(item -> status == null || item.getStatus() == status)
            .filter(item -> priority == null || item.getPriority() == priority)
            .filter(item -> !overdue || item.getDeadline().isBefore(today)
                && item.getStatus() != WorkRequestStatus.COMPLETED && item.getStatus() != WorkRequestStatus.CANCELLED)
            .filter(item -> query.isBlank() || searchable(item.getRequestCode()).contains(query)
                || searchable(item.getTitle()).contains(query)
                || searchable(item.getProject().getProjectName()).contains(query)
                || searchable(item.getExecutingDepartment().getName()).contains(query))
            .filter(item -> health == null || health(item) == health)
            .sorted(java.util.Comparator.comparing(WorkRequest::getDeadline))
            .map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkRequestDto.Response> byProject(Long projectId) {
        Project project = project(projectId);
        verifyCanViewProject(project);
        return requests.findByProjectIdOrderByDeadlineAsc(projectId).stream().filter(this::canView).map(this::response).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public WorkRequestDto.Response get(Long id) {
        WorkRequest item = entity(id);
        if (!canView(item)) throw new BussinessException("You cannot view this work request");
        return response(item);
    }

    @Override
    public WorkRequestDto.Response create(Long projectId, WorkRequestDto.SaveRequest request) {
        requirePortfolioManager();
        Project project = project(projectId);
        Department department = department(request.executingDepartmentId());
        verifyCanManage(project, department);
        verifyParticipant(project, department);
        WorkRequest item = new WorkRequest();
        item.setRequestCode(nextCode(project));
        item.setProject(project);
        item.setCreatedBy(users.currentUser());
        WorkRequest saved = requests.save(build(item, request, project, department));
        recordHistory(saved, null, WorkRequestStatus.DRAFT, null);
        return response(saved);
    }

    @Override
    public WorkRequestDto.Response update(Long id, WorkRequestDto.SaveRequest request) {
        requirePortfolioManager();
        WorkRequest item = entity(id);
        Department department = department(request.executingDepartmentId());
        verifyCanManage(item.getProject(), item.getExecutingDepartment());
        verifyParticipant(item.getProject(), department);
        return response(build(item, request, item.getProject(), department));
    }

    @Override
    public WorkRequestDto.Response updateStatus(Long id, WorkRequestDto.StatusRequest request) {
        WorkRequest item = entity(id);
        verifyCanManage(item.getProject(), item.getExecutingDepartment());
        if (users.currentUser().getRole() == Role.TEAM_LEAD && (item.getCoordinator() == null
                || !item.getCoordinator().getId().equals(users.currentUser().getId())))
            throw new BussinessException("Only the assigned team lead can advance this work request");
        WorkRequestStatus from = item.getStatus();
        WorkRequestStatus to = request.status();
        Role role = users.currentUser().getRole();
        if (role != Role.ADMIN && role != Role.PORTFOLIO_MANAGER && role != Role.TEAM_LEAD)
            throw new BussinessException("This role cannot change work request stages");
        if ((to == WorkRequestStatus.COMPLETED || to == WorkRequestStatus.CHANGES_REQUESTED
                || to == WorkRequestStatus.CANCELLED) && role != Role.ADMIN && role != Role.PORTFOLIO_MANAGER)
            throw new BussinessException("Only portfolio managers can accept, reject or cancel a work request");
        if ((to == WorkRequestStatus.PENDING_ACCEPTANCE || to == WorkRequestStatus.TESTING
                || to == WorkRequestStatus.DEVELOPMENT || to == WorkRequestStatus.READY)
                && role != Role.ADMIN && role != Role.TEAM_LEAD)
            throw new BussinessException("Only team leads can advance delivery stages");
        if (to == WorkRequestStatus.CHANGES_REQUESTED && (request.reason() == null || request.reason().isBlank()))
            throw new BussinessException("A reason is required when requesting changes");
        if (from == to) return response(item);
        if (!TRANSITIONS.getOrDefault(from, EnumSet.noneOf(WorkRequestStatus.class)).contains(to)) {
            throw new BussinessException("Invalid work request transition: " + from + " -> " + to);
        }
        if (to == WorkRequestStatus.PENDING_ACCEPTANCE && (tasks.findByWorkRequestIdOrderByDeadlineAsc(item.getId()).stream()
                .noneMatch(task -> task.getStatus() == TaskStatus.DONE) || hasOpenTasks(item.getId()))) {
            throw new BussinessException("All required tasks must be completed before acceptance");
        }
        if (to == WorkRequestStatus.COMPLETED && from != WorkRequestStatus.PENDING_ACCEPTANCE) {
            throw new BussinessException("Work request must await acceptance before completion");
        }
        item.setStatus(to);
        recordHistory(item, from, to, request.reason());
        return response(item);
    }

    @Override @Transactional(readOnly = true)
    public List<WorkRequestDto.HistoryResponse> history(Long id) {
        get(id);
        return history.findByWorkRequestIdOrderByChangedAtDesc(id).stream()
            .map(h -> new WorkRequestDto.HistoryResponse(h.getFromStatus(), h.getToStatus(),
                h.getReason(), h.getActor().getFullName(), h.getChangedAt())).toList();
    }

    private void recordHistory(WorkRequest item, WorkRequestStatus from, WorkRequestStatus to, String reason) {
        WorkRequestHistory entry = new WorkRequestHistory();
        entry.setWorkRequest(item); entry.setActor(users.currentUser());
        entry.setFromStatus(from); entry.setToStatus(to); entry.setReason(reason);
        history.save(entry);
    }

    @Override
    public void delete(Long id) {
        requirePortfolioManager();
        WorkRequest item = entity(id);
        verifyCanManage(item.getProject(), item.getExecutingDepartment());
        if (tasks.countByWorkRequestId(id) > 0) throw new BussinessException("Cannot delete a work request that contains tasks");
        history.deleteAll(history.findByWorkRequestIdOrderByChangedAtDesc(id));
        requests.delete(item);
    }

    private WorkRequest build(WorkRequest item, WorkRequestDto.SaveRequest request, Project project, Department department) {
        if (request.deadline().isBefore(request.startDate())) throw new BussinessException("Deadline must not precede start date");
        if (request.startDate().isBefore(project.getStartDate()) || request.deadline().isAfter(project.getEndDate())) {
            throw new BussinessException("Work request dates must be within project dates");
        }
        User coordinator = request.coordinatorId() == null ? null : users.findEntity(request.coordinatorId());
        if (coordinator == null || coordinator.getRole() != Role.TEAM_LEAD)
            throw new BussinessException("A team lead must be assigned to this work request");
        if (coordinator != null && (coordinator.getDepartment() == null
                || !coordinator.getDepartment().getId().equals(department.getId()))) {
            throw new BussinessException("Coordinator must belong to the executing department");
        }
        item.setExecutingDepartment(department);
        item.setCoordinator(coordinator);
        item.setTitle(request.title().trim());
        item.setDescription(request.description());
        item.setAcceptanceCriteria(request.acceptanceCriteria());
        item.setPriority(request.priority() == null ? TaskPriority.NORMAL : request.priority());
        item.setStartDate(request.startDate());
        item.setDeadline(request.deadline());
        return item;
    }

    private boolean canView(WorkRequest request) {
        User current = users.currentUser();
        if (current.getRole() == Role.ADMIN || current.getRole() == Role.HR || current.getRole() == Role.PORTFOLIO_MANAGER || current.getRole() == Role.PROJECT_MANAGER) return true;
        if (current.getRole() == Role.TEAM_LEAD || current.getRole() == Role.VIEWER) {
            return current.getDepartment() != null
                && request.getExecutingDepartment().getId().equals(current.getDepartment().getId());
        }
        return tasks.findByWorkRequestIdOrderByDeadlineAsc(request.getId()).stream().anyMatch(task ->
            task.getAssignedTo() != null && task.getAssignedTo().getId().equals(current.getId())
                || task.getTester() != null && task.getTester().getId().equals(current.getId())
                || task.getCreatedBy() != null && task.getCreatedBy().getId().equals(current.getId()));
    }

    private void verifyCanViewProject(Project project) {
        User current = users.currentUser();
        if (current.getRole() == Role.ADMIN || current.getRole() == Role.HR || current.getRole() == Role.PORTFOLIO_MANAGER || current.getRole() == Role.PROJECT_MANAGER) return;
        if (current.getDepartment() == null
                || !projectDepartments.existsByProjectIdAndDepartmentId(project.getId(), current.getDepartment().getId())) {
            throw new BussinessException("You cannot view this project");
        }
    }

    private void verifyCanManage(Project project, Department executingDepartment) {
        User current = users.currentUser();
        if (current.getRole() == Role.ADMIN || current.getRole() == Role.PORTFOLIO_MANAGER) return;
        if (current.getDepartment() == null) throw new BussinessException("A department is required");
        boolean participant = projectDepartments.existsByProjectIdAndDepartmentId(project.getId(), current.getDepartment().getId());
        boolean manager = current.getRole() == Role.PROJECT_MANAGER && participant;
        boolean lead = current.getRole() == Role.TEAM_LEAD
            && current.getDepartment().getId().equals(executingDepartment.getId());
        if (!manager && !lead) throw new BussinessException("You cannot manage this work request");
    }

    private void verifyParticipant(Project project, Department department) {
        if (!projectDepartments.existsByProjectIdAndDepartmentId(project.getId(), department.getId())) {
            throw new BussinessException("Executing department must participate in the project");
        }
    }

    private boolean hasOpenTasks(Long requestId) {
        return tasks.findByWorkRequestIdOrderByDeadlineAsc(requestId).stream()
            .anyMatch(task -> task.getStatus() != TaskStatus.DONE && task.getStatus() != TaskStatus.CANCELLED);
    }

    private WorkRequestHealth health(WorkRequest item) {
        List<Task> requestTasks = tasks.findByWorkRequestIdOrderByDeadlineAsc(item.getId());
        if (requestTasks.stream().anyMatch(task -> task.getStatus() == TaskStatus.BLOCKED)) return WorkRequestHealth.BLOCKED;
        if (item.getStatus() != WorkRequestStatus.COMPLETED && item.getStatus() != WorkRequestStatus.CANCELLED) {
            long days = java.time.temporal.ChronoUnit.DAYS.between(today(), item.getDeadline());
            if (days < 0) return WorkRequestHealth.OVERDUE;
            int progress = progress(requestTasks);
            if (days <= 3 || days <= 7 && progress < 70) return WorkRequestHealth.AT_RISK;
        }
        return WorkRequestHealth.ON_TRACK;
    }

    private WorkRequestDto.Response response(WorkRequest item) {
        List<Task> requestTasks = tasks.findByWorkRequestIdOrderByDeadlineAsc(item.getId());
        long total = requestTasks.stream().filter(task -> task.getStatus() != TaskStatus.CANCELLED).count();
        long done = requestTasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE).count();
        long blocked = requestTasks.stream().filter(task -> task.getStatus() == TaskStatus.BLOCKED).count();
        long overdue = requestTasks.stream().filter(task -> task.getDeadline().isBefore(today())
            && task.getStatus() != TaskStatus.DONE && task.getStatus() != TaskStatus.CANCELLED).count();
        return new WorkRequestDto.Response(item.getId(), item.getRequestCode(), item.getProject().getId(), item.getProject().getProjectName(),
            item.getExecutingDepartment().getId(), item.getExecutingDepartment().getName(),
            item.getCoordinator() == null ? null : item.getCoordinator().getId(),
            item.getCoordinator() == null ? null : item.getCoordinator().getFullName(), item.getCreatedBy().getId(),
            item.getTitle(), item.getDescription(), item.getAcceptanceCriteria(), item.getPriority(), item.getStatus(), health(item),
            item.getStartDate(), item.getDeadline(), total, done, blocked, overdue, total == 0 ? 0 : (int) Math.round(done * 100.0 / total));
    }

    private int progress(List<Task> requestTasks) {
        long total = requestTasks.stream().filter(task -> task.getStatus() != TaskStatus.CANCELLED).count();
        long done = requestTasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE).count();
        return total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
    }

    private String nextCode(Project project) {
        String code;
        do {
            code = "REQ-" + project.getId() + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        } while (requests.existsByRequestCode(code));
        return code;
    }

    private LocalDate today() { return LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")); }
    private String searchable(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd').replace('Đ', 'D')
            .toLowerCase(Locale.ROOT).trim();
    }
    private Project project(Long id) { return projects.findById(id).orElseThrow(() -> new BussinessException("Project not found: " + id)); }
    private Department department(Long id) { return departments.findById(id).orElseThrow(() -> new BussinessException("Department not found: " + id)); }
    private WorkRequest entity(Long id) { return requests.findById(id).orElseThrow(() -> new BussinessException("Work request not found: " + id)); }
    private void requirePortfolioManager() {
        Role role = users.currentUser().getRole();
        if (role != Role.ADMIN && role != Role.PORTFOLIO_MANAGER)
            throw new BussinessException("Only portfolio managers can create or edit work requests");
    }
}
