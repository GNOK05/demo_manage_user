import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { Project, Task } from '../../core/models';
import { Department } from '../../core/models';
import { finalize, forkJoin } from 'rxjs';
@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent],
  templateUrl: './project-list.component.html',
  styleUrl: './project-list.component.scss',
})
export class ProjectListComponent implements OnInit {
  projects = signal<Project[]>([]);
  tasks = signal<Task[]>([]);
  selected = signal<Project | null>(null);
  departments = signal<Department[]>([]);
  selectedDepartmentId: number | null = null;
  selectedStatus = '';
  loading = signal(false);
  errorMessage = signal('');
  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
  ) {}
  ngOnInit() {
    this.loading.set(true);
    forkJoin({ departments: this.api.departments(), projects: this.api.projects() })
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: ({ departments, projects }) => {
          this.departments.set(departments);
          this.projects.set(projects);
        },
        error: () => this.errorMessage.set('Không thể tải danh sách dự án. Vui lòng thử lại.'),
      });
  }

  search() {
    this.loading.set(true);
    this.errorMessage.set('');
    this.api
      .projects(this.selectedDepartmentId, this.selectedStatus || null)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (projects) => {
          this.projects.set(projects);
          this.selected.set(null);
        },
        error: () => this.errorMessage.set('Không thể lọc danh sách dự án. Vui lòng thử lại.'),
      });
  }
  select(p: Project) {
    this.selected.set(p);
    this.api.tasksByProject(p.id).subscribe((x) => this.tasks.set(x));
  }
}
