import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { NotificationService } from '../../../notifications/notification.service';
import { ChannelSuspension } from '../../channel-suspension.model';
import {
  ChannelSuspensionBannerComponent,
  SUSPENSIONS_REFRESH_MS,
} from './channel-suspension-banner.component';
import { provideSignedInAs, signInAs } from '../../../auth/auth.testing';

registerLocaleData(localeFr, 'fr-FR');

const facebook: ChannelSuspension = {
  channel: 'FACEBOOK',
  reason: 'Confirmez votre identité avant de pouvoir publier au nom de cette Page. (Facebook, code 368)',
  suspendedAt: '2026-10-16T14:05:00',
};

describe('ChannelSuspensionBannerComponent', () => {
  let httpTesting: HttpTestingController;
  let notifications: NotificationService;

  beforeEach(async () => {
    vi.useFakeTimers();

    await TestBed.configureTestingModule({
      imports: [ChannelSuspensionBannerComponent],
      providers: [provideSignedInAs(), provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    httpTesting.verify();
    vi.useRealTimers();
  });

  /** Crée le bandeau et répond à la première lecture des suspensions. */
  function render(suspensions: ChannelSuspension[]): ComponentFixture<ChannelSuspensionBannerComponent> {
    const fixture = TestBed.createComponent(ChannelSuspensionBannerComponent);
    vi.advanceTimersByTime(0);
    httpTesting.expectOne('/api/channels/suspensions').flush(suspensions);
    fixture.detectChanges();
    return fixture;
  }

  it('should render nothing while every channel works', () => {
    const compiled = render([]).nativeElement as HTMLElement;

    expect(compiled.querySelector('.suspension-banner')).toBeNull();
  });

  it('should explain the suspension: channel, date and Facebook reason', () => {
    const compiled = render([facebook]).nativeElement as HTMLElement;

    expect(compiled.querySelector('.suspension-banner')?.getAttribute('role')).toBe('alert');
    expect(compiled.querySelector('.suspension-title')?.textContent?.trim()).toBe(
      'Diffusions Facebook suspendues depuis le vendredi 16 octobre à 14:05',
    );
    expect(compiled.querySelector('.suspension-reason')?.textContent).toBe(facebook.reason);
    expect(compiled.querySelector('.suspension-hint')?.textContent).toContain('Réglez le problème sur Facebook');
  });

  it('should link to the channel page to reconnect Facebook', () => {
    const compiled = render([facebook]).nativeElement as HTMLElement;

    expect(compiled.querySelector('.suspension-reconnect')?.getAttribute('href')).toBe('/canal-facebook');
  });

  it('should poll the back, a suspension happening during a scheduler run', () => {
    const fixture = render([]);

    vi.advanceTimersByTime(SUSPENSIONS_REFRESH_MS);
    httpTesting.expectOne('/api/channels/suspensions').flush([facebook]);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.suspension-banner')).not.toBeNull();
  });

  it('should keep the last known state when a refresh fails, without notifying', () => {
    const fixture = render([facebook]);

    vi.advanceTimersByTime(SUSPENSIONS_REFRESH_MS);
    httpTesting
      .expectOne('/api/channels/suspensions')
      .flush(null, { status: 0, statusText: 'Unknown Error' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.suspension-banner')).not.toBeNull();
    expect(notifications.notifications()).toEqual([]);
  });

  it('should lift the suspension, hide the banner and confirm', () => {
    const fixture = render([facebook]);
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>('.suspension-lift')?.click();
    fixture.detectChanges();
    expect(compiled.querySelector<HTMLButtonElement>('.suspension-lift')?.disabled).toBe(true);

    httpTesting.expectOne({ method: 'DELETE', url: '/api/channels/FACEBOOK/suspension' }).flush(null);
    fixture.detectChanges();

    expect(compiled.querySelector('.suspension-banner')).toBeNull();
    expect(notifications.notifications()).toEqual([
      expect.objectContaining({
        level: 'success',
        message: 'Diffusions Facebook reprises : celles en attente partiront dans la minute.',
      }),
    ]);
  });

  it('should keep the banner and relay the reason when lifting fails', () => {
    const fixture = render([facebook]);
    const compiled = fixture.nativeElement as HTMLElement;

    compiled.querySelector<HTMLButtonElement>('.suspension-lift')?.click();
    httpTesting
      .expectOne('/api/channels/FACEBOOK/suspension')
      .flush({ message: 'Base injoignable.', code: 500 }, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(compiled.querySelector('.suspension-banner')).not.toBeNull();
    expect(compiled.querySelector<HTMLButtonElement>('.suspension-lift')?.disabled).toBe(false);
    expect(notifications.notifications()).toEqual([
      expect.objectContaining({
        level: 'error',
        message: 'Les diffusions Facebook n’ont pas pu être reprises. Base injoignable.',
      }),
    ]);
  });

  describe('selon les droits', () => {
    it('should explain the suspension without offering to lift it, without « channels:manage »', () => {
      signInAs(['publications:read']);
      const compiled = render([facebook]).nativeElement as HTMLElement;

      expect(compiled.querySelector('.suspension-banner')).not.toBeNull();
      expect(compiled.querySelector('.suspension-lift')).toBeNull();
      expect(compiled.querySelector('.suspension-reconnect')).toBeNull();
      expect(compiled.querySelector('.suspension-hint')?.textContent).toContain('Gérer les canaux');
    });

    it('should not even ask, without the right to read publications', () => {
      signInAs(['medias:read']);
      TestBed.createComponent(ChannelSuspensionBannerComponent);
      vi.advanceTimersByTime(60_000);

      httpTesting.expectNone('/api/channels/suspensions');
    });
  });
});
