package com.example.demo.service.impl;
import com.example.demo.dto.AttendanceDto;
import com.example.demo.entity.*;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.AttendanceRepository;
import com.example.demo.repository.AttendanceSessionRepository;
import com.example.demo.repository.LeaveRequestRepository;
import com.example.demo.service.AttendanceService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;
@Service @RequiredArgsConstructor @Transactional
public class AttendanceServiceImpl implements AttendanceService {
 private final AttendanceRepository records; private final AttendanceSessionRepository sessions; private final LeaveRequestRepository leaveRequests; private final UserService users;
 public AttendanceDto.Response checkIn(){User u=users.currentUser();LocalDate today=LocalDate.now();Attendance a=records.findByUserIdAndDate(u.getId(),today).orElseGet(()->{Attendance n=new Attendance();n.setUser(u);n.setDate(today);return n;});if(sessions.findFirstByAttendanceIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(a.getId()).isPresent()||(a.getId()!=null&&sessions.findByAttendanceIdOrderByCheckInTimeAsc(a.getId()).isEmpty()&&a.getCheckInTime()!=null&&a.getCheckOutTime()==null))throw new BussinessException("You are already checked in");migrateLegacySession(a);LocalDateTime now=LocalDateTime.now();boolean firstCheckIn=a.getCheckInTime()==null;if(firstCheckIn){a.setCheckInTime(now);a.setStatus(now.toLocalTime().isAfter(LocalTime.of(9,0))?AttendanceStatus.LATE:AttendanceStatus.PRESENT);}a.setCheckOutTime(null);a=records.save(a);AttendanceSession session=new AttendanceSession();session.setAttendance(a);session.setCheckInTime(now);sessions.save(session);return response(a);}
 public AttendanceDto.Response checkOut(){User u=users.currentUser();Attendance a=records.findByUserIdAndDate(u.getId(),LocalDate.now()).orElseThrow(()->new BussinessException("Please check in before checking out"));migrateLegacySession(a);AttendanceSession session=sessions.findFirstByAttendanceIdAndCheckOutTimeIsNullOrderByCheckInTimeDesc(a.getId()).orElseThrow(()->new BussinessException("You must check in before checking out"));LocalDateTime now=LocalDateTime.now();session.setCheckOutTime(now);sessions.save(session);a.setCheckOutTime(now);return response(records.save(a));}
 public List<AttendanceDto.Response> myAttendance(){return responses(records.findByUserIdOrderByDateDesc(users.currentUser().getId()));}
 public List<AttendanceDto.Response> departmentAttendance(){User u=users.currentUser();if(u.getRole()!=Role.MANAGER||u.getDepartment()==null)throw new BussinessException("Only department managers can view department attendance");return responses(records.findByUserDepartmentIdOrderByDateDesc(u.getDepartment().getId()));}
 public AttendanceDto.DepartmentSummary departmentSummary(LocalDate date){User current=users.currentUser();if(current.getRole()!=Role.MANAGER||current.getDepartment()==null)throw new BussinessException("Only department managers can view department attendance summaries");if(date==null)throw new BussinessException("Attendance date is required");if(date.isAfter(LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))))throw new BussinessException("Future attendance summaries are not available");long total=users.departmentMembers("").size();List<Attendance> dayRecords=records.findByUserDepartmentIdAndDate(current.getDepartment().getId(),date);java.util.Map<Long,AttendanceStatus> statusByUser=new java.util.HashMap<>();for(Attendance record:dayRecords)statusByUser.put(record.getUser().getId(),record.getStatus());java.util.Set<Long> approvedLeaveUsers=leaveRequests.findByUserDepartmentIdOrderByCreatedAtDesc(current.getDepartment().getId()).stream().filter(request->request.getStatus()==LeaveRequestStatus.APPROVED&&!request.getFromDate().isAfter(date)&&!request.getToDate().isBefore(date)).map(request->request.getUser().getId()).collect(java.util.stream.Collectors.toSet());long present=0,late=0,leave=0;for(Attendance record:dayRecords){Long userId=record.getUser().getId();if(approvedLeaveUsers.contains(userId)){continue;}AttendanceStatus status=statusByUser.get(userId);if(status==AttendanceStatus.PRESENT)present++;else if(status==AttendanceStatus.LATE)late++;else if(status==AttendanceStatus.LEAVE)leave++;}leave+=approvedLeaveUsers.size();long absent=total-present-late-leave;return new AttendanceDto.DepartmentSummary(date,total,present,late,absent,leave);}
 public List<AttendanceDto.Response> all(){return responses(records.findAll());}
 public List<AttendanceDto.Response> employeeAttendance(Long userId,int month,int year){
    if(month<1||month>12)throw new BussinessException("Month must be between 1 and 12");
    if(year<2000||year>2100)throw new BussinessException("Year must be between 2000 and 2100");
  User current=users.currentUser();
  User selected=users.findEntity(userId);
  if(current.getRole()==Role.MANAGER && (current.getDepartment()==null || selected.getDepartment()==null || !current.getDepartment().getId().equals(selected.getDepartment().getId()))) throw new BussinessException("You can only view attendance in your department");
  if(current.getRole()!=Role.ADMIN && current.getRole()!=Role.MANAGER) throw new BussinessException("Only administrators and managers can view employee attendance");
  LocalDate from=LocalDate.of(year,month,1);
  return responses(records.findByUserIdAndDateBetweenOrderByDateDesc(userId,from,from.withDayOfMonth(from.lengthOfMonth())));
 }
 private List<AttendanceDto.Response> responses(List<Attendance> attendanceRecords){attendanceRecords.forEach(this::migrateLegacySession);return attendanceRecords.stream().map(this::response).toList();}
 private void migrateLegacySession(Attendance a){if(a.getId()==null||a.getCheckInTime()==null||sessions.findByAttendanceIdOrderByCheckInTimeAsc(a.getId()).size()>0)return;AttendanceSession session=new AttendanceSession();session.setAttendance(a);session.setCheckInTime(a.getCheckInTime());session.setCheckOutTime(a.getCheckOutTime());sessions.save(session);}
 private AttendanceDto.Response response(Attendance a){List<AttendanceDto.Session> sessionResponses=a.getId()==null?List.of():sessions.findByAttendanceIdOrderByCheckInTimeAsc(a.getId()).stream().map(session->new AttendanceDto.Session(session.getId(),session.getCheckInTime(),session.getCheckOutTime())).toList();return new AttendanceDto.Response(a.getId(),a.getUser().getId(),a.getUser().getFullName(),a.getDate(),a.getCheckInTime(),a.getCheckOutTime(),a.getStatus(),sessionResponses);}
}
