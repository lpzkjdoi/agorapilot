import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificationService } from '../../../../core/notifications/notification.service';
import { Occurrence } from '../../../occurrences/occurrence.model';
import { OCCURRENCE_DRAG_TYPE } from '../../calendar.utils';
import { CalendarPageComponent } from './calendar-page.component';

registerLocaleData(localeFr, 'fr-FR');

function occurrence(id: number, scheduledAt: string, overrides: Partial<Occurrence> = {}): Occurrence {
  return {
    id,
    scheduledAt,
    pinned: false,
    status: 'SCHEDULED',
    publication: { id, content: `Publication ${ id }`, status: 'VERIFIED', medias: [], campaign: null },
    deliveries: [{ id: id * 10, channel: 'FACEBOOK', status: 'PENDING' }],
    ...overrides,
  };
}

const october: Occurrence[] = [
  occurrence(1, '2026-10-12T16:45:00'),
  occurrence(2, '2026-10-12T20:15:00', { pinned: true }),
  occurrence(3, '2026-10-05T18:30:00', { status: 'PUBLISHED' }),
];

/** jsdom n'implémente pas `DataTransfer`. */
function drop(target: HTMLElement, id: number): void {
  for (const type of ['dragover', 'drop']) {
    const event = new Event(type, { cancelable: true, bubbles: true });
    Object.defineProperty(event, 'dataTransfer', {
      value: { types: [OCCURRENCE_DRAG_TYPE], dropEffect: 'none', getData: () => String(id) },
    });
    target.dispatchEvent(event);
  }
}

