import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CalendarPageComponent } from './features/calendar/pages/calendar-page/calendar-page.component';
import { CampaignsPageComponent } from './features/campaigns/pages/campaigns-page/campaigns-page.component';
import { DashboardPageComponent } from './features/dashboard/pages/dashboard-page/dashboard-page';
import { MediasPageComponent } from './features/medias/pages/medias-page/medias-page.component';
import { PublicationsPageComponent } from './features/publications/pages/publications-page/publications-page.component';
import { routes } from './app.routes';

registerLocaleData(localeFr, 'fr-FR');

describe('routes', () => {
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });

    httpTesting = TestBed.inject(HttpTestingController);
  });

  /** Les pages de l'application exigent un compte : la garde lit `/api/me` d'abord. */
  async function signIn(status: 'ACTIVE' | 'PENDING_PASSWORD' = 'ACTIVE'): Promise<void> {
    (await meRequest()).flush({ username: 'camille', status, permissions: [] });
  }

  /** La navigation est asynchrone : la garde émet sa requête un peu après. */
  function meRequest() {
    return vi.waitFor(() => httpTesting.expectOne('/api/me'));
  }

  async function navigateSignedIn<T>(url: string, component: Type<T>): Promise<T> {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl(url, component);
    await signIn();
    return navigation;
  }

  it('should declare the login and password pages, then the guarded application', () => {
    expect(routes.map((route) => route.path)).toEqual(['connexion', 'mot-de-passe', '', '**']);
    expect(routes[2].children?.map((route) => route.path)).toEqual([
      '',
      'dashboard',
      'campagnes',
      'calendrier',
      'publications',
      'medias',
      'comptes',
    ]);
  });

  it('should keep the accounts page for the accounts administrators', async () => {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl('/comptes');
    (await meRequest()).flush({ username: 'camille', status: 'ACTIVE', permissions: ['publications:read'] });
    await navigation;

    expect(TestBed.inject(Router).url).toBe('/dashboard');
    httpTesting.match(() => true).forEach((request) => request.flush({}));
  });

  it('should send a signed-out visitor to the login page, remembering the requested page', async () => {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl('/campagnes');
    (await meRequest()).flush(null, { status: 401, statusText: 'Unauthorized' });
    await navigation;

    expect(TestBed.inject(Router).url).toBe('/connexion?redirect=%2Fcampagnes');
    httpTesting.verify();
  });

  it('should send an account with a provisional password to the password page', async () => {
    const harness = await RouterTestingHarness.create();
    const navigation = harness.navigateByUrl('/campagnes');
    await signIn('PENDING_PASSWORD');
    await navigation;

    expect(TestBed.inject(Router).url).toBe('/mot-de-passe');
    httpTesting.verify();
  });

  it('should render the campaigns page on /campagnes and set its title', async () => {
    const component = await navigateSignedIn('/campagnes', CampaignsPageComponent);

    expect(component).toBeInstanceOf(CampaignsPageComponent);
    expect(document.title).toBe('Campagnes');

    // La page charge les campagnes dès son initialisation ; la requête est
    // consommée ici pour que `verify()` reste vert.
    httpTesting.expectOne('/api/campaigns');
    httpTesting.verify();
  });

  it('should render the calendar page on /calendrier and set its title', async () => {
    const component = await navigateSignedIn('/calendrier', CalendarPageComponent);

    expect(component).toBeInstanceOf(CalendarPageComponent);
    expect(document.title).toBe('Calendrier');

    // La page charge le mois courant dès son initialisation.
    httpTesting.expectOne((request) => request.url === '/api/occurrences');
    httpTesting.verify();
  });

  it('should render the medias page on /medias and set its title', async () => {
    const component = await navigateSignedIn('/medias', MediasPageComponent);

    expect(component).toBeInstanceOf(MediasPageComponent);
    expect(document.title).toBe('Médiathèque');

    // La page charge la médiathèque dès son initialisation ; la requête est
    // consommée ici pour que `verify()` reste vert.
    httpTesting.expectOne('/api/medias');
    httpTesting.verify();
  });

  it('should render the dashboard page on /dashboard and set its title', async () => {
    const component = await navigateSignedIn('/dashboard', DashboardPageComponent);

    expect(component).toBeInstanceOf(DashboardPageComponent);
    expect(document.title).toBe('Dashboard');

    // La page charge la semaine et la synthèse dès son initialisation ;
    // les requêtes sont consommées ici pour que `verify()` reste vert.
    httpTesting.expectOne('/api/occurrences/weekly');
    httpTesting.expectOne('/api/dashboard');
    httpTesting.verify();
  });

  it('should render the publications page on /publications and set its title', async () => {
    const component = await navigateSignedIn('/publications', PublicationsPageComponent);

    expect(component).toBeInstanceOf(PublicationsPageComponent);
    expect(document.title).toBe('Publications');

    // La page charge la liste et les campagnes dès son initialisation ; les
    // requêtes sont consommées ici pour que `verify()` reste vert.
    httpTesting.expectOne('/api/publications');
    httpTesting.expectOne('/api/campaigns');
    httpTesting.verify();
  });

  it('should lead the root to the dashboard', async () => {
    await navigateSignedIn('/', DashboardPageComponent);

    expect(TestBed.inject(Router).url).toBe('/dashboard');
    httpTesting.match(() => true).forEach((request) => request.flush({}));
  });

  it('should lead any unknown url to the dashboard', async () => {
    await navigateSignedIn('/inconnu', DashboardPageComponent);

    expect(TestBed.inject(Router).url).toBe('/dashboard');
    httpTesting.match(() => true).forEach((request) => request.flush({}));
  });
});
