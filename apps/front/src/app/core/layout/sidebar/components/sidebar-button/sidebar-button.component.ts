import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'button[sideBarButton]',
  imports: [],
  templateUrl: './sidebar-button.component.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SidebarButtonComponent {
  name = input.required<string>()
}
