import { formatDate } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, filter, switchMap, timer } from 'rxjs';
import { AuthService } from '../../../auth/auth.service';
import { NotificationService } from '../../../notifications/notification.service';
import { apiErrorMessage } from '../../../../features/occurrences/occurrence.utils';
import { DeliveryChannel } from '../../../../features/publications/publication.model';
import { CHANNEL_LABELS, ChannelSuspension } from '../../channel-suspension.model';
import { ChannelSuspensionsService } from '../../channel-suspensions.service';

/**
 * Une suspension survient pendant un balayage de l'ordonnanceur, sans action de
 * l'utilisateur : le bandeau interroge donc le back régulièrement. Une minute,
 * c'est la période de balayage elle-même.
 */
export const SUSPENSIONS_REFRESH_MS = 60_000;

/**
 * Bandeau affiché sur toutes les pages tant qu'un canal est suspendu : il dit
 * pourquoi plus rien ne part, et permet de reprendre une fois le problème réglé.
 */
@Component({
  selector: 'app-channel-suspension-banner',
  imports: [],
  templateUrl: './channel-suspension-banner.component.html',
  styleUrl: './channel-suspension-banner.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChannelSuspensionBannerComponent {
  private readonly suspensionsService = inject(ChannelSuspensionsService);
  private readonly notifications = inject(NotificationService);
  private readonly auth = inject(AuthService);

  /** Lever une suspension exige `channels:manage` ; la voir, `publications:read`. */
  protected readonly canLift = computed(() => this.auth.can('channels:manage'));

  protected readonly suspensions = this.suspensionsService.suspensions;
  protected readonly lifting = signal<DeliveryChannel | null>(null);

  constructor() {
    timer(0, SUSPENSIONS_REFRESH_MS)
      .pipe(
        filter(() => this.auth.can('publications:read')),
        // Un échec de lecture garde le dernier état connu : mieux vaut un
        // bandeau un peu en retard qu'une notification d'erreur chaque minute.
        switchMap(() => this.suspensionsService.refresh().pipe(catchError(() => EMPTY))),
        takeUntilDestroyed(),
      )
      .subscribe();
  }

  protected label(suspension: ChannelSuspension): string {
    return CHANNEL_LABELS[suspension.channel];
  }

  protected since(suspension: ChannelSuspension): string {
    return formatDate(suspension.suspendedAt, "EEEE d MMMM 'à' HH:mm", 'fr-FR');
  }

  protected onLift(suspension: ChannelSuspension): void {
    if (this.lifting()) {
      return;
    }

    const label = this.label(suspension);
    this.lifting.set(suspension.channel);

    this.suspensionsService.lift(suspension.channel).subscribe({
      next: () => {
        this.lifting.set(null);
        this.notifications.success(
          `Diffusions ${ label } reprises : celles en attente partiront dans la minute.`,
        );
      },
      error: (err: HttpErrorResponse) => {
        console.error('Levée de la suspension impossible', err);
        this.lifting.set(null);
        this.notifications.error(apiErrorMessage(err, `Les diffusions ${ label } n’ont pas pu être reprises.`));
      },
    });
  }
}
