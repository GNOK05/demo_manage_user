package com.example.demo.config;

import com.example.demo.entity.*;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Seeds richer demo data: multiple employees, multiple projects (in every status),
 * multiple tasks (in every status) and a multi-day attendance history (in every status)
 * per department. All creation is idempotent — restarting the app will not duplicate
 * records, since every entity is looked up by its natural key before being created.
 *
 * For a completely fresh dataset, stop the app, delete the ./data folder, then restart.
 */
@Configuration
@RequiredArgsConstructor
public class DataInitializer {
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final AttendanceRepository attendance;
    private final PasswordEncoder encoder;

    private static final String[] DEPARTMENT_NAMES = {
            "Engineering", "Human Resources", "Finance", "Marketing", "Sales",
            "Operations", "Customer Success", "Product", "Design", "Legal",
            "Procurement", "Quality", "Research", "Training", "Support"
    };

    /** Local bootstrap account; change or remove it before production deployment. */
    @Bean
    CommandLineRunner seedAdministrator() {
        return args -> {
            createIfMissing("admin", "admin123", "System Administrator", "admin@company.local", Role.ADMIN, null);

            Department it = findOrCreateDepartment("IT", "Information Technology", "Demo department");
            User itManager = createIfMissing("manager", "manager123", "IT Manager", "manager@company.local", Role.MANAGER, it);
            if (it.getManager() == null) { it.setManager(itManager); departments.save(it); }
            createIfMissing("employee", "employee123", "Demo Employee", "employee@company.local", Role.EMPLOYEE, it);

            seedDemoData();
        };
    }

    private void seedDemoData() {
        LocalDate today = LocalDate.now();

        for (int i = 0; i < DEPARTMENT_NAMES.length; i++) {
            int number = i + 1;
            String code = String.format("D%02d", number);
            String deptName = DEPARTMENT_NAMES[i];

            Department department = findOrCreateDepartment(code, deptName, "Demo " + deptName + " department");

            User manager = createIfMissing("lead" + number, "lead123", deptName + " Lead", "lead" + number + "@company.local", Role.MANAGER, department);
            if (department.getManager() == null) { department.setManager(manager); departments.save(department); }

            // 3 employees per department for realistic variety
            User staffA = createIfMissing("staff" + number, "staff123", "Demo Staff " + number + "A", "staff" + number + "a@company.local", Role.EMPLOYEE, department);
            User staffB = createIfMissing("staff" + number + "b", "staff123", "Demo Staff " + number + "B", "staff" + number + "b@company.local", Role.EMPLOYEE, department);
            User staffC = createIfMissing("staff" + number + "c", "staff123", "Demo Staff " + number + "C", "staff" + number + "c@company.local", Role.EMPLOYEE, department);
            List<User> employees = List.of(staffA, staffB, staffC);

            // 3 projects per department, one in each status
            Project notStarted = findOrCreateProject(department, deptName + " Revamp " + number,
                    "Upcoming initiative for " + deptName, today.plusDays(10), today.plusDays(70), ProjectStatus.NOT_STARTED);
            Project inProgress = findOrCreateProject(department, deptName + " Improvement " + number,
                    "Seeded demo project for " + deptName, today.minusDays(20), today.plusDays(40), ProjectStatus.IN_PROGRESS);
            Project completed = findOrCreateProject(department, deptName + " Rollout " + number,
                    "Wrapped-up project for " + deptName, today.minusDays(90), today.minusDays(10), ProjectStatus.COMPLETED);

            // NOT_STARTED project: tasks not begun yet
            findOrCreateTask(notStarted, "Kickoff planning", employees.get(0), manager, TaskStatus.TODO, today.plusDays(15));
            findOrCreateTask(notStarted, "Requirements gathering", employees.get(1), manager, TaskStatus.TODO, today.plusDays(20));

            // IN_PROGRESS project: tasks spread across the workflow
            findOrCreateTask(inProgress, "Design phase", employees.get(0), manager, TaskStatus.DONE, today.minusDays(5));
            findOrCreateTask(inProgress, "Implementation", employees.get(1), manager, TaskStatus.IN_PROGRESS, today.plusDays(5));
            findOrCreateTask(inProgress, "Peer review", employees.get(2), manager, TaskStatus.REVIEW, today.plusDays(8));
            findOrCreateTask(inProgress, "Backlog cleanup", null, manager, TaskStatus.TODO, today.plusDays(15));

            // COMPLETED project: everything finished
            findOrCreateTask(completed, "Final delivery", employees.get(0), manager, TaskStatus.DONE, today.minusDays(12));
            findOrCreateTask(completed, "Handover documentation", employees.get(1), manager, TaskStatus.DONE, today.minusDays(11));

            // Attendance history: last 7 days per employee, varied statuses
            for (User employee : employees) {
                seedAttendanceHistory(employee, today, employees.indexOf(employee));
            }
        }
    }

