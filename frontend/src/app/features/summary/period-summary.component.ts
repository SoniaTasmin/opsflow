import { DecimalPipe, PercentPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';

import { EmptyStateComponent } from '../../shared/ui/empty-state.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusChipComponent } from '../../shared/ui/status-chip.component';
import { SummaryFacade } from './summary.facade';

interface StatusTile {
  label: string;
  count: number;
  tone: 'neutral' | 'progress' | 'positive' | 'attention';
}

@Component({
  selector: 'app-period-summary',
  imports: [
    DecimalPipe,
    PercentPipe,
    FormsModule,
    MatCardModule,
    MatButtonModule,
    MatIconModule,
    MatFormFieldModule,
    MatProgressBarModule,
    MatSelectModule,
    PageHeaderComponent,
    StatusChipComponent,
    EmptyStateComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './period-summary.component.html',
  styleUrl: './period-summary.component.scss',
})
export class PeriodSummaryComponent {
  protected readonly facade = inject(SummaryFacade);
  protected readonly selectedPeriodId = signal('');

  protected readonly summary = this.facade.summary;
  protected readonly scopedToMine = computed(() => this.summary()?.scope === 'MY_ORGANIZATIONS');
  protected readonly nobodyReports = computed(
    () => this.summary()?.participatingOrganizations === 0,
  );

  protected readonly tiles = computed<StatusTile[]>(() => {
    const summary = this.summary();
    if (!summary) {
      return [];
    }
    return [
      { label: 'Not started', count: summary.notStarted, tone: 'neutral' },
      { label: 'Draft', count: summary.draft, tone: 'neutral' },
      { label: 'Submitted', count: summary.submitted, tone: 'progress' },
      { label: 'Under review', count: summary.underReview, tone: 'progress' },
      { label: 'Returned', count: summary.rejected, tone: 'attention' },
      { label: 'Approved', count: summary.approved, tone: 'positive' },
    ];
  });

  constructor() {
    queueMicrotask(async () => {
      const first = await this.facade.loadPeriods();
      if (first) {
        this.selectedPeriodId.set(first);
        await this.facade.load(first);
      }
    });
  }

  protected async choose(periodId: string): Promise<void> {
    this.selectedPeriodId.set(periodId);
    await this.facade.load(periodId);
  }

  protected reload(): Promise<void> {
    return this.facade.load(this.selectedPeriodId());
  }
}
