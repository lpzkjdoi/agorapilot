import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { FacebookConnectionStatus } from './facebook-channel.model';
import { BROWSER_REDIRECT, FacebookChannelService } from './facebook-channel.service';

const status: FacebookConnectionStatus = { source: 'CONNECTION', tokenInUse: 'LEGACY', connection: null };

describe('FacebookChannelService', () => {
  let service: FacebookChannelService;
  let httpTesting: HttpTestingController;
  const redirect = vi.fn();

  beforeEach(() => {
    redirect.mockReset();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), { provide: BROWSER_REDIRECT, useValue: redirect }],
    });
    service = TestBed.inject(FacebookChannelService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('should read the connection status', () => {
    let received: FacebookConnectionStatus | undefined;
    service.getStatus().subscribe((value) => (received = value));

    const request = httpTesting.expectOne('/api/channels/facebook/connection');
    expect(request.request.method).toBe('GET');
    request.flush(status);

    expect(received).toEqual(status);
  });

  it('should ask the back to check the token now', () => {
    service.check().subscribe();

    const request = httpTesting.expectOne('/api/channels/facebook/connection/check');
    expect(request.request.method).toBe('POST');
    request.flush(status);
  });

  it('should open a connection request, then send the browser to the Facebook dialog', () => {
    let done = false;
    service.connect().subscribe({ complete: () => (done = true) });

    const request = httpTesting.expectOne('/api/channels/facebook/connection');
    expect(request.request.method).toBe('POST');
    request.flush({ authorizationUrl: 'https://www.facebook.com/v25.0/dialog/oauth?state=abc' });

    expect(redirect).toHaveBeenCalledWith('https://www.facebook.com/v25.0/dialog/oauth?state=abc');
    expect(done).toBe(true);
  });

  it('should not leave the application when the back refuses', () => {
    let failed = false;
    service.connect().subscribe({ error: () => (failed = true) });

    httpTesting.expectOne('/api/channels/facebook/connection')
      .flush({ message: 'désactivée' }, { status: 409, statusText: 'Conflict' });

    expect(redirect).not.toHaveBeenCalled();
    expect(failed).toBe(true);
  });
});
