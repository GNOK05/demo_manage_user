import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { SidebarComponent } from '../../core/layout/sidebar.component';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { User } from '../../core/models';
import { finalize } from 'rxjs';

@Component({
  standalone: true,
  imports: [CommonModule, FormsModule, SidebarComponent],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss',
})
export class SettingsComponent implements OnInit {
  activeTab = signal<'PROFILE' | 'SECURITY'>('PROFILE');
  profile = signal<User | null>(null);
  loadingProfile = signal(false);
  savingPassword = signal(false);
  errorMessage = signal('');
  successMessage = signal('');
  showCurrentPassword = false;
  showNewPassword = false;
  showConfirmPassword = false;
  passwordForm = { currentPassword: '', newPassword: '', confirmPassword: '' };

  constructor(
    public auth: AuthService,
    private api: CompanyApiService,
  ) {}

  ngOnInit() {
    this.loadingProfile.set(true);
    this.auth.me().pipe(finalize(() => this.loadingProfile.set(false))).subscribe({
      next: (response) => this.profile.set(response.data),
      error: () => {
        this.errorMessage.set('Không thể tải thông tin tài khoản.');
        this.profile.set(this.auth.user());
      },
    });
  }

  roleLabel(role?: User['role']) {
    return ({ ADMIN: 'Quản trị viên', MANAGER: 'Quản lý', EMPLOYEE: 'Nhân viên' } as Record<User['role'], string>)[role ?? 'EMPLOYEE'];
  }

  changePassword() {
    this.errorMessage.set('');
    this.successMessage.set('');
    const { currentPassword, newPassword, confirmPassword } = this.passwordForm;
    if (!currentPassword || !newPassword || !confirmPassword) {
      this.errorMessage.set('Vui lòng nhập đầy đủ các trường mật khẩu.');
      return;
    }
    if (newPassword.length < 6) {
      this.errorMessage.set('Mật khẩu mới phải có ít nhất 6 ký tự.');
      return;
    }
    if (newPassword !== confirmPassword) {
      this.errorMessage.set('Mật khẩu mới nhập lại không khớp.');
      return;
    }

    this.savingPassword.set(true);
    this.api.changePassword(currentPassword, newPassword)
      .pipe(finalize(() => this.savingPassword.set(false)))
      .subscribe({
      next: () => {
        this.successMessage.set('Đổi mật khẩu thành công.');
        this.passwordForm = { currentPassword: '', newPassword: '', confirmPassword: '' };
      },
      error: (error) => this.errorMessage.set(error?.error?.message || 'Không thể đổi mật khẩu.'),
    });
  }
}
