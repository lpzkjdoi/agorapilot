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
  value = input.required<number>()
  color = input<string>()
}
