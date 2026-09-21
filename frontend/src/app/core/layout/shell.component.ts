import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { LoadingIndicator } from '../http/loading.interceptor';
import { NAV_ITEMS } from './nav.config';

@Component({
  selector: 'app-shell',
  imports: [
    RouterOutlet,
    RouterLink,
    RouterLinkActive,
    MatToolbarModule,
    MatSidenavModule,
    MatListModule,
    MatIconModule,
    MatButtonModule,
    MatMenuModule,
    MatProgressBarModule,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  protected readonly loading = inject(LoadingIndicator);

  protected readonly sidenavOpen = signal(true);
  protected readonly displayName = this.auth.displayName;
  protected readonly roles = this.auth.roles;
  protected readonly organizations = this.auth.organizations;

  /** The nav offers only what the user's roles allow, matching the route guards. */
  protected readonly navItems = () => NAV_ITEMS.filter((item) => this.auth.hasAnyRole(item.roles));

  protected toggleSidenav(): void {
    this.sidenavOpen.update((open) => !open);
  }

  protected logout(): void {
    this.auth.logout();
  }
}
