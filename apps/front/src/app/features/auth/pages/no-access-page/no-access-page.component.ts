import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * Un compte actif à qui aucun droit n'a encore été donné : il ne voit rien, et
 * cette page le lui dit au lieu de montrer des pages vides d'erreurs.
 */
@Component({
  selector: 'app-no-access-page',
  template: `
    <section class="no-access">
      <h1>Aucun droit pour le moment</h1>
      <p>
        Votre compte est actif, mais aucun droit ne lui a encore été donné. Demandez à un administrateur
        d’AgoraPilot de vous attribuer ceux dont vous avez besoin : ils s’appliqueront dès votre prochaine action.
      </p>
    </section>
  `,
  styles: `
    .no-access {
      display: flex;
      flex-direction: column;
      gap: 0.75rem;
      max-width: 36rem;
      margin: 3rem auto;
      text-align: center;
      color: var(--color-ui-text-secondary);
    }
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NoAccessPageComponent {}
