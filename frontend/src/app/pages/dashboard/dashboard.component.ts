import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { Attendance, DashboardSummary, Department, Project, Task, User } from '../../core/models';
import { Observable } from 'rxjs';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { localDateIso } from '../../core/work-status';
@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, RouterLink],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  projects = signal<Project[]>([]);
  tasks = signal<Task[]>([]);
  departments = signal<Department[]>([]);
  employees = signal<User[]>([]);
  attendance = signal<Attendance[]>([]);
  pendingLeaveCount = signal(0);
  notice = signal('');
  summary = signal<DashboardSummary | null>(null);
  selectedDate = signal(localDateIso());
  showAllDepartments = signal(false);
  readonly maxAttendanceDate = localDateIso();

  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
  ) {}

  ngOnInit() {
    this.loadSummary();
    if (this.canViewPeople()) this.loadDepartments();
    this.loadEmployees();
    this.loadProjects();
    this.loadTasks();
    this.loadAttendance();
    if (this.auth.hasRole(['ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD'])) this.loadPendingLeave();
  }

  loadSummary() {
    this.request(
      this.api.dashboardSummary(),
      (value) => this.summary.set(value),
    );
  }

  private request<T>(
    request: Observable<T>,
    set: (value: T) => void,
  ) {
    request.subscribe({
      next: (value) => set(value),
      error: () => undefined,
    });
  }

  loadDepartments() {
    this.request(
      this.api.departments(),
      (value) => this.departments.set(value),
    );
  }

  canViewPeople() {
    return this.auth.hasRole(['ADMIN', 'HR', 'PROJECT_MANAGER', 'TEAM_LEAD']);
  }

  canCheckInOut() {
    return this.auth.hasRole(['MEMBER', 'QA', 'PROJECT_MANAGER', 'TEAM_LEAD']);
  }

  loadEmployees() {
    const role = this.auth.user()?.role;
    if (['MEMBER', 'QA', 'VIEWER'].includes(role || '')) return;
    const request = role === 'ADMIN' || role === 'HR' ? this.api.users() : this.api.departmentMembers();
    this.request(
      request,
      (value) => this.employees.set(value),
    );
  }

  loadProjects() {
    this.request(
      this.api.projects(),
      (value) => this.projects.set(value),
    );
  }

  loadTasks() {
    this.request(
      this.api.tasks(),
      (value) => this.tasks.set(value),
    );
  }

  loadAttendance() {
    this.request(
      this.api.attendanceByDate(this.selectedDate()),
      (value) => this.attendance.set(value),
    );
  }

  loadPendingLeave() {
    this.request(
      this.api.pendingLeaveRequests(),
      (value) => this.pendingLeaveCount.set(value.length),
    );
  }

  todayAttendance(status: string) {
    const summaryCount = this.isToday() ? this.summary()?.attendanceToday[status] : undefined;
    if (summaryCount !== undefined) return summaryCount;
    if (status === 'ABSENT') {
      return Math.max(0, this.attendanceTotal() - this.rawTodayAttendance('PRESENT')
        - this.rawTodayAttendance('LATE') - this.rawTodayAttendance('LEAVE'));
    }
    return this.rawTodayAttendance(status);
  }

  departmentEmployeeCount(department: Department) {
    return this.employees().filter((employee) => employee.departmentId === department.id).length;
  }

  visibleDepartments() {
    return this.departments()
      .filter((department) => this.departmentEmployeeCount(department) > 0)
      .sort((left, right) => this.departmentEmployeeCount(right) - this.departmentEmployeeCount(left));
  }

  displayedDepartments() {
    const departments = this.visibleDepartments();
    return this.showAllDepartments() ? departments : departments.slice(0, 5);
  }

  toggleDepartments() {
    this.showAllDepartments.update((value) => !value);
  }

  departmentAttendanceCount(department: Department, status: string) {
    const employeeIds = new Set(this.employees()
      .filter((employee) => employee.departmentId === department.id)
      .map((employee) => employee.id));
    if (status === 'ABSENT') {
      return Math.max(0, employeeIds.size
        - this.departmentRecordedCount(employeeIds, 'PRESENT')
        - this.departmentRecordedCount(employeeIds, 'LATE')
        - this.departmentRecordedCount(employeeIds, 'LEAVE'));
    }
    return this.departmentRecordedCount(employeeIds, status);
  }

  departmentAttendancePercent(department: Department, status: string) {
    const total = this.departmentEmployeeCount(department);
    return total ? this.departmentAttendanceCount(department, status) / total * 100 : 0;
  }

  attendanceRingBackground() {
    const total = this.attendanceTotal() || 1;
    const present = this.todayAttendance('PRESENT') / total * 100;
    const late = present + this.todayAttendance('LATE') / total * 100;
    const leave = late + this.todayAttendance('LEAVE') / total * 100;
    return `conic-gradient(#20a47c 0 ${present}%, #e4ae53 ${present}% ${late}%, #a4acba ${late}% ${leave}%, #d95b68 ${leave}% 100%)`;
  }

  isToday() {
    return this.selectedDate() === localDateIso();
  }

  attendanceTitle() {
    return this.isToday() ? 'Chấm công hôm nay' : `Chấm công ngày ${this.selectedDateLabel()}`;
  }

  selectedDateLabel() {
    const [year, month, day] = this.selectedDate().split('-');
    return `${day}/${month}/${year}`;
  }

  selectedDateLongLabel() {
    return new Intl.DateTimeFormat('vi-VN', {
      weekday: 'long', day: '2-digit', month: 'long', year: 'numeric',
    }).format(new Date(`${this.selectedDate()}T00:00:00`));
  }

  changeAttendanceDate(value: string) {
    if (value && value <= localDateIso()) {
      this.selectedDate.set(value);
      this.loadAttendance();
    }
  }

  previousAttendanceDay() {
    const date = new Date(`${this.selectedDate()}T00:00:00`);
    date.setDate(date.getDate() - 1);
    this.selectedDate.set(`${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`);
    this.loadAttendance();
  }

  showTodayAttendance() {
    this.selectedDate.set(localDateIso());
    this.loadAttendance();
  }

  projectStatusCount(status: string) {
    return this.projects().filter((project) => project.status === status).length;
  }

  pendingTasks() {
    if (this.summary()) return this.summary()!.pendingTasks;
    return this.tasks().filter((task) => task.status !== 'DONE').length;
  }

  done(): number {
    if (this.summary()) return this.summary()!.completedTasks;
    return this.tasks().filter((x) => x.status === 'DONE').length;
  }

  attendanceRate(): number {
    const total = this.summary()?.totalEmployees ?? this.employees().length;
    return total ? Math.round((this.todayAttendance('PRESENT') / total) * 100) : 0;
  }

  attendanceTotal(): number {
    return this.summary()?.totalEmployees ?? this.employees().length;
  }

  private todayKey() {
    return this.selectedDate();
  }

  private rawTodayAttendance(status: string) {
    const today = this.todayKey();
    return this.attendance().filter((record) => record.date === today && record.status === status).length;
  }

  private departmentRecordedCount(employeeIds: Set<number>, status: string) {
    const today = this.todayKey();
    return this.attendance().filter((record) => record.date === today
      && record.status === status && employeeIds.has(record.userId)).length;
  }

  topProjects(): Project[] {
    return this.projects().slice(0, 4);
  }

  checkIn() {
    if (!this.canCheckInOut()) return;
    this.api.checkIn().subscribe({
      next: () => this.notice.set('Check-in thành công. Chúc bạn làm việc hiệu quả.'),
      error: (e) => this.notice.set(e.error?.message || 'Không thể check-in lúc này.'),
    });
  }

  checkOut() {
    if (!this.canCheckInOut()) return;
    this.api.checkOut().subscribe({
      next: () => this.notice.set('Check-out thành công. Hẹn gặp lại ngày mai.'),
      error: (e) => this.notice.set(e.error?.message || 'Không thể check-out lúc này.'),
    });
  }
}
