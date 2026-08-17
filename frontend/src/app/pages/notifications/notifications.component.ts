import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { LeaveRequest, NotificationItem } from '../../core/models';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent, DatePipe],
  templateUrl: './notifications.component.html',
  styleUrl: './notifications.component.scss',
})
export class NotificationsComponent implements OnInit {
  notifications = signal<NotificationItem[]>([]);
  leaveRequests = signal<LeaveRequest[]>([]);
  pendingRequests = signal<LeaveRequest[]>([]);
  readonly isPo = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  draft = {
    type: 'ANNUAL' as LeaveRequest['type'],
    fromDate: new Date().toISOString().slice(0, 10),
    toDate: new Date().toISOString().slice(0, 10),
    reason: '',
  };

  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
  ) {}

  ngOnInit() {
    this.isPo.set(this.auth.hasRole(['ADMIN', 'MANAGER']));
    this.load();
  }

  load() {
    this.api.notifications().subscribe((items) => this.notifications.set(items));
    this.api.leaveRequests().subscribe((items) => this.leaveRequests.set(items));
    if (this.isPo()) {
      this.api.pendingLeaveRequests().subscribe((items) => this.pendingRequests.set(items));
    }
  }

  submitLeaveRequest() {
    this.errorMessage.set('');
    this.successMessage.set('');

    // Validate dates
    const fromDate = new Date(this.draft.fromDate);
    const toDate = new Date(this.draft.toDate);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    if (fromDate < today) {
      this.errorMessage.set('Ngày bắt đầu phải từ hôm nay hoặc sau đó');
      return;
    }

    if (toDate < fromDate) {
      this.errorMessage.set('Ngày kết thúc phải sau ngày bắt đầu');
      return;
    }

    if (!this.draft.reason || this.draft.reason.trim().length === 0) {
      this.errorMessage.set('Vui lòng mô tả rõ nguyên nhân xin nghỉ');
      return;
    }

    this.api.createLeaveRequest(this.draft).subscribe({
      next: () => {
        this.successMessage.set('Đơn xin nghỉ đã được gửi thành công');
        this.draft.reason = '';
        this.draft.fromDate = new Date().toISOString().slice(0, 10);
        this.draft.toDate = new Date().toISOString().slice(0, 10);
        this.draft.type = 'ANNUAL';
        setTimeout(() => this.load(), 500);
      },
      error: (err) => {
        const errorMsg = err?.error?.message || 'Có lỗi khi gửi đơn';
        this.errorMessage.set(errorMsg);
      },
    });
  }

  approve(id: number, status: 'APPROVED' | 'REJECTED') {
    this.api.approveLeaveRequest(id, status).subscribe({
      next: () => {
        this.successMessage.set(`Đơn ${status === 'APPROVED' ? 'đã được duyệt' : 'đã bị từ chối'}`);
        setTimeout(() => {
          this.load();
          this.successMessage.set('');
        }, 500);
      },
      error: (err) => {
        this.errorMessage.set(err?.error?.message || 'Có lỗi khi xử lý đơn');
      },
    });
  }

  unreadCount() {
    return this.notifications().length;
  }
}
