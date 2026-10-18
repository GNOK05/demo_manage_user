import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { AnnualLeaveBalance, LeaveDayPart, LeaveRequest, WorkLocationRequest } from '../../core/models';
import { localDateIso } from '../../core/work-status';
import { finalize } from 'rxjs';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent],
  templateUrl: './requests.component.html',
  styleUrl: './requests.component.scss',
})
export class RequestsComponent implements OnInit {
  activeForm = signal<'LEAVE' | 'LOCATION'>('LEAVE');
  leaveRequests = signal<LeaveRequest[]>([]);
  pendingLeaves = signal<LeaveRequest[]>([]);
  locationRequests = signal<WorkLocationRequest[]>([]);
  pendingLocations = signal<WorkLocationRequest[]>([]);
  balance = signal<AnnualLeaveBalance | null>(null);
  loading = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  readonly today = localDateIso();

  leaveDraft = {
    type: 'ANNUAL' as LeaveRequest['type'],
    fromDate: localDateIso(),
    toDate: localDateIso(),
    fromDayPart: 'MORNING' as LeaveDayPart,
    toDayPart: 'AFTERNOON' as LeaveDayPart,
    reason: '',
  };
  locationDraft = {
    type: 'WORK_FROM_HOME' as WorkLocationRequest['type'],
    fromDate: localDateIso(),
    toDate: localDateIso(),
    fromDayPart: 'MORNING' as LeaveDayPart,
    toDayPart: 'AFTERNOON' as LeaveDayPart,
    clientName: '',
    clientAddress: '',
    reason: '',
  };

  constructor(public auth: AuthService, private api: CompanyApiService) {}

  get canApprove() { return this.auth.hasRole(['ADMIN', 'PROJECT_MANAGER', 'TEAM_LEAD']); }
  get canCreate() { return !this.auth.hasRole(['ADMIN', 'VIEWER']); }

  ngOnInit() { this.load(); }

  load() {
    this.errorMessage.set('');
    if (!this.auth.hasRole(['ADMIN'])) {
      this.api.leaveRequests().subscribe({ next: (items) => this.leaveRequests.set(items) });
      this.api.workLocationRequests().subscribe({ next: (items) => this.locationRequests.set(items) });
      this.api.annualLeaveBalance(Number(this.today.slice(0, 4))).subscribe({
        next: (value) => this.balance.set(value),
        error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể tải quỹ phép năm.'),
      });
    }
    if (this.canApprove) {
      this.api.pendingLeaveRequests().subscribe({ next: (items) => this.pendingLeaves.set(items) });
      this.api.pendingWorkLocationRequests().subscribe({ next: (items) => this.pendingLocations.set(items) });
    }
  }

  selectForm(form: 'LEAVE' | 'LOCATION') {
    this.activeForm.set(form);
    this.errorMessage.set('');
    this.successMessage.set('');
  }

