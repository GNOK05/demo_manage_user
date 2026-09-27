package com.example.demo.service.impl;

import com.example.demo.dto.PayrollDto;
import com.example.demo.entity.*;
import com.example.demo.exception.BussinessException;
import com.example.demo.repository.*;
import com.example.demo.service.PayrollService;
import com.example.demo.service.LeaveWorkdayCalculator;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class PayrollServiceImpl implements PayrollService {
    private final UserRepository users;
    private final AttendanceRepository attendance;
    private final AttendanceSessionRepository sessions;
    private final LeaveRequestRepository leaveRequests;
    private final PayrollPeriodRepository periods;
    private final UserService userService;

    @Override
    @Transactional(readOnly = true)
    public PayrollDto.MonthlyReport monthly(int year, int month) {
        YearMonth period = validatePeriod(year, month);
        User current = userService.currentUser();
        List<User> employees;
        if (current.getRole() == Role.ADMIN) employees = users.findAll().stream()
            .filter(user -> user.getRole() != Role.ADMIN).toList();
        else if (current.getRole() == Role.MANAGER && current.getDepartment() != null) {
            employees = users.findByDepartmentId(current.getDepartment().getId());
        } else if (current.getRole() == Role.EMPLOYEE) employees = List.of(current);
        else throw new BussinessException("Only employees, managers, and admins can access payroll attendance reports");

        LocalDate from = period.atDay(1);
        LocalDate to = period.atEndOfMonth();
        List<PayrollDto.EmployeeRow> rows = employees.stream().map(user -> {
            List<Attendance> records = attendance.findByUserIdAndDateBetweenOrderByDateDesc(user.getId(), from, to);
            int workDays = (int) records.stream().filter(record -> record.getStatus() == AttendanceStatus.PRESENT
                    || record.getStatus() == AttendanceStatus.LATE).count();
            int lateDays = (int) records.stream().filter(record -> record.getStatus() == AttendanceStatus.LATE).count();
            int absentDays = (int) records.stream().filter(record -> record.getStatus() == AttendanceStatus.ABSENT).count();
            double workedHours = records.stream().mapToDouble(record -> sessions.findByAttendanceIdOrderByCheckInTimeAsc(record.getId()).stream()
                    .filter(session -> session.getCheckOutTime() != null)
                    .mapToDouble(session -> java.time.Duration.between(session.getCheckInTime(), session.getCheckOutTime()).toMinutes() / 60.0)
                    .sum()).sum();
            List<LeaveRequest> leaves = leaveRequests.findByUserOrderByCreatedAtDesc(user).stream()
                    .filter(request -> request.getStatus() == LeaveRequestStatus.APPROVED
                            && !request.getFromDate().isAfter(to) && !request.getToDate().isBefore(from))
                    .toList();
            double annual = leaveDays(leaves, LeaveRequestType.ANNUAL, from, to);
            double unpaid = leaveDays(leaves, LeaveRequestType.UNPAID, from, to);
            double other = leaves.stream().filter(request -> request.getType() != LeaveRequestType.ANNUAL
                            && request.getType() != LeaveRequestType.UNPAID)
                    .mapToDouble(request -> leaveDays(request, from, to)).sum();
            return new PayrollDto.EmployeeRow(user.getId(), user.getUsername(), user.getFullName(),
                    user.getDepartment() == null ? "" : user.getDepartment().getName(), workDays, lateDays,
                    absentDays, Math.round(workedHours * 100.0) / 100.0, annual, unpaid, other,
                    workDays + annual);
        }).toList();
        PayrollPeriod closed = periods.findByYearAndMonth(year, month).orElse(null);
        return new PayrollDto.MonthlyReport(year, month, closed != null && closed.isClosed(),
            closed == null || !closed.isClosed() ? null : closed.getClosedAt(),
            closed == null || !closed.isClosed() ? null : closed.getClosedBy().getFullName(),
            closed == null ? null : closed.getReopenedAt(),
            closed == null || closed.getReopenedBy() == null ? null : closed.getReopenedBy().getFullName(),
            closed == null ? null : closed.getReopenReason(), rows);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] exportExcel(int year, int month) {
        PayrollDto.MonthlyReport report = monthly(year, month);
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Bang cong");
            String[] headers = {"Username", "Employee", "Department", "Công tính lương", "Công thực tế",
                    "Late days", "Absent days", "Worked hours", "Annual leave", "Unpaid leave", "Other leave"};
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setWrapText(true);
            Row header = sheet.createRow(0);
            for (int column = 0; column < headers.length; column++) {
                Cell cell = header.createCell(column);
                cell.setCellValue(headers[column]);
                cell.setCellStyle(headerStyle);
            }
            int rowIndex = 1;
            for (PayrollDto.EmployeeRow employee : report.employees()) {
                Row row = sheet.createRow(rowIndex++);
                row.createCell(0).setCellValue(employee.username());
                row.createCell(1).setCellValue(employee.fullName());
                row.createCell(2).setCellValue(employee.department());
                row.createCell(3).setCellValue(employee.payrollWorkDays());
                row.createCell(4).setCellValue(employee.workDays());
                row.createCell(5).setCellValue(employee.lateDays());
                row.createCell(6).setCellValue(employee.absentDays());
                row.createCell(7).setCellValue(employee.workedHours());
                row.createCell(8).setCellValue(employee.annualLeaveDays());
                row.createCell(9).setCellValue(employee.unpaidLeaveDays());
                row.createCell(10).setCellValue(employee.otherLeaveDays());
            }
            sheet.createFreezePane(0, 1);
            sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, Math.max(0, rowIndex - 1), 0, headers.length - 1));
            for (int column = 0; column < headers.length; column++) sheet.autoSizeColumn(column);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not generate payroll workbook", exception);
        }
    }

    @Override
    public PayrollDto.MonthlyReport close(int year, int month) {
        if (userService.currentUser().getRole() != Role.ADMIN) throw new BussinessException("Only admins can close payroll periods");
        YearMonth period = validatePeriod(year, month);
        if (!LocalDate.now().isAfter(period.atEndOfMonth())) throw new BussinessException("A payroll period can only be closed after the month ends");
        PayrollPeriod closed = periods.findByYearAndMonth(year, month).orElse(null);
        if (closed == null) {
            PayrollPeriod periodRecord = new PayrollPeriod();
            periodRecord.setYear(year);
            periodRecord.setMonth(month);
            periodRecord.setClosedBy(userService.currentUser());
            periods.save(periodRecord);
        } else if (!closed.isClosed()) {
            closed.setClosed(true);
            closed.setClosedAt(java.time.LocalDateTime.now());
            closed.setClosedBy(userService.currentUser());
            periods.save(closed);
        }
        return monthly(year, month);
    }

    @Override
    public PayrollDto.MonthlyReport reopen(int year, int month, String reason) {
        User current = userService.currentUser();
        if (current.getRole() != Role.ADMIN) throw new BussinessException("Only admins can reopen payroll periods");
        if (reason == null || reason.isBlank() || reason.length() > 500) throw new BussinessException("A reason is required to reopen a payroll period");
        validatePeriod(year, month);
        PayrollPeriod period = periods.findByYearAndMonth(year, month)
                .orElseThrow(() -> new BussinessException("Payroll period is not closed"));
        if (!period.isClosed()) throw new BussinessException("Payroll period is already open");
        period.setClosed(false);
        period.setReopenedAt(java.time.LocalDateTime.now());
        period.setReopenedBy(current);
        period.setReopenReason(reason.trim());
        periods.save(period);
        return monthly(year, month);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isClosed(YearMonth period) {
        return periods.findByYearAndMonth(period.getYear(), period.getMonthValue()).map(PayrollPeriod::isClosed).orElse(false);
    }

    private YearMonth validatePeriod(int year, int month) {
        if (year < 2000 || year > 2100 || month < 1 || month > 12) throw new BussinessException("Invalid payroll period");
        return YearMonth.of(year, month);
    }

    private double leaveDays(List<LeaveRequest> requests, LeaveRequestType type, LocalDate from, LocalDate to) {
        return requests.stream().filter(request -> request.getType() == type)
                .mapToDouble(request -> leaveDays(request, from, to)).sum();
    }

    private double leaveDays(LeaveRequest request, LocalDate from, LocalDate to) {
        LocalDate clippedFrom = max(request.getFromDate(), from);
        LocalDate clippedTo = min(request.getToDate(), to);
        LeaveDayPart fromPart = clippedFrom.equals(request.getFromDate())
                ? request.getFromDayPart() : LeaveDayPart.FULL_DAY;
        LeaveDayPart toPart = clippedTo.equals(request.getToDate())
                ? request.getToDayPart() : LeaveDayPart.FULL_DAY;
        return LeaveWorkdayCalculator.calculate(clippedFrom, clippedTo, fromPart, toPart);
    }

    private LocalDate max(LocalDate left, LocalDate right) { return left.isAfter(right) ? left : right; }
    private LocalDate min(LocalDate left, LocalDate right) { return left.isBefore(right) ? left : right; }

}