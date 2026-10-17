import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
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
} from '../../core/models';
import { finalize } from 'rxjs';
import { localDateIso } from '../../core/work-status';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent],
  templateUrl: './task-board.component.html',
  styleUrl: './task-board.component.scss',
})
export class TaskBoardComponent implements OnInit {
  statuses: TaskStatus[] = ['BACKLOG', 'TODO', 'IN_PROGRESS', 'REVIEW', 'TESTING', 'BLOCKED', 'DONE', 'CANCELLED'];
  priorities: TaskPriority[] = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];
  tasks = signal<Task[]>([]);
  employees = signal<User[]>([]);
  projects = signal<Project[]>([]);
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
  ) {}

  ngOnInit() {
    this.load();
    if (this.canCreateTask()) {
      if (this.auth.hasRole(['MEMBER', 'QA'])) {
        const current = this.auth.user();
        this.employees.set(current ? [current] : []);
      } else {
        const usersRequest = this.auth.hasRole(['ADMIN']) ? this.api.users() : this.api.departmentMembers();
        usersRequest.subscribe({
          next: (x) => this.employees.set(x),
          error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể tải nhân sự'),
        });
      }
      this.api.projects().subscribe({
        next: (projects) => {
          this.projects.set(projects);
        },
        error: (error) =>
          this.errorMessage.set(error?.error?.message || 'Không thể tải danh sách dự án'),
      });
    }
  }

  load() {
    this.loading.set(true);
    this.api
      .tasks(
        this.search(),
        this.selectedRole() || null,
        this.selectedStatus() || null,
        this.selectedSort(),
        this.selectedPriority() || null,
        this.selectedOverdue(),
      )
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (tasks) => this.tasks.set(tasks),
        error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể tải công việc'),
      });
  }

  items(s: TaskStatus) {
    return this.tasks().filter((x) => x.status === s);
  }

  searchTasks() {
    this.load();
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

    const payload: TaskSaveRequest = {
      taskName: this.draft.taskName,
      description: this.draft.description,
      projectId: this.draft.projectId,
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

  isAdmin() {
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
    return [task.status, ...transitions[task.status]];
  }

  canCreateTask() {
    return this.auth.hasRole(['ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD', 'MEMBER', 'QA']);
  }

  canEditTask(task: Task) {
    const userId = this.auth.user()?.id;
    return this.isPo() || !!userId && [task.assignedToId, task.testerId, task.createdById].includes(userId);
  }

  canMoveTask(task: Task) {
    return this.canEditTask(task) && !['DONE', 'CANCELLED'].includes(task.status);
  }

  onEmployeeSelected() {
    return this.employees().find((e) => e.id === this.draft.assignedToId);
  }

  onProjectSelected() {
    const project = this.projects().find((item) => item.id === Number(this.draft.projectId));
    if (project) {
      this.draft.projectId = project.id;
      const startDate = this.draft.startDate || project.startDate;
      if (startDate < project.startDate || startDate > project.endDate) {
        this.draft.startDate = project.startDate;
      }
      if (this.draft.deadline < project.startDate || this.draft.deadline > project.endDate) {
        this.draft.deadline = project.endDate;
      }
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
