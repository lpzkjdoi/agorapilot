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

  it('should surface a loading error to the subscriber', () => {
    let error: unknown;
    service.getCampaigns().subscribe({ error: (err) => (error = err) });

    httpTesting
      .expectOne('/api/campaigns')
      .flush('boom', { status: 500, statusText: 'Server Error' });

    expect(error).toBeInstanceOf(HttpErrorResponse);
    expect((error as HttpErrorResponse).status).toBe(500);
  });
});
