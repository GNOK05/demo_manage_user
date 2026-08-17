import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { Department, Project, Task } from '../../core/models';
@Component({
  standalone: true,
  imports: [CommonModule, SidebarComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  projects = signal<Project[]>([]);
  tasks = signal<Task[]>([]);
  departments = signal<Department[]>([]);
  notice = signal('');

  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
  ) {}

  ngOnInit() {
    this.api.projects().subscribe({ next: (x) => this.projects.set(x), error: () => {} });
    this.api.tasks().subscribe({ next: (x) => this.tasks.set(x), error: () => {} });
    this.api.departments().subscribe({ next: (x) => this.departments.set(x), error: () => {} });
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
