package com.example.demo.config;

import com.example.demo.entity.*;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.repository.LeaveRequestRepository;
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
import java.time.ZoneId;
import java.util.List;

/**
 * Seeds demo data: one working manager + two role-specific staff (DEV/QA/QC/TEST) per department,
 * three projects per department covering every {@link ProjectStatus}, tasks covering every
 * {@link TaskStatus} (with a tester assigned on some of them), and a 7-day attendance history
 * covering a mix of working, late, and absent {@link AttendanceStatus} values.
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
    private final LeaveRequestRepository leaveRequests;
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

    /** Drops old generic demo accounts that are not part of the department dataset. */
    private void removeLegacyDemoAccounts() {
        departments.findAll().stream().filter(d -> "IT".equals(d.getCode())).findFirst().ifPresent(it -> {
            if (it.getManager() != null) { it.setManager(null); departments.save(it); }
            users.findByUsername("manager").ifPresent(users::delete);
            users.findByUsername("employee").ifPresent(users::delete);
            departments.delete(it);
        });
    }

    private void seedDemoData() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));

        for (int i = 0; i < DEPARTMENT_NAMES.length; i++) {
            int number = i + 1;
            String code = String.format("D%02d", number);
            String deptName = DEPARTMENT_NAMES[i];

            Department department = findOrCreateDepartment(code, deptName,
                "Responsible for " + deptName.toLowerCase() + " planning, delivery, and employee support.");

            User manager = createOrMigrateDemoUser("manager_" + number, "lead" + number, "lead123",
                deptName + " Manager", "manager_" + number + "@company.local", Role.MANAGER, department, "Manager");
            if (department.getManager() == null || !department.getManager().getId().equals(manager.getId())) {
                department.setManager(manager);
                departments.save(department);
            }

            User dev1 = createOrMigrateDemoUser("dev_" + number + "_1", "dev" + number, "dev123",
                deptName + " Developer 1", "dev_" + number + "_1@company.local", Role.EMPLOYEE, department, "DEV");
            User dev2 = createIfMissing("dev_" + number + "_2", "dev123", deptName + " Developer 2",
                "dev_" + number + "_2@company.local", Role.EMPLOYEE, department, "DEV");
            User qa1 = createOrMigrateDemoUser("qa_" + number + "_1", "qa" + number, "qa123",
                deptName + " QA 1", "qa_" + number + "_1@company.local", Role.EMPLOYEE, department, "QA");
            User qa2 = createIfMissing("qa_" + number + "_2", "qa123", deptName + " QA 2",
                "qa_" + number + "_2@company.local", Role.EMPLOYEE, department, "QA");
            User qc1 = createOrMigrateDemoUser("qc_" + number + "_1", "qc" + number, "qc123",
                deptName + " QC 1", "qc_" + number + "_1@company.local", Role.EMPLOYEE, department, "QC");
            User qc2 = createIfMissing("qc_" + number + "_2", "qc123", deptName + " QC 2",
                "qc_" + number + "_2@company.local", Role.EMPLOYEE, department, "QC");
            User tester1 = createOrMigrateDemoUser("test_" + number + "_1", "test" + number, "test123",
                deptName + " Tester 1", "test_" + number + "_1@company.local", Role.EMPLOYEE, department, "TEST");
            User tester2 = createIfMissing("test_" + number + "_2", "test123", deptName + " Tester 2",
                "test_" + number + "_2@company.local", Role.EMPLOYEE, department, "TEST");
            List<User> employees = List.of(dev1, qa1, qc1, tester1, dev2, qa2, qc2, tester2);
            List<User> departmentMembers = List.of(manager, dev1, dev2, qa1, qa2, qc1, qc2, tester1, tester2);

            // 3 projects per department, one in each status
            Project notStarted = findOrCreateProject(department, deptName + " Service Refresh " + number,
                "Planned improvements to " + deptName.toLowerCase() + " workflows, reporting, and employee experience.",
                today.plusDays(10), today.plusDays(70), ProjectStatus.NOT_STARTED);
            Project inProgress = findOrCreateProject(department, deptName + " Workflow Automation " + number,
                "Automating routine " + deptName.toLowerCase() + " processes with measurable delivery milestones.",
                today.minusDays(20), today.plusDays(40), ProjectStatus.IN_PROGRESS);
            Project completed = findOrCreateProject(department, deptName + " Quarterly Delivery " + number,
                "Completed quarterly priorities, stakeholder review, and operational handover for " + deptName + ".",
                today.minusDays(90), today.minusDays(10), ProjectStatus.COMPLETED);

            // NOT_STARTED project: tasks not begun yet, no tester needed
            findOrCreateTask(notStarted, "Project kickoff and scope alignment", dev1, null, manager, TaskStatus.TODO, today.plusDays(15));
            findOrCreateTask(notStarted, "Stakeholder requirements workshop", qa1, null, manager, TaskStatus.TODO, today.plusDays(20));
            findOrCreateTask(notStarted, "Data inventory and migration plan", dev2, null, manager, TaskStatus.TODO, today.plusDays(25));
            findOrCreateTask(notStarted, "Acceptance criteria and test plan", qa2, null, manager, TaskStatus.TODO, today.plusDays(28));

            // IN_PROGRESS project: tasks spread across the workflow, testers assigned once implementation exists
            findOrCreateTask(inProgress, "Workflow mapping and solution design", dev1, qa1, manager, TaskStatus.DONE, today.minusDays(5));
            findOrCreateTask(inProgress, "Build automation and reporting", dev1, qc1, manager, TaskStatus.IN_PROGRESS, today.plusDays(5));
            findOrCreateTask(inProgress, "Regression testing and peer review", qc1, tester1, manager, TaskStatus.REVIEW, today.plusDays(8));
            findOrCreateTask(inProgress, "Release checklist and user guide", manager, null, manager, TaskStatus.TODO, today.plusDays(15));
            findOrCreateTask(inProgress, "Data validation and reconciliation", qc2, tester2, manager, TaskStatus.REVIEW, today.plusDays(10));
            findOrCreateTask(inProgress, "Business user acceptance session", tester2, qa2, manager, TaskStatus.TODO, today.plusDays(12));

            // COMPLETED project: everything finished and verified
            findOrCreateTask(completed, "Production rollout and acceptance", dev1, tester1, manager, TaskStatus.DONE, today.minusDays(12));
            findOrCreateTask(completed, "Operations handover and documentation", qa1, qc1, manager, TaskStatus.DONE, today.minusDays(11));

            seedLeaveRequests(employees, manager, users.findByUsername("admin").orElseThrow(), today);

            // Attendance history: last 7 days for every department member, including its manager.
            for (User employee : departmentMembers) {
                // Offset each department's pattern so dashboard totals differ across departments.
                seedAttendanceHistory(employee, today, (number * 3 + departmentMembers.indexOf(employee) * 2) % 7);
            }
        }
    }

    private void seedLeaveRequests(List<User> employees, User manager, User admin, LocalDate today) {
        findOrCreateLeaveRequest(employees.get(0), manager, LeaveRequestType.ANNUAL,
            today.plusDays(12), today.plusDays(14), "Family trip planned in advance", LeaveRequestStatus.PENDING);
        LocalDate pastAnnualDay = today.minusDays(18);
        while (pastAnnualDay.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
                || pastAnnualDay.getDayOfWeek() == java.time.DayOfWeek.SUNDAY) {
            pastAnnualDay = pastAnnualDay.minusDays(1);
        }
        findOrCreateLeaveRequest(employees.get(1), manager, admin, LeaveRequestType.ANNUAL,
            pastAnnualDay, pastAnnualDay, "Previously approved annual leave (demo)", LeaveRequestStatus.APPROVED);
        findOrCreateLeaveRequest(employees.get(2), manager, LeaveRequestType.SICK,
            today.minusDays(32), today.minusDays(31), "Recovery from seasonal illness", LeaveRequestStatus.REJECTED);
    }

    private void findOrCreateLeaveRequest(User employee, User manager, LeaveRequestType type,
                                           LocalDate from, LocalDate to, String reason, LeaveRequestStatus status) {
        findOrCreateLeaveRequest(employee, manager, null, type, from, to, reason, status);
    }

    private void findOrCreateLeaveRequest(User employee, User manager, User admin, LeaveRequestType type,
                                           LocalDate from, LocalDate to, String reason, LeaveRequestStatus status) {
        LeaveRequest request = leaveRequests.findByUserOrderByCreatedAtDesc(employee).stream()
            .filter(existing -> reason.equals(existing.getReason())
                || "Previously approved annual leave (demo)".equals(reason)
                && "Personal appointment".equals(existing.getReason()))
            .findFirst()
            .orElseGet(LeaveRequest::new);
        request.setUser(employee);
        request.setType(type);
        request.setFromDate(from);
        request.setToDate(to);
        request.setReason(reason);
        request.setStatus(status);
        if (status == LeaveRequestStatus.APPROVED && admin != null) {
            request.setManagerApprovedBy(manager);
            request.setManagerApprovedAt(LocalDateTime.now());
            request.setAdminApprovedBy(admin);
            request.setAdminApprovedAt(LocalDateTime.now());
            request.setApprovedBy(admin);
            request.setApprovedAt(LocalDateTime.now());
        } else if (status == LeaveRequestStatus.REJECTED && manager != null) {
            request.setApprovedBy(manager);
        }
        leaveRequests.save(request);
    }

    private void seedAttendanceHistory(User employee, LocalDate today, int variant) {
        AttendanceStatus[] pattern = {
            AttendanceStatus.PRESENT, AttendanceStatus.PRESENT, AttendanceStatus.LATE,
            AttendanceStatus.PRESENT, AttendanceStatus.ABSENT, AttendanceStatus.PRESENT, AttendanceStatus.PRESENT
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
        String legacyName = name.replace(" Service Refresh ", " Revamp ")
            .replace(" Workflow Automation ", " Improvement ")
            .replace(" Quarterly Delivery ", " Rollout ");
        Project existing = projects.findByDepartmentId(department.getId()).stream()
            .filter(project -> name.equals(project.getProjectName()) || legacyName.equals(project.getProjectName()))
            .findFirst().orElse(null);
        if (existing != null) {
            if (!name.equals(existing.getProjectName())) {
                existing.setProjectName(name);
                existing.setDescription(description);
                return projects.save(existing);
            }
            return existing;
        }
        Project project = new Project();
        project.setProjectName(name); project.setDescription(description); project.setDepartment(department);
        project.setStartDate(start); project.setEndDate(end); project.setStatus(status);
        return projects.save(project);
    }

    private Task findOrCreateTask(Project project, String name, User assignee, User tester, User creator,
                                   TaskStatus status, LocalDate deadline) {
        String legacyName = switch (name) {
            case "Project kickoff and scope alignment" -> "Kickoff planning";
            case "Stakeholder requirements workshop" -> "Requirements gathering";
            case "Workflow mapping and solution design" -> "Design phase";
            case "Build automation and reporting" -> "Implementation";
            case "Regression testing and peer review" -> "Peer review";
            case "Release checklist and user guide" -> "Backlog cleanup";
            case "Production rollout and acceptance" -> "Final delivery";
            case "Operations handover and documentation" -> "Handover documentation";
            default -> name;
        };
        Task existing = tasks.findByProjectId(project.getId()).stream()
            .filter(task -> name.equals(task.getTaskName()) || legacyName.equals(task.getTaskName()))
            .findFirst().orElse(null);
        if (existing != null) {
            if (!name.equals(existing.getTaskName())) {
                existing.setTaskName(name);
                existing.setDescription(name + " for the " + project.getProjectName() + " project.");
                return tasks.save(existing);
            }
            if (assignee != null && existing.getAssignedTo() == null) {
                existing.setAssignedTo(assignee);
                return tasks.save(existing);
            }
            return existing;
        }
        Task task = new Task();
        task.setTaskName(name); task.setDescription(name + " for the " + project.getProjectName() + " project.");
        task.setProject(project); task.setAssignedTo(assignee); task.setTester(tester); task.setCreatedBy(creator);
        task.setStatus(status); task.setDeadline(deadline);
        return tasks.save(task);
    }

    /**
     * Creates today's attendance record with real check-in/out timestamps so the "current status"
     * views (Chưa vào làm / Đang làm việc / Tạm vắng / Đã tan làm) have something to compute from;
     * past days only need a status for history display.
     */
    private void findOrCreateAttendance(User employee, LocalDate date, AttendanceStatus status, boolean isToday) {
        if (attendance.findByUserIdAndDate(employee.getId(), date).isPresent()) return;
        // Keep today's workday open so demo users can perform a real check-in in the UI.
        if (isToday && (status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE)) return;
        Attendance record = new Attendance();
        record.setUser(employee);
        record.setDate(date);
        record.setStatus(status);
        if (status == AttendanceStatus.PRESENT || status == AttendanceStatus.LATE) {
            // Stable per-user time offsets make repeated demo runs consistent while avoiding identical records.
            int minuteOffset = Math.floorMod(employee.getUsername().hashCode(), 36);
            LocalDateTime checkIn = status == AttendanceStatus.LATE
                ? date.atTime(9, 5).plusMinutes(minuteOffset)
                : date.atTime(8, 5).plusMinutes(minuteOffset);
            record.setCheckInTime(checkIn);
            // Leave "today" partially open sometimes so the demo shows every current-status state
            if (!isToday || status == AttendanceStatus.LATE) {
                record.setCheckOutTime(date.atTime(16, 45).plusMinutes(Math.floorMod(employee.getUsername().hashCode(), 91)));
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

    private User createOrMigrateDemoUser(String username, String legacyUsername, String rawPassword,
                                          String fullName, String email, Role role, Department department,
                                          String jobTitle) {
        return users.findByUsername(username).orElseGet(() -> users.findByUsername(legacyUsername)
            .map(user -> {
                user.setUsername(username);
                user.setFullName(fullName);
                user.setEmail(email);
                user.setRole(role);
                user.setDepartment(department);
                user.setJobTitle(jobTitle);
                return users.save(user);
            })
            .orElseGet(() -> createIfMissing(username, rawPassword, fullName, email, role, department, jobTitle)));
    }
}
