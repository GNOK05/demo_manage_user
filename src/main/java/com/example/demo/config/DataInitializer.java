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
 * Seeds demo data: one PO (manager) + four role-specific staff (DEV/QA/QC/TEST) per department,
 * three projects per department covering every {@link ProjectStatus}, tasks covering every
 * {@link TaskStatus} (with a tester assigned on some of them), and a 7-day attendance history
 * covering every {@link AttendanceStatus}.
 *
 * Every entity is looked up by its natural key before being created, so restarting the app is
 * safe and will not duplicate records. For a completely fresh dataset, stop the app, delete the
 * ./data folder, then restart.
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
            createIfMissing("admin", "admin123", "System Administrator", "admin@company.local", Role.ADMIN, null, "Administrator");
            removeLegacyDemoAccounts();
            seedDemoData();
        };
    }

    /** Drops the old generic manager/employee (IT) demo accounts — redundant with lead1/staff1 style accounts. */
    private void removeLegacyDemoAccounts() {
        departments.findAll().stream().filter(d -> "IT".equals(d.getCode())).findFirst().ifPresent(it -> {
            if (it.getManager() != null) { it.setManager(null); departments.save(it); }
            users.findByUsername("manager").ifPresent(users::delete);
            users.findByUsername("employee").ifPresent(users::delete);
            departments.delete(it);
        });
    }

    private void seedDemoData() {
        LocalDate today = LocalDate.now();

        for (int i = 0; i < DEPARTMENT_NAMES.length; i++) {
            int number = i + 1;
            String code = String.format("D%02d", number);
            String deptName = DEPARTMENT_NAMES[i];

            Department department = findOrCreateDepartment(code, deptName, "Demo " + deptName + " department");

            User po = createIfMissing("lead" + number, "lead123", deptName + " PO", "lead" + number + "@company.local", Role.MANAGER, department, "PO");
            if (department.getManager() == null) { department.setManager(po); departments.save(department); }

            User dev = createIfMissing("dev" + number, "dev123", deptName + " Developer", "dev" + number + "@company.local", Role.EMPLOYEE, department, "DEV");
            User qa = createIfMissing("qa" + number, "qa123", deptName + " QA", "qa" + number + "@company.local", Role.EMPLOYEE, department, "QA");
            User qc = createIfMissing("qc" + number, "qc123", deptName + " QC", "qc" + number + "@company.local", Role.EMPLOYEE, department, "QC");
            User tester = createIfMissing("test" + number, "test123", deptName + " Tester", "test" + number + "@company.local", Role.EMPLOYEE, department, "TEST");
            List<User> employees = List.of(dev, qa, qc, tester);

            // 3 projects per department, one in each status
            Project notStarted = findOrCreateProject(department, deptName + " Revamp " + number,
                "Upcoming initiative for " + deptName, today.plusDays(10), today.plusDays(70), ProjectStatus.NOT_STARTED);
            Project inProgress = findOrCreateProject(department, deptName + " Improvement " + number,
                "Seeded demo project for " + deptName, today.minusDays(20), today.plusDays(40), ProjectStatus.IN_PROGRESS);
            Project completed = findOrCreateProject(department, deptName + " Rollout " + number,
                "Wrapped-up project for " + deptName, today.minusDays(90), today.minusDays(10), ProjectStatus.COMPLETED);

            // NOT_STARTED project: tasks not begun yet, no tester needed
            findOrCreateTask(notStarted, "Kickoff planning", dev, null, po, TaskStatus.TODO, today.plusDays(15));
            findOrCreateTask(notStarted, "Requirements gathering", qa, null, po, TaskStatus.TODO, today.plusDays(20));

            // IN_PROGRESS project: tasks spread across the workflow, testers assigned once implementation exists
            findOrCreateTask(inProgress, "Design phase", dev, qa, po, TaskStatus.DONE, today.minusDays(5));
            findOrCreateTask(inProgress, "Implementation", dev, qc, po, TaskStatus.IN_PROGRESS, today.plusDays(5));
            findOrCreateTask(inProgress, "Peer review", qc, tester, po, TaskStatus.REVIEW, today.plusDays(8));
            findOrCreateTask(inProgress, "Backlog cleanup", null, null, po, TaskStatus.TODO, today.plusDays(15));

            // COMPLETED project: everything finished and verified
            findOrCreateTask(completed, "Final delivery", dev, tester, po, TaskStatus.DONE, today.minusDays(12));
            findOrCreateTask(completed, "Handover documentation", qa, qc, po, TaskStatus.DONE, today.minusDays(11));

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
            findOrCreateAttendance(employee, date, status, d == 0);
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

    private Task findOrCreateTask(Project project, String name, User assignee, User tester, User creator,
                                   TaskStatus status, LocalDate deadline) {
        return tasks.findByProjectId(project.getId()).stream()
            .filter(t -> name.equals(t.getTaskName())).findFirst().orElseGet(() -> {
                Task t = new Task();
                t.setTaskName(name); t.setDescription("Demo task for " + project.getProjectName());
                t.setProject(project); t.setAssignedTo(assignee); t.setTester(tester); t.setCreatedBy(creator);
                t.setStatus(status); t.setDeadline(deadline);
                return tasks.save(t);
            });
    }

    /**
     * Creates today's attendance record with real check-in/out timestamps so the "current status"
     * views (Chưa vào làm / Đang làm việc / Tạm vắng / Đã tan làm) have something to compute from;
     * past days only need a status for history display.
     */
    private void findOrCreateAttendance(User employee, LocalDate date, AttendanceStatus status, boolean isToday) {
        if (attendance.findByUserIdAndDate(employee.getId(), date).isPresent()) return;
        Attendance record = new Attendance();
        record.setUser(employee);
        record.setDate(date);
        record.setStatus(status);
        if (status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE) {
            LocalDateTime checkIn = status == AttendanceStatus.LATE ? date.atTime(9, 25) : date.atTime(8, 30);
            record.setCheckInTime(checkIn);
            // Leave "today" partially open sometimes so the demo shows every current-status state
            if (!isToday || status == AttendanceStatus.LATE) {
                record.setCheckOutTime(date.atTime(17, 30));
            }
        }
        // ABSENT and LEAVE intentionally have no check-in/check-out times
        attendance.save(record);
    }

    private User createIfMissing(String username, String rawPassword, String fullName, String email, Role role, Department department, String jobTitle) {
        return users.findByUsername(username).orElseGet(() -> {
            User user = new User(); user.setUsername(username); user.setPassword(encoder.encode(rawPassword));
            user.setFullName(fullName); user.setEmail(email); user.setRole(role); user.setDepartment(department);
            user.setJobTitle(jobTitle);
            return users.save(user);
        });
    }
}
