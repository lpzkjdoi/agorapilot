import { provideHttpClient, withInterceptors, withXsrfConfiguration } from "@angular/common/http";
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';

import { authInterceptor } from "./core/auth/auth.interceptor";
import { provideNotifications } from './core/notifications/notifications.providers';
import { routes } from './app.routes';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      withInterceptors([authInterceptor]),
      // Les noms que Spring Security emploie (`csrf.spa()`) : le jeton est lu dans
      // ce cookie et renvoyé dans cet en-tête sur toute requête qui modifie.
      withXsrfConfiguration({ cookieName: 'XSRF-TOKEN', headerName: 'X-XSRF-TOKEN' }),
    ),
    provideNotifications(),
  ],
};
