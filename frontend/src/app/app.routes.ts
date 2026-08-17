import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth.guard';
import { LoginComponent } from './pages/login/login.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { ProjectListComponent } from './pages/project-list/project-list.component';
import { TaskBoardComponent } from './pages/task-board/task-board.component';
import { PeopleComponent } from './pages/people/people.component';
import { AttendanceComponent } from './pages/attendance/attendance.component';
import { NotificationsComponent } from './pages/notifications/notifications.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: '', component: DashboardComponent, canActivate: [authGuard] },
  {
    path: 'projects',
    component: ProjectListComponent,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER'] },
  },
  { path: 'tasks', component: TaskBoardComponent, canActivate: [authGuard] },
  {
    path: 'people',
    component: PeopleComponent,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MANAGER'] },
  },
  { path: 'attendance', component: AttendanceComponent, canActivate: [authGuard] },
  { path: 'notifications', component: NotificationsComponent, canActivate: [authGuard] },
  { path: '**', redirectTo: '' },
];
