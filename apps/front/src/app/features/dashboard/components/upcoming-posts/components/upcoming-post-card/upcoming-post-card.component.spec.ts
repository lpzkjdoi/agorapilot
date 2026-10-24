import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { TestBed } from '@angular/core/testing';
import { Occurrence } from '../../../../../occurrences/occurrence.model';
import { UpcomingPostCardComponent } from './upcoming-post-card.component';

registerLocaleData(localeFr, 'fr-FR');

const occurrence: Occurrence = {
  id: 7,
  // Heure serveur sans fuseau, comme la renvoie le back.
  scheduledAt: '2026-10-29T18:30:00',
  pinned: false,
  status: 'SCHEDULED',
  publication: { id: 3, content: 'Réunion publique ce jeudi', status: 'VERIFIED', medias: [], campaign: null },
  deliveries: [{ id: 11, channel: 'FACEBOOK', status: 'PENDING' }],
};

describe('UpcomingPostCardComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UpcomingPostCardComponent],
    }).compileComponents();
  });

  function render(value: Occurrence) {
    const fixture = TestBed.createComponent(UpcomingPostCardComponent);
    fixture.componentRef.setInput('occurrence', value);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('should create', () => {
    const fixture = TestBed.createComponent(UpcomingPostCardComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the date block, the content and the time', () => {
    const compiled = render(occurrence);

    expect(compiled.querySelector('.upcoming-day')?.textContent?.trim()).toBe('29');
    expect(compiled.querySelector('.upcoming-month')?.textContent?.trim()).toBe('oct.');
    expect(compiled.querySelector('.upcoming-content')?.textContent).toContain('Réunion publique ce jeudi');
    expect(compiled.querySelector('.upcoming-time')?.textContent).toContain('jeudi');
    expect(compiled.querySelector('.upcoming-time')?.textContent).toContain('18:30');
  });

  it('should render one badge per channel and the status badge', () => {
    const compiled = render(occurrence);

    expect(compiled.querySelector('.occurrence-badge-facebook')?.textContent?.trim()).toBe('Facebook');
    expect(compiled.querySelector('.occurrence-badge-scheduled')?.textContent?.trim()).toBe('Programmée');
  });

  it('should render the failed status in red', () => {
    const compiled = render({ ...occurrence, status: 'FAILED' });

    expect(compiled.querySelector('.occurrence-badge-failed')?.textContent?.trim()).toBe('Échec');
  });

  it('should render nothing without an occurrence', () => {
    const fixture = TestBed.createComponent(UpcomingPostCardComponent);
    expect(() => fixture.detectChanges()).not.toThrow();
    expect((fixture.nativeElement as HTMLElement).querySelector('.upcoming-card')).toBeNull();
  });
});
