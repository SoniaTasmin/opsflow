import { Routes } from '@angular/router';

export const adminRoutes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'organizations' },
  {
    path: 'organizations',
    loadComponent: () =>
      import('./organizations/organization-list.component').then(
        (m) => m.OrganizationListComponent,
      ),
  },
  {
    path: 'organizations/:id/members',
    loadComponent: () =>
      import('./organizations/organization-members.component').then(
        (m) => m.OrganizationMembersComponent,
      ),
  },
  {
    path: 'metrics',
    loadComponent: () =>
      import('./metrics/metric-list.component').then((m) => m.MetricListComponent),
  },
  {
    path: 'periods',
    loadComponent: () =>
      import('./periods/period-list.component').then((m) => m.PeriodListComponent),
  },
  {
    path: 'periods/:id',
    loadComponent: () =>
      import('./periods/period-detail.component').then((m) => m.PeriodDetailComponent),
  },
];
