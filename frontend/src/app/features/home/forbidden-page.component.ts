import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';

import { EmptyStateComponent } from '../../shared/ui/empty-state.component';

@Component({
  selector: 'app-forbidden-page',
  imports: [EmptyStateComponent, MatButtonModule, RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <app-empty-state icon="lock" message="You do not have access to that area.">
      <a matButton routerLink="/home">Back to home</a>
    </app-empty-state>
  `,
})
export class ForbiddenPageComponent {}
