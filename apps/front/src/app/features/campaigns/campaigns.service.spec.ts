import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Campaign } from './campaign.model';
import { CampaignsService } from './campaigns.service';

const campaigns: Campaign[] = [
  {
    id: 1,
    name: 'Parcs & Loisirs',
    description: 'Programme du printemps',
    startDate: '2026-03-01T00:00:00',
    endDate: '2026-06-30T00:00:00',
    status: 'ACTIVE',
    publications: [],
  },
];

describe('CampaignsService', () => {
  let service: CampaignsService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(CampaignsService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should GET the campaigns list on /api/campaigns', () => {
    let received: Campaign[] | undefined;
    service.getCampaigns().subscribe((list) => (received = list));

    const request = httpTesting.expectOne('/api/campaigns');
    expect(request.request.method).toBe('GET');

    request.flush(campaigns);
    expect(received).toEqual(campaigns);
  });

  it('should not issue any request until the observable is subscribed', () => {
    service.getCampaigns();
    httpTesting.expectNone('/api/campaigns');
  });

  it('should POST a new campaign on /api/campaigns', () => {
    let received: Campaign | undefined;
    const request = {
      name: 'Marché de Noël',
      description: 'Toutes les annonces du marché',
      startDate: '2026-12-01T00:00:00',
      endDate: '2026-12-24T00:00:00',
    };

    service.createCampaign(request).subscribe((campaign) => (received = campaign));

    const pending = httpTesting.expectOne('/api/campaigns');
    expect(pending.request.method).toBe('POST');
    expect(pending.request.body).toEqual(request);

    pending.flush(campaigns[0]);
    expect(received).toEqual(campaigns[0]);
  });

  it('should surface a loading error to the subscriber', () => {
    let error: unknown;
    service.getCampaigns().subscribe({ error: (err) => (error = err) });

    httpTesting
      .expectOne('/api/campaigns')
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(error).toBeInstanceOf(HttpErrorResponse);
    expect((error as HttpErrorResponse).status).toBe(500);
  });
  it('should PUT the edited campaign on /api/campaigns/{id}', () => {
    const body = { name: 'Parcs', description: '', startDate: '2026-03-01T00:00:00', endDate: null };
    let received: Campaign | undefined;
    service.updateCampaign(1, body).subscribe((campaign) => (received = campaign));

    const request = httpTesting.expectOne('/api/campaigns/1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual(body);

    request.flush(campaigns[0]);
    expect(received).toEqual(campaigns[0]);
  });

  it('should POST an empty body on /closure to close a campaign', () => {
    service.closeCampaign(1).subscribe();

    const request = httpTesting.expectOne('/api/campaigns/1/closure');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();
    request.flush({ ...campaigns[0], status: 'COMPLETED' });
  });

  it('should POST an empty body on /cancellation to cancel a campaign', () => {
    service.cancelCampaign(1).subscribe();

    const request = httpTesting.expectOne('/api/campaigns/1/cancellation');
    expect(request.request.method).toBe('POST');
    request.flush({ ...campaigns[0], status: 'CANCELED' });
  });

  it('should DELETE the campaign to archive it', () => {
    let done = false;
    service.archiveCampaign(1).subscribe({ complete: () => (done = true) });

    const request = httpTesting.expectOne('/api/campaigns/1');
    expect(request.request.method).toBe('DELETE');
    request.flush(null, { status: 204, statusText: 'No Content' });
    expect(done).toBe(true);
  });
});
