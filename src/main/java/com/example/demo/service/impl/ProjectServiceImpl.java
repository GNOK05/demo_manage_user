package com.example.demo.service.impl;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.dto.ProjectDto;
import com.example.demo.entity.Department;
import com.example.demo.entity.Project;
import com.example.demo.entity.ProjectDepartment;
import com.example.demo.entity.ProjectParticipationRole;
import com.example.demo.entity.ProjectStatus;
import com.example.demo.entity.Role;
import com.example.demo.entity.TaskStatus;
import com.example.demo.entity.User;
import com.example.demo.entity.WorkRequestStatus;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.ProjectDepartmentRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.WorkRequestRepository;
import com.example.demo.service.ProjectService;
import com.example.demo.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectServiceImpl implements ProjectService {
    private final ProjectRepository projects;
    private final DepartmentRepository departments;
    private final TaskRepository tasks;
    private final ProjectDepartmentRepository projectDepartments;
    private final WorkRequestRepository workRequests;
    private final UserService users;

    public List<ProjectDto.Response> findAccessible(String keyword, Long departmentId, ProjectStatus status) {
        User u = users.currentUser();
        String query = searchable(keyword);
        List<Project> result = switch (u.getRole()) {
            case ADMIN, HR, PROJECT_MANAGER -> projects.findAll();
            case MEMBER, QA -> projects.findAssignedToUser(u.getId());
            case TEAM_LEAD, VIEWER -> u.getDepartment() == null ? List.of()
                : projects.findByParticipantDepartmentId(u.getDepartment().getId());
        };
        return result.stream()
            .filter(p -> departmentId == null || projectDepartments.existsByProjectIdAndDepartmentId(p.getId(), departmentId))
            .filter(p -> status == null || p.getStatus() == status)
            .filter(p -> query.isBlank() || searchable(p.getProjectName()).contains(query)
                || searchable(p.getDescription()).contains(query)
                || searchable(p.getDepartment().getName()).contains(query)
                || projectDepartments.findByProjectIdOrderByParticipationRoleAscDepartmentNameAsc(p.getId()).stream()
                    .anyMatch(item -> searchable(item.getDepartment().getName()).contains(query)))
            .map(this::response).toList();
    }

    public ProjectDto.Response get(Long id) {
        Project p = entity(id);
        User u = users.currentUser();
        boolean companyScope = u.getRole() == Role.ADMIN || u.getRole() == Role.HR || u.getRole() == Role.PROJECT_MANAGER;
        boolean personalScope = (u.getRole() == Role.MEMBER || u.getRole() == Role.QA)
            && projects.findAssignedToUser(u.getId()).stream().anyMatch(item -> item.getId().equals(id));
        boolean departmentScope = u.getDepartment() != null
            && projectDepartments.existsByProjectIdAndDepartmentId(id, u.getDepartment().getId());
        if (!companyScope && !personalScope && !departmentScope)
            throw new BussinessException("You cannot view this project");
        return response(p);
    }

    public ProjectDto.Response save(ProjectDto.SaveRequest r) {
        Department d = department(r.departmentId());
        verifyManage(d);
        Project project = projects.save(build(new Project(), r, d));
        ensureOwnerMembership(project, d);
        return response(project);
    }

    public ProjectDto.Response update(Long id, ProjectDto.SaveRequest r) {
        Project p = entity(id);
        verifyManage(p.getDepartment());
        Department d = department(r.departmentId());
        verifyManage(d);
        Project updated = build(p, r, d);
        ensureOwnerMembership(updated, d);
        return response(updated);
    }

    public List<ProjectDto.DepartmentRef> departments(Long id) {
        Project project = entity(id);
        get(id);
        return departmentRefs(project);
    }

    public List<ProjectDto.DepartmentRef> addDepartment(Long id, Long departmentId) {
        Project project = entity(id);
        verifyManage(project.getDepartment());
        Department department = department(departmentId);
        if (!projectDepartments.existsByProjectIdAndDepartmentId(id, departmentId)) {
            ProjectDepartment membership = new ProjectDepartment();
            membership.setProject(project);
            membership.setDepartment(department);
            membership.setParticipationRole(ProjectParticipationRole.PARTICIPANT);
            projectDepartments.save(membership);
        }
        return departmentRefs(project);
    }

    public List<ProjectDto.DepartmentRef> removeDepartment(Long id, Long departmentId) {
        Project project = entity(id);
        verifyManage(project.getDepartment());
        if (project.getDepartment().getId().equals(departmentId)) throw new BussinessException("Owner department cannot be removed");
        if (workRequests.existsByProjectIdAndExecutingDepartmentId(id, departmentId)) {
            throw new BussinessException("Department still owns work requests in this project");
        }
        projectDepartments.deleteByProjectIdAndDepartmentId(id, departmentId);
        return departmentRefs(project);
    }

    public void delete(Long id) {
        Project p = entity(id);
        verifyManage(p.getDepartment());
        projects.delete(p);
    }

    private Project entity(Long id) {
        return projects.findById(id).orElseThrow(() -> new BussinessException("Project not found: " + id));
    }

    private Department department(Long id) {
        return departments.findById(id).orElseThrow(() -> new BussinessException("Department not found: " + id));
    }

    private Project build(Project p, ProjectDto.SaveRequest r, Department d) {
        if (r.endDate().isBefore(r.startDate())) throw new BussinessException("End date must not precede start date");
        p.setProjectName(r.projectName());
        p.setDescription(r.description());
        p.setDepartment(d);
        p.setStartDate(r.startDate());
        p.setEndDate(r.endDate());
        p.setStatus(r.status() == null ? ProjectStatus.NOT_STARTED : r.status());
        return p;
    }

    private void ensureOwnerMembership(Project project, Department department) {
        boolean ownerExists = false;
        var memberships = projectDepartments.findByProjectIdOrderByParticipationRoleAscDepartmentNameAsc(project.getId());
        for (ProjectDepartment membership : memberships) {
            boolean isOwner = membership.getDepartment().getId().equals(department.getId());
            membership.setParticipationRole(isOwner ? ProjectParticipationRole.OWNER : ProjectParticipationRole.PARTICIPANT);
            ownerExists = ownerExists || isOwner;
        }
        projectDepartments.saveAll(memberships);
        if (ownerExists) return;
        ProjectDepartment membership = new ProjectDepartment();
        membership.setProject(project);
        membership.setDepartment(department);
        membership.setParticipationRole(ProjectParticipationRole.OWNER);
        projectDepartments.save(membership);
    }

    private List<ProjectDto.DepartmentRef> departmentRefs(Project project) {
        return projectDepartments.findByProjectIdOrderByParticipationRoleAscDepartmentNameAsc(project.getId()).stream()
            .map(item -> new ProjectDto.DepartmentRef(item.getDepartment().getId(), item.getDepartment().getName(),
                item.getDepartment().getCode(), item.getParticipationRole().name())).toList();
    }

    private void verifyManage(Department d) {
        User u = users.currentUser();
        if (u.getRole() == Role.ADMIN) return;
        if (u.getRole() != Role.PROJECT_MANAGER || u.getDepartment() == null || !u.getDepartment().getId().equals(d.getId()))
            throw new BussinessException("Only project managers can manage projects in their department");
    }

    private ProjectDto.Response response(Project p) {
        User current = users.currentUser();
        var visibleTasks = tasks.findByProjectId(p.getId()).stream().filter(task -> canViewTask(task, current)).toList();
        long all = visibleTasks.size(), done = visibleTasks.stream().filter(task -> task.getStatus() == TaskStatus.DONE).count();
        int progress = all == 0 ? 0 : (int) Math.round(done * 100.0 / all);
        var requestList = workRequests.findByProjectIdOrderByDeadlineAsc(p.getId()).stream()
            .filter(request -> canViewRequest(request, current, visibleTasks)).toList();
        long completedRequests = requestList.stream().filter(item -> item.getStatus() == WorkRequestStatus.COMPLETED).count();
        return new ProjectDto.Response(p.getId(), p.getProjectName(), p.getDescription(), p.getDepartment().getId(), p.getDepartment().getName(),
            p.getStartDate(), p.getEndDate(), p.getStatus(), all, done, progress, requestList.size(), completedRequests, departmentRefs(p));
    }

    private String searchable(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replaceAll("\\p{M}+", "")
            .replace('đ', 'd').replace('Đ', 'D')
            .toLowerCase(Locale.ROOT).trim();
    }

    private boolean canViewTask(com.example.demo.entity.Task task, User current) {
        if (current.getRole() == Role.ADMIN || current.getRole() == Role.HR || current.getRole() == Role.PROJECT_MANAGER) return true;
        if (current.getRole() == Role.MEMBER || current.getRole() == Role.QA) {
            return task.getAssignedTo() != null && task.getAssignedTo().getId().equals(current.getId())
                || task.getTester() != null && task.getTester().getId().equals(current.getId())
                || task.getCreatedBy() != null && task.getCreatedBy().getId().equals(current.getId());
        }
        return current.getDepartment() != null && task.getWorkRequest() != null
            && task.getWorkRequest().getExecutingDepartment().getId().equals(current.getDepartment().getId());
    }

    private boolean canViewRequest(com.example.demo.entity.WorkRequest request, User current,
            List<com.example.demo.entity.Task> visibleTasks) {
        if (current.getRole() == Role.ADMIN || current.getRole() == Role.HR || current.getRole() == Role.PROJECT_MANAGER) return true;
        if (current.getRole() == Role.TEAM_LEAD || current.getRole() == Role.VIEWER) {
            return current.getDepartment() != null
                && request.getExecutingDepartment().getId().equals(current.getDepartment().getId());
        }
        return visibleTasks.stream().anyMatch(task -> task.getWorkRequest() != null
            && task.getWorkRequest().getId().equals(request.getId()));
    }
}
