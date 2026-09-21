import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-page-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <div>
        <h1>{{ title() }}</h1>
        @if (subtitle()) {
          <p class="page-header__subtitle">{{ subtitle() }}</p>
        }
      </div>
      <div class="page-header__actions"><ng-content /></div>
    </header>
  `,
  styles: `
    .page-header {
      display: flex;
      flex-wrap: wrap;
      gap: 1rem;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 1.5rem;
    }
    h1 {
      margin: 0;
      font-size: 1.6rem;
    }
    .page-header__subtitle {
      margin: 0.25rem 0 0;
      color: var(--mat-sys-on-surface-variant);
    }
    .page-header__actions {
      display: flex;
      gap: 0.5rem;
    }
  `,
})
export class PageHeaderComponent {
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
}
