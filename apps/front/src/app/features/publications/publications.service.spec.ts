import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  CreatePublicationRequest,
  Publication,
  PublicationDelivery,
} from './publication.model';
import { PublicationsService } from './publications.service';

const publications: Publication[] = [
  { id: 1, content: 'Marché de producteurs', status: 'VERIFIED' },
  { id: 2, content: 'Conseil municipal', status: 'DRAFT' },
];

describe('PublicationsService', () => {
  let service: PublicationsService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(PublicationsService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should GET the publications list on /api/publications', () => {
    let received: Publication[] | undefined;
    service.getPublications().subscribe((list) => (received = list));

    const request = httpTesting.expectOne('/api/publications');
    expect(request.request.method).toBe('GET');

    request.flush(publications);
    expect(received).toEqual(publications);
  });

  it('should POST the creation request body as-is', () => {
    const payload: CreatePublicationRequest = {
      content: 'Fermeture de la mairie',
      status: 'DRAFT',
    };
    let created: Publication | undefined;
    service.createPublication(payload).subscribe((publication) => (created = publication));

    const request = httpTesting.expectOne('/api/publications');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(payload);

    request.flush({ id: 3, ...payload });
    expect(created).toEqual({ id: 3, ...payload });
  });

  it('should not issue any request until the observable is subscribed', () => {
    service.getPublications();
    service.createPublication({ content: 'Jamais envoyé', status: 'DRAFT' });
    service.publishOnFacebook(5);

    httpTesting.expectNone(() => true);
  });

  it('should POST the FACEBOOK channel on /api/publications/{id}/deliveries', () => {
    const delivery: PublicationDelivery = {
      id: 99,
      occurrenceId: 42,
      channel: 'FACEBOOK',
      status: 'PUBLISHED',
      publishedAt: '2026-08-22T10:00:00',
      externalId: '123_456',
    };
    let received: PublicationDelivery | undefined;
    service.publishOnFacebook(5).subscribe((result) => (received = result));

    const request = httpTesting.expectOne('/api/publications/5/deliveries');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ channel: 'FACEBOOK' });

    request.flush(delivery);
    expect(received).toEqual(delivery);
  });

  // Le back trace la livraison en `FAILED` puis répond 502 avec le motif : le
  // service laisse remonter l'erreur telle quelle, la page s'en sert pour le toast.
  it('should surface the 502 returned when Facebook refuses the post', () => {
    let error: unknown;
    service.publishOnFacebook(5).subscribe({ error: (err) => (error = err) });

    httpTesting.expectOne('/api/publications/5/deliveries').flush(
      { message: 'No token available', code: 502 },
      { status: 502, statusText: 'Bad Gateway' },
    );

    expect(error).toBeInstanceOf(HttpErrorResponse);
    expect((error as HttpErrorResponse).status).toBe(502);
    expect((error as HttpErrorResponse).error.message).toBe('No token available');
  });

  // Les deux actions restantes n'ont pas encore d'endpoint côté back : elles
  // doivent échouer explicitement, et surtout n'émettre aucune requête HTTP.
  it.each([
    ['generateXlsx', () => service.generateXlsx(1)],
    ['assignToCampaign', () => service.assignToCampaign(1, 7)],
  ] as const)('should fail `%s` without issuing a request while the endpoint is missing', (_name, call) => {
    let error: unknown;
    call().subscribe({ error: (err) => (error = err) });

    expect(error).toBeInstanceOf(Error);
    httpTesting.expectNone(() => true);
  });

  it('should surface a creation error to the subscriber', () => {
    let error: unknown;
    service
      .createPublication({ content: '', status: 'DRAFT' })
      .subscribe({ error: (err) => (error = err) });

    httpTesting
      .expectOne('/api/publications')
      .flush('invalide', { status: 400, statusText: 'Bad Request' });

    expect(error).toBeInstanceOf(HttpErrorResponse);
    expect((error as HttpErrorResponse).status).toBe(400);
  });
});
