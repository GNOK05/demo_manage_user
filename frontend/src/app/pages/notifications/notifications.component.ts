import { CommonModule, DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { AttendanceAdjustment, NotificationItem } from '../../core/models';
import { isAttendanceAdjustmentDateAllowed } from '../../core/work-status';
import { finalize } from 'rxjs';

@Component({ standalone: true, imports: [CommonModule, FormsModule, SidebarComponent, DatePipe], templateUrl: './notifications.component.html', styleUrl: './notifications.component.scss' })
export class NotificationsComponent implements OnInit {
  notifications = signal<NotificationItem[]>([]);
  myAdjustments = signal<AttendanceAdjustment[]>([]);
  pendingAdjustments = signal<AttendanceAdjustment[]>([]);
  showAdjustmentForm = signal(false);
  savingAdjustment = signal(false);
  loadingNotifications = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  adjustmentDraft = { sessionId: null as number | null, userId: null as number | null, date: '', requestedCheckIn: '', requestedCheckOut: '', reason: '' };

  constructor(public auth: AuthService, private api: CompanyApiService, private route: ActivatedRoute, private router: Router) {}

  get canApprove() { return this.auth.hasRole(['ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD']); }

  ngOnInit() {
    this.route.queryParamMap.subscribe((params) => {
      const sessionId = Number(params.get('adjustmentSessionId'));
      const userId = Number(params.get('adjustmentUserId'));
      const date = params.get('adjustmentDate');
      const checkIn = params.get('adjustmentCheckIn');
      const checkOut = params.get('adjustmentCheckOut');
      if (!sessionId || !userId || !date || !checkIn || !checkOut || this.auth.hasRole(['ADMIN', 'VIEWER'])) return;
      if (!isAttendanceAdjustmentDateAllowed(date)) { this.errorMessage.set('Chỉ có thể gửi yêu cầu điều chỉnh trong ngày hiện tại hoặc 2 ngày trước đó.'); return; }
      this.adjustmentDraft = { sessionId, userId, date, requestedCheckIn: checkIn.slice(0, 16), requestedCheckOut: checkOut.slice(0, 16), reason: '' };
      this.showAdjustmentForm.set(true);
    });
    this.load();
  }

  load() {
    this.loadingNotifications.set(true);
    this.api.notifications().pipe(finalize(() => this.loadingNotifications.set(false))).subscribe({
      next: (items) => this.notifications.set(items),
      error: () => this.errorMessage.set('Không thể tải thông báo từ máy chủ.'),
    });
    this.api.myAttendanceAdjustments().subscribe({ next: (items) => this.myAdjustments.set(items) });
    if (this.canApprove) this.api.pendingAttendanceAdjustments().subscribe({ next: (items) => this.pendingAdjustments.set(items) });
  }

  submitAdjustment() {
    const draft = this.adjustmentDraft;
    if (!isAttendanceAdjustmentDateAllowed(draft.date)) { this.errorMessage.set('Chỉ có thể gửi yêu cầu điều chỉnh trong ngày hiện tại hoặc 2 ngày trước đó.'); return; }
    if (!draft.sessionId || !draft.userId || !draft.reason.trim()) { this.errorMessage.set('Cần có phiên chấm công và lý do điều chỉnh.'); return; }
    this.savingAdjustment.set(true);
    this.api.createAttendanceAdjustment({ sessionId: draft.sessionId, userId: draft.userId, requestedCheckIn: draft.requestedCheckIn, requestedCheckOut: draft.requestedCheckOut, reason: draft.reason.trim() })
      .pipe(finalize(() => this.savingAdjustment.set(false))).subscribe({
        next: () => { this.successMessage.set('Yêu cầu điều chỉnh đã được gửi.'); this.showAdjustmentForm.set(false); this.router.navigate(['/notifications'], { replaceUrl: true }); this.load(); },
        error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể gửi yêu cầu điều chỉnh.'),
      });
  }

  decideAdjustment(item: AttendanceAdjustment, approved: boolean) {
    const status = approved ? 'APPROVED' : this.auth.user()?.role === 'ADMIN' ? 'REJECTED_ADMIN' : 'REJECTED_MANAGER';
    this.api.decideAttendanceAdjustment(item.id, status).subscribe({
      next: () => { this.successMessage.set(approved ? 'Yêu cầu đã chuyển sang bước duyệt tiếp theo.' : 'Yêu cầu đã bị từ chối.'); this.load(); },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể xử lý yêu cầu điều chỉnh.'),
    });
  }

  adjustmentStatusLabel(status: AttendanceAdjustment['status']) { return ({ PENDING_MANAGER: 'Chờ manager duyệt', PENDING_ADMIN: 'Chờ admin duyệt', APPROVED: 'Đã duyệt', REJECTED_MANAGER: 'Manager từ chối', REJECTED_ADMIN: 'Admin từ chối' } as const)[status]; }
  adjustmentTime(value: string) { return new Intl.DateTimeFormat('vi-VN', { timeZone: 'Asia/Ho_Chi_Minh', hour: '2-digit', minute: '2-digit' }).format(new Date(/[zZ]|[+-]\d{2}:?\d{2}$/.test(value) ? value : `${value}+07:00`)); }
  cancelAdjustment() { this.showAdjustmentForm.set(false); this.router.navigate(['/notifications'], { replaceUrl: true }); }
  unreadCount() { return this.notifications().filter((item) => item.unread).length; }
  markRead(item: NotificationItem) { if (!item.unread) return; this.api.markNotificationRead(item.key).subscribe({ next: () => this.notifications.update((items) => items.map((current) => current.key === item.key ? { ...current, unread: false } : current)) }); }
}
