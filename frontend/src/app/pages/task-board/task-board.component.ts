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
  statuses: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE', 'REVIEW'];
  tasks = signal<Task[]>([]);
  employees = signal<User[]>([]);
  projects = signal<Project[]>([]);
  search = signal('');
  selectedRole = signal<Role | ''>('');
  selectedStatus = signal<TaskStatus | ''>('');
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
    status: 'TODO',
    deadline: localDateIso(),
  };

  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
  ) {}

  ngOnInit() {
    this.load();
    if (this.auth.hasRole(['ADMIN', 'MANAGER'])) {
      const usersRequest = this.auth.hasRole(['ADMIN'])
        ? this.api.users()
        : this.api.departmentMembers();
      usersRequest.subscribe({
        next: (x) => this.employees.set(x),
        error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể tải nhân sự'),
      });
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
    this.errorMessage.set('');
    this.operationLoading.set(true);
    this.api
      .updateTaskStatus(t.id, status)
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
      deadline: task.deadline,
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
      deadline: this.draft.deadline,
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
      status: 'TODO',
      deadline: localDateIso(),
    };
  }

  isPo() {
    return this.auth.hasRole(['ADMIN', 'MANAGER']);
  }

  isAdmin() {
    return this.auth.hasRole(['ADMIN']);
  }

  onEmployeeSelected() {
    return this.employees().find((e) => e.id === this.draft.assignedToId);
  }

  onProjectSelected() {
    const project = this.projects().find((item) => item.id === Number(this.draft.projectId));
    if (project) {
      this.draft.projectId = project.id;
    }
  }
}