describe('CalendarPageComponent', () => {
  let httpTesting: HttpTestingController;
  let notifications: NotificationService;
  let fixture: ComponentFixture<CalendarPageComponent>;
  let element: HTMLElement;

  function expectMonth(from: string, to: string): TestRequest {
    return httpTesting.expectOne(
      (request) => request.url === '/api/occurrences'
        && request.params.get('from') === from
        && request.params.get('to') === to,
    );
  }

  function render(occurrences: Occurrence[] = october): void {
    fixture = TestBed.createComponent(CalendarPageComponent);
    expectMonth('2026-10-01', '2026-11-01').flush(occurrences);
    fixture.detectChanges();
    element = fixture.nativeElement as HTMLElement;
  }

  /** La case du `day` du mois affiché. */
  function cell(day: number): HTMLElement {
    const cells = Array.from(element.querySelectorAll<HTMLElement>('.month-cell'));
    return cells.filter((candidate) => !candidate.classList.contains('month-cell-outside'))[day - 1];
  }

  function reloadWith(occurrences: Occurrence[]): void {
    expectMonth('2026-10-01', '2026-11-01').flush(occurrences);
    fixture.detectChanges();
  }

  beforeEach(async () => {
    // Seule `Date` est figée : les requêtes simulées restent synchrones.
    vi.useFakeTimers({ now: new Date(2026, 9, 10, 12, 0), toFake: ['Date'] });

    await TestBed.configureTestingModule({
      imports: [CalendarPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpTesting = TestBed.inject(HttpTestingController);
    notifications = TestBed.inject(NotificationService);
  });

  afterEach(() => {
    httpTesting.verify();
    vi.useRealTimers();
  });

  it('should load the current month and title it as in the maquette', () => {
    render();

    expect(element.querySelector('h1')?.textContent).toBe('Octobre 2026');
    expect(element.querySelector('.calendar-subtitle')?.textContent).toContain('Planification des publications');
    expect(element.querySelector('.calendar-legend')?.textContent).toContain('Facebook');
    expect(element.querySelector('.calendar-legend')?.textContent).toContain('Intramuros');
  });

  it('should place each occurrence on its day', () => {
    render();

    expect(cell(12).querySelectorAll('app-calendar-occurrence-chip')).toHaveLength(2);
    expect(cell(5).textContent).toContain('Publication 3');
  });

  it('should show the loader until the month is loaded', () => {
    fixture = TestBed.createComponent(CalendarPageComponent);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('app-loader')).not.toBeNull();
    expectMonth('2026-10-01', '2026-11-01').flush([]);
  });

  it('should surface a loading error', () => {
    fixture = TestBed.createComponent(CalendarPageComponent);
    expectMonth('2026-10-01', '2026-11-01').flush('boom', { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('[role="alert"]')?.textContent)
      .toContain('Le calendrier n’a pas pu être chargé.');
  });

  it('should move between months and back to today', () => {
    render();
    const [previous, today, next] = Array.from(element.querySelectorAll<HTMLButtonElement>('.calendar-nav button'));

    next.click();
    expectMonth('2026-11-01', '2026-12-01').flush([]);
    fixture.detectChanges();
    expect(element.querySelector('h1')?.textContent).toBe('Novembre 2026');

    previous.click();
    previous.click();
    expectMonth('2026-10-01', '2026-11-01').flush([]);
    expectMonth('2026-09-01', '2026-10-01').flush([]);
    fixture.detectChanges();
    expect(element.querySelector('h1')?.textContent).toBe('Septembre 2026');

    today.click();
    expectMonth('2026-10-01', '2026-11-01').flush([]);
    fixture.detectChanges();
    expect(element.querySelector('h1')?.textContent).toBe('Octobre 2026');
  });

  it('should ignore a late answer for a month that is no longer displayed', () => {
    render();
    const [previous, , next] = Array.from(element.querySelectorAll<HTMLButtonElement>('.calendar-nav button'));

    next.click();
    previous.click();
    const late = expectMonth('2026-11-01', '2026-12-01');
    expectMonth('2026-10-01', '2026-11-01').flush(october);
    late.flush([occurrence(9, '2026-11-12T18:30:00')]);
    fixture.detectChanges();

    expect(element.querySelector('h1')?.textContent).toBe('Octobre 2026');
    expect(cell(12).textContent).toContain('Publication 1');
    expect(cell(12).textContent).not.toContain('Publication 9');
  });

  describe('glisser-déposer', () => {
    it('should move an automatic occurrence to the new day, leaving the time to the back', () => {
      render();

      drop(cell(14), 1);

      const request = httpTesting.expectOne('/api/occurrences/1/schedule');
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({ date: '2026-10-14', time: null });

      request.flush(occurrence(1, '2026-10-14T18:30:00'));
      reloadWith([occurrence(1, '2026-10-14T18:30:00')]);

      expect(notifications.notifications()).toEqual([
        expect.objectContaining({ level: 'success', message: 'Diffusion reprogrammée le mercredi 14 octobre à 18:30.' }),
      ]);
      expect(cell(14).textContent).toContain('Publication 1');
    });

    it('should keep a pinned time when the occurrence changes day', () => {
      render();

      drop(cell(14), 2);

      expect(httpTesting.expectOne('/api/occurrences/2/schedule').request.body)
        .toEqual({ date: '2026-10-14', time: '20:15' });
    });

    it('should do nothing when dropped back on its own day', () => {
      render();

      drop(cell(12), 1);

      httpTesting.expectNone('/api/occurrences/1/schedule');
    });

    it('should relay the reason of a refusal and reload the month', () => {
      render();

      drop(cell(14), 1);
      httpTesting.expectOne('/api/occurrences/1/schedule').flush(
        { message: 'La diffusion 1 est déjà traitée ou imminente.', code: 409 },
        { status: 409, statusText: 'Conflict' },
      );
      reloadWith(october);

      expect(notifications.notifications()).toEqual([
        expect.objectContaining({
          level: 'error',
          message: 'La diffusion n’a pas pu être déplacée. La diffusion 1 est déjà traitée ou imminente.',
        }),
      ]);
    });
  });

  describe('bulle du jour et modification', () => {
    function openDay(day: number): void {
      cell(day).querySelector<HTMLButtonElement>('.month-day')!.click();
      fixture.detectChanges();
    }

    function editFirstOf(day: number): void {
      openDay(day);
      cell(day).querySelector<HTMLButtonElement>('.day-panel-edit')!.click();
      fixture.detectChanges();
    }

    it('should open on the clicked day and list its occurrences', () => {
      render();

      openDay(12);

      const panel = cell(12).querySelector('app-calendar-day-panel');
      expect(panel?.textContent).toContain('lundi 12 octobre 2026');
      expect(panel?.querySelectorAll('.day-panel-item')).toHaveLength(2);
    });

    it('should close on a click outside without reopening', () => {
      render();
      openDay(12);

      cell(12).querySelector<HTMLButtonElement>('.day-panel-backdrop')!.click();
      fixture.detectChanges();

      expect(element.querySelector('app-calendar-day-panel')).toBeNull();
    });

    it('should hand over to the edit modal', () => {
      render();

      editFirstOf(12);

      expect(element.querySelector('app-calendar-day-panel')).toBeNull();
      expect(element.querySelector('.edit-publication')?.textContent).toContain('Publication 1');
    });

    it('should pin the time entered in the modal and reload the month', () => {
      render();
      editFirstOf(12);

      const time = element.querySelector<HTMLInputElement>('#edit-time')!;
      time.value = '17:00';
      time.dispatchEvent(new Event('input'));
      element.querySelector<HTMLButtonElement>('app-edit-occurrence-modal .primary')!.click();
      fixture.detectChanges();

      const request = httpTesting.expectOne('/api/occurrences/1/schedule');
      expect(request.request.body).toEqual({ date: '2026-10-12', time: '17:00' });
      request.flush(occurrence(1, '2026-10-12T17:00:00', { pinned: true }));
      reloadWith(october);

      expect(element.querySelector('app-edit-occurrence-modal')).toBeNull();
      expect(notifications.notifications()).toEqual([
        expect.objectContaining({ level: 'success', message: 'Diffusion reprogrammée le lundi 12 octobre à 17:00.' }),
      ]);
    });

    it('should cancel after confirmation and reload the month', () => {
      render();
      editFirstOf(12);

      const cancel = () => element.querySelector<HTMLButtonElement>('.edit-cancel')!;
      cancel().click();
      fixture.detectChanges();
      cancel().click();

      const request = httpTesting.expectOne('/api/occurrences/1');
      expect(request.request.method).toBe('DELETE');
      request.flush(null, { status: 204, statusText: 'No Content' });
      reloadWith(october.slice(1));

      expect(notifications.notifications()).toEqual([
        expect.objectContaining({ level: 'success', message: 'Diffusion annulée.' }),
      ]);
      expect(element.querySelector('app-edit-occurrence-modal')).toBeNull();
      expect(cell(12).querySelectorAll('app-calendar-occurrence-chip')).toHaveLength(1);
    });

    it('should not offer to edit what is already published', () => {
      render();

      openDay(5);

      expect(cell(5).querySelector('.day-panel-edit')).toBeNull();
    });
  });

  describe('reprise d’une diffusion en échec', () => {
    const REFUSAL = 'Confirmez votre identité (Facebook, code 368, sous-code 4854002)';
    const failed = occurrence(4, '2026-10-08T17:05:00', {
      status: 'FAILED',
      deliveries: [{ id: 40, channel: 'FACEBOOK', status: 'FAILED', errorMessage: REFUSAL }],
    });

    function openRetry(): void {
      render([...october, failed]);
      cell(8).querySelector<HTMLButtonElement>('.month-day')!.click();
      fixture.detectChanges();
      cell(8).querySelector<HTMLButtonElement>('.day-panel-retry')!.click();
      fixture.detectChanges();
    }

    function submit(): void {
      element.querySelector<HTMLButtonElement>('app-retry-occurrence-modal .primary')!.click();
      fixture.detectChanges();
    }

    it('should hand over to the retry modal, reason included', () => {
      openRetry();

      expect(element.querySelector('app-calendar-day-panel')).toBeNull();
      expect(element.querySelector('.retry-reason')?.textContent).toContain(REFUSAL);
    });

    it('should retry today by default, then reload the month', () => {
      openRetry();

      submit();

      const request = httpTesting.expectOne('/api/occurrences/4/retry');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual({ date: '2026-10-10', time: null });
      request.flush(occurrence(4, '2026-10-10T18:30:00'));
      reloadWith(october);

      expect(element.querySelector('app-retry-occurrence-modal')).toBeNull();
      expect(notifications.notifications()).toEqual([
        expect.objectContaining({ level: 'success', message: 'Diffusion reprise : elle partira le samedi 10 octobre à 18:30.' }),
      ]);
    });

    /** Fenêtre du jour close : il suffit de choisir un autre jour, la modale reste ouverte. */
    it('should keep the modal open when the day cannot take it', () => {
      openRetry();

      submit();
      httpTesting.expectOne('/api/occurrences/4/retry').flush(
        { message: 'Plus aucune publication possible le 2026-10-10.', code: 400 },
        { status: 400, statusText: 'Bad Request' },
      );
      fixture.detectChanges();

      expect(element.querySelector('app-retry-occurrence-modal')).not.toBeNull();
      expect(notifications.notifications()).toEqual([
        expect.objectContaining({
          level: 'error',
          message: 'La diffusion n’a pas pu être reprise. Plus aucune publication possible le 2026-10-10.',
        }),
      ]);
    });

    it('should close and reload when the occurrence is no longer in failure', () => {
      openRetry();

      submit();
      httpTesting.expectOne('/api/occurrences/4/retry').flush(
        { message: 'La diffusion 4 n’est pas en échec.', code: 409 },
        { status: 409, statusText: 'Conflict' },
      );
      reloadWith(october);

      expect(element.querySelector('app-retry-occurrence-modal')).toBeNull();
    });
  });
});
