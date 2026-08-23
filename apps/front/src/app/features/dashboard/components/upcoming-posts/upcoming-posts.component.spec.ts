import { TestBed } from '@angular/core/testing';
import { Occurrence } from '../../../occurrences/occurrence.model';
import { UpcomingPostsComponent } from './upcoming-posts.component';

function makeOccurrence(id: number, content: string): Occurrence {
  return {
    id,
    scheduledAt: '2026-07-20T09:00:00Z',
    status: 'SCHEDULED',
    publication: { id, content, status: 'VERIFIED', medias: [] },
    deliveries: [],
  };
}

describe('UpcomingPostsComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [UpcomingPostsComponent],
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
});
