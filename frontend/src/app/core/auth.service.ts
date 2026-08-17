import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs';
import { ApiResponse, User } from './models';
const API = 'http://localhost:8080/api/v1';
@Injectable({ providedIn: 'root' })
export class AuthService {
  readonly user = signal<User | null>(this.readUser());
  private logoutTimer?: ReturnType<typeof setTimeout>;
  constructor(private http: HttpClient, private router: Router)
  { this.scheduleAutoLogout(this.token()); }
  login(username: string, password: string) {
    return this.http.post<ApiResponse<{token: string; user: User}>>(`${API}/auth/login`, {username, password}).pipe(tap(r => {
      localStorage.setItem('token', r.data.token);
      localStorage.setItem('user', JSON.stringify(r.data.user));
      this.user.set(r.data.user); this.scheduleAutoLogout(r.data.token);
    })); }
  me() {
    return this.http.get<ApiResponse<User>>(`${API}/auth/me`).pipe(tap(r => {
      localStorage.setItem('user', JSON.stringify(r.data)); this.user.set(r.data);
    })); }
  token() {
    return localStorage.getItem('token');
  }
  hasRole(roles: string[]) {
    return !!this.user() && roles.includes(this.user()!.role);
  }
  logout() {
    if (this.logoutTimer) clearTimeout(this.logoutTimer);
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    this.user.set(null);
    this.router.navigateByUrl('/login'); }
  private readUser(): User | null {
    try {
      return JSON.parse(localStorage.getItem('user') || 'null');
    } catch { return null; }
  }
  private scheduleAutoLogout(token: string | null) {
    if (this.logoutTimer) clearTimeout(this.logoutTimer);
    if (!token) return;
    try {
      const base64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const payload = JSON.parse(atob(base64.padEnd(base64.length + (4 - base64.length % 4) % 4, '=')));
      const remaining = payload.exp * 1000 - Date.now();
      if (remaining <= 0) { this.logout(); return; }
      this.logoutTimer = setTimeout(() => this.logout(), remaining);
    } catch { this.logout(); }
  }
}
