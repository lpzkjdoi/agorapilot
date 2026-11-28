import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Component } from '@angular/core';
import { of } from 'rxjs';
import { ALL_PERMISSIONS } from '../../../features/users/permissions';
import { CurrentUser, Permission } from '../../auth/auth.model';
import { AuthService } from '../../auth/auth.service';
import { NavbarComponent } from './navbar.component';

@Component({ template: '' })
class EmptyPageComponent {}

describe('NavbarComponent', () => {
  const user = signal<CurrentUser | null>(null);
  const logout = vi.fn(() => of(undefined));

  const can = (permission: Permission) => user()?.permissions.includes(permission) ?? false;

  beforeEach(async () => {
    user.set({ username: 'camille', status: 'ACTIVE', permissions: ALL_PERMISSIONS });
    logout.mockClear();

    await TestBed.configureTestingModule({
      imports: [NavbarComponent],
      providers: [provideRouter([{ path: 'calendrier', component: EmptyPageComponent }]), { provide: AuthService, useValue: { user, logout, can } }],
    }).compileComponents();
  });

  it('should show only the entries the account may open', () => {
    user.set({ username: 'camille', status: 'ACTIVE', permissions: ['medias:read'] });
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();

    const names = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('[appNavbarButton]'))
                       .map((button) => button.getAttribute('name'));
    expect(names).toEqual(['Médiathèque']);
  });

  it('should show the signed-in username, linking to the password change', () => {
    user.set({ username: 'camille', status: 'ACTIVE', permissions: [] });
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();

    const link = (fixture.nativeElement as HTMLElement).querySelector('.navbar-username');
    expect(link?.textContent?.trim()).toBe('camille');
    expect(link?.getAttribute('href')).toBe('/mot-de-passe');
  });

  it('should log out and go to the login page', async () => {
    user.set({ username: 'camille', status: 'ACTIVE', permissions: [] });
    const fixture = TestBed.createComponent(NavbarComponent);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    fixture.detectChanges();

    (fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.navbar-logout')!.click();

    expect(logout).toHaveBeenCalled();
    expect(navigate).toHaveBeenCalledWith('/connexion');
  });

  it('should show the Comptes entry only to accounts administrators', () => {
    user.set({ username: 'camille', status: 'ACTIVE', permissions: ['publications:read'] });
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[routerLink="/comptes"]')).toBeNull();

    user.set({ username: 'camille', status: 'ACTIVE', permissions: ['users:manage'] });
    fixture.detectChanges();
    expect(compiled.querySelector('[routerLink="/comptes"]')).not.toBeNull();
  });

  it('should show the Canal Facebook entry only to accounts managing the channels', () => {
    user.set({ username: 'camille', status: 'ACTIVE', permissions: ['publications:read'] });
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('[routerLink="/canal-facebook"]')).toBeNull();

    user.set({ username: 'camille', status: 'ACTIVE', permissions: ['channels:manage'] });
    fixture.detectChanges();
    expect(compiled.querySelector('[routerLink="/canal-facebook"]')).not.toBeNull();
  });

  it('should show no account block without a signed-in user', () => {
    user.set(null);
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.navbar-account')).toBeNull();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the AgoraPilot logo (badge + wordmark)', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    const logoBadge = compiled.querySelector('app-logo.navbar-logo-badge');
    const logoSvg = logoBadge?.querySelector('svg');
    const wordmark = compiled.querySelector('.navbar-logo-text');

    expect(logoBadge).not.toBeNull();
    expect(logoSvg).not.toBeNull();
    expect(wordmark?.textContent?.trim()).toBe('AgoraPilot');
  });

  it('should render the Dashboard navigation entry linking to /dashboard', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    const button = compiled.querySelector('.navbar-nav-button');
    expect(button).not.toBeNull();
    expect(button?.tagName.toLowerCase()).toBe('button');
    expect(button?.textContent).toContain('Dashboard');
    // The Dashboard entry is wired to the router (routerLink="/dashboard").
    expect(button?.getAttribute('routerlink')).toBe('/dashboard');
  });

  it('should render the Campagnes navigation entry linking to /campagnes', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    const campaigns = compiled.querySelectorAll('.navbar-nav-button')[1];
    expect(campaigns.textContent).toContain('Campagnes');
    expect(campaigns.getAttribute('routerlink')).toBe('/campagnes');
  });

  it('should render the Calendrier navigation entry linking to /calendrier, after Campagnes as in the maquette', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    const calendar = compiled.querySelectorAll('.navbar-nav-button')[2];
    expect(calendar.textContent).toContain('Calendrier');
    expect(calendar.getAttribute('routerlink')).toBe('/calendrier');
  });

  it('should render the Publications navigation entry linking to /publications', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    const buttons = compiled.querySelectorAll('.navbar-nav-button');
    // Les cinq pages, plus « Canal Facebook » et « Comptes » pour un compte qui a tous les droits.
    expect(buttons.length).toBe(7);

    const publications = buttons[3];
    expect(publications.textContent).toContain('Publications');
    expect(publications.getAttribute('routerlink')).toBe('/publications');
  });

  it('should render the Médiathèque navigation entry linking to /medias', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const compiled = fixture.nativeElement as HTMLElement;

    const medias = compiled.querySelectorAll('.navbar-nav-button')[4];
    expect(medias.textContent).toContain('Médiathèque');
    expect(medias.getAttribute('routerlink')).toBe('/medias');
  });

  it('should paint the header with the dark blue bar of the maquette', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const header = (fixture.nativeElement as HTMLElement).querySelector('.navbar') as HTMLElement;

    expect(getComputedStyle(header).backgroundColor).toBe('rgb(30, 58, 138)');
  });

  it('should render the environment badge at the far right of the bar', () => {
    // Sans compte : le bloc du compte, sinon dernier, n'est pas rendu.
    user.set(null);
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const header = (fixture.nativeElement as HTMLElement).querySelector('.navbar') as HTMLElement;

    // Dernier enfant du bandeau : `.navbar-nav` porte `flex: 1`, le badge est
    // donc poussé contre le bord droit, comme dans la maquette.
    const badge = header.lastElementChild as HTMLElement;
    expect(badge.tagName.toLowerCase()).toBe('app-environment-badge');
    expect(badge.textContent?.trim()).toBe('dev');
  });

  it('should render the navigation icon at the 16px size of the maquette', () => {
    const fixture = TestBed.createComponent(NavbarComponent);
    fixture.detectChanges();
    const icon = (fixture.nativeElement as HTMLElement).querySelector('.navbar-nav-button svg');

    expect(icon?.getAttribute('width')).toBe('16px');
    expect(icon?.getAttribute('height')).toBe('16px');
  });

  describe('menu des écrans étroits', () => {
    const toggle = (root: HTMLElement) => root.querySelector<HTMLButtonElement>('.navbar-toggle')!;

    it('should render a closed menu button controlling the navigation', () => {
      const fixture = TestBed.createComponent(NavbarComponent);
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;

      expect(toggle(root).getAttribute('aria-controls')).toBe('navbar-menu');
      expect(root.querySelector('#navbar-menu')?.tagName.toLowerCase()).toBe('nav');
      expect(toggle(root).getAttribute('aria-expanded')).toBe('false');
      expect(toggle(root).getAttribute('aria-label')).toBe('Ouvrir le menu');
      expect(root.querySelector('.navbar')?.classList.contains('navbar-menu-open')).toBe(false);
    });

    it('should open then close the menu on each click', () => {
      const fixture = TestBed.createComponent(NavbarComponent);
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;

      toggle(root).click();
      fixture.detectChanges();
      expect(toggle(root).getAttribute('aria-expanded')).toBe('true');
      expect(toggle(root).getAttribute('aria-label')).toBe('Fermer le menu');
      expect(root.querySelector('.navbar')?.classList.contains('navbar-menu-open')).toBe(true);

      toggle(root).click();
      fixture.detectChanges();
      expect(toggle(root).getAttribute('aria-expanded')).toBe('false');
      expect(root.querySelector('.navbar')?.classList.contains('navbar-menu-open')).toBe(false);
    });

    it('should close the menu with the Escape key', () => {
      const fixture = TestBed.createComponent(NavbarComponent);
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;

      toggle(root).click();
      fixture.detectChanges();
      toggle(root).dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      fixture.detectChanges();

      expect(toggle(root).getAttribute('aria-expanded')).toBe('false');
    });

    it('should close the menu once a page is reached', async () => {
      const fixture = TestBed.createComponent(NavbarComponent);
      fixture.detectChanges();
      const root = fixture.nativeElement as HTMLElement;

      toggle(root).click();
      fixture.detectChanges();
      await TestBed.inject(Router).navigateByUrl('/calendrier');
      fixture.detectChanges();

      expect(toggle(root).getAttribute('aria-expanded')).toBe('false');
    });
  });
});
