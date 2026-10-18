import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { AnnualLeaveBalance, Attendance, Department, DepartmentAttendanceSummary, LeaveRequest, PayrollMonthlyReport, User } from '../../core/models';
import { computeWorkStatus, isAttendanceAdjustmentDateAllowed, localDateIso, parseVietnamDateTime, WORK_STATUS_LABEL } from '../../core/work-status';
import { finalize } from 'rxjs';
import { Router } from '@angular/router';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent],
  templateUrl: './attendance.component.html',
  styleUrl: './attendance.component.scss',
})
export class AttendanceComponent implements OnInit {
  departments = signal<Department[]>([]);
  employees = signal<User[]>([]);
  attendance = signal<Attendance[]>([]);
  selfAttendance = signal<Attendance[]>([]);
  selectedLeaves = signal<LeaveRequest[]>([]);
  selfLeaves = signal<LeaveRequest[]>([]);
  selectedBalance = signal<AnnualLeaveBalance | null>(null);
  selfBalance = signal<AnnualLeaveBalance | null>(null);
  payrollReport = signal<PayrollMonthlyReport | null>(null);
  departmentAttendanceSummary = signal<DepartmentAttendanceSummary | null>(null);
  selectedAttendanceDate = localDateIso();
  selectedDepartmentId = signal<number | null>(null);
  selectedEmployeeId = signal<number | null>(null);
  search = signal('');
  attendanceView = signal<'ACTUAL' | 'PAYROLL' | 'CLOSED'>('ACTUAL');
  selfStatusLabel = signal('');
  month = Number(localDateIso().slice(5, 7));
  year = Number(localDateIso().slice(0, 4));
  loadingDepartments = signal(false);
  loadingEmployees = signal(false);
  loadingAttendance = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  leaveQuotaDraft = 15;
  reopenReason = '';
  payrollClosed = signal(false);
  payrollClosedBy = signal('');
  private employeeRequestId = 0;
  private attendanceRequestId = 0;

  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
    private router: Router,
  ) {}

  ngOnInit() {
    this.loadMyLeaveData();
    if (this.isManager()) {
      this.loadSelfAttendance();
      this.loadDepartmentAttendanceSummary();
    }
    if (this.isSelf()) {
      this.loadSelfAttendance();
      this.loadPayrollReport();
      return;
    }
    this.loadDepartmentsAndEmployees();
  }

  title() {
    return this.auth.hasRole(['ADMIN', 'HR']) ? 'Chấm công toàn công ty' : 'Chấm công phòng ban';
  }

  isSelf() {
    return this.auth.hasRole(['MEMBER', 'QA', 'VIEWER']);
  }

  localDate() {
    return localDateIso();
  }

  isManager() { return this.auth.hasRole(['PROJECT_MANAGER', 'TEAM_LEAD']); }
  isAdmin() { return this.auth.hasRole(['ADMIN', 'HR']); }
  isPayrollAdmin() { return this.auth.hasRole(['ADMIN']); }

  private loadMyLeaveData() {
    const year = Number(localDateIso().slice(0, 4));
    this.api.leaveRequests().subscribe({ next: (requests) => this.selfLeaves.set(requests) });
    this.api.annualLeaveBalance(year).subscribe({ next: (balance) => this.selfBalance.set(balance) });
  }

  private loadSelfAttendance() {
    this.api.myAttendance().subscribe({
      next: (records) => {
        this.selfAttendance.set(records);
        this.selfStatusLabel.set(WORK_STATUS_LABEL[computeWorkStatus(records.find((record) => record.date === this.localDate()))]);
      },
      error: () => this.errorMessage.set('Không thể tải lịch sử chấm công của bạn.'),
    });
  }

  loadDepartmentAttendanceSummary() {
    if (!this.isManager() || !this.selectedAttendanceDate) return;
    this.api.departmentAttendanceSummary(this.selectedAttendanceDate).subscribe({
      next: (summary) => this.departmentAttendanceSummary.set(summary),
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể tải biểu đồ chấm công phòng ban.'),
    });
  }

  departmentAttendanceRate() {
    const summary = this.departmentAttendanceSummary();
    return summary?.totalEmployees
      ? Math.round(((summary.present + summary.late) / summary.totalEmployees) * 100)
      : 0;
  }

  departmentAttendanceChart() {
    const summary = this.departmentAttendanceSummary();
    if (!summary || summary.totalEmployees === 0) return 'conic-gradient(#e7edf8 0 100%)';
    const total = summary.totalEmployees;
    const stops = [
      { value: summary.present, color: '#4354d8' },
      { value: summary.late, color: '#f2b640' },
      { value: summary.absent, color: '#ef6470' },
      { value: summary.leave, color: '#8f6fe8' },
    ];
    let position = 0;
    const segments = stops.map((segment) => {
      const start = position;
      position += (segment.value / total) * 100;
      return `${segment.color} ${start}% ${position}%`;
    });
    return `conic-gradient(${segments.join(', ')})`;
  }

  private loadDepartmentsAndEmployees() {
    this.loadingEmployees.set(true);
    const admin = this.isAdmin();
    const managerDepartmentId = this.auth.user()?.departmentId ?? null;
    if (admin) {
      this.loadingDepartments.set(true);
      this.api
        .departments()
        .pipe(finalize(() => this.loadingDepartments.set(false)))
        .subscribe({
          next: (departments) => this.departments.set(departments),
          error: () => this.errorMessage.set('Không thể tải danh sách phòng ban. Vui lòng thử lại.'),
        });
    } else {
      this.selectedDepartmentId.set(managerDepartmentId);
    }
    const peopleRequest = admin ? this.api.users() : this.api.departmentMembers();
    peopleRequest.pipe(finalize(() => this.loadingEmployees.set(false))).subscribe({
      next: (employees) => {
        this.employees.set(employees);
        this.selectFirstEmployee();
      },
      error: () => this.errorMessage.set('Không thể tải danh sách nhân sự. Vui lòng thử lại.'),
    });
  }

  departmentEmployees(departmentId: number | null) {
    const people =
      !this.isAdmin() || departmentId === null
        ? this.employees()
        : this.employees().filter((employee) => employee.departmentId === departmentId);
    const query = this.search().trim().toLowerCase();
    if (!query) return people;
    return people.filter(
      (employee) =>
        employee.fullName.toLowerCase().includes(query) ||
        employee.username.toLowerCase().includes(query) ||
        employee.email.toLowerCase().includes(query),
    );
  }

  departmentEmployeeCount(departmentId: number) {
    return this.employees().filter((employee) => employee.departmentId === departmentId).length;
  }

  departmentHeading() {
    return this.isAdmin() ? 'Phòng ban' : 'Nhân viên trong phòng ban của bạn';
  }

  onSearch(value: string) {
    this.search.set(value);
  }

  searchEmployees() {
    const requestId = ++this.employeeRequestId;
    this.loadingEmployees.set(true);
    this.errorMessage.set('');
    const request =
      this.auth.hasRole(['ADMIN', 'HR'])
        ? this.api.users(this.search(), this.selectedDepartmentId())
        : this.api.departmentMembers(this.search());
    request
      .pipe(
        finalize(() => {
          if (requestId === this.employeeRequestId) this.loadingEmployees.set(false);
        }),
      )
      .subscribe({
        next: (employees) => {
          if (requestId !== this.employeeRequestId) return;
          this.employees.set(employees);
          this.selectFirstEmployee();
        },
        error: () => {
          if (requestId === this.employeeRequestId) {
            this.errorMessage.set('Không thể tìm kiếm nhân sự. Vui lòng thử lại.');
          }
        },
      });
  }

  selectDepartment(id: number | null) {
    this.selectedDepartmentId.set(id);
    this.searchEmployees();
  }

  selectEmployee(id: number) {
    this.selectedEmployeeId.set(id);
    this.loadEmployeeAttendance();
  }

  private selectFirstEmployee() {
    const employee = this.departmentEmployees(this.selectedDepartmentId())[0];
    this.selectedEmployeeId.set(employee?.id ?? null);
    if (employee) this.loadEmployeeAttendance();
    else {
      this.attendanceRequestId++;
      this.loadingAttendance.set(false);
      this.attendance.set([]);
    }
  }

  loadEmployeeAttendance() {
    if (this.isSelf()) {
      this.loadPayrollReport();
      return;
    }
    const employeeId = this.selectedEmployeeId();
    if (!employeeId) return;
    const requestId = ++this.attendanceRequestId;
    this.loadingAttendance.set(true);
    this.errorMessage.set('');
    this.api
      .employeeAttendance(employeeId, this.month, this.year)
      .pipe(
        finalize(() => {
          if (requestId === this.attendanceRequestId) this.loadingAttendance.set(false);
        }),
      )
      .subscribe({
        next: (records) => {
          if (requestId !== this.attendanceRequestId) return;
          this.attendance.set(records);
        },
        error: (error) => {
          if (requestId !== this.attendanceRequestId) return;
          this.errorMessage.set(
            error?.error?.message || 'Không thể tải dữ liệu chấm công. Vui lòng thử lại.',
          );
        },
      });
    this.selectedLeaves.set([]);
    this.api.leaveRequestsForUser(employeeId).subscribe({
      next: (requests) => this.selectedLeaves.set(requests),
      error: () => this.errorMessage.set('Không thể tải đơn nghỉ của nhân viên.'),
    });
    this.api.annualLeaveBalanceForUser(employeeId, this.year).subscribe({
      next: (balance) => {
        this.selectedBalance.set(balance);
        this.leaveQuotaDraft = balance.entitledDays;
      },
      error: () => this.errorMessage.set('Không thể tải số dư phép năm.'),
    });
    this.loadPayrollReport();
  }

  private loadPayrollReport() {
    this.api.payrollMonthly(this.year, this.month).subscribe({
      next: (report) => {
        this.payrollReport.set(report);
        this.payrollClosed.set(report.closed);
        this.payrollClosedBy.set(report.closedBy ?? '');
        if (!report.closed && this.attendanceView() === 'CLOSED') this.attendanceView.set('ACTUAL');
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể tải tổng hợp công.'),
    });
  }

  payrollRows() {
    return this.payrollReport()?.employees ?? [];
  }

  selfPayrollRow() {
    const userId = this.auth.user()?.id;
    return this.payrollRows().find((row) => row.userId === userId) ?? this.payrollRows()[0];
  }

  retryAttendance() {
    if (this.isSelf()) {
      this.loadingAttendance.set(true);
      this.errorMessage.set('');
      this.api
        .myAttendance()
        .pipe(finalize(() => this.loadingAttendance.set(false)))
        .subscribe({
          next: (records) => {
            this.selfAttendance.set(records);
            this.selfStatusLabel.set(
              WORK_STATUS_LABEL[
                computeWorkStatus(records.find((record) => record.date === this.localDate()))
              ],
            );
          },
          error: () => this.errorMessage.set('Không thể tải lịch sử chấm công của bạn.'),
        });
      return;
    }
    this.loadEmployeeAttendance();
  }

  shiftMonth(offset: number) {
    const next = new Date(Date.UTC(this.year, this.month - 1 + offset, 1, 12));
    this.month = next.getMonth() + 1;
    this.year = next.getFullYear();
    this.loadEmployeeAttendance();
  }

  currentMonth() {
    const today = localDateIso();
    this.month = Number(today.slice(5, 7));
    this.year = Number(today.slice(0, 4));
    this.loadEmployeeAttendance();
  }

  monthLabel() {
    return new Intl.DateTimeFormat('vi-VN', { month: 'long', year: 'numeric' }).format(
      new Date(this.year, this.month - 1, 1),
    );
  }

  weekdayLabels() {
    return ['Thứ Hai', 'Thứ Ba', 'Thứ Tư', 'Thứ Năm', 'Thứ Sáu', 'Thứ Bảy', 'Chủ Nhật'];
  }

  calendarDays() {
    const first = new Date(Date.UTC(this.year, this.month - 1, 1, 12));
    const offset = (first.getUTCDay() + 6) % 7;
    return Array.from({ length: 42 }, (_, index) => {
      const date = localDateIso(new Date(Date.UTC(this.year, this.month - 1, index - offset + 1, 12)));
      return { date, day: Number(date.slice(-2)), inMonth: date.slice(0, 7) === `${this.year}-${String(this.month).padStart(2, '0')}` };
    });
  }

  attendanceForDate(date: string) {
    return (this.isSelf() ? this.selfAttendance() : this.attendance()).find((record) => record.date === date);
  }

  attendanceSessions(record: Attendance) {
    const sessions = record.sessions?.length ? record.sessions : record.checkInTime
      ? [{ checkInTime: record.checkInTime, checkOutTime: record.checkOutTime }]
      : [];
    return sessions.filter((session, index, all) => all.findIndex((candidate) =>
      candidate.checkInTime === session.checkInTime && candidate.checkOutTime === session.checkOutTime) === index);
  }

  attendanceStatusLabel(status: string) {
    return ({ PRESENT: 'Có mặt', LATE: 'Đi muộn', LEAVE: 'Nghỉ phép', ABSENT: 'Vắng mặt' } as Record<string, string>)[status] ?? status;
  }

  leaveForDate(date: string) {
    return (this.isSelf() ? this.selfLeaves() : this.selectedLeaves()).find((request) =>
      request.status === 'APPROVED' && request.fromDate <= date && request.toDate >= date);
  }

  leaveTypeLabel(type: LeaveRequest['type']) {
    return ({ ANNUAL: 'Phép năm', UNPAID: 'Không lương', MATERNITY: 'Thai sản' } as Record<LeaveRequest['type'], string>)[type];
  }

  selectedEmployee() {
    return this.employees().find((employee) => employee.id === this.selectedEmployeeId());
  }

  summary() {
    const prefix = `${this.year}-${String(this.month).padStart(2, '0')}`;
    const records = this.attendance().filter((record) => record.date.startsWith(prefix));
    const balance = this.selectedBalance();
    const actualWorkDays = records.filter((record) => record.status === 'PRESENT' || record.status === 'LATE').length;
    const annualLeaveDays = this.leaveWorkdays(
      this.selectedLeaves().filter((request) => request.type === 'ANNUAL' && request.status === 'APPROVED'),
      prefix,
    );
    return {
      workingDays: actualWorkDays,
      actualWorkDays,
      annualLeaveDays,
      payrollWorkDays: actualWorkDays + annualLeaveDays,
      hours: this.recordedHours(records),
      annualLeaveRemaining: Math.max(0, (balance?.remainingDays ?? 0) - (balance?.pendingDays ?? 0)),
      unpaidDays: this.leaveWorkdays(this.selectedLeaves().filter((request) => request.type === 'UNPAID' && request.status === 'APPROVED'), prefix),
    };
  }

  selfSummary() {
    const prefix = `${this.year}-${String(this.month).padStart(2, '0')}`;
    const records = this.selfAttendance().filter((record) => record.date.startsWith(prefix));
    const balance = this.selfBalance();
    const actualWorkDays = records.filter((record) => record.status === 'PRESENT' || record.status === 'LATE').length;
    const annualLeaveDays = this.leaveWorkdays(
      this.selfLeaves().filter((request) => request.type === 'ANNUAL' && request.status === 'APPROVED'),
      prefix,
    );
    return {
      workingDays: actualWorkDays,
      actualWorkDays,
      annualLeaveDays,
      payrollWorkDays: actualWorkDays + annualLeaveDays,
      hours: this.recordedHours(records),
      annualLeaveRemaining: Math.max(0, (balance?.remainingDays ?? 0) - (balance?.pendingDays ?? 0)),
      unpaidDays: this.leaveWorkdays(this.selfLeaves().filter((request) => request.type === 'UNPAID' && request.status === 'APPROVED'), prefix),
    };
  }

  private recordedHours(records: Attendance[]) {
    const hours = records.reduce((total, record) => total + this.attendanceSessions(record).reduce((dayTotal, session) =>
      dayTotal + (session.checkOutTime
        ? (parseVietnamDateTime(session.checkOutTime).getTime() - parseVietnamDateTime(session.checkInTime).getTime()) / 3600000
        : 0), 0), 0);
    return hours.toFixed(1);
  }

  private leaveWorkdays(requests: LeaveRequest[], monthPrefix: string) {
    const monthStart = `${monthPrefix}-01`;
    const monthEnd = localDateIso(new Date(Date.UTC(this.year, this.month, 0, 12)));
    let days = 0;
    for (const request of requests) {
      const clippedFrom = request.fromDate > monthStart ? request.fromDate : monthStart;
      const clippedTo = request.toDate < monthEnd ? request.toDate : monthEnd;
      let date = new Date(`${clippedFrom}T12:00:00Z`);
      const end = new Date(`${clippedTo}T12:00:00Z`);
      let requestDays = 0;
      while (date <= end) {
        if (date.getUTCDay() !== 0 && date.getUTCDay() !== 6) requestDays++;
        date.setUTCDate(date.getUTCDate() + 1);
      }
      if (requestDays > 0) {
        if (clippedFrom === request.fromDate && request.fromDayPart === 'AFTERNOON' && this.isWeekday(clippedFrom)) requestDays -= 0.5;
        if (clippedTo === request.toDate && request.toDayPart === 'MORNING' && this.isWeekday(clippedTo)) requestDays -= 0.5;
      }
      days += requestDays;
    }
    return days;
  }

  private isWeekday(date: string) {
    const weekday = new Date(`${date}T12:00:00Z`).getUTCDay();
    return weekday !== 0 && weekday !== 6;
  }

  time(value?: string) {
    return value
      ? new Date(value).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
      : '—';
  }
  selfToday() {
    return this.selfAttendance().find((record) => record.date === this.localDate());
  }
  canCheckIn() {
    if (this.auth.hasRole(['VIEWER'])) return false;
    const today = this.selfToday();
    return !today?.checkInTime || !!today.checkOutTime;
  }
  canCheckOut() {
    if (this.auth.hasRole(['VIEWER'])) return false;
    const today = this.selfToday();
    return !!today?.checkInTime && !today.checkOutTime;
  }
  canCheckAttendance() {
    return !this.auth.hasRole(['VIEWER']);
  }
  currentCheckInTime() {
    const sessions = this.selfToday()?.sessions ?? [];
    for (let index = sessions.length - 1; index >= 0; index--) {
      if (!sessions[index].checkOutTime) return sessions[index].checkInTime;
    }
    return this.selfToday()?.checkInTime;
  }
  startAdjustment(record: Attendance, session: NonNullable<Attendance['sessions']>[number]) {
    if (!session.id || !this.canAdjustAttendance(record.date)) return;
    this.router.navigate(['/notifications'], {
      queryParams: {
        adjustmentSessionId: session.id,
        adjustmentUserId: record.userId,
        adjustmentDate: record.date,
        adjustmentCheckIn: session.checkInTime,
        adjustmentCheckOut: session.checkOutTime ?? `${record.date}T17:00:00`,
      },
    });
  }
  canAdjustAttendance(date: string) {
    return !this.isAdmin() && !this.auth.hasRole(['VIEWER'])
      && isAttendanceAdjustmentDateAllowed(date, this.localDate());
  }
  selectAttendanceView(view: 'ACTUAL' | 'PAYROLL' | 'CLOSED') {
    if (view === 'CLOSED' && !this.payrollClosed()) return;
    this.attendanceView.set(view);
  }
  saveLeaveQuota() {
    const employeeId = this.selectedEmployeeId();
    if (!employeeId || !this.isPayrollAdmin()) return;
    this.api.setAnnualLeaveBalance(employeeId, this.year, this.leaveQuotaDraft).subscribe({
      next: (balance) => {
        this.selectedBalance.set(balance);
        this.successMessage.set('Đã cập nhật quota phép năm.');
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể cập nhật quota phép năm.'),
    });
  }
  canClosePayroll() {
    const end = new Date(Date.UTC(this.year, this.month, 0, 12));
    return this.isPayrollAdmin() && end < new Date(`${this.localDate()}T12:00:00Z`) && !this.payrollClosed();
  }
  exportPayroll() {
    this.api.payrollExcel(this.year, this.month).subscribe({
      next: (blob) => {
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `payroll-inputs-${this.year}-${String(this.month).padStart(2, '0')}.xlsx`;
        link.click();
        URL.revokeObjectURL(url);
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể xuất bảng công.'),
    });
  }
  closePayroll() {
    if (!this.canClosePayroll()) return;
    this.api.closePayrollPeriod(this.year, this.month).subscribe({
      next: (report) => {
        this.payrollClosed.set(report.closed);
        this.payrollClosedBy.set(report.closedBy ?? '');
        this.attendanceView.set('CLOSED');
        this.successMessage.set('Kỳ công đã chốt; yêu cầu chỉnh sửa giờ cho kỳ này sẽ bị khóa.');
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể chốt kỳ công.'),
    });
  }
  reopenPayroll() {
    if (!this.isPayrollAdmin() || !this.payrollClosed() || !this.reopenReason.trim()) return;
    this.api.reopenPayrollPeriod(this.year, this.month, this.reopenReason.trim()).subscribe({
      next: (report) => {
        this.payrollClosed.set(report.closed);
        this.payrollClosedBy.set(report.closedBy ?? '');
        if (this.attendanceView() === 'CLOSED') this.attendanceView.set('PAYROLL');
        this.reopenReason = '';
        this.successMessage.set('Đã mở lại kỳ công. Lý do đã được lưu trong lịch sử.');
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể mở lại kỳ công.'),
    });
  }
  checkIn() {
    this.api.checkIn().subscribe({
      next: (record) => {
        this.selfAttendance.set([
          record,
          ...this.selfAttendance().filter((item) => item.date !== record.date),
        ]);
        this.selfStatusLabel.set(WORK_STATUS_LABEL[computeWorkStatus(record)]);
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể check-in'),
    });
  }
  checkOut() {
    this.api.checkOut().subscribe({
      next: (record) => {
        this.selfAttendance.set([
          record,
          ...this.selfAttendance().filter((item) => item.date !== record.date),
        ]);
        this.selfStatusLabel.set(WORK_STATUS_LABEL[computeWorkStatus(record)]);
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể check-out'),
    });
  }
}
