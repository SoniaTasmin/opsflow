import { ApplicationConfig, provideAppInitializer, inject } from '@angular/core';
import { provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter, withComponentInputBinding } from '@angular/router';
import { provideNativeDateAdapter } from '@angular/material/core';
import { AbstractSecurityStorage, provideAuth } from 'angular-auth-oidc-client';
import { firstValueFrom } from 'rxjs';

import { routes } from './app.routes';
import { authConfig } from './core/auth/auth.config';
import { InMemorySecurityStorage } from './core/auth/in-memory-storage';
import { AuthService } from './core/auth/auth.service';
import { authInterceptor } from './core/http/auth.interceptor';
import { correlationIdInterceptor } from './core/http/correlation-id.interceptor';
import { errorInterceptor } from './core/http/error.interceptor';
import { loadingInterceptor } from './core/http/loading.interceptor';
import { BASE_PATH } from './core/api/generated';
import { environment } from '../environments/environment';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    provideAuth(authConfig),
    // mat-datepicker has no adapter of its own; without this the period form throws on open.
    provideNativeDateAdapter(),
    { provide: AbstractSecurityStorage, useClass: InMemorySecurityStorage },
    provideHttpClient(
      // Order matters: correlation id and bearer token are attached on the way out,
      // errors are translated on the way back.
      withInterceptors([
        correlationIdInterceptor,
        authInterceptor,
        loadingInterceptor,
        errorInterceptor,
      ]),
    ),
    { provide: BASE_PATH, useValue: environment.apiBaseUrl },
    provideAppInitializer(() => firstValueFrom(inject(AuthService).bootstrap())),
  ],
};
