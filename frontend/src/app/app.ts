import { ChangeDetectionStrategy, Component } from '@angular/core';

import { ShellComponent } from './core/layout/shell.component';

@Component({
  selector: 'app-root',
  imports: [ShellComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<app-shell />',
})
export class App {}
