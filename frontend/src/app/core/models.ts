export type Role = 'ADMIN' | 'MANAGER' | 'EMPLOYEE';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'REVIEW';
export type TaskSort =
  'DEFAULT' | 'NEWEST' | 'OLDEST' | 'DEADLINE_ASC' | 'DEADLINE_DESC' | 'NAME_ASC' | 'NAME_DESC';
export interface User {
  id: number;
  username: string;
  fullName: string;
  email: string;
  phone?: string;
  role: Role;
  jobTitle?: string;
  departmentId?: number;
  departmentName?: string;
}
export interface ApiResponse<T> {
  data: T;
  message: string;
}
export interface DashboardSummary {
  totalEmployees: number;
  totalDepartments: number;
  totalProjects: number;
  activeProjects: number;
  completedProjects: number;
  totalTasks: number;
  pendingTasks: number;
  completedTasks: number;
  overdueTasks: number;
  attendanceToday: Record<string, number>;
  pendingLeaveRequests: number;
}
export interface Project {
  id: number;
  projectName: string;
  description?: string;
  departmentId: number;
  departmentName: string;
  startDate: string;
  endDate: string;
  status: string;
  totalTasks: number;
  completedTasks: number;
  progress: number;
}
export interface Department {
  id: number;
  name: string;
  code: string;
  description?: string;
  managerId?: number;
  managerName?: string;
}
export interface Task {
  id: number;
  taskName: string;
  description?: string;
  projectId: number;
  projectName: string;
  assignedToId?: number;
  assignedToName?: string;
  testerId?: number;
  testerName?: string;
  status: TaskStatus;
  deadline: string;
}
export interface TaskSaveRequest {
  taskName: string;
  description?: string;
  projectId: number | null;
  assignedToId: number | null;
  testerId: number | null;
  status?: TaskStatus;
  deadline: string;
}
export interface Attendance {
  id: number;
  userId: number;
  userName?: string;
  date: string;
  checkInTime?: string;
  checkOutTime?: string;
  status: string;
  sessions?: { id?: number; checkInTime: string; checkOutTime?: string }[];
}

export type LeaveStatus =
  | 'PENDING'
  | 'PENDING_MANAGER'
  | 'PENDING_ADMIN'
  | 'APPROVED'
  | 'REJECTED'
  | 'REJECTED_MANAGER'
  | 'REJECTED_ADMIN'
  | 'CANCELLED';

export type LeaveType = 'ANNUAL' | 'UNPAID' | 'PERSONAL' | 'SICK' | 'MATERNITY';

export interface LeaveRequest {
  id: number;
  userId: number;
  userName: string;
  type: LeaveType;
  fromDate: string;
  toDate: string;
  reason: string;
  status: LeaveStatus;
  createdAt: string;
  approvedBy?: string;
  managerApprovedBy?: string;
  adminApprovedBy?: string;
  rejectionReason?: string;
  cancelledAt?: string;
  requestedWorkdays: number;
}

export interface AnnualLeaveBalance {
  userId: number;
  userName: string;
  year: number;
  entitledDays: number;
  usedDays: number;
  pendingDays: number;
  remainingDays: number;
}

export interface PayrollMonthlyReport {
  year: number;
  month: number;
  closed: boolean;
  closedAt?: string;
  closedBy?: string;
  employees: {
    userId: number;
    username: string;
    fullName: string;
    department: string;
    workDays: number;
    payrollWorkDays: number;
    lateDays: number;
    absentDays: number;
    workedHours: number;
    annualLeaveDays: number;
    unpaidLeaveDays: number;
    otherLeaveDays: number;
  }[];
}

export type AttendanceAdjustmentStatus =
  | 'PENDING_MANAGER'
  | 'PENDING_ADMIN'
  | 'APPROVED'
  | 'REJECTED_MANAGER'
  | 'REJECTED_ADMIN';

export interface AttendanceAdjustment {
  id: number;
  sessionId: number;
  userId: number;
  userName: string;
  requestedBy: string;
  date: string;
  originalCheckIn: string;
  originalCheckOut?: string;
  requestedCheckIn: string;
  requestedCheckOut: string;
  reason: string;
  status: AttendanceAdjustmentStatus;
  managerApprovedBy?: string;
  adminApprovedBy?: string;
  createdAt: string;
}

export interface NotificationItem {
  id: number;
  type: 'TASK' | 'SYSTEM' | 'LEAVE';
  title: string;
  message: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH';
  createdAt: string;
  relatedId?: number;
  key: string;
  unread: boolean;
}
