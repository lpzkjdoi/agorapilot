import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CreatePublicationRequest, Publication } from './publication.model';
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

    httpTesting.expectNone('/api/publications');
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
