import { Injectable, computed, inject, signal } from '@angular/core';
import { OidcSecurityService } from 'angular-auth-oidc-client';
import { Observable, map, of, switchMap, tap } from 'rxjs';

import { CurrentUserResponse, IdentityService } from '../api/generated';

export type Role = 'USER' | 'REVIEWER' | 'ADMIN';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly oidc = inject(OidcSecurityService);
  private readonly identity = inject(IdentityService);

  private readonly currentUser = signal<CurrentUserResponse | null>(null);
  private readonly authenticated = signal(false);

  readonly user = this.currentUser.asReadonly();
  readonly isAuthenticated = this.authenticated.asReadonly();
  readonly displayName = computed(() => this.currentUser()?.displayName ?? '');
  readonly organizations = computed(() => this.currentUser()?.organizations ?? []);
  readonly roles = computed<Role[]>(() => (this.currentUser()?.roles ?? []) as Role[]);
  readonly isAdmin = computed(() => this.hasRole('ADMIN'));

  hasRole(role: Role): boolean {
    return this.roles().includes(role);
  }

  hasAnyRole(roles: readonly Role[]): boolean {
    return roles.some((role) => this.hasRole(role));
  }

  /** Completes the OIDC handshake, then loads the identity the backend recognises. */
  bootstrap(): Observable<CurrentUserResponse | null> {
    return this.oidc.checkAuth().pipe(
      tap((result) => this.authenticated.set(result.isAuthenticated)),
      switchMap((result) => (result.isAuthenticated ? this.loadCurrentUser() : of(null))),
    );
  }

  loadCurrentUser(): Observable<CurrentUserResponse | null> {
    return this.identity.getCurrentUser().pipe(
      tap((user) => this.currentUser.set(user)),
      map((user) => user ?? null),
    );
  }

  login(): void {
    this.oidc.authorize();
  }

  logout(): void {
    this.currentUser.set(null);
    this.authenticated.set(false);
    this.oidc.logoff().subscribe();
  }

  accessToken(): Observable<string> {
    return this.oidc.getAccessToken();
  }
}
