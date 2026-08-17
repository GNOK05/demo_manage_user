import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../auth.service';

@Component({
  standalone: true,
  selector: 'app-sidebar',
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent {
  constructor(public auth: AuthService) {}

  get peopleLabel(): string {
    return this.auth.user()?.role === 'ADMIN' ? 'Nhân sự công ty' : 'Thành viên phòng ban';
  }

  get attendanceLabel(): string {
    const role = this.auth.user()?.role;
    if (role === 'ADMIN') return 'Chấm công toàn công ty';
    if (role === 'MANAGER') return 'Chấm công phòng ban';
    return 'Chấm công cá nhân';
  }
}
