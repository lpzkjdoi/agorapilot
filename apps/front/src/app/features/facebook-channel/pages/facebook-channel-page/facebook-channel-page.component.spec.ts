import { registerLocaleData } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import localeFr from '@angular/common/locales/fr';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { FacebookConnection, FacebookConnectionStatus } from '../../facebook-channel.model';
import { BROWSER_REDIRECT } from '../../facebook-channel.service';
import { FacebookChannelPageComponent } from './facebook-channel-page.component';

registerLocaleData(localeFr, 'fr-FR');

const connection: FacebookConnection = {
  pageId: '1234',
  pageName: 'Mairie',
  scopes: ['pages_show_list', 'pages_manage_posts'],
  connectedBy: 'maxime',
  connectedAt: '2026-10-27T10:00:00',
  expiresAt: null,
  dataAccessExpiresAt: null,
  lastCheckedAt: '2026-10-28T07:00:00',
  lastCheckResult: 'VALID',
  lastCheckMessage: null,
};
const connected: FacebookConnectionStatus = { source: 'CONNECTION', tokenInUse: 'CONNECTION', connection };
const notYetConnected: FacebookConnectionStatus = { source: 'CONNECTION', tokenInUse: 'LEGACY', connection: null };
const legacy: FacebookConnectionStatus = { source: 'LEGACY', tokenInUse: 'LEGACY', connection: null };

describe('FacebookChannelPageComponent', () => {
  let httpTesting: HttpTestingController;
  const redirect = vi.fn();

  function setUp(queryParams: Record<string, string> = {}) {
    redirect.mockReset();
    TestBed.configureTestingModule({
      imports: [FacebookChannelPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(queryParams) } } },
        { provide: BROWSER_REDIRECT, useValue: redirect },
      ],
    });
    httpTesting = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpTesting.verify());

  function render(status: FacebookConnectionStatus) {
    const fixture = TestBed.createComponent(FacebookChannelPageComponent);
    httpTesting.expectOne('/api/channels/facebook/connection').flush(status);
    fixture.detectChanges();
    return { fixture, compiled: fixture.nativeElement as HTMLElement };
  }

  function button(compiled: HTMLElement, label: string): HTMLButtonElement | undefined {
    return Array.from(compiled.querySelectorAll<HTMLButtonElement>('button'))
      .find((b) => b.textContent?.includes(label));
  }

  it('should describe the connected page and its last check, never the token', () => {
    setUp();
    const { compiled } = render(connected);

    expect(compiled.textContent).toContain('Jeton connecté depuis AgoraPilot');
    expect(compiled.textContent).toContain('Mairie');
    expect(compiled.textContent).toContain('maxime, le 27 octobre 2026 à 10:00');
    expect(compiled.textContent).toContain('Aucune (jeton permanent)');
    expect(compiled.textContent).toContain('pages_show_list, pages_manage_posts');
    expect(compiled.querySelector('.channel-check-valid')?.textContent).toContain('Valide');
    expect(button(compiled, 'Reconnecter la Page')).toBeDefined();
    expect(button(compiled, 'Contrôler maintenant')).toBeDefined();
  });

  it('should explain that the legacy token publishes until a page is connected', () => {
    setUp();
    const { compiled } = render(notYetConnected);

    expect(compiled.textContent).toContain('Jeton historique (bootstrap à la main)');
    expect(compiled.textContent).toContain('le jeton historique sert en attendant');
    expect(button(compiled, 'Connecter la Page Facebook')).toBeDefined();
    expect(button(compiled, 'Contrôler maintenant')).toBeUndefined();
  });

  it('should offer no connection in legacy mode', () => {
    setUp();
    const { compiled } = render(legacy);

    expect(compiled.querySelector('.channel-legacy')?.textContent).toContain('FACEBOOK_TOKEN_SOURCE=legacy');
    expect(compiled.querySelector('.channel-actions')).toBeNull();
  });

  it('should send the browser to the Facebook dialog', () => {
    setUp();
    const { fixture, compiled } = render(notYetConnected);

    button(compiled, 'Connecter la Page Facebook')!.click();
    httpTesting.expectOne({ method: 'POST', url: '/api/channels/facebook/connection' })
      .flush({ authorizationUrl: 'https://www.facebook.com/v25.0/dialog/oauth?state=abc' });
    fixture.detectChanges();

    expect(redirect).toHaveBeenCalledWith('https://www.facebook.com/v25.0/dialog/oauth?state=abc');
    expect(button(compiled, 'Ouverture de Facebook')?.disabled).toBe(true);
  });

  it('should report a refused connection start and let the user retry', () => {
    setUp();
    const error = vi.spyOn(TestBed.inject(NotificationService), 'error');
    const { fixture, compiled } = render(notYetConnected);

    button(compiled, 'Connecter la Page Facebook')!.click();
    httpTesting.expectOne({ method: 'POST', url: '/api/channels/facebook/connection' })
      .flush({ message: 'Connexion désactivée.' }, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    expect(redirect).not.toHaveBeenCalled();
    expect(error).toHaveBeenCalledWith('La connexion à Facebook n’a pas pu démarrer. Connexion désactivée.');
    expect(button(compiled, 'Connecter la Page Facebook')?.disabled).toBe(false);
  });

  it('should check the token on demand and refresh the suspension banner', () => {
    setUp();
    const error = vi.spyOn(TestBed.inject(NotificationService), 'error');
    const { fixture, compiled } = render(connected);

    button(compiled, 'Contrôler maintenant')!.click();
    httpTesting.expectOne({ method: 'POST', url: '/api/channels/facebook/connection/check' }).flush({
      ...connected,
      connection: { ...connection, lastCheckResult: 'INVALID', lastCheckMessage: 'Le jeton Facebook n’est plus valide.' },
    });
    httpTesting.expectOne('/api/channels/suspensions').flush([]);
    fixture.detectChanges();

    expect(compiled.querySelector('.channel-check-invalid')?.textContent).toContain('Invalide');
    expect(error).toHaveBeenCalledWith('Le jeton Facebook n’est plus valide.');
  });

  it('should announce a successful return from Facebook and clean the address', () => {
    setUp({ connexion: 'ok' });
    const success = vi.spyOn(TestBed.inject(NotificationService), 'success');
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    render(connected);
    httpTesting.expectOne('/api/channels/suspensions').flush([]);

    expect(success).toHaveBeenCalledWith('Page Facebook connectée : c’est désormais son jeton qui publie.');
    expect(navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: {}, replaceUrl: true }));
  });

  it('should show the reason of a failed return from Facebook', () => {
    setUp({ connexion: 'erreur', motif: 'Connexion annulée sur Facebook.' });
    const error = vi.spyOn(TestBed.inject(NotificationService), 'error');
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    render(notYetConnected);

    expect(error).toHaveBeenCalledWith('Connexion annulée sur Facebook.');
  });

  it('should show an error when the status cannot be loaded', () => {
    setUp();
    const fixture = TestBed.createComponent(FacebookChannelPageComponent);
    httpTesting.expectOne('/api/channels/facebook/connection').flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.channel-error')?.textContent)
      .toContain('L’état du canal Facebook n’a pas pu être chargé.');
  });
});
