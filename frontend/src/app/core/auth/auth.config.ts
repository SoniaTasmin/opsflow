import { LogLevel, PassedInitialConfig } from 'angular-auth-oidc-client';

import { environment } from '../../../environments/environment';

export const authConfig: PassedInitialConfig = {
  config: {
    authority: environment.oidcAuthority,
    clientId: environment.oidcClientId,
    redirectUrl: `${window.location.origin}/`,
    postLogoutRedirectUri: `${window.location.origin}/`,
    scope: 'openid profile email',
    // Authorization Code + PKCE. No client secret reaches the browser and the
    // password grant is disabled on this client.
    responseType: 'code',
    useRefreshToken: true,
    silentRenew: true,
    renewTimeBeforeTokenExpiresInSeconds: 30,
    logLevel: environment.production ? LogLevel.Error : LogLevel.Warn,
  },
};
