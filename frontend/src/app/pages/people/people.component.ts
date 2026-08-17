import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { User } from '../../core/models';
import { DepartmentStatusGroup, groupPeopleByDepartmentWithStatus } from '../../core/work-status';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent],
  templateUrl: './people.component.html',
  styleUrl: './people.component.scss',
})
export class PeopleComponent implements OnInit {
  groups = signal<DepartmentStatusGroup[]>([]);
  people = signal<User[]>([]);
  search = signal('');
  selectedUser: User | null = null;
  draft: User = {
    id: 0,
    username: '',
    fullName: '',
    email: '',
    role: 'EMPLOYEE',
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
    const isAdmin = this.auth.user()?.role === 'ADMIN';
    const peopleRequest = isAdmin ? this.api.users() : this.api.departmentMembers();
    const attendanceRequest = isAdmin ? this.api.allAttendance() : this.api.departmentAttendance();

    peopleRequest.subscribe((list) => {
      this.people.set(list);
      attendanceRequest.subscribe((records) => {
        this.groups.set(groupPeopleByDepartmentWithStatus(list, records));
      });
    });
  }

  visiblePeople(group: DepartmentStatusGroup) {
    const q = this.search().trim().toLowerCase();
    if (!q) return group.people;
    return group.people.filter(
      (person) =>
        person.fullName.toLowerCase().includes(q) ||
        person.email.toLowerCase().includes(q) ||
        (person.departmentName || '').toLowerCase().includes(q),
    );
  }

  editUser(user: User) {
    this.selectedUser = user;
    this.draft = { ...user };
  }

  saveUser() {
    const payload: Partial<User> = {
      username: this.draft.username,
      fullName: this.draft.fullName,
      email: this.draft.email,
      phone: this.draft.phone,
      role: this.draft.role,
      jobTitle: this.draft.jobTitle,
      departmentName: this.draft.departmentName,
    };

    if (this.selectedUser) {
      this.api.updateUser(this.selectedUser.id, payload).subscribe({
        next: () => {
          this.resetForm();
          this.load();
        },
        error: () => this.load(),
      });
      return;
    }

    this.api.createUser(payload).subscribe({
      next: () => {
        this.resetForm();
        this.load();
      },
      error: () => this.load(),
    });
  }

  deleteUser(id: number) {
    this.api.deleteUser(id).subscribe({
      next: () => {
        if (this.selectedUser?.id === id) this.resetForm();
        this.load();
      },
      error: () => this.load(),
    });
  }

  resetForm() {
    this.selectedUser = null;
    this.draft = {
      id: 0,
      username: '',
      fullName: '',
      email: '',
      role: 'EMPLOYEE',
      jobTitle: 'Nhân viên',
      departmentName: 'Phòng ban',
    };
  }
}
