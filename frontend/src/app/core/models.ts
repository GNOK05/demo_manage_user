export type Role = 'ADMIN' | 'HR' | 'PROJECT_MANAGER' | 'TEAM_LEAD' | 'MEMBER' | 'QA' | 'VIEWER';
export type TaskStatus = 'BACKLOG' | 'TODO' | 'IN_PROGRESS' | 'REVIEW' | 'TESTING' | 'BLOCKED' | 'DONE' | 'CANCELLED';
export type TaskPriority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT';
export type WorkRequestStatus = 'DRAFT' | 'READY' | 'IN_PROGRESS' | 'PENDING_ACCEPTANCE' | 'COMPLETED' | 'CANCELLED';
export type WorkRequestHealth = 'ON_TRACK' | 'AT_RISK' | 'OVERDUE' | 'BLOCKED';
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
  totalRequests: number;
  completedRequests: number;
  participatingDepartments: ProjectDepartment[];
}
export interface ProjectSaveRequest {
  projectName: string;
  description?: string;
  departmentId: number | null;
  startDate: string;
  endDate: string;
  status: 'NOT_STARTED' | 'IN_PROGRESS' | 'COMPLETED' | 'ON_HOLD';
}
export interface ProjectDepartment {
  id: number;
  name: string;
  code: string;
  participationRole: 'OWNER' | 'PARTICIPANT';
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
  workRequestId: number;
  workRequestCode: string;
  workRequestTitle: string;
  executingDepartmentId: number;
  executingDepartmentName: string;
  assignedToId?: number;
  assignedToName?: string;
  testerId?: number;
  testerName?: string;
  createdById?: number;
  status: TaskStatus;
  startDate: string;
  deadline: string;
  priority: TaskPriority;
  estimatedHours?: number;
  acceptanceCriteria?: string;
  blockedReason?: string;
}
export interface TaskSaveRequest {
  taskName: string;
  description?: string;
  projectId: number | null;
  workRequestId: number | null;
  assignedToId: number | null;
  testerId: number | null;
  status?: TaskStatus;
  startDate?: string;
  deadline: string;
  priority?: TaskPriority;
  estimatedHours?: number | null;
  acceptanceCriteria?: string;
  blockedReason?: string;
}
export interface WorkRequest {
  id: number;
  requestCode: string;
  projectId: number;
  projectName: string;
  executingDepartmentId: number;
  executingDepartmentName: string;
  coordinatorId?: number;
  coordinatorName?: string;
  createdById: number;
  title: string;
  description?: string;
  acceptanceCriteria?: string;
  priority: TaskPriority;
  status: WorkRequestStatus;
  health: WorkRequestHealth;
  startDate: string;
  deadline: string;
  totalTasks: number;
  completedTasks: number;
  blockedTasks: number;
  overdueTasks: number;
  progress: number;
}
export interface WorkRequestSaveRequest {
  title: string;
  description?: string;
  executingDepartmentId: number | null;
  coordinatorId: number | null;
  startDate: string;
  deadline: string;
  priority: TaskPriority;
  acceptanceCriteria?: string;
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

export interface DepartmentAttendanceSummary {
  date: string;
  totalEmployees: number;
  present: number;
  late: number;
  absent: number;
  leave: number;
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

export type LeaveType = 'ANNUAL' | 'UNPAID' | 'MATERNITY';
export type LeaveDayPart = 'MORNING' | 'AFTERNOON';

export interface LeaveRequest {
  id: number;
  userId: number;
  userName: string;
  type: LeaveType;
  fromDate: string;
  toDate: string;
  fromDayPart: LeaveDayPart;
  toDayPart: LeaveDayPart;
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

export type WorkLocationType = 'WORK_FROM_HOME' | 'CLIENT_SITE';

export interface WorkLocationRequest {
  id: number;
  userId: number;
  userName: string;
  type: WorkLocationType;
  fromDate: string;
  toDate: string;
  fromDayPart: LeaveDayPart;
  toDayPart: LeaveDayPart;
  requestedWorkdays: number;
  clientName?: string;
  clientAddress?: string;
  reason: string;
  status: LeaveStatus;
  managerApprovedBy?: string;
  adminApprovedBy?: string;
  cancelledAt?: string;
  createdAt: string;
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
  type: 'TASK' | 'REQUEST' | 'SYSTEM' | 'LEAVE';
  title: string;
  message: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH';
  createdAt: string;
  relatedId?: number;
  key: string;
  unread: boolean;
}