  submitLeave() {
    const draft = this.leaveDraft;
    this.clearMessages();
    if (!this.validDates(draft.fromDate, draft.toDate)) return;
    if (!this.validSessions(draft.fromDate, draft.toDate, draft.fromDayPart, draft.toDayPart)) return;
    if (!draft.reason.trim()) { this.errorMessage.set('Vui lòng nhập lý do nghỉ.'); return; }
    if (draft.type === 'ANNUAL' && this.projectedRemaining() < 0) {
      this.errorMessage.set('Quỹ phép năm không đủ cho đơn này.'); return;
    }
    this.loading.set(true);
    this.api.createLeaveRequest(draft).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: () => {
        this.successMessage.set('Đơn xin nghỉ đã được gửi thành công.');
        this.leaveDraft.reason = '';
        this.load();
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể gửi đơn xin nghỉ.'),
    });
  }

  submitLocation() {
    const draft = this.locationDraft;
    this.clearMessages();
    if (!this.validDates(draft.fromDate, draft.toDate)) return;
    if (!this.validSessions(draft.fromDate, draft.toDate, draft.fromDayPart, draft.toDayPart)) return;
    if (draft.type === 'CLIENT_SITE' && (!draft.clientName.trim() || !draft.clientAddress.trim())) {
      this.errorMessage.set('Vui lòng nhập tên và địa chỉ công ty khách hàng.'); return;
    }
    if (!draft.reason.trim()) { this.errorMessage.set('Vui lòng nhập mục đích và nội dung công việc.'); return; }
    this.loading.set(true);
    this.api.createWorkLocationRequest(draft).pipe(finalize(() => this.loading.set(false))).subscribe({
      next: () => {
        this.successMessage.set('Đơn làm việc ngoài văn phòng đã được gửi.');
        this.locationDraft.reason = '';
        this.load();
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể gửi đơn làm việc ngoài văn phòng.'),
    });
  }

  decideLeave(item: LeaveRequest, approved: boolean) {
    this.api.approveLeaveRequest(item.id, approved ? 'APPROVED' : 'REJECTED').subscribe({
      next: () => { this.successMessage.set(approved ? 'Đã duyệt đơn.' : 'Đã từ chối đơn.'); this.load(); },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể xử lý đơn.'),
    });
  }

  decideLocation(item: WorkLocationRequest, approved: boolean) {
    this.api.decideWorkLocationRequest(item.id, approved ? 'APPROVED' : 'REJECTED').subscribe({
      next: () => { this.successMessage.set(approved ? 'Đã duyệt đơn.' : 'Đã từ chối đơn.'); this.load(); },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể xử lý đơn.'),
    });
  }

  cancelLeave(item: LeaveRequest) {
    this.api.cancelLeaveRequest(item.id).subscribe({ next: () => { this.successMessage.set('Đã hủy đơn nghỉ.'); this.load(); } });
  }

  cancelLocation(item: WorkLocationRequest) {
    this.api.cancelWorkLocationRequest(item.id).subscribe({ next: () => { this.successMessage.set('Đã hủy đơn làm việc ngoài văn phòng.'); this.load(); } });
  }

  canCancel(item: LeaveRequest | WorkLocationRequest) {
    return ['PENDING', 'PENDING_MANAGER', 'PENDING_ADMIN', 'APPROVED'].includes(item.status) && item.fromDate > this.today;
  }

  requestedDays() {
    const draft = this.activeForm() === 'LEAVE' ? this.leaveDraft : this.locationDraft;
    if (!draft.fromDate || !draft.toDate || draft.toDate < draft.fromDate) return 0;
    let sessions = 0;
    const cursor = new Date(`${draft.fromDate}T12:00:00Z`);
    const end = new Date(`${draft.toDate}T12:00:00Z`);
    while (cursor <= end) {
      if (cursor.getUTCDay() !== 0 && cursor.getUTCDay() !== 6) {
        const date = cursor.toISOString().slice(0, 10);
        const firstSession = date === draft.fromDate && draft.fromDayPart === 'AFTERNOON' ? 1 : 0;
        const lastSession = date === draft.toDate && draft.toDayPart === 'MORNING' ? 0 : 1;
        if (firstSession <= lastSession) sessions += lastSession - firstSession + 1;
      }
      cursor.setUTCDate(cursor.getUTCDate() + 1);
    }
    return sessions / 2;
  }

  availableAnnualLeave() { return Math.max(0, (this.balance()?.remainingDays ?? 15) - (this.balance()?.pendingDays ?? 0)); }
  projectedRemaining() { return this.availableAnnualLeave() - (this.leaveDraft.type === 'ANNUAL' ? this.requestedDays() : 0); }
  leavePaidLabel() { return this.leaveDraft.type === 'ANNUAL' ? 'Hưởng lương' : this.leaveDraft.type === 'UNPAID' ? 'Không hưởng lương' : 'Theo chế độ thai sản'; }
  leaveQuotaLabel() { return this.leaveDraft.type === 'ANNUAL' ? 'Trừ quỹ phép năm' : 'Không trừ quỹ phép'; }
  leaveTypeLabel(type: LeaveRequest['type']) { return ({ ANNUAL: 'Nghỉ phép năm', UNPAID: 'Nghỉ không lương', MATERNITY: 'Nghỉ thai sản' } as const)[type]; }
  locationTypeLabel(type: WorkLocationRequest['type']) { return type === 'WORK_FROM_HOME' ? 'Làm việc tại nhà' : 'Onsite tại công ty khách hàng'; }
  statusLabel(status: LeaveRequest['status']) { return ({ PENDING: 'Chờ quản lý', PENDING_MANAGER: 'Chờ quản lý', PENDING_ADMIN: 'Chờ admin', APPROVED: 'Đã duyệt', REJECTED: 'Đã từ chối', REJECTED_MANAGER: 'Quản lý từ chối', REJECTED_ADMIN: 'Admin từ chối', CANCELLED: 'Đã hủy' } as const)[status]; }
  dayPartLabel(part: LeaveDayPart) { return part === 'MORNING' ? 'Buổi sáng' : 'Buổi chiều'; }
  leaveSessionChanged() { if (this.leaveDraft.fromDate === this.leaveDraft.toDate && this.leaveDraft.fromDayPart === 'AFTERNOON' && this.leaveDraft.toDayPart === 'MORNING') this.leaveDraft.toDayPart = 'AFTERNOON'; }
  locationSessionChanged() { if (this.locationDraft.fromDate === this.locationDraft.toDate && this.locationDraft.fromDayPart === 'AFTERNOON' && this.locationDraft.toDayPart === 'MORNING') this.locationDraft.toDayPart = 'AFTERNOON'; }
  isWorkday(date: string) { const day = new Date(`${date}T12:00:00Z`).getUTCDay(); return day !== 0 && day !== 6; }
  private validDates(from: string, to: string) { if (!from || !to || from < this.today || to < from) { this.errorMessage.set('Ngày bắt đầu phải từ hôm nay và ngày kết thúc không được trước ngày bắt đầu.'); return false; } return true; }
  private validSessions(from: string, to: string, fromPart: LeaveDayPart, toPart: LeaveDayPart) { if (from === to && fromPart === 'AFTERNOON' && toPart === 'MORNING') { this.errorMessage.set('Buổi kết thúc không được trước buổi bắt đầu.'); return false; } return true; }
  private clearMessages() { this.errorMessage.set(''); this.successMessage.set(''); }
}
