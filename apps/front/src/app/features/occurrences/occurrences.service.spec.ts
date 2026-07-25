import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { WeeklyOccurrences } from './occurrence.model';
import { OccurrencesService } from './occurrences.service';

// Le back renvoie un objet JSON indexé par date. `WeeklyOccurrences` le déclare
// comme une `Map`, ce qu'`HttpClient` ne produira jamais : ce jeu d'essai décrit
// la réponse réelle, pas la déclaration de type — laquelle est à corriger.
const weeklyPayload = {
  '2026-07-20': [
    {
      id: 1,
      scheduledAt: '2026-07-20T09:00:00',
      status: 'SCHEDULED',
      publication: { id: 10, content: 'Marché de producteurs', status: 'VERIFIED' },
      deliveries: [{ id: 100, channel: 'FACEBOOK', status: 'PENDING' }],
    },
  ],
  '2026-07-21': [],
};

describe('OccurrencesService', () => {
  let service: OccurrencesService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(OccurrencesService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should GET /api/occurrences/weekly and pass the payload through untouched', () => {
    let received: WeeklyOccurrences | undefined;
    service.getWeeklyOccurrences().subscribe((weekly) => (received = weekly));

    const request = httpTesting.expectOne('/api/occurrences/weekly');
    expect(request.request.method).toBe('GET');
    expect(request.request.body).toBeNull();

    request.flush(weeklyPayload);
    expect(received).toEqual(weeklyPayload);
  });

  it('should not issue any request until the observable is subscribed', () => {
    service.getWeeklyOccurrences();

    httpTesting.expectNone('/api/occurrences/weekly');
  });

  it('should surface a server error to the subscriber', () => {
    let error: unknown;
    service.getWeeklyOccurrences().subscribe({ error: (err) => (error = err) });

    httpTesting
      .expectOne('/api/occurrences/weekly')
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(error).toBeInstanceOf(HttpErrorResponse);
    expect((error as HttpErrorResponse).status).toBe(500);
  });
});
