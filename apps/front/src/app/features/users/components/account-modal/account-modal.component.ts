import {
  ChangeDetectionStrategy,
  Component,
  HostListener,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { Permission } from "../../../../core/auth/auth.model";
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { Account } from "../../user.model";
import { PermissionPickerComponent } from "../permission-picker/permission-picker.component";

export interface AccountFormValue {
  username: string
  permissions: Permission[]
}

const USERNAME = /^[a-z0-9._-]{3,32}$/;

/**
 * Création d'un compte (pseudo et droits), ou modification des droits d'un compte
 * existant quand `account` est fourni.
 */
@Component({
  selector: 'app-account-modal',
  imports: [LoaderComponent, PermissionPickerComponent],
  templateUrl: './account-modal.component.html',
  styleUrl: './account-modal.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountModalComponent {
  /** Compte dont on modifie les droits ; absent, on en crée un. */
  readonly account = input<Account | null>(null);
  readonly grantable = input.required<Permission[]>();
  readonly saving = input(false);
  /** Refus du back (pseudo pris…), affiché dans la modale qui reste ouverte. */
  readonly error = input<string | null>(null);

  readonly closed = output<void>();
  readonly submitted = output<AccountFormValue>();

  protected readonly username = signal('');
  protected readonly permissions = linkedSignal<Permission[]>(() => this.account()?.permissions ?? []);
  protected readonly usernameError = signal(false);

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.closed.emit();
  }

  protected onSubmit(): void {
    if (this.saving()) {
      return;
    }

    const username = this.account()?.username ?? this.username().trim().toLowerCase();

    if (!this.account() && !USERNAME.test(username)) {
      this.usernameError.set(true);
      return;
    }

    this.usernameError.set(false);
    this.submitted.emit({ username, permissions: this.permissions() });
  }
}
