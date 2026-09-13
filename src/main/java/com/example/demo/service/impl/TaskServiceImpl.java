package com.example.demo.service.impl;
import com.example.demo.dto.TaskDto;
import com.example.demo.entity.*;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.ProjectRepository;
import com.example.demo.repository.TaskRepository;
import com.example.demo.service.TaskService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
@Service @RequiredArgsConstructor @Transactional
public class TaskServiceImpl implements TaskService {
 private final TaskRepository tasks; private final ProjectRepository projects; private final UserService users; private final AttendanceRepository attendance;
 @Override public List<TaskDto.Response> byProject(Long projectId){Project p=project(projectId);canManage(p);return tasks.findByProjectId(Objects.requireNonNull(projectId)).stream().map(this::response).toList();}
 @Override public List<TaskDto.Response> myTasks(){return tasks.findByAssignedToIdOrderByDeadlineAsc(users.currentUser().getId()).stream().map(this::response).toList();}
 @Override
 public List<TaskDto.Response> accessibleTasks(String keyword, Role assignedRole, TaskStatus status, String sort){
    User current=users.currentUser();
     List<Task> accessible;
    switch (current.getRole()) {
    case EMPLOYEE -> accessible = tasks.findByAssignedToIdOrderByDeadlineAsc(current.getId());
    case ADMIN -> accessible = tasks.findAll();
    case MANAGER -> {
      if(current.getDepartment()==null)throw new BussinessException("Manager must belong to a department");
      accessible = projects.findByDepartmentId(current.getDepartment().getId()).stream()
       .flatMap(project->tasks.findByProjectId(project.getId()).stream()).toList();
    }
    default -> throw new BussinessException("Unsupported user role");
    }
     return filterAndSort(accessible, keyword, assignedRole, status, sort).stream().map(this::response).toList();
 }
 private List<Task> filterAndSort(List<Task> source, String keyword, Role assignedRole, TaskStatus status, String sort){
  String query=keyword==null?"":keyword.trim().toLowerCase();
  var filtered=source.stream().filter(task -> query.isBlank()
   || task.getTaskName().toLowerCase().contains(query)
   || task.getProject().getProjectName().toLowerCase().contains(query)
   || (task.getAssignedTo()!=null && task.getAssignedTo().getFullName().toLowerCase().contains(query)))
   .filter(task -> assignedRole==null || (task.getAssignedTo()!=null && task.getAssignedTo().getRole()==assignedRole))
   .filter(task -> status==null || task.getStatus()==status).toList();
  Comparator<Task> comparator=switch(sort==null?"DEFAULT":sort.toUpperCase()){
  case "NEWEST" -> Comparator.comparing((Task task) -> task.getId(), Comparator.nullsLast(Comparator.reverseOrder()));
  case "OLDEST" -> Comparator.comparing((Task task) -> task.getId(), Comparator.nullsLast(Comparator.naturalOrder()));
  case "DEADLINE_ASC" -> Comparator.comparing((Task task) -> task.getDeadline(), Comparator.nullsLast(Comparator.naturalOrder()));
  case "DEADLINE_DESC" -> Comparator.comparing((Task task) -> task.getDeadline(), Comparator.nullsLast(Comparator.reverseOrder()));
   case "NAME_ASC" -> Comparator.comparing(task -> task.getTaskName().toLowerCase());
   case "NAME_DESC" -> Comparator.comparing((Task task) -> task.getTaskName().toLowerCase()).reversed();
  default -> Comparator.comparing((Task task) -> task.getDeadline(), Comparator.nullsLast(Comparator.naturalOrder()));
  };
  return filtered.stream().sorted(comparator).toList();
 }
 @Override public TaskDto.Response save(TaskDto.SaveRequest r){Project p=project(r.projectId());canManage(p);Task task=build(new Task(),r,p);return response(tasks.save(Objects.requireNonNull(task)));}
 @Override public TaskDto.Response update(Long id,TaskDto.SaveRequest r){Task t=task(id);canManage(t.getProject());Project p=project(r.projectId());canManage(p);return response(build(t,r,p));}
 @Override public TaskDto.Response updateStatus(Long id,TaskDto.StatusRequest r){Task t=task(id);User current=users.currentUser();boolean manager=current.getRole()==Role.ADMIN||(current.getRole()==Role.MANAGER&&current.getDepartment()!=null&&current.getDepartment().getId().equals(t.getProject().getDepartment().getId()));boolean owner=t.getAssignedTo()!=null&&t.getAssignedTo().getId().equals(current.getId());if(!manager&&!owner)throw new BussinessException("You are not allowed to update this task");if(owner&&r.status()==TaskStatus.REVIEW)throw new BussinessException("Employees cannot move tasks to REVIEW");validateAttendanceForStatusUpdate(current,r.status());t.setStatus(r.status());return response(t);}
 @Override public void delete(Long id){Task t=task(id);canManage(t.getProject());tasks.delete(t);}
 private Task build(Task t,TaskDto.SaveRequest r,Project p){
  if(r.deadline().isBefore(p.getStartDate())||r.deadline().isAfter(p.getEndDate()))throw new BussinessException("Task deadline must be within project dates");
  if(r.assignedToId()!=null){User assignee=users.findEntity(r.assignedToId());if(assignee.getDepartment()==null||!assignee.getDepartment().getId().equals(p.getDepartment().getId()))throw new BussinessException("Task assignee must belong to the project department");t.setAssignedTo(assignee);}else t.setAssignedTo(null);
  if(r.testerId()!=null){User tester=users.findEntity(r.testerId());if(tester.getDepartment()==null||!tester.getDepartment().getId().equals(p.getDepartment().getId()))throw new BussinessException("Tester must belong to the project department");t.setTester(tester);}else t.setTester(null);
  t.setTaskName(r.taskName());t.setDescription(r.description());t.setProject(p);t.setDeadline(r.deadline());t.setStatus(r.status()==null?TaskStatus.TODO:r.status());if(t.getCreatedBy()==null)t.setCreatedBy(users.currentUser());return t;}
 private Project project(Long id){return projects.findById(Objects.requireNonNull(id)).orElseThrow(()->new BussinessException("Project not found: "+id));}
 private Task task(Long id){return tasks.findById(Objects.requireNonNull(id)).orElseThrow(()->new BussinessException("Task not found: "+id));}
 private void canManage(Project p){User u=users.currentUser();if(u.getRole()==Role.ADMIN)return;if(u.getRole()!=Role.MANAGER||u.getDepartment()==null||!u.getDepartment().getId().equals(p.getDepartment().getId()))throw new BussinessException("Only the department manager can manage tasks");}
 private void validateAttendanceForStatusUpdate(User user, TaskStatus status){if(user.getRole()==Role.ADMIN||user.getRole()==Role.MANAGER)return;if(status==TaskStatus.DONE||status==TaskStatus.REVIEW){var today=attendance.findByUserIdAndDate(user.getId(),LocalDate.now());if(today.isEmpty()||today.get().getCheckOutTime()==null){throw new BussinessException("You must check out before marking this task as complete. Current time is during work hours, please finish your day before updating task status.");}}}
 private TaskDto.Response response(Task t){return new TaskDto.Response(t.getId(),t.getTaskName(),t.getDescription(),t.getProject().getId(),t.getProject().getProjectName(),t.getAssignedTo()==null?null:t.getAssignedTo().getId(),t.getAssignedTo()==null?null:t.getAssignedTo().getFullName(),t.getTester()==null?null:t.getTester().getId(),t.getTester()==null?null:t.getTester().getFullName(),t.getCreatedBy()==null?null:t.getCreatedBy().getId(),t.getStatus(),t.getDeadline());}
}
