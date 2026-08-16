package com.example.demo.service;
import com.example.demo.dto.DepartmentDto;
import java.util.List;
public interface DepartmentService { List<DepartmentDto.Response> findAll(); DepartmentDto.Response get(Long id); DepartmentDto.Response save(DepartmentDto.SaveRequest r); DepartmentDto.Response update(Long id, DepartmentDto.SaveRequest r); void delete(Long id); }
