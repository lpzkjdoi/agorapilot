import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ChannelSuspension } from './channel-suspension.model';
import { ChannelSuspensionsService } from './channel-suspensions.service';

const facebook: ChannelSuspension = {
  channel: 'FACEBOOK',
  reason: 'Confirmez votre identité (Facebook, code 368)',
  suspendedAt: '2026-10-16T14:05:00',
};

describe('ChannelSuspensionsService', () => {
  let service: ChannelSuspensionsService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ChannelSuspensionsService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should start with no suspension', () => {
    expect(service.suspensions()).toEqual([]);
  });

  it('should load the suspended channels into the shared state', () => {
    service.refresh().subscribe();

    httpTesting.expectOne({ method: 'GET', url: '/api/channels/suspensions' }).flush([facebook]);

    expect(service.suspensions()).toEqual([facebook]);
  });

  it('should drop the channel from the state once its suspension is lifted', () => {
    service.refresh().subscribe();
    httpTesting.expectOne('/api/channels/suspensions').flush([facebook]);

    service.lift('FACEBOOK').subscribe();
    httpTesting.expectOne({ method: 'DELETE', url: '/api/channels/FACEBOOK/suspension' }).flush(null);

    expect(service.suspensions()).toEqual([]);
  });

  it('should keep the suspension when lifting fails', () => {
    service.refresh().subscribe();
    httpTesting.expectOne('/api/channels/suspensions').flush([facebook]);

    service.lift('FACEBOOK').subscribe({ error: () => undefined });
    httpTesting
      .expectOne('/api/channels/FACEBOOK/suspension')
      .flush(null, { status: 500, statusText: 'Server Error' });

    expect(service.suspensions()).toEqual([facebook]);
  });
});
