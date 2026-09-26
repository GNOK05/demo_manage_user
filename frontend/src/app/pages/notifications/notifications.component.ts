import { Component, OnInit, signal } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { AnnualLeaveBalance, LeaveRequest, NotificationItem } from '../../core/models';
import { finalize } from 'rxjs';
import { isAttendanceAdjustmentDateAllowed, localDateIso } from '../../core/work-status';
import { ActivatedRoute, Router } from '@angular/router';
import { AttendanceAdjustment } from '../../core/models';

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
  balance = signal<AnnualLeaveBalance | null>(null);
  myAdjustments = signal<AttendanceAdjustment[]>([]);
  pendingAdjustments = signal<AttendanceAdjustment[]>([]);
  showAdjustmentForm = signal(false);
  savingAdjustment = signal(false);
  adjustmentDraft: {
    sessionId: number | null;
    userId: number | null;
    date: string;
    requestedCheckIn: string;
    requestedCheckOut: string;
    reason: string;
  } = { sessionId: null, userId: null, date: '', requestedCheckIn: '', requestedCheckOut: '', reason: '' };
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
    private route: ActivatedRoute,
    private router: Router,
  ) {}

  ngOnInit() {
    this.isPo.set(this.auth.hasRole(['ADMIN', 'MANAGER']));
    this.route.queryParamMap.subscribe((params) => {
      const sessionId = Number(params.get('adjustmentSessionId'));
      const userId = Number(params.get('adjustmentUserId'));
      const date = params.get('adjustmentDate');
      const checkIn = params.get('adjustmentCheckIn');
      const checkOut = params.get('adjustmentCheckOut');
      if (!sessionId || !userId || !date || !checkIn || !checkOut || this.auth.user()?.role === 'ADMIN') return;
      if (!isAttendanceAdjustmentDateAllowed(date)) {
        this.errorMessage.set('Chỉ có thể gửi yêu cầu điều chỉnh trong ngày hiện tại hoặc 2 ngày trước đó.');
        return;
      }
      this.adjustmentDraft = {
        sessionId,
        userId,
        date,
        requestedCheckIn: checkIn.slice(0, 16),
        requestedCheckOut: checkOut.slice(0, 16),
        reason: '',
      };
      this.showAdjustmentForm.set(true);
    });
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
    this.api.annualLeaveBalance(Number(localDateIso().slice(0, 4))).subscribe({
      next: (balance) => this.balance.set(balance),
      error: () => this.errorMessage.set('Không thể tải số dư phép năm'),
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
    this.api.myAttendanceAdjustments().subscribe({ next: (items) => this.myAdjustments.set(items) });
    if (this.isPo()) {
      this.api.pendingAttendanceAdjustments().subscribe({ next: (items) => this.pendingAdjustments.set(items) });
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

  cancelLeave(id: number) {
    this.loading.set(true);
    this.api.cancelLeaveRequest(id).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: () => {
        this.successMessage.set('Đơn nghỉ đã được hủy');
        this.load();
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể hủy đơn nghỉ'),
    });
  }

  canCancel(item: LeaveRequest) {
    return ['PENDING', 'PENDING_MANAGER', 'PENDING_ADMIN', 'APPROVED'].includes(item.status)
      && item.fromDate > localDateIso();
  }

  leaveStatusLabel(status: LeaveRequest['status']) {
    return ({
      PENDING: 'Chờ manager duyệt',
      PENDING_MANAGER: 'Chờ manager duyệt',
      PENDING_ADMIN: 'Chờ admin duyệt',
      APPROVED: 'Đã duyệt',
      REJECTED: 'Từ chối',
      REJECTED_MANAGER: 'Manager từ chối',
      REJECTED_ADMIN: 'Admin từ chối',
      CANCELLED: 'Đã hủy',
    } as Record<LeaveRequest['status'], string>)[status];
  }

  leaveTypeLabel(type: LeaveRequest['type']) {
    return ({ ANNUAL: 'Phép năm hưởng lương', UNPAID: 'Nghỉ không lương', PERSONAL: 'Nghỉ cá nhân',
      SICK: 'Nghỉ ốm', MATERNITY: 'Nghỉ thai sản' } as Record<LeaveRequest['type'], string>)[type];
  }

  submitAdjustment() {
    const draft = this.adjustmentDraft;
    if (!isAttendanceAdjustmentDateAllowed(draft.date)) {
      this.errorMessage.set('Chỉ có thể gửi yêu cầu điều chỉnh trong ngày hiện tại hoặc 2 ngày trước đó.');
      return;
    }
    if (!draft.sessionId || !draft.userId || !draft.reason.trim()) {
      this.errorMessage.set('Cần có phiên chấm công và lý do điều chỉnh.');
      return;
    }
    this.savingAdjustment.set(true);
    this.api.createAttendanceAdjustment({
      sessionId: draft.sessionId,
      userId: draft.userId,
      requestedCheckIn: draft.requestedCheckIn,
      requestedCheckOut: draft.requestedCheckOut,
      reason: draft.reason.trim(),
    }).pipe(finalize(() => this.savingAdjustment.set(false))).subscribe({
      next: () => {
        this.successMessage.set('Yêu cầu điều chỉnh đã được gửi; dữ liệu công chỉ đổi sau khi duyệt đủ cấp.');
        this.showAdjustmentForm.set(false);
        this.router.navigate(['/notifications'], { replaceUrl: true });
        this.load();
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể gửi yêu cầu điều chỉnh.'),
    });
  }

  decideAdjustment(item: AttendanceAdjustment, approved: boolean) {
    const status = approved ? 'APPROVED' : this.auth.user()?.role === 'ADMIN' ? 'REJECTED_ADMIN' : 'REJECTED_MANAGER';
    this.api.decideAttendanceAdjustment(item.id, status).subscribe({
      next: () => {
        this.successMessage.set(approved ? 'Yêu cầu đã chuyển sang bước duyệt tiếp theo.' : 'Yêu cầu điều chỉnh đã bị từ chối.');
        this.load();
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể xử lý yêu cầu điều chỉnh.'),
    });
  }

  adjustmentStatusLabel(status: AttendanceAdjustment['status']) {
    return ({ PENDING_MANAGER: 'Chờ manager duyệt', PENDING_ADMIN: 'Chờ admin duyệt', APPROVED: 'Đã duyệt',
      REJECTED_MANAGER: 'Manager từ chối', REJECTED_ADMIN: 'Admin từ chối' } as Record<AttendanceAdjustment['status'], string>)[status];
  }

  adjustmentTime(value: string) {
    return new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit' })
      .format(new Date(/[zZ]|[+-]\d{2}:?\d{2}$/.test(value) ? value : `${value}+07:00`));
  }

  cancelAdjustment() {
    this.showAdjustmentForm.set(false);
    this.router.navigate(['/notifications'], { replaceUrl: true });
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
