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
  { id: 1, content: 'Marché de producteurs', status: 'VERIFIED', medias: [], campaign: null },
  { id: 2, content: 'Conseil municipal', status: 'DRAFT', medias: [], campaign: null },
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
      errorMessage: null,
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

  it('should GET the EventProxy export as a blob, named after `Content-Disposition`', () => {
    let received: { content: Blob, filename: string } | undefined;
    service.exportXlsx(7).subscribe((file) => (received = file));

    const request = httpTesting.expectOne('/api/publications/7/xlsx');
    expect(request.request.method).toBe('GET');
    expect(request.request.responseType).toBe('blob');
    const content = new Blob(['xlsx']);
    request.flush(content, {
      headers: { 'Content-Disposition': 'attachment; filename="EventProxy-2026-11-17-publication-7.xlsx"' },
    });

    expect(received).toEqual({ content, filename: 'EventProxy-2026-11-17-publication-7.xlsx' });
  });

  it('should fall back to a generic file name without `Content-Disposition`', () => {
    let filename: string | undefined;
    service.exportXlsx(7).subscribe((file) => (filename = file.filename));

    httpTesting.expectOne('/api/publications/7/xlsx').flush(new Blob(['xlsx']));

    expect(filename).toBe('EventProxy-publication-7.xlsx');
  });

  it('should PUT the campaign of a publication', () => {
    let received: Publication | undefined;
    service.assignToCampaign(7, 3).subscribe((publication) => (received = publication));

    const request = httpTesting.expectOne('/api/publications/7/campaign');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ campaignId: 3 });

    const updated = { ...publications[0], campaign: { id: 3, name: 'Marché de Noël' } };
    request.flush(updated);
    expect(received).toEqual(updated);
  });

  it('should PUT a null campaign to detach a publication', () => {
    service.assignToCampaign(7, null).subscribe();

    const request = httpTesting.expectOne('/api/publications/7/campaign');
    expect(request.request.body).toEqual({ campaignId: null });

    request.flush({ ...publications[0], campaign: null });
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

  it('should PUT the ordered media ids on /medias', () => {
    let updated: Publication | undefined;
    service.setMedias(7, [3, 1]).subscribe((publication) => (updated = publication));

    const request = httpTesting.expectOne('/api/publications/7/medias');
    expect(request.request.method).toBe('PUT');
    // L'ordre est porteur de sens : le premier visuel sert de vignette.
    expect(request.request.body).toEqual({ mediaIds: [3, 1] });

    request.flush({ id: 7, content: 'Marché', status: 'DRAFT', medias: [], campaign: null });
    expect(updated?.id).toBe(7);
  });

  it('should send an empty list to detach every media', () => {
    service.setMedias(7, []).subscribe();

    const request = httpTesting.expectOne('/api/publications/7/medias');
    expect(request.request.body).toEqual({ mediaIds: [] });

    request.flush({ id: 7, content: 'Marché', status: 'DRAFT', medias: [], campaign: null });
  });

  it('should PUT the content and status on /api/publications/{id}', () => {
    let received: Publication | undefined;
    service.updatePublication(2, { content: 'Conseil municipal reporté', status: 'VERIFIED' })
           .subscribe((publication) => (received = publication));

    const req = httpTesting.expectOne('/api/publications/2');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ content: 'Conseil municipal reporté', status: 'VERIFIED' });
    req.flush({ ...publications[1], content: 'Conseil municipal reporté', status: 'VERIFIED' });

    expect(received?.content).toBe('Conseil municipal reporté');
  });

  it('should DELETE /api/publications/{id}', () => {
    let done = false;
    service.deletePublication(2).subscribe(() => (done = true));

    const req = httpTesting.expectOne('/api/publications/2');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(done).toBe(true);
  });
});
