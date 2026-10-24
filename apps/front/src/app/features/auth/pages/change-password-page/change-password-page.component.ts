import { HttpErrorResponse } from "@angular/common/http";
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { LOGIN_PATH } from "../../../../core/auth/auth.guards";
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from "../../../../core/auth/auth.model";
import { AuthService } from "../../../../core/auth/auth.service";
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { LogoComponent } from "../../../../core/logo/logo.component";
import { NotificationService } from "../../../../core/notifications/notification.service";

function sameAsNewPassword(group: AbstractControl): ValidationErrors | null {
  const value = group.value as { newPassword: string, confirmation: string };
  return value.newPassword === value.confirmation ? null : { mismatch: true };
}

/**
 * Changement du mot de passe. Pour un compte au mot de passe provisoire, c'est la
 * seule page accessible : il n'exerce aucun droit avant d'en avoir choisi un.
 */
@Component({
  selector: 'app-change-password-page',
  imports: [ReactiveFormsModule, RouterLink, LoaderComponent, LogoComponent],
  templateUrl: './change-password-page.component.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ChangePasswordPageComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);

  protected readonly minLength = PASSWORD_MIN_LENGTH;
  protected readonly pending = computed(() => this.auth.user()?.status === 'PENDING_PASSWORD');
  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = new FormGroup({
    currentPassword: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    newPassword: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(PASSWORD_MIN_LENGTH),
        Validators.maxLength(PASSWORD_MAX_LENGTH),
      ],
    }),
    confirmation: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  }, { validators: sameAsNewPassword });

  protected onSubmit(): void {
    if (this.submitting()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const { currentPassword, newPassword } = this.form.getRawValue();
    this.submitting.set(true);
    this.error.set(null);

    this.auth.changePassword({ currentPassword, newPassword }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.notifications.success('Mot de passe enregistré.');
        void this.router.navigateByUrl('/dashboard');
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        this.error.set(err.status === 400 && typeof err.error?.message === 'string'
          ? err.error.message
          : 'Le mot de passe n’a pas pu être changé. Réessayez dans un instant.');
      },
    });
  }

  protected onLogout(): void {
    this.auth.logout().subscribe(() => void this.router.navigateByUrl(LOGIN_PATH));
  }
}
