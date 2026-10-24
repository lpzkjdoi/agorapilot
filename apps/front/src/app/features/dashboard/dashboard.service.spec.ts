import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { DashboardSummary } from './dashboard.model';
import { DashboardService } from './dashboard.service';

const summary: DashboardSummary = {
  activeCampaigns: 2,
  scheduledThisWeek: 6,
  draftPublications: 3,
  failedOccurrences: 1,
  upcomingOccurrences: [],
};

describe('DashboardService', () => {
  let service: DashboardService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });

    service = TestBed.inject(DashboardService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should GET the summary on /api/dashboard', () => {
    let received: DashboardSummary | undefined;
    service.getSummary().subscribe((value) => (received = value));

    const req = httpTesting.expectOne('/api/dashboard');
    expect(req.request.method).toBe('GET');
    req.flush(summary);

    expect(received).toEqual(summary);
  });
});
