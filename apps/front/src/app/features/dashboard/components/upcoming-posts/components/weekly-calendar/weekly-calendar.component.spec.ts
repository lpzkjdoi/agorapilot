import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { TestBed } from '@angular/core/testing';
import { WeeklyCalendarComponent } from './weekly-calendar.component';

// Child occurrence cards format dates with the `fr-FR` locale.
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

describe('WeeklyCalendarComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WeeklyCalendarComponent],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(WeeklyCalendarComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should expose an empty entries list when no data is provided', () => {
    const fixture = TestBed.createComponent(WeeklyCalendarComponent);
    expect(fixture.componentInstance.entries()).toEqual([]);

    fixture.componentRef.setInput('weeklyOccurrences', null);
    expect(fixture.componentInstance.entries()).toEqual([]);
  });

  it('should map the weekly occurrences to key/value entries', () => {
    const fixture = TestBed.createComponent(WeeklyCalendarComponent);
    // The runtime payload is a plain object keyed by day; the declared Map type
    // is looser than what the backend actually returns.
    fixture.componentRef.setInput('weeklyOccurrences', week as never);

    const entries = fixture.componentInstance.entries();
    expect(entries.length).toBe(7);
    expect(entries[0][0]).toBe('2026-07-20');
  });

  it('should render one occurrence card per day', () => {
    const fixture = TestBed.createComponent(WeeklyCalendarComponent);
    fixture.componentRef.setInput('weeklyOccurrences', week as never);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h3')?.textContent).toContain('Cette semaine');
    expect(compiled.querySelectorAll('app-weekly-occurrence-card').length).toBe(7);
  });
});
