import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { Attendance, Department, Project, Task, User } from '../../core/models';
import { Observable, finalize } from 'rxjs';
import { RouterLink } from '@angular/router';
@Component({
  standalone: true,
  imports: [CommonModule, SidebarComponent, RouterLink],
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
  loading = signal<Record<string, boolean>>({});
  sectionErrors = signal<Record<string, string>>({});
  notice = signal('');

  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
  ) {}

  ngOnInit() {
    this.loadDepartments();
    this.loadEmployees();
    this.loadProjects();
    this.loadTasks();
    this.loadAttendance();
    if (this.auth.hasRole(['ADMIN', 'MANAGER'])) this.loadPendingLeave();
  }

  private request<T>(
    section: string,
    request: Observable<T>,
    set: (value: T) => void,
    message: string,
  ) {
    this.setLoading(section, true);
    this.clearError(section);
    request.pipe(finalize(() => this.setLoading(section, false))).subscribe({
      next: (value) => set(value),
      error: () => this.setError(section, message),
    });
  }

  private setLoading(section: string, value: boolean) {
    this.loading.update((state) => ({ ...state, [section]: value }));
  }

  private setError(section: string, message: string) {
    this.sectionErrors.update((errors) => ({ ...errors, [section]: message }));
  }

  private clearError(section: string) {
    this.sectionErrors.update((errors) => {
      const next = { ...errors };
      delete next[section];
      return next;
    });
  }

  loadDepartments() {
    this.request(
      'departments',
      this.api.departments(),
      (value) => this.departments.set(value),
      'Không thể tải phòng ban.',
    );
  }

  loadEmployees() {
    const role = this.auth.user()?.role;
    if (role === 'EMPLOYEE') return;
    const request = role === 'ADMIN' ? this.api.users() : this.api.departmentMembers();
    this.request(
      'employees',
      request,
      (value) => this.employees.set(value),
      'Không thể tải nhân sự.',
    );
  }

  loadProjects() {
    this.request(
      'projects',
      this.api.projects(),
      (value) => this.projects.set(value),
      'Không thể tải thống kê dự án.',
    );
  }

  loadTasks() {
    this.request(
      'tasks',
      this.api.tasks(),
      (value) => this.tasks.set(value),
      'Không thể tải thống kê công việc.',
    );
  }

  loadAttendance() {
    const role = this.auth.user()?.role;
    const request =
      role === 'ADMIN'
        ? this.api.allAttendance()
        : role === 'MANAGER'
          ? this.api.departmentAttendance()
          : this.api.myAttendance();
    this.request(
      'attendance',
      request,
      (value) => this.attendance.set(value),
      'Không thể tải tổng hợp chấm công.',
    );
  }

  loadPendingLeave() {
    this.request(
      'leave',
      this.api.pendingLeaveRequests(),
      (value) => this.pendingLeaveCount.set(value.length),
      'Không thể tải đơn nghỉ đang chờ.',
    );
  }

  retry(section: string) {
    const loaders: Record<string, () => void> = {
      departments: () => this.loadDepartments(),
      employees: () => this.loadEmployees(),
      projects: () => this.loadProjects(),
      tasks: () => this.loadTasks(),
      attendance: () => this.loadAttendance(),
      leave: () => this.loadPendingLeave(),
    };
    loaders[section]?.();
  }

  isLoading(section: string) {
    return !!this.loading()[section];
  }
  error(section: string) {
    return this.sectionErrors()[section];
  }

  todayAttendance(status: string) {
    const today = new Date().toISOString().slice(0, 10);
    return this.attendance().filter((record) => record.date === today && record.status === status)
      .length;
  }

  departmentEmployeeCount(department: Department) {
    return this.employees().filter((employee) => employee.departmentId === department.id).length;
  }

  projectStatusCount(status: string) {
    return this.projects().filter((project) => project.status === status).length;
  }

  pendingTasks() {
    return this.tasks().filter((task) => task.status !== 'DONE').length;
  }

  done(): number {
    return this.tasks().filter((x) => x.status === 'DONE').length;
  }

  completionRate(): number {
    if (!this.tasks().length) return 0;
    return Math.round((this.done() / this.tasks().length) * 100);
  }

  topProjects(): Project[] {
    return this.projects().slice(0, 4);
  }

  checkIn() {
    this.api.checkIn().subscribe({
      next: () => this.notice.set('Check-in thành công. Chúc bạn làm việc hiệu quả.'),
      error: (e) => this.notice.set(e.error?.message || 'Không thể check-in lúc này.'),
    });
  }

  checkOut() {
    this.api.checkOut().subscribe({
      next: () => this.notice.set('Check-out thành công. Hẹn gặp lại ngày mai.'),
      error: (e) => this.notice.set(e.error?.message || 'Không thể check-out lúc này.'),
    });
  }
}
