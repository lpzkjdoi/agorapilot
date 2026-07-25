import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  // Exception assumée à `@angular-eslint/component-selector`, qui attend un
  // sélecteur d'élément préfixé `app-`. Ce composant habille un `<button>`
  // natif : en faire un élément à part entière imposerait un bouton imbriqué
  // dans un `<app-navbar-button>`, au prix de la sémantique et du focus. Le
  // préfixe `app` est bien respecté, sur l'attribut.
  // eslint-disable-next-line @angular-eslint/component-selector
  selector: 'button[appNavbarButton]',
  imports: [],
  templateUrl: './navbar-button.component.html',
  styleUrl: './navbar-button.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavbarButtonComponent {
  name = input.required<string>()
}
