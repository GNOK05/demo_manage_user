import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { Task, TaskStatus, User } from '../../core/models';

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
  search = signal('');
  selectedTask: Task | null = null;
  errorMessage = signal('');
  successMessage = signal('');
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
      this.api.users().subscribe((x) => this.employees.set(x));
    }
  }

  load() {
    this.api.tasks().subscribe((x) => this.tasks.set(x));
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
  }

  saveTask() {
    this.errorMessage.set('');

    if (!this.draft.taskName || this.draft.taskName.trim() === '') {
      this.errorMessage.set('Vui lòng nhập tên công việc');
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
    this.api.deleteTask(id).subscribe({
      next: () => {
        if (this.selectedTask?.id === id) this.resetForm();
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

  onEmployeeSelected() {
    const emp = this.employees().find((e) => e.id === this.draft.assignedToId);
    if (emp) {
      this.draft.assignedToName = emp.fullName;
    }
  }
}
