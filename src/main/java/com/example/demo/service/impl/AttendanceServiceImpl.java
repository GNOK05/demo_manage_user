package com.example.demo.service.impl;
import com.example.demo.dto.AttendanceDto;
import com.example.demo.entity.*;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.service.AttendanceService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional
public class AttendanceServiceImpl implements AttendanceService {
 private final AttendanceRepository records; private final UserService users;
 public AttendanceDto.Response checkIn(){User u=users.currentUser();LocalDate today=LocalDate.now();Attendance a=records.findByUserIdAndDate(u.getId(),today).orElseGet(()->{Attendance n=new Attendance();n.setUser(u);n.setDate(today);return n;});if(a.getCheckInTime()!=null)throw new BussinessException("You have already checked in today");LocalDateTime now=LocalDateTime.now();a.setCheckInTime(now);a.setStatus(now.toLocalTime().isAfter(LocalTime.of(9,0))?AttendanceStatus.LATE:AttendanceStatus.PRESENT);return response(records.save(a));}
 public AttendanceDto.Response checkOut(){User u=users.currentUser();Attendance a=records.findByUserIdAndDate(u.getId(),LocalDate.now()).orElseThrow(()->new BussinessException("Please check in before checking out"));if(a.getCheckOutTime()!=null)throw new BussinessException("You have already checked out today");a.setCheckOutTime(LocalDateTime.now());return response(a);}
 public List<AttendanceDto.Response> myAttendance(){return records.findByUserIdOrderByDateDesc(users.currentUser().getId()).stream().map(this::response).toList();}
 public List<AttendanceDto.Response> departmentAttendance(){User u=users.currentUser();if(u.getRole()!=Role.MANAGER||u.getDepartment()==null)throw new BussinessException("Only department managers can view department attendance");return records.findByUserDepartmentIdOrderByDateDesc(u.getDepartment().getId()).stream().map(this::response).toList();}
 public List<AttendanceDto.Response> all(){return records.findAll().stream().map(this::response).toList();}
 public List<AttendanceDto.Response> employeeAttendance(Long userId,int month,int year){
    if(month<1||month>12)throw new BussinessException("Month must be between 1 and 12");
    if(year<2000||year>2100)throw new BussinessException("Year must be between 2000 and 2100");
  User current=users.currentUser();
  User selected=users.findEntity(userId);
  if(current.getRole()==Role.MANAGER && (current.getDepartment()==null || selected.getDepartment()==null || !current.getDepartment().getId().equals(selected.getDepartment().getId()))) throw new BussinessException("You can only view attendance in your department");
  if(current.getRole()!=Role.ADMIN && current.getRole()!=Role.MANAGER) throw new BussinessException("Only administrators and managers can view employee attendance");
  LocalDate from=LocalDate.of(year,month,1);
  return records.findByUserIdAndDateBetweenOrderByDateDesc(userId,from,from.withDayOfMonth(from.lengthOfMonth())).stream().map(this::response).toList();
 }
 private AttendanceDto.Response response(Attendance a){return new AttendanceDto.Response(a.getId(),a.getUser().getId(),a.getUser().getFullName(),a.getDate(),a.getCheckInTime(),a.getCheckOutTime(),a.getStatus());}
}
