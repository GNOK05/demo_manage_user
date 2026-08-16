package com.example.demo.service.impl;
import com.example.demo.dto.ProjectDto;
import com.example.demo.entity.*;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.*;
import com.example.demo.service.ProjectService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional
public class ProjectServiceImpl implements ProjectService {
 private final ProjectRepository projects; private final DepartmentRepository departments; private final TaskRepository tasks; private final UserService users;
 public List<ProjectDto.Response> findAccessible(){User u=users.currentUser(); List<Project> result=u.getRole()==Role.ADMIN?projects.findAll():u.getRole()==Role.MANAGER&&u.getDepartment()!=null?projects.findByDepartmentId(u.getDepartment().getId()):projects.findAssignedToUser(u.getId()); return result.stream().map(this::response).toList();}
 public ProjectDto.Response get(Long id){Project p=entity(id); User u=users.currentUser(); if(u.getRole()==Role.EMPLOYEE&&!projects.findAssignedToUser(u.getId()).stream().anyMatch(x->x.getId().equals(id)))throw new BussinessException("You are not assigned to this project"); if(u.getRole()!=Role.EMPLOYEE)verifyDepartment(p);return response(p);}
 public ProjectDto.Response save(ProjectDto.SaveRequest r){Department d=department(r.departmentId());verifyManage(d);return response(projects.save(build(new Project(),r,d)));}
 public ProjectDto.Response update(Long id,ProjectDto.SaveRequest r){Project p=entity(id);verifyManage(p.getDepartment());Department d=department(r.departmentId());verifyManage(d);return response(build(p,r,d));}
 public void delete(Long id){Project p=entity(id);verifyManage(p.getDepartment());projects.delete(p);}
 private Project entity(Long id){return projects.findById(id).orElseThrow(()->new BussinessException("Project not found: "+id));}
 private Department department(Long id){return departments.findById(id).orElseThrow(()->new BussinessException("Department not found: "+id));}
 private Project build(Project p,ProjectDto.SaveRequest r,Department d){if(r.endDate().isBefore(r.startDate()))throw new BussinessException("End date must not precede start date");p.setProjectName(r.projectName());p.setDescription(r.description());p.setDepartment(d);p.setStartDate(r.startDate());p.setEndDate(r.endDate());p.setStatus(r.status()==null?ProjectStatus.NOT_STARTED:r.status());return p;}
 private void verifyDepartment(Project p){verifyManage(p.getDepartment());}
 private void verifyManage(Department d){User u=users.currentUser();if(u.getRole()==Role.ADMIN)return;if(u.getRole()!=Role.MANAGER||u.getDepartment()==null||!u.getDepartment().getId().equals(d.getId()))throw new BussinessException("You can only manage projects in your department");}
 private ProjectDto.Response response(Project p){long all=tasks.countByProjectId(p.getId()),done=tasks.countByProjectIdAndStatus(p.getId(),TaskStatus.DONE);int progress=all==0?0:(int)Math.round(done*100.0/all);return new ProjectDto.Response(p.getId(),p.getProjectName(),p.getDescription(),p.getDepartment().getId(),p.getDepartment().getName(),p.getStartDate(),p.getEndDate(),p.getStatus(),all,done,progress);}
}
