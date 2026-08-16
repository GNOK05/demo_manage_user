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

@Configuration
@RequiredArgsConstructor
public class DataInitializer {
    private final UserRepository users;
    private final DepartmentRepository departments;
    private final ProjectRepository projects;
    private final TaskRepository tasks;
    private final AttendanceRepository attendance;
    private final PasswordEncoder encoder;

    /** Local bootstrap account; change or remove it before production deployment. */
    @Bean
    CommandLineRunner seedAdministrator() {
        return args -> {
            createIfMissing("admin", "admin123", "System Administrator", "admin@company.local", Role.ADMIN, null);

            Department it = departments.findAll().stream().filter(d -> d.getCode().equals("IT")).findFirst().orElseGet(() -> {
                Department d = new Department(); d.setName("Information Technology"); d.setCode("IT"); d.setDescription("Demo department"); return departments.save(d);
            });
            User manager = createIfMissing("manager", "manager123", "IT Manager", "manager@company.local", Role.MANAGER, it);
            if (it.getManager() == null) { it.setManager(manager); departments.save(it); }
            createIfMissing("employee", "employee123", "Demo Employee", "employee@company.local", Role.EMPLOYEE, it);
            seedDemoData();
        };
    }

    private void seedDemoData() {
        if (projects.count() >= 15) return;
        String[] names = {"Engineering", "Human Resources", "Finance", "Marketing", "Sales", "Operations", "Customer Success", "Product", "Design", "Legal", "Procurement", "Quality", "Research", "Training", "Support"};
        LocalDate today = LocalDate.now();
        for (int i = 0; i < names.length; i++) {
            int number = i + 1;
            String code = String.format("D%02d", number);
            String departmentName = names[i];
            Department department = departments.findAll().stream().filter(d -> code.equals(d.getCode())).findFirst().orElseGet(() -> {
                Department d = new Department(); d.setName(departmentName); d.setCode(code); d.setDescription("Demo " + departmentName + " department"); return departments.save(d);
            });
            User manager = createIfMissing("lead" + number, "lead123", names[i] + " Lead", "lead" + number + "@company.local", Role.MANAGER, department);
            if (department.getManager() == null) { department.setManager(manager); departments.save(department); }
            User employee = createIfMissing("staff" + number, "staff123", "Demo Staff " + number, "staff" + number + "@company.local", Role.EMPLOYEE, department);

            Project project = new Project(); project.setProjectName(names[i] + " Improvement " + number); project.setDescription("Seeded demo project for " + names[i]); project.setDepartment(department);
            project.setStartDate(today.minusDays(20)); project.setEndDate(today.plusDays(40)); project.setStatus(ProjectStatus.IN_PROGRESS); project = projects.save(project);
            for (int taskNumber = 1; taskNumber <= 2; taskNumber++) {
                Task task = new Task(); task.setTaskName("Task " + taskNumber + " - " + names[i]); task.setDescription("Demo task for project " + project.getProjectName()); task.setProject(project); task.setAssignedTo(employee); task.setCreatedBy(manager);
                task.setStatus(taskNumber == 1 ? TaskStatus.IN_PROGRESS : TaskStatus.TODO); task.setDeadline(today.plusDays(7 + taskNumber)); tasks.save(task);
            }
            Attendance record = new Attendance(); record.setUser(employee); record.setDate(today.minusDays(i % 7)); record.setCheckInTime(today.minusDays(i % 7).atTime(8, 30)); record.setCheckOutTime(today.minusDays(i % 7).atTime(17, 30)); record.setStatus(i % 4 == 0 ? AttendanceStatus.LATE : AttendanceStatus.PRESENT); attendance.save(record);
        }
    }

    private User createIfMissing(String username, String rawPassword, String fullName, String email, Role role, Department department) {
        return users.findByUsername(username).orElseGet(() -> {
            User user = new User(); user.setUsername(username); user.setPassword(encoder.encode(rawPassword));
            user.setFullName(fullName); user.setEmail(email); user.setRole(role); user.setDepartment(department);
            return users.save(user);
        });
    }
}
