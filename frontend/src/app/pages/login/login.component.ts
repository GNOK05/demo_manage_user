import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/auth.service';
@Component({
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  form;
  error = '';
  loading = false;

  constructor(
    fb: FormBuilder,
    private auth: AuthService,
    private router: Router,
  ) {
    this.form = fb.group({
      username: ['', [Validators.required, Validators.pattern(/^\S+$/)]],
      password: ['', [Validators.required, Validators.pattern(/^\S+$/)]],
    });
  }

  submit() {
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.error = 'Vui lòng kiểm tra tên đăng nhập và mật khẩu';
      return;
    }

    this.error = '';
    this.loading = true;
    const v = this.form.getRawValue();
    this.auth
      .login(v.username!, v.password!)
      .pipe(finalize(() => (this.loading = false)))
      .subscribe({
        next: () => this.router.navigateByUrl('/'),
        error: (e) => {
          this.error =
            e.status === 0
              ? 'Không thể kết nối đến máy chủ. Vui lòng thử lại.'
              : e.status === 401 || e.status === 403
                ? 'Tên đăng nhập hoặc mật khẩu không đúng.'
                : e.error?.message || 'Đăng nhập thất bại. Vui lòng thử lại.';
        },
      });
  }

  showFieldError(field: 'username' | 'password') {
    return this.form.controls[field].touched && this.form.controls[field].invalid;
  }
}
