import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { AppNotification } from '../../notification.model';

/**
 * Rendu d'une notification. Purement présentationnel : il ne connaît pas le
 * `NotificationService`, c'est le conteneur qui relaie la fermeture.
 */
@Component({
  selector: 'app-notification-toast',
  imports: [],
  templateUrl: './notification-toast.component.html',
  styleUrl: './notification-toast.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationToastComponent {
  readonly notification = input.required<AppNotification>();

  readonly closed = output<number>();

  /**
   * Les erreurs et les avertissements interrompent le lecteur d'écran
   * (`alert`) ; les succès et les informations attendent une pause (`status`).
   */
  readonly role = computed(() => {
    const level = this.notification().level;
    return level === 'error' || level === 'warning' ? 'alert' : 'status';
  });
}
