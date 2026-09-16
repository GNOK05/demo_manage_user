import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { LeaveRequest, NotificationItem } from '../../core/models';
import { finalize } from 'rxjs';
import { localDateIso } from '../../core/work-status';

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
  loading = signal(false);
  loadingNotifications = signal(false);
  loadingLeaveRequests = signal(false);
  loadingPendingRequests = signal(false);
  draft = {
    type: 'ANNUAL' as LeaveRequest['type'],
    fromDate: localDateIso(),
    toDate: localDateIso(),
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
    this.loadingNotifications.set(true);
    this.api
      .notifications()
      .pipe(finalize(() => this.loadingNotifications.set(false)))
      .subscribe({
        next: (items) => this.notifications.set(items),
        error: () => this.errorMessage.set('Không thể tải thông báo từ máy chủ'),
      });
    this.loadingLeaveRequests.set(true);
    this.api
      .leaveRequests()
      .pipe(finalize(() => this.loadingLeaveRequests.set(false)))
      .subscribe({
        next: (items) => this.leaveRequests.set(items),
        error: () => this.errorMessage.set('Không thể tải danh sách đơn nghỉ phép'),
      });
    if (this.isPo()) {
      this.loadingPendingRequests.set(true);
      this.api
        .pendingLeaveRequests()
        .pipe(finalize(() => this.loadingPendingRequests.set(false)))
        .subscribe({
          next: (items) => this.pendingRequests.set(items),
          error: () => this.errorMessage.set('Không thể tải đơn nghỉ đang chờ duyệt'),
        });
    }
  }

  submitLeaveRequest() {
    this.errorMessage.set('');
    this.successMessage.set('');

    // Validate dates
    const fromDate = new Date(`${this.draft.fromDate}T00:00:00`);
    const toDate = new Date(`${this.draft.toDate}T00:00:00`);
    const today = new Date();
    today.setHours(0, 0, 0, 0);

    if (!this.draft.type || !this.draft.fromDate || !this.draft.toDate) {
      this.errorMessage.set('Vui lòng nhập đầy đủ loại nghỉ và thời gian nghỉ');
      return;
    }

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

    if (this.draft.reason.trim().length > 500) {
      this.errorMessage.set('Nguyên nhân xin nghỉ không được vượt quá 500 ký tự');
      return;
    }

    this.loading.set(true);
    this.api
      .createLeaveRequest(this.draft)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: () => {
          this.successMessage.set('Đơn xin nghỉ đã được gửi thành công');
          this.draft.reason = '';
          this.draft.fromDate = localDateIso();
          this.draft.toDate = localDateIso();
          this.draft.type = 'ANNUAL';
          this.load();
        },
        error: (err) => {
          const errorMsg = err?.error?.message || 'Có lỗi khi gửi đơn';
          this.errorMessage.set(errorMsg);
        },
      });
  }

  approve(id: number, status: 'APPROVED' | 'REJECTED') {
    this.loading.set(true);
    this.api
      .approveLeaveRequest(id, status)
      .pipe(finalize(() => this.loading.set(false)))
      .subscribe({
        next: () => {
          this.successMessage.set(
            `Đơn ${status === 'APPROVED' ? 'đã được duyệt' : 'đã bị từ chối'}`,
          );
          this.load();
        },
        error: (err) => {
          this.errorMessage.set(err?.error?.message || 'Có lỗi khi xử lý đơn');
        },
      });
  }

  unreadCount() {
    return this.notifications().filter((item) => item.unread).length;
  }

  markRead(item: NotificationItem) {
    if (!item.unread) return;
    this.api.markNotificationRead(item.key).subscribe({
      next: () =>
        this.notifications.update((items) =>
          items.map((current) =>
            current.key === item.key ? { ...current, unread: false } : current,
          ),
        ),
      error: (error) =>
        this.errorMessage.set(error?.error?.message || 'Không thể cập nhật thông báo.'),
    });
  }
}
