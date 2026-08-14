import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NotificationService } from '../../notification.service';
import { NotificationToastComponent } from '../notification-toast/notification-toast.component';

/**
 * Pile de toasts. Ce composant n'est jamais déclaré dans un template : il est
 * instancié dans `<body>` par `NotificationPortalService`, de sorte que les
 * notifications flottent au-dessus de la page quel que soit le contexte
 * d'empilement (`overflow`, `transform`, `z-index`) de la vue courante.
 */
@Component({
  selector: 'app-notification-container',
  imports: [NotificationToastComponent],
  templateUrl: './notification-container.component.html',
  styleUrl: './notification-container.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'region',
    'aria-label': 'Notifications',
  },
})
export class NotificationContainerComponent {
  private readonly notificationService = inject(NotificationService);

  protected readonly notifications = this.notificationService.notifications;

  protected close(id: number): void {
    this.notificationService.closeNotification(id);
  }
}
