import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize, forkJoin } from 'rxjs';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import {
  Department,
  Project,
  Task,
  TaskPriority,
  User,
  WorkRequest,
  WorkRequestHealth,
  WorkRequestSaveRequest,
  WorkRequestStatus,
} from '../../core/models';
import { localDateIso } from '../../core/work-status';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, SidebarComponent],
  templateUrl: './work-requests.component.html',
  styleUrl: './work-requests.component.scss',
})
export class WorkRequestsComponent implements OnInit {
  requests = signal<WorkRequest[]>([]);
  projects = signal<Project[]>([]);
  currentProject = signal<Project | null>(null);
  departments = signal<Department[]>([]);
  coordinators = signal<User[]>([]);
  tasks = signal<Task[]>([]);
  expandedId = signal<number | null>(null);
  loading = signal(false);
  saving = signal(false);
  modalOpen = signal(false);
  selected = signal<WorkRequest | null>(null);
  errorMessage = signal('');
  successMessage = signal('');

  keyword = '';
  projectId: number | null = null;
  departmentId: number | null = null;
  status: WorkRequestStatus | '' = '';
  health: WorkRequestHealth | '' = '';
  overdue = false;
  draft: WorkRequestSaveRequest & { projectId: number | null } = this.emptyDraft();

  readonly statuses: WorkRequestStatus[] = ['DRAFT', 'READY', 'IN_PROGRESS', 'PENDING_ACCEPTANCE', 'COMPLETED', 'CANCELLED'];
  readonly healthOptions: WorkRequestHealth[] = ['ON_TRACK', 'AT_RISK', 'OVERDUE', 'BLOCKED'];
  readonly priorities: TaskPriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];

  constructor(public auth: AuthService, private api: CompanyApiService, private route: ActivatedRoute, private router: Router) {}

  ngOnInit() {
    this.projectId = Number(this.route.snapshot.paramMap.get('projectId'));
    if (!this.projectId) {
      this.router.navigate(['/work']);
      return;
    }
    this.loading.set(true);
    forkJoin({
      project: this.api.project(this.projectId),
      departments: this.api.departments(),
      requests: this.api.workRequestsByProject(this.projectId),
    }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: ({ project, departments, requests }) => {
        this.projects.set([project]);
        this.currentProject.set(project);
        this.departments.set(departments);
        this.requests.set(requests);
      },
      error: (error) => this.showError(error, 'Không thể tải danh sách phiếu yêu cầu'),
    });
    if (this.canManage()) {
      const source = this.auth.hasRole(['ADMIN', 'PROJECT_MANAGER']) ? this.api.users() : this.api.departmentMembers();
      source.subscribe({ next: (users) => this.coordinators.set(users), error: () => this.coordinators.set([]) });
    }
  }

  load() {
    this.loading.set(true);
    this.errorMessage.set('');
    this.api.workRequests({
      keyword: this.keyword,
      projectId: this.projectId,
      departmentId: this.departmentId,
      status: this.status || null,
      health: this.health || null,
      overdue: this.overdue,
    }).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (items) => this.requests.set(items),
      error: (error) => this.showError(error, 'Không thể lọc phiếu yêu cầu'),
    });
  }

  toggle(request: WorkRequest) {
    this.router.navigate(['/work/projects', this.projectId, 'requests', request.id]);
  }

  openCreate() {
    this.selected.set(null);
    this.draft = this.emptyDraft();
    const project = this.currentProject();
    if (project) {
      this.draft.projectId = project.id;
      this.onProjectChanged();
    }
    this.modalOpen.set(true);
  }

  edit(request: WorkRequest) {
    this.selected.set(request);
    this.draft = {
      projectId: request.projectId,
      title: request.title,
      description: request.description || '',
      executingDepartmentId: request.executingDepartmentId,
      coordinatorId: request.coordinatorId || null,
      startDate: request.startDate,
      deadline: request.deadline,
      priority: request.priority,
      acceptanceCriteria: request.acceptanceCriteria || '',
    };
    this.modalOpen.set(true);
  }

  save() {
    if (!this.draft.projectId || !this.draft.executingDepartmentId || !this.draft.title.trim()) {
      this.errorMessage.set('Vui lòng chọn dự án, phòng ban thực hiện và nhập tiêu đề');
      return;
    }
    if (this.draft.deadline < this.draft.startDate) {
      this.errorMessage.set('Hạn hoàn thành phải từ ngày bắt đầu trở đi');
      return;
    }
    const { projectId, ...payload } = this.draft;
    this.saving.set(true);
    const request = this.selected()
      ? this.api.updateWorkRequest(this.selected()!.id, payload)
      : this.api.createWorkRequest(projectId, payload);
    request.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: () => {
        this.modalOpen.set(false);
        this.successMessage.set(this.selected() ? 'Đã cập nhật phiếu yêu cầu' : 'Đã tạo phiếu yêu cầu');
        this.load();
        setTimeout(() => this.successMessage.set(''), 3000);
      },
      error: (error) => this.showError(error, 'Không thể lưu phiếu yêu cầu'),
    });
  }

  changeStatus(request: WorkRequest, status: WorkRequestStatus) {
    if (status === request.status) return;
    this.api.updateWorkRequestStatus(request.id, status).subscribe({
      next: () => { this.successMessage.set('Đã cập nhật trạng thái phiếu'); this.load(); },
      error: (error) => this.showError(error, 'Không thể cập nhật trạng thái'),
    });
  }

  remove(request: WorkRequest) {
    if (!window.confirm(`Xóa phiếu ${request.requestCode}?`)) return;
    this.api.deleteWorkRequest(request.id).subscribe({
      next: () => { this.successMessage.set('Đã xóa phiếu yêu cầu'); this.load(); },
      error: (error) => this.showError(error, 'Không thể xóa phiếu yêu cầu'),
    });
  }

  onProjectChanged() {
    const project = this.projects().find((item) => item.id === Number(this.draft.projectId));
    if (!project) return;
    this.draft.projectId = project.id;
    const departments = project.participatingDepartments || [];
    if (!departments.some((item) => item.id === Number(this.draft.executingDepartmentId))) {
      this.draft.executingDepartmentId = departments[0]?.id || project.departmentId;
    }
    this.draft.startDate = project.startDate;
    this.draft.deadline = project.endDate;
  }

  participatingDepartments() {
    const project = this.projects().find((item) => item.id === Number(this.draft.projectId));
    return project?.participatingDepartments?.length
      ? project.participatingDepartments
      : project ? [{ id: project.departmentId, name: project.departmentName, code: '', participationRole: 'OWNER' as const }] : [];
  }

  eligibleCoordinators() {
    const departmentId = Number(this.draft.executingDepartmentId);
    return this.coordinators().filter((user) => user.departmentId === departmentId);
  }

  allowedStatuses(request: WorkRequest): WorkRequestStatus[] {
    const transitions: Record<WorkRequestStatus, WorkRequestStatus[]> = {
      DRAFT: ['READY', 'CANCELLED'],
      READY: ['IN_PROGRESS', 'DRAFT', 'CANCELLED'],
      IN_PROGRESS: ['PENDING_ACCEPTANCE', 'CANCELLED'],
      PENDING_ACCEPTANCE: ['COMPLETED', 'IN_PROGRESS'],
      COMPLETED: [],
      CANCELLED: [],
    };
    return [request.status, ...transitions[request.status]];
  }

  canManage() { return this.auth.hasRole(['ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD']); }
  statusLabel(value: WorkRequestStatus) { return ({ DRAFT: 'Bản nháp', READY: 'Sẵn sàng', IN_PROGRESS: 'Đang thực hiện', PENDING_ACCEPTANCE: 'Chờ nghiệm thu', COMPLETED: 'Hoàn thành', CANCELLED: 'Đã hủy' })[value]; }
  healthLabel(value: WorkRequestHealth) { return ({ ON_TRACK: 'Đúng tiến độ', AT_RISK: 'Có nguy cơ', OVERDUE: 'Quá hạn', BLOCKED: 'Đang bị chặn' })[value]; }
  priorityLabel(value: TaskPriority) { return ({ LOW: 'Thấp', NORMAL: 'Bình thường', HIGH: 'Cao', URGENT: 'Khẩn cấp' })[value]; }

  closeModal() { this.modalOpen.set(false); this.errorMessage.set(''); }
  private emptyDraft(): WorkRequestSaveRequest & { projectId: number | null } {
    return { projectId: null, title: '', description: '', executingDepartmentId: null, coordinatorId: null, startDate: localDateIso(), deadline: localDateIso(), priority: 'NORMAL', acceptanceCriteria: '' };
  }
  private showError(error: any, fallback: string) { this.errorMessage.set(error?.error?.message || fallback); }
}
