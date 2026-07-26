import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { NavbarButtonComponent } from './components/navbar-button/navbar-button.component';
import { LogoComponent } from '../../logo/logo.component';

@Component({
  selector: 'app-navbar',
  imports: [NavbarButtonComponent, RouterLink, RouterLinkActive, LogoComponent],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavbarComponent {}
