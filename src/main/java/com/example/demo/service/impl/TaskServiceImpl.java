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
import java.util.List;
@Service @RequiredArgsConstructor @Transactional
public class TaskServiceImpl implements TaskService {
 private final TaskRepository tasks; private final ProjectRepository projects; private final UserService users;
 public List<TaskDto.Response> byProject(Long projectId){Project p=project(projectId);canManage(p);return tasks.findByProjectId(projectId).stream().map(this::response).toList();}
 public List<TaskDto.Response> myTasks(){return tasks.findByAssignedToIdOrderByDeadlineAsc(users.currentUser().getId()).stream().map(this::response).toList();}
 public TaskDto.Response save(TaskDto.SaveRequest r){Project p=project(r.projectId());canManage(p);return response(tasks.save(build(new Task(),r,p)));}
 public TaskDto.Response update(Long id,TaskDto.SaveRequest r){Task t=task(id);canManage(t.getProject());Project p=project(r.projectId());canManage(p);return response(build(t,r,p));}
 public TaskDto.Response updateStatus(Long id,TaskDto.StatusRequest r){Task t=task(id);User current=users.currentUser();boolean manager=current.getRole()==Role.ADMIN||(current.getRole()==Role.MANAGER&&current.getDepartment()!=null&&current.getDepartment().getId().equals(t.getProject().getDepartment().getId()));boolean owner=t.getAssignedTo()!=null&&t.getAssignedTo().getId().equals(current.getId());if(!manager&&!owner)throw new BussinessException("You are not allowed to update this task");if(owner&&r.status()==TaskStatus.REVIEW)throw new BussinessException("Employees cannot move tasks to REVIEW");t.setStatus(r.status());return response(t);}
 public void delete(Long id){Task t=task(id);canManage(t.getProject());tasks.delete(t);}
 private Task build(Task t,TaskDto.SaveRequest r,Project p){if(r.deadline().isBefore(p.getStartDate())||r.deadline().isAfter(p.getEndDate()))throw new BussinessException("Task deadline must be within project dates");if(r.assignedToId()!=null){User assignee=users.findEntity(r.assignedToId());if(assignee.getDepartment()==null||!assignee.getDepartment().getId().equals(p.getDepartment().getId()))throw new BussinessException("Task assignee must belong to the project department");t.setAssignedTo(assignee);}else t.setAssignedTo(null);t.setTaskName(r.taskName());t.setDescription(r.description());t.setProject(p);t.setDeadline(r.deadline());t.setStatus(r.status()==null?TaskStatus.TODO:r.status());if(t.getCreatedBy()==null)t.setCreatedBy(users.currentUser());return t;}
 private Project project(Long id){return projects.findById(id).orElseThrow(()->new BussinessException("Project not found: "+id));}
 private Task task(Long id){return tasks.findById(id).orElseThrow(()->new BussinessException("Task not found: "+id));}
 private void canManage(Project p){User u=users.currentUser();if(u.getRole()==Role.ADMIN)return;if(u.getRole()!=Role.MANAGER||u.getDepartment()==null||!u.getDepartment().getId().equals(p.getDepartment().getId()))throw new BussinessException("Only the department manager can manage tasks");}
 private TaskDto.Response response(Task t){return new TaskDto.Response(t.getId(),t.getTaskName(),t.getDescription(),t.getProject().getId(),t.getProject().getProjectName(),t.getAssignedTo()==null?null:t.getAssignedTo().getId(),t.getAssignedTo()==null?null:t.getAssignedTo().getFullName(),t.getCreatedBy().getId(),t.getStatus(),t.getDeadline());}
}
