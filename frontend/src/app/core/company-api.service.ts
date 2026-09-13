import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs';
import {
  ApiResponse,
  Attendance,
  DashboardSummary,
  Department,
  LeaveRequest,
  NotificationItem,
  Project,
  Task,
  TaskSaveRequest,
  TaskSort,
  TaskStatus,
  User,
} from './models';

// Use a relative URL so the application works behind a reverse proxy and on
// any deployed host. Angular's development server forwards this path through
// proxy.conf.json.
const API = '/api/v1';

@Injectable({ providedIn: 'root' })
export class CompanyApiService {
  constructor(private http: HttpClient) {}

  dashboardSummary() {
    return this.http
      .get<ApiResponse<DashboardSummary>>(`${API}/dashboard/summary`)
      .pipe(map((r) => r.data));
  }

  notifications() {
    return this.http
      .get<ApiResponse<NotificationItem[]>>(`${API}/notifications`)
      .pipe(map((r) => r.data));
  }
  markNotificationRead(key: string) {
    return this.http.post<void>(`${API}/notifications/read`, { key });
  }

  leaveRequests() {
    return this.http
      .get<ApiResponse<LeaveRequest[]>>(`${API}/leave-requests/my`)
      .pipe(map((r) => r.data));
  }

  pendingLeaveRequests() {
    return this.http
      .get<ApiResponse<LeaveRequest[]>>(`${API}/leave-requests/pending`)
      .pipe(map((r) => r.data));
  }

  createLeaveRequest(request: Partial<LeaveRequest>) {
    return this.http
      .post<ApiResponse<LeaveRequest>>(`${API}/leave-requests`, {
        type: request.type,
        fromDate: request.fromDate,
        toDate: request.toDate,
        reason: request.reason,
      })
      .pipe(map((r) => r.data));
  }

  approveLeaveRequest(id: number, status: 'APPROVED' | 'REJECTED') {
    return this.http
      .patch<ApiResponse<LeaveRequest>>(`${API}/leave-requests/${id}/approve`, { status })
      .pipe(map((r) => r.data));
  }

  departments() {
    return this.http.get<ApiResponse<Department[]>>(`${API}/departments`).pipe(map((r) => r.data));
  }
  project(id: number) {
    return this.http.get<ApiResponse<Project>>(`${API}/projects/${id}`).pipe(map((r) => r.data));
  }
  projects(departmentId?: number | null, status?: string | null) {
    return this.http
      .get<ApiResponse<Project[]>>(`${API}/projects`, {
        params: {
          ...(departmentId ? { departmentId } : {}),
          ...(status ? { status } : {}),
        },
      })
      .pipe(map((r) => r.data));
  }
  createProject(payload: Partial<Project>) {
    return this.http
      .post<ApiResponse<Project>>(`${API}/projects`, payload)
      .pipe(map((r) => r.data));
  }
  updateProject(id: number, payload: Partial<Project>) {
    return this.http
      .put<ApiResponse<Project>>(`${API}/projects/${id}`, payload)
      .pipe(map((r) => r.data));
  }
  deleteProject(id: number) {
    return this.http.delete<void>(`${API}/projects/${id}`);
  }
  tasks(
    keyword = '',
    role?: string | null,
    status?: TaskStatus | null,
    sort: TaskSort = 'DEFAULT',
  ) {
    return this.http
      .get<ApiResponse<Task[]>>(`${API}/tasks`, {
        params: {
          keyword: keyword.trim(),
          ...(role ? { role } : {}),
          ...(status ? { status } : {}),
          sort,
        },
      })
      .pipe(map((r) => r.data));
  }
  tasksByProject(id: number) {
    return this.http
      .get<ApiResponse<Task[]>>(`${API}/tasks/project/${id}`)
      .pipe(map((r) => r.data));
  }
  createTask(payload: TaskSaveRequest) {
    return this.http.post<ApiResponse<Task>>(`${API}/tasks`, payload).pipe(map((r) => r.data));
  }
  updateTask(id: number, payload: TaskSaveRequest) {
    return this.http.put<ApiResponse<Task>>(`${API}/tasks/${id}`, payload).pipe(map((r) => r.data));
  }
  deleteTask(id: number) {
    return this.http.delete<void>(`${API}/tasks/${id}`);
  }
  updateTaskStatus(id: number, status: TaskStatus) {
    return this.http
      .patch<ApiResponse<Task>>(`${API}/tasks/${id}/status`, { status })
      .pipe(map((r) => r.data));
  }
  checkIn() {
    return this.http
      .post<ApiResponse<Attendance>>(`${API}/attendance/check-in`, {})
      .pipe(map((r) => r.data));
  }
  checkOut() {
    return this.http
      .post<ApiResponse<Attendance>>(`${API}/attendance/check-out`, {})
      .pipe(map((r) => r.data));
  }
  users(query = '', departmentId?: number | null) {
    return this.http
      .get<ApiResponse<User[]>>(`${API}/users`, {
        params: {
          query,
          ...(departmentId ? { departmentId } : {}),
        },
      })
      .pipe(map((r) => r.data));
  }
  createUser(payload: Partial<User> & { password?: string }) {
    return this.http.post<ApiResponse<User>>(`${API}/users`, payload).pipe(map((r) => r.data));
  }
  updateUser(id: number, payload: Partial<User> & { password?: string }) {
    return this.http.put<ApiResponse<User>>(`${API}/users/${id}`, payload).pipe(map((r) => r.data));
  }
  deleteUser(id: number) {
    return this.http.delete<void>(`${API}/users/${id}`);
  }
  departmentMembers(query = '') {
    return this.http
      .get<ApiResponse<User[]>>(`${API}/users/department`, { params: { query } })
      .pipe(map((r) => r.data));
  }
  myAttendance() {
    return this.http
      .get<ApiResponse<Attendance[]>>(`${API}/attendance/my`)
      .pipe(map((r) => r.data));
  }
  departmentAttendance() {
    return this.http
      .get<ApiResponse<Attendance[]>>(`${API}/attendance/department`)
      .pipe(map((r) => r.data));
  }
  employeeAttendance(userId: number, month: number, year: number) {
    return this.http
      .get<ApiResponse<Attendance[]>>(`${API}/attendance/user/${userId}`, {
        params: { month, year },
      })
      .pipe(map((r) => r.data));
  }
  allAttendance() {
    return this.http.get<ApiResponse<Attendance[]>>(`${API}/attendance`).pipe(map((r) => r.data));
  }
}
