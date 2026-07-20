import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { TestBed } from '@angular/core/testing';
import { EMPTY, Observable, of } from 'rxjs';
import { OccurrencesService } from '../../../occurrences/occurrences.service';
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

describe('DashboardPageComponent', () => {
  let response$: Observable<unknown>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardPageComponent],
      providers: [
        {
          provide: OccurrencesService,
          useValue: { getWeeklyOccurrences: () => response$ },
        },
      ],
    }).compileComponents();
  });

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

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('app-weekly-calendar'),
    ).toBeNull();
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
    const consoleSpy = spyOn(console, 'error');
    response$ = new Observable((subscriber) => subscriber.error(new Error('boom')));

    const fixture = TestBed.createComponent(DashboardPageComponent);
    expect(() => fixture.detectChanges()).not.toThrow();
    expect(consoleSpy).toHaveBeenCalledWith('Failed to load weekly occurrences', jasmine.any(Error));
    expect(
      (fixture.nativeElement as HTMLElement).querySelector('app-weekly-calendar'),
    ).toBeNull();
  });
});
