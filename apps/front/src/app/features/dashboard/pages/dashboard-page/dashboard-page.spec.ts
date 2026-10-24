import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { EMPTY, Observable, of } from 'rxjs';
import { OccurrencesService } from '../../../occurrences/occurrences.service';
import { DashboardSummary } from '../../dashboard.model';
import { DashboardService } from '../../dashboard.service';
import { DashboardPageComponent } from './dashboard-page';

// The weekly calendar (rendered on the success path) formats dates in `fr-FR`.
registerLocaleData(localeFr, 'fr-FR');

const week = {
  '2026-07-20': '',
  '2026-07-21': '',
  '2026-07-22': '',
  '2026-07-23': '',
  '2026-07-24': '',
  '2026-07-25': '',
  '2026-07-26': '',
};

const summary: DashboardSummary = {
  activeCampaigns: 2,
  scheduledThisWeek: 6,
  draftPublications: 3,
  failedOccurrences: 1,
  upcomingOccurrences: [
    {
      id: 9,
      scheduledAt: '2026-10-25T18:30:00',
      pinned: false,
      status: 'SCHEDULED',
      publication: { id: 3, content: 'Marché de Noël samedi', status: 'VERIFIED', medias: [], campaign: null },
      deliveries: [{ id: 4, channel: 'FACEBOOK', status: 'PENDING' }],
    },
  ],
};

describe('DashboardPageComponent', () => {
  let response$: Observable<unknown>;
  let summary$: Observable<DashboardSummary>;

  beforeEach(async () => {
    summary$ = EMPTY;

    await TestBed.configureTestingModule({
      imports: [DashboardPageComponent],
      providers: [
        provideRouter([]),
        {
          provide: OccurrencesService,
          useValue: { getWeeklyOccurrences: () => response$ },
        },
        {
          provide: DashboardService,
          useValue: { getSummary: () => summary$ },
        },
      ],
    }).compileComponents();
  });

  function kpiValues(compiled: HTMLElement): string[] {
    return Array.from(compiled.querySelectorAll('.kpi-value')).map((el) => el.textContent?.trim() ?? '');
  }

  it('should create', () => {
    response$ = EMPTY;
    const fixture = TestBed.createComponent(DashboardPageComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the title, the four KPIs and the upcoming posts panel', () => {
    response$ = EMPTY;
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h1')?.textContent).toContain('Dashboard');
    expect(compiled.querySelectorAll('app-kpi').length).toBe(4);
    expect(compiled.querySelector('app-upcoming-posts')).not.toBeNull();
  });

  it('should not render the weekly calendar until occurrences are loaded', () => {
    response$ = EMPTY;
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('app-weekly-calendar')).toBeNull();
  });

  it('should show the loader in place of the weekly calendar while it loads', () => {
    // Un flux qui n'émet ni ne se termine : la requête est encore en vol.
    response$ = new Observable(() => undefined);
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('.dashboard-loading app-loader')).not.toBeNull();
    expect(compiled.querySelector('app-weekly-calendar')).toBeNull();
  });

  it('should render the weekly calendar once occurrences are available', () => {
    response$ = of(week);
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('app-weekly-calendar'),
    ).not.toBeNull();
  });

  it('should log and swallow a loading error without throwing', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    response$ = new Observable((subscriber) => subscriber.error(new Error('boom')));

    const fixture = TestBed.createComponent(DashboardPageComponent);
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(consoleSpy).toHaveBeenCalledWith('Failed to load weekly occurrences', expect.any(Error));

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('app-weekly-calendar')).toBeNull();
    // L'erreur arrête le loader : rien ne tourne dans le vide.
    expect(compiled.querySelector('.dashboard-loading')).toBeNull();
  });

  it('should render the summary counters in the KPIs and flag failures', () => {
    response$ = EMPTY;
    summary$ = of(summary);
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(kpiValues(compiled)).toEqual(['2', '6', '3', '1']);
    expect(compiled.querySelectorAll('.kpi-value-alert').length).toBe(1);
  });

  it('should not flag failures when there is none', () => {
    response$ = EMPTY;
    summary$ = of({ ...summary, failedOccurrences: 0 });
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.kpi-value-alert')).toBeNull();
  });

  it('should pass the upcoming occurrences to the panel', () => {
    response$ = EMPTY;
    summary$ = of(summary);
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('app-upcoming-post-card').length).toBe(1);
    expect(compiled.textContent).toContain('Marché de Noël samedi');
  });

  it('should show dashes and a loading note while the summary loads', () => {
    response$ = EMPTY;
    summary$ = new Observable(() => undefined);
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(kpiValues(compiled)).toEqual(['–', '–', '–', '–']);
    expect(compiled.querySelector('app-upcoming-posts')?.textContent).toContain('Chargement');
  });

  it('should keep dashes rather than zeros when the summary fails to load', () => {
    const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    response$ = EMPTY;
    summary$ = new Observable((subscriber) => subscriber.error(new Error('boom')));
    const fixture = TestBed.createComponent(DashboardPageComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(consoleSpy).toHaveBeenCalledWith('Failed to load dashboard summary', expect.any(Error));
    expect(kpiValues(compiled)).toEqual(['–', '–', '–', '–']);
    expect(compiled.querySelector('app-upcoming-posts')?.textContent).not.toContain('Chargement');
  });
});
