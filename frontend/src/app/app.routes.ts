import { Routes } from '@angular/router';

import { authGuard } from './core/auth/auth.guard';
import { roleGuard } from './core/auth/role.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'home' },
  {
    path: 'home',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/home/home-page.component').then((m) => m.HomePageComponent),
  },
  {
    path: 'summary',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/summary/period-summary.component').then((m) => m.PeriodSummaryComponent),
  },
  {
    path: 'submissions',
    canActivate: [authGuard],
    loadChildren: () =>
      import('./features/submissions/submissions.routes').then((m) => m.submissionRoutes),
  },
  {
    path: 'imports',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['USER', 'ADMIN'] },
    loadChildren: () => import('./features/imports/imports.routes').then((m) => m.importRoutes),
  },
  {
    path: 'review',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['REVIEWER', 'ADMIN'] },
    loadComponent: () =>
      import('./features/submissions/review-queue.component').then((m) => m.ReviewQueueComponent),
  },
  {
    path: 'admin',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN'] },
    loadChildren: () => import('./features/admin/admin.routes').then((m) => m.adminRoutes),
  },
  {
    path: 'forbidden',
    loadComponent: () =>
      import('./features/home/forbidden-page.component').then((m) => m.ForbiddenPageComponent),
  },
  { path: '**', redirectTo: 'home' },
];
