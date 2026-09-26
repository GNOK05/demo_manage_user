import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { Attendance, Department, User } from '../../core/models';
import { computeWorkStatus, localDateIso, WORK_STATUS_LABEL } from '../../core/work-status';
import { finalize } from 'rxjs';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, DatePipe],
  templateUrl: './attendance.component.html',
  styleUrl: './attendance.component.scss',
})
export class AttendanceComponent implements OnInit {
  departments = signal<Department[]>([]);
  employees = signal<User[]>([]);
  attendance = signal<Attendance[]>([]);
  selfAttendance = signal<Attendance[]>([]);
  selectedDepartmentId = signal<number | null>(null);
  selectedEmployeeId = signal<number | null>(null);
  search = signal('');
  statusFilter = signal('ALL');
  selfStatusLabel = signal('');
  month = new Date().getMonth() + 1;
  year = new Date().getFullYear();
  page = 1;
  pageSize = 10;
  loadingDepartments = signal(false);
  loadingEmployees = signal(false);
  loadingAttendance = signal(false);
  errorMessage = signal('');
  private employeeRequestId = 0;
  private attendanceRequestId = 0;

  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
  ) {}

  ngOnInit() {
    if (this.isManager()) this.loadSelfAttendance();
    if (this.isSelf()) {
      this.loadSelfAttendance();
      return;
    }
    this.loadDepartmentsAndEmployees();
  }

  title() {
    return this.auth.user()?.role === 'ADMIN' ? 'Chấm công toàn công ty' : 'Chấm công phòng ban';
  }

  isSelf() {
    return this.auth.user()?.role === 'EMPLOYEE';
  }

  isManager() {
    return this.auth.user()?.role === 'MANAGER';
  }

  private loadSelfAttendance() {
    this.api.myAttendance().subscribe({
      next: (records) => this.updateSelfAttendance(records),
      error: () => this.errorMessage.set('Không thể tải lịch sử chấm công của bạn.'),
    });
  }

  private updateSelfAttendance(records: Attendance[]) {
    this.selfAttendance.set(records);
    this.selfStatusLabel.set(
      WORK_STATUS_LABEL[
        computeWorkStatus(records.find((record) => record.date === this.localDate()))
      ],
    );
  }

  private localDate() {
    return localDateIso();
  }

  private loadDepartmentsAndEmployees() {
    this.loadingDepartments.set(true);
    this.loadingEmployees.set(true);
    const peopleRequest =
      this.auth.user()?.role === 'ADMIN' ? this.api.users() : this.api.departmentMembers();
    this.api
      .departments()
      .pipe(finalize(() => this.loadingDepartments.set(false)))
      .subscribe({
        next: (departments) => this.departments.set(departments),
        error: () => this.errorMessage.set('Không thể tải danh sách phòng ban. Vui lòng thử lại.'),
      });
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
      departmentId === null
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

  onSearch(value: string) {
    this.search.set(value);
  }

  searchEmployees() {
    const requestId = ++this.employeeRequestId;
    this.loadingEmployees.set(true);
    this.errorMessage.set('');
    const request =
      this.auth.user()?.role === 'ADMIN'
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
    this.page = 1;
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
    const employeeId = this.selectedEmployeeId();
    if (!employeeId || this.isSelf()) return;
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
          this.page = 1;
        },
        error: (error) => {
          if (requestId !== this.attendanceRequestId) return;
          this.errorMessage.set(
            error?.error?.message || 'Không thể tải dữ liệu chấm công. Vui lòng thử lại.',
          );
        },
      });
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
    const next = new Date(this.year, this.month - 1 + offset, 1);
    this.month = next.getMonth() + 1;
    this.year = next.getFullYear();
    this.loadEmployeeAttendance();
  }

  currentMonth() {
    const now = new Date();
    this.month = now.getMonth() + 1;
    this.year = now.getFullYear();
    this.loadEmployeeAttendance();
  }

  onPageSizeChange(value: number | string) {
    this.pageSize = Number(value);
    this.page = 1;
  }

  monthLabel() {
    return new Intl.DateTimeFormat('vi-VN', { month: 'long', year: 'numeric' }).format(
      new Date(this.year, this.month - 1, 1),
    );
  }

  selectedEmployee() {
    return this.employees().find((employee) => employee.id === this.selectedEmployeeId());
  }

  visibleAttendance() {
    const records =
      this.statusFilter() === 'ALL'
        ? this.attendance()
        : this.attendance().filter((record) => record.status === this.statusFilter());
    const start = (this.page - 1) * this.pageSize;
    return records.slice(start, start + this.pageSize);
  }

  filteredAttendanceCount() {
    return this.statusFilter() === 'ALL'
      ? this.attendance().length
      : this.attendance().filter((record) => record.status === this.statusFilter()).length;
  }

  totalPages() {
    return Math.max(1, Math.ceil(this.filteredAttendanceCount() / this.pageSize));
  }
  pages() {
    return Array.from({ length: this.totalPages() }, (_, index) => index + 1);
  }
  setPage(page: number) {
    this.page = Math.min(Math.max(1, page), this.totalPages());
  }

  summary() {
    const records = this.attendance();
    const workingDays = records.filter(
      (record) => record.status === 'PRESENT' || record.status === 'LATE',
    ).length;
    const leaveDays = records.filter((record) => record.status === 'LEAVE').length;
    const lateDays = records.filter((record) => record.status === 'LATE').length;
    const hours = records.reduce(
      (total, record) =>
        total +
        (record.checkInTime && record.checkOutTime
          ? (new Date(record.checkOutTime).getTime() - new Date(record.checkInTime).getTime()) /
            3600000
          : 0),
      0,
    );
    return { workingDays, leaveDays, lateDays, hours: hours.toFixed(1) };
  }

  workStatus(record: Attendance) {
    return WORK_STATUS_LABEL[computeWorkStatus(record)];
  }
  dayName(date: string) {
    return new Intl.DateTimeFormat('vi-VN', { weekday: 'short' }).format(
      new Date(`${date}T00:00:00`),
    );
  }
  time(value?: string) {
    return value
      ? new Date(value).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })
      : '—';
  }
  duration(record: Attendance) {
    return record.checkInTime && record.checkOutTime
      ? `${((new Date(record.checkOutTime).getTime() - new Date(record.checkInTime).getTime()) / 3600000).toFixed(1)}h`
      : '—';
  }

  selfToday() {
    return this.selfAttendance().find((record) => record.date === this.localDate());
  }
  checkIn() {
    this.api.checkIn().subscribe({
      next: (record) => {
        this.selfAttendance.set([
          record,
          ...this.selfAttendance().filter((item) => item.date !== record.date),
        ]);
        this.selfStatusLabel.set(WORK_STATUS_LABEL[computeWorkStatus(record)]);
        this.errorMessage.set('');
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
        this.errorMessage.set('');
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể check-out'),
    });
  }
}
