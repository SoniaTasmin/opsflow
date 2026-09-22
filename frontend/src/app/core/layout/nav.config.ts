import { Role } from '../auth/auth.service';

export interface NavItem {
  label: string;
  path: string;
  icon: string;
  roles: readonly Role[];
}

export const NAV_ITEMS: readonly NavItem[] = [
  { label: 'Home', path: '/home', icon: 'home', roles: ['USER', 'REVIEWER', 'ADMIN'] },
  {
    label: 'Period summary',
    path: '/summary',
    icon: 'donut_large',
    roles: ['USER', 'REVIEWER', 'ADMIN'],
  },
  {
    label: 'Submissions',
    path: '/submissions',
    icon: 'assignment',
    roles: ['USER', 'REVIEWER', 'ADMIN'],
  },
  { label: 'Review queue', path: '/review', icon: 'fact_check', roles: ['REVIEWER', 'ADMIN'] },
  { label: 'CSV imports', path: '/imports', icon: 'upload_file', roles: ['USER', 'ADMIN'] },
  { label: 'Organizations', path: '/admin/organizations', icon: 'apartment', roles: ['ADMIN'] },
  { label: 'Metrics', path: '/admin/metrics', icon: 'straighten', roles: ['ADMIN'] },
  { label: 'Reporting periods', path: '/admin/periods', icon: 'event', roles: ['ADMIN'] },
];
