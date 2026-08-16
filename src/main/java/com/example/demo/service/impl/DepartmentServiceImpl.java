package com.example.demo.service.impl;
import com.example.demo.dto.DepartmentDto;
import com.example.demo.entity.Department;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.DepartmentRepository;
import com.example.demo.service.DepartmentService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional
public class DepartmentServiceImpl implements DepartmentService {
 private final DepartmentRepository departments; private final UserService users;
 public List<DepartmentDto.Response> findAll(){return departments.findAll().stream().map(this::response).toList();}
 public DepartmentDto.Response get(Long id){return response(entity(id));}
 public DepartmentDto.Response save(DepartmentDto.SaveRequest r){if(departments.existsByCode(r.code()))throw new BussinessException("Department code already exists"); return response(departments.save(build(new Department(),r)));}
 public DepartmentDto.Response update(Long id,DepartmentDto.SaveRequest r){return response(build(entity(id),r));}
 public void delete(Long id){departments.delete(entity(id));}
 private Department entity(Long id){return departments.findById(id).orElseThrow(()->new BussinessException("Department not found: "+id));}
 private Department build(Department d,DepartmentDto.SaveRequest r){d.setName(r.name());d.setCode(r.code());d.setDescription(r.description());d.setManager(r.managerId()==null?null:users.findEntity(r.managerId()));return d;}
 private DepartmentDto.Response response(Department d){return new DepartmentDto.Response(d.getId(),d.getName(),d.getCode(),d.getDescription(),d.getManager()==null?null:d.getManager().getId(),d.getManager()==null?null:d.getManager().getFullName());}
}
