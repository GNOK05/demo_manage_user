import { Routes } from '@angular/router';
import { authGuard, roleGuard } from './core/auth.guard';
import { LoginComponent } from './pages/login/login.component';
import { DashboardComponent } from './pages/dashboard/dashboard.component';
import { ProjectListComponent } from './pages/project-list/project-list.component';
import { TaskBoardComponent } from './pages/task-board/task-board.component';
import { PeopleComponent } from './pages/people/people.component';
import { AttendanceComponent } from './pages/attendance/attendance.component';
import { NotificationsComponent } from './pages/notifications/notifications.component';
import { WorkRequestsComponent } from './pages/work-requests/work-requests.component';
import { RequestsComponent } from './pages/requests/requests.component';

export const routes: Routes = [
  { path: 'login', component: LoginComponent },
  { path: '', component: DashboardComponent, canActivate: [authGuard] },
  { path: 'work', component: ProjectListComponent, canActivate: [authGuard] },
  { path: 'work/projects/:projectId/requests', component: WorkRequestsComponent, canActivate: [authGuard] },
  { path: 'work/projects/:projectId/requests/:requestId', component: TaskBoardComponent, canActivate: [authGuard] },
  { path: 'work/requests/:requestId', component: TaskBoardComponent, canActivate: [authGuard] },
  { path: 'projects', redirectTo: 'work', pathMatch: 'full' },
  { path: 'tasks', redirectTo: 'work', pathMatch: 'full' },
  { path: 'tasks/projects/:projectId/requests', redirectTo: 'work/projects/:projectId/requests' },
  { path: 'tasks/requests/:requestId/tasks', redirectTo: 'work/requests/:requestId' },
  { path: 'work-requests', redirectTo: 'work', pathMatch: 'full' },
  {
    path: 'people',
    component: PeopleComponent,
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'HR', 'PROJECT_MANAGER', 'TEAM_LEAD'] },
  },
  { path: 'attendance', component: AttendanceComponent, canActivate: [authGuard] },
  { path: 'requests', component: RequestsComponent, canActivate: [authGuard] },
  { path: 'notifications', component: NotificationsComponent, canActivate: [authGuard] },
  {
    path: 'settings',
    loadComponent: () => import('./pages/settings/settings.component').then((module) => module.SettingsComponent),
    canActivate: [authGuard],
  },
  { path: '**', redirectTo: '' },
];
