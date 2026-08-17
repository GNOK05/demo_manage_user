import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { Task, TaskStatus } from '../../core/models';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent],
  templateUrl: './task-board.component.html',
  styleUrl: './task-board.component.scss',
})
export class TaskBoardComponent implements OnInit {
  statuses: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE', 'REVIEW'];
  tasks = signal<Task[]>([]);
  search = signal('');
  selectedTask: Task | null = null;
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
    this.api.updateTaskStatus(t.id, status).subscribe({
      next: () => this.load(),
      error: () => this.load(),
    });
  }

  editTask(task: Task) {
    this.selectedTask = task;
    this.draft = { ...task };
  }

  saveTask() {
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
          this.resetForm();
          this.load();
        },
        error: () => this.load(),
      });
      return;
    }

    this.api.createTask(payload).subscribe({
      next: () => {
        this.resetForm();
        this.load();
      },
      error: () => this.load(),
    });
  }

  deleteTask(id: number) {
    this.api.deleteTask(id).subscribe({
      next: () => {
        if (this.selectedTask?.id === id) this.resetForm();
        this.load();
      },
      error: () => this.load(),
    });
  }

  resetForm() {
    this.selectedTask = null;
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
}
