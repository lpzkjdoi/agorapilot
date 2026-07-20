import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'button[navBarButton]',
  imports: [],
  templateUrl: './navbar-button.component.html',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavbarButtonComponent {
  name = input.required<string>()
}
