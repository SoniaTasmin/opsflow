import { Routes } from '@angular/router';

export const submissionRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () =>
      import('./submission-list.component').then((m) => m.SubmissionListComponent),
  },
  {
    path: ':id',
    loadComponent: () =>
      import('./submission-detail.component').then((m) => m.SubmissionDetailComponent),
  },
];
