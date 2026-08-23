import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
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

  it('should declare the `dashboard`, `publications` and `medias` routes', () => {
    expect(routes.map((route) => route.path)).toEqual(['dashboard', 'publications', 'medias']);
  });

  it('should render the medias page on /medias and set its title', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/medias', MediasPageComponent);

    expect(component).toBeInstanceOf(MediasPageComponent);
    expect(document.title).toBe('Médiathèque');

    // La page charge la médiathèque dès son initialisation ; la requête est
    // consommée ici pour que `verify()` reste vert.
    httpTesting.expectOne('/api/medias');
    httpTesting.verify();
  });

  it('should render the dashboard page on /dashboard and set its title', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/dashboard', DashboardPageComponent);

    expect(component).toBeInstanceOf(DashboardPageComponent);
    expect(document.title).toBe('Dashboard');

    // La page charge les occurrences de la semaine dès son initialisation ;
    // la requête est consommée ici pour que `verify()` reste vert.
    httpTesting.expectOne('/api/occurrences/weekly');
    httpTesting.verify();
  });

  it('should render the publications page on /publications and set its title', async () => {
    const harness = await RouterTestingHarness.create();
    const component = await harness.navigateByUrl('/publications', PublicationsPageComponent);

    expect(component).toBeInstanceOf(PublicationsPageComponent);
    expect(document.title).toBe('Publications');

    // La page charge la liste et les campagnes dès son initialisation ; les
    // requêtes sont consommées ici pour que `verify()` reste vert.
    httpTesting.expectOne('/api/publications');
    httpTesting.expectOne('/api/campaigns');
    httpTesting.verify();
  });

  it('should not match an unknown url — no catch-all route is declared yet', async () => {
    const router = TestBed.inject(Router);

    await expect(router.navigateByUrl('/inconnu')).rejects.toThrow();
    httpTesting.verify();
  });
});
