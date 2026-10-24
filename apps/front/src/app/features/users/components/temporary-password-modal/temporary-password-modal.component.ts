import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';

/**
 * Le mot de passe provisoire, montré une seule fois : le back n'en garde que
 * l'empreinte. Fermer la fenêtre sans l'avoir noté oblige à réinitialiser.
 */
@Component({
  selector: 'app-temporary-password-modal',
  templateUrl: './temporary-password-modal.component.html',
  styleUrl: './temporary-password-modal.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TemporaryPasswordModalComponent {
  readonly username = input.required<string>();
  readonly password = input.required<string>();

  readonly closed = output<void>();

  protected readonly copied = signal(false);

  protected copy(): void {
    navigator.clipboard?.writeText(this.password())
             .then(() => this.copied.set(true))
             .catch(() => this.copied.set(false));
  }
}