    private void seedAttendanceHistory(User employee, LocalDate today, int variant) {
        AttendanceStatus[] pattern = {
                AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.LATE,
                AttendanceStatus.PRESENT, AttendanceStatus.ABSENT, AttendanceStatus.PRESENT, AttendanceStatus.LEAVE
        };
        for (int d = 0; d < 7; d++) {
            LocalDate date = today.minusDays(d);
            AttendanceStatus status = pattern[(d + variant) % pattern.length];
            findOrCreateAttendance(employee, date, status);
        }
    }

    private Department findOrCreateDepartment(String code, String name, String description) {
        return departments.findAll().stream().filter(d -> code.equals(d.getCode())).findFirst().orElseGet(() -> {
            Department d = new Department();
            d.setName(name); d.setCode(code); d.setDescription(description);
            return departments.save(d);
        });
    }

    private Project findOrCreateProject(Department department, String name, String description,
                                        LocalDate start, LocalDate end, ProjectStatus status) {
        return projects.findByDepartmentId(department.getId()).stream()
                .filter(p -> name.equals(p.getProjectName())).findFirst().orElseGet(() -> {
                    Project p = new Project();
                    p.setProjectName(name); p.setDescription(description); p.setDepartment(department);
                    p.setStartDate(start); p.setEndDate(end); p.setStatus(status);
                    return projects.save(p);
                });
    }

    private Task findOrCreateTask(Project project, String name, User assignee, User creator,
                                  TaskStatus status, LocalDate deadline) {
        return tasks.findByProjectId(project.getId()).stream()
                .filter(t -> name.equals(t.getTaskName())).findFirst().orElseGet(() -> {
                    Task t = new Task();
                    t.setTaskName(name); t.setDescription("Demo task for " + project.getProjectName());
                    t.setProject(project); t.setAssignedTo(assignee); t.setCreatedBy(creator);
                    t.setStatus(status); t.setDeadline(deadline);
                    return tasks.save(t);
                });
    }

    private void findOrCreateAttendance(User employee, LocalDate date, AttendanceStatus status) {
        if (attendance.findByUserIdAndDate(employee.getId(), date).isPresent()) return;
        Attendance record = new Attendance();
        record.setUser(employee);
        record.setDate(date);
        record.setStatus(status);
        if (status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE) {
            LocalDateTime checkIn = status == AttendanceStatus.LATE ? date.atTime(9, 25) : date.atTime(8, 30);
            record.setCheckInTime(checkIn);
            record.setCheckOutTime(date.atTime(17, 30));
        }
        // ABSENT and LEAVE intentionally have no check-in/check-out times
        attendance.save(record);
    }

    private User createIfMissing(String username, String rawPassword, String fullName, String email, Role role, Department department) {
        return users.findByUsername(username).orElseGet(() -> {
            User user = new User(); user.setUsername(username); user.setPassword(encoder.encode(rawPassword));
            user.setFullName(fullName); user.setEmail(email); user.setRole(role); user.setDepartment(department);
            return users.save(user);
        });
    }
}