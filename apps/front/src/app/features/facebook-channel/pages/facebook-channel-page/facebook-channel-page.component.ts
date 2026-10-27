import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { ChannelSuspensionsService } from '../../../../core/channel-suspensions/channel-suspensions.service';
import { LoaderComponent } from '../../../../core/loader/loader.component';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { apiErrorMessage } from '../../../occurrences/occurrence.utils';
import {
  CHECK_RESULT_LABELS,
  FacebookConnectionStatus,
  TOKEN_ORIGIN_LABELS,
} from '../../facebook-channel.model';
import { FacebookChannelService } from '../../facebook-channel.service';

/**
 * État du canal Facebook et connexion de la Page (`channels:manage`).
 *
 * Remplace le bootstrap à la main (Graph Explorer, puis `curl` sur le VPS) :
 * « Connecter » passe par la boîte de dialogue de Facebook, qui ramène ici avec
 * `?connexion=ok` ou `?connexion=erreur&motif=…`.
 */
@Component({
  selector: 'app-facebook-channel-page',
  imports: [DatePipe, LoaderComponent],
  templateUrl: './facebook-channel-page.component.html',
  styleUrl: './facebook-channel-page.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FacebookChannelPageComponent {
  private readonly facebook = inject(FacebookChannelService);
  private readonly suspensions = inject(ChannelSuspensionsService);
  private readonly notifications = inject(NotificationService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  protected readonly originLabels = TOKEN_ORIGIN_LABELS;
  protected readonly checkLabels = CHECK_RESULT_LABELS;

  protected readonly status = signal<FacebookConnectionStatus | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly connecting = signal(false);
  protected readonly checking = signal(false);

  constructor() {
    this.reportReturnFromFacebook();
    this.load();
  }

  protected connect(): void {
    if (this.connecting()) {
      return;
    }
    this.connecting.set(true);

    // En cas de succès, le navigateur quitte la page : `connecting` reste vrai
    // pour que le bouton ne soit pas recliqué pendant la redirection.
    this.facebook.connect().subscribe({
      error: (err: HttpErrorResponse) => {
        this.connecting.set(false);
        this.notifications.error(apiErrorMessage(err, 'La connexion à Facebook n’a pas pu démarrer.'));
      },
    });
  }

  protected check(): void {
    if (this.checking()) {
      return;
    }
    this.checking.set(true);

    this.facebook.check().subscribe({
      next: (status) => {
        this.checking.set(false);
        this.status.set(status);
        // Un jeton invalide vient de suspendre le canal : le bandeau doit le dire.
        this.suspensions.refresh().subscribe({ error: () => undefined });
        if (status.connection?.lastCheckResult === 'VALID') {
          this.notifications.success('Le jeton Facebook est valide.');
        } else {
          this.notifications.error(status.connection?.lastCheckMessage ?? 'Le jeton Facebook n’a pas pu être contrôlé.');
        }
      },
      error: (err: HttpErrorResponse) => {
        this.checking.set(false);
        this.notifications.error(apiErrorMessage(err, 'Le contrôle du jeton a échoué.'));
      },
    });
  }

  private load(): void {
    this.facebook.getStatus().subscribe({
      next: (status) => {
        this.status.set(status);
        this.loading.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        this.error.set(apiErrorMessage(err, 'L’état du canal Facebook n’a pas pu être chargé.'));
      },
    });
  }

  /** Affiche l'issue du retour de Facebook, puis la retire de l'adresse. */
  private reportReturnFromFacebook(): void {
    const params = this.route.snapshot.queryParamMap;
    const outcome = params.get('connexion');
    if (!outcome) {
      return;
    }

    if (outcome === 'ok') {
      this.notifications.success('Page Facebook connectée : c’est désormais son jeton qui publie.');
      this.suspensions.refresh().subscribe({ error: () => undefined });
    } else {
      this.notifications.error(params.get('motif') ?? 'La connexion à Facebook a échoué.');
    }

    void this.router.navigate([], { relativeTo: this.route, queryParams: {}, replaceUrl: true });
  }
}
