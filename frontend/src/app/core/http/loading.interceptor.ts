import { HttpInterceptorFn } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { finalize } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class LoadingIndicator {
  private readonly inFlight = signal(0);
  readonly isLoading = signal(false);

  start(): void {
    this.inFlight.update((count) => count + 1);
    this.isLoading.set(true);
  }

  stop(): void {
    this.inFlight.update((count) => Math.max(0, count - 1));
    this.isLoading.set(this.inFlight() > 0);
  }
}

export const loadingInterceptor: HttpInterceptorFn = (request, next) => {
  const indicator = inject(LoadingIndicator);
  indicator.start();
  return next(request).pipe(finalize(() => indicator.stop()));
};
