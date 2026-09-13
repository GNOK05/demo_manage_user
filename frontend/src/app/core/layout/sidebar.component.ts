import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../auth.service';
import { CompanyApiService } from '../company-api.service';
import { catchError, map, of } from 'rxjs';

@Component({
  standalone: true,
  selector: 'app-sidebar',
  imports: [CommonModule, RouterLink, RouterLinkActive],
  templateUrl: './sidebar.component.html',
  styleUrl: './sidebar.component.scss',
})
export class SidebarComponent implements OnInit {
  notificationCount = signal(0);

  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
  ) {}

  ngOnInit() {
    this.api
      .notifications()
      .pipe(
        map((items) => items.filter((item) => item.unread).length),
        catchError(() => of(0)),
      )
      .subscribe((count) => this.notificationCount.set(count));
  }

  get peopleLabel(): string {
    return 'Quản Lý Nhân Sự';
  }

  get attendanceLabel(): string {
    const role = this.auth.user()?.role;
    if (role === 'ADMIN') return 'Chấm công toàn công ty';
    if (role === 'MANAGER') return 'Chấm công phòng ban';
    return 'Chấm công cá nhân';
  }
}
