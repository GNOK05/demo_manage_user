export type Role = 'ADMIN' | 'MANAGER' | 'EMPLOYEE';
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE' | 'REVIEW';
export interface User { id: number; username: string; fullName: string; email: string; phone?: string; role: Role; departmentId?: number; departmentName?: string; }
export interface ApiResponse<T> { data: T; message: string; }
export interface Project { id: number; projectName: string; description?: string; departmentId: number; departmentName: string; startDate: string; endDate: string; status: string; totalTasks: number; completedTasks: number; progress: number; }
export interface Task { id: number; taskName: string; description?: string; projectId: number; projectName: string; assignedToId?: number; assignedToName?: string; status: TaskStatus; deadline: string; }
export interface Attendance { id: number; date: string; checkInTime?: string; checkOutTime?: string; status: string; }
