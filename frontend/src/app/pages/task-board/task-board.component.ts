import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import {
  Project,
  Role,
  Task,
  TaskSaveRequest,
  TaskPriority,
  TaskSort,
  TaskStatus,
  User,
  WorkRequest,
} from '../../core/models';
import { finalize, forkJoin } from 'rxjs';
import { localDateIso } from '../../core/work-status';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink, SidebarComponent],
  templateUrl: './task-board.component.html',
  styleUrl: './task-board.component.scss',
})
export class TaskBoardComponent implements OnInit {
  statuses: TaskStatus[] = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'TESTING', 'BLOCKED', 'DONE', 'CANCELLED'];
  priorities: TaskPriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];
  tasks = signal<Task[]>([]);
  employees = signal<User[]>([]);
  projects = signal<Project[]>([]);
  workRequests = signal<WorkRequest[]>([]);
  currentRequest = signal<WorkRequest | null>(null);
  expandedProjectId = signal<number | null>(null);
  expandedRequestId = signal<number | null>(null);
  search = signal('');
  selectedRole = signal<Role | ''>('');
  selectedStatus = signal<TaskStatus | ''>('');
  selectedPriority = signal<TaskPriority | ''>('');
  selectedOverdue = signal(false);
  selectedSort = signal<TaskSort>('DEFAULT');
  selectedTask: Task | null = null;
  errorMessage = signal('');
  successMessage = signal('');
  loading = signal(false);
  operationLoading = signal(false);
  taskModalOpen = signal(false);
  deleteModalOpen = signal(false);
  draft: TaskSaveRequest = {
    taskName: '',
    description: '',
    projectId: null,
    workRequestId: null,
    assignedToId: null,
    testerId: null,
    status: 'BACKLOG',
    priority: 'NORMAL',
    startDate: localDateIso(),
    deadline: localDateIso(),
  };

  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit() {
    const requestId = Number(this.route.snapshot.paramMap.get('requestId'));
    if (!requestId) return;
    this.expandedRequestId.set(requestId);
    this.loading.set(true);
    forkJoin({ request: this.api.workRequest(requestId), tasks: this.api.tasksByWorkRequest(requestId) })
      .pipe(finalize(() => this.loading.set(false))).subscribe({
        next: ({ request, tasks }) => {
          this.currentRequest.set(request);
          this.workRequests.set([request]);
          this.expandedProjectId.set(request.projectId);
          this.tasks.set(tasks);
          this.api.project(request.projectId).subscribe((project) => this.projects.set([project]));
          if (!this.route.snapshot.paramMap.get('projectId')) {
            this.router.navigate(['/work/projects', request.projectId, 'requests', request.id], { replaceUrl: true });
          }
        },
        error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể tải task của phiếu yêu cầu'),
      });
    if (this.canCreateTask()) {
      if (this.auth.hasRole(['MEMBER', 'QA'])) {
        const current = this.auth.user();
        this.employees.set(current ? [current] : []);
      } else {
        const usersRequest = this.auth.hasRole(['ADMIN', 'PROJECT_MANAGER']) ? this.api.users() : this.api.departmentMembers();
        usersRequest.subscribe({
          next: (x) => this.employees.set(x),
          error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể tải nhân sự'),
        });
      }
    }
  }

  load() {
    const requestId = this.expandedRequestId();
    if (requestId) {
      this.loadTasks(requestId);
      return;
    }
    this.loadProjects();
  }

  loadProjects() {
    this.loading.set(true);
    this.api.projects()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (projects) => this.projects.set(projects),
        error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể tải dự án'),
      });
  }

  toggleProject(project: Project) {
    if (this.expandedProjectId() === project.id) {
      this.expandedProjectId.set(null);
      this.expandedRequestId.set(null);
      this.workRequests.set([]);
      this.tasks.set([]);
      return;
    }
    this.expandedProjectId.set(project.id);
    this.expandedRequestId.set(null);
    this.tasks.set([]);
    this.loading.set(true);
    this.api.workRequestsByProject(project.id).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (requests) => this.workRequests.set(requests),
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể tải phiếu yêu cầu'),
    });
  }

  toggleRequest(request: WorkRequest) {
    if (this.expandedRequestId() === request.id) {
      this.expandedRequestId.set(null);
      this.tasks.set([]);
      return;
    }
    this.expandedRequestId.set(request.id);
    this.loadTasks(request.id);
  }

  loadTasks(requestId: number) {
    this.loading.set(true);
    this.errorMessage.set('');
    this.api.tasks(
      this.search(),
      this.selectedRole() || null,
      this.selectedStatus() || null,
      this.selectedSort(),
      this.selectedPriority() || null,
      this.selectedOverdue(),
      requestId,
    ).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: (tasks) => this.tasks.set(tasks),
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể tìm kiếm task'),
    });
  }

  visibleProjects() {
    const query = this.search().trim().toLowerCase();
    return !query ? this.projects() : this.projects().filter((project) =>
      project.projectName.toLowerCase().includes(query)
      || project.departmentName.toLowerCase().includes(query));
  }

  visibleTasks() {
    return this.tasks();
  }

  items(s: TaskStatus) {
    return this.tasks().filter((x) => x.status === s);
  }

  searchTasks() {
    const requestId = this.expandedRequestId();
    if (requestId) this.loadTasks(requestId);
  }

  move(t: Task, status: TaskStatus) {
    if (this.operationLoading()) return;
    const blockedReason = status === 'BLOCKED'
      ? window.prompt('Nhập lý do công việc bị chặn')
      : undefined;
    if (status === 'BLOCKED' && (!blockedReason || !blockedReason.trim())) {
      this.load();
      return;
    }
    this.errorMessage.set('');
    this.operationLoading.set(true);
    this.api
      .updateTaskStatus(t.id, status, blockedReason?.trim())
      .pipe(finalize(() => this.operationLoading.set(false)))
      .subscribe({
        next: () => {
          this.successMessage.set('✓ Cập nhật công việc thành công');
          setTimeout(() => this.successMessage.set(''), 3000);
          this.load();
        },
        error: (err) => {
          const msg =
            err?.error?.message ||
            'Không thể cập nhật công việc. Bạn cần chấm công kết thúc trước khi hoàn thành task.';
          this.errorMessage.set(msg);
        },
      });
  }

  editTask(task: Task) {
    this.selectedTask = task;
    this.draft = {
      taskName: task.taskName,
      description: task.description,
      projectId: task.projectId,
      workRequestId: task.workRequestId,
      assignedToId: task.assignedToId ?? null,
      testerId: task.testerId ?? null,
      status: task.status,
      priority: task.priority,
      startDate: task.startDate,
      deadline: task.deadline,
      estimatedHours: task.estimatedHours ?? null,
      acceptanceCriteria: task.acceptanceCriteria ?? '',
      blockedReason: task.blockedReason ?? '',
    };
    this.taskModalOpen.set(true);
  }

  openCreate() {
    this.resetForm();
    this.draft.projectId = this.expandedProjectId();
    this.draft.workRequestId = this.expandedRequestId();
    const request = this.workRequests().find((item) => item.id === this.draft.workRequestId);
    if (request) {
      this.draft.startDate = request.startDate;
      this.draft.deadline = request.deadline;
    }
    this.taskModalOpen.set(true);
  }

  saveTask() {
    this.errorMessage.set('');

    if (!this.draft.taskName || this.draft.taskName.trim() === '') {
      this.errorMessage.set('Vui lòng nhập tên công việc');
      return;
    }

    if (
      !this.draft.projectId ||
      !this.projects().some((project) => project.id === this.draft.projectId)
    ) {
      this.errorMessage.set('Vui lòng chọn một dự án hợp lệ');
      return;
    }
    if (!this.draft.workRequestId || !this.filteredWorkRequests().some((request) => request.id === this.draft.workRequestId)) {
      this.errorMessage.set('Vui lòng chọn phiếu yêu cầu thuộc dự án');
      return;
    }

    const payload: TaskSaveRequest = {
      taskName: this.draft.taskName,
      description: this.draft.description,
      projectId: this.draft.projectId,
      workRequestId: this.draft.workRequestId,
      assignedToId: this.draft.assignedToId || null,
      testerId: this.draft.testerId || null,
      status: this.draft.status,
      priority: this.draft.priority,
      startDate: this.draft.startDate,
      deadline: this.draft.deadline,
      estimatedHours: this.draft.estimatedHours ?? null,
      acceptanceCriteria: this.draft.acceptanceCriteria,
      blockedReason: this.draft.blockedReason,
    };

    if (this.selectedTask) {
      this.operationLoading.set(true);
      this.api
        .updateTask(this.selectedTask.id, payload)
        .pipe(finalize(() => this.operationLoading.set(false)))
        .subscribe({
          next: () => {
            this.successMessage.set('✓ Cập nhật công việc thành công');
            setTimeout(() => {
              this.successMessage.set('');
              this.resetForm();
              this.load();
            }, 500);
          },
          error: (err) => {
            const msg = err?.error?.message || 'Có lỗi khi cập nhật công việc';
            this.errorMessage.set(msg);
          },
        });
      return;
    }

    this.operationLoading.set(true);
    this.api
      .createTask(payload)
      .pipe(finalize(() => this.operationLoading.set(false)))
      .subscribe({
        next: () => {
          this.successMessage.set('✓ Tạo công việc thành công');
          setTimeout(() => {
            this.successMessage.set('');
            this.resetForm();
            this.load();
          }, 500);
        },
        error: (err) => {
          const msg = err?.error?.message || 'Có lỗi khi tạo công việc';
          this.errorMessage.set(msg);
        },
      });
  }

  deleteTask(id: number) {
    this.selectedTask = this.tasks().find((task) => task.id === id) || null;
    this.deleteModalOpen.set(true);
  }

  confirmDelete() {
    const id = this.selectedTask?.id;
    if (!id || this.operationLoading()) return;
    this.operationLoading.set(true);
    this.api
      .deleteTask(id)
      .pipe(finalize(() => this.operationLoading.set(false)))
      .subscribe({
        next: () => {
          this.resetForm();
          this.successMessage.set('✓ Xóa công việc thành công');
          setTimeout(() => {
            this.successMessage.set('');
            this.load();
          }, 1000);
        },
        error: (err) => {
          const msg =
            err?.status === 404
              ? 'Công việc không còn tồn tại. Danh sách đã được cập nhật.'
              : err?.error?.message || 'Có lỗi khi xóa công việc';
          if (err?.status === 404) {
            this.resetForm();
            this.load();
          }
          this.errorMessage.set(msg);
        },
      });
  }

  resetForm() {
    this.selectedTask = null;
    this.taskModalOpen.set(false);
    this.deleteModalOpen.set(false);
    this.errorMessage.set('');
    this.draft = {
      taskName: '',
      description: '',
      projectId: null,
      workRequestId: null,
      assignedToId: null,
      testerId: null,
      status: 'BACKLOG',
      priority: 'NORMAL',
      startDate: localDateIso(),
      deadline: localDateIso(),
    };
  }

  isPo() {
    return this.auth.hasRole(['ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD']);
  }

  allowedStatuses(task: Task): TaskStatus[] {
    const transitions: Record<TaskStatus, TaskStatus[]> = {
      BACKLOG: ['TODO', 'CANCELLED'],
      TODO: ['IN_PROGRESS', 'BACKLOG', 'BLOCKED', 'CANCELLED'],
      IN_PROGRESS: ['REVIEW', 'BLOCKED', 'CANCELLED'],
      REVIEW: ['TESTING', 'IN_PROGRESS', 'BLOCKED', 'CANCELLED'],
      TESTING: ['DONE', 'IN_PROGRESS', 'BLOCKED', 'CANCELLED'],
      BLOCKED: ['TODO', 'IN_PROGRESS', 'CANCELLED'],
      DONE: [],
      CANCELLED: [],
    };
    const role = this.auth.user()?.role;
    if (role === 'VIEWER') return [task.status];
    const manager = role === 'ADMIN' || role === 'PROJECT_MANAGER' || role === 'TEAM_LEAD';
    const assignee = this.auth.user()?.id === task.assignedToId;
    const tester = this.auth.user()?.id === task.testerId;
    const allowed = transitions[task.status] ?? [];
    return [
      task.status,
      ...allowed.filter((next) => {
        if (['CANCELLED', 'BACKLOG'].includes(next) || task.status === 'BACKLOG') return manager;
        if (task.status === 'REVIEW') return manager;
        if (task.status === 'TESTING' && ['DONE', 'IN_PROGRESS'].includes(next)) {
          return manager || tester || role === 'QA';
        }
        return manager || assignee;
      }),
    ];
  }

  canCreateTask() {
    return this.auth.hasRole(['ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD', 'MEMBER', 'QA']);
  }

  canEditTask(task: Task) {
    if (this.auth.hasRole(['HR', 'VIEWER'])) return false;
    const userId = this.auth.user()?.id;
    return this.isPo() || !!userId && [task.assignedToId, task.testerId, task.createdById].includes(userId);
  }

  canDeleteTask(task: Task) {
    const role = this.auth.user()?.role;
    if (role === 'ADMIN') return true;
    if (!this.auth.hasRole(['PROJECT_MANAGER', 'TEAM_LEAD'])) return false;
    return role !== 'TEAM_LEAD' || task.status !== 'DONE';
  }

  canMoveTask(task: Task) {
    return this.allowedStatuses(task).length > 1;
  }

  canFilterByAssigneeRole() {
    return this.auth.hasRole(['ADMIN', 'HR', 'PROJECT_MANAGER', 'TEAM_LEAD']);
  }

  onEmployeeSelected() {
    return this.employees().find((e) => e.id === this.draft.assignedToId);
  }

  onProjectSelected() {
    const project = this.projects().find((item) => item.id === Number(this.draft.projectId));
    if (project) {
      this.draft.projectId = project.id;
      if (!this.filteredWorkRequests().some((request) => request.id === this.draft.workRequestId)) {
        this.draft.workRequestId = null;
      }
      this.api.workRequestsByProject(project.id).subscribe({
        next: (requests) => this.workRequests.set(requests),
        error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể tải phiếu yêu cầu'),
      });
      const startDate = this.draft.startDate || project.startDate;
      if (startDate < project.startDate || startDate > project.endDate) {
        this.draft.startDate = project.startDate;
      }
      if (this.draft.deadline < project.startDate || this.draft.deadline > project.endDate) {
        this.draft.deadline = project.endDate;
      }
    }
  }

  filteredWorkRequests() {
    return this.workRequests().filter((request) => request.projectId === Number(this.draft.projectId));
  }

  eligibleAssignees() {
    const departmentId = this.currentRequest()?.executingDepartmentId;
    return this.employees().filter((user) => user.departmentId === departmentId);
  }

  eligibleTesters() {
    const departmentIds = this.projects()[0]?.participatingDepartments.map((department) => department.id) || [];
    return this.employees().filter((user) =>
      user.role === 'QA' && !!user.departmentId && departmentIds.includes(user.departmentId));
  }

  onWorkRequestSelected() {
    const request = this.filteredWorkRequests().find((item) => item.id === Number(this.draft.workRequestId));
    if (!request) return;
    this.draft.workRequestId = request.id;
    this.draft.projectId = request.projectId;
    if (!this.draft.startDate || this.draft.startDate < request.startDate || this.draft.startDate > request.deadline) {
      this.draft.startDate = request.startDate;
    }
    if (!this.draft.deadline || this.draft.deadline < request.startDate || this.draft.deadline > request.deadline) {
      this.draft.deadline = request.deadline;
    }
  }

  statusLabel(status: TaskStatus) {
    return {
      BACKLOG: 'Backlog',
      TODO: 'Chưa bắt đầu',
      IN_PROGRESS: 'Đang thực hiện',
      REVIEW: 'Chờ review',
      TESTING: 'Đang kiểm thử',
      BLOCKED: 'Bị chặn',
      DONE: 'Hoàn thành',
      CANCELLED: 'Đã huỷ',
    }[status];
  }

  priorityLabel(priority: TaskPriority) {
    return { LOW: 'Thấp', NORMAL: 'Bình thường', HIGH: 'Cao', URGENT: 'Khẩn cấp' }[priority];
  }

  requestStatusLabel(status: WorkRequest['status']) {
    return { DRAFT: 'Bản nháp', READY: 'Sẵn sàng', IN_PROGRESS: 'Đang thực hiện', PENDING_ACCEPTANCE: 'Chờ nghiệm thu', COMPLETED: 'Hoàn thành', CANCELLED: 'Đã hủy' }[status];
  }

  roleScopeLabel() {
    const role = this.auth.user()?.role;
    if (role === 'ADMIN' || role === 'HR' || role === 'PROJECT_MANAGER') return 'Phạm vi toàn công ty';
    if (role === 'TEAM_LEAD' || role === 'VIEWER') return `Phạm vi ${this.auth.user()?.departmentName || 'phòng ban'}`;
    return 'Công việc của tôi';
  }

  canManageRequests() { return this.auth.hasRole(['ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD']); }

  isOverdue(task: Task) {
    return task.status !== 'DONE' && task.deadline < localDateIso();
  }

  countOverdue() {
    return this.tasks().filter((task) => this.isOverdue(task)).length;
  }

  countBlocked() {
    return this.tasks().filter((task) => task.status === 'BLOCKED').length;
  }

  countUrgent() {
    return this.tasks().filter((task) => task.priority === 'URGENT').length;
  }
}
