import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { LOGIN_PATH } from "../../auth/auth.guards";
import { AuthService } from "../../auth/auth.service";
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
export class NavbarComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;

  protected onLogout(): void {
    this.auth.logout().subscribe(() => void this.router.navigateByUrl(LOGIN_PATH));
  }
}
