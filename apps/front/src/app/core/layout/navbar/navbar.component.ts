import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { NavbarButtonComponent } from './components/navbar-button/navbar-button.component';
import { LogoComponent } from '../../logo/logo.component';
import { EnvironmentBadgeComponent } from './components/environment-badge/environment-badge.component';

@Component({
  selector: 'app-navbar',
  imports: [
    NavbarButtonComponent,
    RouterLink,
    RouterLinkActive,
    LogoComponent,
    EnvironmentBadgeComponent,
  ],
  templateUrl: './navbar.component.html',
  styleUrl: './navbar.component.css',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class NavbarComponent {}
