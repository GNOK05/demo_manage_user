import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { map } from 'rxjs';
import { ApiResponse, Attendance, Project, Task, TaskStatus, User } from './models';
const API = 'http://localhost:8080/api/v1';
@Injectable({ providedIn: 'root' })
export class CompanyApiService {
  constructor(private http: HttpClient) {}
  projects() { return this.http.get<ApiResponse<Project[]>>(`${API}/projects`).pipe(map(r => r.data)); }
  tasks() { return this.http.get<ApiResponse<Task[]>>(`${API}/tasks/my`).pipe(map(r => r.data)); }
  tasksByProject(id: number) { return this.http.get<ApiResponse<Task[]>>(`${API}/tasks/project/${id}`).pipe(map(r => r.data)); }
  updateTaskStatus(id: number, status: TaskStatus) { return this.http.patch<ApiResponse<Task>>(`${API}/tasks/${id}/status`, {status}).pipe(map(r => r.data)); }
  checkIn() { return this.http.post<ApiResponse<Attendance>>(`${API}/attendance/check-in`, {}).pipe(map(r => r.data)); }
  checkOut() { return this.http.post<ApiResponse<Attendance>>(`${API}/attendance/check-out`, {}).pipe(map(r => r.data)); }
  users() { return this.http.get<ApiResponse<User[]>>(`${API}/users`).pipe(map(r => r.data)); }
  departmentMembers() { return this.http.get<ApiResponse<User[]>>(`${API}/users/department`).pipe(map(r => r.data)); }
  myAttendance() { return this.http.get<ApiResponse<Attendance[]>>(`${API}/attendance/my`).pipe(map(r => r.data)); }
  departmentAttendance() { return this.http.get<ApiResponse<Attendance[]>>(`${API}/attendance/department`).pipe(map(r => r.data)); }
  allAttendance() { return this.http.get<ApiResponse<Attendance[]>>(`${API}/attendance`).pipe(map(r => r.data)); }
}
