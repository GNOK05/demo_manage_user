export type Role = 'ADMIN' | 'MANAGER' | 'EMPLOYEE';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'REVIEW';
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
export interface Attendance {
  id: number;
  userId: number;
  userName?: string;
  date: string;
  checkInTime?: string;
  checkOutTime?: string;
  status: string;
}

export type LeaveStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface LeaveRequest {
  id: number;
  userId: number;
  userName: string;
  type: 'ANNUAL' | 'PERSONAL' | 'SICK' | 'MATERNITY';
  fromDate: string;
  toDate: string;
  reason: string;
  status: LeaveStatus;
  createdAt: string;
  approvedBy?: string;
}

export interface NotificationItem {
  id: number;
  type: 'TASK' | 'SYSTEM' | 'LEAVE';
  title: string;
  message: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH';
  createdAt: string;
  relatedId?: number;
}
