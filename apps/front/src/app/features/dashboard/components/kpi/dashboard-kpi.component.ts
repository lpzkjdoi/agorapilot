import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-kpi',
  imports: [],
  templateUrl: './dashboard-kpi.component.html',
  styleUrl: './dashboard-kpi.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DashboardKpiComponent {
  title = input.required<string>()
  /** `null` tant que la valeur n'est pas connue (chargement, erreur) : un tiret s'affiche. */
  value = input.required<number | null>()
  color = input<string>()
  /** Valeur qui appelle une action (des échecs, par exemple) : affichée en rouge. */
  alert = input(false)
}
