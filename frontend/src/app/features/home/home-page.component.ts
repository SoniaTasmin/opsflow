import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

import { AuthService } from '../../core/auth/auth.service';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';

@Component({
  selector: 'app-home-page',
  imports: [MatCardModule, MatButtonModule, RouterLink, PageHeaderComponent, StatusChipComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-page-header title="Welcome" [subtitle]="auth.displayName()" />

    <div class="cards">
      <mat-card>
        <mat-card-header><mat-card-title>Your access</mat-card-title></mat-card-header>
        <mat-card-content>
          <p>Roles: {{ auth.roles().join(', ') || 'none' }}</p>
          @if (auth.isAdmin()) {
            <a matButton="filled" routerLink="/admin/organizations">Administration</a>
          }
        </mat-card-content>
      </mat-card>

      <mat-card>
        <mat-card-header><mat-card-title>Your organizations</mat-card-title></mat-card-header>
        <mat-card-content>
          @if (auth.organizations().length) {
            <ul class="orgs">
              @for (organization of auth.organizations(); track organization.id) {
                <li>
                  <strong>{{ organization.code }}</strong> — {{ organization.name }}
                  <app-status-chip [status]="organization.active ? 'ACTIVE' : 'ARCHIVED'" />
                </li>
              }
            </ul>
          } @else {
            <p>You do not belong to any organization yet.</p>
          }
        </mat-card-content>
      </mat-card>
    </div>
  `,
  styles: `
    .cards {
      display: grid;
      gap: 1rem;
      grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr));
    }
    .orgs {
      margin: 0;
      padding-left: 1rem;
      display: grid;
      gap: 0.4rem;
    }
  `,
})
export class HomePageComponent {
  protected readonly auth = inject(AuthService);
}
