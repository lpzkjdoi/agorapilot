import { DatePipe } from "@angular/common";
import { HttpErrorResponse } from "@angular/common/http";
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { AuthService } from "../../../../core/auth/auth.service";
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { NotificationService } from "../../../../core/notifications/notification.service";
import { apiErrorMessage } from "../../../occurrences/occurrence.utils";
import {
  AccountFormValue,
  AccountModalComponent,
} from "../../components/account-modal/account-modal.component";
import {
  TemporaryPasswordModalComponent,
} from "../../components/temporary-password-modal/temporary-password-modal.component";
import { matchingProfile } from "../../permissions";
import { Account, EVENT_LABELS, SecurityEvent, STATUS_LABELS, TemporaryPassword } from "../../user.model";
import { UsersService } from "../../users.service";

interface PendingAction {
  account: Account
  action: 'disable' | 'reset'
}

/**
 * Administration des comptes (`users:manage`) et journal de sécurité.
 *
 * Son propre compte n'y a aucune action : le back les refuserait (ni ses droits,
 * ni sa désactivation, ni son mot de passe, qui se change depuis « Mon compte »).
 */
@Component({
  selector: 'app-users-page',
  imports: [DatePipe, LoaderComponent, AccountModalComponent, TemporaryPasswordModalComponent],
  templateUrl: './users-page.component.html',
  styleUrl: './users-page.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UsersPageComponent {
  private readonly usersService = inject(UsersService);
  private readonly auth = inject(AuthService);
  private readonly notifications = inject(NotificationService);

  protected readonly statusLabels = STATUS_LABELS;
  protected readonly eventLabels = EVENT_LABELS;

  protected readonly accounts = signal<Account[]>([]);
  protected readonly events = signal<SecurityEvent[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  protected readonly me = computed(() => this.auth.user()?.username ?? null);
  protected readonly grantable = computed(() => this.auth.user()?.permissions ?? []);

  /** Modale de création (`null` en compte) ou de droits (le compte), fermée : `undefined`. */
  protected readonly editing = signal<Account | null | undefined>(undefined);
  protected readonly saving = signal(false);
  protected readonly modalError = signal<string | null>(null);

  protected readonly confirming = signal<PendingAction | null>(null);
  protected readonly busy = signal<number | null>(null);
  protected readonly revealed = signal<TemporaryPassword | null>(null);

  constructor() {
    this.load();
  }

  protected profileOf(account: Account): string {
    if (account.permissions.length === 0) {
      return 'Aucun droit';
    }

    return matchingProfile(account.permissions)?.label
      ?? `${ account.permissions.length } droit${ account.permissions.length > 1 ? 's' : '' }`;
  }

  protected openCreate(): void {
    this.modalError.set(null);
    this.editing.set(null);
  }

  protected openPermissions(account: Account): void {
    this.modalError.set(null);
    this.editing.set(account);
  }

  protected closeModal(): void {
    this.editing.set(undefined);
  }

  protected onSubmitted(value: AccountFormValue): void {
    const account = this.editing();
    this.saving.set(true);
    this.modalError.set(null);

    if (account) {
      this.usersService.setPermissions(account.id, value.permissions).subscribe({
        next: (updated) => {
          this.replace(updated);
          this.saving.set(false);
          this.editing.set(undefined);
          this.notifications.success(`Droits de ${ updated.username } enregistrés.`);
          this.loadEvents();
        },
        error: (err: HttpErrorResponse) => this.onModalError(err, 'Les droits n’ont pas pu être enregistrés.'),
      });
    } else {
      this.usersService.create(value).subscribe({
        next: (created) => {
          this.accounts.update((list) => [...list, created.account].sort((a, b) => a.username.localeCompare(b.username)));
          this.saving.set(false);
          this.editing.set(undefined);
          this.revealed.set(created);
          this.loadEvents();
        },
        error: (err: HttpErrorResponse) => this.onModalError(err, 'Le compte n’a pas pu être créé.'),
      });
    }
  }

  protected ask(account: Account, action: PendingAction['action']): void {
    this.confirming.set({ account, action });
  }

  protected confirm(): void {
    const pending = this.confirming();

    if (!pending) {
      return;
    }

    this.confirming.set(null);
    this.busy.set(pending.account.id);

    if (pending.action === 'disable') {
      this.usersService.disable(pending.account.id).subscribe({
        next: (updated) => {
          this.replace(updated);
          this.busy.set(null);
          this.notifications.success(`Compte ${ updated.username } désactivé : ses sessions sont coupées.`);
          this.loadEvents();
        },
        error: (err: HttpErrorResponse) => this.onRowError(err, 'Le compte n’a pas pu être désactivé.'),
      });
    } else {
      this.usersService.resetPassword(pending.account.id).subscribe({
        next: (reset) => {
          this.replace(reset.account);
          this.busy.set(null);
          this.revealed.set(reset);
          this.loadEvents();
        },
        error: (err: HttpErrorResponse) => this.onRowError(err, 'Le mot de passe n’a pas pu être réinitialisé.'),
      });
    }
  }

  private load(): void {
    this.usersService.getAccounts().subscribe({
      next: (accounts) => {
        this.accounts.set(accounts);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Chargement des comptes impossible', err);
        this.error.set('Les comptes n’ont pas pu être chargés.');
        this.loading.set(false);
      },
    });
    this.loadEvents();
  }

  private loadEvents(): void {
    this.usersService.getEvents().subscribe({
      next: (events) => this.events.set(events),
      error: (err) => console.error('Chargement du journal impossible', err),
    });
  }

  private replace(updated: Account): void {
    this.accounts.update((list) => list.map((item) => (item.id === updated.id ? updated : item)));
  }

  private onModalError(err: HttpErrorResponse, fallback: string): void {
    console.error(fallback, err);
    this.saving.set(false);
    this.modalError.set(apiErrorMessage(err, fallback));
  }

  private onRowError(err: HttpErrorResponse, fallback: string): void {
    console.error(fallback, err);
    this.busy.set(null);
    this.notifications.error(apiErrorMessage(err, fallback));
  }
}
