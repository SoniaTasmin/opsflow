import { Injectable, inject } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

import { ApiError } from './api-error';

@Injectable({ providedIn: 'root' })
export class ErrorNotifier {
  private readonly snackBar = inject(MatSnackBar);

  notify(error: ApiError): void {
    if (error.status >= 500 && error.correlationId) {
      // The correlation id is the only thing that connects what the user saw to
      // the server logs, so it has to be copyable rather than merely displayed.
      this.snackBar
        .open(`${error.detail} (reference ${error.correlationId})`, 'Copy reference', {
          duration: 15000,
          panelClass: 'snack-error',
        })
        .onAction()
        .subscribe(() => navigator.clipboard?.writeText(error.correlationId ?? ''));
      return;
    }

    this.snackBar.open(error.detail, 'Dismiss', { duration: 8000, panelClass: 'snack-error' });
  }

  success(message: string): void {
    this.snackBar.open(message, undefined, { duration: 4000 });
  }
}
