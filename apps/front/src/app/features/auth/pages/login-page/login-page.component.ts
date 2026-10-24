import { HttpErrorResponse } from "@angular/common/http";
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from "@angular/forms";
import { ActivatedRoute, Router } from "@angular/router";
import { PASSWORD_PATH } from "../../../../core/auth/auth.guards";
import { AuthService } from "../../../../core/auth/auth.service";
import { LoaderComponent } from "../../../../core/loader/loader.component";
import { LogoComponent } from "../../../../core/logo/logo.component";

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule, LoaderComponent, LogoComponent],
  templateUrl: './login-page.component.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPageComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected readonly submitting = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly form = new FormGroup({
    username: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    password: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
  });

  protected onSubmit(): void {
    if (this.submitting()) {
      return;
    }

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.error.set(null);

    this.auth.login(this.form.getRawValue()).subscribe({
      next: (user) => {
        this.submitting.set(false);
        void this.router.navigateByUrl(user.status === 'PENDING_PASSWORD' ? PASSWORD_PATH : this.redirectTarget());
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        // Le mot de passe saisi ne reste pas dans le champ après un échec.
        this.form.controls.password.reset();
        this.error.set(err.status === 401 && typeof err.error?.message === 'string'
          ? err.error.message
          : 'Connexion impossible pour le moment. Réessayez dans un instant.');
      },
    });
  }

  /**
   * La page demandée avant la connexion, seulement si c'est un chemin de
   * l'application : un `redirect` qui mène ailleurs (`//site.example`,
   * `https://…`) est ignoré.
   */
  private redirectTarget(): string {
    const redirect = this.route.snapshot.queryParamMap.get('redirect');

    return redirect && redirect.startsWith('/') && !redirect.startsWith('//') ? redirect : '/dashboard';
  }
}
