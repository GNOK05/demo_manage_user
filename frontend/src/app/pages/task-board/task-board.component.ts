import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { Project, Task, TaskStatus, User } from '../../core/models';
import { finalize } from 'rxjs';

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
  selectedTask: Task | null = null;
  errorMessage = signal('');
  successMessage = signal('');
  loading = signal(false);
  taskModalOpen = signal(false);
  deleteModalOpen = signal(false);
  draft: Task = {
    id: 0,
    taskName: '',
    description: '',
    projectId: 1,
    projectName: 'Dự án demo',
    assignedToId: 1,
    assignedToName: 'Nguyễn Văn A',
    testerId: 0,
    testerName: '',
    status: 'TODO',
    deadline: new Date().toISOString().slice(0, 10),
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
          if (projects.length && !this.selectedTask) {
            this.draft.projectId = projects[0].id;
            this.draft.projectName = projects[0].projectName;
          }
        },
        error: (error) =>
          this.errorMessage.set(error?.error?.message || 'Không thể tải danh sách dự án'),
      });
    }
  }

  load() {
    this.loading.set(true);
    this.api
      .tasks()
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: (tasks) => this.tasks.set(tasks),
        error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể tải công việc'),
      });
  }

  filteredTasks() {
    const q = this.search().trim().toLowerCase();
    if (!q) return this.tasks();
    return this.tasks().filter(
      (task) =>
        task.taskName.toLowerCase().includes(q) ||
        task.projectName.toLowerCase().includes(q) ||
        (task.assignedToName || '').toLowerCase().includes(q),
    );
  }

  items(s: TaskStatus) {
    return this.filteredTasks().filter((x) => x.status === s);
  }

  move(t: Task, status: TaskStatus) {
    this.errorMessage.set('');
    this.api.updateTaskStatus(t.id, status).subscribe({
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
    this.draft = { ...task };
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

    const payload: Partial<Task> = {
      taskName: this.draft.taskName,
      description: this.draft.description,
      projectId: this.draft.projectId,
      projectName: this.draft.projectName,
      assignedToId: this.draft.assignedToId,
      assignedToName: this.draft.assignedToName,
      testerId: this.draft.testerId,
      testerName: this.draft.testerName,
      status: this.draft.status,
      deadline: this.draft.deadline,
    };

    if (this.selectedTask) {
      this.api.updateTask(this.selectedTask.id, payload).subscribe({
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

    this.api.createTask(payload).subscribe({
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
    if (!id) return;
    this.api.deleteTask(id).subscribe({
      next: () => {
        this.resetForm();
        this.successMessage.set('✓ Xóa công việc thành công');
        setTimeout(() => {
          this.successMessage.set('');
          this.load();
        }, 1000);
      },
      error: (err) => {
        const msg = err?.error?.message || 'Có lỗi khi xóa công việc';
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
      id: 0,
      taskName: '',
      description: '',
      projectId: 1,
      projectName: 'Dự án demo',
      assignedToId: 1,
      assignedToName: 'Nguyễn Văn A',
      testerId: 0,
      testerName: '',
      status: 'TODO',
      deadline: new Date().toISOString().slice(0, 10),
    };
  }

  isPo() {
    return this.auth.hasRole(['ADMIN', 'MANAGER']);
  }

  isAdmin() {
    return this.auth.hasRole(['ADMIN']);
  }

  onEmployeeSelected() {
    const emp = this.employees().find((e) => e.id === this.draft.assignedToId);
    if (emp) {
      this.draft.assignedToName = emp.fullName;
    }
  }

  onProjectSelected() {
    const project = this.projects().find((item) => item.id === Number(this.draft.projectId));
    if (project) {
      this.draft.projectId = project.id;
      this.draft.projectName = project.projectName;
    }
  }
}
