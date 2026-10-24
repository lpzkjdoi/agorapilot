import { registerLocaleData } from '@angular/common';
import localeFr from '@angular/common/locales/fr';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Occurrence } from '../../../occurrences/occurrence.model';
import { UpcomingPostsComponent } from './upcoming-posts.component';

// Les cartes formatent leur date en `fr-FR`.
registerLocaleData(localeFr, 'fr-FR');

function makeOccurrence(id: number, content: string): Occurrence {
  return {
    id,
    scheduledAt: '2026-07-20T09:00:00',
    pinned: false,
    status: 'SCHEDULED',
    publication: { id, content, status: 'VERIFIED', medias: [], campaign: null },
    deliveries: [],
  };
}

describe('UpcomingPostsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UpcomingPostsComponent],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('should create', () => {
    const fixture = TestBed.createComponent(UpcomingPostsComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should show the empty placeholder when there is no occurrence', () => {
    const fixture = TestBed.createComponent(UpcomingPostsComponent);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelector('h3')?.textContent).toContain('Publications à venir');
    expect(compiled.textContent).toContain('Aucune publication prévue');
    expect(compiled.querySelectorAll('app-upcoming-post-card').length).toBe(0);
  });

  it('should show a loading note instead of the empty placeholder while loading', () => {
    const fixture = TestBed.createComponent(UpcomingPostsComponent);
    fixture.componentRef.setInput('loading', true);
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Chargement');
    expect(text).not.toContain('Aucune publication prévue');
  });

  it('should render one card per occurrence', () => {
    const fixture = TestBed.createComponent(UpcomingPostsComponent);
    fixture.componentRef.setInput('upcomingOccurrences', [
      makeOccurrence(1, 'Post A'),
      makeOccurrence(2, 'Post B'),
    ]);
    fixture.detectChanges();

    const compiled = fixture.nativeElement as HTMLElement;
    expect(compiled.querySelectorAll('app-upcoming-post-card').length).toBe(2);
    expect(compiled.textContent).not.toContain('Aucune publication prévue');
  });

  it('should link « Voir tout » to the calendar', () => {
    const fixture = TestBed.createComponent(UpcomingPostsComponent);
    fixture.detectChanges();

    const link = (fixture.nativeElement as HTMLElement).querySelector('.posts-panel-see-all');
    expect(link?.getAttribute('href')).toBe('/calendrier');
  });
});
