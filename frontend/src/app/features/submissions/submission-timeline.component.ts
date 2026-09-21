import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { DatePipe } from '@angular/common';
import { MatIconModule } from '@angular/material/icon';

import { TransitionResponse } from '../../core/api/generated';

@Component({
  selector: 'app-submission-timeline',
  imports: [DatePipe, MatIconModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="timeline">
      @for (entry of entries(); track $index) {
        <li>
          <mat-icon class="icon">{{ icon(entry.toStatus) }}</mat-icon>
          <div class="body">
            <span class="headline">
              @if (entry.fromStatus) {
                {{ entry.fromStatus }} → {{ entry.toStatus }}
              } @else {
                Started as {{ entry.toStatus }}
              }
            </span>
            <span class="meta">
              {{ entry.occurredAt | date: 'medium' }}
              @if (entry.actorName) {
                · {{ entry.actorName }}
              }
            </span>
            @if (entry.reason) {
              <p class="reason">{{ entry.reason }}</p>
            }
          </div>
        </li>
      }
    </ol>
  `,
  styles: `
    .timeline {
      list-style: none;
      margin: 0;
      padding: 0;
    }
    li {
      display: flex;
      gap: 0.75rem;
      padding: 0.5rem 0;
      border-left: 2px solid #e0e0e0;
      padding-left: 0.75rem;
    }
    .body {
      display: flex;
      flex-direction: column;
    }
    .headline {
      font-weight: 600;
    }
    .meta {
      font-size: 0.8rem;
      opacity: 0.7;
    }
    .reason {
      margin: 0.35rem 0 0;
      font-style: italic;
    }
  `,
})
export class SubmissionTimelineComponent {
  readonly entries = input.required<TransitionResponse[]>();

  protected icon(status: string): string {
    switch (status) {
      case 'APPROVED':
        return 'check_circle';
      case 'REJECTED':
        return 'undo';
      case 'UNDER_REVIEW':
        return 'visibility';
      case 'SUBMITTED':
        return 'send';
      default:
        return 'edit_note';
    }
  }
}
