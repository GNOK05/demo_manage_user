import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { Department, Role, User } from '../../core/models';
import { DepartmentStatusGroup, groupPeopleByDepartmentWithStatus } from '../../core/work-status';
import { finalize } from 'rxjs';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent],
  templateUrl: './people.component.html',
  styleUrl: './people.component.scss',
})
export class PeopleComponent implements OnInit {
  groups = signal<DepartmentStatusGroup[]>([]);
  people = signal<User[]>([]);
  departments = signal<Department[]>([]);
  selectedDepartmentId = signal<number | null>(null);
  search = signal('');
  loading = signal(false);
  errorMessage = signal('');
  attendanceRecords = signal<import('../../core/models').Attendance[]>([]);
  modal = signal<'create' | 'edit' | 'delete' | null>(null);
  draftPassword = '';
  readonly roles: Role[] = ['ADMIN', 'HR', 'PROJECT_MANAGER', 'TEAM_LEAD', 'MEMBER', 'QA', 'VIEWER'];
  selectedUser: User | null = null;
  draft: User = {
    id: 0,
    username: '',
    fullName: '',
    email: '',
    role: 'MEMBER',
    jobTitle: 'Nhân viên',
    departmentName: 'Phòng ban',
  };

  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
  ) {}

  ngOnInit() {
    this.load();
  }

  load() {
    this.loading.set(true);
    this.errorMessage.set('');
    const canViewAll = this.auth.hasRole(['ADMIN', 'HR']);
    if (!canViewAll) this.selectedDepartmentId.set(this.auth.user()?.departmentId ?? null);
    const peopleRequest = canViewAll
      ? this.api.users(this.search(), this.selectedDepartmentId())
      : this.api.departmentMembers(this.search());
    const attendanceRequest = canViewAll ? this.api.allAttendance() : this.api.departmentAttendance();
    if (canViewAll) {
      this.api.departments().subscribe({
        next: (departments) => this.departments.set(departments),
        error: () => this.errorMessage.set('Không thể tải danh sách phòng ban.'),
      });
    }
    peopleRequest.pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (people) => {
        this.people.set(people);
        this.groups.set(groupPeopleByDepartmentWithStatus(people, this.attendanceRecords()));
      },
      error: () => this.errorMessage.set('Không thể tải dữ liệu nhân sự. Vui lòng thử lại.'),
    });
    attendanceRequest.subscribe({
      next: (attendance) => {
        this.attendanceRecords.set(attendance);
        this.groups.set(groupPeopleByDepartmentWithStatus(this.people(), attendance));
      },
      error: () => this.errorMessage.set('Không thể tải trạng thái chấm công. Vui lòng thử lại.'),
    });
  }

  searchPeople() {
    this.load();
  }

  selectDepartment(id: number | null) {
    this.selectedDepartmentId.set(id);
    this.load();
  }

  visiblePeople(group: DepartmentStatusGroup) {
    return group.people;
  }

  editUser(user: User) {
    this.selectedUser = user;
    this.draft = { ...user };
    this.modal.set('edit');
  }

  openCreate() {
    this.resetForm();
    this.modal.set('create');
  }

  saveUser() {
    const payload = {
      fullName: this.draft.fullName,
      email: this.draft.email,
      phone: this.draft.phone,
      role: this.draft.role,
      jobTitle: this.draft.jobTitle,
      departmentId: this.draft.departmentId,
      ...(this.draftPassword ? { password: this.draftPassword } : {}),
    };

    if (this.selectedUser) {
      this.loading.set(true);
      this.errorMessage.set('');
      this.api
        .updateUser(this.selectedUser.id, payload)
        .pipe(finalize(() => this.loading.set(false)))
        .subscribe({
          next: () => {
            this.resetForm();
            this.load();
          },
          error: (error) => {
            this.errorMessage.set(error?.error?.message || 'Không thể cập nhật nhân sự.');
          },
        });
      return;
    }

    this.loading.set(true);
    this.errorMessage.set('');
    this.api
      .createUser({ ...payload, username: this.draft.username, password: this.draftPassword })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: () => {
          this.resetForm();
          this.load();
        },
        error: (error) => {
          this.errorMessage.set(error?.error?.message || 'Không thể tạo nhân sự.');
        },
      });
  }

  deleteUser(id: number) {
    this.selectedUser = this.people().find((user) => user.id === id) || null;
    this.modal.set('delete');
  }

  confirmDelete() {
    const id = this.selectedUser?.id;
    if (!id) return;
    this.loading.set(true);
    this.errorMessage.set('');
    this.api
      .deleteUser(id)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: () => {
          this.resetForm();
          this.load();
        },
        error: (error) => {
          this.errorMessage.set(error?.error?.message || 'Không thể xoá nhân sự.');
        },
      });
  }

  positionOptions() {
    return Array.from(
      new Set(
        this.people()
          .map((person) => person.jobTitle)
          .filter(Boolean),
      ),
    ) as string[];
  }

  canDelete(user: User) {
    const current = this.auth.user();
    if (!current || !this.auth.hasRole(['ADMIN', 'HR']) || current.id === user.id) return false;
    const rank = (role: Role) => ({ ADMIN: 7, HR: 6, PROJECT_MANAGER: 5, TEAM_LEAD: 4, QA: 3, MEMBER: 2, VIEWER: 1 })[role];
    return rank(current.role) > rank(user.role);
  }

  canEditUser(user: User) {
    if (!this.auth.hasRole(['ADMIN', 'HR'])) return false;
    return !(this.auth.hasRole(['HR']) && ['ADMIN', 'HR'].includes(user.role));
  }

  canManageDepartments() {
    return this.auth.hasRole(['ADMIN', 'HR']);
  }

  availableRoles(): Role[] {
    return this.auth.hasRole(['HR'])
      ? this.roles.filter((role) => role !== 'ADMIN' && role !== 'HR')
      : this.roles;
  }

  resetForm() {
    this.selectedUser = null;
    this.modal.set(null);
    this.draftPassword = '';
    this.draft = {
      id: 0,
      username: '',
      fullName: '',
      email: '',
      role: 'MEMBER',
      jobTitle: 'Nhân viên',
      departmentName: 'Phòng ban',
    };
  }
}
