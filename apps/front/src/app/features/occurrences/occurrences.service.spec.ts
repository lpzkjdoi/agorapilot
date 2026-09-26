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
      publication: { id: 10, content: 'Marché de producteurs', status: 'VERIFIED', medias: [], campaign: null },
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

  it('should GET /api/occurrences with the requested range', () => {
    let received: unknown;
    service.getBetween('2026-10-01', '2026-11-01').subscribe((occurrences) => (received = occurrences));

    const request = httpTesting.expectOne(
      (req) => req.url === '/api/occurrences'
        && req.params.get('from') === '2026-10-01'
        && req.params.get('to') === '2026-11-01',
    );
    expect(request.request.method).toBe('GET');

    request.flush([]);
    expect(received).toEqual([]);
  });

  it('should POST a day-only schedule as is', () => {
    service.create({ publicationId: 7, channels: ['FACEBOOK'], date: '2026-10-03' }).subscribe();

    const request = httpTesting.expectOne('/api/occurrences');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ publicationId: 7, channels: ['FACEBOOK'], date: '2026-10-03' });
    request.flush({});
  });

  it('should PUT the new day and time on /api/occurrences/{id}/schedule', () => {
    service.reschedule(4, { date: '2026-10-04', time: null }).subscribe();

    const request = httpTesting.expectOne('/api/occurrences/4/schedule');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ date: '2026-10-04', time: null });
    request.flush({});
  });

  it('should POST the day and time of a retry on /api/occurrences/{id}/retry', () => {
    service.retry(4, { date: '2026-09-27', time: '19:00' }).subscribe();

    const request = httpTesting.expectOne('/api/occurrences/4/retry');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ date: '2026-09-27', time: '19:00' });
    request.flush({});
  });

  it('should DELETE /api/occurrences/{id} to cancel', () => {
    let done = false;
    service.cancel(4).subscribe({ complete: () => (done = true) });

    const request = httpTesting.expectOne('/api/occurrences/4');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(done).toBe(true);
  });
});
