import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { BuildInfo, buildInfo } from '../../../../../../environments/build-info';

@Component({
  selector: 'app-environment-badge',
  imports: [],
  templateUrl: './environment-badge.component.html',
  styleUrl: './environment-badge.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnvironmentBadgeComponent {
  // Entrée plutôt que lecture directe de `buildInfo` : le badge reste testable
  // sur les trois environnements sans avoir à simuler le module.
  build = input<BuildInfo>(buildInfo);

  // En production, c'est le numéro de version qui informe : l'environnement y
  // est implicite. Ailleurs, c'est l'inverse — le nom de l'environnement lève
  // l'ambiguïté, la version n'a pas de sens hors release.
  label = computed(() => {
    const build = this.build();
    return build.environment === 'prod' ? build.version : build.environment;
  });
}
