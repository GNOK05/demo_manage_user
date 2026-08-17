import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { Attendance } from '../../core/models';
import {
  computeWorkStatus,
  DepartmentStatusGroup,
  groupPeopleByDepartmentWithStatus,
  WORK_STATUS_LABEL,
} from '../../core/work-status';

@Component({
  standalone: true,
  imports: [CommonModule, SidebarComponent],
  templateUrl: './attendance.component.html',
  styleUrl: './attendance.component.scss',
})
export class AttendanceComponent implements OnInit {
  entries = signal<Attendance[]>([]);
  groups = signal<DepartmentStatusGroup[]>([]);
  myStatusLabel = signal<string>('');

  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
  ) {}

  title() {
    const role = this.auth.user()?.role;
    return role === 'ADMIN'
      ? 'Chấm công toàn công ty'
      : role === 'MANAGER'
        ? 'Chấm công phòng ban'
        : 'Chấm công cá nhân';
  }

  isSelf() {
    return this.auth.user()?.role === 'EMPLOYEE';
  }

  ngOnInit() {
    const role = this.auth.user()?.role;
    if (role === 'ADMIN' || role === 'MANAGER') {
      const peopleRequest = role === 'ADMIN' ? this.api.users() : this.api.departmentMembers();
      const attendanceRequest =
        role === 'ADMIN' ? this.api.allAttendance() : this.api.departmentAttendance();
      peopleRequest.subscribe((people) =>
        attendanceRequest.subscribe((records) => {
          this.entries.set(records);
          this.groups.set(groupPeopleByDepartmentWithStatus(people, records));
        }),
      );
    } else {
      this.api.myAttendance().subscribe((records) => {
        this.entries.set(records);
        const today = new Date().toISOString().slice(0, 10);
        const todayEntry = records.find((r) => r.date === today);
        this.myStatusLabel.set(WORK_STATUS_LABEL[computeWorkStatus(todayEntry)]);
      });
    }
  }
}
