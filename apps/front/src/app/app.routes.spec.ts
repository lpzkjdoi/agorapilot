import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { DashboardPageComponent } from './features/dashboard/pages/dashboard-page/dashboard-page';
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

  it('should declare a single `dashboard` route', () => {
    expect(routes.map((route) => route.path)).toEqual(['dashboard']);
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

  it('should not match an unknown url — no catch-all route is declared yet', async () => {
    const router = TestBed.inject(Router);

    await expect(router.navigateByUrl('/inconnu')).rejects.toThrow();
    httpTesting.verify();
  });
});
