import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';
import { NavigationEnd, Router, RouterLink, RouterLinkActive } from '@angular/router';
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
  host: { '(document:keydown.escape)': 'closeMenu()' },
})
export class NavbarComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly user = this.auth.user;
  // Une entrée par page que le compte a le droit de voir.
  protected readonly canReadPublications = computed(() => this.auth.can('publications:read'));
  protected readonly canReadCampaigns = computed(() => this.auth.can('campaigns:read'));
  protected readonly canReadMedias = computed(() => this.auth.can('medias:read'));
  protected readonly canManageChannels = computed(() => this.auth.can('channels:manage'));
  protected readonly canManageAccounts = computed(() => this.auth.can('users:manage'));

  /** Menu déroulant des écrans étroits ; sans effet visible sur grand écran. */
  protected readonly menuOpen = signal(false);

  constructor() {
    // Une entrée choisie dans le menu mène à une autre page : on le replie.
    this.router.events
        .pipe(filter((event) => event instanceof NavigationEnd), takeUntilDestroyed())
        .subscribe(() => this.menuOpen.set(false));
  }

  protected toggleMenu(): void {
    this.menuOpen.update((open) => !open);
  }

  protected closeMenu(): void {
    this.menuOpen.set(false);
  }

  protected onLogout(): void {
    this.auth.logout().subscribe(() => void this.router.navigateByUrl(LOGIN_PATH));
  }
}
