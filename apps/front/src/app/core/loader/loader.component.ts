import { ChangeDetectionStrategy, Component, input } from '@angular/core';

/** Les trois tailles de la maquette : 20px, 36px (défaut) et 56px. */
export type LoaderSize = 'sm' | 'md' | 'lg';

/**
 * `light` — arc bleu nuit sur fond clair (défaut).
 * `dark` — arc blanc, calibré pour le fond `#1E3A8A` du header et des boutons.
 */
export type LoaderVariant = 'light' | 'dark';

@Component({
  selector: 'app-loader',
  imports: [],
  templateUrl: './loader.component.html',
  styleUrl: './loader.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    role: 'status',
    '[class]': '"loader-" + size() + " loader-" + variant()',
  },
})
export class LoaderComponent {
  readonly size = input<LoaderSize>('md');
  readonly variant = input<LoaderVariant>('light');

  /** Texte lu par les lecteurs d'écran ; l'anneau lui-même est décoratif. */
  readonly label = input('Chargement en cours…');
}
