import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { Project, ProjectSaveRequest, Task, WorkRequest } from '../../core/models';
import { Department } from '../../core/models';
import { finalize, forkJoin, map, of, switchMap } from 'rxjs';
import { Router } from '@angular/router';
import { localDateIso } from '../../core/work-status';
@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent],
  templateUrl: './project-list.component.html',
  styleUrl: './project-list.component.scss',
})
export class ProjectListComponent implements OnInit {
  projects = signal<Project[]>([]);
  tasks = signal<Task[]>([]);
  requests = signal<WorkRequest[]>([]);
  selectedRequestId = signal<number | null>(null);
  selected = signal<Project | null>(null);
  departments = signal<Department[]>([]);
  selectedDepartmentId: number | null = null;
  selectedStatus = '';
  keyword = '';
  projectModalOpen = signal(false);
  saving = signal(false);
  selectedProject: Project | null = null;
  projectDraft: ProjectSaveRequest = this.emptyProject();
  participantDepartmentIds: number[] = [];
  participantDepartmentId: number | null = null;
  loading = signal(false);
  errorMessage = signal('');
  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
    private router: Router,
  ) {}
  ngOnInit() {
    this.loading.set(true);
    const projects = this.api.projects();
    const request = this.canLoadDepartments()
      ? forkJoin({ departments: this.api.departments(), projects })
      : forkJoin({ departments: of([] as Department[]), projects });
    request.pipe(finalize(() => this.loading.set(false))).subscribe({
      next: ({ departments, projects: items }) => {
        this.departments.set(departments);
        this.projects.set(items);
      },
      error: () => this.errorMessage.set('Không thể tải danh sách dự án. Vui lòng thử lại.'),
    });
  }

  search() {
    this.loading.set(true);
    this.errorMessage.set('');
    this.api
      .projects(this.selectedDepartmentId, this.selectedStatus || null, this.keyword)
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
    this.router.navigate(['/work/projects', p.id, 'requests']);
  }

  addParticipant(project: Project) {
    if (!this.participantDepartmentId) return;
    this.api.addProjectDepartment(project.id, this.participantDepartmentId).subscribe({
      next: (departments) => this.updateProjectDepartments(project.id, departments),
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể thêm phòng ban vào dự án'),
    });
  }

  removeParticipant(project: Project, departmentId: number) {
    this.api.removeProjectDepartment(project.id, departmentId).subscribe({
      next: (departments) => this.updateProjectDepartments(project.id, departments),
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể gỡ phòng ban khỏi dự án'),
    });
  }

  private updateProjectDepartments(projectId: number, departments: Project['participatingDepartments']) {
    this.projects.update((items) => items.map((item) => item.id === projectId ? { ...item, participatingDepartments: departments } : item));
    const project = this.projects().find((item) => item.id === projectId) || null;
    this.selected.set(project);
    this.participantDepartmentId = null;
  }

  selectRequest(request: WorkRequest) {
    if (this.selectedRequestId() === request.id) {
      this.selectedRequestId.set(null);
      this.tasks.set([]);
      return;
    }
    this.selectedRequestId.set(request.id);
    this.api.tasksByWorkRequest(request.id).subscribe((x) => this.tasks.set(x));
  }

  canFilterDepartments() {
    return this.auth.hasRole(['ADMIN', 'HR']);
  }
  canManageParticipants() { return this.auth.hasRole(['ADMIN', 'PROJECT_MANAGER']); }
  canLoadDepartments() { return this.canFilterDepartments() || this.canManageParticipants(); }
  isParticipant(project: Project, departmentId: number) {
    return project.participatingDepartments?.some((item) => item.id === departmentId) || false;
  }
  visibleProjects() {
    return this.projects();
  }
  canManageProjects() { return this.auth.hasRole(['ADMIN', 'PROJECT_MANAGER']); }
  openCreateProject() { this.selectedProject = null; this.projectDraft = this.emptyProject(); this.participantDepartmentIds = []; this.projectModalOpen.set(true); }
  editProject(project: Project, event: Event) {
    event.stopPropagation(); this.selectedProject = project;
    this.projectDraft = { projectName: project.projectName, description: project.description || '', departmentId: project.departmentId, startDate: project.startDate, endDate: project.endDate, status: project.status as ProjectSaveRequest['status'] };
    this.participantDepartmentIds = project.participatingDepartments.map((department) => department.id);
    this.projectModalOpen.set(true);
  }
  toggleParticipant(departmentId: number, checked: boolean) {
    this.participantDepartmentIds = checked
      ? [...new Set([...this.participantDepartmentIds, departmentId])]
      : this.participantDepartmentIds.filter((id) => id !== departmentId);
  }
  isSelectedParticipant(departmentId: number) {
    return departmentId === Number(this.projectDraft.departmentId) || this.participantDepartmentIds.includes(departmentId);
  }
  saveProject() {
    if (!this.projectDraft.projectName.trim() || !this.projectDraft.departmentId) { this.errorMessage.set('Vui lòng nhập tên và chọn phòng chủ trì'); return; }
    if (this.projectDraft.endDate < this.projectDraft.startDate) { this.errorMessage.set('Ngày kết thúc phải từ ngày bắt đầu trở đi'); return; }
    this.saving.set(true);
    const request = this.selectedProject ? this.api.updateProject(this.selectedProject.id, this.projectDraft) : this.api.createProject(this.projectDraft);
    request.pipe(
      switchMap((project) => {
        const desiredIds = new Set([...this.participantDepartmentIds, Number(this.projectDraft.departmentId)]);
        const existingIds = new Set(project.participatingDepartments.map((department) => department.id));
        const operations = [
          ...[...desiredIds].filter((id) => !existingIds.has(id)).map((id) => this.api.addProjectDepartment(project.id, id)),
          ...[...existingIds].filter((id) => !desiredIds.has(id) && id !== Number(this.projectDraft.departmentId)).map((id) => this.api.removeProjectDepartment(project.id, id)),
        ];
        return operations.length ? forkJoin(operations).pipe(map(() => project)) : of(project);
      }),
      finalize(() => this.saving.set(false)),
    ).subscribe({ next: () => { this.projectModalOpen.set(false); this.search(); }, error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể lưu dự án và các phòng tham gia') });
  }
  deleteProject(project: Project, event: Event) {
    event.stopPropagation();
    if (!window.confirm(`Xóa dự án ${project.projectName}?`)) return;
    this.api.deleteProject(project.id).subscribe({ next: () => this.search(), error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể xóa dự án đang có phiếu yêu cầu') });
  }
  closeProjectModal() { this.projectModalOpen.set(false); }
  private emptyProject(): ProjectSaveRequest { const today = localDateIso(); const end = new Date(); end.setDate(end.getDate() + 90); return { projectName: '', description: '', departmentId: null, startDate: today, endDate: end.toISOString().slice(0,10), status: 'NOT_STARTED' }; }
}
