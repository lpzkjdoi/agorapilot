import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { NotificationsService } from '../notifications.service';

@Component({
  selector: 'app-notifications-container',
  standalone: true,
  templateUrl: './notifications-container.component.html',
  styleUrl: './notifications-container.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NotificationsContainerComponent {
  protected readonly notificationsService = inject(NotificationsService);
}
