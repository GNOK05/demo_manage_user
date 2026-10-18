import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs';
import {
  ApiResponse,
  AnnualLeaveBalance,
  AttendanceAdjustment,
  Attendance,
  DepartmentAttendanceSummary,
  DashboardSummary,
  Department,
  LeaveRequest,
  NotificationItem,
  PayrollMonthlyReport,
  Project,
  ProjectDepartment,
  ProjectSaveRequest,
  Task,
  TaskPriority,
  TaskSaveRequest,
  TaskSort,
  TaskStatus,
  User,
  WorkRequest,
  WorkRequestHealth,
  WorkRequestSaveRequest,
  WorkRequestStatus,
  WorkLocationRequest,
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

  leaveRequestsForUser(userId: number) {
    return this.http
      .get<ApiResponse<LeaveRequest[]>>(`${API}/leave-requests/user/${userId}`)
      .pipe(map((r) => r.data));
  }

  annualLeaveBalance(year: number) {
    return this.http
      .get<ApiResponse<AnnualLeaveBalance>>(`${API}/leave-requests/balance`, { params: { year } })
      .pipe(map((r) => r.data));
  }

  annualLeaveBalanceForUser(userId: number, year: number) {
    return this.http
      .get<ApiResponse<AnnualLeaveBalance>>(`${API}/leave-requests/balance/${userId}`, { params: { year } })
      .pipe(map((r) => r.data));
  }

  setAnnualLeaveBalance(userId: number, year: number, entitledDays: number) {
    return this.http
      .put<ApiResponse<AnnualLeaveBalance>>(`${API}/leave-requests/balance/${userId}`, { entitledDays }, { params: { year } })
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
        fromDayPart: request.fromDayPart,
        toDayPart: request.toDayPart,
      })
      .pipe(map((r) => r.data));
  }

  approveLeaveRequest(id: number, status: 'APPROVED' | 'REJECTED') {
    return this.http
      .patch<ApiResponse<LeaveRequest>>(`${API}/leave-requests/${id}/approve`, { status })
      .pipe(map((r) => r.data));
  }

  cancelLeaveRequest(id: number) {
    return this.http
      .post<ApiResponse<LeaveRequest>>(`${API}/leave-requests/${id}/cancel`, {})
      .pipe(map((r) => r.data));
  }

  departments() {
    return this.http.get<ApiResponse<Department[]>>(`${API}/departments`).pipe(map((r) => r.data));
  }
  projects(departmentId?: number | null, status?: string | null, keyword = '') {
    return this.http
      .get<ApiResponse<Project[]>>(`${API}/projects`, {
        params: {
          keyword: keyword.trim(),
          ...(departmentId ? { departmentId } : {}),
          ...(status ? { status } : {}),
        },
      })
      .pipe(map((r) => r.data));
  }
  tasks(
    keyword = '',
    role?: string | null,
    status?: TaskStatus | null,
    sort: TaskSort = 'DEFAULT',
    priority?: TaskPriority | null,
    overdue = false,
    workRequestId?: number | null,
  ) {
    return this.http
      .get<ApiResponse<Task[]>>(`${API}/tasks`, {
        params: {
          keyword: keyword.trim(),
          ...(role ? { role } : {}),
          ...(status ? { status } : {}),
          ...(priority ? { priority } : {}),
          ...(overdue ? { overdue: true } : {}),
          ...(workRequestId ? { workRequestId } : {}),
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

  workLocationRequests() {
    return this.http.get<ApiResponse<WorkLocationRequest[]>>(`${API}/work-location-requests/my`).pipe(map((r) => r.data));
  }

  pendingWorkLocationRequests() {
    return this.http.get<ApiResponse<WorkLocationRequest[]>>(`${API}/work-location-requests/pending`).pipe(map((r) => r.data));
  }

  createWorkLocationRequest(request: Partial<WorkLocationRequest>) {
    return this.http.post<ApiResponse<WorkLocationRequest>>(`${API}/work-location-requests`, request).pipe(map((r) => r.data));
  }

  decideWorkLocationRequest(id: number, status: 'APPROVED' | 'REJECTED') {
    return this.http.patch<ApiResponse<WorkLocationRequest>>(`${API}/work-location-requests/${id}/decision`, { status }).pipe(map((r) => r.data));
  }

  cancelWorkLocationRequest(id: number) {
    return this.http.post<ApiResponse<WorkLocationRequest>>(`${API}/work-location-requests/${id}/cancel`, {}).pipe(map((r) => r.data));
  }
  project(id: number) {
    return this.http.get<ApiResponse<Project>>(`${API}/projects/${id}`).pipe(map((r) => r.data));
  }
  createProject(payload: ProjectSaveRequest) {
    return this.http.post<ApiResponse<Project>>(`${API}/projects`, payload).pipe(map((r) => r.data));
  }
  updateProject(id: number, payload: ProjectSaveRequest) {
    return this.http.put<ApiResponse<Project>>(`${API}/projects/${id}`, payload).pipe(map((r) => r.data));
  }
  deleteProject(id: number) { return this.http.delete<void>(`${API}/projects/${id}`); }
  addProjectDepartment(projectId: number, departmentId: number) {
    return this.http
      .post<ApiResponse<ProjectDepartment[]>>(`${API}/projects/${projectId}/departments/${departmentId}`, {})
      .pipe(map((r) => r.data));
  }
  removeProjectDepartment(projectId: number, departmentId: number) {
    return this.http
      .delete<ApiResponse<ProjectDepartment[]>>(`${API}/projects/${projectId}/departments/${departmentId}`)
      .pipe(map((r) => r.data));
  }
  tasksByWorkRequest(id: number) {
    return this.http
      .get<ApiResponse<Task[]>>(`${API}/tasks/work-request/${id}`)
      .pipe(map((r) => r.data));
  }
  workRequests(filters: {
    keyword?: string;
    projectId?: number | null;
    departmentId?: number | null;
    status?: WorkRequestStatus | null;
    priority?: TaskPriority | null;
    health?: WorkRequestHealth | null;
    overdue?: boolean;
  } = {}) {
    return this.http
      .get<ApiResponse<WorkRequest[]>>(`${API}/work-requests`, {
        params: {
          keyword: filters.keyword?.trim() || '',
          ...(filters.projectId ? { projectId: filters.projectId } : {}),
          ...(filters.departmentId ? { departmentId: filters.departmentId } : {}),
          ...(filters.status ? { status: filters.status } : {}),
          ...(filters.priority ? { priority: filters.priority } : {}),
          ...(filters.health ? { health: filters.health } : {}),
          ...(filters.overdue ? { overdue: true } : {}),
        },
      })
      .pipe(map((r) => r.data));
  }
  workRequestsByProject(projectId: number) {
    return this.http
      .get<ApiResponse<WorkRequest[]>>(`${API}/projects/${projectId}/work-requests`)
      .pipe(map((r) => r.data));
  }
  workRequest(id: number) {
    return this.http.get<ApiResponse<WorkRequest>>(`${API}/work-requests/${id}`).pipe(map((r) => r.data));
  }
  createWorkRequest(projectId: number, payload: WorkRequestSaveRequest) {
    return this.http
      .post<ApiResponse<WorkRequest>>(`${API}/projects/${projectId}/work-requests`, payload)
      .pipe(map((r) => r.data));
  }
  updateWorkRequest(id: number, payload: WorkRequestSaveRequest) {
    return this.http
      .put<ApiResponse<WorkRequest>>(`${API}/work-requests/${id}`, payload)
      .pipe(map((r) => r.data));
  }
  updateWorkRequestStatus(id: number, status: WorkRequestStatus, reason?: string) {
    return this.http
      .patch<ApiResponse<WorkRequest>>(`${API}/work-requests/${id}/status`, { status, reason })
      .pipe(map((r) => r.data));
  }
  deleteWorkRequest(id: number) {
    return this.http.delete<void>(`${API}/work-requests/${id}`);
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
  updateTaskStatus(id: number, status: TaskStatus, blockedReason?: string) {
    return this.http
      .patch<ApiResponse<Task>>(`${API}/tasks/${id}/status`, { status, blockedReason })
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
  attendanceByDate(date: string) {
    return this.http
      .get<ApiResponse<Attendance[]>>(`${API}/attendance/day`, { params: { date } })
      .pipe(map((r) => r.data));
  }
  departmentAttendance() {
    return this.http
      .get<ApiResponse<Attendance[]>>(`${API}/attendance/department`)
      .pipe(map((r) => r.data));
  }
  departmentAttendanceSummary(date: string) {
    return this.http
      .get<ApiResponse<DepartmentAttendanceSummary>>(`${API}/attendance/department/summary`, { params: { date } })
      .pipe(map((r) => r.data));
  }
  changePassword(currentPassword: string, newPassword: string) {
    return this.http.patch<ApiResponse<void>>(`${API}/auth/change-password`, { currentPassword, newPassword });
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
  myAttendanceAdjustments() {
    return this.http
      .get<ApiResponse<AttendanceAdjustment[]>>(`${API}/attendance/adjustments/my`)
      .pipe(map((r) => r.data));
  }
  pendingAttendanceAdjustments() {
    return this.http
      .get<ApiResponse<AttendanceAdjustment[]>>(`${API}/attendance/adjustments/pending`)
      .pipe(map((r) => r.data));
  }
  createAttendanceAdjustment(request: {
    userId?: number;
    sessionId: number;
    requestedCheckIn: string;
    requestedCheckOut: string;
    reason: string;
  }) {
    return this.http
      .post<ApiResponse<AttendanceAdjustment>>(`${API}/attendance/adjustments`, request)
      .pipe(map((r) => r.data));
  }
  decideAttendanceAdjustment(id: number, status: 'APPROVED' | 'REJECTED_MANAGER' | 'REJECTED_ADMIN') {
    return this.http
      .patch<ApiResponse<AttendanceAdjustment>>(`${API}/attendance/adjustments/${id}/decision`, { status })
      .pipe(map((r) => r.data));
  }
  payrollMonthly(year: number, month: number) {
    return this.http
      .get<ApiResponse<PayrollMonthlyReport>>(`${API}/payroll/monthly`, { params: { year, month } })
      .pipe(map((r) => r.data));
  }
  payrollExcel(year: number, month: number) {
    return this.http.get(`${API}/payroll/monthly.xlsx`, { params: { year, month }, responseType: 'blob' });
  }
  closePayrollPeriod(year: number, month: number) {
    return this.http
      .post<ApiResponse<PayrollMonthlyReport>>(`${API}/payroll/${year}/${month}/close`, {})
      .pipe(map((r) => r.data));
  }
  reopenPayrollPeriod(year: number, month: number, reason: string) {
    return this.http
      .post<ApiResponse<PayrollMonthlyReport>>(`${API}/payroll/${year}/${month}/reopen`, { reason })
      .pipe(map((r) => r.data));
  }
}
