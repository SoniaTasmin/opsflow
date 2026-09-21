import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-empty-state',
  imports: [MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="empty">
      <mat-icon>{{ icon() }}</mat-icon>
      <p class="empty__message">{{ message() }}</p>
      <ng-content />
    </div>
  `,
  styles: `
    .empty {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.75rem;
      padding: 3rem 1rem;
      color: var(--mat-sys-on-surface-variant);
      text-align: center;
    }
    mat-icon {
      font-size: 2.5rem;
      width: 2.5rem;
      height: 2.5rem;
    }
    .empty__message {
      margin: 0;
    }
  `,
})
export class EmptyStateComponent {
  readonly message = input.required<string>();
  readonly icon = input('inbox');
}
