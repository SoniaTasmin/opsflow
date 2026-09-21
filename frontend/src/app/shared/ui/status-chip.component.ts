import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-status-chip',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="chip" [class]="'chip--' + tone()">{{ status() }}</span>`,
  styles: `
    .chip {
      display: inline-block;
      padding: 0.15rem 0.6rem;
      border-radius: 1rem;
      font-size: 0.75rem;
      font-weight: 600;
      letter-spacing: 0.02em;
    }
    .chip--neutral {
      background: #e3e6ea;
      color: #39424e;
    }
    .chip--positive {
      background: #d8f0dd;
      color: #14532d;
    }
    .chip--muted {
      background: #ececec;
      color: #6b7280;
    }
  `,
})
export class StatusChipComponent {
  readonly status = input.required<string>();

  readonly tone = computed(() => {
    switch (this.status()) {
      case 'ACTIVE':
      case 'OPEN':
        return 'positive';
      case 'ARCHIVED':
      case 'CLOSED':
        return 'muted';
      default:
        return 'neutral';
    }
  });
}
