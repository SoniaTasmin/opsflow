import { Routes } from '@angular/router';

export const importRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    loadComponent: () => import('./import-list.component').then((m) => m.ImportListComponent),
  },
  {
    path: ':id',
    loadComponent: () => import('./import-detail.component').then((m) => m.ImportDetailComponent),
  },
];
