import {
  EnvironmentProviders,
  inject,
  makeEnvironmentProviders,
  provideEnvironmentInitializer,
} from '@angular/core';
import { NotificationPortalService } from './notification-portal.service';

/**
 * Monte le portal de notifications au démarrage de l'application.
 *
 * À déclarer une seule fois, dans `appConfig` : le `NotificationService` est
 * ensuite injectable partout sans avoir à placer de composant dans un template.
 */
export function provideNotifications(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideEnvironmentInitializer(() => inject(NotificationPortalService).attach()),
  ]);
}
